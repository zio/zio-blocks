/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.sql

import zio.test._
import zio.blocks.schema.Schema

object ExplainSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo {
    implicit val schema: Schema[Repo] = Schema.derived
  }

  case class Star(userId: Int, repoId: Int)
  object Star {
    implicit val schema: Schema[Star] = Schema.derived
  }

  val userTable = Table.derived[User]
  val repoTable = Table.derived[Repo]
  val starTable = Table.derived[Star]

  def spec = suite("ExplainSpec")(
    test("2-join query with filters renders golden SQL and param footer") {
      val q = SqlQuery
        .from(userTable)
        .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
        .join(starTable, leftColumn = "id", rightColumn = "repo_id")
        .where(userTable, "name", DbValue.DbString("alice"))
        .where(repoTable, "name", DbValue.DbString("my-repo"))

      val explain = q.explain(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)

      val expectedSql =
        "SELECT t0.id, t0.name, t1.id, t1.owner_id, t1.name, t2.user_id, t2.repo_id FROM user t0 INNER JOIN repo t1 ON t0.id = t1.owner_id INNER JOIN star t2 ON t1.id = t2.repo_id WHERE t0.name = ?1 AND t1.name = ?2"

      assertTrue(
        explain.contains(expectedSql),
        explain.contains("-- params: 1:String, 2:String"),
        !explain.contains("alice"),
        !explain.contains("my-repo"),
        st.source.table == "user",
        st.source.alias == "t0",
        st.joins.size == 2,
        st.joins(0).kind == SqlStatement.JoinKind.Inner,
        st.joins(0).onLeft == SqlStatement.ColumnRef("t0", "id"),
        st.joins(0).onRight == SqlStatement.ColumnRef("t1", "owner_id"),
        st.joins(1).onLeft == SqlStatement.ColumnRef("t1", "id"),
        st.joins(1).onRight == SqlStatement.ColumnRef("t2", "repo_id"),
        st.filters.size == 2,
        st.filters(0).column == SqlStatement.ColumnRef("t0", "name"),
        st.filters(1).column == SqlStatement.ColumnRef("t1", "name"),
        st.frag.params == IndexedSeq(DbValue.DbString("alice"), DbValue.DbString("my-repo"))
      )
    },
    test("zero params query has no placeholders and (none) footer") {
      val q = SqlQuery
        .from(userTable)
        .join(repoTable, leftColumn = "id", rightColumn = "owner_id")

      val explain = q.explain(SqlDialect.SQLite)
      val st      = q.statement(SqlDialect.SQLite)

      assertTrue(
        !explain.contains("?1"),
        explain.contains("-- params: (none)"),
        st.filters.isEmpty,
        st.joins.size == 1,
        st.joins.head.kind == SqlStatement.JoinKind.Inner,
        st.frag.params.isEmpty
      )
    },
    test("single join with one filter") {
      val q = SqlQuery
        .from(userTable)
        .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
        .where(userTable, "id", DbValue.DbInt(42))

      val explain = q.explain(SqlDialect.PostgreSQL)
      assertTrue(
        explain.contains("INNER JOIN repo t1 ON t0.id = t1.owner_id"),
        explain.contains("WHERE t0.id = ?1"),
        explain.contains("-- params: 1:Int"),
        !explain.contains("42")
      )
    },
    test("orderBy and limit appear in explain and statement") {
      val q = SqlQuery
        .from(userTable)
        .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
        .where(userTable, "name", DbValue.DbString("bob"))
        .orderBy(userTable, "id", SqlStatement.OrderDirection.Asc)
        .orderBy(repoTable, "name", SqlStatement.OrderDirection.Desc)
        .limit(10)
        .offset(5)

      val explain = q.explain(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)

      assertTrue(
        explain.contains("ORDER BY t0.id ASC, t1.name DESC"),
        explain.contains("LIMIT 10"),
        explain.contains("OFFSET 5"),
        explain.contains("?1"),
        explain.contains("-- params: 1:String"),
        st.orderBy.size == 2,
        st.orderBy.head.column == SqlStatement.ColumnRef("t0", "id"),
        st.orderBy.head.direction == SqlStatement.OrderDirection.Asc,
        st.orderBy(1).direction == SqlStatement.OrderDirection.Desc,
        st.limit.contains(SqlStatement.Limit(10)),
        st.offset.contains(SqlStatement.Offset(5)),
        !explain.contains("bob")
      )
    },
    test("left join kind preserved and groupBy appears") {
      val q = SqlQuery
        .from(userTable)
        .joinLeft(repoTable, leftColumn = "id", rightColumn = "owner_id")
        .where(userTable, "name", DbValue.DbString("x"))
        .groupBy(userTable, "id")

      val explain = q.explain(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)

      assertTrue(
        explain.contains("LEFT JOIN repo t1"),
        explain.contains("GROUP BY t0.id"),
        st.joins.head.kind == SqlStatement.JoinKind.Left,
        st.groupBy.contains(SqlStatement.GroupBy(Vector(SqlStatement.ColumnRef("t0", "id"))))
      )
    },
    test("explain never leaks values for multiple param types") {
      val q = SqlQuery
        .from(userTable)
        .where(userTable, "id", DbValue.DbInt(123))
        .where(userTable, "name", DbValue.DbString("secret"))
        .where(userTable, "id", ">", DbValue.DbLong(999L))

      val explain = q.explain(SqlDialect.PostgreSQL)
      assertTrue(
        !explain.contains("123"),
        !explain.contains("secret"),
        !explain.contains("999"),
        explain.contains("-- params: 1:Int, 2:String, 3:Long"),
        explain.contains("?1"),
        explain.contains("?2"),
        explain.contains("?3")
      )
    },
    test("explain reuses renderer - statement frag equals toFrag") {
      val q = SqlQuery
        .from(userTable)
        .join(repoTable, "id", "owner_id")
        .where(userTable, "name", DbValue.DbString("a"))

      val st   = q.statement(SqlDialect.PostgreSQL)
      val frag = q.toFrag(SqlDialect.PostgreSQL)
      assertTrue(st.frag == frag)
    }
  )
}
