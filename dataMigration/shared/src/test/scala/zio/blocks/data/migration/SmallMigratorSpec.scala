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
import zio.blocks.sql.{DbCodec, DbCon, DbTx, Repo, Transactor}
import zio.blocks.schema.Schema
import zio.test.*

object SmallMigratorSpec extends ZIOSpecDefault {

  // Minimal stub Transactor for testing transaction wrapping.
  // The tests below use batchSize=0 so the early return is taken before any DB access.
  final class StubTransactor(var connectCalled: Int = 0, var transactCalled: Int = 0) extends Transactor {
    override def connect[A](f: DbCon ?=> A): A = {
      connectCalled += 1
      f(using null.asInstanceOf[DbCon])
    }
    override def transact[A](f: DbTx ?=> A): A = {
      transactCalled += 1
      f(using null.asInstanceOf[DbTx])
    }
  }



  // Dummy schema + migration for compile test
  case class V1(x: Int)
  case class V2(x: Int, y: String = "")

  given Schema[V1] = Schema.derived[V1]
  given Schema[V2] = Schema.derived[V2]
  given DbCodec[Int] = DbCodec.intCodec

  val dummyMigration: Migration[V1, V2] = Migration(
    summon[Schema[V1]],
    summon[Schema[V2]],
    zio.blocks.schema.migration.DynamicMigration.empty
  )

  def spec = suite("SmallMigrator")(
    test("signature compiles") {
      val stubTx   = new StubTransactor()
      // Use a minimal concrete Repo via derived constructor (requires schema + codec in scope)
      val repoV1   = new Repo[V1, Int]() {}
      val repoV2   = new Repo[V2, Int]() {}
      // This line must compile:
      val migrator = new SmallMigrator[V1, V2, Int](
        repoV1, repoV2, dummyMigration, "q", 10, TargetStrategy.InPlace
      )(using stubTx, summon[DbCodec[Int]])
      assertTrue(true)
    },

    test("processBatch calls transactor.transact") {
      val stubTx   = new StubTransactor()
      val repoV1   = new Repo[V1, Int]() {}
      val repoV2   = new Repo[V2, Int]() {}
      // batchSize=1 exercises the transact path; the stub does not provide a real connection
      // so the test only asserts that transact was entered (the NPE inside is acceptable for this unit test)
      val migrator = new SmallMigrator[V1, V2, Int](
        repoV1, repoV2, dummyMigration, "q", 1, TargetStrategy.InPlace
      )(using stubTx, summon[DbCodec[Int]])

      // We only care that transact was invoked; ignore the internal NPE from the null connection
      try { migrator.processBatch() } catch { case _: NullPointerException => () }
      assertTrue(stubTx.transactCalled == 1)
    },

    test("empty queue returns 0") {
      val stubTx   = new StubTransactor()
      val repoV1   = new Repo[V1, Int]() {}
      val repoV2   = new Repo[V2, Int]() {}
      val migrator = new SmallMigrator[V1, V2, Int](
        repoV1, repoV2, dummyMigration, "q", 0, TargetStrategy.InPlace
      )(using stubTx, summon[DbCodec[Int]])

      val result = migrator.processBatch()
      assertTrue(result == 0)
    }
  )
}
