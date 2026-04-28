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

class TransactorZIO(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val logger: SqlLogger = SqlLogger.noop
) {

  // Keep the synchronous wrappers for compatibility
  private val underlying = new JdbcTransactor(connectionFactory, dialect, logger)

  def connect[A](f: DbCon ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.connect(f))

  def transact[A](f: DbTx ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.transact(f))

  // NEW: Effect-aware ZIO connection management
  def connectZIO[R, E, A](f: DbCon ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A] =
    ZIO.scoped[R] {
      ZIO
        .acquireRelease(ZIO.attemptBlocking {
          val conn   = connectionFactory()
          val dbConn = new JdbcConnection(conn)
          (conn, dbConn)
        }) { case (_, dbConn) =>
          ZIO.succeed {
            try dbConn.close()
            catch { case _: Throwable => () }
          }
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
          ZIO.succeed {
            try conn.setAutoCommit(prevAutoCommit)
            catch { case _: Throwable => () }
            try dbConn.close()
            catch { case _: Throwable => () }
          }
        }
        .flatMap { case (conn, dbConn, _) =>
          val tx = new DbTx {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = TransactorZIO.this.dialect
            val logger: SqlLogger        = TransactorZIO.this.logger
          }
          f(using tx)
            .tapErrorCause(_ => ZIO.attemptBlocking(conn.rollback()).ignore)
            .tap(_ => ZIO.attemptBlocking(conn.commit()))
        }
    }
}

object TransactorZIO {

  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url), dialect)

  def fromUrl(
    url: String,
    user: String,
    password: String,
    dialect: SqlDialect
  ): TransactorZIO =
    new TransactorZIO(() => java.sql.DriverManager.getConnection(url, user, password), dialect)

  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(() => dataSource.getConnection, dialect)

  // ZLayer for dependency injection
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO] =
    ZLayer.succeed(fromUrl(url, dialect))
}
