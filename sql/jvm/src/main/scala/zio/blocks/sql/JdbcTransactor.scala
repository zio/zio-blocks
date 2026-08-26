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

import java.lang.reflect.InvocationTargetException
import java.sql.{Connection, DriverManager, SQLException}
import java.util.Properties

class JdbcTransactor(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val sqlLogger: SqlLogger = SqlLogger.noop
) extends Transactor {

  def connect[A](f: DbCon ?=> A): A = {
    val conn = connectionFactory()
    try {
      if (dialect == SqlDialect.SQLite) JdbcTransactor.configureSQLiteConnection(conn)
    } catch {
      case e: Throwable =>
        try conn.close()
        catch { case ce: Throwable => e.addSuppressed(ce) }
        throw e
    }
    val dbConn = new JdbcConnection(conn)
    try {
      if (dialect == SqlDialect.SQLite) {
        val stmt = conn.createStatement()
        try stmt.execute("PRAGMA busy_timeout = 5000")
        finally stmt.close()
      }
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
   * Opens a connection, applies the requested isolation level and read-only
   * flag, disables auto-commit, executes `f`, commits on success, and rolls
   * back on exception. Previous `autoCommit`, isolation level and `readOnly`
   * flag are restored and the connection is closed after commit or rollback.
   *
   * For `SqlDialect.SQLite`, `transact` runs with `busy_timeout=5000` and
   * `IMMEDIATE` — `fromUrl` and `sqlite` create SQLite connections with
   * `busy_timeout=5000` and `transaction_mode=IMMEDIATE`, and `transact` also
   * configures any SQLite `Connection` (including pooled/wrapped via
   * `isWrapperFor`/`unwrap`) per-transaction so queue workers reserve the write
   * lock before the `SELECT` and wait on contention instead of failing the
   * later `DELETE` with `SQLITE_BUSY`. See PR #1534 discussion_r3863454094;
   * SQLite is single-consumer.
   *
   * SQLite note: SQLite natively supports only `SERIALIZABLE` (its default).
   * Other levels are accepted and applied via
   * `Connection.setTransactionIsolation` so behaviour is uniform across
   * dialects, but on SQLite the engine still behaves as serializable — the
   * requested level may be ignored by the engine even though it is set on the
   * connection.
   */
  override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A = {
    val conn = connectionFactory()
    try {
      if (dialect == SqlDialect.SQLite) JdbcTransactor.configureSQLiteConnection(conn)
    } catch {
      case e: Throwable =>
        try conn.close()
        catch { case ce: Throwable => e.addSuppressed(ce) }
        throw e
    }
    val dbConn         = new JdbcConnection(conn)
    val prevAutoCommit =
      try conn.getAutoCommit
      catch {
        case e: Throwable =>
          try dbConn.close()
          catch { case ce: Throwable => e.addSuppressed(ce) }
          throw e
      }
    val prevIsolation = conn.getTransactionIsolation
    val prevReadOnly  =
      try conn.isReadOnly
      catch { case _: Throwable => false }
    try {
      // Fail fast: isolation failures must propagate (do not swallow); the
      // outer finally will restore previous settings and close the connection.
      conn.setTransactionIsolation(isolation.jdbcLevel)
      // Intentionally swallow readOnly failures: SQLite (and some other drivers)
      // throw on setReadOnly even though transactions still work; keep
      // best-effort semantics for this flag only.
      try conn.setReadOnly(readOnly)
      catch { case _: Throwable => () }
      // Fail fast: if autoCommit cannot be disabled, do not run the body
      // outside a transaction; the finally block below will clean up.
      try conn.setAutoCommit(false)
      catch {
        case e: Throwable =>
          try conn.setReadOnly(prevReadOnly)
          catch { case _: Throwable => () }
          try conn.setTransactionIsolation(prevIsolation)
          catch { case _: Throwable => () }
          try conn.setAutoCommit(prevAutoCommit)
          catch { case _: Throwable => () }
          try dbConn.close()
          catch { case _: Throwable => () }
          throw e
      }
      try {
        given tx: DbTx = new DbTx {
          val connection: DbConnection                = dbConn
          val dialect: SqlDialect                     = JdbcTransactor.this.dialect
          val logger: SqlLogger                       = JdbcTransactor.this.sqlLogger
          override var currentDepth: Int              = 0
          override def savepoint(name: String): Unit  = dbConn.savepoint(name)
          override def release(name: String): Unit    = dbConn.release(name)
          override def rollbackTo(name: String): Unit = dbConn.rollbackTo(name)
        }
        val result = f
        conn.commit()
        result
      } catch {
        case e: Throwable =>
          try conn.rollback()
          catch { case rb: Throwable => e.addSuppressed(rb) }
          throw e
      }
    } finally {
      try conn.setReadOnly(prevReadOnly)
      catch { case _: Throwable => () }
      try conn.setTransactionIsolation(prevIsolation)
      catch { case _: Throwable => () }
      try conn.setAutoCommit(prevAutoCommit)
      catch { case _: Throwable => () }
      try dbConn.close()
      catch { case _: Throwable => () }
    }
  }

  /**
   * Opens a connection, disables auto-commit, executes `f`, commits on success,
   * and rolls back on exception. The connection is closed after commit or
   * rollback. Preserves the driver's default isolation level and `readOnly`
   * flag — does not force `SERIALIZABLE` — so callers that previously relied on
   * the driver default (e.g. PostgreSQL `READ_COMMITTED`) are not unexpectedly
   * upgraded. Use the two-arg overload to request a specific isolation level.
   *
   * For `SqlDialect.SQLite`, `transact` runs with `busy_timeout=5000` and
   * `IMMEDIATE` — `fromUrl` and `sqlite` create SQLite connections with
   * `busy_timeout=5000` and `transaction_mode=IMMEDIATE`, and `transact` also
   * configures any SQLite `Connection` (including pooled/wrapped via
   * `isWrapperFor`/`unwrap`) per-transaction so queue workers reserve the write
   * lock before the `SELECT` and wait on contention instead of failing the
   * later `DELETE` with `SQLITE_BUSY`. See PR #1534 discussion_r3863454094;
   * SQLite is single-consumer.
   */
  override def transact[A](f: DbTx ?=> A): A = {
    val conn = connectionFactory()
    try {
      if (dialect == SqlDialect.SQLite) JdbcTransactor.configureSQLiteConnection(conn)
    } catch {
      case e: Throwable =>
        try conn.close()
        catch { case ce: Throwable => e.addSuppressed(ce) }
        throw e
    }
    val dbConn         = new JdbcConnection(conn)
    val prevAutoCommit =
      try conn.getAutoCommit
      catch {
        case e: Throwable =>
          try dbConn.close()
          catch { case ce: Throwable => e.addSuppressed(ce) }
          throw e
      }
    try conn.setAutoCommit(false)
    catch {
      case e: Throwable =>
        try dbConn.close()
        catch { case ce: Throwable => e.addSuppressed(ce) }
        throw e
    }
    try {
      // SQLite workaround for queue dequeue: the current dequeue design issues a
      // plain SELECT followed by a DELETE in the same transaction. With a
      // normal DEFERRED transaction the worker acquires only a read lock for the
      // SELECT and can then lose the reserved lock to a concurrent writer,
      // failing the later DELETE with SQLITE_BUSY. Start the transaction in
      // IMMEDIATE mode so the worker reserves the write lock before claiming
      // rows, and configure the documented busy timeout so it waits instead of
      // failing instantly under contention. See PR #1534 discussion_r3863454094.
      if (dialect == SqlDialect.SQLite) {
        val timeoutStmt = conn.createStatement()
        try timeoutStmt.execute("PRAGMA busy_timeout = 5000")
        finally timeoutStmt.close()
        val beginStmt = conn.createStatement()
        try beginStmt.execute("BEGIN IMMEDIATE")
        finally beginStmt.close()
      }
      given tx: DbTx = new DbTx {
        val connection: DbConnection                = dbConn
        val dialect: SqlDialect                     = JdbcTransactor.this.dialect
        val logger: SqlLogger                       = JdbcTransactor.this.sqlLogger
        override var currentDepth: Int              = 0
        override def savepoint(name: String): Unit  = dbConn.savepoint(name)
        override def release(name: String): Unit    = dbConn.release(name)
        override def rollbackTo(name: String): Unit = dbConn.rollbackTo(name)
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
    // when it is a SQLiteDataSource. Use reflection to avoid a hard compile-time
    // dependency on sqlite-jdbc in main. Only ClassNotFoundException is
    // swallowed (sqlite-jdbc not on classpath, e.g., mock test); other failures
    // propagate to fail fast.
    if (dialect == SqlDialect.SQLite) {
      try {
        val clazz = Class.forName("org.sqlite.SQLiteDataSource")
        if (clazz.isInstance(dataSource)) {
          val setBusy = clazz.getMethod("setBusyTimeout", classOf[Int])
          invokeReflective(setBusy, dataSource, Integer.valueOf(5000))
          val setMode = clazz.getMethod("setTransactionMode", classOf[String])
          invokeReflective(setMode, dataSource, "IMMEDIATE")
        }
      } catch {
        case _: ClassNotFoundException => ()
      }
    }
    new JdbcTransactor(() => dataSource.getConnection, dialect)
  }

  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.PostgreSQL)

  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.SQLite)

  private[sql] def configureSQLiteConnection(conn: Connection): Unit = {
    // Per-connection configuration for SQLite, including pooled/wrapped
    // DataSources (e.g., Hikari). Handles both direct SQLiteConnection and
    // wrapped via isWrapperFor/unwrap. Only the initial lookup of
    // `org.sqlite.SQLiteConnection` may be swallowed (sqlite-jdbc absent or mock
    // test); once identified as SQLite, missing APIs or invocation failures
    // propagate to avoid silently leaving the connection DEFERRED.
    val sqliteConnClass =
      try Class.forName("org.sqlite.SQLiteConnection")
      catch { case _: ClassNotFoundException => return }
    val isWrapper =
      try conn.isWrapperFor(sqliteConnClass.asInstanceOf[Class[Object]])
      catch { case _: Throwable => false }
    val sqliteConn: Connection =
      if (isWrapper) {
        val unwrapped = conn.unwrap(sqliteConnClass.asInstanceOf[Class[Object]])
        if (unwrapped == null)
          throw new SQLException(
            "isWrapperFor returned true but unwrap returned null for org.sqlite.SQLiteConnection"
          )
        unwrapped.asInstanceOf[Connection]
      } else if (sqliteConnClass.isInstance(conn)) conn
      else return
    val setBusy = sqliteConnClass.getMethod("setBusyTimeout", classOf[Int])
    invokeReflective(setBusy, sqliteConn, Integer.valueOf(5000))
    val getConfig   = sqliteConnClass.getMethod("getConnectionConfig")
    val config      = invokeReflective(getConfig, sqliteConn)
    val configClass = config.getClass
    val modeClass   = Class.forName("org.sqlite.SQLiteConfig$TransactionMode")
    val immediate   = modeClass.getField("IMMEDIATE").get(null)
    val setMode     = configClass.getMethod("setTransactionMode", modeClass)
    invokeReflective(setMode, config.asInstanceOf[AnyRef], immediate.asInstanceOf[AnyRef])
  }

  private def invokeReflective(method: java.lang.reflect.Method, target: AnyRef, args: AnyRef*): AnyRef =
    try method.invoke(target, args*)
    catch {
      case e: InvocationTargetException => throw Option(e.getCause).getOrElse(e)
    }
}
