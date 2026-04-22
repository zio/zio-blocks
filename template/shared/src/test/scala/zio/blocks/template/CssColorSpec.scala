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

object CssColorSpec extends ZIOSpecDefault {
  def spec = suite("CssColor")(
    test("Hex renders with hash") {
      assertTrue(CssColor.Hex("ff0000").map(_.render) == Some("#ff0000"))
    },
    test("Hex validates format") {
      assertTrue(CssColor.Hex("invalid").isEmpty)
    },
    test("Rgb renders") {
      assertTrue(CssColor.Rgb(255, 0, 0).render == "rgb(255,0,0)")
    },
    test("Rgba renders") {
      assertTrue(CssColor.Rgba(255, 0, 0, 0.5).render == "rgba(255,0,0,0.5)")
    },
    test("Hsl renders with percent") {
      assertTrue(CssColor.Hsl(120, 50, 50).render == "hsl(120,50%,50%)")
    },
    test("Named validates against whitelist") {
      assertTrue(CssColor.Named("red").map(_.render) == Some("red"))
    },
    test("Named rejects invalid names") {
      assertTrue(CssColor.Named("notacolor").isEmpty)
    },
    test("Named unsafe allows any string") {
      assertTrue(CssColor.Named.unsafe("anything").render == "anything")
    },
    test("Hex with # prefix strips the hash") {
      assertTrue(CssColor.Hex("#00ff00").map(_.render) == Some("#00ff00"))
    }
  )
}
