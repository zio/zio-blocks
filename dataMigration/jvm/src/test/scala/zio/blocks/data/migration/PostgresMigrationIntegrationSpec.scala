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
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}
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

  /**
   * Creates a transactor whose connections default to auto-commit=true.
   * Read-only queries (`connect`) auto-commit each statement. Write operations
   * that need a transaction (`transact`) explicitly disable auto-commit and
   * commit after success.
   */
  private def pgTx(): JdbcTransactor =
    new JdbcTransactor(
      () => DriverManager.getConnection(pgConnStr, pgUser, pgPassword),
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

  private val identityMigration: Migration[V1, V2] = {
    val addAge = DynamicMigration(
      MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema[Int])
      )
    )
    Migration.fromDynamic(addAge)(using V1.schema, V2.schema)
  }

  private def withPgDb[A](f: JdbcTransactor => A): A = {
    val tx = pgTx()
    // Drop tables from any previous run in a dedicated transaction
    try {
      tx.transact { (c: DbTx) ?=>
        Frag.literal("DROP TABLE IF EXISTS v1 CASCADE").update
        Frag.literal("DROP TABLE IF EXISTS v2 CASCADE").update
      }
    } catch { case _: Throwable => () }
    try {
      tx.transact { (c: DbTx) ?=>
        Frag.literal("CREATE TABLE IF NOT EXISTS v1 (id INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY (id))").update
        Frag.literal("CREATE TABLE IF NOT EXISTS v2 (id INTEGER NOT NULL, name TEXT NOT NULL, age INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (id))").update
      }
      f(tx)
    } finally {
      try
        tx.transact { (c: DbTx) ?=>
          Frag.literal("DROP TABLE IF EXISTS v1 CASCADE").update
          Frag.literal("DROP TABLE IF EXISTS v2 CASCADE").update
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
            val qname              = "q_prim_01"
            QueueTable.create[Int](qname, tx)
            tx.transact(QueueTable.enqueue(qname, List(10, 20, 30)))
            val before = tx.transact(QueueTable.pending(qname))
            val ids    = tx.transact(QueueTable.dequeue[Int](qname, 2))
            val after  = tx.transact(QueueTable.pending(qname))
            assertTrue(before == 3 && ids.size == 2 && after == 1)
          }
        },
        test("LargeMigrator basic lifecycle with SKIP LOCKED") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "q_large_01"
            QueueTable.create[Int](qname, tx)
            tx.transact(QueueTable.enqueue(qname, List(100)))
            // Insert matching V1/V2 records so processBatch finds and updates data
            tx.transact { (c2: DbTx) ?=>
              Frag.literal("INSERT INTO v1 (id, name) VALUES (100, 'test') ON CONFLICT (id) DO NOTHING").update
              Frag.literal("INSERT INTO v2 (id, name, age) VALUES (100, 'test', 0) ON CONFLICT (id) DO NOTHING").update
            }
            val migrator = LargeMigrator[V1, V2, Int, Int](
              repoV1 = v1Repo,
              repoV2 = v2Repo,
              migration = identityMigration,
              queueTable = qname,
              batchSize = 10,
              target = TargetStrategy.InPlace
            )(using tx, summon[DbCodec[Int]])
            val ran = migrator.run()
            assertTrue(ran == 1)
          }
        },
        test("LargeMigrator fence/drain/complete protocol") {
          withPgDb { tx =>
            given dialect: Dialect = Dialect.Postgres
            val qname              = "q_fence_01"
            QueueTable.create[Int](qname, tx)
            tx.transact(QueueTable.enqueue(qname, List(1, 2, 3)))
            tx.transact { (c2: DbTx) ?=>
              Frag.literal("INSERT INTO v1 (id, name) VALUES (1, 'a'), (2, 'b'), (3, 'c') ON CONFLICT (id) DO NOTHING").update
              Frag.literal("INSERT INTO v2 (id, name, age) VALUES (1, 'a', 0), (2, 'b', 0), (3, 'c', 0) ON CONFLICT (id) DO NOTHING").update
            }
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
            val qname              = "q_shadow_01"
            QueueTable.create[Int](qname, tx)
            tx.transact(QueueTable.enqueue(qname, List(1)))
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
      ) @@ TestAspect.sequential
}
