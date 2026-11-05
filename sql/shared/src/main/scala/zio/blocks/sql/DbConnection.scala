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
 * The JDBC-independent view of a database connection that a [[DbCon]] carries.
 * [[Frag]] and [[Repo]] run their statements through it; the JVM implementation
 * wraps a `java.sql.Connection`. You rarely call it directly: the transactor
 * opens, commits, rolls back and closes it, so calling `close`, `commit`,
 * `rollback` or `setAutoCommit` inside a block interferes with that.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-connection/ DbConnection]]
 */
trait DbConnection extends AutoCloseable {

  /** Prepares `sql`, which uses `?` placeholders, as a statement. */
  def prepareStatement(sql: String): DbPreparedStatement

  /**
   * Prepares `sql` as a statement whose generated keys can be read with
   * `DbPreparedStatement#executeUpdateReturningKeys`.
   */
  def prepareStatementReturningKeys(sql: String): DbPreparedStatement

  /** Closes the connection. */
  def close(): Unit

  /** Returns `true` once the connection is closed. */
  def isClosed: Boolean

  /** Turns auto-commit on or off. */
  def setAutoCommit(autoCommit: Boolean): Unit

  /** Returns `true` when each statement commits as it runs. */
  def getAutoCommit: Boolean

  /** Commits the current transaction. */
  def commit(): Unit

  /** Rolls back the current transaction. */
  def rollback(): Unit

  /**
   * Starts a savepoint with `SAVEPOINT <name>`, for the nested transactions of
   * [[DbTx]] (names `zib_tx_1`, `zib_tx_2`, ...). The default throws
   * `UnsupportedOperationException`; the JDBC implementation runs the SQL after
   * checking that `name` is made of ASCII letters, digits and underscores.
   */
  def savepoint(name: String): Unit =
    throw new UnsupportedOperationException("savepoint not supported by this DbConnection implementation")

  /**
   * Ends a savepoint, keeping its work, with `RELEASE SAVEPOINT <name>`. A
   * nested `DbTx#transact` calls it when its block returns. The default throws
   * `UnsupportedOperationException`.
   */
  def release(name: String): Unit =
    throw new UnsupportedOperationException("release not supported by this DbConnection implementation")

  /**
   * Undoes the work done since a savepoint with `ROLLBACK TO SAVEPOINT <name>`,
   * leaving the work before it in the transaction. A nested `DbTx#transact`
   * calls it when its block throws. The default throws
   * `UnsupportedOperationException`.
   */
  def rollbackTo(name: String): Unit =
    throw new UnsupportedOperationException("rollbackTo not supported by this DbConnection implementation")
}

/**
 * A prepared statement of a [[DbConnection]]. Bind its parameters through
 * `paramWriter` (1-based positions), then run it.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-connection/ DbConnection]]
 */
trait DbPreparedStatement extends AutoCloseable {

  /** Runs the statement as a query and returns its rows. */
  def executeQuery(): DbResultSet

  /** Runs the statement and returns the number of rows it changed. */
  def executeUpdate(): Int

  /**
   * Runs the statement and returns the keys the database generated. The
   * statement must come from `DbConnection#prepareStatementReturningKeys`.
   */
  def executeUpdateReturningKeys(): DbResultSet

  /** Closes the statement. */
  def close(): Unit

  /** Returns a writer that binds values to this statement's parameters. */
  def paramWriter: DbParamWriter

  /** Adds the parameters bound so far to the statement's batch. */
  def addBatch(): Unit

  /** Runs the batch and returns the row count of each of its entries. */
  def executeBatch(): Array[Int]
}

/**
 * The rows of a query, read one at a time: call `next()`, then read the current
 * row through `reader`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-connection/ DbConnection]]
 */
trait DbResultSet extends AutoCloseable {

  /** Moves to the next row and returns `false` when there is none. */
  def next(): Boolean

  /** Closes the result set. */
  def close(): Unit

  /** Returns the reader for the current row's columns. */
  def reader: DbResultReader
}
