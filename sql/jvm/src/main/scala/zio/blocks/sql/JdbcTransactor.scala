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

class JdbcTransactor(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val sqlLogger: SqlLogger = SqlLogger.noop
) extends Transactor {

  def connect[A](f: DbCon ?=> A): A = {
    val conn   = connectionFactory()
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

  def transact[A](f: DbTx ?=> A): A = {
    val conn           = connectionFactory()
    val dbConn         = new JdbcConnection(conn)
    val prevAutoCommit = conn.getAutoCommit
    try {
      // SQLite workaround for queue dequeue: the current dequeue design issues a
      // plain SELECT followed by a DELETE in the same transaction. With a
      // normal DEFERRED transaction the worker acquires only a read lock for the
      // SELECT and can then lose the reserved lock to a concurrent writer,
      // failing the later DELETE with SQLITE_BUSY. Start the transaction in
      // IMMEDIATE mode so the worker reserves the write lock before claiming
      // rows, and configure the documented busy timeout so it waits instead of
      // failing instantly under contention. See PR #1534 discussion_r3863454094.
      // PRAGMA must run before disabling autoCommit, otherwise it starts a
      // DEFERRED transaction and the subsequent BEGIN IMMEDIATE fails with
      // "cannot start a transaction within a transaction".
      if (dialect == SqlDialect.SQLite) {
        val timeoutStmt = conn.createStatement()
        try timeoutStmt.execute("PRAGMA busy_timeout = 5000")
        finally timeoutStmt.close()
      }
      conn.setAutoCommit(false)
      if (dialect == SqlDialect.SQLite) {
        val beginStmt = conn.createStatement()
        try beginStmt.execute("BEGIN IMMEDIATE")
        finally beginStmt.close()
      }
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
    new JdbcTransactor(() => DriverManager.getConnection(url), dialect)

  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(url, user, password), dialect)

  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): JdbcTransactor =
    new JdbcTransactor(() => dataSource.getConnection, dialect)

  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.PostgreSQL)

  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor =
    fromDataSource(dataSource, SqlDialect.SQLite)
}
