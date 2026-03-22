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

package zio.http

import zio.test._

object HeaderSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Header")(
    suite("Custom header")(
      test("custom headers via rawGet") {
        val headers = Headers("x-request-id" -> "abc-123", "x-trace-id" -> "trace-456")
        assertTrue(
          headers.rawGet("x-request-id") == Some("abc-123"),
          headers.rawGet("x-trace-id") == Some("trace-456"),
          headers.rawGet("x-missing") == None
        )
      }
    )
  )
}
