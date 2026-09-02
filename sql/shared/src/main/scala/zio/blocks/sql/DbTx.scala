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
 * Transactional connection with savepoint support for nested transactions.
 *
 * Savepoint semantics: nested transactions are emulated via SQL savepoints
 * named `zib_tx_1 .. zib_tx_N` where N is the nesting depth. On success the
 * savepoint is released via `RELEASE SAVEPOINT`, on failure the transaction
 * rolls back to the savepoint via `ROLLBACK TO SAVEPOINT` and rethrows the
 * exception. Only the outermost `Transactor.transact` does a real
 * `COMMIT`/`ROLLBACK`; inner blocks are savepoint-scoped and share the same
 * underlying JDBC connection.
 *
 * Depth tracking: each [[DbTx]] instance tracks its current nesting depth (0 =
 * outermost). The counter is incremented only after the savepoint is created
 * successfully and decremented in a `finally` block, guaranteeing correct depth
 * even if savepoint creation throws and reset even when inner blocks throw.
 */
trait DbTx extends DbCon {

  /**
   * Create a savepoint with the given name.
   *
   * Implemented via `SAVEPOINT <name>` SQL for portability (SQLite,
   * PostgreSQL). The underlying connection must be inside an active transaction
   * (autoCommit = false).
   */
  def savepoint(name: String): Unit =
    throw new UnsupportedOperationException("savepoint not supported by this DbTx implementation; use JdbcTransactor")

  /**
   * Release the savepoint with the given name.
   *
   * Implemented via `RELEASE SAVEPOINT <name>`. No savepoint leak on the
   * success path: every successful nested `transact` releases its savepoint
   * before returning.
   */
  def release(name: String): Unit =
    throw new UnsupportedOperationException("release not supported by this DbTx implementation; use JdbcTransactor")

  /**
   * Roll back to the savepoint with the given name.
   *
   * Implemented via `ROLLBACK TO SAVEPOINT <name>`. The savepoint remains valid
   * after rollback on most drivers; on failure only depth is decremented and
   * the savepoint is not released (it will be cleaned up when outer transaction
   * commits/rolls back).
   */
  def rollbackTo(name: String): Unit =
    throw new UnsupportedOperationException("rollbackTo not supported by this DbTx implementation; use JdbcTransactor")

  def currentDepth: Int = 0

  private[sql] def currentDepth_=(depth: Int): Unit = ()
}

object DbTx {

  /**
   * Nested transaction via savepoint using an ambient [[DbTx]].
   *
   * Example:
   * {{{
   * transactor.transact {
   *   sql"INSERT INTO t VALUES (1)".update
   *   summon[DbTx].transact {
   *     sql"INSERT INTO t VALUES (2)".update
   *   }
   * }
   * }}}
   *
   * Also available as `transactNested` for call sites that prefer a `using`
   * parameter over an extension receiver.
   */
  def transactNested[A](f: DbTx ?=> A)(using outer: DbTx): A =
    outer.transact(f)
}

/**
 * Savepoint-based nested transaction support.
 *
 * Names are `zib_tx_1 .. zib_tx_N` where N is the nesting depth derived from
 * [[DbTx.currentDepth]]. Success → `RELEASE SAVEPOINT`, failure →
 * `ROLLBACK TO SAVEPOINT` then rethrow. Depth is decremented in `finally` so
 * the counter resets even after failures, allowing subsequent nested blocks to
 * reuse the same name sequence without leaking.
 *
 * Given-priority trick (documented): When a [[DbTx]] is already in scope
 * (inside `Transactor.transact`), the extension `summon[DbTx].transact { ... }`
 * is preferred over `transactor.transact { ... }` because it resolves via the
 * more specific `DbTx` receiver and reuses the same connection with a
 * savepoint. A transparent-inline `given` trick that would make
 * `Transactor.transact` auto-delegate to the savepoint path when an ambient
 * `DbTx` exists is possible (e.g. `transparent inline given DbTx` with
 * priority), but is not required here: the explicit `summon[DbTx].transact`
 * form is the supported nested path and `Transactor.transact` remains the
 * top-level entry point that opens a new connection. This keeps semantics
 * explicit and avoids implicit-resolution surprises. If desired, a future
 * `given Conversion` or inline trick can delegate `transactor.transact` to
 * `summon[DbTx].transact` when `summon[DbTx]` is available, but the current
 * implementation documents the pattern instead of hiding it behind implicits.
 */
extension (outer: DbTx) {

  /**
   * Execute `f` inside a savepoint nested transaction.
   *
   * Creates savepoint `zib_tx_<depth>` where depth is `outer.currentDepth + 1`,
   * runs `f` with the same underlying connection (given `DbTx` is `outer` with
   * incremented depth), releases on success and rolls back to the savepoint on
   * failure. Depth is always decremented in `finally`. On failure only depth is
   * decremented and the savepoint is not released (it will be cleaned up when
   * outer transaction commits/rolls back).
   */
  def transact[A](f: DbTx ?=> A): A = {
    val depth = outer.currentDepth + 1
    val name  = s"zib_tx_$depth"
    outer.savepoint(name)
    outer.currentDepth = depth
    try {
      given DbTx = outer
      val result =
        try f
        catch {
          case e: Throwable =>
            try outer.rollbackTo(name)
            catch { case rb: Throwable => e.addSuppressed(rb) }
            throw e
        }
      outer.release(name)
      result
    } finally {
      outer.currentDepth = depth - 1
    }
  }
}

/** Top-level helper that mirrors `DbTx.transactNested` with `using` syntax. */
def transactNested[A](f: DbTx ?=> A)(using outer: DbTx): A =
  outer.transact(f)
