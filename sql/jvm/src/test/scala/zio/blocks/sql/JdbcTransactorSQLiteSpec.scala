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

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.nio.file.Files
import java.sql.{Connection, DriverManager, SQLException, Statement}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

import org.sqlite.{SQLiteConfig, SQLiteConnection, SQLiteDataSource}

object JdbcTransactorSQLiteSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  private def busyTimeoutViaPragma(conn: Connection): Int = {
    val st = conn.createStatement()
    try {
      val rs = st.executeQuery("PRAGMA busy_timeout")
      try {
        rs.next()
        rs.getInt(1)
      } finally rs.close()
    } finally st.close()
  }

  private def transactionModeViaConfig(conn: Connection): String = {
    val sqliteConn = conn match {
      case sc: SQLiteConnection => sc
      case other                =>
        if (other.isWrapperFor(classOf[SQLiteConnection])) other.unwrap(classOf[SQLiteConnection])
        else throw new IllegalArgumentException("not a SQLiteConnection")
    }
    sqliteConn.getConnectionConfig.getTransactionMode.toString
  }

  private def wrappedDataSource(delegate: DataSource): DataSource =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[DataSource]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "getConnection" =>
              val c = delegate.getConnection
              wrappedConnection(c)
            case "getLogWriter" => delegate.getLogWriter
            case "setLogWriter" =>
              delegate.setLogWriter(args.nn(0).asInstanceOf[java.io.PrintWriter]); null
            case "setLoginTimeout" =>
              delegate.setLoginTimeout(args.nn(0).asInstanceOf[Integer].intValue()); null
            case "getLoginTimeout" => Integer.valueOf(delegate.getLoginTimeout)
            case "getParentLogger" => delegate.getParentLogger
            case "unwrap"          =>
              val iface = args.nn(0).asInstanceOf[Class[?]]
              if (iface.isInstance(delegate)) delegate.asInstanceOf[AnyRef] else null
            case "isWrapperFor" =>
              val iface = args.nn(0).asInstanceOf[Class[?]]
              java.lang.Boolean.valueOf(iface.isInstance(delegate) || delegate.isWrapperFor(iface))
            case "toString" => "WrappedTestDataSource"
            case _          => null
          }
        }
      )
      .asInstanceOf[DataSource]

  private def wrappedConnection(delegate: Connection): Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "prepareStatement" => delegate.prepareStatement(args.nn(0).asInstanceOf[String])
            case "createStatement"  => delegate.createStatement()
            case "prepareCall"      => delegate.prepareCall(args.nn(0).asInstanceOf[String])
            case "getAutoCommit"    => java.lang.Boolean.valueOf(delegate.getAutoCommit)
            case "setAutoCommit"    =>
              delegate.setAutoCommit(args.nn(0).asInstanceOf[java.lang.Boolean].booleanValue()); null
            case "commit"       => delegate.commit(); null
            case "rollback"     => delegate.rollback(); null
            case "close"        => delegate.close(); null
            case "isClosed"     => java.lang.Boolean.valueOf(delegate.isClosed)
            case "isWrapperFor" =>
              val iface = args.nn(0).asInstanceOf[Class[?]]
              java.lang.Boolean.valueOf(iface.isInstance(delegate) || delegate.isWrapperFor(iface))
            case "unwrap" =>
              val iface = args.nn(0).asInstanceOf[Class[?]]
              if (iface.isInstance(delegate)) delegate.asInstanceOf[AnyRef]
              else delegate.unwrap(iface).asInstanceOf[AnyRef]
            case "getTransactionIsolation" => Integer.valueOf(delegate.getTransactionIsolation)
            case "setTransactionIsolation" =>
              delegate.setTransactionIsolation(args.nn(0).asInstanceOf[Integer].intValue()); null
            case "isReadOnly"  => java.lang.Boolean.valueOf(delegate.isReadOnly)
            case "setReadOnly" =>
              delegate.setReadOnly(args.nn(0).asInstanceOf[java.lang.Boolean].booleanValue()); null
            case "toString" => "WrappedTestConnection"
            case _          =>
              if (args == null) {
                try method.invoke(delegate)
                catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
              } else {
                try method.invoke(delegate, args*)
                catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
              }
          }
        }
      )
      .asInstanceOf[Connection]

  private def mockConnection(): Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "isWrapperFor" => java.lang.Boolean.FALSE
            case "unwrap"       => null
            case "isClosed"     => java.lang.Boolean.FALSE
            case "toString"     => "MockConnection"
            case _              =>
              val rt = method.getReturnType
              if (rt == java.lang.Boolean.TYPE) java.lang.Boolean.FALSE
              else if (rt == java.lang.Integer.TYPE) Integer.valueOf(0)
              else if (rt == java.lang.Long.TYPE) java.lang.Long.valueOf(0L)
              else null
          }
        }
      )
      .asInstanceOf[Connection]

  private def trackingSQLiteWrapper(
    delegate: Connection,
    closedFlag: AtomicBoolean,
    failUnwrap: Boolean = false,
    failGetAutoCommit: Boolean = false,
    failSetAutoCommit: Boolean = false,
    failClose: Boolean = false
  ): Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "isWrapperFor" => java.lang.Boolean.TRUE
            case "unwrap"       =>
              if (failUnwrap) throw new SQLException("unwrap boom")
              else delegate.unwrap(args.nn(0).asInstanceOf[Class[?]]).asInstanceOf[AnyRef]
            case "getAutoCommit" =>
              if (failGetAutoCommit) throw new SQLException("getAutoCommit boom")
              else java.lang.Boolean.valueOf(delegate.getAutoCommit)
            case "setAutoCommit" =>
              if (failSetAutoCommit) throw new SQLException("setAutoCommit boom")
              else { delegate.setAutoCommit(args.nn(0).asInstanceOf[java.lang.Boolean].booleanValue()); null }
            case "close" =>
              closedFlag.set(true)
              if (failClose) throw new SQLException("close boom")
              else { delegate.close(); null }
            case "isClosed" => java.lang.Boolean.valueOf(closedFlag.get() || delegate.isClosed)
            case "toString" => "TrackingSQLiteWrapper"
            case _          =>
              if (args == null) {
                try method.invoke(delegate)
                catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
              } else {
                try method.invoke(delegate, args*)
                catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
              }
          }
        }
      )
      .asInstanceOf[Connection]

  def spec: Spec[TestEnvironment, Any] = suite("JdbcTransactorSQLiteSpec")(
    test("fromUrl configures busy_timeout and IMMEDIATE") {
      val tmp = Files.createTempFile("sqlite-fromUrl", ".db").toFile
      tmp.deleteOnExit()
      val url = s"jdbc:sqlite:${tmp.getAbsolutePath}"
      val tx  = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      try {
        val ok = tx.transact {
          val conn = summon[DbTx].connection match {
            case jc: JdbcConnection => jc.underlying
            case other              => throw new IllegalStateException(s"unexpected $other")
          }
          val busy = busyTimeoutViaPragma(conn)
          val mode = transactionModeViaConfig(conn)
          assertTrue(busy == 5000, mode == "IMMEDIATE")
        }
        ok
      } finally tmp.delete()
    },
    test("sqlite(DataSource) with concrete SQLiteDataSource configures busy_timeout and IMMEDIATE") {
      val sds = new SQLiteDataSource()
      sds.setUrl("jdbc:sqlite::memory:")
      val tx = JdbcTransactor.sqlite(sds)
      // DataSource-level config also set via fromDataSource reflection
      assertTrue(sds.getConfig.getBusyTimeout == 5000) &&
      assertTrue(sds.getConfig.getTransactionMode == SQLiteConfig.TransactionMode.IMMEDIATE) &&
      tx.transact {
        val conn = summon[DbTx].connection match {
          case jc: JdbcConnection => jc.underlying
          case other              => throw new IllegalStateException(s"unexpected $other")
        }
        val busy = busyTimeoutViaPragma(conn)
        val mode = transactionModeViaConfig(conn)
        assertTrue(busy == 5000, mode == "IMMEDIATE")
      }
    },
    test("wrapped/pooled DataSource via isWrapperFor/unwrap still configures busy_timeout and IMMEDIATE") {
      val real = new SQLiteDataSource()
      real.setUrl("jdbc:sqlite::memory:")
      val wrapped = wrappedDataSource(real)
      val tx      = JdbcTransactor.sqlite(wrapped)
      tx.transact {
        val conn = summon[DbTx].connection match {
          case jc: JdbcConnection => jc.underlying
          case other              => throw new IllegalStateException(s"unexpected $other")
        }
        val isWrapper = conn.isWrapperFor(classOf[SQLiteConnection])
        val unwrapped = conn.unwrap(classOf[SQLiteConnection])
        val busy      = busyTimeoutViaPragma(conn)
        val mode      = transactionModeViaConfig(conn)
        assertTrue(isWrapper, unwrapped != null, busy == 5000, mode == "IMMEDIATE")
      }
    },
    test("connect also configures pooled SQLite connection") {
      val real = new SQLiteDataSource()
      real.setUrl("jdbc:sqlite::memory:")
      val wrapped = wrappedDataSource(real)
      val tx      = JdbcTransactor.sqlite(wrapped)
      tx.connect {
        val conn = summon[DbCon].connection match {
          case jc: JdbcConnection => jc.underlying
          case other              => throw new IllegalStateException(s"unexpected $other")
        }
        val busy = busyTimeoutViaPragma(conn)
        val mode = transactionModeViaConfig(conn)
        assertTrue(busy == 5000, mode == "IMMEDIATE")
      }
    },
    test("configureSQLiteConnection skips non-SQLite connections without throwing") {
      val mock = mockConnection()
      // Should not throw; mock is not SQLite so early return
      JdbcTransactor.configureSQLiteConnection(mock)
      assertTrue(true)
    },
    test("configureSQLiteConnection propagates failures on real SQLite connections") {
      val tmp = Files.createTempFile("sqlite-propagate", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        // Wrap real SQLite connection but make unwrap throw
        val failingWrapper = Proxy
          .newProxyInstance(
            getClass.getClassLoader,
            Array(classOf[Connection]),
            new InvocationHandler {
              override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef =
                method.getName match {
                  case "isWrapperFor" => java.lang.Boolean.TRUE
                  case "unwrap"       => throw new java.sql.SQLException("unwrap boom")
                  case "toString"     => "FailingWrapper"
                  case _              =>
                    if (args == null) {
                      try method.invoke(realConn)
                      catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
                    } else {
                      try method.invoke(realConn, args*)
                      catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
                    }
                }
            }
          )
          .asInstanceOf[Connection]
        var threw = false
        try JdbcTransactor.configureSQLiteConnection(failingWrapper)
        catch { case _: java.sql.SQLException => threw = true; case _: Throwable => threw = true }
        assertTrue(threw)
      } finally {
        realConn.close()
        tmp.delete()
      }
    },
    test("connect closes connection when SQLite configuration fails") {
      val tmp = Files.createTempFile("sqlite-connect-close", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        val closed     = new AtomicBoolean(false)
        val wrapper    = trackingSQLiteWrapper(realConn, closed, failUnwrap = true, failClose = false)
        val tx         = new JdbcTransactor(() => wrapper, SqlDialect.SQLite)
        var threw      = false
        var suppressed = false
        try tx.connect(1)
        catch {
          case e: SQLException =>
            threw = e.getMessage.contains("unwrap boom")
            suppressed = e.getSuppressed.nonEmpty
          case _: Throwable => threw = true
        }
        assertTrue(threw, closed.get(), !suppressed)
      } finally {
        try realConn.close()
        catch { case _: Throwable => () }
        tmp.delete()
      }
    },
    test("connect suppresses close failure onto configuration exception") {
      val tmp = Files.createTempFile("sqlite-connect-suppress", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        val wrapper       = trackingSQLiteWrapper(realConn, new AtomicBoolean(false), failUnwrap = true, failClose = true)
        val tx            = new JdbcTransactor(() => wrapper, SqlDialect.SQLite)
        var threw         = false
        var hasSuppressed = false
        try tx.connect(1)
        catch {
          case e: SQLException =>
            threw = e.getMessage.contains("unwrap boom")
            hasSuppressed = e.getSuppressed.exists(_.getMessage.contains("close boom"))
          case _: Throwable => threw = true
        }
        assertTrue(threw, hasSuppressed)
      } finally {
        try realConn.close()
        catch { case _: Throwable => () }
        tmp.delete()
      }
    },
    test("transact closes connection when SQLite configuration fails") {
      val tmp = Files.createTempFile("sqlite-transact-config-close", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        val closed  = new AtomicBoolean(false)
        val wrapper = trackingSQLiteWrapper(realConn, closed, failUnwrap = true)
        val tx      = new JdbcTransactor(() => wrapper, SqlDialect.SQLite)
        var threw   = false
        try tx.transact(1)
        catch { case _: SQLException => threw = true; case _: Throwable => threw = true }
        assertTrue(threw, closed.get())
      } finally {
        try realConn.close()
        catch { case _: Throwable => () }
        tmp.delete()
      }
    },
    test("transact closes connection when getAutoCommit fails") {
      val tmp = Files.createTempFile("sqlite-transact-getAC", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        val closed  = new AtomicBoolean(false)
        val wrapper = trackingSQLiteWrapper(realConn, closed, failGetAutoCommit = true)
        val tx      = new JdbcTransactor(() => wrapper, SqlDialect.SQLite)
        var threw   = false
        try tx.transact(1)
        catch { case _: SQLException => threw = true; case _: Throwable => threw = true }
        assertTrue(threw, closed.get())
      } finally {
        try realConn.close()
        catch { case _: Throwable => () }
        tmp.delete()
      }
    },
    test("transact closes connection when setAutoCommit(false) fails") {
      val tmp = Files.createTempFile("sqlite-transact-setAC", ".db").toFile
      tmp.deleteOnExit()
      val realConn = DriverManager.getConnection(s"jdbc:sqlite:${tmp.getAbsolutePath}")
      try {
        val closed  = new AtomicBoolean(false)
        val wrapper = trackingSQLiteWrapper(realConn, closed, failSetAutoCommit = true)
        val tx      = new JdbcTransactor(() => wrapper, SqlDialect.SQLite)
        var threw   = false
        try tx.transact(1)
        catch { case _: SQLException => threw = true; case _: Throwable => threw = true }
        assertTrue(threw, closed.get())
      } finally {
        try realConn.close()
        catch { case _: Throwable => () }
        tmp.delete()
      }
    },
    test("contention: second transaction waits rather than failing immediately") {
      val tmp = Files.createTempFile("sqlite-contention", ".db").toFile
      tmp.deleteOnExit()
      val url = s"jdbc:sqlite:${tmp.getAbsolutePath}"
      val tx  = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      tx.connect {
        Frag.literal("CREATE TABLE contention_test (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)").update
      }
      val executor = Executors.newFixedThreadPool(2)
      try {
        val holderStarted   = new CountDownLatch(1)
        val holderCanCommit = new CountDownLatch(1)
        // Explicit raw JDBC holder with IMMEDIATE/busy_timeout=5000, holds RESERVED lock deterministically
        val holderFuture = executor.submit(new java.util.concurrent.Callable[Unit] {
          override def call(): Unit = {
            val props = new java.util.Properties()
            props.setProperty("busy_timeout", "5000")
            props.setProperty("transaction_mode", "IMMEDIATE")
            val holderConn = DriverManager.getConnection(url, props)
            try {
              holderConn.setAutoCommit(false)
              val st = holderConn.createStatement()
              try st.executeUpdate("INSERT INTO contention_test (v) VALUES ('first')")
              finally st.close()
              holderStarted.countDown()
              // Hold lock until main thread releases
              holderCanCommit.await(5, TimeUnit.SECONDS)
              holderConn.commit()
            } finally holderConn.close()
          }
        })
        assertTrue(holderStarted.await(5, TimeUnit.SECONDS))
        // Second transaction via JdbcTransactor against same file DB — must wait via busy_timeout, not fail BUSY
        val secondFuture = executor.submit(new java.util.concurrent.Callable[Unit] {
          override def call(): Unit =
            tx.transact {
              Frag.literal("INSERT INTO contention_test (v) VALUES ('second')").update
            }
        })
        // Release holder so second can proceed; both get bounded awaits so test cannot hang
        holderCanCommit.countDown()
        var succeeded  = false
        var busyFailed = false
        try {
          secondFuture.get(5, TimeUnit.SECONDS)
          succeeded = true
        } catch {
          case e: java.util.concurrent.ExecutionException =>
            val cause = e.getCause
            if (cause != null && cause.getMessage != null && cause.getMessage.contains("BUSY")) busyFailed = true
            else busyFailed = true
          case _: Throwable => busyFailed = true
        }
        holderFuture.get(5, TimeUnit.SECONDS)
        val rows = tx.connect {
          Frag.literal("SELECT COUNT(*) FROM contention_test").queryOne[Int]
        }
        assertTrue(succeeded, !busyFailed, rows.contains(2))
      } finally {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        tmp.delete()
      }
    }
  )
}
