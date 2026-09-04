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

object SmallMigratorInitConcurrencySpec extends ZIOSpecDefault {

  /**
   * Transactor stub that counts preparation transactions without running them.
   */
  final class CountingTransactor extends Transactor {
    val transactCalls                          = new java.util.concurrent.atomic.AtomicInteger(0)
    override def connect[A](f: DbCon ?=> A): A = 0L.asInstanceOf[A]
    override def transact[A](f: DbTx ?=> A): A = {
      transactCalls.incrementAndGet()
      0.asInstanceOf[A]
    }
    override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A =
      transact(f)
  }

  private val idColumns = IndexedSeq(ColumnMeta("id", DbValue.DbInt(0), nullable = false))
  private val v1Repo    = Repo(Table[Int]("users_v1", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)
  private val v2Repo    = Repo(Table[Int]("users_v2", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)

  private val unusedMigration = null.asInstanceOf[zio.blocks.schema.migration.Migration[Int, Int]]

  private given dialect: Dialect = Dialect.Postgres

  def spec = suite("SmallMigratorInitConcurrency")(
    test("concurrent init() calls prepare the target exactly once") {
      val tx = new CountingTransactor
      val m  = SmallMigrator[Int, Int, Int, Int](
        repoV1 = v1Repo,
        repoV2 = v2Repo,
        migration = unusedMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using tx, DbCodec.intCodec)
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
    }
  )
}
