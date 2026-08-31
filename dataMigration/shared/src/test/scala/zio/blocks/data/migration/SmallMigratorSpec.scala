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

object SmallMigratorSpec extends ZIOSpecDefault {

  /**
   * Transactor stub whose transaction bodies are never executed: `connect`
   * reports a fixed pending count. This exercises SmallMigrator's guards
   * without a database.
   */
  final class StubTransactor(pendingValue: Long) extends Transactor {
    def connect[A](f: DbCon ?=> A): A = pendingValue.asInstanceOf[A]
    def transact[A](f: DbTx ?=> A): A = 0.asInstanceOf[A]
  }

  private val idColumns = IndexedSeq(ColumnMeta("id", DbValue.DbInt(0), nullable = false))
  private val v1Repo    = Repo(Table[Int]("users_v1", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)
  private val v2Repo    = Repo(Table[Int]("users_v2", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)

  // Never invoked: StubTransactor skips transaction bodies entirely.
  private val unusedMigration = null.asInstanceOf[Migration[Int, Int]]

  private given dialect: Dialect = Dialect.Postgres

  private def newMigrator(tx: Transactor): SmallMigrator[Int, Int, Int, Int] =
    SmallMigrator[Int, Int, Int, Int](
      repoV1 = v1Repo,
      repoV2 = v2Repo,
      migration = unusedMigration,
      queueTable = "q",
      batchSize = 10,
      target = TargetStrategy.InPlace
    )(using tx, DbCodec.intCodec)

  def spec = suite("SmallMigrator")(
    test("processBatch() before init() fails") {
      val m = newMigrator(new StubTransactor(0L))
      assertTrue(scala.util.Try(m.processBatch()).isFailure)
    },
    test("complete() before init() fails") {
      val m = newMigrator(new StubTransactor(0L))
      assertTrue(scala.util.Try(m.complete()).isFailure)
    },
    test("complete() refuses while the queue still has pending items") {
      val m = newMigrator(new StubTransactor(7L))
      m.init()
      val result = scala.util.Try(m.complete())
      assertTrue(result.isFailure && result.failed.get.getMessage.contains("still pending"))
    },
    test("complete() succeeds once the queue reports empty") {
      val m = newMigrator(new StubTransactor(0L))
      m.init()
      m.complete()
      assertTrue(true)
    },
    test("processBatch() after complete() fails") {
      val m = newMigrator(new StubTransactor(0L))
      m.init()
      m.complete()
      assertTrue(scala.util.Try(m.processBatch()).isFailure)
    },
    test("init() after complete() fails") {
      val m = newMigrator(new StubTransactor(0L))
      m.init()
      m.complete()
      assertTrue(scala.util.Try(m.init()).isFailure)
    },
    test("complete() twice fails") {
      val m = newMigrator(new StubTransactor(0L))
      m.init()
      m.complete()
      assertTrue(scala.util.Try(m.complete()).isFailure)
    }
  )
}
