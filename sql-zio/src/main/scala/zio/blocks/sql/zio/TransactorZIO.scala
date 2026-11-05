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

package zio.blocks.sql.zio

import zio._
import zio.blocks.sql._
import java.sql.Connection

/**
 * A transactor whose methods return ZIO effects. `connect` and `transact` run a
 * synchronous block, as [[zio.blocks.sql.JdbcTransactor]] does, on ZIO's
 * blocking thread pool. `connectZIO` and `transactZIO` take a block that
 * returns a ZIO effect, so database work can mix with other effects; they open
 * the connection with `ZIO.acquireRelease`, so it is closed when the effect
 * ends, fails or is interrupted. Each call opens a new connection from the
 * connection factory.
 *
 * {{{
 * import zio._
 * import zio.blocks.sql._
 *
 * val db = java.nio.file.Files.createTempFile("shop", ".db")
 * val tx = TransactorZIO.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
 *
 * val program: Task[List[String]] = for {
 *   _     <- tx.transact {
 *              sql"CREATE TABLE customer (name TEXT)".update
 *              sql"INSERT INTO customer VALUES ('Ada'), ('Grace')".update
 *            }
 *   names <- tx.connect(sql"SELECT name FROM customer ORDER BY name".query[String])
 * } yield names
 * // program succeeds with List(Ada, Grace)
 * }}}
 *
 * `import zio.blocks.sql._` also imports this package under the name `zio`,
 * hiding the root `zio` package for the rest of the scope: a known bug in
 * 0.0.56. Put it after every other `zio` import, or write `_root_.zio`.
 *
 * @param connectionFactory
 *   opens a new JDBC connection; called once per `connect`, `transact`,
 *   `connectZIO` or `transactZIO`
 * @param dialect
 *   the [[zio.blocks.sql.SqlDialect]] that fragments are rendered for
 * @param logger
 *   receives statement events; [[zio.blocks.sql.SqlLogger.noop]] by default
 * @see
 *   [[https://zioblocks.com/reference/sql/transactor-zio/ TransactorZIO]]
 */
class TransactorZIO(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val logger: SqlLogger = SqlLogger.noop
) {

  /** Runs the synchronous `connect` and `transact`. */
  private val underlying = new JdbcTransactor(connectionFactory, dialect, logger)

  /**
   * Returns an effect that opens a connection, runs `f` with it as the given
   * [[zio.blocks.sql.DbCon]] on the blocking thread pool, and closes it; a
   * throw from `f` becomes the effect's failure. It runs through
   * [[zio.blocks.sql.JdbcTransactor]], so SQLite connections get its busy
   * timeout and `IMMEDIATE` transactions.
   *
   * {{{
   * import zio._
   * import zio.blocks.sql._
   *
   * val tx = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   *
   * val total: Task[Int] = tx.connect {
   *   sql"CREATE TABLE refund (amount INTEGER)".update
   *   sql"INSERT INTO refund VALUES (15), (5)".update
   *   sql"SELECT sum(amount) FROM refund".queryOne[Int].getOrElse(0)
   * }
   * // total succeeds with 20
   * }}}
   */
  def connect[A](f: DbCon ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.connect(f))

  /**
   * Returns an effect that runs `f` in a transaction on the blocking thread
   * pool, through `JdbcTransactor#transact`: auto-commit off, commit when `f`
   * returns, roll back when it throws, and the exception becomes the effect's
   * failure. The driver's isolation level and read-only flag are left as they
   * are.
   *
   * {{{
   * import zio._
   * import zio.blocks.sql._
   *
   * val db = java.nio.file.Files.createTempFile("shipping", ".db")
   * val tx = TransactorZIO.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
   *
   * val program = for {
   *   _      <- tx.connect(sql"CREATE TABLE shipment (id INTEGER)".update)
   *   failed <- tx.transact {
   *               sql"INSERT INTO shipment VALUES (1)".update
   *               throw new IllegalStateException("carrier unavailable")
   *             }.either
   *   count  <- tx.connect(sql"SELECT count(*) FROM shipment".queryOne[Int].getOrElse(0))
   * } yield (failed, count)
   * // program succeeds with (Left(java.lang.IllegalStateException: carrier unavailable),0)
   * }}}
   */
  def transact[A](f: DbTx ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.transact(f))

  /**
   * Like `transact(f)`, with the isolation level and read-only flag set for the
   * transaction and restored afterwards, as
   * `JdbcTransactor#transact(isolation, readOnly)` does. A failure to set the
   * read-only flag is ignored.
   */
  def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.transact(isolation, readOnly)(f))

  /**
   * Returns an effect that opens a connection with `ZIO.acquireRelease`, runs
   * the effect `f` builds with the connection as the given
   * [[zio.blocks.sql.DbCon]], and closes the connection when that effect ends,
   * fails or is interrupted. Only opening and closing run on the blocking pool:
   * wrap each statement in `ZIO.attemptBlocking`.
   *
   * {{{
   * import zio._
   * import zio.blocks.sql._
   *
   * val tx = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   *
   * val program: ZIO[Any, Throwable, Int] = tx.connectZIO {
   *   for {
   *     _ <- ZIO.attemptBlocking(sql"CREATE TABLE visit (page TEXT)".update)
   *     _ <- ZIO.attemptBlocking(sql"INSERT INTO visit VALUES ('/'), ('/cart')".update)
   *     n <- ZIO.attemptBlocking(sql"SELECT count(*) FROM visit".queryOne[Int].getOrElse(0))
   *   } yield n
   * }
   * // program succeeds with 2
   * }}}
   *
   * The connection comes straight from the connection factory, so on SQLite it
   * gets none of the settings `connect` applies.
   */
  def connectZIO[R, E, A](f: DbCon ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    ZIO.scoped[R] {
      ZIO
        .acquireRelease(ZIO.attemptBlocking {
          val conn   = connectionFactory()
          val dbConn = new JdbcConnection(conn)
          (conn, dbConn)
        }) { case (_, dbConn) =>
          ZIO.attemptBlocking(dbConn.close()).ignore
        }
        .flatMap { case (_, dbConn) =>
          val con = new DbCon {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = TransactorZIO.this.dialect
            val logger: SqlLogger        = TransactorZIO.this.logger
          }
          f(using con)
        }
    }

  /**
   * Returns an effect that opens a connection with `ZIO.acquireRelease`, turns
   * auto-commit off, runs the effect `f` builds with the connection as the
   * given [[zio.blocks.sql.DbTx]], commits when that effect succeeds and rolls
   * back when it fails or dies, then restores auto-commit and closes the
   * connection. The driver's isolation level and read-only flag are left as
   * they are. Wrap each statement in `ZIO.attemptBlocking`.
   *
   * {{{
   * import zio._
   * import zio.blocks.sql._
   *
   * val db = java.nio.file.Files.createTempFile("payments", ".db")
   * val tx = TransactorZIO.fromUrl("jdbc:sqlite:" + db, SqlDialect.SQLite)
   *
   * def charge(amount: Int): ZIO[Any, Throwable | String, Int] =
   *   tx.transactZIO {
   *     for {
   *       _ <- ZIO.attemptBlocking(sql"INSERT INTO payment VALUES (\$amount)".update)
   *       _ <- ZIO.when(amount > 100)(ZIO.fail("over the limit"))
   *     } yield amount
   *   }
   *
   * val program = for {
   *   _       <- tx.connect(sql"CREATE TABLE payment (amount INTEGER)".update)
   *   small   <- charge(40).either
   *   large   <- charge(500).either
   *   amounts <- tx.connect(sql"SELECT amount FROM payment".query[Int])
   * } yield (small, large, amounts)
   * // program succeeds with (Right(40),Left(over the limit),List(40))
   * }}}
   *
   * Two known bugs in 0.0.56: the statements already run are committed, not
   * rolled back, when `f` throws while building its effect (outside any ZIO
   * constructor) or when the running transaction is interrupted, for example by
   * `timeout`. And the connection comes straight from the connection factory,
   * so on SQLite the transaction is `DEFERRED` with the driver's busy timeout,
   * unlike `transact`: two transactions that each read and then write can fail
   * with `SQLITE_BUSY`.
   */
  def transactZIO[R, E, A](f: DbTx ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    transactZIOInternal(None, None)(f)

  /**
   * Like `transactZIO(f)`, with the isolation level and read-only flag set for
   * the transaction and the previous values restored afterwards. A failure to
   * set the isolation level fails the effect before `f` runs, and closes the
   * connection; a failure to set the read-only flag is ignored. The known bugs
   * of `transactZIO(f)` apply.
   */
  def transactZIO[R, E, A](isolation: TransactionIsolation, readOnly: Boolean)(
    f: DbTx ?=> ZIO[R, E, A]
  ): ZIO[R, E | Throwable, A] =
    transactZIOInternal(Some(isolation), Some(readOnly))(f)

  /** Both `transactZIO` overloads; `None` leaves that setting alone. */
  private def transactZIOInternal[R, E, A](
    isolation: Option[TransactionIsolation],
    readOnly: Option[Boolean]
  )(f: DbTx ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    ZIO.scoped[R] {
      ZIO
        .acquireRelease(ZIO.attemptBlocking {
          val conn   = connectionFactory()
          val dbConn = new JdbcConnection(conn)
          try {
            val prevAutoCommit = conn.getAutoCommit
            val prevIsolation  = isolation.map(_ => conn.getTransactionIsolation)
            val prevReadOnly   = readOnly.map(_ =>
              try conn.isReadOnly
              catch { case _: Throwable => false }
            )
            // Fail fast: isolation failures must propagate (do not swallow); the
            // release finalizer restores previous settings and closes the connection.
            isolation.foreach(iso => conn.setTransactionIsolation(iso.jdbcLevel))
            // Intentionally swallow readOnly failures: SQLite (and some other
            // drivers) throw on setReadOnly even though transactions still work;
            // keep best-effort semantics for this flag only, as in JdbcTransactor.
            readOnly.foreach(ro =>
              try conn.setReadOnly(ro)
              catch { case _: Throwable => () }
            )
            // Fail fast: if autoCommit cannot be disabled, do not run the body
            // outside a transaction.
            conn.setAutoCommit(false)
            (conn, dbConn, prevAutoCommit, prevIsolation, prevReadOnly)
          } catch {
            case e: Throwable =>
              // Setup failed before the acquire completed, so the release
              // finalizer is not registered; close the connection here.
              try conn.close()
              catch { case _: Throwable => () }
              throw e
          }
        }) { case (conn, dbConn, prevAutoCommit, prevIsolation, prevReadOnly) =>
          ZIO.attemptBlocking {
            prevReadOnly.foreach(ro =>
              try conn.setReadOnly(ro)
              catch { case _: Throwable => () }
            )
            prevIsolation.foreach(iso =>
              try conn.setTransactionIsolation(iso)
              catch { case _: Throwable => () }
            )
            try conn.setAutoCommit(prevAutoCommit)
            catch { case _: Throwable => () }
            dbConn.close()
          }.ignore
        }
        .flatMap { case (conn, dbConn, _, _, _) =>
          val tx = new DbTx {
            val connection: DbConnection                = dbConn
            val dialect: SqlDialect                     = TransactorZIO.this.dialect
            val logger: SqlLogger                       = TransactorZIO.this.logger
            override var currentDepth: Int              = 0
            override def savepoint(name: String): Unit  = dbConn.savepoint(name)
            override def release(name: String): Unit    = dbConn.release(name)
            override def rollbackTo(name: String): Unit = dbConn.rollbackTo(name)
          }
          f(using tx).exit.flatMap {
            case Exit.Success(value) =>
              ZIO.attemptBlocking(conn.commit()).as(value).catchAll { commitError =>
                ZIO.attemptBlocking(conn.rollback()).catchAll { rollbackError =>
                  ZIO.succeed(commitError.addSuppressed(rollbackError))
                } *> ZIO.fail(commitError)
              }

            case Exit.Failure(cause) =>
              ZIO.attemptBlocking(conn.rollback()).ignore *> ZIO.refailCause(cause)
          }
        }
    }
}

/** Constructors for [[TransactorZIO]]. */
object TransactorZIO {

  /**
   * Returns a transactor that opens each connection with
   * `DriverManager.getConnection(url)`, with no pooling. Unlike
   * `JdbcTransactor.fromUrl`, it passes no SQLite properties.
   */
  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url), dialect)

  /**
   * Returns a transactor that opens each connection with
   * `DriverManager.getConnection(url, user, password)`, with no pooling.
   */
  def fromUrl(
    url: String,
    user: String,
    password: String,
    dialect: SqlDialect
  ): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url, user, password), dialect)

  /**
   * Returns a transactor that takes each connection from `dataSource`, so a
   * pooling `DataSource` decides how connections are reused.
   */
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => dataSource.getConnection, dialect)

  /**
   * Returns a layer that provides `fromUrl(url, dialect)`. For credentials or a
   * `DataSource`, wrap the transactor in `ZLayer.succeed` instead.
   *
   * {{{
   * import zio._
   * import zio.blocks.sql._
   *
   * val program: ZIO[TransactorZIO, Throwable, Int] =
   *   ZIO.serviceWithZIO[TransactorZIO](_.connect(sql"SELECT 6 * 7".queryOne[Int].getOrElse(0)))
   *
   * val runnable = program.provide(TransactorZIO.layer("jdbc:sqlite::memory:", SqlDialect.SQLite))
   * // runnable succeeds with 42
   * }}}
   */
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO] =
    ZLayer.succeed(fromUrl(url, dialect))
}
