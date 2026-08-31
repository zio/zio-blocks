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
import zio.blocks.sql.{
  DbCon,
  DbConnection,
  DbValue,
  Frag,
  JdbcConnection,
  JdbcTransactor,
  PgSupport,
  SqlDialect,
  Table,
  sql
}
import zio.test.*

object QueryIntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  @Modifier.config("sql.table_name", "users")
  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  @Modifier.config("sql.table_name", "repos")
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  @Modifier.config("sql.table_name", "stars")
  case class Star(id: Int, repoId: Int, userId: Int)
  object Star { given Schema[Star] = Schema.derived }

  case class Employee(id: Int, name: String, managerId: Option[Int])
  object Employee { given Schema[Employee] = Schema.derived }

  @Modifier.config("sql.table_name", "sales")
  case class Sale(id: Int, userId: Int, amount: Int)
  object Sale { given Schema[Sale] = Schema.derived }

  val userTable     = Table.derived[User]
  val repoTable     = Table.derived[Repo]
  val starTable     = Table.derived[Star]
  val employeeTable = Table.derived[Employee]
  val saleTable     = Table.derived[Sale]

  val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
  val repoStarRel = Rel.manyToOne(starTable, _.repoId, repoTable, _.id)
  val empSelfRel  = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)

  private val sqliteTransactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  private def sqliteSuite = suite("sqlite")(
    test("3-table join chain renders exact SQL and decodes rows") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS stars").update
        Frag.literal("DROP TABLE IF EXISTS repos").update
        Frag.literal("DROP TABLE IF EXISTS users").update
        userTable.createTable(SqlDialect.SQLite).update
        repoTable.createTable(SqlDialect.SQLite).update
        starTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
        sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
        sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
        sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(20)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r2")})".update
        sql"INSERT INTO stars (id, repo_id, user_id) VALUES (${DbValue.DbInt(100)}, ${DbValue.DbInt(10)}, ${DbValue.DbInt(2)})".update
        sql"INSERT INTO stars (id, repo_id, user_id) VALUES (${DbValue.DbInt(101)}, ${DbValue.DbInt(20)}, ${DbValue.DbInt(1)})".update
        val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel).innerJoin(repoStarRel)
        val q     =
          qBase.select[(String, String, Int)](qBase.col[User](_.name), qBase.col[Repo](_.name), qBase.col[Star](_.id))
        val sqlStr   = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected =
          "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\", t2.\"id\" AS \"_3\" FROM \"users\" AS t0 INNER JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\" INNER JOIN \"stars\" AS t2 ON t2.\"repo_id\" = t1.\"id\""
        val rows = q.run.sortBy(_._3)
        assertTrue(
          sqlStr == expected,
          rows == List(("alice", "r1", 100), ("bob", "r2", 101))
        )
      }
    },
    test("self-join roundtrip renders exact alias-qualified SQL and decodes rows") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS employee").update
        employeeTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("boss")}, ${DbValue.DbNull})".update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("alice")}, ${DbValue.DbInt(1)})".update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbString("carol")}, ${DbValue.DbInt(1)})".update
        val qBase    = SqlQuery.from(employeeTable).innerJoin(empSelfRel)
        val q        = qBase.select[(String, String)](qBase.colAt[Employee]("t0", _.name), qBase.colAt[Employee]("t1", _.name))
        val sqlStr   = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected =
          "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\""
        val rows = q.run.sortBy(_._1)
        assertTrue(
          sqlStr == expected,
          rows == List(("alice", "boss"), ("carol", "boss"))
        )
      }
    },
    test("chained self-join t1->t2 renders exact SQL and decodes hierarchy via SQLite") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS employee").update
        employeeTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("boss")}, ${DbValue.DbNull})".update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("mgr")}, ${DbValue.DbInt(1)})".update
        sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbString("worker")}, ${DbValue.DbInt(2)})".update
        val qBase = SqlQuery.from(employeeTable).innerJoin(empSelfRel).innerJoin(empSelfRel)
        val q     = qBase.select[(String, String, String)](
          qBase.colAt[Employee]("t0", _.name),
          qBase.colAt[Employee]("t1", _.name),
          qBase.colAt[Employee]("t2", _.name)
        )
        val sqlStr   = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected =
          "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\", t2.\"name\" AS \"_3\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\" INNER JOIN \"employee\" AS t2 ON t1.\"manager_id\" = t2.\"id\""
        val rows = q.run
        assertTrue(
          sqlStr == expected,
          sqlStr.contains("t1.\"manager_id\" = t2.\"id\""),
          !sqlStr.contains("t0.\"manager_id\" = t2.\"id\""),
          rows == List(("worker", "mgr", "boss"))
        )
      }
    },
    test("groupBy+having aggregate renders exact SQL and decodes aggregate rows") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS repos").update
        repoTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
        sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r2")})".update
        sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r3")})".update
        val qBase = SqlQuery.from(repoTable)
        val q     = qBase
          .groupBy(qBase.col[Repo](_.ownerId))
          .having(qBase.count(qBase.col[Repo](_.id)) > lit(1L))
          .select[(Int, Long)](qBase.col[Repo](_.ownerId), qBase.count(qBase.col[Repo](_.id)))
        val sqlStr   = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val expected =
          "SELECT t0.\"owner_id\" AS \"_1\", COUNT(t0.\"id\") AS \"_2\" FROM \"repos\" AS t0 GROUP BY t0.\"owner_id\" HAVING COUNT(t0.\"id\") > ?"
        val frag = q.toFrag(SqlDialect.SQLite)
        val rows = q.run
        assertTrue(
          sqlStr == expected,
          frag.sql(SqlDialect.SQLite).count(_ == '?') == frag.params.size,
          frag.params == IndexedSeq(DbValue.DbLong(1L)),
          rows == List((1, 2L))
        )
      }
    },
    test("aggregate sum/min/max/avg renders valid SQLite and decodes via typed path") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales").update
        saleTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO sales (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
        sql"INSERT INTO sales (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
        sql"INSERT INTO sales (id, user_id, amount) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbInt(2)}, ${DbValue.DbInt(5)})".update
        val qCountQ = SqlQuery.from(saleTable)
        val qCount  = qCountQ
          .groupBy(qCountQ.col[Sale](_.userId))
          .having(qCountQ.count(qCountQ.col[Sale](_.id)) > lit(1L))
          .select[(Int, Long)](qCountQ.col[Sale](_.userId), qCountQ.count(qCountQ.col[Sale](_.id)))
        val qSumQ = SqlQuery.from(saleTable)
        val qSum  = qSumQ
          .groupBy(qSumQ.col[Sale](_.userId))
          .having(qSumQ.sum(qSumQ.col[Sale](_.amount)) > lit(15L))
          .select[(Int, Option[Long])](qSumQ.col[Sale](_.userId), qSumQ.sum(qSumQ.col[Sale](_.amount)))
        val qAvgQ = SqlQuery.from(saleTable)
        val qAvg  = qAvgQ
          .groupBy(qAvgQ.col[Sale](_.userId))
          .having(qAvgQ.avg(qAvgQ.col[Sale](_.amount)) > lit(BigDecimal(10.0)))
          .select[(Int, Option[BigDecimal])](qAvgQ.col[Sale](_.userId), qAvgQ.avg(qAvgQ.col[Sale](_.amount)))
        val qMinQ = SqlQuery.from(saleTable)
        val qMin  = qMinQ
          .groupBy(qMinQ.col[Sale](_.userId))
          .having(qMinQ.min(qMinQ.col[Sale](_.amount)) > lit(5))
          .select[(Int, Option[Int])](qMinQ.col[Sale](_.userId), qMinQ.min(qMinQ.col[Sale](_.amount)))
        val qMaxQ = SqlQuery.from(saleTable)
        val qMax  = qMaxQ
          .groupBy(qMaxQ.col[Sale](_.userId))
          .having(qMaxQ.max(qMaxQ.col[Sale](_.amount)) > lit(10))
          .select[(Int, Option[Int])](qMaxQ.col[Sale](_.userId), qMaxQ.max(qMaxQ.col[Sale](_.amount)))
        val countRows = qCount.run
        val sumRows   = qSum.run.sortBy(_._1)
        val avgRows   = qAvg.run.sortBy(_._1)
        val minRows   = qMin.run.sortBy(_._1)
        val maxRows   = qMax.run.sortBy(_._1)
        assertTrue(
          qCount.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"user_id\" AS \"_1\", COUNT(t0.\"id\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING COUNT(t0.\"id\") > ?",
          qSum.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"user_id\" AS \"_1\", SUM(t0.\"amount\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING SUM(t0.\"amount\") > ?",
          qAvg.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"user_id\" AS \"_1\", AVG(t0.\"amount\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING AVG(t0.\"amount\") > ?",
          qMin.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"user_id\" AS \"_1\", MIN(t0.\"amount\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING MIN(t0.\"amount\") > ?",
          qMax.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite) ==
            "SELECT t0.\"user_id\" AS \"_1\", MAX(t0.\"amount\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING MAX(t0.\"amount\") > ?",
          countRows == List((1, 2L)),
          sumRows == List((1, Some(21L))),
          avgRows == List((1, Some(BigDecimal(10.5)))),
          minRows == List((1, Some(10))),
          maxRows == List((1, Some(11)))
        )
      }
    },
    test("avg over integral columns decodes fractional Double without truncation") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales").update
        saleTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO sales (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
        sql"INSERT INTO sales (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
        val qBase = SqlQuery.from(saleTable)
        val q     = qBase
          .groupBy(qBase.col[Sale](_.userId))
          .having(qBase.avg(qBase.col[Sale](_.amount)) > lit(BigDecimal(10.0)))
          .select[(Int, Option[BigDecimal])](qBase.col[Sale](_.userId), qBase.avg(qBase.col[Sale](_.amount)))
        val sqlStr = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val rows   = q.run
        assertTrue(
          sqlStr ==
            "SELECT t0.\"user_id\" AS \"_1\", AVG(t0.\"amount\") AS \"_2\" FROM \"sales\" AS t0 GROUP BY t0.\"user_id\" HAVING AVG(t0.\"amount\") > ?",
          rows == List((1, Some(BigDecimal(10.5))))
        )
      }
    }
  )

  private def pgSuite =
    if (!PgSupport.pgAvailable)
      suite("postgres")(
        test("SKIPPED (env unavailable) - PostgreSQL not available - set PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE") {
          assertTrue(true)
        }
      )
    else
      suite("postgres")(
        test("3-table join chain on PostgreSQL") {
          val tx = PgSupport.pgTransactor()
          try {
            tx.connect {
              Frag.literal("DROP TABLE IF EXISTS stars CASCADE").update
              Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update
              Frag.literal("DROP TABLE IF EXISTS users CASCADE").update
              userTable.createTable(SqlDialect.PostgreSQL).update
              repoTable.createTable(SqlDialect.PostgreSQL).update
              starTable.createTable(SqlDialect.PostgreSQL).update
            }
            try {
              val (sqlStr, rows) = tx.connect {
                sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
                sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
                sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
                sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(20)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r2")})".update
                sql"INSERT INTO stars (id, repo_id, user_id) VALUES (${DbValue.DbInt(100)}, ${DbValue.DbInt(10)}, ${DbValue.DbInt(2)})".update
                sql"INSERT INTO stars (id, repo_id, user_id) VALUES (${DbValue.DbInt(101)}, ${DbValue.DbInt(20)}, ${DbValue.DbInt(1)})".update
                val qBase = SqlQuery.from(userTable).innerJoin(userRepoRel).innerJoin(repoStarRel)
                val q     = qBase.select[(String, String, Int)](
                  qBase.col[User](_.name),
                  qBase.col[Repo](_.name),
                  qBase.col[Star](_.id)
                )
                (q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL), q.run.sortBy(_._3))
              }
              val expected =
                "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\", t2.\"id\" AS \"_3\" FROM \"users\" AS t0 INNER JOIN \"repos\" AS t1 ON t1.\"owner_id\" = t0.\"id\" INNER JOIN \"stars\" AS t2 ON t2.\"repo_id\" = t1.\"id\""
              assertTrue(
                sqlStr == expected,
                rows == List(("alice", "r1", 100), ("bob", "r2", 101))
              )
            } finally {
              try
                tx.connect {
                  Frag.literal("DROP TABLE IF EXISTS stars CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS users CASCADE").update
                }
              catch { case _: Throwable => () }
            }
          } catch {
            case e: Throwable =>
              try
                tx.connect {
                  Frag.literal("DROP TABLE IF EXISTS stars CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS users CASCADE").update
                }
              catch { case _: Throwable => () }
              throw new RuntimeException(s"PG 3-chain failed: ${e.getMessage}", e)
          }
        },
        test("self-join on PostgreSQL") {
          val tx = PgSupport.pgTransactor()
          try {
            tx.connect {
              Frag.literal("DROP TABLE IF EXISTS employee CASCADE").update
              employeeTable.createTable(SqlDialect.PostgreSQL).update
            }
            try {
              val (sqlStr, rows) = tx.connect {
                sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("boss")}, ${DbValue.DbNull})".update
                sql"INSERT INTO employee (id, name, manager_id) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("alice")}, ${DbValue.DbInt(1)})".update
                val qBase = SqlQuery.from(employeeTable).innerJoin(empSelfRel)
                val q     = qBase
                  .select[(String, String)](qBase.colAt[Employee]("t0", _.name), qBase.colAt[Employee]("t1", _.name))
                (q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL), q.run.sortBy(_._1))
              }
              val expected =
                "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\""
              assertTrue(sqlStr == expected, rows == List(("alice", "boss")))
            } finally {
              try tx.connect(Frag.literal("DROP TABLE IF EXISTS employee CASCADE").update)
              catch { case _: Throwable => () }
            }
          } catch {
            case e: Throwable =>
              try tx.connect(Frag.literal("DROP TABLE IF EXISTS employee CASCADE").update)
              catch { case _: Throwable => () }
              throw new RuntimeException(s"PG self-join failed: ${e.getMessage}", e)
          }
        },
        test("groupBy+having aggregate on PostgreSQL") {
          val tx = PgSupport.pgTransactor()
          try {
            tx.connect {
              Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update
              repoTable.createTable(SqlDialect.PostgreSQL).update
            }
            try {
              val (sqlStr, rows) = tx.connect {
                sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
                sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r2")})".update
                sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r3")})".update
                val qBase = SqlQuery.from(repoTable)
                val q     = qBase
                  .groupBy(qBase.col[Repo](_.ownerId))
                  .having(qBase.count(qBase.col[Repo](_.id)) > lit(1L))
                  .select[(Int, Long)](qBase.col[Repo](_.ownerId), qBase.count(qBase.col[Repo](_.id)))
                (q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL), q.run)
              }
              val expected =
                "SELECT t0.\"owner_id\" AS \"_1\", COUNT(t0.\"id\") AS \"_2\" FROM \"repos\" AS t0 GROUP BY t0.\"owner_id\" HAVING COUNT(t0.\"id\") > ?"
              assertTrue(sqlStr == expected, rows == List((1, 2L)))
            } finally {
              try tx.connect(Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update)
              catch { case _: Throwable => () }
            }
          } catch {
            case e: Throwable =>
              try tx.connect(Frag.literal("DROP TABLE IF EXISTS repos CASCADE").update)
              catch { case _: Throwable => () }
              throw new RuntimeException(s"PG aggregate failed: ${e.getMessage}", e)
          }
        }
      )

  def spec = suite("QueryIntegrationSpec")(
    sqliteSuite,
    pgSuite
  ) @@ TestAspect.sequential
}
