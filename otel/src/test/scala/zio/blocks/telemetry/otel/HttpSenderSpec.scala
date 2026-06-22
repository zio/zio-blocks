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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._
import zio.test._
import java.time.Duration

object HttpSenderSpec extends ZIOSpecDefault {
  def spec = suite("HttpSenderSpec")(
    test("HttpResponse case class creation") {
      val response = HttpResponse(
        statusCode = 200,
        body = "test body".getBytes(),
        headers = Map("content-type" -> Seq("application/json"))
      )
      assertTrue(
        response.statusCode == 200 &&
          response.body.sameElements("test body".getBytes()) &&
          response.firstHeader("content-type").contains("application/json")
      )
    },
    test("HttpResponse case class with empty body") {
      val response = HttpResponse(
        statusCode = 204,
        body = Array.empty[Byte],
        headers = Map.empty[String, Seq[String]]
      )
      assertTrue(
        response.statusCode == 204 &&
          response.body.isEmpty &&
          response.headers.isEmpty
      )
    },
    test("HttpResponse case class with multiple headers") {
      val response = HttpResponse(
        statusCode = 201,
        body = Array.empty[Byte],
        headers = Map(
          "x-header-1"   -> Seq("value1"),
          "x-header-2"   -> Seq("value2"),
          "content-type" -> Seq("text/plain")
        )
      )
      assertTrue(
        response.headers.size == 3 &&
          response.firstHeader("x-header-1").contains("value1") &&
          response.firstHeader("x-header-2").contains("value2") &&
          response.firstHeader("content-type").contains("text/plain")
      )
    },
    test("HttpResponse preserves multi-value headers") {
      val response = HttpResponse(
        statusCode = 200,
        body = Array.empty[Byte],
        headers = Map("set-cookie" -> Seq("a=1", "b=2"))
      )
      assertTrue(
        response.headers("set-cookie").size == 2 &&
          response.firstHeader("set-cookie").contains("a=1")
      )
    },
    test("JdkHttpSender construction with default timeout") {
      val sender = new JdkHttpSender()
      assertTrue(sender != null)
    },
    test("JdkHttpSender construction with custom timeout") {
      val sender = new JdkHttpSender(timeout = Duration.ofSeconds(60))
      assertTrue(sender != null)
    },
    test("JdkHttpSender shutdown is callable") {
      val sender = new JdkHttpSender()
      sender.shutdown()
      assertTrue(true)
    }
  )
}
