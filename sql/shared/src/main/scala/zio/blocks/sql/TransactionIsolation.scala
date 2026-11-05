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
 * The isolation level to request with `Transactor#transact(isolation,
 * readOnly)`. Each case carries the matching `java.sql.Connection` constant as
 * `jdbcLevel` (1, 2, 4, 8), written as a number so the shared sources need no
 * `java.sql`.
 *
 * {{{
 * TransactionIsolation.ReadCommitted.jdbcLevel // 2
 * }}}
 *
 * The level is passed to `Connection#setTransactionIsolation`. The SQLite
 * driver accepts all four and reports back the one set, but SQLite runs every
 * transaction serializably (`READ UNCOMMITTED` matters only in its shared-cache
 * mode).
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/transactor/ Transactor]]
 */
enum TransactionIsolation(val jdbcLevel: Int) {

  /** May read rows that other transactions have not committed. */
  case ReadUncommitted extends TransactionIsolation(1) // JDBC TRANSACTION_READ_UNCOMMITTED = 1

  /** Reads only committed rows; PostgreSQL's default. */
  case ReadCommitted extends TransactionIsolation(2) // JDBC TRANSACTION_READ_COMMITTED = 2

  /** Rows read once read the same again within the transaction. */
  case RepeatableRead extends TransactionIsolation(4) // JDBC TRANSACTION_REPEATABLE_READ = 4

  /** Transactions behave as if they ran one after another. */
  case Serializable extends TransactionIsolation(8) // JDBC TRANSACTION_SERIALIZABLE = 8
}
