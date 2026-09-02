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

package zio.blocks.schema

import zio.blocks.schema.{NameMapper, SchemaBaseSpec}
import zio.test._
import zio.test.Assertion._

object NameMapperSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("NameMapperSpec")(
    test("custom") {
      val mapper = NameMapper.Custom(_.toUpperCase)
      assert(mapper("o_o_1"))(equalTo("O_O_1")) &&
      assert(mapper("o-o-1"))(equalTo("O-O-1")) &&
      assert(mapper("ooOo1"))(equalTo("OOOO1")) &&
      assert(mapper("OoOo1"))(equalTo("OOOO1")) &&
      assert(mapper("OOOO1"))(equalTo("OOOO1"))
    },
    test("camelCase") {
      val mapper = NameMapper.CamelCase
      assert(mapper("oO"))(equalTo("oO")) &&
      assert(mapper("oOoo"))(equalTo("oOoo")) &&
      assert(mapper("oOoo1"))(equalTo("oOoo1")) &&
      assert(mapper("oOoo$"))(equalTo("oOoo$")) &&
      assert(mapper("OO"))(equalTo("oo")) &&
      assert(mapper("OOoo"))(equalTo("oOoo")) &&
      assert(mapper("OOoo1"))(equalTo("oOoo1")) &&
      assert(mapper("OOOooo"))(equalTo("ooOooo")) &&
      assert(mapper("o-o"))(equalTo("oO")) &&
      assert(mapper("o-ooo"))(equalTo("oOoo")) &&
      assert(mapper("o-ooo-1"))(equalTo("oOoo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("oOoo$")) &&
      assert(mapper("ooo-ooo"))(equalTo("oooOoo")) &&
      assert(mapper("o-o"))(equalTo("oO")) &&
      assert(mapper("o-ooo"))(equalTo("oOoo")) &&
      assert(mapper("o-ooo-1"))(equalTo("oOoo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("oOoo$")) &&
      assert(mapper("OOO-OOO"))(equalTo("oooOoo"))
    },
    test("PascalCase") {
      val mapper = NameMapper.PascalCase
      assert(mapper("oO"))(equalTo("OO")) &&
      assert(mapper("oOoo"))(equalTo("OOoo")) &&
      assert(mapper("oOoo1"))(equalTo("OOoo1")) &&
      assert(mapper("oOoo$"))(equalTo("OOoo$")) &&
      assert(mapper("OO"))(equalTo("Oo")) &&
      assert(mapper("OOoo"))(equalTo("OOoo")) &&
      assert(mapper("OOoo1"))(equalTo("OOoo1")) &&
      assert(mapper("OOOooo"))(equalTo("OoOooo")) &&
      assert(mapper("o-o"))(equalTo("OO")) &&
      assert(mapper("o-ooo"))(equalTo("OOoo")) &&
      assert(mapper("o-ooo-1"))(equalTo("OOoo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("OOoo$")) &&
      assert(mapper("ooo-ooo"))(equalTo("OooOoo")) &&
      assert(mapper("o-o"))(equalTo("OO")) &&
      assert(mapper("o-ooo"))(equalTo("OOoo")) &&
      assert(mapper("o-ooo-1"))(equalTo("OOoo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("OOoo$")) &&
      assert(mapper("OOO-OOO"))(equalTo("OooOoo"))
    },
    test("snake_case") {
      val mapper = NameMapper.SnakeCase
      assert(mapper("oO"))(equalTo("o_o")) &&
      assert(mapper("oOoo"))(equalTo("o_ooo")) &&
      assert(mapper("oOoo1"))(equalTo("o_ooo1")) &&
      assert(mapper("oOoo$"))(equalTo("o_ooo$")) &&
      assert(mapper("OO"))(equalTo("oo")) &&
      assert(mapper("OOoo"))(equalTo("o_ooo")) &&
      assert(mapper("OOoo1"))(equalTo("o_ooo1")) &&
      assert(mapper("OOOooo"))(equalTo("oo_oooo")) &&
      assert(mapper("o-o"))(equalTo("o_o")) &&
      assert(mapper("o-ooo"))(equalTo("o_ooo")) &&
      assert(mapper("o-ooo-1"))(equalTo("o_ooo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("o_ooo$")) &&
      assert(mapper("ooo-ooo"))(equalTo("ooo_ooo")) &&
      assert(mapper("o-o"))(equalTo("o_o")) &&
      assert(mapper("o-ooo"))(equalTo("o_ooo")) &&
      assert(mapper("o-ooo-1"))(equalTo("o_ooo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("o_ooo$")) &&
      assert(mapper("OOO-OOO"))(equalTo("ooo_ooo"))
    },
    test("kebab-case") {
      val mapper = NameMapper.KebabCase
      assert(mapper("oO"))(equalTo("o-o")) &&
      assert(mapper("oOoo"))(equalTo("o-ooo")) &&
      assert(mapper("oOoo1"))(equalTo("o-ooo1")) &&
      assert(mapper("oOoo$"))(equalTo("o-ooo$")) &&
      assert(mapper("OO"))(equalTo("oo")) &&
      assert(mapper("OOoo"))(equalTo("o-ooo")) &&
      assert(mapper("OOoo1"))(equalTo("o-ooo1")) &&
      assert(mapper("OOOooo"))(equalTo("oo-oooo")) &&
      assert(mapper("o-o"))(equalTo("o-o")) &&
      assert(mapper("o-ooo"))(equalTo("o-ooo")) &&
      assert(mapper("o-ooo-1"))(equalTo("o-ooo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("o-ooo$")) &&
      assert(mapper("ooo-ooo"))(equalTo("ooo-ooo")) &&
      assert(mapper("o-o"))(equalTo("o-o")) &&
      assert(mapper("o-ooo"))(equalTo("o-ooo")) &&
      assert(mapper("o-ooo-1"))(equalTo("o-ooo1")) &&
      assert(mapper("o-ooo-$"))(equalTo("o-ooo$")) &&
      assert(mapper("OOO-OOO"))(equalTo("ooo-ooo"))
    }
  )
}
