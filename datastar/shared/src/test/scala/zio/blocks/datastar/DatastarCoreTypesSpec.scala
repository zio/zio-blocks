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

package zio.blocks.datastar

import zio.blocks.html.ToJs
import zio.test._

object DatastarCoreTypesSpec extends ZIOSpecDefault {
  def spec = suite("DatastarCoreTypes")(
    suite("Signal")(
      test("name returns the signal name") {
        val count = Signal[Int]("count")
        assertTrue(count.name == "count")
      },
      test("ref returns dollar-prefixed name") {
        val count = Signal[Int]("count")
        assertTrue(count.ref == "$count")
      },
      test("ToJs[Signal[Int]] produces raw dollar-prefixed name") {
        val count = Signal[Int]("count")
        assertTrue(ToJs[Signal[Int]].toJs(count) == "$count")
      },
      test("ToJs[Signal[String]] produces raw dollar-prefixed name") {
        val query = Signal[String]("query")
        assertTrue(ToJs[Signal[String]].toJs(query) == "$query")
      },
      test(":= creates SignalUpdate with correct name") {
        import zio.blocks.schema.Schema
        val count  = Signal[Int]("count")
        val update = count := 42
        assertTrue(update.name == "count")
      },
      test(":= creates SignalUpdate with correct serialized value for Int") {
        import zio.blocks.schema.Schema
        val count  = Signal[Int]("count")
        val update = count := 42
        assertTrue(update.serialized == "42")
      },
      test(":= creates SignalUpdate with correct serialized value for String") {
        import zio.blocks.schema.Schema
        val query  = Signal[String]("query")
        val update = query := "hello"
        assertTrue(update.serialized == "\"hello\"")
      },
      test(":= with wrong type does not compile") {
        typeCheck("""
          import zio.blocks.schema.Schema
          Signal[Int]("count") := "hello"
        """).map(result => assertTrue(result.isLeft))
      }
    ),
    suite("SignalUpdate")(
      test("ToJs[SignalUpdate[Int]] produces Datastar expression format") {
        import zio.blocks.schema.Schema
        val count  = Signal[Int]("count")
        val update = count := 42
        assertTrue(ToJs[SignalUpdate[Int]].toJs(update) == "{count: 42}")
      },
      test("ToJs[SignalUpdate[String]] produces Datastar expression format with serialized value") {
        import zio.blocks.schema.Schema
        val query  = Signal[String]("query")
        val update = query := "hello"
        assertTrue(ToJs[SignalUpdate[String]].toJs(update) == "{query: \"hello\"}")
      }
    ),
    suite("CaseModifier")(
      test("Camel renders empty string") {
        assertTrue(CaseModifier.Camel.render == "")
      },
      test("Kebab renders __case.kebab") {
        assertTrue(CaseModifier.Kebab.render == "__case.kebab")
      },
      test("Snake renders __case.snake") {
        assertTrue(CaseModifier.Snake.render == "__case.snake")
      },
      test("Pascal renders __case.pascal") {
        assertTrue(CaseModifier.Pascal.render == "__case.pascal")
      },
      test("suffix returns empty when same as default") {
        assertTrue(CaseModifier.Camel.suffix(CaseModifier.Camel) == "")
      },
      test("suffix returns render when different from default") {
        assertTrue(CaseModifier.Snake.suffix(CaseModifier.Camel) == "__case.snake")
      },
      test("suffix for Kebab with Kebab default returns empty") {
        assertTrue(CaseModifier.Kebab.suffix(CaseModifier.Kebab) == "")
      },
      test("suffix for Pascal with Camel default returns render") {
        assertTrue(CaseModifier.Pascal.suffix(CaseModifier.Camel) == "__case.pascal")
      }
    ),
    suite("ElementPatchMode")(
      test("Outer renders outer") {
        assertTrue(ElementPatchMode.Outer.render == "outer")
      },
      test("Inner renders inner") {
        assertTrue(ElementPatchMode.Inner.render == "inner")
      },
      test("Replace renders replace") {
        assertTrue(ElementPatchMode.Replace.render == "replace")
      },
      test("Prepend renders prepend") {
        assertTrue(ElementPatchMode.Prepend.render == "prepend")
      },
      test("Append renders append") {
        assertTrue(ElementPatchMode.Append.render == "append")
      },
      test("Before renders before") {
        assertTrue(ElementPatchMode.Before.render == "before")
      },
      test("After renders after") {
        assertTrue(ElementPatchMode.After.render == "after")
      },
      test("Remove renders remove") {
        assertTrue(ElementPatchMode.Remove.render == "remove")
      }
    ),
    suite("EventType")(
      test("PatchElements renders datastar-patch-elements") {
        assertTrue(EventType.PatchElements.render == "datastar-patch-elements")
      },
      test("PatchSignals renders datastar-patch-signals") {
        assertTrue(EventType.PatchSignals.render == "datastar-patch-signals")
      }
    )
  )
}
