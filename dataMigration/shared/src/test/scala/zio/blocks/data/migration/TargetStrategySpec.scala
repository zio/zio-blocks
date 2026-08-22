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
import zio.blocks.sql.{DbCodec, DbCon, DbConnection, DbTx, Dialect, SqlDialect, SqlLogger, Table}

object TargetStrategySpec extends ZIOSpecDefault {

  // DbCon is only demanded by the API shape; InPlace branches never touch it.
  // Members are defs so the ??? bodies are never evaluated.
  private val unusedCon: DbCon = new DbCon {
    def connection: DbConnection = ???
    def dialect: SqlDialect      = SqlDialect.PostgreSQL
    def logger: SqlLogger        = SqlLogger.noop
  }

  private val users = Table[Int]("users", DbCodec.intCodec, IndexedSeq.empty)

  def spec = suite("TargetStrategyApplier")(
    test("resolveTableName: InPlace keeps base name") {
      assertTrue(TargetStrategyApplier.resolveTableName(users, TargetStrategy.InPlace) == "users")
    },
    test("resolveTableName: ShadowTable appends underscore-separated suffix") {
      val resolved = TargetStrategyApplier.resolveTableName(users, TargetStrategy.ShadowTable("v2"))
      assertTrue(resolved == "users_v2")
    },
    test("resolveTableName rejects invalid suffix characters") {
      val result = scala.util.Try(
        TargetStrategyApplier.resolveTableName(users, TargetStrategy.ShadowTable("v2; DROP TABLE users"))
      )
      assertTrue(result.isFailure)
    },
    test("finalizeTarget: InPlace is a no-op returning (table, table)") {
      val (oldName, newName) =
        TargetStrategyApplier.finalizeTarget("users", TargetStrategy.InPlace)(using unusedCon)
      assertTrue(oldName == "users" && newName == "users")
    }
  )
}
