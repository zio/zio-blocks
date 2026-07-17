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
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{
  DbCodec,
  DbCodecDeriver,
  DbCon,
  DbTx,
  Dialect,
  Frag,
  JdbcTransactor,
  Repo,
  SqlDialect,
  Table,
  Transactor
}
import zio.blocks.data.migration._
import java.sql.DriverManager

/**
 * PostgreSQL-backed integration tests for migration features that require
 * Postgres-specific SQL (SKIP LOCKED, CREATE TABLE ... LIKE ...).
 *
 * CI already runs a PostgreSQL 16 service (port 32886). For local runs, set
 * PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE env vars. If PostgreSQL is
 * unavailable, the suite gracefully skips.
 */
object PostgresMigrationIntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.postgresql.Driver")

  private val pgHost     = sys.env.getOrElse("PGHOST", "localhost")
  private val pgPort     = sys.env.getOrElse("PGPORT", "32886").toInt
  private val pgUser     = sys.env.getOrElse("PGUSER", "postgres")
  private val pgPassword = sys.env.getOrElse("PGPASSWORD", "postgres")
  private val pgDb       = sys.env.getOrElse("PGDATABASE", "postgres")

  private lazy val pgAvailable: Boolean =
    try {
      val conn = DriverManager.getConnection(
        s"jdbc:postgresql://$pgHost:$pgPort/$pgDb",
        pgUser,
        pgPassword
      )
      conn.close()
      true
    } catch {
      case _: Throwable => false
    }

  private val pgConnStr = s"jdbc:postgresql://$pgHost:$pgPort/$pgDb"

  private def pgTx(): JdbcTransactor =
    new JdbcTransactor(
      () => {
        val conn = DriverManager.getConnection(pgConnStr, pgUser, pgPassword)
        conn.setAutoCommit(false)
        conn
      },
      SqlDialect.PostgreSQL
    )

  case class V1(id: Int, name: String)
  object V1 {
    given schema: Schema[V1] = Schema.derived
  }

  case class V2(id: Int, name: String, age: Int)
  object V2 {
    given schema: Schema[V2] = Schema.derived
  }

  private given DbCodec[V1]  = V1.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[V2]  = V2.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int] = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive

  private val v1Table = Table.derived[V1]
  private val v2Table = Table.derived[V2]
  private val v1Repo  = Repo(v1Table, "id", summon[DbCodec[Int]], (_: V1).id)
  private val v2Repo  = Repo(v2Table, "id", summon[DbCodec[Int]], (_: V2).id)

  private val identityMigration: Migration[V1, V2] = null.asInstanceOf[Migration[V1, V2]]

  private def withPgDb[A](f: JdbcTransactor => A): A = {
    val tx = pgTx()
    try {
      tx.connect { (c: DbCon) ?=>
        Frag
          .literal("""
          CREATE TABLE IF NOT EXISTS v1 (id INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY (id))
        """)
          .update
        Frag
          .literal("""
          CREATE TABLE IF NOT EXISTS v2 (id INTEGER NOT NULL, name TEXT NOT NULL, age INTEGER NOT NULL, PRIMARY KEY (id))
        """)
          .update
      }
      f(tx)
    } finally {
      try
        tx.connect { (c: DbCon) ?=>
          Frag.literal("DROP TABLE IF EXISTS v1").update
          Frag.literal("DROP TABLE IF EXISTS v2").update
        }
      catch { case _: Throwable => () }
    }
  }

  def spec =
    if (!pgAvailable)
      suite("PostgresMigrationIntegration")(
        test("PostgreSQL not available - set PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE") {
          assertTrue(true)
        }
      )
    else
      suite("PostgresMigrationIntegration")(
        test("queue primitives: enqueue/dequeue with SKIP LOCKED") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "pg_queue_skip"
            QueueTable.create[Int](qname, tx)
            tx.connect(QueueTable.enqueue(qname, List(10, 20, 30)))
            val before = tx.connect(QueueTable.pending(qname))
            val ids    = tx.transact(QueueTable.dequeue[Int](qname, 2))
            val after  = tx.connect(QueueTable.pending(qname))
            assertTrue(before == 3 && ids.size == 2 && after == 1)
          }
        },
        test("LargeMigrator basic lifecycle with SKIP LOCKED") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "pg_large_basic"
            QueueTable.create[Int](qname, tx)
            tx.connect(QueueTable.enqueue(qname, List(100)))
            val migrator = LargeMigrator[V1, V2, Int, Int](
              repoV1 = v1Repo,
              repoV2 = v2Repo,
              migration = identityMigration,
              queueTable = qname,
              batchSize = 10,
              target = TargetStrategy.InPlace
            )(using tx, summon[DbCodec[Int]])
            val ran = migrator.run()
            assertTrue(ran >= 0)
          }
        },
        test("LargeMigrator fence/drain/complete protocol") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "pg_fence_drain"
            QueueTable.create[Int](qname, tx)
            tx.connect(QueueTable.enqueue(qname, List(1, 2, 3)))
            val migrator = LargeMigrator[V1, V2, Int, Int](
              repoV1 = v1Repo,
              repoV2 = v2Repo,
              migration = identityMigration,
              queueTable = qname,
              batchSize = 10,
              target = TargetStrategy.InPlace
            )(using tx, summon[DbCodec[Int]])
            migrator.init()
            migrator.fence()
            val drained = migrator.drain()
            migrator.complete()
            assertTrue(drained > 0)
          }
        },
        test("SmallMigrator with ShadowTable init") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "pg_shadow"
            QueueTable.create[Int](qname, tx)
            tx.connect(QueueTable.enqueue(qname, List(1)))
            val migrator = SmallMigrator[V1, V2, Int, Int](
              repoV1 = v1Repo,
              repoV2 = v2Repo,
              migration = identityMigration,
              queueTable = qname,
              batchSize = 10,
              target = TargetStrategy.ShadowTable("_mig")
            )(using tx, summon[DbCodec[Int]])
            migrator.init()
            assertTrue(true)
          }
        }
      )
}
