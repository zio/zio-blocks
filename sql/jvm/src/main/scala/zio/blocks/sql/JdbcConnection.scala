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
   * Savepoint support via SQL strings for portability.
   *
   * Uses `SAVEPOINT <name>`, `RELEASE SAVEPOINT <name>`, and
   * `ROLLBACK TO SAVEPOINT <name>` executed via a fresh Statement. This avoids
   * tracking `java.sql.Savepoint` objects and works on both SQLite and
   * PostgreSQL. The statement is closed in `finally` to avoid leaking
   * resources. Names are expected to be simple identifiers like `zib_tx_1` so
   * no quoting/escaping is needed.
   *
   * Cost: Each savepoint operation allocates a new JDBC `Statement` (two per
   * nested transaction: one for `SAVEPOINT`, one for `RELEASE` or
   * `ROLLBACK TO`). This is negligible for typical nesting depths (1-3) and
   * avoids holding `Savepoint` objects, but very deep or high-frequency nesting
   * should be benchmarked if it becomes hot.
   */
  override def savepoint(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"SAVEPOINT $name")
    finally stmt.close()
  }

  override def release(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"RELEASE SAVEPOINT $name")
    finally stmt.close()
  }

  override def rollbackTo(name: String): Unit = {
    SqlIdentifier.validate("savepoint", name)
    val stmt = underlying.createStatement()
    try stmt.execute(s"ROLLBACK TO SAVEPOINT $name")
    finally stmt.close()
  }
}

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
