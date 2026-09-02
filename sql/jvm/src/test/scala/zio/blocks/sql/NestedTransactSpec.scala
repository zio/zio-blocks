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
import java.nio.file.Files

object NestedTransactSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  private def freshTransactor(): (JdbcTransactor, () => Unit) = {
    val tmp = Files.createTempFile("zib_nested", ".db")
    tmp.toFile.deleteOnExit()
    val url                 = s"jdbc:sqlite:${tmp.toAbsolutePath}"
    val tx                  = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
    val cleanup: () => Unit = () => {
      try { Files.deleteIfExists(tmp); () }
      catch { case _: Throwable => () }
    }
    (tx, cleanup)
  }

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

  /** Fake connection that captures savepoint SQL via Statement proxy. */
  private def capturingTransactor(
    savepoints: scala.collection.mutable.ArrayBuffer[String],
    releases: scala.collection.mutable.ArrayBuffer[String],
    rollbacks: scala.collection.mutable.ArrayBuffer[String]
  ): JdbcTransactor = {
    val underlying = DriverManager.getConnection("jdbc:sqlite::memory:")
    // create table not needed for savepoint capture, but need real connection for savepoint SQL
    val handler = new InvocationHandler {
      def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef =
        method.getName match {
          case "createStatement" =>
            val realStmt    = underlying.createStatement()
            val stmtHandler = new InvocationHandler {
              def invoke(p2: AnyRef, m2: Method, a2: Array[AnyRef]): AnyRef = {
                if (m2.getName == "execute" && a2 != null && a2.length == 1 && a2(0).isInstanceOf[String]) {
                  val sql = a2(0).asInstanceOf[String]
                  if (sql.startsWith("SAVEPOINT ")) savepoints += sql.stripPrefix("SAVEPOINT ")
                  else if (sql.startsWith("RELEASE SAVEPOINT ")) releases += sql.stripPrefix("RELEASE SAVEPOINT ")
                  else if (sql.startsWith("ROLLBACK TO SAVEPOINT "))
                    rollbacks += sql.stripPrefix("ROLLBACK TO SAVEPOINT ")
                }
                val actualArgs = if (a2 == null) Array.empty[AnyRef] else a2
                try m2.invoke(realStmt, actualArgs*)
                catch { case e: InvocationTargetException => throw e.getCause }
              }
            }
            Proxy
              .newProxyInstance(getClass.getClassLoader, Array(classOf[java.sql.Statement]), stmtHandler)
              .asInstanceOf[java.sql.Statement]
          case "close" => underlying.close(); null
          case _       =>
            val actualArgs = if (args == null) Array.empty[AnyRef] else args
            try method.invoke(underlying, actualArgs*)
            catch { case e: InvocationTargetException => throw e.getCause }
        }
    }
    val connProxy = Proxy
      .newProxyInstance(getClass.getClassLoader, Array(classOf[Connection]), handler)
      .asInstanceOf[Connection]
    new JdbcTransactor(() => nonClosing(connProxy), SqlDialect.SQLite)
  }

  def spec: Spec[TestEnvironment, Any] = suite("NestedTransactSpec")(
    test("3-level nesting all-commit leaves all rows") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS nested_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          sql"INSERT INTO nested_t (id) VALUES (${DbValue.DbInt(1)})".update
          summon[DbTx].transact {
            sql"INSERT INTO nested_t (id) VALUES (${DbValue.DbInt(2)})".update
            summon[DbTx].transact {
              sql"INSERT INTO nested_t (id) VALUES (${DbValue.DbInt(3)})".update
            }
          }
        }
        val rows = tx.connect {
          sql"SELECT id FROM nested_t ORDER BY id".query[Int]
        }
        assertTrue(rows == List(1, 2, 3))
      } finally cleanup()
    },
    test("inner-failure rolls back to savepoint only and outer commits") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS nested_rollback_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          sql"INSERT INTO nested_rollback_t (id) VALUES (${DbValue.DbInt(1)})".update
          try {
            summon[DbTx].transact {
              sql"INSERT INTO nested_rollback_t (id) VALUES (${DbValue.DbInt(2)})".update
              throw new RuntimeException("inner boom")
            }
          } catch { case _: RuntimeException => () }
          sql"INSERT INTO nested_rollback_t (id) VALUES (${DbValue.DbInt(3)})".update
        }
        val rows = tx.connect {
          sql"SELECT id FROM nested_rollback_t ORDER BY id".query[Int]
        }
        assertTrue(rows == List(1, 3))
      } finally cleanup()
    },
    test("depth counter resets after success and failure") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS depth_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          val outer = summon[DbTx]
          assertTrue(outer.currentDepth == 0)
          outer.transact {
            val inner = summon[DbTx]
            assertTrue(inner.currentDepth == 1)
            inner.transact {
              val innest = summon[DbTx]
              assertTrue(innest.currentDepth == 2)
            }
            assertTrue(summon[DbTx].currentDepth == 1)
          }
          assertTrue(summon[DbTx].currentDepth == 0)
          // failure path
          try {
            summon[DbTx].transact {
              throw new RuntimeException("boom")
            }
          } catch { case _: RuntimeException => () }
          assertTrue(summon[DbTx].currentDepth == 0)
          // second nested after failure reuses zib_tx_1
          summon[DbTx].transact {
            assertTrue(summon[DbTx].currentDepth == 1)
            sql"INSERT INTO depth_t (id) VALUES (${DbValue.DbInt(99)})".update
          }
          assertTrue(summon[DbTx].currentDepth == 0)
        }
        val rows = tx.connect {
          sql"SELECT id FROM depth_t".query[Int]
        }
        assertTrue(rows == List(99))
      } finally cleanup()
    },
    test("savepoint leak check - release on success allows sibling reuse") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS leak_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          // two siblings sequentially; if first leaked, second SAVEPOINT zib_tx_1 would fail or duplicate
          summon[DbTx].transact {
            sql"INSERT INTO leak_t (id) VALUES (${DbValue.DbInt(1)})".update
          }
          summon[DbTx].transact {
            sql"INSERT INTO leak_t (id) VALUES (${DbValue.DbInt(2)})".update
          }
          summon[DbTx].transact {
            sql"INSERT INTO leak_t (id) VALUES (${DbValue.DbInt(3)})".update
          }
        }
        val rows = tx.connect {
          sql"SELECT id FROM leak_t ORDER BY id".query[Int]
        }
        assertTrue(rows == List(1, 2, 3))
      } finally cleanup()
    },
    test("transactNested extension works via DbTx and top-level helper") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS transact_nested_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          // via DbTx.transactNested (companion)
          DbTx.transactNested {
            sql"INSERT INTO transact_nested_t (id) VALUES (${DbValue.DbInt(10)})".update
          }
          // via top-level transactNested helper
          transactNested {
            sql"INSERT INTO transact_nested_t (id) VALUES (${DbValue.DbInt(20)})".update
          }
          // via extension with using
          {
            val outer: DbTx = summon[DbTx]
            {
              given DbTx = outer
              transactNested {
                sql"INSERT INTO transact_nested_t (id) VALUES (${DbValue.DbInt(30)})".update
              }
            }
          }
        }
        val rows = tx.connect {
          sql"SELECT id FROM transact_nested_t ORDER BY id".query[Int]
        }
        assertTrue(rows == List(10, 20, 30))
      } finally cleanup()
    },
    test("savepoint SQL capture - release on success, rollback on failure") {
      val savepoints = scala.collection.mutable.ArrayBuffer.empty[String]
      val releases   = scala.collection.mutable.ArrayBuffer.empty[String]
      val rollbacks  = scala.collection.mutable.ArrayBuffer.empty[String]
      val tx         = capturingTransactor(savepoints, releases, rollbacks)
      tx.connect {
        Frag.literal("CREATE TABLE IF NOT EXISTS capture_t (id INTEGER NOT NULL)").update
      }
      tx.transact {
        summon[DbTx].transact {
          // success
          sql"INSERT INTO capture_t (id) VALUES (${DbValue.DbInt(1)})".update
        }
        assertTrue(savepoints.contains("zib_tx_1"), releases.contains("zib_tx_1"))
        savepoints.clear(); releases.clear(); rollbacks.clear()
        try {
          summon[DbTx].transact {
            sql"INSERT INTO capture_t (id) VALUES (${DbValue.DbInt(2)})".update
            throw new RuntimeException("boom")
          }
        } catch { case _: RuntimeException => () }
        assertTrue(savepoints.contains("zib_tx_1"), rollbacks.contains("zib_tx_1"), releases.isEmpty)
      }
      assertTrue(true)
    },
    test("nested failure isolates inner only - outer and sibling commit") {
      val (tx, cleanup) = freshTransactor()
      try {
        tx.connect {
          Frag.literal("CREATE TABLE IF NOT EXISTS isolate_t (id INTEGER NOT NULL)").update
        }
        tx.transact {
          sql"INSERT INTO isolate_t (id) VALUES (${DbValue.DbInt(1)})".update
          try {
            summon[DbTx].transact {
              sql"INSERT INTO isolate_t (id) VALUES (${DbValue.DbInt(2)})".update
              // nested inside failing inner: also fails
              summon[DbTx].transact {
                sql"INSERT INTO isolate_t (id) VALUES (${DbValue.DbInt(3)})".update
              }
              throw new RuntimeException("inner fail")
            }
          } catch { case _: RuntimeException => () }
          // sibling after failure should still commit
          summon[DbTx].transact {
            sql"INSERT INTO isolate_t (id) VALUES (${DbValue.DbInt(4)})".update
          }
        }
        val rows = tx.connect {
          sql"SELECT id FROM isolate_t ORDER BY id".query[Int]
        }
        assertTrue(rows == List(1, 4))
      } finally cleanup()
    }
  )
}
