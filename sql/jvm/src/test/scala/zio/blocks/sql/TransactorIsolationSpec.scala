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

import zio.test.*
import java.sql.{Connection, DriverManager}
import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy}

object TransactorIsolationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  private def nonClosing(conn: Connection): Connection = {
    val handler = new InvocationHandler {
      def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef =
        if (method.getName == "close") null
        else {
          val actualArgs = if (args == null) Array.empty[AnyRef] else args
          try method.invoke(conn, actualArgs*)
          catch { case e: InvocationTargetException => throw e.getCause }
        }
    }
    Proxy
      .newProxyInstance(getClass.getClassLoader, Array(classOf[Connection]), handler)
      .asInstanceOf[Connection]
  }

  private def fakeConnection(): Connection = {
    var autoCommit = true
    var readOnly   = false
    var isolation  = Connection.TRANSACTION_SERIALIZABLE
    var closed     = false
    val handler    = new InvocationHandler {
      def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
        case "setAutoCommit" =>
          autoCommit = args(0).asInstanceOf[Boolean]
          null
        case "getAutoCommit" => java.lang.Boolean.valueOf(autoCommit)
        case "setReadOnly"   =>
          readOnly = args(0).asInstanceOf[Boolean]
          null
        case "isReadOnly"              => java.lang.Boolean.valueOf(readOnly)
        case "setTransactionIsolation" =>
          isolation = args(0).asInstanceOf[Int]
          null
        case "getTransactionIsolation" => Integer.valueOf(isolation)
        case "commit"                  => null
        case "rollback"                => null
        case "close"                   => closed = true; null
        case "isClosed"                => java.lang.Boolean.valueOf(closed)
        case "prepareStatement"        =>
          throw new UnsupportedOperationException("fakeConnection does not support prepareStatement")
        case other => throw new UnsupportedOperationException(s"fakeConnection: $other not supported")
      }
    }
    Proxy
      .newProxyInstance(getClass.getClassLoader, Array(classOf[Connection]), handler)
      .asInstanceOf[Connection]
  }

  def spec: Spec[TestEnvironment, Any] = suite("TransactorIsolationSpec")(
    test("isolation level is applied inside transact") {
      val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
      val inside     = transactor.transact(TransactionIsolation.ReadCommitted, false) {
        val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        jdbcConn.getTransactionIsolation
      }
      assertTrue(inside == Connection.TRANSACTION_READ_COMMITTED)
    },
    test("all isolation levels round-trip inside transact") {
      val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
      val levels     = List(
        TransactionIsolation.ReadUncommitted -> Connection.TRANSACTION_READ_UNCOMMITTED,
        TransactionIsolation.ReadCommitted   -> Connection.TRANSACTION_READ_COMMITTED,
        TransactionIsolation.RepeatableRead  -> Connection.TRANSACTION_REPEATABLE_READ,
        TransactionIsolation.Serializable    -> Connection.TRANSACTION_SERIALIZABLE
      )
      val results = levels.map { case (iso, expected) =>
        val got = transactor.transact(iso, false) {
          val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
          jdbcConn.getTransactionIsolation
        }
        got == expected
      }
      assertTrue(results.forall(identity))
    },
    test("isolation-setting failure propagates and does not swallow") {
      var isolationSet = false
      val handler      = new java.lang.reflect.InvocationHandler {
        def invoke(proxy: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "setTransactionIsolation" =>
              isolationSet = true
              throw new java.sql.SQLException("isolation not supported")
            case "getTransactionIsolation" => Integer.valueOf(java.sql.Connection.TRANSACTION_SERIALIZABLE)
            case "setReadOnly"             => null
            case "isReadOnly"              => java.lang.Boolean.FALSE
            case "getAutoCommit"           => java.lang.Boolean.TRUE
            case "setAutoCommit"           => null
            case "commit"                  => null
            case "rollback"                => null
            case "close"                   => null
            case "isClosed"                => java.lang.Boolean.FALSE
            case _                         => throw new UnsupportedOperationException(method.getName)
          }
      }
      val conn = java.lang.reflect.Proxy
        .newProxyInstance(getClass.getClassLoader, Array(classOf[java.sql.Connection]), handler)
        .asInstanceOf[java.sql.Connection]
      val transactor   = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      var bodyExecuted = false
      val result       = try {
        transactor.transact(TransactionIsolation.ReadCommitted, false) {
          bodyExecuted = true
          42
        }
        false
      } catch {
        case e: java.sql.SQLException =>
          e.getMessage == "isolation not supported" && isolationSet && !bodyExecuted
        case _ => false
      }
      assertTrue(result)
    },
    test("swallowed setReadOnly failure still runs body") {
      var readOnlySet = false
      val handler     = new java.lang.reflect.InvocationHandler {
        def invoke(proxy: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "setReadOnly" =>
              readOnlySet = true
              throw new java.sql.SQLException("readOnly not supported")
            case "isReadOnly"              => java.lang.Boolean.FALSE
            case "getTransactionIsolation" => Integer.valueOf(java.sql.Connection.TRANSACTION_SERIALIZABLE)
            case "setTransactionIsolation" => null
            case "getAutoCommit"           => java.lang.Boolean.TRUE
            case "setAutoCommit"           => null
            case "commit"                  => null
            case "rollback"                => null
            case "close"                   => null
            case "isClosed"                => java.lang.Boolean.FALSE
            case _                         => throw new UnsupportedOperationException(method.getName)
          }
      }
      val conn = java.lang.reflect.Proxy
        .newProxyInstance(getClass.getClassLoader, Array(classOf[java.sql.Connection]), handler)
        .asInstanceOf[java.sql.Connection]
      val transactor = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      val result     = transactor.transact(TransactionIsolation.Serializable, true)(99)
      assertTrue(result == 99 && readOnlySet)
    },
    test("rollback failure is suppressed and original exception is rethrown") {
      var rollbackFailed = false
      val handler        = new java.lang.reflect.InvocationHandler {
        def invoke(proxy: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "getAutoCommit"           => java.lang.Boolean.TRUE
            case "setAutoCommit"           => null
            case "getTransactionIsolation" => Integer.valueOf(java.sql.Connection.TRANSACTION_SERIALIZABLE)
            case "setTransactionIsolation" => null
            case "isReadOnly"              => java.lang.Boolean.FALSE
            case "setReadOnly"             => null
            case "commit"                  => null
            case "rollback"                =>
              rollbackFailed = true
              throw new java.sql.SQLException("rollback failed")
            case "close"    => null
            case "isClosed" => java.lang.Boolean.FALSE
            case _          => throw new UnsupportedOperationException(method.getName)
          }
      }
      val conn = java.lang.reflect.Proxy
        .newProxyInstance(getClass.getClassLoader, Array(classOf[java.sql.Connection]), handler)
        .asInstanceOf[java.sql.Connection]
      val transactor = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      val result     = try {
        transactor.transact(TransactionIsolation.Serializable, false)(throw new RuntimeException("body failed"))
        false
      } catch {
        case e: RuntimeException =>
          e.getMessage == "body failed" && e.getSuppressed.exists(_.getMessage == "rollback failed") && rollbackFailed
        case _ => false
      }
      assertTrue(result)
    },
    test("connection is closed even when body throws") {
      var closed  = false
      val handler = new java.lang.reflect.InvocationHandler {
        def invoke(proxy: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef =
          method.getName match {
            case "getAutoCommit"           => java.lang.Boolean.TRUE
            case "setAutoCommit"           => null
            case "getTransactionIsolation" => Integer.valueOf(java.sql.Connection.TRANSACTION_SERIALIZABLE)
            case "setTransactionIsolation" => null
            case "isReadOnly"              => java.lang.Boolean.FALSE
            case "setReadOnly"             => null
            case "commit"                  => null
            case "rollback"                => null
            case "close"                   => closed = true; null
            case "isClosed"                => java.lang.Boolean.valueOf(closed)
            case _                         => throw new UnsupportedOperationException(method.getName)
          }
      }
      val conn = java.lang.reflect.Proxy
        .newProxyInstance(getClass.getClassLoader, Array(classOf[java.sql.Connection]), handler)
        .asInstanceOf[java.sql.Connection]
      val transactor = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      try { transactor.transact(TransactionIsolation.Serializable, false)(throw new RuntimeException("fail")) }
      catch { case _: Throwable => () }
      assertTrue(closed)
    },
    test("readOnly flag is set inside transact via fake connection") {
      val conn           = fakeConnection()
      val tx             = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      val insideReadOnly = tx.transact(TransactionIsolation.Serializable, true) {
        val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        jdbcConn.isReadOnly
      }
      val conn2             = fakeConnection()
      val tx2               = new JdbcTransactor(() => conn2, SqlDialect.SQLite)
      val insideNotReadOnly = tx2.transact(TransactionIsolation.Serializable, false) {
        val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        jdbcConn.isReadOnly
      }
      assertTrue(insideReadOnly == true, insideNotReadOnly == false)
    },
    test("single-arg transact defaults to Serializable and readOnly false") {
      val conn                  = fakeConnection()
      val tx                    = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      val (iso, ro, autoCommit) = tx.transact {
        val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        (jdbcConn.getTransactionIsolation, jdbcConn.isReadOnly, jdbcConn.getAutoCommit)
      }
      assertTrue(
        iso == Connection.TRANSACTION_SERIALIZABLE,
        ro == false,
        autoCommit == false
      )
    },
    test("isolation and readOnly restored after successful transact via fake connection") {
      val conn           = fakeConnection()
      val prevIso        = conn.getTransactionIsolation
      val prevReadOnly   = conn.isReadOnly
      val prevAutoCommit = conn.getAutoCommit
      val tx             = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      tx.transact(TransactionIsolation.ReadCommitted, true) {
        val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        assertTrue(jdbcConn.getTransactionIsolation == Connection.TRANSACTION_READ_COMMITTED)
        assertTrue(jdbcConn.isReadOnly == true)
      }
      assertTrue(
        conn.getTransactionIsolation == prevIso,
        conn.isReadOnly == prevReadOnly,
        conn.getAutoCommit == prevAutoCommit
      )
    },
    test("isolation and readOnly restored after failed transact (rollback) via fake") {
      val conn           = fakeConnection()
      val prevIso        = conn.getTransactionIsolation
      val prevReadOnly   = conn.isReadOnly
      val prevAutoCommit = conn.getAutoCommit
      val tx             = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      try {
        tx.transact(TransactionIsolation.RepeatableRead, true) {
          val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
          assertTrue(jdbcConn.getTransactionIsolation == Connection.TRANSACTION_REPEATABLE_READ)
          throw new RuntimeException("boom")
        }
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(
        conn.getTransactionIsolation == prevIso,
        conn.isReadOnly == prevReadOnly,
        conn.getAutoCommit == prevAutoCommit
      )
    },
    test("isolation and readOnly restored after successful transact via shared real connection") {
      val underlying = DriverManager.getConnection("jdbc:sqlite::memory:")
      try {
        val prevIso = underlying.getTransactionIsolation
        val tx      = new JdbcTransactor(() => nonClosing(underlying), SqlDialect.SQLite)
        tx.transact(TransactionIsolation.ReadCommitted, false) {
          val jdbcConn = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
          assertTrue(jdbcConn.getTransactionIsolation == Connection.TRANSACTION_READ_COMMITTED)
        }
        assertTrue(underlying.getTransactionIsolation == prevIso)
      } finally underlying.close()
    },
    test("transact with Serializable commits") {
      val underlying = DriverManager.getConnection("jdbc:sqlite::memory:")
      try {
        val tx = new JdbcTransactor(() => nonClosing(underlying), SqlDialect.SQLite)
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS tx_iso_commit_t (id INTEGER NOT NULL)").update
        }
        tx.transact(TransactionIsolation.Serializable, false) {
          sql"INSERT INTO tx_iso_commit_t (id) VALUES (${DbValue.DbInt(1)})".update
        }
        val rows = tx.connect {
          sql"SELECT id FROM tx_iso_commit_t".query[Int]
        }
        assertTrue(rows == List(1))
      } finally underlying.close()
    },
    test("readOnly transact still allows read (SQLite readOnly flag ignored but transact succeeds)") {
      val underlying = DriverManager.getConnection("jdbc:sqlite::memory:")
      try {
        val tx = new JdbcTransactor(() => nonClosing(underlying), SqlDialect.SQLite)
        tx.connect {
          Frag.literal("CREATE TABLE ro_read (id INTEGER NOT NULL)").update
          sql"INSERT INTO ro_read (id) VALUES (${DbValue.DbInt(42)})".update
        }
        val rows = tx.transact(TransactionIsolation.Serializable, true) {
          sql"SELECT id FROM ro_read".query[Int]
        }
        assertTrue(rows == List(42))
      } finally underlying.close()
    },
    test("next transact sees defaults after previous transact via fake") {
      val conn = fakeConnection()
      val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite)
      tx.transact(TransactionIsolation.ReadUncommitted, true) {
        val c = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        assertTrue(c.isReadOnly == true)
      }
      val secondReadOnly = tx.transact(TransactionIsolation.Serializable, false) {
        val c = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        c.isReadOnly
      }
      val secondIso = tx.transact(TransactionIsolation.Serializable, false) {
        val c = summon[DbTx].connection.asInstanceOf[JdbcConnection].underlying
        c.getTransactionIsolation
      }
      assertTrue(secondReadOnly == false, secondIso == Connection.TRANSACTION_SERIALIZABLE)
    }
  )
}
