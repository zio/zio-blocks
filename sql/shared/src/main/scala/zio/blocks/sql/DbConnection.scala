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

trait DbConnection extends AutoCloseable {
  def prepareStatement(sql: String): DbPreparedStatement
  def prepareStatementReturningKeys(sql: String): DbPreparedStatement
  def close(): Unit
  def isClosed: Boolean
  def setAutoCommit(autoCommit: Boolean): Unit
  def getAutoCommit: Boolean
  def commit(): Unit
  def rollback(): Unit

  /**
   * Create a savepoint with the given name via `SAVEPOINT <name>`.
   *
   * Used by [[DbTx]] nested transactions. Names are `zib_tx_1..N`. Default
   * implementation throws `UnsupportedOperationException`; JDBC implementations
   * (e.g. `JdbcConnection`) override to execute SQL.
   */
  def savepoint(name: String): Unit =
    throw new UnsupportedOperationException("savepoint not supported by this DbConnection implementation")

  /**
   * Release a savepoint via `RELEASE SAVEPOINT <name>`.
   *
   * Called on the success path of a nested transact to avoid leaking
   * savepoints. Default implementation throws `UnsupportedOperationException`;
   * JDBC implementations override.
   */
  def release(name: String): Unit =
    throw new UnsupportedOperationException("release not supported by this DbConnection implementation")

  /**
   * Roll back to a savepoint via `ROLLBACK TO SAVEPOINT <name>`.
   *
   * Called on the failure path of a nested transact to isolate the inner
   * failure without rolling back the outer transaction. Default implementation
   * throws `UnsupportedOperationException`; JDBC implementations override.
   */
  def rollbackTo(name: String): Unit =
    throw new UnsupportedOperationException("rollbackTo not supported by this DbConnection implementation")
}

trait DbPreparedStatement extends AutoCloseable {
  def executeQuery(): DbResultSet
  def executeUpdate(): Int
  def executeUpdateReturningKeys(): DbResultSet
  def close(): Unit
  def paramWriter: DbParamWriter
  def addBatch(): Unit
  def executeBatch(): Array[Int]
}

trait DbResultSet extends AutoCloseable {
  def next(): Boolean
  def close(): Unit
  def reader: DbResultReader
}
