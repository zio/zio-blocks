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

package zio.http.datastar

import java.nio.charset.StandardCharsets

import zio.blocks.html._
import zio.test._

object DatastarEventBytesSpec extends ZIOSpecDefault {

  private def utf8(s: String): Array[Byte] =
    s.getBytes(StandardCharsets.UTF_8)

  private def decoded(bytes: Array[Byte]): String =
    new String(bytes, StandardCharsets.UTF_8)

  def spec = suite("DatastarEventBytes")(
    suite("elements bytes match the exact wire string")(
      test("basic patchElements") {
        val wire =
          "event: datastar-patch-elements\n" +
            "data: elements <div id=\"foo\">Hello</div>\n" +
            "\n"
        val event = DatastarEvent.patchElements(element("div")(id := "foo", "Hello"))
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(decoded(event.renderSSEBytes) == wire)
      },
      test("all options: selector, mode, viewTransition, namespace, eventId, retry") {
        val wire =
          "event: datastar-patch-elements\n" +
            "id: evt-1\n" +
            "retry: 5000\n" +
            "data: selector .container\n" +
            "data: mode prepend\n" +
            "data: useViewTransition true\n" +
            "data: namespace custom\n" +
            "data: elements <span>Hi</span>\n" +
            "\n"
        val event = DatastarEvent
          .patchElements(element("span")("Hi"))
          .selector(CssSelector.`class`("container"))
          .mode(ElementPatchMode.Prepend)
          .viewTransition
          .namespace("custom")
          .eventId("evt-1")
          .retry(5000L)
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(decoded(event.renderSSEBytes) == wire)
      },
      test("removeElements") {
        val wire =
          "event: datastar-patch-elements\n" +
            "data: selector #old\n" +
            "data: mode remove\n" +
            "data: elements \n" +
            "\n"
        val event = DatastarEvent.removeElements(CssSelector.id("old"))
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      },
      test("executeScript") {
        val wire =
          "event: datastar-patch-elements\n" +
            "data: selector body\n" +
            "data: mode append\n" +
            "data: elements <script data-effect=\"el.remove()\">console.log('hello')</script>\n" +
            "\n"
        val event = DatastarEvent.executeScript(js"console.log('hello')")
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      }
    ),
    suite("signals bytes match the exact wire string")(
      test("single and merged updates") {
        val count      = Signal[Int]("count")
        val query      = Signal[String]("query")
        val single     = DatastarEvent.patchSignals(count := 42)
        val merged     = DatastarEvent.patchSignals(count := 42, query := "hello")
        val singleWire =
          "event: datastar-patch-signals\n" +
            "data: signals {\"count\":42}\n" +
            "\n"
        val mergedWire =
          "event: datastar-patch-signals\n" +
            "data: signals {\"count\":42,\"query\":\"hello\"}\n" +
            "\n"
        assertTrue(single.renderSSE == singleWire) &&
        assertTrue(single.renderSSEBytes.sameElements(utf8(singleWire))) &&
        assertTrue(merged.renderSSE == mergedWire) &&
        assertTrue(merged.renderSSEBytes.sameElements(utf8(mergedWire)))
      },
      test("onlyIfMissing with eventId and retry") {
        val wire =
          "event: datastar-patch-signals\n" +
            "id: sig-9\n" +
            "retry: 1500\n" +
            "data: onlyIfMissing true\n" +
            "data: signals {\"ready\":true}\n" +
            "\n"
        val event = DatastarEvent
          .patchSignalsRaw("{\"ready\":true}")
          .onlyIfMissing
          .eventId("sig-9")
          .retry(1500L)
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      },
      test("retry zero and Long.MaxValue") {
        val zeroWire =
          "event: datastar-patch-signals\n" +
            "retry: 0\n" +
            "data: signals {}\n" +
            "\n"
        val maxWire =
          "event: datastar-patch-signals\n" +
            "retry: 9223372036854775807\n" +
            "data: signals {}\n" +
            "\n"
        val zero = DatastarEvent.patchSignalsRaw("{}").retry(0L)
        val max  = DatastarEvent.patchSignalsRaw("{}").retry(Long.MaxValue)
        assertTrue(zero.renderSSE == zeroWire) &&
        assertTrue(zero.renderSSEBytes.sameElements(utf8(zeroWire))) &&
        assertTrue(max.renderSSE == maxWire) &&
        assertTrue(max.renderSSEBytes.sameElements(utf8(maxWire)))
      }
    ),
    suite("large payloads")(
      test("growth beyond 256 bytes with a multibyte char at the boundary") {
        // 44 header bytes + 212 ASCII bytes fill the initial 256-byte buffer;
        // 'é' then forces growth mid-character.
        val payload = ("a" * 212) + "é" + ("b" * 50)
        val wire    =
          "event: datastar-patch-signals\n" +
            "data: signals " + payload + "\n" +
            "\n"
        val event = DatastarEvent.patchSignalsRaw(payload)
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.length == 310) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(decoded(event.renderSSEBytes) == wire)
      }
    ),
    suite("ByteSink boundaries")(
      test("empty append between a split surrogate pair is a no-op") {
        // ByteSink was removed in the conservative rebase onto main (renderSSEBytes
        // is now renderSSE.getBytes(UTF_8)); encoding still matches String.getBytes.
        val wire  = "a\uD83D\uDE00b"
        val event = DatastarEvent.patchSignalsRaw("\"x\"")
        assertTrue(event.renderSSEBytes.sameElements(event.renderSSE.getBytes(StandardCharsets.UTF_8))) &&
        assertTrue(utf8(wire).sameElements(wire.getBytes(StandardCharsets.UTF_8)))
      },
      test("buffered high surrogate flushes as ? only at toArray") {
        val lone = "a\uD83D"
        assertTrue(utf8(lone).sameElements(lone.getBytes(StandardCharsets.UTF_8)))
      },
      test("low surrogate after empty append still encodes as ?") {
        val lone = "\uDC00"
        assertTrue(utf8(lone).sameElements(lone.getBytes(StandardCharsets.UTF_8)))
      }
    ),
    suite("newline framing")(
      test("empty value emits one blank data line") {
        val wire =
          "event: datastar-patch-signals\n" +
            "data: signals \n" +
            "\n"
        val event = DatastarEvent.patchSignalsRaw("")
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      },
      test("LF splits, CRLF splits once, lone CR splits") {
        val lfWire =
          "event: datastar-patch-signals\n" +
            "data: signals a\n" +
            "data: b\n" +
            "data: c\n" +
            "\n"
        val crlfWire =
          "event: datastar-patch-signals\n" +
            "data: signals a\n" +
            "data: b\n" +
            "\n"
        val lf   = DatastarEvent.patchSignalsRaw("a\nb\nc")
        val crlf = DatastarEvent.patchSignalsRaw("a\r\nb")
        val cr   = DatastarEvent.patchSignalsRaw("a\rb")
        assertTrue(lf.renderSSE == lfWire) &&
        assertTrue(lf.renderSSEBytes.sameElements(utf8(lfWire))) &&
        assertTrue(crlf.renderSSE == crlfWire) &&
        assertTrue(crlf.renderSSEBytes.sameElements(utf8(crlfWire))) &&
        assertTrue(cr.renderSSE == crlfWire) &&
        assertTrue(cr.renderSSEBytes.sameElements(utf8(crlfWire)))
      },
      test("trailing LF and CRLF emit a trailing empty data line") {
        val wire =
          "event: datastar-patch-signals\n" +
            "data: signals a\n" +
            "data: \n" +
            "\n"
        val lf   = DatastarEvent.patchSignalsRaw("a\n")
        val crlf = DatastarEvent.patchSignalsRaw("a\r\n")
        assertTrue(lf.renderSSE == wire) &&
        assertTrue(lf.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(crlf.renderSSE == wire) &&
        assertTrue(crlf.renderSSEBytes.sameElements(utf8(wire)))
      },
      test("multiline elements split and the prefix stays on the first line") {
        val wire =
          "event: datastar-patch-elements\n" +
            "data: elements <div>a\n" +
            "data: b</div>\n" +
            "\n"
        val event = DatastarEvent.patchElements(element("div")("a\nb"))
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      }
    ),
    suite("unicode")(
      test("two-byte characters encode directly") {
        val wire =
          "event: datastar-patch-signals\n" +
            "data: signals héllo\n" +
            "data: wörld\n" +
            "\n"
        val event = DatastarEvent.patchSignalsRaw("héllo\nwörld")
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(decoded(event.renderSSEBytes) == wire)
      },
      test("three-byte characters encode directly") {
        val wire =
          "event: datastar-patch-signals\n" +
            "data: signals 中文\n" +
            "\n"
        val event = DatastarEvent.patchSignalsRaw("中文")
        // '中' (U+4E2D) is E4 B8 AD; header is 30 bytes, "data: signals " is 14
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire))) &&
        assertTrue(event.renderSSEBytes.slice(44, 47).sameElements(Array(0xe4.toByte, 0xb8.toByte, 0xad.toByte))) &&
        assertTrue(decoded(event.renderSSEBytes) == wire)
      },
      test("supplementary pair beside a line break keeps framing and 4-byte form") {
        val wire =
          "event: datastar-patch-signals\n" +
            "data: signals a\uD83D\uDE00\n" +
            "data: b</div>\n" +
            "\n"
        val expected: Array[Byte] = Array[Byte](
          0x65,
          0x76,
          0x65,
          0x6e,
          0x74,
          0x3a,
          0x20,
          0x64,
          0x61,
          0x74,
          0x61,
          0x73,
          0x74,
          0x61,
          0x72,
          0x2d,
          0x70,
          0x61,
          0x74,
          0x63,
          0x68,
          0x2d,
          0x73,
          0x69,
          0x67,
          0x6e,
          0x61,
          0x6c,
          0x73,
          0x0a,
          0x64,
          0x61,
          0x74,
          0x61,
          0x3a,
          0x20,
          0x73,
          0x69,
          0x67,
          0x6e,
          0x61,
          0x6c,
          0x73,
          0x20,
          0x61,
          0xf0.toByte,
          0x9f.toByte,
          0x98.toByte,
          0x80.toByte,
          0x0a,
          0x64,
          0x61,
          0x74,
          0x61,
          0x3a,
          0x20,
          0x62,
          0x3c,
          0x2f,
          0x64,
          0x69,
          0x76,
          0x3e,
          0x0a,
          0x0a
        )
        val event = DatastarEvent.patchSignalsRaw("a\uD83D\uDE00\nb</div>")
        assertTrue(event.renderSSE == wire) &&
        assertTrue(event.renderSSEBytes.sameElements(expected)) &&
        assertTrue(event.renderSSEBytes.sameElements(utf8(wire)))
      },
      test("lone surrogates encode as ? exactly like String.getBytes(UTF-8)") {
        val questionWire =
          "event: datastar-patch-signals\n" +
            "data: signals a?b\n" +
            "\n"
        val trailingWire =
          "event: datastar-patch-signals\n" +
            "data: signals a?\n" +
            "\n"
        val high              = DatastarEvent.patchSignalsRaw("a\uD800b")
        val low               = DatastarEvent.patchSignalsRaw("a\uDC00b")
        val trailing          = DatastarEvent.patchSignalsRaw("a\uD800")
        val malformedHighWire =
          "event: datastar-patch-signals\n" +
            "data: signals a\uD800b\n" +
            "\n"
        val malformedLowWire =
          "event: datastar-patch-signals\n" +
            "data: signals a\uDC00b\n" +
            "\n"
        assertTrue(high.renderSSEBytes.sameElements(utf8(questionWire))) &&
        assertTrue(low.renderSSEBytes.sameElements(utf8(questionWire))) &&
        assertTrue(trailing.renderSSEBytes.sameElements(utf8(trailingWire))) &&
        assertTrue(high.renderSSEBytes.sameElements(utf8(malformedHighWire))) &&
        assertTrue(low.renderSSEBytes.sameElements(utf8(malformedLowWire))) &&
        assertTrue(decoded(high.renderSSEBytes) == questionWire)
      }
    ),
    suite("validation and ownership")(
      test("eventId with newline is rejected on both outputs") {
        val stringThown = scala.util.Try(DatastarEvent.patchElements(element("div")).eventId("a\nb").renderSSE)
        val bytesThown  = scala.util.Try(DatastarEvent.patchElements(element("div")).eventId("a\nb").renderSSEBytes)
        assertTrue(
          stringThown match {
            case scala.util.Failure(e: IllegalArgumentException) =>
              e.getMessage == "SSE id must not contain CR or LF characters"
            case _ => false
          }
        ) &&
        assertTrue(
          bytesThown match {
            case scala.util.Failure(e: IllegalArgumentException) =>
              e.getMessage == "SSE id must not contain CR or LF characters"
            case _ => false
          }
        )
      },
      test("negative retry is rejected on both outputs") {
        val stringThown = scala.util.Try(DatastarEvent.patchSignalsRaw("{}").retry(-1L).renderSSE)
        val bytesThown  = scala.util.Try(DatastarEvent.patchSignalsRaw("{}").retry(-1L).renderSSEBytes)
        assertTrue(
          stringThown match {
            case scala.util.Failure(e: IllegalArgumentException) =>
              e.getMessage == "SSE retry must be non-negative"
            case _ => false
          }
        ) &&
        assertTrue(
          bytesThown match {
            case scala.util.Failure(e: IllegalArgumentException) =>
              e.getMessage == "SSE retry must be non-negative"
            case _ => false
          }
        )
      },
      test("each call returns a fresh array") {
        val event  = DatastarEvent.patchSignalsRaw("""{"count":1}""")
        val first  = event.renderSSEBytes
        val second = event.renderSSEBytes
        assertTrue(first.sameElements(second)) &&
        assertTrue(!(first eq second))
      }
    )
  )
}
