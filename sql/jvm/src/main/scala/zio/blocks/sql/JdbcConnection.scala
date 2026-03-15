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
