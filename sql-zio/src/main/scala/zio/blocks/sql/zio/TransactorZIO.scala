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
 * A ZIO-integrated database transactor that lifts synchronous SQL operations
 * into the ZIO effect system.
 *
 * `TransactorZIO` wraps a JDBC connection factory and exposes two groups of
 * methods:
 *
 *   - **Blocking wrappers** (`connect`, `transact`): Run a synchronous
 *     `DbCon ?=> A` or `DbTx ?=> A` block on ZIO's blocking thread pool
 *     (`ZIO.attemptBlocking`). Use these when your body is entirely
 *     synchronous.
 *   - **Effect-aware methods** (`connectZIO`, `transactZIO`): Accept a body
 *     that itself returns a `ZIO[R, E, A]`. The JDBC connection is bracketed
 *     via `ZIO.acquireRelease` so it is always closed even on interruption.
 *     `transactZIO` additionally rolls back on failure and commits on success.
 *
 * ==Construction==
 * Use the factory methods in the companion object:
 * {{{
 *   val tx = TransactorZIO.fromUrl("jdbc:postgresql://localhost/mydb", SqlDialect.PostgreSQL)
 *   val layer: ZLayer[Any, Nothing, TransactorZIO] = TransactorZIO.layer(url, dialect)
 * }}}
 *
 * @param connectionFactory
 *   A zero-argument function that opens a new JDBC `Connection`. Called once
 *   per `connect`/`transact` invocation.
 * @param dialect
 *   The [[SqlDialect]] used to render [[Frag]] SQL strings and resolve column
 *   types.
 * @param logger
 *   Optional [[SqlLogger]] for query execution events. Defaults to
 *   [[SqlLogger.noop]].
 */
class TransactorZIO(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val logger: SqlLogger = SqlLogger.noop
) {

  // Keep the synchronous wrappers for compatibility
  private val underlying = new JdbcTransactor(connectionFactory, dialect, logger)

  /**
   * Acquires a connection, runs `f` on the blocking thread pool, then closes
   * the connection.
   */
  def connect[A](f: DbCon ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.connect(f))

  /**
   * Acquires a connection, disables auto-commit, runs `f` on the blocking
   * thread pool, commits on success or rolls back on failure, then closes the
   * connection.
   */
  def transact[A](f: DbTx ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.transact(f))

  /**
   * Acquires a connection via `ZIO.acquireRelease`, passes it to `f` as a
   * `DbCon`, and ensures the connection is closed when the scope exits (even on
   * interruption).
   */
  // NEW: Effect-aware ZIO connection management
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
   * Acquires a connection via `ZIO.acquireRelease`, disables auto-commit,
   * passes it to `f` as a `DbTx`, commits on success, rolls back on failure,
   * then closes the connection.
   */
  // NEW: Effect-aware ZIO transaction management
  def transactZIO[R, E, A](f: DbTx ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    ZIO.scoped[R] {
      ZIO
        .acquireRelease(ZIO.attemptBlocking {
          val conn           = connectionFactory()
          val dbConn         = new JdbcConnection(conn)
          val prevAutoCommit = conn.getAutoCommit
          conn.setAutoCommit(false)
          (conn, dbConn, prevAutoCommit)
        }) { case (conn, dbConn, prevAutoCommit) =>
          ZIO.attemptBlocking {
            conn.setAutoCommit(prevAutoCommit)
            dbConn.close()
          }.ignore
        }
        .flatMap { case (conn, dbConn, _) =>
          val tx = new DbTx {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = TransactorZIO.this.dialect
            val logger: SqlLogger        = TransactorZIO.this.logger
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

object TransactorZIO {

  /**
   * Creates a `TransactorZIO` that opens connections via
   * `DriverManager.getConnection(url)`.
   */
  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url), dialect)

  /**
   * Creates a `TransactorZIO` that opens connections via
   * `DriverManager.getConnection(url, user, password)`.
   */
  def fromUrl(
    url: String,
    user: String,
    password: String,
    dialect: SqlDialect
  ): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url, user, password), dialect)

  /**
   * Creates a `TransactorZIO` backed by a `DataSource` (recommended for
   * connection pooling).
   */
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => dataSource.getConnection, dialect)

  /**
   * A `ZLayer` that provides a `TransactorZIO` for the given URL and dialect.
   */
  // ZLayer for dependency injection
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO] =
    ZLayer.succeed(fromUrl(url, dialect))
}
