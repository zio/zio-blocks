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

package zio.blocks.template

import zio.test._
import CssLength.CssLengthIntOps

object CssInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("css interpolator")(
    test("static CSS string") {
      val result = css"color: red"
      assertTrue(result == Css.Raw("color: red"))
    },
    test("CSS with interpolated CssLength") {
      val result = css"margin: ${10.px}"
      assertTrue(result == Css.Raw("margin: 10px"))
    },
    test("CSS with interpolated Int") {
      val result = css"width: ${100}"
      assertTrue(result == Css.Raw("width: 100"))
    }
  )
}
