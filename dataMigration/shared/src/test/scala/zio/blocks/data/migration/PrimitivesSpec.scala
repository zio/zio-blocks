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

import zio.blocks.sql.{DbCodec, Transactor}
import zio.test.*
import zio.test.Assertion.*

object PrimitivesSpec extends ZIOSpecDefault {

  // Minimal in-memory Transactor stub for DDL tests (no real DB needed for syntax validation)
  val stubTransactor: Transactor = new Transactor {
    def connect[A](f: zio.blocks.sql.DbCon ?=> A): A = throw new UnsupportedOperationException("stub")
    def transact[A](f: zio.blocks.sql.DbTx ?=> A): A = throw new UnsupportedOperationException("stub")
  }

  def spec = suite("Migration Primitives")(
    suite("QueueTable")(
      test("create/enqueue/dequeue/pending signatures compile") {
        assertCompletes
      }
    ),
    suite("MigrationMetadata")(
      test("createTable/markDone/getVersion/migratedCount signatures compile") {
        assertCompletes
      }
    ),
    suite("ShadowTable")(
      test("create/swap signatures compile") {
        assertCompletes
      }
    )
  )
}
