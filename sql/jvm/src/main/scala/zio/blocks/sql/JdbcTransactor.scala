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

import java.sql.{Connection, DriverManager}
import java.util.Properties

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

  /**
   * Opens a connection, disables auto-commit, executes `f`, commits on success,
   * and rolls back on exception. The connection is closed after commit or
   * rollback.
   *
   * For `SqlDialect.SQLite`, connections are created with `busy_timeout=5000`
   * and `transaction_mode=IMMEDIATE` (see `fromUrl` / `sqlite`) so that queue
   * workers reserve the write lock before the `SELECT` and wait on contention
   * instead of failing the later `DELETE` with `SQLITE_BUSY`. See PR #1534
   * discussion_r3863454094; SQLite is single-consumer.
   */
  def transact[A](f: DbTx ?=> A): A = {
    val conn           = connectionFactory()
    val dbConn         = new JdbcConnection(conn)
    val prevAutoCommit = conn.getAutoCommit
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
      try conn.setAutoCommit(prevAutoCommit)
      catch { case _: Throwable => () }
      try dbConn.close()
      catch { case _: Throwable => () }
    }
  }
}

object JdbcTransactor {

  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor =
    if (dialect == SqlDialect.SQLite) {
      val props = new Properties()
      props.setProperty("busy_timeout", "5000")
      props.setProperty("transaction_mode", "IMMEDIATE")
      new JdbcTransactor(() => DriverManager.getConnection(url, props), dialect)
    } else new JdbcTransactor(() => DriverManager.getConnection(url), dialect)

  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor =
    if (dialect == SqlDialect.SQLite) {
      val props = new Properties()
      props.setProperty("busy_timeout", "5000")
      props.setProperty("transaction_mode", "IMMEDIATE")
      props.setProperty("user", user)
      props.setProperty("password", password)
      new JdbcTransactor(() => DriverManager.getConnection(url, props), dialect)
    } else new JdbcTransactor(() => DriverManager.getConnection(url, user, password), dialect)

  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): JdbcTransactor = {
    // For SQLite, try to configure the DataSource for IMMEDIATE + busy timeout
    // when it is a SQLiteDataSource; otherwise the connection will still work
    // as DEFERRED (tests use a mock DataSource). Use reflection to avoid a
    // hard compile-time dependency on sqlite-jdbc in main.
    if (dialect == SqlDialect.SQLite) {
      try {
        val clazz = Class.forName("org.sqlite.SQLiteDataSource")
        if (clazz.isInstance(dataSource)) {
          val setBusy = clazz.getMethod("setBusyTimeout", classOf[Int])
          setBusy.invoke(dataSource, Integer.valueOf(5000))
          val setMode = clazz.getMethod("setTransactionMode", classOf[String])
          setMode.invoke(dataSource, "IMMEDIATE")
        }
      } catch { case _: Throwable => () }
    }
    new JdbcTransactor(() => dataSource.getConnection, dialect)
  }

  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.PostgreSQL)

  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.SQLite)
}
