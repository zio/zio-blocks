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

package zio.http.headers

import zio.test._

object CookieHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("CookieHeaders")(
    suite("CookieHeader")(
      test("parse and render") {
        val result = CookieHeader.parse("session=abc123; theme=dark")
        assertTrue(
          result == Right(CookieHeader("session=abc123; theme=dark")),
          result.map(_.headerName) == Right("cookie")
        )
      },
      test("render") {
        assertTrue(CookieHeader.render(CookieHeader("a=b")) == "a=b")
      }
    ),
    suite("SetCookieHeader")(
      test("parse and render") {
        val result = SetCookieHeader.parse("session=abc123; Path=/; HttpOnly")
        assertTrue(
          result == Right(SetCookieHeader("session=abc123; Path=/; HttpOnly")),
          result.map(_.headerName) == Right("set-cookie")
        )
      },
      test("render") {
        assertTrue(SetCookieHeader.render(SetCookieHeader("a=b; Path=/")) == "a=b; Path=/")
      }
    )
  )
}
