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
 * An open connection lent to a block by `Transactor#connect`, together with the
 * dialect to render SQL for and the logger that statement events go to. Every
 * [[Frag]] and [[Repo]] operation takes one as a context parameter, so a helper
 * that declares `(using DbCon)` works inside `connect` and, since [[DbTx]]
 * extends `DbCon`, inside `transact` too. The connection is valid only until
 * the block returns.
 *
 * {{{
 * def countRows(table: SqlLiteral)(using DbCon): Int =
 *   sql"SELECT count(*) FROM \$table".queryOne[Int].getOrElse(0)
 *
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
 * val n = xa.connect {
 *   sql"CREATE TABLE cart_item (sku TEXT)".update
 *   sql"INSERT INTO cart_item VALUES ('KB-01'), ('MS-02')".update
 *   countRows(SqlLiteral("cart_item"))
 * }
 * // 2
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-con/ DbCon]]
 */
trait DbCon {

  /**
   * The open connection. The transactor closes it when the block ends, so don't
   * close it yourself.
   */
  def connection: DbConnection

  /** The dialect that fragments are rendered for on this connection. */
  def dialect: SqlDialect

  /** The logger that [[Frag]] and [[Repo]] operations report statements to. */
  def logger: SqlLogger
}
