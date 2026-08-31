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

package zio.blocks.sql

/**
 * Entry point for executing SQL against a database.
 *
 * Both methods acquire a connection, execute the provided body, and close the
 * connection upon return (whether the body succeeds or throws).
 *
 * ==Nested transactions (savepoints)==
 *
 * Top-level transactions are started via `transact { ... }`, which opens a new
 * connection, disables auto-commit, and commits/rolls back on exit. Inner
 * (nested) transactions are emulated via SQL savepoints on the *same*
 * connection and are accessed through the ambient [[DbTx]]:
 * {{{
 * transactor.transact {
 *   sql"INSERT INTO t VALUES (1)".update
 *   summon[DbTx].transact {
 *     sql"INSERT INTO t VALUES (2)".update
 *   }
 * }
 * }}}
 * Names are `zib_tx_1 .. zib_tx_N` where N is the nesting depth tracked in
 * [[DbTx.currentDepth]]. Success → `RELEASE SAVEPOINT`, failure →
 * `ROLLBACK TO SAVEPOINT` then rethrow; depth is decremented in `finally` so no
 * savepoint leak occurs and the counter resets for subsequent siblings.
 *
 * ===Given-priority trick (documented)===
 *
 * When a [[DbTx]] is already in scope (inside `transact`), the extension
 * `summon[DbTx].transact { ... }` is preferred over
 * `transactor.transact { ... }` because it resolves via the more specific
 * `DbTx` receiver and reuses the same connection with a savepoint. A
 * transparent-inline `given` trick that would make `Transactor.transact`
 * auto-delegate to the savepoint path when an ambient `DbTx` exists is possible
 * (e.g. `transparent inline given DbTx` with priority), but is not required
 * here: the explicit `summon[DbTx].transact` form is the supported nested path
 * and `Transactor.transact` remains the top-level entry point that opens a new
 * connection. This keeps semantics explicit and avoids implicit-resolution
 * surprises. If desired, a future inline trick can delegate
 * `transactor.transact` to `summon[DbTx].transact` when `summon[DbTx]` is
 * available, but the current implementation documents the pattern instead of
 * hiding it behind implicits.
 *
 * Additionally `transactNested { ... }` (both `DbTx.transactNested` and the
 * top-level `transactNested` helper) is provided as an alias that takes the
 * outer transaction as a `using` parameter.
 */
trait Transactor {

  /** Opens a connection, executes `f`, and closes the connection on return. */
  def connect[A](f: DbCon ?=> A): A

  /**
   * Opens a connection, disables auto-commit, executes `f`, commits on success,
   * and rolls back on exception. The connection is closed after commit or
   * rollback. Preserves the driver's default isolation level and `readOnly`
   * flag (does not force `SERIALIZABLE`); use the overload with explicit
   * `isolation`/`readOnly` to request a specific isolation level. This avoids
   * unexpectedly upgrading PostgreSQL's default `READ_COMMITTED` to
   * `SERIALIZABLE` for callers that used the no-arg form.
   */
  def transact[A](f: DbTx ?=> A): A

  /**
   * Opens a connection, applies the requested isolation level and read-only
   * flag, disables auto-commit, executes `f`, commits on success, and rolls
   * back on exception. The previous isolation level and read-only flag are
   * restored and the connection is closed after commit or rollback.
   *
   * SQLite note: SQLite natively supports only `SERIALIZABLE`; other levels are
   * accepted and the driver is asked to apply them, but the engine still
   * behaves as serializable. See [[TransactionIsolation]] for details.
   *
   * Compatibility note: This is a new method added in 0.3.x. A concrete default
   * is provided that delegates to `transact(f)` and ignores
   * `isolation`/`readOnly` so existing external `Transactor` implementations
   * continue to compile. Implementations that wish to honor isolation should
   * override this method (as `JdbcTransactor` does). This default will be
   * removed in a future major version.
   */
  @scala.annotation.nowarn("msg=unused")
  def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A = transact(f)
}
