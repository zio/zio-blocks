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

package zio.blocks.telemetry

import zio.test._
import java.time.Duration

object HttpSenderSpec extends ZIOSpecDefault {
  def spec = suite("HttpSenderSpec")(
    test("HttpResponse case class creation") {
      val response = HttpResponse(
        statusCode = 200,
        body = "test body".getBytes(),
        headers = Map("content-type" -> "application/json")
      )
      assertTrue(
        response.statusCode == 200 &&
          response.body.sameElements("test body".getBytes()) &&
          response.headers.get("content-type").contains("application/json")
      )
    },
    test("HttpResponse case class with empty body") {
      val response = HttpResponse(
        statusCode = 204,
        body = Array.empty[Byte],
        headers = Map.empty[String, String]
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
          "x-header-1"   -> "value1",
          "x-header-2"   -> "value2",
          "content-type" -> "text/plain"
        )
      )
      assertTrue(
        response.headers.size == 3 &&
          response.headers.get("x-header-1").contains("value1") &&
          response.headers.get("x-header-2").contains("value2") &&
          response.headers.get("content-type").contains("text/plain")
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
    test("JdkHttpSender shutdown completes without error") {
      val sender = new JdkHttpSender()
      val result = sender.shutdown()
      assertTrue(result == ())
    }
  )
}
