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
 * Runs code that needs a database connection. `connect` lends a connection as a
 * [[DbCon]] for the length of a block, and `transact` does the same inside a
 * transaction, as a [[DbTx]]: it commits when the block returns and rolls back
 * when it throws. Either way the connection is closed when the block ends, and
 * any [[Frag]] or [[Repo]] call in the block picks it up as a context
 * parameter. `JdbcTransactor` is the JDBC implementation.
 *
 * {{{
 * val db = java.nio.file.Files.createTempFile("shop", ".db")
 * val xa: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
 *
 * xa.transact {
 *   sql"CREATE TABLE customer (id INTEGER, name TEXT)".update
 *   sql"INSERT INTO customer VALUES (1, 'Ada'), (2, 'Grace')".update
 * }
 * val names = xa.connect {
 *   sql"SELECT name FROM customer ORDER BY id".query[String]
 * }
 * // List(Ada, Grace)
 * }}}
 *
 * Inside `transact`, `summon[DbTx].transact { ... }` runs a nested block under
 * a savepoint on the same connection; see [[DbTx]].
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/transactor/ Transactor]]
 */
trait Transactor {

  /**
   * Opens a connection, runs `f` with it as the given [[DbCon]], and closes the
   * connection when `f` returns or throws. The connection keeps its auto-commit
   * setting (on, for a JDBC driver's new connection), so each statement commits
   * as it runs. Anything that reads from the connection later, such as a stream
   * from `Frag#queryStream` returned out of `f`, fails once the connection is
   * closed.
   *
   * {{{
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val autoCommit = xa.connect {
   *   summon[DbCon].connection.getAutoCommit
   * }
   * // true
   * }}}
   */
  def connect[A](f: DbCon ?=> A): A

  /**
   * Opens a connection, turns auto-commit off, runs `f` with the connection as
   * the given [[DbTx]], commits when `f` returns and rolls back when it throws,
   * rethrowing the exception. The connection is closed afterwards. The driver's
   * isolation level and read-only flag are left as they are; the overload that
   * takes a [[TransactionIsolation]] sets them.
   *
   * {{{
   * val db = java.nio.file.Files.createTempFile("stock", ".db")
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
   * xa.connect(sql"CREATE TABLE stock (sku TEXT, qty INTEGER)".update)
   *
   * val attempt = scala.util.Try {
   *   xa.transact {
   *     sql"INSERT INTO stock VALUES ('KB-01', 5)".update
   *     throw new IllegalStateException("supplier rejected the order")
   *   }
   * }
   * val rows = xa.connect(sql"SELECT count(*) FROM stock".queryOne[Int].toOption)
   * // attempt: Failure(java.lang.IllegalStateException: supplier rejected the order)
   * // rows: Some(0)
   * }}}
   */
  def transact[A](f: DbTx ?=> A): A

  /**
   * Runs `f` in a transaction like `transact(f)`, with the connection's
   * isolation level set to `isolation` and its read-only flag to `readOnly` for
   * the transaction. `JdbcTransactor` restores the previous level and flag
   * afterwards; a failure to set the level is thrown before `f` runs, while a
   * failure to set the read-only flag is ignored.
   *
   * {{{
   * val db = java.nio.file.Files.createTempFile("report", ".db")
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
   * xa.connect {
   *   sql"CREATE TABLE sale (amount INTEGER)".update
   *   sql"INSERT INTO sale VALUES (30), (12)".update
   * }
   * val total = xa.transact(TransactionIsolation.Serializable, readOnly = true) {
   *   sql"SELECT sum(amount) FROM sale".queryOne[Int].toOption
   * }
   * // Some(42)
   * }}}
   *
   * On SQLite the read-only flag has no effect: an `INSERT` in a read-only
   * transaction succeeds. SQLite transactions are serializable at every level;
   * see [[TransactionIsolation]].
   *
   * This default implementation ignores `isolation` and `readOnly` and calls
   * `transact(f)`, so a `Transactor` written before this overload existed still
   * compiles; `JdbcTransactor` overrides it.
   */
  @scala.annotation.nowarn("msg=unused")
  def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A = transact(f)
}
