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
import java.sql.{PreparedStatement, Types}
import scala.collection.mutable.ArrayBuffer

object JdbcParamWriterSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("JdbcParamWriterSpec")(
    test("setRaw binds driver-specific values with Types.OTHER") {
      val calls   = ArrayBuffer.empty[(String, List[AnyRef])]
      val writer  = new JdbcParamWriter(preparedStatementProxy(calls))
      val payload = new Object

      writer.setRaw(2, "vector", payload)

      assertTrue(
        calls.toList == List("setObject" -> List(Int.box(2), payload, Int.box(Types.OTHER)))
      )
    },
    test("setRaw binds null with Types.OTHER") {
      val calls  = ArrayBuffer.empty[(String, List[AnyRef])]
      val writer = new JdbcParamWriter(preparedStatementProxy(calls))

      writer.setRaw(1, "jsonb", null)

      assertTrue(
        calls.toList == List("setNull" -> List(Int.box(1), Int.box(Types.OTHER)))
      )
    },
    test("setRaw rejects an empty SQL type name") {
      val calls  = ArrayBuffer.empty[(String, List[AnyRef])]
      val writer = new JdbcParamWriter(preparedStatementProxy(calls))

      val result = scala.util.Try(writer.setRaw(1, "", new Object))

      assertTrue(
        result.isFailure,
        result.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]),
        calls.isEmpty
      )
    }
  )

  private def preparedStatementProxy(calls: ArrayBuffer[(String, List[AnyRef])]): PreparedStatement = {
    val handler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = {
        val arguments = Option(args).map(_.toList).getOrElse(Nil)
        method.getName match {
          case "setObject" | "setNull" =>
            calls += method.getName -> arguments
            null
          case "toString" => "JdbcParamWriterSpec.PreparedStatementProxy"
          case other      => throw new UnsupportedOperationException(s"Unexpected PreparedStatement method: $other")
        }
      }
    }

    Proxy
      .newProxyInstance(getClass.getClassLoader, Array(classOf[PreparedStatement]), handler)
      .asInstanceOf[PreparedStatement]
  }
}
