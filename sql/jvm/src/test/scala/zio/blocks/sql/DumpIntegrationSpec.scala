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

import zio.test.*
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{Rel, SqlQuery => Qry, lit}
import zio.blocks.sql.query.*

private object DumpFixtureTwoFilters {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val twoFilters             =
    q0.where(q0.col[User](_.name) === lit("alice")).where(q0.col[User](_.id) === lit(42))
}

private object DumpFixtureAndOr {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val andPred                = q0.where((q0.col[User](_.name) === lit("bob")) && (q0.col[User](_.id) === lit(7)))
  val orPred                 = q0.where((q0.col[User](_.name) === lit("bob")) || (q0.col[User](_.id) === lit(7)))
}

private object DumpFixtureLikeIn {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val likeQ                  = q0.where(q0.col[User](_.name).like("%ali%"))
  val inQ                    = q0.where(q0.col[User](_.id).in(Seq(1, 2, 3)))
}

private object DumpFixtureJoinCombined {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val repoTable: Table[Repo] = Table.derived[Repo]
  val rel: Rel[Repo, User]   = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
  val q0                     = Qry.from(userTable).innerJoin(rel)
  val combined               = q0.where(q0.col[User](_.name).like("a%")).where(q0.col[Repo](_.name) === lit("my-repo"))
}

object DumpIntegrationSpec extends ZIOSpecDefault {

  def spec = suite("DumpIntegrationSpec")(
    test("typed equality single filter exact sql") {
      case class User(id: Int, name: String)
      object User { implicit val schema: Schema[User] = Schema.derived }
      val tbl     = Table.derived[User]
      val qBase   = Qry.from(tbl)
      val q       = qBase.where(qBase.col[User](_.name) === lit("alice"))
      val frag    = q.toFrag(SqlDialect.PostgreSQL)
      val fragSql = frag.sql(SqlDialect.PostgreSQL)
      assertTrue(
        fragSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ?""",
        !fragSql.contains("WHERE ?"),
        frag.params == IndexedSeq(DbValue.DbString("alice"))
      )
    },
    test("multiple typed filters preserve order, count, and exact params") {
      val q       = DumpFixtureTwoFilters.twoFilters
      val frag    = q.toFrag(SqlDialect.PostgreSQL)
      val fragSql = frag.sql(SqlDialect.PostgreSQL)
      assertTrue(
        fragSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?""",
        !fragSql.contains("WHERE ?"),
        frag.params == IndexedSeq(DbValue.DbString("alice"), DbValue.DbInt(42))
      )
    },
    test("AND combined predicate vs multiple where render distinctly") {
      val qAnd   = DumpFixtureAndOr.andPred
      val qTwo   = DumpFixtureTwoFilters.twoFilters
      val andSql = qAnd.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val twoSql = qTwo.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      assertTrue(
        andSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE (t0."name" = ? AND t0."id" = ?)""",
        twoSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?""",
        !andSql.contains("WHERE ?"),
        !twoSql.contains("WHERE ?")
      )
    },
    test("OR predicate exact sql") {
      val q    = DumpFixtureAndOr.orPred
      val frag = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      assertTrue(
        frag == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE (t0."name" = ? OR t0."id" = ?)""",
        !frag.contains("WHERE ?")
      )
    },
    test("LIKE exact predicate and sql") {
      val q    = DumpFixtureLikeIn.likeQ
      val frag = q.toFrag(SqlDialect.PostgreSQL)
      assertTrue(
        frag.sql(SqlDialect.PostgreSQL) == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" LIKE ?""",
        !frag.sql(SqlDialect.PostgreSQL).contains("WHERE ?"),
        frag.params == IndexedSeq(DbValue.DbString("%ali%"))
      )
    },
    test("IN exact predicate, placeholder list, and params") {
      val q    = DumpFixtureLikeIn.inQ
      val frag = q.toFrag(SqlDialect.PostgreSQL)
      assertTrue(
        frag.sql(SqlDialect.PostgreSQL) == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."id" IN (?, ?, ?)""",
        !frag.sql(SqlDialect.PostgreSQL).contains("WHERE ?"),
        frag.sql(SqlDialect.PostgreSQL).contains("IN (?, ?, ?)"),
        frag.params == IndexedSeq(DbValue.DbInt(1), DbValue.DbInt(2), DbValue.DbInt(3))
      )
    },
    test("join+like+relational params align with frag params") {
      val q    = DumpFixtureJoinCombined.combined
      val frag = q.toFrag(SqlDialect.PostgreSQL)
      assertTrue(
        frag.sql(
          SqlDialect.PostgreSQL
        ) == """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" LIKE ? AND t1."name" = ?""",
        !frag.sql(SqlDialect.PostgreSQL).contains("WHERE ?"),
        frag.params == IndexedSeq(DbValue.DbString("a%"), DbValue.DbString("my-repo"))
      )
    },
    test("Expr node constructors are sealed — external callers cannot forge a query scope") {
      val forgeErrors = scala.compiletime.testing.typeCheckErrors(
        """import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { implicit val schema: Schema[User] = Schema.derived }
          val userTable = Table.derived[User]
          val q = zio.blocks.sql.query.SqlQuery.from(userTable)
          val forged = new Column[User, Int, q.type](userTable, "id", None, null)
        """
      )
      val litForgeErrors = scala.compiletime.testing.typeCheckErrors(
        """import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { implicit val schema: Schema[User] = Schema.derived }
          val userTable = Table.derived[User]
          val forged = Lit(1, null, null)
        """
      )
      val aggForgeErrors = scala.compiletime.testing.typeCheckErrors(
        """import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { implicit val schema: Schema[User] = Schema.derived }
          val userTable = Table.derived[User]
          val q = zio.blocks.sql.query.SqlQuery.from(userTable)
          val forged = Agg[Long, q.type](AggFunc.Count, q.col[User](_.id))
        """
      )
      assertTrue(
        forgeErrors.nonEmpty,
        forgeErrors.exists(_.message.contains("constructor Column cannot be accessed")),
        litForgeErrors.nonEmpty,
        litForgeErrors.exists(_.message.contains("Lit")),
        aggForgeErrors.nonEmpty,
        aggForgeErrors.exists(_.message.contains("Agg"))
      )
    },
    test("foreign, ambiguous and bad-alias columns fail at compile time") {
      val foreignErrors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { implicit val schema: Schema[User] = Schema.derived }
          case class Other(id: Int, x: String)
          object Other { implicit val schema: Schema[Other] = Schema.derived }
          val userTable = Table.derived[User]
          val qBase = SqlQuery.from(userTable)
          qBase.where(qBase.col[Other](_.x) === lit("a"))
        }"""
      )
      val ambiguousErrors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class Node(id: Int, parentId: Int)
          object Node { implicit val schema: Schema[Node] = Schema.derived }
          val nodeTable = Table.derived[Node]
          val qBase = SqlQuery.from(nodeTable).innerJoin(Rel.manyToOne(nodeTable, "parent_id", nodeTable, "id"))
          qBase.where(qBase.col[Node](_.id) === lit(1))
        }"""
      )
      val badAliasErrors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { implicit val schema: Schema[User] = Schema.derived }
          val userTable = Table.derived[User]
          val qBase = SqlQuery.from(userTable)
          qBase.where(qBase.colAt[User]("bad-alias!", _.name) === lit("a"))
        }"""
      )
      assertTrue(foreignErrors.nonEmpty, ambiguousErrors.nonEmpty, badAliasErrors.nonEmpty)
    },
    test("typed query explain body matches rendered sql with numbered placeholders") {
      val q        = DumpFixtureTwoFilters.twoFilters
      val fragSql  = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val explain  = q.explain(SqlDialect.PostgreSQL)
      val expected =
        """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ?1 AND t0."id" = ?2""" +
          "\n-- params: 1:String, 2:Int"
      assertTrue(
        fragSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?""",
        explain == expected,
        !explain.contains("alice")
      )
    }
  )
}
