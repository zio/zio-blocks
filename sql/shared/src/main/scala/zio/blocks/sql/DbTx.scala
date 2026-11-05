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
 * The connection that `Transactor#transact` lends to its block, with
 * auto-commit off: the transaction commits when the block returns and rolls
 * back when it throws. It extends [[DbCon]], so every [[Frag]] and [[Repo]]
 * operation accepts it. Inside the block, `summon[DbTx].transact { ... }` runs
 * a nested block under a savepoint on the same connection: when the nested
 * block throws, only its own statements are undone and the exception is
 * rethrown. Only the outermost block commits or rolls back.
 *
 * {{{
 * val db = java.nio.file.Files.createTempFile("orders", ".db")
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
 * xa.connect(sql"CREATE TABLE audit (event TEXT)".update)
 *
 * val events = xa.transact {
 *   sql"INSERT INTO audit VALUES ('order placed')".update
 *   scala.util.Try {
 *     summon[DbTx].transact {
 *       sql"INSERT INTO audit VALUES ('coupon applied')".update
 *       throw new IllegalArgumentException("coupon expired")
 *     }
 *   }
 *   sql"INSERT INTO audit VALUES ('order paid')".update
 *   sql"SELECT event FROM audit".query[String]
 * }
 * // List(order placed, order paid)
 * }}}
 *
 * Savepoints are named `zib_tx_1`, `zib_tx_2`, ... by nesting depth, so sibling
 * blocks at the same depth reuse a name. The savepoint methods of this trait
 * throw `UnsupportedOperationException` unless an implementation overrides
 * them, as the `DbTx` of `JdbcTransactor` and of `TransactorZIO` do.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-tx/ DbTx]]
 */
trait DbTx extends DbCon {

  /**
   * Starts a savepoint with `SAVEPOINT <name>`. Used by the nested `transact`;
   * the default throws `UnsupportedOperationException`.
   */
  def savepoint(name: String): Unit =
    throw new UnsupportedOperationException("savepoint not supported by this DbTx implementation; use JdbcTransactor")

  /**
   * Ends a savepoint, keeping its work, with `RELEASE SAVEPOINT <name>`. The
   * nested `transact` calls it when its block returns; the default throws
   * `UnsupportedOperationException`.
   */
  def release(name: String): Unit =
    throw new UnsupportedOperationException("release not supported by this DbTx implementation; use JdbcTransactor")

  /**
   * Undoes the work done since a savepoint with `ROLLBACK TO SAVEPOINT <name>`.
   * The nested `transact` calls it when its block throws and does not release
   * the savepoint afterwards; the outer commit or rollback ends it. The default
   * throws `UnsupportedOperationException`.
   */
  def rollbackTo(name: String): Unit =
    throw new UnsupportedOperationException("rollbackTo not supported by this DbTx implementation; use JdbcTransactor")

  /**
   * How many nested `transact` blocks are open: 0 in the outermost block, 1
   * inside the first nested one.
   */
  def currentDepth: Int = 0

  /** Set by the nested `transact`; a no-op unless overridden. */
  private[sql] def currentDepth_=(depth: Int): Unit = ()
}

/** Helpers for nested transactions on a [[DbTx]]. */
object DbTx {

  /**
   * Runs `f` in a nested transaction of the [[DbTx]] in scope, like
   * `summon[DbTx].transact(f)`. Useful in a helper that takes the transaction
   * as a context parameter.
   *
   * {{{
   * def recordView(page: String)(using DbTx): Int =
   *   DbTx.transactNested {
   *     sql"INSERT INTO page_view VALUES (\$page)".update
   *   }
   *
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val views = xa.transact {
   *   sql"CREATE TABLE page_view (page TEXT)".update
   *   recordView("/checkout")
   *   sql"SELECT page FROM page_view".query[String]
   * }
   * // List(/checkout)
   * }}}
   */
  def transactNested[A](f: DbTx ?=> A)(using outer: DbTx): A =
    outer.transact(f)
}

extension (outer: DbTx) {

  /**
   * Runs `f` in a nested transaction on the same connection: creates savepoint
   * `zib_tx_<n>`, where `n` is `outer.currentDepth + 1`, runs `f` with
   * `currentDepth` set to `n`, then releases the savepoint when `f` returns.
   * When `f` throws, it rolls back to the savepoint (adding any rollback
   * failure to the exception as suppressed) and rethrows, so the enclosing
   * block decides whether to go on. `currentDepth` goes back to `n - 1` either
   * way. The enclosing transaction's commit decides whether the nested work is
   * kept.
   *
   * {{{
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val depths = xa.transact {
   *   val outer = summon[DbTx].currentDepth
   *   val inner = summon[DbTx].transact(summon[DbTx].currentDepth)
   *   (outer, inner)
   * }
   * // (0,1)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-tx/ DbTx]]
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

/**
 * Runs `f` in a nested transaction of the [[DbTx]] in scope: the same as
 * `DbTx.transactNested(f)`.
 */
def transactNested[A](f: DbTx ?=> A)(using outer: DbTx): A =
  outer.transact(f)
