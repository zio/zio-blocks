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

import zio.blocks.sql.*
import zio.test.*

object TinyMigratorSpec extends ZIOSpecDefault {

  // Stub Transactor that records whether transact was called
  final class RecordingTransactor extends Transactor {
    var transactCalled: Boolean = false

    override def connect[A](f: DbCon ?=> A): A =
      // Not used by TinyMigrator; provide a dummy implementation
      f(using
        new DbCon {
          def connection: DbConnection = ???
          def dialect: SqlDialect      = ???
          def logger: SqlLogger        = ???
        }
      )

    override def transact[A](f: DbTx ?=> A): A = {
      transactCalled = true
      f(using
        new DbTx {
          def connection: DbConnection = ???
          def dialect: SqlDialect      = ???
          def logger: SqlLogger        = ???
        }
      )
    }
  }

  // Concrete subclass for compile-time signature test
  class TestStartupMigrations(transactor: Transactor) extends TinyMigrator(transactor) {
    override def run()(using tx: DbTx): Unit = {
      // Empty implementation for signature test
    }
  }

  def spec: Spec[Any, Any] =
    suite("TinyMigrator")(
      test("concrete subclass compiles and instantiates") {
        val tx   = new RecordingTransactor()
        val migr = new TestStartupMigrations(tx)
        assertTrue(migr != null)
      },
      test("migrate() calls transactor.transact") {
        val tx   = new RecordingTransactor()
        val migr = new TestStartupMigrations(tx)
        migr.migrate()
        assertTrue(tx.transactCalled)
      },
      test("DDL operations compile inside run()") {
        // This test verifies that Frag/Ddl operations are usable inside run()
        // Actual execution requires a real DB; here we only check compilation
        val tx   = new RecordingTransactor()
        val migr = new TinyMigrator(tx) {
          override def run()(using tx: DbTx): Unit = {
            // DDL operations must type-check with DbTx (subtype of DbCon)
            val _ = Ddl
              .createTable(
                "test_table",
                IndexedSeq(
                  ColumnDef("id", "BIGSERIAL", nullable = false),
                  ColumnDef("name", "TEXT", nullable = true)
                )
              )
              .update
          }
        }
        // Just ensure instantiation succeeds; execution tested in integration
        assertTrue(migr != null)
      }
    )
}
