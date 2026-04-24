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

object ToAttrValueSpec extends ZIOSpecDefault {
  def spec = suite("ToAttrValue")(
    test("String is HTML-escaped") {
      assertTrue(ToAttrValue[String].toAttrValue("a&b") == "a&amp;b")
    },
    test("Int renders as string") {
      assertTrue(ToAttrValue[Int].toAttrValue(42) == "42")
    },
    test("Boolean renders as string") {
      assertTrue(ToAttrValue[Boolean].toAttrValue(true) == "true")
    },
    test("Js is HTML-escaped") {
      assertTrue(ToAttrValue[Js].toAttrValue(Js("fn()")) == "fn()")
    },
    test("Js with HTML special chars is escaped") {
      assertTrue(ToAttrValue[Js].toAttrValue(Js("""alert("xss")""")) == "alert(&quot;xss&quot;)")
    },
    test("Css is HTML-escaped") {
      assertTrue(ToAttrValue[Css].toAttrValue(Css("color: red")) == "color: red")
    },
    test("Long renders as string") {
      assertTrue(ToAttrValue[Long].toAttrValue(100L) == "100")
    },
    test("Double renders as string") {
      assertTrue(ToAttrValue[Double].toAttrValue(3.14) == "3.14")
    },
    test("Char is HTML-escaped") {
      assertTrue(ToAttrValue[Char].toAttrValue('<') == "&lt;")
    },
    test("Css with special chars is HTML-escaped") {
      assertTrue(ToAttrValue[Css].toAttrValue(Css("a&b")) == "a&amp;b")
    },
    test("Option[String] Some") {
      assertTrue(ToAttrValue[Option[String]].toAttrValue(Some("x")) == "x")
    },
    test("Option[String] None") {
      assertTrue(ToAttrValue[Option[String]].toAttrValue(None) == "")
    },
    test("Iterable[String]") {
      assertTrue(ToAttrValue[Iterable[String]].toAttrValue(List("a", "b")) == "a b")
    },
    test("Iterable[String] with escaping") {
      assertTrue(ToAttrValue[Iterable[String]].toAttrValue(List("a&b", "c<d")) == "a&amp;b c&lt;d")
    }
  )
}
