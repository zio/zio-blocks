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
import zio.blocks.codegen.ir._

object ScalaEmitterGroupImportSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter GroupImport")(
      test("emits group import") {
        val imp    = Import.GroupImport("com.example", List("Foo", "Bar"))
        val result = ScalaEmitter.emitImport(imp)
        assertTrue(result == "import com.example.{Foo, Bar}")
      },
      test("single name group import") {
        val imp    = Import.GroupImport("com.example", List("Foo"))
        val result = ScalaEmitter.emitImport(imp)
        assertTrue(result == "import com.example.{Foo}")
      },
      test("group import sort key uses first name") {
        val imports = List(
          Import.GroupImport("com.example", List("Zebra", "Alpha")),
          Import.SingleImport("com.example", "Apple")
        )
        val result = ScalaEmitter.organizeImports(imports, EmitterConfig.default)
        assertTrue(result.head == Import.SingleImport("com.example", "Apple"))
      },
      test("group import deduplication") {
        val imports = List(
          Import.GroupImport("com.example", List("Foo", "Bar")),
          Import.GroupImport("com.example", List("Foo", "Bar"))
        )
        val result = ScalaEmitter.organizeImports(imports, EmitterConfig.default)
        assertTrue(result.length == 1)
      }
    )
}
