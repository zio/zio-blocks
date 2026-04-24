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
import CssLength.{CssLengthIntOps, CssLengthDoubleOps}

object CssLengthSpec extends ZIOSpecDefault {
  def spec = suite("CssLength")(
    test("integer value renders without decimal") {
      assertTrue(CssLength(10, "px").render == "10px")
    },
    test("fractional value renders with decimal") {
      assertTrue(CssLength(1.5, "em").render == "1.5em")
    },
    test("Int extension px") {
      assertTrue(10.px == CssLength(10, "px"))
    },
    test("Int extension em") {
      assertTrue(2.em == CssLength(2, "em"))
    },
    test("Int extension rem") {
      assertTrue(1.rem == CssLength(1, "rem"))
    },
    test("Int extension pct") {
      assertTrue(50.pct == CssLength(50, "%"))
    },
    test("Int extension vh") {
      assertTrue(100.vh == CssLength(100, "vh"))
    },
    test("Int extension vw") {
      assertTrue(100.vw == CssLength(100, "vw"))
    },
    test("Double extension px") {
      assertTrue(1.5.px == CssLength(1.5, "px"))
    },
    test("Double extension em") {
      assertTrue(2.5.em == CssLength(2.5, "em"))
    },
    test("Double extension rem") {
      assertTrue(1.5.rem == CssLength(1.5, "rem"))
    },
    test("Double extension pct") {
      assertTrue(50.5.pct == CssLength(50.5, "%"))
    },
    test("Double extension vh") {
      assertTrue(33.3.vh == CssLength(33.3, "vh"))
    },
    test("Double extension vw") {
      assertTrue(66.6.vw == CssLength(66.6, "vw"))
    },
    test("NaN renders as 0 with unit") {
      assertTrue(CssLength(Double.NaN, "px").render == "0px")
    },
    test("Infinity renders as 0 with unit") {
      assertTrue(CssLength(Double.PositiveInfinity, "em").render == "0em")
    }
  )
}
