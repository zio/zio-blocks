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
import zio.blocks.schema.Schema
import zio.test._

object SchemaIntegrationSpec extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  def spec = suite("SchemaIntegration")(
    suite("Multi-signal SSE event")(
      test("two signals merged into single JSON object") {
        val count  = Signal[Int]("count")
        val query  = Signal[String]("query")
        val result = DatastarEvent.patchSignals(count := 42, query := "hello").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"count\":42,\"query\":\"hello\"}\n" +
            "\n"
        )
      },
      test("three signals of different types") {
        val count   = Signal[Int]("count")
        val query   = Signal[String]("query")
        val visible = Signal[Boolean]("visible")
        val result  =
          DatastarEvent.patchSignals(count := 10, query := "search", visible := true).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"count\":10,\"query\":\"search\",\"visible\":true}\n" +
            "\n"
        )
      },
      test("single signal produces single-key JSON") {
        val count  = Signal[Int]("count")
        val result = DatastarEvent.patchSignals(count := 0).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"count\":0}\n" +
            "\n"
        )
      }
    ),
    suite("Unicode values")(
      test("emoji in signal value serializes correctly") {
        val emoji  = Signal[String]("emoji")
        val update = emoji := "\uD83D\uDE80"
        assertTrue(update.serialized == "\"\uD83D\uDE80\"")
      },
      test("emoji in SSE event renders correctly") {
        val emoji  = Signal[String]("emoji")
        val result = DatastarEvent.patchSignals(emoji := "\uD83D\uDE80").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"emoji\":\"\uD83D\uDE80\"}\n" +
            "\n"
        )
      },
      test("CJK characters in signal value") {
        val label  = Signal[String]("label")
        val update = label := "\u4F60\u597D"
        assertTrue(update.serialized == "\"\u4F60\u597D\"")
      },
      test("accented characters in signal value") {
        val name   = Signal[String]("name")
        val result = DatastarEvent.patchSignals(name := "caf\u00E9").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"name\":\"caf\u00E9\"}\n" +
            "\n"
        )
      }
    ),
    suite("Empty string value")(
      test("empty string serializes to empty JSON string") {
        val text   = Signal[String]("text")
        val update = text := ""
        assertTrue(update.serialized == "\"\"")
      },
      test("empty string in SSE event") {
        val text   = Signal[String]("text")
        val result = DatastarEvent.patchSignals(text := "").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"text\":\"\"}\n" +
            "\n"
        )
      }
    ),
    suite("Boolean signal")(
      test("true serializes to true") {
        val visible = Signal[Boolean]("visible")
        val update  = visible := true
        assertTrue(update.serialized == "true")
      },
      test("false serializes to false") {
        val visible = Signal[Boolean]("visible")
        val update  = visible := false
        assertTrue(update.serialized == "false")
      },
      test("boolean in SSE event") {
        val visible = Signal[Boolean]("visible")
        val result  = DatastarEvent.patchSignals(visible := true).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"visible\":true}\n" +
            "\n"
        )
      }
    ),
    suite("SignalUpdate ToJs integration")(
      test("ToJs[SignalUpdate[Int]] renders Datastar expression format") {
        val count  = Signal[Int]("count")
        val update = count := 42
        assertTrue(ToJs[SignalUpdate[Int]].toJs(update) == "{count: 42}")
      },
      test("ToJs[SignalUpdate[String]] renders Datastar expression format") {
        val query  = Signal[String]("query")
        val update = query := "hello"
        assertTrue(ToJs[SignalUpdate[String]].toJs(update) == "{query: \"hello\"}")
      },
      test("ToJs[SignalUpdate[Boolean]] renders Datastar expression format") {
        val visible = Signal[Boolean]("visible")
        val update  = visible := true
        assertTrue(ToJs[SignalUpdate[Boolean]].toJs(update) == "{visible: true}")
      }
    ),
    suite("Multiple signals with onlyIfMissing")(
      test("onlyIfMissing with two signals") {
        val count  = Signal[Int]("count")
        val query  = Signal[String]("query")
        val result =
          DatastarEvent.patchSignals(count := 0, query := "").withOnlyIfMissing.renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: onlyIfMissing true\n" +
            "data: signals {\"count\":0,\"query\":\"\"}\n" +
            "\n"
        )
      },
      test("onlyIfMissing false is omitted") {
        val count  = Signal[Int]("count")
        val result = DatastarEvent.patchSignals(count := 0).renderSSE
        assertTrue(!result.contains("onlyIfMissing"))
      }
    ),
    suite("Nested case class serialization")(
      test("case class with Schema.derived serializes to nested JSON") {
        val pos    = Signal[Point]("pos")
        val update = pos := Point(1, 2)
        assertTrue(update.name == "pos") &&
        assertTrue(update.serialized.contains("1")) &&
        assertTrue(update.serialized.contains("2"))
      },
      test("nested case class in SSE event") {
        val pos    = Signal[Point]("pos")
        val result = DatastarEvent.patchSignals(pos := Point(3, 4)).renderSSE
        assertTrue(result.contains("event: datastar-patch-signals")) &&
        assertTrue(result.contains("data: signals")) &&
        assertTrue(result.contains("\"pos\""))
      },
      test("nested case class ToJs renders expression format") {
        val pos    = Signal[Point]("pos")
        val update = pos := Point(5, 6)
        val jsExpr = ToJs[SignalUpdate[Point]].toJs(update)
        assertTrue(jsExpr.startsWith("{pos: ")) &&
        assertTrue(jsExpr.endsWith("}"))
      }
    ),
    suite("Special characters in string values")(
      test("string with quotes serializes correctly") {
        val msg    = Signal[String]("msg")
        val update = msg := "say \"hello\""
        assertTrue(update.serialized.contains("\\\"hello\\\""))
      },
      test("string with backslash serializes correctly") {
        val path   = Signal[String]("path")
        val update = path := "C:\\Users"
        assertTrue(update.serialized.contains("\\\\"))
      },
      test("string with newline serializes correctly") {
        val text   = Signal[String]("text")
        val update = text := "line1\nline2"
        assertTrue(update.serialized.contains("\\n"))
      }
    ),
    suite("Numeric edge cases")(
      test("zero serializes correctly") {
        val n      = Signal[Int]("n")
        val update = n := 0
        assertTrue(update.serialized == "0")
      },
      test("negative number serializes correctly") {
        val n      = Signal[Int]("n")
        val update = n := -42
        assertTrue(update.serialized == "-42")
      },
      test("large number serializes correctly") {
        val n      = Signal[Long]("n")
        val update = n := 9999999999L
        assertTrue(update.serialized == "9999999999")
      }
    )
  )
}
