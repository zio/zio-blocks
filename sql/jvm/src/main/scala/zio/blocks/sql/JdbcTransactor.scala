package zio.blocks.sql

import java.sql.{Connection, DriverManager}

class JdbcTransactor(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val sqlLogger: SqlLogger = SqlLogger.noop
) extends Transactor {

  def connect[A](f: DbCon ?=> A): A = {
    val conn   = connectionFactory()
    val dbConn = new JdbcConnection(conn)
    try {
      given con: DbCon = new DbCon {
        val connection: DbConnection = dbConn
        val dialect: SqlDialect      = JdbcTransactor.this.dialect
        val logger: SqlLogger        = JdbcTransactor.this.sqlLogger
      }
      f
    } finally {
      try dbConn.close()
      catch { case _: Throwable => () }
    }
  }

  def transact[A](f: DbTx ?=> A): A = {
    val conn   = connectionFactory()
    val dbConn = new JdbcConnection(conn)
    conn.setAutoCommit(false)
    try {
      given tx: DbTx = new DbTx {
        val connection: DbConnection = dbConn
        val dialect: SqlDialect      = JdbcTransactor.this.dialect
        val logger: SqlLogger        = JdbcTransactor.this.sqlLogger
      }
      val result = f
      conn.commit()
      result
    } catch {
      case e: Throwable =>
        try conn.rollback()
        catch { case rb: Throwable => e.addSuppressed(rb) }
        throw e
    } finally {
      try dbConn.close()
      catch { case _: Throwable => () }
    }
  }
}

object JdbcTransactor {

  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(url), dialect)

  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(url, user, password), dialect)

  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => dataSource.getConnection, dialect)
}
