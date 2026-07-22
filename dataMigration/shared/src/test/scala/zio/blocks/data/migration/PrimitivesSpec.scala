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

import zio.blocks.sql.{DbCodec, DbCon, DbTx, Dialect, Transactor, Table}
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
        // Lambda bodies verify signatures compile; lambdas are never called so no stub exception
        given dialect: Dialect = Dialect.Postgres
        val _create            = (n: String, tx: Transactor) => QueueTable.create[Int](n, tx)(using DbCodec.intCodec, dialect)
        val _enqueue           =
          (n: String, ids: Seq[Int], c: DbCon) => QueueTable.enqueue[Int](n, ids)(using c, DbCodec.intCodec)
        val _dequeue =
          (n: String, b: Int, tx: DbTx) => QueueTable.dequeue[Int](n, b)(using tx, DbCodec.intCodec, dialect)
        val _pending = (n: String, c: DbCon) => QueueTable.pending(n)(using c)
        assertTrue(true)
      }
    ),
    suite("MigrationMetadata")(
      test("createTable/markDone/getVersion/migratedCount signatures compile") {
        val _createTable = (tx: Transactor) => MigrationMetadata.createTable(tx)
        val _markDone    =
          (t: String, id: String, v: DataVersion, c: DbCon) => MigrationMetadata.markDone(t, id, v)(using c)
        val _getVersion    = (t: String, id: String, c: DbCon) => MigrationMetadata.getVersion(t, id)(using c)
        val _migratedCount = (t: String, c: DbCon) => MigrationMetadata.migratedCount(t)(using c)
        assertTrue(true)
      }
    ),
    suite("ShadowTable")(
      test("create/swap signatures compile") {
        given dialect: Dialect = Dialect.Postgres
        val tbl                = Table[Int]("test_table", DbCodec.intCodec, IndexedSeq.empty)
        val _create            = (suffix: String, c: DbCon) => ShadowTable.create[Int](tbl, suffix)(using c, dialect)
        val _swap              = (name: String, suffix: String, c: DbCon) => ShadowTable.swap(name, suffix)(using c)
        assertTrue(true)
      }
    )
  )
}
