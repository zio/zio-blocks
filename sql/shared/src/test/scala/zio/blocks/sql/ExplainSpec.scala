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
import zio.blocks.schema._
import zio.blocks.sql.query.{JoinKind, OrderBy, Rel, SortOrder, SqlQuery}

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

  val userRepoRel: Rel[Repo, User] = Rel(repoTable, "owner_id", userTable, "id")
  val repoStarRel: Rel[Star, Repo] = Rel(starTable, "repo_id", repoTable, "id")

  private def nameIs(alias: String, value: String): Frag =
    Frag(IndexedSeq(s"$alias.\"name\" = ", ""), IndexedSeq(DbValue.DbString(value)))

  def spec = suite("ExplainSpec")(
    test("two inner joins with filters render numbered placeholders and typed param footer") {
      val q = SqlQuery
        .from(userTable)
        .innerJoin(userRepoRel)
        .innerJoin(repoStarRel)
        .filter(nameIs("t0", "alice"))
        .filter(nameIs("t1", "my-repo"))

      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name", t2."user_id", t2."repo_id" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" INNER JOIN "star" AS t2 ON t2."repo_id" = t1."id" WHERE t0."name" = ?1 AND t1."name" = ?2""" +
          "\n-- params: 1:String, 2:String"

      assertTrue(
        explain == expected,
        q.source.name == "user",
        q.joins.size == 2,
        q.joins(0).kind == JoinKind.Inner,
        q.joins(0).table.name == "repo",
        q.joins(0).alias == "t1",
        q.joins(0).on.sql(SqlDialect.PostgreSQL) == """t1."owner_id" = t0."id"""",
        q.joins(1).alias == "t2",
        q.joins(1).on.sql(SqlDialect.PostgreSQL) == """t2."repo_id" = t1."id"""",
        q.filters.size == 2,
        q.toFrag(SqlDialect.PostgreSQL).params == IndexedSeq(DbValue.DbString("alice"), DbValue.DbString("my-repo"))
      )
    },
    test("query without filters renders no placeholders and a none footer") {
      val q = SqlQuery
        .from(userTable)
        .innerJoin(userRepoRel)

      val explain = q.explain(SqlDialect.SQLite)

      val expected =
        """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id"""" +
          "\n-- params: (none)"

      assertTrue(
        explain == expected,
        q.filters.isEmpty,
        q.joins.size == 1,
        q.joins.head.kind == JoinKind.Inner,
        q.toFrag(SqlDialect.SQLite).params.isEmpty
      )
    },
    test("single join with one filter numbers its placeholder") {
      val q = SqlQuery
        .from(userTable)
        .innerJoin(userRepoRel)
        .filter(Frag(IndexedSeq("t0.\"id\" = ", ""), IndexedSeq(DbValue.DbInt(42))))

      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."id" = ?1""" +
          "\n-- params: 1:Int"

      assertTrue(explain == expected)
    },
    test("order by limit and offset render on the source alias") {
      val q = SqlQuery
        .from(userTable)
        .innerJoin(userRepoRel)
        .filter(nameIs("t0", "bob"))
        .orderBy("id", SortOrder.Asc)
        .orderBy("name", SortOrder.Desc)
        .limit(10)
        .offset(5)

      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" = ?1 ORDER BY t0."id" ASC, t0."name" DESC LIMIT 10 OFFSET 5""" +
          "\n-- params: 1:String"

      assertTrue(
        explain == expected,
        q.orderBy == Vector(OrderBy("id", SortOrder.Asc), OrderBy("name", SortOrder.Desc)),
        q.limit.contains(10),
        q.offset.contains(5)
      )
    },
    test("left join and group by render join kind and grouped columns") {
      val q = SqlQuery
        .from(userTable)
        .leftJoin(userRepoRel)
        .filter(nameIs("t0", "x"))
        .groupBy("id")

      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 LEFT JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" = ?1 GROUP BY t0."id"""" +
          "\n-- params: 1:String"

      assertTrue(
        explain == expected,
        q.joins.head.kind == JoinKind.Left,
        q.groupBy == Vector("id")
      )
    },
    test("explain never leaks bound values and footers every param type") {
      val q = SqlQuery
        .from(userTable)
        .filter(Frag(IndexedSeq("t0.\"id\" = ", ""), IndexedSeq(DbValue.DbInt(123))))
        .filter(nameIs("t0", "secret"))
        .filter(Frag(IndexedSeq("t0.\"id\" > ", ""), IndexedSeq(DbValue.DbLong(999L))))

      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."id" = ?1 AND t0."name" = ?2 AND t0."id" > ?3""" +
          "\n-- params: 1:Int, 2:String, 3:Long"

      assertTrue(
        explain == expected,
        !explain.contains("123"),
        !explain.contains("secret"),
        !explain.contains("999")
      )
    },
    test("explain body matches rendered sql with numbered placeholders") {
      val q = SqlQuery
        .from(userTable)
        .innerJoin(userRepoRel)
        .filter(nameIs("t0", "a"))

      val frag     = q.toFrag(SqlDialect.PostgreSQL)
      val rendered = frag.sql(SqlDialect.PostgreSQL)
      val parts    = rendered.split("\\?", -1)
      val numbered = parts.zipWithIndex.map {
        case (p, 0) => p
        case (p, n) => s"?$n$p"
      }.mkString
      val explain = q.explain(SqlDialect.PostgreSQL)
      assertTrue(explain == numbered + "\n-- params: 1:String")
    },
    test("reserved and mixed-case columns render double-quoted") {
      case class Weird(@Modifier.rename("order") ord: Int, @Modifier.rename("MixedCase") mixed: String)
      object Weird {
        implicit val schema: Schema[Weird] = Schema.derived
      }
      val table = Table.derived[Weird]
      val q     = SqlQuery
        .from(table)
        .filter(Frag(IndexedSeq("t0.\"order\" = ", ""), IndexedSeq(DbValue.DbInt(1))))
      val explain = q.explain(SqlDialect.PostgreSQL)

      val expected =
        """SELECT t0."order", t0."MixedCase" FROM "weird" AS t0 WHERE t0."order" = ?1""" +
          "\n-- params: 1:Int"

      assertTrue(explain == expected)
    }
  )
}
