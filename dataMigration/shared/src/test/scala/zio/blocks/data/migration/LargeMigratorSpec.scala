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
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.*

object LargeMigratorSpec extends ZIOSpecDefault {

  /**
   * Transactor stub whose transaction bodies are never executed: `transact`
   * reports an empty batch, `connect` reports a fixed pending count. This
   * exercises LargeMigrator's state machine without a database.
   */
  final class StubTransactor(pendingValue: Long) extends Transactor {
    var transactCalls: Int = 0

    override def connect[A](f: DbCon ?=> A): A = pendingValue.asInstanceOf[A]
    override def transact[A](f: DbTx ?=> A): A = {
      transactCalls += 1
      0.asInstanceOf[A]
    }
    override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A = {
      transactCalls += 1
      0.asInstanceOf[A]
    }
  }

  private val idColumns = IndexedSeq(ColumnMeta("id", DbValue.DbInt(0), nullable = false))
  private val v1Repo    = Repo(Table[Int]("users_v1", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)
  private val v2Repo    = Repo(Table[Int]("users_v2", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)

  // Never invoked: StubTransactor skips transaction bodies entirely.
  private val unusedMigration = null.asInstanceOf[Migration[Int, Int]]

  private given dialect: Dialect = Dialect.Postgres

  private def newMigrator(
    tx: Transactor,
    target: TargetStrategy = TargetStrategy.InPlace
  ): LargeMigrator[Int, Int, Int, Int] =
    LargeMigrator[Int, Int, Int, Int](
      repoV1 = v1Repo,
      repoV2 = v2Repo,
      migration = unusedMigration,
      queueTable = "q",
      batchSize = 10,
      target = target
    )(using tx, DbCodec.intCodec)

  def spec = suite("LargeMigrator")(
    suite("lifecycle guards")(
      test("run() rejects non-positive batchSize") {
        val m = LargeMigrator[Int, Int, Int, Int](
          repoV1 = v1Repo,
          repoV2 = v2Repo,
          migration = unusedMigration,
          queueTable = "q",
          batchSize = 0,
          target = TargetStrategy.InPlace
        )(using new StubTransactor(0L), DbCodec.intCodec)
        assertTrue(scala.util.Try(m.run()).isFailure)
      },
      test("fence() before init() fails") {
        val m = newMigrator(new StubTransactor(0L))
        assertTrue(scala.util.Try(m.fence()).isFailure)
      },
      test("run() initializes implicitly and returns 0 on an empty batch stream") {
        val m = newMigrator(new StubTransactor(0L))
        assertTrue(m.run() == 0)
      },
      test("complete() before drain() fails") {
        val m = newMigrator(new StubTransactor(0L))
        m.init()
        m.fence()
        assertTrue(scala.util.Try(m.complete()).isFailure)
      },
      test("happy path init -> fence -> drain -> complete transitions cleanly") {
        val m = newMigrator(new StubTransactor(0L))
        m.init()
        m.fence()
        val drained = m.drain()
        m.complete()
        assertTrue(drained == 0 && scala.util.Try(m.fence()).isFailure)
      },
      test("run() after complete() fails") {
        val m = newMigrator(new StubTransactor(0L))
        m.init()
        m.fence()
        m.drain()
        m.complete()
        assertTrue(scala.util.Try(m.run()).isFailure)
      }
    ),
    suite("drain safety valve")(
      test("stalled queue (empty batches with pending items) fails loudly instead of spinning") {
        val tx = new StubTransactor(5L)
        val m  = newMigrator(tx)
        m.init()
        m.fence()
        val result = scala.util.Try(m.drain())
        assertTrue(
          result.isFailure &&
            result.failed.get.getMessage.contains("did not drain") &&
            tx.transactCalls > 0 && tx.transactCalls < 1000
        )
      },
      test("pause() during stalled drain exits without throwing and stays Fenced") {
        val tx = new StubTransactor(5L)
        val m  = newMigrator(tx)
        m.init()
        m.fence()
        m.pause()
        val drained = scala.util.Try(m.drain())
        assertTrue(drained.isSuccess && drained.get == 0 && scala.util.Try(m.complete()).isFailure)
      }
    ),
    suite("pause/resume")(
      test("isPaused reflects pause/resume") {
        val m = newMigrator(new StubTransactor(0L))
        assertTrue(!m.isPaused)
        m.pause()
        assertTrue(m.isPaused)
        m.resume()
        assertTrue(!m.isPaused)
      },
      test("pendingCount reads through transactor.connect") {
        val m = newMigrator(new StubTransactor(42L))
        assertTrue(m.pendingCount == 42L)
      }
    )
  )
}
