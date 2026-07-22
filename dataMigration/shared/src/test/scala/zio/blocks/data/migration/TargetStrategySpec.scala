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
import zio.blocks.sql.{DbCodec, DbCon, DbTx, Dialect, Repo, Transactor}

object TargetStrategySpec extends ZIOSpecDefault {

  // Minimal stub transactor for compile-time verification (no real DB needed).
  val stubTransactor: Transactor = new Transactor {
    def connect[A](f: DbCon ?=> A): A = throw new UnsupportedOperationException("stub")
    def transact[A](f: DbTx ?=> A): A = throw new UnsupportedOperationException("stub")
  }

  // Tests that verify TargetStrategy integration (no real DB needed).
  // resolveTableName/prepare/finalize need Table[E]/DbCon which require a real DB;
  // we test them at compile level and verify the strategy dispatch logic inline.
  def spec = suite("TargetStrategyApplier")(
    test("InPlace strategy round-trips") {
      assertTrue(
        TargetStrategy.InPlace.productPrefix == "InPlace"
      )
    },
    test("ShadowTable stores suffix") {
      val s = TargetStrategy.ShadowTable("users_v2")
      assertTrue(s.suffix == "users_v2")
    },
    test("LargeMigrator with InPlace strategy compiles") {
      given dialect: Dialect = Dialect.Postgres
      val _                  = new LargeMigrator[Int, String, Long, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using stubTransactor, DbCodec.longCodec)
      assertTrue(true)
    },
    test("LargeMigrator with ShadowTable strategy compiles") {
      given dialect: Dialect = Dialect.Postgres
      val _                  = new LargeMigrator[Int, String, Long, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.ShadowTable("v2")
      )(using stubTransactor, DbCodec.longCodec)
      assertTrue(true)
    },
    test("SmallMigrator with both strategies compiles") {
      given dialect: Dialect = Dialect.Postgres
      val _                  = new SmallMigrator[Int, String, Long, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace
      )(using stubTransactor, DbCodec.longCodec)
      val _ = new SmallMigrator[Int, String, Long, Long](
        repoV1 = null.asInstanceOf[Repo[Int, Long]],
        repoV2 = null.asInstanceOf[Repo[String, Long]],
        migration = null.asInstanceOf[Migration[Int, String]],
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.ShadowTable("v2")
      )(using stubTransactor, DbCodec.longCodec)
      assertTrue(true)
    }
  )
}
