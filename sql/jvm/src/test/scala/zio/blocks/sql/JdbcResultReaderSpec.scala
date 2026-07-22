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
import java.sql.ResultSet
import java.time.Instant
import java.util.Calendar
import scala.collection.mutable.ArrayBuffer

object JdbcResultReaderSpec extends ZIOSpecDefault {

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
    }
  )

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
