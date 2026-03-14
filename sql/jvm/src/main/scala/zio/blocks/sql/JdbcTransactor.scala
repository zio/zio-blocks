package zio.blocks.sql

import java.sql.{Connection, DriverManager}

class JdbcTransactor(connectionFactory: () => Connection, val dialect: SqlDialect) extends Transactor {

  def connect[A](f: DbCon ?=> A): A = {
    val conn   = connectionFactory()
    val dbConn = new JdbcConnection(conn)
    try {
      given con: DbCon = new DbCon {
        val connection: DbConnection = dbConn
        val dialect: SqlDialect      = JdbcTransactor.this.dialect
      }
      f
    } finally dbConn.close()
  }

  def transact[A](f: DbTx ?=> A): A = {
    val conn   = connectionFactory()
    val dbConn = new JdbcConnection(conn)
    conn.setAutoCommit(false)
    try {
      given tx: DbTx = new DbTx {
        val connection: DbConnection = dbConn
        val dialect: SqlDialect      = JdbcTransactor.this.dialect
      }
      val result = f
      conn.commit()
      result
    } catch {
      case e: Throwable =>
        conn.rollback()
        throw e
    } finally dbConn.close()
  }
}

object JdbcTransactor {

  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(url), dialect)

  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(url, user, password), dialect)
}
