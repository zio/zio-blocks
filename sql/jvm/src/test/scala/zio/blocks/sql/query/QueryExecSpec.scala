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

import zio.blocks.chunk.Chunk
import zio.blocks.maybe.Maybe
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.{
  DbCodec,
  DbCon,
  DbConnection,
  DbTx,
  DbValue,
  Frag,
  JdbcConnection,
  JdbcTransactor,
  SqlDialect,
  SqlLogger,
  Table,
  sql
}
import zio.test.*

object QueryExecSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  @Modifier.config("sql.table_name", "users")
  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  @Modifier.config("sql.table_name", "repos")
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  @Modifier.config("sql.table_name", "repos2")
  case class Repo2(id: Int, ownerId: Int, title: String)
  object Repo2 { given Schema[Repo2] = Schema.derived }

  @Modifier.config("sql.inline_fields", "true")
  case class UserRepoRow(userName: String, repoName: String)
  object UserRepoRow { given Schema[UserRepoRow] = Schema.derived }

  case class Triple(a: Int, b: String, c: Int)
  object Triple { given Schema[Triple] = Schema.derived }

  case class Employee(id: Int, name: String, managerId: Option[Int])
  object Employee { given Schema[Employee] = Schema.derived }

  @Modifier.config("sql.table_name", "w8s")
  case class W8(v1: Int, v2: Int, v3: Int, v4: Int, v5: Int, v6: Int, v7: Int, v8: Int)
  object W8 { given Schema[W8] = Schema.derived }

  case class Inner(street: String, city: String)
  object Inner { given Schema[Inner] = Schema.derived }

  @Modifier.config("sql.table_name", "outers")
  case class Outer(@Modifier.config("sql.inline", "true") inner: Inner, label: String)
  object Outer { given Schema[Outer] = Schema.derived }

  val userTable     = Table.derived[User]
  val repoTable     = Table.derived[Repo]
  val employeeTable = Table.derived[Employee]
  val w8Table       = Table.derived[W8]
  val outerTable    = Table.derived[Outer]

  val userRepoRel     = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
  val employeeSelfRel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)

  private val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  def spec = suite("QueryExecSpec")(
    suite("compile-time type safety")(
      test("swapped tuple types fail compilation") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.sql.{Table, DbCodec}
            import zio.blocks.schema.Schema
            import zio.blocks.schema.Modifier
            @Modifier.config("sql.table_name", "users")
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            @Modifier.config("sql.table_name", "repos")
            case class Repo(id: Int, ownerId: Int, name: String)
            object Repo { given Schema[Repo] = Schema.derived }
            val userTable = Table.derived[User]
            val repoTable = Table.derived[Repo]
            val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
            val qBase = SqlQuery.from(userTable).innerJoin(rel)
            val q     = qBase.select[(Int,String)](qBase.col[User](_.name), qBase.col[User](_.id))
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("correct tuple types compile") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.sql.{Table, DbCodec}
              import zio.blocks.schema.Schema
              import zio.blocks.schema.Modifier
              @Modifier.config("sql.table_name", "users")
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              @Modifier.config("sql.table_name", "repos")
              case class Repo(id: Int, ownerId: Int, name: String)
              object Repo { given Schema[Repo] = Schema.derived }
              val userTable = Table.derived[User]
              val repoTable = Table.derived[Repo]
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val qBase = SqlQuery.from(userTable).innerJoin(rel)
              val q     = qBase.select[(Int,String)](qBase.col[User](_.id), qBase.col[Repo](_.name))
            }"""
          )
        )
      }
    ),
    suite("two-table inner join tuples")(
      test("inner join decodes (Int,String) tuples") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          Frag.literal("DROP TABLE IF EXISTS repos").update
          userTable.createTable(SqlDialect.SQLite).update
          repoTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
          sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("repo-a")})".update
          sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(11)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("repo-b")})".update
          val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel)
          val q     = qBase.select[(Int, String)](qBase.col[User](_.id), qBase.col[Repo](_.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run
          assertTrue(
            sqlStr == "SELECT t0.\"id\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users\" AS t0 INNER JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\"",
            rows.toSet == Set((1, "repo-a"), (2, "repo-b"))
          )
        }
      },
      test("inner join decodes arity 3 tuple (Int,String,Int)") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          Frag.literal("DROP TABLE IF EXISTS repos").update
          userTable.createTable(SqlDialect.SQLite).update
          repoTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
          sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("ra")})".update
          val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel)
          val q     = qBase.select[(Int, String, Int)](qBase.col[User](_.id), qBase.col[Repo](_.name), qBase.col[Repo](_.id))
          val rows = q.run
          assertTrue(rows == List((1, "ra", 10)))
        }
      },
      test("inner join decodes arity 8 tuple") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS w8s").update
          w8Table.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO w8s (v1, v2, v3, v4, v5, v6, v7, v8) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(3)}, ${DbValue.DbInt(4)}, ${DbValue.DbInt(5)}, ${DbValue.DbInt(6)}, ${DbValue.DbInt(7)}, ${DbValue.DbInt(8)})".update
          val q = SqlQuery.from(w8Table)
          val qq = q.select[(Int, Int, Int, Int, Int, Int, Int, Int)](
            q.col[W8](_.v1),
            q.col[W8](_.v2),
            q.col[W8](_.v3),
            q.col[W8](_.v4),
            q.col[W8](_.v5),
            q.col[W8](_.v6),
            q.col[W8](_.v7),
            q.col[W8](_.v8)
          )
          val sqlStr = qq.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = qq.run
          assertTrue(
            sqlStr == "SELECT t0.\"v1\" AS \"_1\", t0.\"v2\" AS \"_2\", t0.\"v3\" AS \"_3\", t0.\"v4\" AS \"_4\", t0.\"v5\" AS \"_5\", t0.\"v6\" AS \"_6\", t0.\"v7\" AS \"_7\", t0.\"v8\" AS \"_8\" FROM \"w8s\" AS t0",
            rows == List((1, 2, 3, 4, 5, 6, 7, 8))
          )
        }
      },
      test("projection emits only selected columns, not all joined columns") {
        val qProjBase = SqlQuery.from(userTable).innerJoin(userRepoRel)
        val q         = qProjBase.select[(Int, String)](qProjBase.col[User](_.id), qProjBase.col[Repo](_.name))
        val sql      = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected =
          "SELECT t0.\"id\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users\" AS t0 INNER JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        assertTrue(sql == expected)
      }
    ),
    suite("LEFT JOIN nullable-side projection")(
      test("left join decodes Option side as None and preserves rows") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          Frag.literal("DROP TABLE IF EXISTS repos").update
          userTable.createTable(SqlDialect.SQLite).update
          repoTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
          sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("repo-a")})".update
          val leftQ  = SqlQuery.from(userTable).leftJoin(userRepoRel)
          val q      = leftQ.select[(Int, Option[String])](leftQ.col[User](_.id), leftQ.col[Repo](_.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run.sortBy(_._1)
          assertTrue(
            sqlStr == "SELECT t0.\"id\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users\" AS t0 LEFT JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\"",
            rows == List((1, Some("repo-a")), (2, None)),
            rows.length == 2
          )
        }
      },
      test("left join all-null nullable side preserves all rows as None") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          Frag.literal("DROP TABLE IF EXISTS repos").update
          userTable.createTable(SqlDialect.SQLite).update
          repoTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")})".update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("b")})".update
          val leftQ  = SqlQuery.from(userTable).leftJoin(userRepoRel)
          val q      = leftQ.select[(String, Option[String])](leftQ.col[User](_.name), leftQ.col[Repo](_.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run.sortBy(_._1)
          assertTrue(
            sqlStr == "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users\" AS t0 LEFT JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\"",
            rows == List(("a", None), ("b", None))
          )
        }
      }
    ),
    suite("derived record with inline flattening")(
      test("select into flat case class decodes") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          Frag.literal("DROP TABLE IF EXISTS repos").update
          userTable.createTable(SqlDialect.SQLite).update
          repoTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
          sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
          val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel)
          val q     = qBase.select[UserRepoRow](qBase.col[User](_.name), qBase.col[Repo](_.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run
          assertTrue(
            sqlStr == "SELECT t0.\"name\" AS \"user_name\", t1.\"name\" AS \"repo_name\" FROM \"users\" AS t0 INNER JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\"",
            rows == List(UserRepoRow("alice", "r1"))
          )
        }
      },
      test("select into nested inline record decodes") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS outers").update
          outerTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO outers (inner_street, inner_city, label) VALUES (${DbValue.DbString("Main St")}, ${DbValue.DbString("NYC")}, ${DbValue.DbString("office")})".update
          val qBase = SqlQuery.from(outerTable)
          val q     = qBase.select[Outer](qBase.col[Outer](_.inner.street), qBase.col[Outer](_.inner.city), qBase.col[Outer](_.label))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run
          assertTrue(
            sqlStr == "SELECT t0.\"inner_street\" AS \"inner_street\", t0.\"inner_city\" AS \"inner_city\", t0.\"label\" AS \"label\" FROM \"outers\" AS t0",
            rows == List(Outer(Inner("Main St", "NYC"), "office"))
          )
        }
      }
    ),
    suite("execution methods delegate to Frag")(
      test("queryOne returns single row") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          userTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(5)}, ${DbValue.DbString("carol")})".update
          val qBase  = SqlQuery.from(userTable)
          val qBaseW = qBase.where(qBase.col[User](_.id) === lit(5))
          val q      = qBaseW.select[String](qBaseW.col[User](_.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val result = q.queryOne
          assertTrue(
            sqlStr == "SELECT t0.\"name\" AS \"value\" FROM \"users\" AS t0 WHERE t0.\"id\" = ?",
            result == Maybe("carol")
          )
        }
      },
      test("queryOne empty returns Maybe absent") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          userTable.createTable(SqlDialect.SQLite).update
          val qBase  = SqlQuery.from(userTable)
          val qBaseW = qBase.where(qBase.col[User](_.id) === lit(999))
          val q      = qBaseW.select[String](qBaseW.col[User](_.name))
          val result = q.queryOne
          assertTrue(result.isEmpty)
        }
      },
      test("queryLimit returns at most N rows") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          userTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")})".update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("b")})".update
          sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbString("c")})".update
          val qBase   = SqlQuery.from(userTable)
          val q       = qBase.select[Int](qBase.col[User](_.id))
          val limited = q.queryLimit(2)
          assertTrue(limited.length == 2)
        }
      },
      test("queryStream and queryChunked deliver all rows") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS users").update
          userTable.createTable(SqlDialect.SQLite).update
          (1 to 5).foreach(i =>
            sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(i)}, ${DbValue.DbString(s"u$i")})".update
          )
          val qBase      = SqlQuery.from(userTable)
          val q          = qBase.select[Int](qBase.col[User](_.id))
          val viaRun     = q.run.sorted
          val viaStream  = q.queryStream.runCollect.map(_.flatMap(_.toList).sorted)
          val viaChunked = q.queryChunked(2).runCollect.map(_.flatMap(_.toList).sorted)
          assertTrue(
            viaRun == List(1, 2, 3, 4, 5),
            viaStream.isRight && viaStream.toOption.get == viaRun,
            viaChunked.isRight && viaChunked.toOption.get == viaRun
          )
        }
      },
      test("ambient DbTx behaves like DbCon") {
        val (tx, conn) = {
          val c = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
          val t = new JdbcTransactor(() => c, SqlDialect.SQLite) {
            override def connect[A](f: DbCon ?=> A): A = {
              val dbConn       = new JdbcConnection(c, SqlDialect.SQLite)
              given con: DbCon = new DbCon {
                val connection: DbConnection = dbConn
                val dialect: SqlDialect      = SqlDialect.SQLite
                val logger: SqlLogger        = SqlLogger.noop
              }
              f
            }
            override def transact[A](f: DbTx ?=> A): A = {
              val dbConn = new JdbcConnection(c, SqlDialect.SQLite)
              c.setAutoCommit(false)
              try {
                given tx: DbTx = new DbTx {
                  val connection: DbConnection = dbConn
                  val dialect: SqlDialect      = SqlDialect.SQLite
                  val logger: SqlLogger        = SqlLogger.noop
                }
                val r = f
                c.commit(); r
              } catch { case e: Throwable => c.rollback(); throw e }
              finally c.setAutoCommit(true)
            }
          }
          (t, c)
        }
        try {
          tx.connect {
            userTable.createTable(SqlDialect.SQLite).update
            sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")})".update
          }
          val rows = tx.transact {
            {
              val qRunBase = SqlQuery.from(userTable)
              qRunBase.select[Int](qRunBase.col[User](_.id)).run
            }
          }
          assertTrue(rows == List(1))
        } finally conn.close()
      }
    ),
    suite("adversarial")(
      test("empty projection throws") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.schema.Modifier
            import zio.blocks.sql.Table
            @Modifier.config("sql.table_name", "users")
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable = Table.derived[User]
            val q = SqlQuery.from(userTable).select[String]()
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("self-join projection via colAt is alias-qualified") {
        transactor.connect {
          Frag.literal("DROP TABLE IF EXISTS employee").update
          employeeTable.createTable(SqlDialect.SQLite).update
          sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("boss")}, ${DbValue.DbNull})".update
          sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("alice")}, ${DbValue.DbInt(1)})".update
          val qBase = SqlQuery.from(employeeTable).innerJoin(employeeSelfRel)
          val q     = qBase.select[(String, String)](qBase.colAt[Employee]("t0", _.name), qBase.colAt[Employee]("t1", _.name))
          val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
          val rows   = q.run.sortBy(_._1)
          assertTrue(
            sqlStr == "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\"",
            rows.contains(("alice", "boss"))
          )
        }
      },
      test("repeated executions produce identical SQL and fresh params") {
        val qBase   = SqlQuery.from(userTable)
        val qWhere  = qBase.where(qBase.col[User](_.id) > lit(1))
        val q       = qWhere.select[Int](qWhere.col[User](_.id))
        val s1       = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val s2       = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected = "SELECT t0.\"id\" AS \"value\" FROM \"users\" AS t0 WHERE t0.\"id\" > ?"
        assertTrue(s1 == expected, s2 == expected)
      }
    )
  )
}
