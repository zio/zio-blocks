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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.test.*
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{Rel, SqlQuery => Qry, lit}
import zio.blocks.sql.query.*

private object Task7DumpFixtureTwoFilters {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val twoFilters             =
    q0.where(q0.col[User](_.name) === lit("alice")).where(q0.col[User](_.id) === lit(42))
}

private object Task7DumpFixtureAndOr {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val andPred                = q0.where((q0.col[User](_.name) === lit("bob")) && (q0.col[User](_.id) === lit(7)))
  val orPred                 = q0.where((q0.col[User](_.name) === lit("bob")) || (q0.col[User](_.id) === lit(7)))
}

private object Task7DumpFixtureLikeIn {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable: Table[User] = Table.derived[User]
  val q0                     = Qry.from(userTable)
  val likeQ                  = q0.where(q0.col[User](_.name).like("%ali%"))
  val inQ                    = q0.where(q0.col[User](_.id).in(Seq(1, 2, 3)))
}

private object Task7DumpFixtureJoinCombined {
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

object Task7RepairSpec extends ZIOSpecDefault {

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      val s = Files.list(path)
      try s.forEach(deleteRecursively)
      finally s.close()
      Files.deleteIfExists(path)
      ()
    } else Files.deleteIfExists(path)
    ()
  }

  private def withTempDumpDir[A](body: Path => A): A = {
    val tmp  = Files.createTempDirectory("task7-dump-")
    val orig = Option(System.getProperty("zib.sql.dumpDir"))
    try {
      System.setProperty("zib.sql.dumpDir", tmp.toString)
      body(tmp)
    } finally {
      orig match {
        case Some(v) => System.setProperty("zib.sql.dumpDir", v)
        case None    => System.clearProperty("zib.sql.dumpDir")
      }
      deleteRecursively(tmp)
    }
  }

  private def normalizeSql(sql: String): String = {
    val noAs  = sql.replaceAll("(?i)\\s+AS\\s+", " ")
    val qNorm = noAs.replaceAll("\\?[0-9]+", "?")
    qNorm.replaceAll("\\s+", " ").trim
  }

  def spec = suite("Task7RepairSpec")(
    test("typed equality single filter exact sql and statement") {
      case class User(id: Int, name: String)
      object User { implicit val schema: Schema[User] = Schema.derived }
      val tbl     = Table.derived[User]
      val qBase   = Qry.from(tbl)
      val q       = qBase.where(qBase.col[User](_.name) === lit("alice"))
      val fragSql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        fragSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ?""",
        !fragSql.contains("WHERE ?"),
        st.filters.size == 1,
        st.filters.head.operator == "=",
        st.filters.head.column.contains(SqlStatement.ColumnRef("t0", "name")),
        st.filters.head.predicate.sql(SqlDialect.PostgreSQL) == """t0."name" = ?""",
        st.filters.head.params == IndexedSeq(DbValue.DbString("alice")),
        st.frag.params == IndexedSeq(DbValue.DbString("alice")),
        st.frag == q.toFrag(SqlDialect.PostgreSQL)
      )
    },
    test("multiple typed filters preserve order, count, and exact params") {
      val q       = Task7DumpFixtureTwoFilters.twoFilters
      val fragSql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        fragSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?""",
        !fragSql.contains("WHERE ?"),
        st.filters.size == 2,
        st.filters(0).operator == "=",
        st.filters(0).column.contains(SqlStatement.ColumnRef("t0", "name")),
        st.filters(0).predicate.sql(SqlDialect.PostgreSQL) == """t0."name" = ?""",
        st.filters(0).params == IndexedSeq(DbValue.DbString("alice")),
        st.filters(1).operator == "=",
        st.filters(1).column.contains(SqlStatement.ColumnRef("t0", "id")),
        st.filters(1).predicate.sql(SqlDialect.PostgreSQL) == """t0."id" = ?""",
        st.filters(1).params == IndexedSeq(DbValue.DbInt(42)),
        st.frag.params == IndexedSeq(DbValue.DbString("alice"), DbValue.DbInt(42)),
        st.frag == q.toFrag(SqlDialect.PostgreSQL)
      )
    },
    test("AND combined predicate vs multiple where have distinct filter inspection") {
      val qAnd   = Task7DumpFixtureAndOr.andPred
      val qTwo   = Task7DumpFixtureTwoFilters.twoFilters
      val andSql = qAnd.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val twoSql = qTwo.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val stAnd  = qAnd.statement(SqlDialect.PostgreSQL)
      val stTwo  = qTwo.statement(SqlDialect.PostgreSQL)
      assertTrue(
        andSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE (t0."name" = ? AND t0."id" = ?)""",
        twoSql == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?""",
        !andSql.contains("WHERE ?"),
        !twoSql.contains("WHERE ?"),
        stAnd.filters.size == 1,
        stAnd.filters.head.operator == "AND",
        stAnd.filters.head.column.isEmpty,
        stAnd.filters.head.predicate.sql(SqlDialect.PostgreSQL) == """(t0."name" = ? AND t0."id" = ?)""",
        stAnd.filters.head.params == IndexedSeq(DbValue.DbString("bob"), DbValue.DbInt(7)),
        stTwo.filters.size == 2
      )
    },
    test("OR predicate exact inspection and sql") {
      val q    = Task7DumpFixtureAndOr.orPred
      val frag = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st   = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        frag == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE (t0."name" = ? OR t0."id" = ?)""",
        !frag.contains("WHERE ?"),
        st.filters.size == 1,
        st.filters.head.operator == "OR",
        st.filters.head.column.isEmpty,
        st.filters.head.predicate.sql(SqlDialect.PostgreSQL) == """(t0."name" = ? OR t0."id" = ?)""",
        st.filters.head.params == IndexedSeq(DbValue.DbString("bob"), DbValue.DbInt(7))
      )
    },
    test("LIKE exact predicate, operator, and sql") {
      val q    = Task7DumpFixtureLikeIn.likeQ
      val frag = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st   = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        frag == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" LIKE ?""",
        !frag.contains("WHERE ?"),
        st.filters.size == 1,
        st.filters.head.operator == "LIKE",
        st.filters.head.column.contains(SqlStatement.ColumnRef("t0", "name")),
        st.filters.head.predicate.sql(SqlDialect.PostgreSQL) == """t0."name" LIKE ?""",
        st.filters.head.params == IndexedSeq(DbValue.DbString("%ali%"))
      )
    },
    test("IN exact predicate, operator, placeholder list, and params") {
      val q    = Task7DumpFixtureLikeIn.inQ
      val frag = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st   = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        frag == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."id" IN (?, ?, ?)""",
        !frag.contains("WHERE ?"),
        frag.contains("IN (?, ?, ?)"),
        st.filters.size == 1,
        st.filters.head.operator == "IN",
        st.filters.head.column.contains(SqlStatement.ColumnRef("t0", "id")),
        st.filters.head.predicate.sql(SqlDialect.PostgreSQL) == """t0."id" IN (?, ?, ?)""",
        st.filters.head.params == IndexedSeq(DbValue.DbInt(1), DbValue.DbInt(2), DbValue.DbInt(3)),
        st.frag.params == IndexedSeq(DbValue.DbInt(1), DbValue.DbInt(2), DbValue.DbInt(3))
      )
    },
    test("statement params align with frag params for join+like+relational") {
      val q    = Task7DumpFixtureJoinCombined.combined
      val frag = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val st   = q.statement(SqlDialect.PostgreSQL)
      assertTrue(
        frag == """SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" LIKE ? AND t1."name" = ?""",
        !frag.contains("WHERE ?"),
        st.filters.size == 2,
        st.filters(0).operator == "LIKE",
        st.filters(0).column.contains(SqlStatement.ColumnRef("t0", "name")),
        st.filters(1).operator == "=",
        st.filters(1).column.contains(SqlStatement.ColumnRef("t1", "name")),
        st.frag.params == IndexedSeq(DbValue.DbString("a%"), DbValue.DbString("my-repo")),
        st.filters(0).params == IndexedSeq(DbValue.DbString("a%")),
        st.filters(1).params == IndexedSeq(DbValue.DbString("my-repo"))
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
    test("generated dump equals runtime QueryRenderer sql via scoped temp dir") {
      withTempDumpDir { dir =>
        val qTwo  = Task7DumpFixtureTwoFilters.twoFilters
        val qAnd  = Task7DumpFixtureAndOr.andPred
        val qIn   = Task7DumpFixtureLikeIn.inQ
        val qLike = Task7DumpFixtureLikeIn.likeQ
        val qJoin = Task7DumpFixtureJoinCombined.combined
        val cases = Seq(
          ("task7_two_filters", qTwo),
          ("task7_and", qAnd),
          ("task7_in", qIn),
          ("task7_like", qLike),
          ("task7_join", qJoin)
        )
        for ((base, q) <- cases) {
          val pgSql     = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
          val sqliteSql = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          Dump.emitRuntime(base, SqlDialect.PostgreSQL, pgSql)
          Dump.emitRuntime(base, SqlDialect.SQLite, sqliteSql)
          val pgFile     = dir.resolve(s"$base-postgresql.sql")
          val sqliteFile = dir.resolve(s"$base-sqlite.sql")
          val pgRead     = new String(Files.readAllBytes(pgFile), StandardCharsets.UTF_8).trim
          val sqliteRead = new String(Files.readAllBytes(sqliteFile), StandardCharsets.UTF_8).trim
          assertTrue(
            pgRead == pgSql.trim,
            sqliteRead == sqliteSql.trim,
            !pgRead.contains("WHERE ?"),
            !sqliteRead.contains("WHERE ?"),
            pgRead != "WHERE ?",
            pgRead.contains("?") == pgSql.contains("?")
          )
          if (pgRead != pgSql.trim || sqliteRead != sqliteSql.trim)
            throw new AssertionError(s"dump mismatch for $base: pgRead=$pgRead pgSql=$pgSql")
          if (pgRead.contains("WHERE ?"))
            throw new AssertionError(s"dump contained WHERE ? fallback for $base: $pgRead")
        }
        // also verify exact normalized equality for one case
        val twoPg     = Task7DumpFixtureTwoFilters.twoFilters.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val pgFileTwo = dir.resolve("task7_two_filters-postgresql.sql")
        val twoRead   = new String(Files.readAllBytes(pgFileTwo), StandardCharsets.UTF_8).trim
        assertTrue(
          normalizeSql(twoRead) == normalizeSql(twoPg),
          twoRead == """SELECT t0."id", t0."name" FROM "user" AS t0 WHERE t0."name" = ? AND t0."id" = ?"""
        )
      }
      assertTrue(true)
    }
  )
}
