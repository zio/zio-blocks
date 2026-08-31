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

package zio.blocks.sql.query

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.{DbCon, DbValue, Frag, JdbcTransactor, SqlDialect, Table, sql}
import zio.test.*

/**
 * Task 9: generalized typed ordering and projections.
 *
 * Typed `ORDER BY` (joined aliases, aggregates, mixed directions, Option and
 * arithmetic expressions, scope enforcement) and unbounded projection arity via
 * recursive tuple flattening (Tuple9, Tuple22, `*:`-shaped tuples, arity 23).
 */
object Task9IntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  @Modifier.config("sql.table_name", "users_t9")
  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  @Modifier.config("sql.table_name", "repos_t9")
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  @Modifier.config("sql.table_name", "sales_t9")
  case class Sale(id: Int, userId: Int, amount: Int)
  object Sale { given Schema[Sale] = Schema.derived }

  @Modifier.config("sql.table_name", "wide9")
  case class Wide9(c1: Int, c2: Int, c3: Int, c4: Int, c5: Int, c6: Int, c7: Int, c8: Int, c9: Int)
  object Wide9 { given Schema[Wide9] = Schema.derived }

  @Modifier.config("sql.table_name", "wide22")
  case class Wide22(
    c1: Int,
    c2: Int,
    c3: Int,
    c4: Int,
    c5: Int,
    c6: Int,
    c7: Int,
    c8: Int,
    c9: Int,
    c10: Int,
    c11: Int,
    c12: Int,
    c13: Int,
    c14: Int,
    c15: Int,
    c16: Int,
    c17: Int,
    c18: Int,
    c19: Int,
    c20: Int,
    c21: Int,
    c22: Int
  )
  object Wide22 { given Schema[Wide22] = Schema.derived }

  @Modifier.config("sql.table_name", "triples")
  case class Triple(a: Int, b: String, c: Int)
  object Triple { given Schema[Triple] = Schema.derived }

  val userTable   = Table.derived[User]
  val repoTable   = Table.derived[Repo]
  val saleTable   = Table.derived[Sale]
  val wide9Table  = Table.derived[Wide9]
  val wide22Table = Table.derived[Wide22]
  val tripleTable = Table.derived[Triple]

  val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)

  private val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  private def setupUsersRepos()(using con: DbCon): Unit = {
    Frag.literal("DROP TABLE IF EXISTS repos_t9").update
    Frag.literal("DROP TABLE IF EXISTS users_t9").update
    userTable.createTable(SqlDialect.SQLite).update
    repoTable.createTable(SqlDialect.SQLite).update
    sql"INSERT INTO users_t9 (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
    sql"INSERT INTO users_t9 (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
    sql"INSERT INTO repos_t9 (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
    sql"INSERT INTO repos_t9 (id, owner_id, name) VALUES (${DbValue.DbInt(20)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r2")})".update
    sql"INSERT INTO repos_t9 (id, owner_id, name) VALUES (${DbValue.DbInt(30)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r3")})".update
  }

  private def setupSales()(using con: DbCon): Unit = {
    Frag.literal("DROP TABLE IF EXISTS sales_t9").update
    saleTable.createTable(SqlDialect.SQLite).update
    sql"INSERT INTO sales_t9 (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
    sql"INSERT INTO sales_t9 (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
    sql"INSERT INTO sales_t9 (id, user_id, amount) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(5)})".update
    sql"INSERT INTO sales_t9 (id, user_id, amount) VALUES (${DbValue.DbInt(4)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(7)})".update
  }

  private def orderingSuite = suite("typed ordering")(
    test("orderBy joined alias renders qualified joined alias and executes") {
      transactor.connect {
        setupUsersRepos()
        val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel)
        val q     = qBase
          .select[(String, String)](qBase.col[User](_.name), qBase.col[Repo](_.name))
          .orderBy(qBase.colAt[Repo]("t1", _.name), SortOrder.Asc)
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        assertTrue(
          sqlStr ==
            "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users_t9\" AS t0 INNER JOIN \"repos_t9\" AS t1 ON t1.\"owner_id\" = t0.\"id\" ORDER BY t1.\"name\" ASC",
          rows == List(("alice", "r1"), ("bob", "r2"), ("alice", "r3"))
        )
      }
    },
    test("aggregate ORDER BY after groupBy renders exact SQL and executes with mixed directions") {
      transactor.connect {
        setupSales()
        val qBase = SqlQuery.from(saleTable)
        val q     = qBase
          .groupBy(qBase.col[Sale](_.userId))
          .select[(Int, Long)](qBase.col[Sale](_.userId), qBase.count(qBase.col[Sale](_.id)))
          .orderBy(qBase.count(qBase.col[Sale](_.id)), SortOrder.Desc)
          .orderBy(qBase.col[Sale](_.userId), SortOrder.Asc)
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        assertTrue(
          sqlStr ==
            "SELECT t0.\"user_id\" AS \"_1\", COUNT(t0.\"id\") AS \"_2\" FROM \"sales_t9\" AS t0 GROUP BY t0.\"user_id\" ORDER BY COUNT(t0.\"id\") DESC, t0.\"user_id\" ASC",
          rows == List((1, 3L), (2, 1L))
        )
      }
    },
    test("orderByMany with mixed directions renders exact SQL and executes roundtrip") {
      transactor.connect {
        setupUsersRepos()
        val qBase = SqlQuery.from(repoTable)
        val q     = qBase
          .select[String](qBase.col[Repo](_.name))
          .orderByMany(
            (qBase.col[Repo](_.ownerId), SortOrder.Asc),
            (qBase.col[Repo](_.name), SortOrder.Desc)
          )
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        assertTrue(
          sqlStr == "SELECT t0.\"name\" AS \"value\" FROM \"repos_t9\" AS t0 ORDER BY t0.\"owner_id\" ASC, t0.\"name\" DESC",
          rows == List("r3", "r1", "r2")
        )
      }
    },
    test("left-joined Option expression orderBy renders inner column and executes") {
      transactor.connect {
        // bob (id 2) has no repo so the left side stays None.
        Frag.literal("DROP TABLE IF EXISTS repos_t9").update
        Frag.literal("DROP TABLE IF EXISTS users_t9").update
        userTable.createTable(SqlDialect.SQLite).update
        repoTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO users_t9 (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
        sql"INSERT INTO users_t9 (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
        sql"INSERT INTO repos_t9 (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
        val leftQ = SqlQuery.from(userTable).leftJoin(userRepoRel)
        val q     = leftQ
          .select[(String, Option[String])](leftQ.col[User](_.name), leftQ.col[Repo](_.name))
          .orderBy(leftQ.col[Repo](_.name), SortOrder.Desc)
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        // NULL sorts last in DESC on SQLite.
        assertTrue(
          sqlStr ==
            "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users_t9\" AS t0 LEFT JOIN \"repos_t9\" AS t1 ON t1.\"owner_id\" = t0.\"id\" ORDER BY t1.\"name\" DESC",
          rows == List(("alice", Some("r1")), ("bob", None))
        )
      }
    },
    test("arithmetic expression orderBy renders exact SQL and executes") {
      transactor.connect {
        setupSales()
        val qBase = SqlQuery.from(saleTable)
        val q     = qBase
          .select[Int](qBase.col[Sale](_.amount))
          .orderBy(qBase.col[Sale](_.amount) + qBase.col[Sale](_.amount), SortOrder.Asc)
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        assertTrue(
          sqlStr == "SELECT t0.\"amount\" AS \"value\" FROM \"sales_t9\" AS t0 ORDER BY (t0.\"amount\" + t0.\"amount\") ASC",
          rows == List(5, 7, 10, 11)
        )
      }
    },
    test("scope-neutral literal orderBy is accepted (Nothing <: Scope)") {
      val qBase = SqlQuery.from(saleTable)
      val q     = qBase.orderBy(lit(1), SortOrder.Asc)
      val sql   = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
      assertTrue(sql == "SELECT t0.\"id\", t0.\"user_id\", t0.\"amount\" FROM \"sales_t9\" AS t0 ORDER BY ? ASC")
    }
  )

  private def lineageSuite = suite("scope-neutral first projection lineage")(
    test("countStar-first select keeps receiver scope for subsequent query operations") {
      transactor.connect {
        setupSales()
        val qBase        = SqlQuery.from(saleTable)
        val tq           = qBase.select[(Long, Int)](countStar, qBase.col[Sale](_.userId))
        val filtered     = tq.where(qBase.col[Sale](_.userId) === lit(1))
        val grouped      = tq.groupBy(qBase.col[Sale](_.userId))
        val ordered      = grouped.orderBy(qBase.col[Sale](_.userId), SortOrder.Desc)
        val sqlStr       = tq.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val filteredRows = filtered.run
        val groupedRows  = grouped.run
        val orderedRows  = ordered.run
        assertTrue(
          sqlStr == "SELECT COUNT(*) AS \"_1\", t0.\"user_id\" AS \"_2\" FROM \"sales_t9\" AS t0",
          filteredRows == List((3L, 1)),
          groupedRows == List((3L, 1), (1L, 2)),
          orderedRows == List((1L, 2), (3L, 1))
        )
      }
    },
    test("all-neutral select keeps receiver scope: countStar then q-scoped where/orderBy/groupBy") {
      transactor.connect {
        setupSales()
        val qBase = SqlQuery.from(saleTable)
        val tq    = qBase.select[Long](countStar)
        // Sc = qBase.Scope even though every projection expression is scope-neutral.
        val filtered     = tq.where(qBase.col[Sale](_.userId) === lit(1))
        val ordered      = tq.orderBy(qBase.col[Sale](_.userId), SortOrder.Asc)
        val grouped      = tq.groupBy(qBase.col[Sale](_.userId))
        val filteredRows = filtered.run
        val orderedRows  = ordered.run
        val groupedRows  = grouped.run
        assertTrue(
          filteredRows == List(3L),
          orderedRows == List(4L),
          groupedRows.sorted == List(1L, 3L)
        )
      }
    },
    test("orderByMany accepts mixed scope-neutral and query-scoped expressions") {
      val qBase = SqlQuery.from(saleTable)
      val q     = qBase
        .select[Int](qBase.col[Sale](_.amount))
        .orderByMany((lit(1), SortOrder.Asc), (qBase.col[Sale](_.amount), SortOrder.Desc))
      val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
      assertTrue(
        sqlStr == "SELECT t0.\"amount\" AS \"value\" FROM \"sales_t9\" AS t0 ORDER BY ? ASC, t0.\"amount\" DESC"
      )
    },
    test("query-bound-first select has identical usable lineage") {
      transactor.connect {
        setupSales()
        val qBase        = SqlQuery.from(saleTable)
        val tq           = qBase.select[(Int, Long)](qBase.col[Sale](_.userId), countStar)
        val filtered     = tq.where(qBase.col[Sale](_.userId) === lit(1))
        val grouped      = tq.groupBy(qBase.col[Sale](_.userId))
        val ordered      = grouped.orderBy(qBase.col[Sale](_.userId), SortOrder.Asc)
        val filteredRows = filtered.run
        val orderedRows  = ordered.run
        assertTrue(
          filteredRows == List((1, 3L)),
          orderedRows == List((1, 3L), (2, 1L))
        )
      }
    }
  )

  private def rejectionSuite: zio.test.Spec[Any, Nothing] = suite("cross-query and arity rejection")(
    test("cross-query orderBy expression fails at compile time with scope mismatch") {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { given Schema[User] = Schema.derived }
          val userTable: Table[User] = Table.derived[User]
          val q1 = SqlQuery.from(userTable)
          val q2 = SqlQuery.from(userTable)
          q1.orderBy(q2.col[User](_.name), SortOrder.Asc)
        }"""
      )
      assertTrue(
        errors.nonEmpty,
        errors.exists(_.message.contains("Scope")),
        errors.exists(_.message.contains("q1"))
      )
    },
    test("same-query orderBy expression compiles (positive control)") {
      val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { given Schema[User] = Schema.derived }
          val userTable: Table[User] = Table.derived[User]
          val q1 = SqlQuery.from(userTable)
          q1.orderBy(q1.col[User](_.name), SortOrder.Asc)
        }"""
      )
      assertTrue(errors.isEmpty)
    },
    test("cross-query orderByMany expression fails at compile time") {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { given Schema[User] = Schema.derived }
          val userTable: Table[User] = Table.derived[User]
          val q1 = SqlQuery.from(userTable)
          val q2 = SqlQuery.from(userTable)
          q1.orderByMany((lit(1), SortOrder.Asc), (q2.col[User](_.name), SortOrder.Desc))
        }"""
      )
      assertTrue(errors.exists(_.message.contains("Scope")))
    },
    test("type mismatch at position 9 gives exact compile error") {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.schema.Modifier
          import zio.blocks.sql.Table
          @Modifier.config("sql.table_name", "wide9")
          case class Wide9(c1: Int, c2: Int, c3: Int, c4: Int, c5: Int, c6: Int, c7: Int, c8: Int, c9: Int)
          object Wide9 { given Schema[Wide9] = Schema.derived }
          val wide9Table: Table[Wide9] = Table.derived[Wide9]
          val q = SqlQuery.from(wide9Table)
          q.select[(Int, Int, Int, Int, Int, Int, Int, Int, String)](
            q.col[Wide9](_.c1), q.col[Wide9](_.c2), q.col[Wide9](_.c3), q.col[Wide9](_.c4),
            q.col[Wide9](_.c5), q.col[Wide9](_.c6), q.col[Wide9](_.c7), q.col[Wide9](_.c8),
            q.col[Wide9](_.c1)
          )
        }"""
      )
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("type mismatch at position 9")))
    },
    test("type mismatch at position 22 gives exact compile error") {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.schema.Modifier
          import zio.blocks.sql.Table
          @Modifier.config("sql.table_name", "wide22")
          case class Wide22(
            c1: Int, c2: Int, c3: Int, c4: Int, c5: Int, c6: Int, c7: Int, c8: Int, c9: Int, c10: Int, c11: Int,
            c12: Int, c13: Int, c14: Int, c15: Int, c16: Int, c17: Int, c18: Int, c19: Int, c20: Int, c21: Int, c22: Int
          )
          object Wide22 { given Schema[Wide22] = Schema.derived }
          val wide22Table: Table[Wide22] = Table.derived[Wide22]
          val q = SqlQuery.from(wide22Table)
          q.select[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, String)](
            q.col[Wide22](_.c1), q.col[Wide22](_.c2), q.col[Wide22](_.c3), q.col[Wide22](_.c4),
            q.col[Wide22](_.c5), q.col[Wide22](_.c6), q.col[Wide22](_.c7), q.col[Wide22](_.c8),
            q.col[Wide22](_.c9), q.col[Wide22](_.c10), q.col[Wide22](_.c11), q.col[Wide22](_.c12),
            q.col[Wide22](_.c13), q.col[Wide22](_.c14), q.col[Wide22](_.c15), q.col[Wide22](_.c16),
            q.col[Wide22](_.c17), q.col[Wide22](_.c18), q.col[Wide22](_.c19), q.col[Wide22](_.c20),
            q.col[Wide22](_.c21), q.col[Wide22](_.c1)
          )
        }"""
      )
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("type mismatch at position 22")))
    },
    test("projection arity mismatch stays compile-time rejected") {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.schema.Modifier
          import zio.blocks.sql.Table
          @Modifier.config("sql.table_name", "wide9")
          case class Wide9(c1: Int, c2: Int, c3: Int, c4: Int, c5: Int, c6: Int, c7: Int, c8: Int, c9: Int)
          object Wide9 { given Schema[Wide9] = Schema.derived }
          val wide9Table: Table[Wide9] = Table.derived[Wide9]
          val q = SqlQuery.from(wide9Table)
          q.select[(Int, Int)](
            q.col[Wide9](_.c1), q.col[Wide9](_.c2), q.col[Wide9](_.c3), q.col[Wide9](_.c4),
            q.col[Wide9](_.c5), q.col[Wide9](_.c6), q.col[Wide9](_.c7), q.col[Wide9](_.c8),
            q.col[Wide9](_.c9)
          )
        }"""
      )
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("projection arity")))
    }
  )

  private def wide9Tuple: (Int, Int, Int, Int, Int, Int, Int, Int, Int) = (1, 2, 3, 4, 5, 6, 7, 8, 9)

  private def wide22Tuple
    : (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) =
    (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

  private def projectionSuite = suite("high-arity projections")(
    test("Tuple9 projection renders exact SQL and executes roundtrip") {
      transactor.connect {
        Frag.literal("DROP TABLE IF EXISTS wide9").update
        wide9Table.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO wide9 (c1, c2, c3, c4, c5, c6, c7, c8, c9) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(3)}, ${DbValue.DbInt(4)}, ${DbValue.DbInt(5)}, ${DbValue.DbInt(6)}, ${DbValue.DbInt(7)}, ${DbValue.DbInt(8)}, ${DbValue.DbInt(9)})".update
        val qBase = SqlQuery.from(wide9Table)
        val q     = qBase.select[(Int, Int, Int, Int, Int, Int, Int, Int, Int)](
          qBase.col[Wide9](_.c1),
          qBase.col[Wide9](_.c2),
          qBase.col[Wide9](_.c3),
          qBase.col[Wide9](_.c4),
          qBase.col[Wide9](_.c5),
          qBase.col[Wide9](_.c6),
          qBase.col[Wide9](_.c7),
          qBase.col[Wide9](_.c8),
          qBase.col[Wide9](_.c9)
        )
        val cols     = (1 to 9).map(i => s"""t0."c$i" AS "_$i"""").mkString(", ")
        val expected = s"SELECT $cols FROM \"wide9\" AS t0"
        val rows     = q.run
        assertTrue(
          q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) == expected,
          rows == List(wide9Tuple)
        )
      }
    },
    test("Tuple22 projection renders exact SQL and executes roundtrip") {
      transactor.connect {
        Frag.literal("DROP TABLE IF EXISTS wide22").update
        wide22Table.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO wide22 (c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(3)}, ${DbValue.DbInt(4)}, ${DbValue.DbInt(5)}, ${DbValue.DbInt(6)}, ${DbValue.DbInt(7)}, ${DbValue.DbInt(8)}, ${DbValue.DbInt(9)}, ${DbValue.DbInt(10)}, ${DbValue.DbInt(11)}, ${DbValue.DbInt(12)}, ${DbValue.DbInt(13)}, ${DbValue.DbInt(14)}, ${DbValue.DbInt(15)}, ${DbValue.DbInt(16)}, ${DbValue.DbInt(17)}, ${DbValue.DbInt(18)}, ${DbValue.DbInt(19)}, ${DbValue.DbInt(20)}, ${DbValue.DbInt(21)}, ${DbValue.DbInt(22)})".update
        val qBase = SqlQuery.from(wide22Table)
        val q     = qBase.select[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
        ](
          qBase.col[Wide22](_.c1),
          qBase.col[Wide22](_.c2),
          qBase.col[Wide22](_.c3),
          qBase.col[Wide22](_.c4),
          qBase.col[Wide22](_.c5),
          qBase.col[Wide22](_.c6),
          qBase.col[Wide22](_.c7),
          qBase.col[Wide22](_.c8),
          qBase.col[Wide22](_.c9),
          qBase.col[Wide22](_.c10),
          qBase.col[Wide22](_.c11),
          qBase.col[Wide22](_.c12),
          qBase.col[Wide22](_.c13),
          qBase.col[Wide22](_.c14),
          qBase.col[Wide22](_.c15),
          qBase.col[Wide22](_.c16),
          qBase.col[Wide22](_.c17),
          qBase.col[Wide22](_.c18),
          qBase.col[Wide22](_.c19),
          qBase.col[Wide22](_.c20),
          qBase.col[Wide22](_.c21),
          qBase.col[Wide22](_.c22)
        )
        val cols     = (1 to 22).map(i => s"""t0."c$i" AS "_$i"""").mkString(", ")
        val expected = s"SELECT $cols FROM \"wide22\" AS t0"
        val rows     = q.run
        assertTrue(
          q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) == expected,
          rows == List(wide22Tuple)
        )
      }
    },
    test("*:-shaped tuple projection compiles, renders exact SQL, and executes") {
      transactor.connect {
        Frag.literal("DROP TABLE IF EXISTS triples").update
        tripleTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO triples (a, b, c) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("x")}, ${DbValue.DbInt(3)})".update
        val qBase = SqlQuery.from(tripleTable)
        val q     = qBase.select[Int *: String *: Int *: EmptyTuple](
          qBase.col[Triple](_.a),
          qBase.col[Triple](_.b),
          qBase.col[Triple](_.c)
        )
        val rows = q.run
        assertTrue(
          q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"a\" AS \"_1\", t0.\"b\" AS \"_2\", t0.\"c\" AS \"_3\" FROM \"triples\" AS t0",
          rows == List(1 *: "x" *: 3 *: EmptyTuple)
        )
      }
    },
    test("arity-23 projection (beyond Tuple22) compiles and executes") {
      transactor.connect {
        Frag.literal("DROP TABLE IF EXISTS wide22").update
        wide22Table.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO wide22 (c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(3)}, ${DbValue.DbInt(4)}, ${DbValue.DbInt(5)}, ${DbValue.DbInt(6)}, ${DbValue.DbInt(7)}, ${DbValue.DbInt(8)}, ${DbValue.DbInt(9)}, ${DbValue.DbInt(10)}, ${DbValue.DbInt(11)}, ${DbValue.DbInt(12)}, ${DbValue.DbInt(13)}, ${DbValue.DbInt(14)}, ${DbValue.DbInt(15)}, ${DbValue.DbInt(16)}, ${DbValue.DbInt(17)}, ${DbValue.DbInt(18)}, ${DbValue.DbInt(19)}, ${DbValue.DbInt(20)}, ${DbValue.DbInt(21)}, ${DbValue.DbInt(22)})".update
        val qBase = SqlQuery.from(wide22Table)
        val q     = qBase.select[
          (
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int
          )
        ](
          qBase.col[Wide22](_.c1),
          qBase.col[Wide22](_.c2),
          qBase.col[Wide22](_.c3),
          qBase.col[Wide22](_.c4),
          qBase.col[Wide22](_.c5),
          qBase.col[Wide22](_.c6),
          qBase.col[Wide22](_.c7),
          qBase.col[Wide22](_.c8),
          qBase.col[Wide22](_.c9),
          qBase.col[Wide22](_.c10),
          qBase.col[Wide22](_.c11),
          qBase.col[Wide22](_.c12),
          qBase.col[Wide22](_.c13),
          qBase.col[Wide22](_.c14),
          qBase.col[Wide22](_.c15),
          qBase.col[Wide22](_.c16),
          qBase.col[Wide22](_.c17),
          qBase.col[Wide22](_.c18),
          qBase.col[Wide22](_.c19),
          qBase.col[Wide22](_.c20),
          qBase.col[Wide22](_.c21),
          qBase.col[Wide22](_.c22),
          lit(1)
        )
        val cols     = (1 to 22).map(i => s"""t0."c$i" AS "_$i"""").mkString(", ")
        val expected = s"SELECT $cols, ? AS \"_23\" FROM \"wide22\" AS t0"
        val row      =
          1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: 22 *: 1 *: EmptyTuple
        val rows = q.run
        assertTrue(
          q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) == expected,
          rows == List(row)
        )
      }
    }
  )

  def spec = suite("Task9IntegrationSpec")(
    orderingSuite,
    lineageSuite,
    rejectionSuite,
    projectionSuite
  ) @@ TestAspect.sequential
}
