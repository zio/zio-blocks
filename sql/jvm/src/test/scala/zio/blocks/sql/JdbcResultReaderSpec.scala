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
import java.sql.{DriverManager, ResultSet}
import java.time.Instant
import java.util.Calendar
import scala.collection.mutable.ArrayBuffer

object JdbcResultReaderSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  def spec: Spec[TestEnvironment, Any] = suite("JdbcResultReaderSpec")(
    test("getInstant(index) reads via getTimestamp with UTC Calendar") {
      val instant = Instant.parse("2025-07-10T15:52:46.632293Z")
      val calls   = ArrayBuffer.empty[(String, List[AnyRef])]
      val reader  = new JdbcResultReader(resultSetProxy(calls, instant))

      val decoded1 = reader.getInstant(1)
      val decoded2 = reader.getInstant(2)

      val utcCals = calls.collect { case ("getTimestamp", args) =>
        args.collectFirst { case cal: Calendar => cal }
      }.flatten

      assertTrue(
        decoded1 == instant,
        decoded2 == instant,
        utcCals.size == 2,
        utcCals(0) eq utcCals(1), // same calendar instance (cached)
        utcCals(0).getTimeZone.getID == "UTC"
      )
    },
    test("getInstant(label) reads via getTimestamp with UTC Calendar") {
      val instant = Instant.parse("2025-07-10T15:52:46.632293Z")
      val calls   = ArrayBuffer.empty[(String, List[AnyRef])]
      val reader  = new JdbcResultReader(resultSetProxy(calls, instant))

      val decoded1 = reader.getInstant("published_until")
      val decoded2 = reader.getInstant("created_at")

      val utcCals = calls.collect { case ("getTimestamp", args) =>
        args.collectFirst { case cal: Calendar => cal }
      }.flatten

      assertTrue(
        decoded1 == instant,
        decoded2 == instant,
        utcCals.size == 2,
        utcCals(0) eq utcCals(1), // same calendar instance (cached)
        utcCals(0).getTimeZone.getID == "UTC"
      )
    },
    test("isNull(index) records wasNull per column and resets per row") {
      withSqlite { conn =>
        val stmt = conn.createStatement()
        stmt.executeUpdate("CREATE TABLE null_bitmap_rt (a INTEGER, b TEXT)")
        stmt.executeUpdate("INSERT INTO null_bitmap_rt (a, b) VALUES (NULL, 'x')")
        stmt.executeUpdate("INSERT INTO null_bitmap_rt (a, b) VALUES (7, NULL)")
        val rs = new JdbcResultSet(stmt.executeQuery("SELECT a, b FROM null_bitmap_rt ORDER BY rowid"))
        try {
          val hasRow1   = rs.next()
          val reader    = rs.reader
          val unread1_1 = reader.isNull(1)
          val unread1_2 = reader.isNull(2)
          val int1      = reader.getInt(1)
          val null1     = reader.isNull(1)
          val str1      = reader.getString(2)
          val nonNull2  = reader.isNull(2)
          val hasRow2   = rs.next()
          val reset1_1  = reader.isNull(1)
          val reset1_2  = reader.isNull(2)
          val int2      = reader.getInt(1)
          val null2     = reader.isNull(1)
          val str2      = reader.getString(2)
          val null22    = reader.isNull(2)
          assertTrue(
            hasRow1 && hasRow2,
            !unread1_1,
            !unread1_2,
            int1 == 0,
            null1,
            str1 == "x",
            !nonNull2,
            !reset1_1,
            !reset1_2,
            int2 == 7,
            !null2,
            str2 == null,
            null22
          )
        } finally {
          rs.close()
          stmt.close()
        }
      }
    },
    test("isNull(label) records wasNull for label-based reads") {
      withSqlite { conn =>
        val stmt = conn.createStatement()
        stmt.executeUpdate("CREATE TABLE null_bitmap_label_rt (a INTEGER, b TEXT)")
        stmt.executeUpdate("INSERT INTO null_bitmap_label_rt (a, b) VALUES (NULL, 'x')")
        val rs = new JdbcResultSet(stmt.executeQuery("SELECT a, b FROM null_bitmap_label_rt"))
        try {
          val hasRow   = rs.next()
          val reader   = rs.reader
          val unreadA  = reader.isNull("a")
          val unreadB  = reader.isNull("b")
          val intA     = reader.getInt("a")
          val nullA    = reader.isNull("a")
          val strB     = reader.getString("b")
          val nonNullB = reader.isNull("b")
          assertTrue(
            hasRow,
            !unreadA,
            !unreadB,
            intA == 0,
            nullA,
            strB == "x",
            !nonNullB
          )
        } finally {
          rs.close()
          stmt.close()
        }
      }
    }
  )

  private def withSqlite[A](f: java.sql.Connection => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    try f(conn)
    finally conn.close()
  }

  private def resultSetProxy(calls: ArrayBuffer[(String, List[AnyRef])], instant: Instant): ResultSet = {
    val handler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = {
        val arguments = Option(args).map(_.toList).getOrElse(Nil)
        method.getName match {
          case "getTimestamp" =>
            calls += method.getName -> arguments
            java.sql.Timestamp.from(instant)
          case "wasNull"  => java.lang.Boolean.FALSE
          case "toString" => "JdbcResultReaderSpec.ResultSetProxy"
          case other      => throw new UnsupportedOperationException(s"Unexpected ResultSet method: $other")
        }
      }
    }

    Proxy
      .newProxyInstance(getClass.getClassLoader, Array(classOf[ResultSet]), handler)
      .asInstanceOf[ResultSet]
  }
}
