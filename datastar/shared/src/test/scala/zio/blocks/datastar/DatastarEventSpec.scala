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

import zio.blocks.chunk.Chunk
import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.test._

object DatastarEventSpec extends ZIOSpecDefault {
  def spec = suite("DatastarEvent")(
    suite("PatchElements")(
      test("basic: just Dom with default mode (outer)") {
        val dom = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("foo"))),
          Chunk(Dom.Text("Hello"))
        )
        val result = DatastarEvent.patchElements(dom).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "data: elements <div id=\"foo\">Hello</div>\n" +
            "\n"
        )
      },
      test("with selector and non-default mode") {
        val dom = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("foo"))),
          Chunk(Dom.Text("Hello"))
        )
        val result = DatastarEvent
          .patchElements(dom)
          .withSelector(CssSelector.id("foo"))
          .withMode(ElementPatchMode.Inner)
          .renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "data: selector #foo\n" +
            "data: mode inner\n" +
            "data: elements <div id=\"foo\">Hello</div>\n" +
            "\n"
        )
      },
      test("with all options: viewTransition, namespace, eventId, retry") {
        val dom    = Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("Hi")))
        val result = DatastarEvent
          .patchElements(dom)
          .withSelector(CssSelector.`class`("container"))
          .withMode(ElementPatchMode.Prepend)
          .withViewTransition
          .withNamespace("custom")
          .withEventId("evt-1")
          .withRetry(5000L)
          .renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "id: evt-1\n" +
            "retry: 5000\n" +
            "data: selector .container\n" +
            "data: mode prepend\n" +
            "data: useViewTransition true\n" +
            "data: namespace custom\n" +
            "data: elements <span>Hi</span>\n" +
            "\n"
        )
      },
      test("multi-line HTML gets separate data: elements lines") {
        val dom = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("line1"))),
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("line2")))
          )
        )
        val rendered = dom.renderMinified
        assertTrue(rendered == "<div><p>line1</p><p>line2</p></div>") &&
        assertTrue(
          DatastarEvent.patchElements(dom).renderSSE ==
            "event: datastar-patch-elements\n" +
            "data: elements <div><p>line1</p><p>line2</p></div>\n" +
            "\n"
        )
      },
      test("default mode (outer) is omitted from output") {
        val dom    = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = DatastarEvent.patchElements(dom).withMode(ElementPatchMode.Outer).renderSSE
        assertTrue(!result.contains("data: mode"))
      },
      test("eventId only adds id field") {
        val dom    = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = DatastarEvent.patchElements(dom).withEventId("abc").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "id: abc\n" +
            "data: elements <div></div>\n" +
            "\n"
        )
      },
      test("retry only adds retry field") {
        val dom    = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = DatastarEvent.patchElements(dom).withRetry(1000L).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "retry: 1000\n" +
            "data: elements <div></div>\n" +
            "\n"
        )
      }
    ),
    suite("PatchSignals")(
      test("single SignalUpdate") {
        val count  = Signal[Int]("count")
        val update = count := 42
        val result = DatastarEvent.patchSignals(update).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"count\":42}\n" +
            "\n"
        )
      },
      test("multiple SignalUpdates merged into single JSON") {
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
      test("with onlyIfMissing") {
        val count  = Signal[Int]("count")
        val result = DatastarEvent.patchSignals(count := 0).withOnlyIfMissing.renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: onlyIfMissing true\n" +
            "data: signals {\"count\":0}\n" +
            "\n"
        )
      },
      test("with eventId and retry") {
        val count  = Signal[Int]("count")
        val result = DatastarEvent.patchSignals(count := 1).withEventId("sig-1").withRetry(3000L).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "id: sig-1\n" +
            "retry: 3000\n" +
            "data: onlyIfMissing false\n" +
            "data: signals {\"count\":1}\n" +
            "\n"
        ) ||
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "id: sig-1\n" +
            "retry: 3000\n" +
            "data: signals {\"count\":1}\n" +
            "\n"
        )
      },
      test("raw JSON signals") {
        val result = DatastarEvent.patchSignalsRaw("""{"count":42,"active":true}""").renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: signals {\"count\":42,\"active\":true}\n" +
            "\n"
        )
      },
      test("raw JSON signals with onlyIfMissing") {
        val result = DatastarEvent.patchSignalsRaw("""{"x":1}""").withOnlyIfMissing.renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "data: onlyIfMissing true\n" +
            "data: signals {\"x\":1}\n" +
            "\n"
        )
      }
    ),
    suite("removeElements")(
      test("creates patchElements with Remove mode and selector") {
        val result = DatastarEvent.removeElements(CssSelector.id("old")).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "data: selector #old\n" +
            "data: mode remove\n" +
            "data: elements \n" +
            "\n"
        )
      }
    ),
    suite("executeScript")(
      test("creates patchElements with script element, body selector, append mode, and data-effect") {
        val code   = Js("console.log('hello')")
        val result = DatastarEvent.executeScript(code).renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "data: selector body\n" +
            "data: mode append\n" +
            "data: elements <script data-effect=\"el.remove()\">console.log('hello')</script>\n" +
            "\n"
        )
      }
    )
  )
}
