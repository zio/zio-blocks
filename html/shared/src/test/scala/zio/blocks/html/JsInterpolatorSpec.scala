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

package zio.blocks.html

import zio.test._

object JsInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("js interpolator")(
    test("static JS string") {
      val result = js"var x = 1"
      assertTrue(result == Js("var x = 1"))
    },
    test("JS with interpolated Int") {
      val result = js"var x = ${42}"
      assertTrue(result == Js("var x = 42"))
    },
    test("JS with interpolated String is quoted and escaped") {
      val result = js"var s = ${"hello"}"
      assertTrue(result == Js("""var s = "hello""""))
    },
    test("JS with interpolated Boolean") {
      val result = js"var b = ${true}"
      assertTrue(result == Js("var b = true"))
    }
  )
}
