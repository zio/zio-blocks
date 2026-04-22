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

object ToTextSpec extends ZIOSpecDefault {
  def spec = suite("ToText")(
    test("String passthrough") {
      assertTrue(ToText[String].toText("hello") == "hello")
    },
    test("Int") {
      assertTrue(ToText[Int].toText(42) == "42")
    },
    test("Boolean") {
      assertTrue(ToText[Boolean].toText(true) == "true")
    },
    test("Char") {
      assertTrue(ToText[Char].toText('x') == "x")
    },
    test("BigInt") {
      assertTrue(ToText[BigInt].toText(BigInt(999)) == "999")
    },
    test("BigDecimal") {
      assertTrue(ToText[BigDecimal].toText(BigDecimal("3.14")) == "3.14")
    },
    test("Long") {
      assertTrue(ToText[Long].toText(100L) == "100")
    },
    test("Double") {
      assertTrue(ToText[Double].toText(3.14) == "3.14")
    },
    test("Float") {
      assertTrue(ToText[Float].toText(1.5f) == "1.5")
    },
    test("Byte") {
      assertTrue(ToText[Byte].toText(42.toByte) == "42")
    },
    test("Short") {
      assertTrue(ToText[Short].toText(100.toShort) == "100")
    }
  )
}
