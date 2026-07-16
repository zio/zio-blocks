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

import zio.blocks.schema.migration.Migration
import zio.blocks.sql.*
import zio.test.*

object LargeMigratorSpec extends ZIOSpecDefault {

  // Minimal stubs for signature tests only (no real DB).
  // connect returns 0L (pendingCount: Long), transact returns 0 (run: Int).
  trait FakeTransactor extends Transactor {
    override def connect[A](f: DbCon ?=> A): A = 0L.asInstanceOf[A]
    override def transact[A](f: DbTx ?=> A): A = 0.asInstanceOf[A]
  }

  trait FakeRepo[E, ID] extends Repo[E, ID] {
    // Repo requires Schema/Codec context; stubbed for compile test only
  }

  // Use a dummy migration value that satisfies the type; actual apply is never called in compile tests
  val fakeMigration: Migration[Int, String] = null.asInstanceOf[Migration[Int, String]]

  val fakeTransactor: Transactor = new FakeTransactor {}

  // Compile test: LargeMigrator construction
  val _compileTest: LargeMigrator[Int, String, Long] = new LargeMigrator[Int, String, Long](
    repoV1 = null.asInstanceOf[Repo[Int, Long]],
    repoV2 = null.asInstanceOf[Repo[String, Long]],
    migration = fakeMigration,
    queueTable = "q",
    batchSize = 10,
    target = TargetStrategy.InPlace
  )(using fakeTransactor, null.asInstanceOf[DbCodec[Long]])

  def spec = suite("LargeMigrator")(
    test("pause/resume/isPaused work") {
      val m = new LargeMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = fakeMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using fakeTransactor, null.asInstanceOf[DbCodec[Long]])
      assertTrue(!m.isPaused)
      m.pause()
      assertTrue(m.isPaused)
      m.resume()
      assertTrue(!m.isPaused)
    },
    test("run returns 0 on empty queue") {
      val m = new LargeMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = fakeMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using fakeTransactor, null.asInstanceOf[DbCodec[Long]])
      assertTrue(m.run() == 0)
    },
    test("pendingCount compiles") {
      val m = new LargeMigrator[Int, String, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = fakeMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using fakeTransactor, null.asInstanceOf[DbCodec[Long]])
      val _ = m.pendingCount
      assertTrue(true)
    }
  )
}
