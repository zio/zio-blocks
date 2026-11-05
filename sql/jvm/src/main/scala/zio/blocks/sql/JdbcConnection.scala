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

import java.sql.Connection

/** The [[DbConnection]] over a `java.sql.Connection`. */
private[sql] class JdbcConnection(val underlying: Connection) extends DbConnection {

  def prepareStatement(sql: String): DbPreparedStatement =
    new JdbcPreparedStatement(underlying.prepareStatement(sql))

  def prepareStatementReturningKeys(sql: String): DbPreparedStatement =
    new JdbcPreparedStatement(underlying.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS))

  def close(): Unit = underlying.close()

  def isClosed: Boolean = underlying.isClosed

  def setAutoCommit(autoCommit: Boolean): Unit = underlying.setAutoCommit(autoCommit)

  def getAutoCommit: Boolean = underlying.getAutoCommit

  def commit(): Unit = underlying.commit()

  def rollback(): Unit = underlying.rollback()

  /**
   * Runs `SAVEPOINT <name>` on a new `Statement`, closed afterwards, instead of
   * keeping `java.sql.Savepoint` objects; `release` and `rollbackTo` do the
   * same with `RELEASE SAVEPOINT` and `ROLLBACK TO SAVEPOINT`, so a nested
   * transaction costs two statements. Each checks `name` with
   * `SqlIdentifier.validate` first, because the name is written unquoted.
   */
  override def savepoint(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"SAVEPOINT $name")
    finally stmt.close()
  }

  /** Runs `RELEASE SAVEPOINT <name>` after checking `name`. */
  override def release(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"RELEASE SAVEPOINT $name")
    finally stmt.close()
  }

  /** Runs `ROLLBACK TO SAVEPOINT <name>` after checking `name`. */
  override def rollbackTo(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"ROLLBACK TO SAVEPOINT $name")
    finally stmt.close()
  }
}

/** The [[DbPreparedStatement]] over a `java.sql.PreparedStatement`. */
private[sql] class JdbcPreparedStatement(val underlying: java.sql.PreparedStatement) extends DbPreparedStatement {

  def executeQuery(): DbResultSet =
    new JdbcResultSet(underlying.executeQuery())

  def executeUpdate(): Int = underlying.executeUpdate()

  def executeUpdateReturningKeys(): DbResultSet = {
    underlying.executeUpdate()
    new JdbcResultSet(underlying.getGeneratedKeys)
  }

  def close(): Unit = underlying.close()

  def paramWriter: DbParamWriter = new JdbcParamWriter(underlying)

  def addBatch(): Unit = underlying.addBatch()

  def executeBatch(): Array[Int] = underlying.executeBatch()
}

/** The [[DbResultSet]] over a `java.sql.ResultSet`. */
private[sql] class JdbcResultSet(val underlying: java.sql.ResultSet) extends DbResultSet {

  /**
   * Single reader instance shared across the result set's lifetime so the
   * per-row null bitmap stays in sync with row advancement: `next()` resets it
   * via [[JdbcResultReader.beginRow]] before each row is decoded.
   */
  private val readerInstance = new JdbcResultReader(underlying)

  def next(): Boolean = {
    val advanced = underlying.next()
    if (advanced) readerInstance.beginRow()
    advanced
  }

  def close(): Unit = underlying.close()

  def reader: DbResultReader = readerInstance
}
