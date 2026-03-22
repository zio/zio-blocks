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

package zio.blocks.codegen.emit

import zio.test._

object EmitterConfigSpec extends ZIOSpecDefault {
  def spec = suite("EmitterConfig")(
    test("default config has indentWidth=2") {
      assertTrue(EmitterConfig.default.indentWidth == 2)
    },
    test("default config has sortImports=true") {
      assertTrue(EmitterConfig.default.sortImports == true)
    },
    test("default config has scala3Syntax=true") {
      assertTrue(EmitterConfig.default.scala3Syntax == true)
    },
    test("default config has trailingCommas=true") {
      assertTrue(EmitterConfig.default.trailingCommas == true)
    },
    test("EmitterConfig() equals EmitterConfig.default") {
      assertTrue(EmitterConfig() == EmitterConfig.default)
    },
    test("EmitterConfig.scala2 has scala3Syntax=false") {
      assertTrue(EmitterConfig.scala2.scala3Syntax == false)
    },
    test("EmitterConfig.scala2 has trailingCommas=false") {
      assertTrue(EmitterConfig.scala2.trailingCommas == false)
    },
    test("EmitterConfig.scala2 inherits other defaults") {
      val cfg = EmitterConfig.scala2
      assertTrue(
        cfg.indentWidth == 2 && cfg.sortImports == true
      )
    },
    test("custom config with indentWidth=4") {
      val cfg = EmitterConfig(indentWidth = 4)
      assertTrue(cfg.indentWidth == 4 && cfg.sortImports == true && cfg.scala3Syntax == true)
    },
    test("custom config with sortImports=false") {
      val cfg = EmitterConfig(sortImports = false)
      assertTrue(cfg.indentWidth == 2 && cfg.sortImports == false)
    },
    test("multiple field overrides work") {
      val cfg = EmitterConfig(
        indentWidth = 4,
        sortImports = false,
        scala3Syntax = false,
        trailingCommas = false
      )
      assertTrue(
        cfg.indentWidth == 4 &&
          cfg.sortImports == false &&
          cfg.scala3Syntax == false &&
          cfg.trailingCommas == false
      )
    }
  )
}
