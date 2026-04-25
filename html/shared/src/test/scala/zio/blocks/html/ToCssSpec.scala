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

object ToCssSpec extends ZIOSpecDefault {
  def spec = suite("ToCss")(
    test("String is CSS-escaped") {
      assertTrue(ToCss[String].toCss("red") == "red")
    },
    test("String with special chars") {
      assertTrue(ToCss[String].toCss("a\\b") == "a\\\\b")
    },
    test("Int renders as string") {
      assertTrue(ToCss[Int].toCss(10) == "10")
    },
    test("Double renders as string") {
      assertTrue(ToCss[Double].toCss(1.5) == "1.5")
    },
    test("Css passthrough renders") {
      assertTrue(ToCss[Css].toCss(Css("color: red")) == "color: red")
    },
    test("Option[String] Some") {
      assertTrue(ToCss[Option[String]].toCss(Some("blue")) == "blue")
    },
    test("Option[String] None") {
      assertTrue(ToCss[Option[String]].toCss(None) == "")
    },
    test("CssLength") {
      assertTrue(ToCss[CssLength].toCss(CssLength(10, "px")) == "10px")
    },
    test("CssColor Rgb") {
      assertTrue(ToCss[CssColor].toCss(CssColor.Rgb(255, 0, 0)) == "rgb(255,0,0)")
    },
    test("Long ToCss") {
      assertTrue(ToCss[Long].toCss(100L) == "100")
    },
    test("Float ToCss") {
      assertTrue(ToCss[Float].toCss(1.5f) == "1.5")
    },
    test("Option[CssLength] Some") {
      assertTrue(ToCss[Option[CssLength]].toCss(Some(CssLength(10, "px"))) == "10px")
    },
    test("Option[CssLength] None") {
      assertTrue(ToCss[Option[CssLength]].toCss(None) == "")
    }
  )
}
