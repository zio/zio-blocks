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

package zio.blocks.data.migration

import zio.test.*
import zio.blocks.sql.*
import MigratorTestStubs.{CountingTransactor, newInPlaceMigrator}

object SmallMigratorInitConcurrencySpec extends ZIOSpecDefault {

  private given dialect: Dialect = Dialect.Postgres

  /**
   * Delegating transactor that counts preparation transactions against a live
   * database.
   */
  final class CountingLiveTransactor(underlying: Transactor) extends Transactor {
    val transactCalls                          = new java.util.concurrent.atomic.AtomicInteger(0)
    override def connect[A](f: DbCon ?=> A): A = underlying.connect(f)
    override def transact[A](f: DbTx ?=> A): A = {
      transactCalls.incrementAndGet()
      underlying.transact(f)
    }
    override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A =
      underlying.transact(isolation, readOnly)(f)
  }

  def spec = suite("SmallMigratorInitConcurrency")(
    test("concurrent init() calls run the real preparation body once behind the synchronized gate") {
      // The shared stub executes the real `init()` body (InPlace preparation
      // never touches the connection), so this proves the in-process gate
      // serializes concurrent calls end to end — not just the counter.
      // Database-level idempotence under real races rests on the idempotent
      // trigger DDL (see `QueueTable.installTriggers` and `SmallMigrator.init`).
      val tx      = new CountingTransactor
      val m       = newInPlaceMigrator(tx)
      val gate    = new java.util.concurrent.CountDownLatch(1)
      val errors  = new java.util.concurrent.ConcurrentLinkedQueue[Throwable]()
      val threads = (1 to 8).map(_ =>
        new Thread(new Runnable {
          def run(): Unit =
            try {
              gate.await()
              m.init()
            } catch {
              case t: Throwable =>
                errors.add(t)
                ()
            }
        })
      )
      threads.foreach(_.start())
      gate.countDown()
      threads.foreach(_.join())
      assertTrue(tx.transactCalls.get() == 1, errors.isEmpty)
    },
    test("concurrent capture-trigger init against live SQLite prepares once and installs triggers") {
      // Meaningful DB race: 8 threads race `init()` with capture triggers
      // against a real (file-backed) SQLite database. The synchronized gate
      // must admit exactly one preparation transaction; the idempotent
      // `CREATE TRIGGER IF NOT EXISTS` DDL keeps the database consistent even
      // if the gate ever admitted two. Multi-process races are NOT covered
      // here — only the in-process gate plus DDL idempotence.
      val _         = Class.forName("org.sqlite.JDBC")
      val dbFile    = java.nio.file.Files.createTempFile("small-migrator-race", ".db")
      val jdbc      = JdbcTransactor.fromUrl(s"jdbc:sqlite:${dbFile.toAbsolutePath}", SqlDialect.SQLite)
      val counting  = new CountingLiveTransactor(jdbc)
      val queueName = "q_race"
      jdbc.connect {
        Frag.literal("CREATE TABLE users_v1 (id INTEGER NOT NULL)").update
      }
      QueueTable.create[Int](queueName, jdbc)(using DbCodec.intCodec, Dialect.SQLite)
      val m = SmallMigrator[Int, Int, Int, Int](
        repoV1 = MigratorTestStubs.v1Repo,
        repoV2 = MigratorTestStubs.v2Repo,
        migration = MigratorTestStubs.unusedMigration,
        queueTable = queueName,
        batchSize = 10,
        target = TargetStrategy.InPlace,
        captureTriggers = true
      )(using counting, DbCodec.intCodec, Dialect.SQLite)
      val gate    = new java.util.concurrent.CountDownLatch(1)
      val errors  = new java.util.concurrent.ConcurrentLinkedQueue[Throwable]()
      val threads = (1 to 8).map(_ =>
        new Thread(new Runnable {
          def run(): Unit =
            try {
              gate.await()
              m.init()
            } catch {
              case t: Throwable =>
                errors.add(t)
                ()
            }
        })
      )
      try {
        threads.foreach(_.start())
        gate.countDown()
        threads.foreach(_.join())
        m.init() // idempotent after the race: still no extra preparation
        val triggerCount = counting.connect {
          Frag.literal("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").queryOne[Long]
        }
        counting.connect {
          Frag.literal("INSERT INTO users_v1 (id) VALUES (9)").update
        }
        val pendingAfterInsert = counting.connect {
          QueueTable.pending(queueName)
        }
        assertTrue(
          errors.isEmpty,
          counting.transactCalls.get() == 1,
          triggerCount.contains(3L),
          pendingAfterInsert == 1L
        )
      } finally {
        java.nio.file.Files.deleteIfExists(dbFile)
        ()
      }
    }
  )
}
