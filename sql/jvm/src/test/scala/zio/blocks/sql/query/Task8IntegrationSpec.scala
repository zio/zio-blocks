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
import zio.blocks.sql.{DbValue, DbResultReader, Frag, JdbcTransactor, PgSupport, SqlDialect, Table, sql}
import zio.test.*

object Task8IntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  @Modifier.config("sql.table_name", "users_t8")
  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  @Modifier.config("sql.table_name", "repos_t8")
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  @Modifier.config("sql.table_name", "sales_t8")
  case class Sale(id: Int, userId: Int, amount: Int)
  object Sale { given Schema[Sale] = Schema.derived }

  val userTable = Table.derived[User]
  val repoTable = Table.derived[Repo]
  val saleTable = Table.derived[Sale]

  val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)

  private val sqliteTransactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  private def sqliteSuite = suite("sqlite task8")(
    test("LEFT JOIN right-side missing row decodes None via query-bound col without asOption") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS repos_t8").update
        Frag.literal("DROP TABLE IF EXISTS users_t8").update
        userTable.createTable(SqlDialect.SQLite).update
        repoTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO users_t8 (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
        sql"INSERT INTO users_t8 (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
        sql"INSERT INTO repos_t8 (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
        // bob (id 2) has no repo
        val q    = SqlQuery.from(userTable).leftJoin(userRepoRel)
        val tq   = q.select[(String, Option[String])](q.col[User](_.name), q.col[Repo](_.name))
        val rows = tq.run.sortBy(_._1)
        assertTrue(rows == List(("alice", Some("r1")), ("bob", None)))
      }
    },
    test("LEFT JOIN per-slot nullability: inner still non-optional, left optional") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS repos_t8").update
        Frag.literal("DROP TABLE IF EXISTS users_t8").update
        userTable.createTable(SqlDialect.SQLite).update
        repoTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO users_t8 (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
        sql"INSERT INTO repos_t8 (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
        val q      = SqlQuery.from(userTable).leftJoin(userRepoRel)
        val tq     = q.select[(String, Option[String])](q.col[User](_.name), q.col[Repo](_.name))
        val sqlStr = tq.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        assertTrue(
          sqlStr == "SELECT t0.\"name\" AS \"_1\", t1.\"name\" AS \"_2\" FROM \"users_t8\" AS t0 LEFT JOIN \"repos_t8\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        )
      }
    },
    test("SUM integral widening to Long decodes correctly") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales_t8").update
        saleTable.createTable(SqlDialect.SQLite).update
        // Insert 2 rows for user 1: 10 + 11 = 21, sum as Long
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
        val q  = SqlQuery.from(saleTable)
        val tq = q
          .groupBy(q.col[Sale](_.userId))
          .select[(Int, Option[Long])](q.col[Sale](_.userId), q.sum(q.col[Sale](_.amount)))
        val rows = tq.run.sortBy(_._1)
        assertTrue(rows == List((1, Some(21L))))
      }
    },
    test("AVG integral returns Option[BigDecimal] (PostgreSQL numeric truth) and decodes SQLite REAL") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales_t8").update
        saleTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
        val q  = SqlQuery.from(saleTable)
        val tq = q
          .groupBy(q.col[Sale](_.userId))
          .select[(Int, Option[BigDecimal])](q.col[Sale](_.userId), q.avg(q.col[Sale](_.amount)))
        val rows = tq.run
        // SQLite AVG over INTEGER returns REAL; DbCodec[BigDecimal] decodes it via getBigDecimal.
        assertTrue(rows == List((1, Some(BigDecimal(10.5)))))
      }
    },
    test("DbCodec[BigDecimal] decodes SQLite INTEGER and REAL values (AVG cross-dialect strategy)") {
      sqliteTransactor.connect {
        val fromInt  = sql"SELECT ${DbValue.DbLong(21L)}".queryOne[BigDecimal]
        val fromReal = sql"SELECT ${DbValue.DbDouble(10.5)}".queryOne[BigDecimal]
        assertTrue(fromInt.contains(BigDecimal(21)), fromReal.contains(BigDecimal(10.5)))
      }
    },
    test("empty-input aggregate SUM/AVG/MIN/MAX returns None") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales_t8").update
        saleTable.createTable(SqlDialect.SQLite).update
        // No rows inserted, whole-query aggregate over empty table
        val qSum      = SqlQuery.from(saleTable)
        val qAvg      = SqlQuery.from(saleTable)
        val qMin      = SqlQuery.from(saleTable)
        val qMax      = SqlQuery.from(saleTable)
        val qCount    = SqlQuery.from(saleTable)
        val sumRows   = qSum.select[Option[Long]](qSum.sum(qSum.col[Sale](_.amount))).run
        val avgRows   = qAvg.select[Option[BigDecimal]](qAvg.avg(qAvg.col[Sale](_.amount))).run
        val minRows   = qMin.select[Option[Int]](qMin.min(qMin.col[Sale](_.amount))).run
        val maxRows   = qMax.select[Option[Int]](qMax.max(qMax.col[Sale](_.amount))).run
        val countRows = qCount.select[Long](qCount.count(qCount.col[Sale](_.id))).run
        assertTrue(
          sumRows == List(None),
          avgRows == List(None),
          minRows == List(None),
          maxRows == List(None),
          countRows == List(0L)
        )
      }
    },
    test("scope-neutral countStar and literal projections are selectable and execute") {
      sqliteTransactor.connect {
        Frag.literal("DROP TABLE IF EXISTS sales_t8").update
        saleTable.createTable(SqlDialect.SQLite).update
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
        sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
        val q       = SqlQuery.from(saleTable)
        val tqStar  = q.select[Long](countStar)
        val starSql = tqStar.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val starRow = tqStar.run
        assertTrue(
          starSql == "SELECT COUNT(*) AS \"value\" FROM \"sales_t8\" AS t0",
          starRow == List(2L)
        )
        // literal projection
        val tqLit  = q.select[Int](lit(1))
        val litSql = tqLit.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val litRow = tqLit.run
        assertTrue(
          litSql == "SELECT ? AS \"value\" FROM \"sales_t8\" AS t0",
          litRow == List(1)
        )
        // mixed scope-neutral + query-bound projection
        val tqMixed  = q.select[(Int, Long)](q.col[Sale](_.userId), countStar)
        val mixedSql = tqMixed.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val mixedRow = tqMixed.run
        assertTrue(
          mixedSql == "SELECT t0.\"user_id\" AS \"_1\", COUNT(*) AS \"_2\" FROM \"sales_t8\" AS t0",
          mixedRow == List((1, 2L))
        )
      }
    },
    test("foreign table column fails at compile time (no render/execute fallback)") {
      val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
        """{
          import zio.blocks.sql.query.*
          import zio.blocks.schema.Schema
          import zio.blocks.sql.Table
          case class User(id: Int, name: String)
          object User { given Schema[User] = Schema.derived }
          case class Extra(id: Int, ref: Int)
          object Extra { given Schema[Extra] = Schema.derived }
          val userTable: Table[User] = Table.derived[User]
          val q = SqlQuery.from(userTable)
          val bad = q.col[Extra](_.ref)
        }"""
      )
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("not part of this query")))
    }
  )

  private def pgSuite =
    if (!PgSupport.pgAvailable)
      suite("postgres task8")(
        test("SKIPPED (env unavailable) - PostgreSQL not available - set PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE") {
          assertTrue(true)
        }
      )
    else
      suite("postgres task8")(
        test("LEFT JOIN missing side and aggregate widening on PostgreSQL") {
          val tx = PgSupport.pgTransactor()
          try {
            tx.connect {
              Frag.literal("DROP TABLE IF EXISTS sales_t8 CASCADE").update
              Frag.literal("DROP TABLE IF EXISTS repos_t8 CASCADE").update
              Frag.literal("DROP TABLE IF EXISTS users_t8 CASCADE").update
              userTable.createTable(SqlDialect.PostgreSQL).update
              repoTable.createTable(SqlDialect.PostgreSQL).update
              saleTable.createTable(SqlDialect.PostgreSQL).update
            }
            try {
              val (rows, sumRows, avgRows, emptySum) = tx.connect {
                sql"INSERT INTO users_t8 (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
                sql"INSERT INTO users_t8 (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
                sql"INSERT INTO repos_t8 (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
                sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(10)})".update
                sql"INSERT INTO sales_t8 (id, user_id, amount) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbInt(1)}, ${DbValue.DbInt(11)})".update
                val leftQ = SqlQuery.from(userTable).leftJoin(userRepoRel)
                val qLeft = leftQ.select[(String, Option[String])](leftQ.col[User](_.name), leftQ.col[Repo](_.name))
                val qSumQ = SqlQuery.from(saleTable)
                val qSum  = qSumQ
                  .groupBy(qSumQ.col[Sale](_.userId))
                  .select[(Int, Option[Long])](qSumQ.col[Sale](_.userId), qSumQ.sum(qSumQ.col[Sale](_.amount)))
                val qAvgQ = SqlQuery.from(saleTable)
                val qAvg  = qAvgQ
                  .groupBy(qAvgQ.col[Sale](_.userId))
                  .select[(Int, Option[BigDecimal])](qAvgQ.col[Sale](_.userId), qAvgQ.avg(qAvgQ.col[Sale](_.amount)))
                val qEmptyQ = SqlQuery.from(saleTable)
                val qEmpty  = qEmptyQ
                  .where(qEmptyQ.col[Sale](_.userId) === lit(999))
                  .select[Option[Long]](qEmptyQ.sum(qEmptyQ.col[Sale](_.amount)))
                (qLeft.run.sortBy(_._1), qSum.run, qAvg.run, qEmpty.run)
              }
              assertTrue(
                rows == List(("alice", Some("r1")), ("bob", None)),
                sumRows == List((1, Some(21L))),
                avgRows == List((1, Some(BigDecimal(10.5)))),
                emptySum == List(None)
              )
            } finally {
              try
                tx.connect {
                  Frag.literal("DROP TABLE IF EXISTS sales_t8 CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS repos_t8 CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS users_t8 CASCADE").update
                }
              catch { case _: Throwable => () }
            }
          } catch {
            case e: Throwable =>
              try
                tx.connect {
                  Frag.literal("DROP TABLE IF EXISTS sales_t8 CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS repos_t8 CASCADE").update
                  Frag.literal("DROP TABLE IF EXISTS users_t8 CASCADE").update
                }
              catch { case _: Throwable => () }
              throw new RuntimeException(s"PG task8 failed: ${e.getMessage}", e)
          }
        }
      )

  def spec = suite("Task8IntegrationSpec")(sqliteSuite, pgSuite) @@ TestAspect.sequential
}
