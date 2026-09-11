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

  def spec = suite("SmallMigratorInitConcurrency")(
    test("concurrent init() calls run the real preparation body exactly once") {
      // The shared stub executes the real `init()` body (InPlace preparation
      // never touches the connection), so this proves the gate serializes
      // concurrent calls end to end — not just the counter. Database-level
      // idempotence under real races rests on the idempotent trigger DDL (see
      // `QueueTable.installTriggers` and `SmallMigrator.init`).
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
    }
  )
}
