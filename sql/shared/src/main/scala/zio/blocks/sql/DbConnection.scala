package zio.blocks.sql

trait DbConnection extends AutoCloseable {
  def prepareStatement(sql: String): DbPreparedStatement
  def close(): Unit
  def isClosed: Boolean
  def setAutoCommit(autoCommit: Boolean): Unit
  def getAutoCommit: Boolean
  def commit(): Unit
  def rollback(): Unit
}

trait DbPreparedStatement extends AutoCloseable {
  def executeQuery(): DbResultSet
  def executeUpdate(): Int
  def close(): Unit
  def paramWriter: DbParamWriter
}

trait DbResultSet extends AutoCloseable {
  def next(): Boolean
  def close(): Unit
  def reader: DbResultReader
}
