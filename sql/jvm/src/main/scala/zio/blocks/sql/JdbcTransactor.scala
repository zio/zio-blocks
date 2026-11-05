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

/**
 * The JDBC [[Transactor]]: each `connect` or `transact` call takes a new
 * `java.sql.Connection` from `connectionFactory` and closes it when the block
 * ends. Build one with `JdbcTransactor.fromUrl`, or with
 * `JdbcTransactor.fromDataSource` (or `postgres`, `sqlite`) to take connections
 * from a pool. Use the constructor to pass a [[SqlLogger]]:
 *
 * {{{
 * val logger = new SqlLogger {
 *   def onSuccess(event: SqlLogger.SuccessEvent): Unit = println(event.sql + " -> " + event.rowCount + " rows")
 *   def onError(event: SqlLogger.ErrorEvent): Unit     = println(event.sql + " failed: " + event.error.getMessage)
 * }
 * val xa = new JdbcTransactor(
 *   () => java.sql.DriverManager.getConnection("jdbc:sqlite::memory:"),
 *   SqlDialect.SQLite,
 *   logger
 * )
 * xa.connect {
 *   sql"CREATE TABLE tag (name TEXT)".update
 *   sql"INSERT INTO tag VALUES ('sale'), ('new')".update
 * }
 * // CREATE TABLE tag (name TEXT) -> 0 rows
 * // INSERT INTO tag VALUES ('sale'), ('new') -> 2 rows
 * }}}
 *
 * With `SqlDialect.SQLite`, every connection it opens (directly from the driver
 * or wrapped by a pool) gets a 5-second busy timeout and `IMMEDIATE`
 * transactions, so a transaction takes the write lock when it starts and a
 * second writer waits for it (up to 5 seconds) instead of failing with
 * `SQLITE_BUSY`. A `jdbc:sqlite::memory:` URL gives each connection, and so
 * each block, its own empty database; use a file URL to keep data between
 * blocks.
 *
 * @param connectionFactory
 *   opens a new connection; called once per `connect` or `transact`
 * @param dialect
 *   the SQL dialect of the database
 * @param sqlLogger
 *   receives statement events; [[SqlLogger.noop]] by default
 * @see
 *   [[https://zioblocks.com/reference/sql/transactor/ Transactor]]
 */
class JdbcTransactor(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val sqlLogger: SqlLogger = SqlLogger.noop
) extends Transactor {

  /**
   * Opens a connection, runs `f` with it as the given [[DbCon]], and closes it
   * when `f` returns or throws. Auto-commit stays as the connection has it.
   */
  def connect[A](f: DbCon ?=> A): A = {
    val dbConn = JdbcTransactor.openConnection(connectionFactory, dialect)
    try {
      if (dialect == SqlDialect.SQLite) {
        try {
          val stmt = dbConn.underlying.createStatement()
          if (stmt != null) {
            try stmt.execute("PRAGMA busy_timeout = 5000")
            finally
              try stmt.close()
              catch { case _: Throwable => () }
          }
        } catch { case _: Throwable => () }
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
   * Opens a connection, sets its isolation level and read-only flag, turns
   * auto-commit off, runs `f` with the connection as the given [[DbTx]], and
   * commits when `f` returns or rolls back when it throws (adding any rollback
   * failure to the exception as suppressed). Then it restores the previous
   * read-only flag, isolation level and auto-commit, and closes the connection.
   * A failure to set the isolation level or turn auto-commit off is thrown
   * before `f` runs; a failure to set the read-only flag is ignored, so on
   * SQLite a read-only transaction can still write.
   */
  override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A = {
    val dbConn         = JdbcTransactor.openConnection(connectionFactory, dialect)
    val conn           = dbConn.underlying
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
   * Opens a connection, turns auto-commit off, runs `f` with the connection as
   * the given [[DbTx]], and commits when `f` returns or rolls back when it
   * throws (adding any rollback failure to the exception as suppressed). If the
   * commit itself throws, it rolls back and rethrows. Then it restores
   * auto-commit and closes the connection. The driver's isolation level and
   * read-only flag are left as they are, for example PostgreSQL's
   * `READ COMMITTED`.
   */
  override def transact[A](f: DbTx ?=> A): A = {
    val dbConn         = JdbcTransactor.openConnection(connectionFactory, dialect)
    val conn           = dbConn.underlying
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

/** Constructors for [[JdbcTransactor]]. */
object JdbcTransactor {

  /**
   * Opens a connection for `connect` and both `transact` overloads, applies the
   * SQLite settings when `dialect` is SQLite, and wraps it; if the settings
   * fail, it closes the connection and rethrows.
   */
  private def openConnection(
    connectionFactory: () => Connection,
    dialect: SqlDialect
  ): JdbcConnection = {
    val conn = connectionFactory()
    try {
      if (dialect == SqlDialect.SQLite) configureSQLiteConnection(conn)
    } catch {
      case e: Throwable =>
        try conn.close()
        catch { case ce: Throwable => e.addSuppressed(ce) }
        throw e
    }
    new JdbcConnection(conn)
  }

  /**
   * Returns a transactor that opens each connection with
   * `DriverManager.getConnection(url)`, with no pooling. For SQLite it also
   * passes the driver properties `busy_timeout=5000` and
   * `transaction_mode=IMMEDIATE`. Each block gets a new connection, so use a
   * file URL when data must outlive one block:
   *
   * {{{
   * val db = java.nio.file.Files.createTempFile("catalog", ".db")
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
   * xa.connect(sql"CREATE TABLE brand (name TEXT)".update)
   * xa.connect(sql"INSERT INTO brand VALUES ('Acme')".update)
   * val brands = xa.connect(sql"SELECT name FROM brand".query[String])
   * // List(Acme)
   * }}}
   */
  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor =
    if (dialect == SqlDialect.SQLite) {
      val props = new Properties()
      props.setProperty("busy_timeout", "5000")
      props.setProperty("transaction_mode", "IMMEDIATE")
      new JdbcTransactor(() => DriverManager.getConnection(url, props), dialect)
    } else new JdbcTransactor(() => DriverManager.getConnection(url), dialect)

  /**
   * Returns a transactor that opens each connection with
   * `DriverManager.getConnection` and these credentials, with no pooling.
   */
  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor =
    if (dialect == SqlDialect.SQLite) {
      val props = new Properties()
      props.setProperty("busy_timeout", "5000")
      props.setProperty("transaction_mode", "IMMEDIATE")
      props.setProperty("user", user)
      props.setProperty("password", password)
      new JdbcTransactor(() => DriverManager.getConnection(url, props), dialect)
    } else new JdbcTransactor(() => DriverManager.getConnection(url, user, password), dialect)

  /**
   * Returns a transactor that takes each connection from `dataSource`, so a
   * pooling `DataSource` such as HikariCP's decides how connections are reused.
   * For SQLite, an `org.sqlite.SQLiteDataSource` is also set to a 5-second busy
   * timeout and `IMMEDIATE` transactions.
   *
   * {{{
   * // pool: a HikariDataSource or any other pooling javax.sql.DataSource
   * def transactor(pool: javax.sql.DataSource): JdbcTransactor =
   *   JdbcTransactor.fromDataSource(pool, SqlDialect.PostgreSQL)
   * }}}
   */
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

  /** Returns `fromDataSource(dataSource, SqlDialect.PostgreSQL)`. */
  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.PostgreSQL)

  /** Returns `fromDataSource(dataSource, SqlDialect.SQLite)`. */
  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.SQLite)

  /**
   * Sets a SQLite connection, unwrapped from a pool's wrapper when needed, to a
   * 5-second busy timeout and `IMMEDIATE` transactions; leaves other
   * connections alone.
   */
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

  /** Calls `method`, rethrowing the cause of an `InvocationTargetException`. */
  private def invokeReflective(method: java.lang.reflect.Method, target: AnyRef, args: AnyRef*): AnyRef =
    try method.invoke(target, args*)
    catch {
      case e: InvocationTargetException => throw Option(e.getCause).getOrElse(e)
    }
}
