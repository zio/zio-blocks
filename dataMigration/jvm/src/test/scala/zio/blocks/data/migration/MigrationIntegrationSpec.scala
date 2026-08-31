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

// Must be in sql package because SingleConnectionTransactor accesses private[sql] JdbcConnection
package zio.blocks.sql

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}
import zio.blocks.sql.{JdbcTransactor, SqlDialect, DbCon, DbTx, Table, Repo, Frag, DbCodec, DbCodecDeriver}
import zio.blocks.data.migration._
import zio.blocks.sql.Dialect
import java.sql.DriverManager

object MigrationIntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

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

  /**
   * JdbcTransactor that keeps a single connection open across all calls. SQLite
   * :memory: requires one connection for schema persistence; the default
   * JdbcTransactor closes the connection after each transact()/connect().
   */
  private class SingleConnectionTransactor(conn: java.sql.Connection)
      extends JdbcTransactor(() => conn, SqlDialect.SQLite) {
    override def connect[A](f: DbCon ?=> A): A = {
      val dbConn       = new JdbcConnection(conn)
      given con: DbCon = new DbCon {
        val connection: DbConnection = dbConn
        val dialect: SqlDialect      = SqlDialect.SQLite
        val logger: SqlLogger        = SqlLogger.noop
      }
      f
    }
    override def transact[A](f: DbTx ?=> A): A = {
      val dbConn         = new JdbcConnection(conn)
      val prevAutoCommit = conn.getAutoCommit
      conn.setAutoCommit(false)
      try {
        given tx: DbTx = new DbTx {
          val connection: DbConnection                = dbConn
          val dialect: SqlDialect                     = SqlDialect.SQLite
          val logger: SqlLogger                       = SqlLogger.noop
          override var currentDepth: Int              = 0
          override def savepoint(name: String): Unit  = dbConn.savepoint(name)
          override def release(name: String): Unit    = dbConn.release(name)
          override def rollbackTo(name: String): Unit = dbConn.rollbackTo(name)
        }
        val result = f
        conn.commit()
        result
      } catch {
        case e: Throwable =>
          try conn.rollback()
          catch { case rb: Throwable => e.addSuppressed(rb) }
          throw e
      } finally {
        try conn.setAutoCommit(prevAutoCommit)
        catch { case _: Throwable => () }
      }
    }
  }

  private def withFreshDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new SingleConnectionTransactor(conn)
    try {
      tx.connect {
        Frag.literal("CREATE TABLE IF NOT EXISTS v1 (id INTEGER NOT NULL, name TEXT NOT NULL)").update
        Frag
          .literal("CREATE TABLE IF NOT EXISTS v2 (id INTEGER NOT NULL, name TEXT NOT NULL, age INTEGER NOT NULL)")
          .update
      }
      f(tx)
    } finally conn.close()
  }

  private val identityMigration: Migration[V1, V2] = {
    val addAge = DynamicMigration(
      MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema[Int])
      )
    )
    Migration.fromDynamic(addAge)(using V1.schema, V2.schema)
  }

  def spec = suite("MigrationIntegration")(
    test("queue primitives: create/enqueue/pending (SQLite: no SKIP LOCKED)") {
      withFreshDb { tx =>
        given dialect: Dialect = Dialect.SQLite
        val qname              = "queue_test"
        tx.connect {
          QueueTable.create[Int](qname, tx)
          QueueTable.enqueue(qname, List(1, 2, 3))
          val count1 = QueueTable.pending(qname)
          QueueTable.enqueue(qname, List(4))
          val count2 = QueueTable.pending(qname)
          assertTrue(count1 == 3 && count2 == 4)
        }
      }
    },
    test("SmallMigrator construction and init with InPlace (SQLite limited)") {
      withFreshDb { tx =>
        given dialect: Dialect = Dialect.SQLite
        val qname              = "q_small_inplace"
        tx.connect {
          QueueTable.create[Int](qname, tx)
          QueueTable.enqueue(qname, List(1, 2))
        }
        val migrator = SmallMigrator[V1, V2, Int, Int](
          repoV1 = v1Repo,
          repoV2 = v2Repo,
          migration = identityMigration,
          queueTable = qname,
          batchSize = 10,
          target = TargetStrategy.InPlace
        )(using tx, summon[DbCodec[Int]])
        migrator.init()
        migrator.init() // idempotent: second call must not throw or recreate
        val processed = migrator.processBatch()
        assertTrue(processed == 0) // queue IDs have no matching v1 rows, so nothing migrates
      }
    },
    test("capture triggers (SQLite): INSERT then DELETE coalesce to a single 'D' entry") {
      withFreshDb { tx =>
        given dialect: Dialect = Dialect.SQLite
        val qname              = "q_sqlite_trig"
        QueueTable.create[Int](qname, tx)
        tx.transact {
          QueueTable.installTriggers[Int](qname, "v1", "id")
        }
        tx.connect {
          Frag.literal("INSERT INTO v1 (id, name) VALUES (7, 'x')").update
          val pendingAfterInsert = QueueTable.pending(qname)
          Frag.literal("DELETE FROM v1 WHERE id = 7").update
          val pendingAfterDelete = QueueTable.pending(qname)
          val lastOp             = Frag.literal(s"SELECT op FROM $qname").queryOne[String]
          assertTrue(
            pendingAfterInsert == 1 &&
              pendingAfterDelete == 1 &&
              lastOp.contains("D")
          )
        }
      }
    },
    test("Worker failure resilience: pending count preserved on error path") {
      withFreshDb { tx =>
        given dialect: Dialect = Dialect.SQLite
        val qname              = "q_fail"
        tx.connect {
          QueueTable.create[Int](qname, tx)
          QueueTable.enqueue(qname, List(1, 2))
          val before = QueueTable.pending(qname)
          assertTrue(before == 2)
        }
      }
    },
    test("Edge cases: empty, single, duplicate enqueue (ON CONFLICT DO NOTHING)") {
      withFreshDb { tx =>
        given dialect: Dialect = Dialect.SQLite
        val qname              = "q_edge"
        tx.connect {
          QueueTable.create[Int](qname, tx)
          val empty = QueueTable.pending(qname)
          QueueTable.enqueue(qname, List(42))
          val single = QueueTable.pending(qname)
          QueueTable.enqueue(qname, List(42))
          val afterDup = QueueTable.pending(qname)
          assertTrue(empty == 0 && single == 1 && afterDup == 1)
        }
      }
    }
  )
}
