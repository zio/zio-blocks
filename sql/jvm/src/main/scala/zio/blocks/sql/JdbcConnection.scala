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

class JdbcConnection(val underlying: Connection) extends DbConnection {

  def prepareStatement(sql: String): DbPreparedStatement =
    new JdbcPreparedStatement(underlying.prepareStatement(sql))

  def close(): Unit = underlying.close()

  def isClosed: Boolean = underlying.isClosed

  def setAutoCommit(autoCommit: Boolean): Unit = underlying.setAutoCommit(autoCommit)

  def getAutoCommit: Boolean = underlying.getAutoCommit

  def commit(): Unit = underlying.commit()

  def rollback(): Unit = underlying.rollback()
}

class JdbcPreparedStatement(val underlying: java.sql.PreparedStatement) extends DbPreparedStatement {

  def executeQuery(): DbResultSet =
    new JdbcResultSet(underlying.executeQuery())

  def executeUpdate(): Int = underlying.executeUpdate()

  def close(): Unit = underlying.close()

  def paramWriter: DbParamWriter = new JdbcParamWriter(underlying)

  def addBatch(): Unit = underlying.addBatch()

  def executeBatch(): Array[Int] = underlying.executeBatch()
}

class JdbcResultSet(val underlying: java.sql.ResultSet) extends DbResultSet {

  def next(): Boolean = underlying.next()

  def close(): Unit = underlying.close()

  def reader: DbResultReader = new JdbcResultReader(underlying)
}
