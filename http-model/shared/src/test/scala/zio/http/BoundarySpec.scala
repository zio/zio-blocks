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

object BoundarySpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Boundary")(
    suite("construction")(
      test("value returns the string passed to constructor") {
        assertTrue(Boundary("abc123").value == "abc123")
      },
      test("equality works by value") {
        assertTrue(Boundary("x") == Boundary("x"))
      },
      test("toString returns the value") {
        assertTrue(Boundary("test-boundary").toString == "test-boundary")
      }
    ),
    suite("generate")(
      test("produces a non-empty boundary") {
        val b = Boundary.generate
        assertTrue(b.value.nonEmpty)
      },
      test("produces 24-character boundaries") {
        val b = Boundary.generate
        assertTrue(b.value.length == 24)
      },
      test("produces different values on successive calls") {
        val b1 = Boundary.generate
        val b2 = Boundary.generate
        assertTrue(b1 != b2)
      },
      test("generated boundary contains only alphanumeric characters") {
        val b = Boundary.generate
        assertTrue(b.value.forall(c => c.isLetterOrDigit))
      }
    )
  )
}
