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

package zio.http

import zio.blocks.chunk.Chunk
import zio.test._

object ServerSentEventSpec extends ZIOSpecDefault {
  private final case class Payload(value: String)

  private implicit val payloadEncoder: SseDataEncoder[Payload] =
    new SseDataEncoder[Payload] {
      def lines(value: Payload): Chunk[String] = Chunk.single(s"payload:${value.value}")
    }

  def spec = suite("ServerSentEvent")(
    test("Basic: renders only data when event is absent") {
      val event = ServerSentEvent("hello")
      assertTrue(event.render == "data: hello\n\n")
    },
    test("With event: renders event and single-line data") {
      val event = ServerSentEvent("hello", "test")
      assertTrue(event.render == "event: test\ndata: hello\n\n")
    },
    test("With id: renders event type, id, and data") {
      val event = ServerSentEvent("hello", "test").withId("1")
      assertTrue(event.render == "event: test\nid: 1\ndata: hello\n\n")
    },
    test("With retry: renders event type, retry, and data") {
      val event = ServerSentEvent("hello", "test").withRetry(5000)
      assertTrue(event.render == "event: test\nretry: 5000\ndata: hello\n\n")
    },
    test("Multi-line string data: splits on newlines and prefixes each with data:") {
      val event = ServerSentEvent("line1\nline2", "test")
      assertTrue(event.render == "event: test\ndata: line1\ndata: line2\n\n")
    },
    test("CRLF data: strips carriage returns and splits each line once") {
      val event = ServerSentEvent("line1\r\nline2", "test")
      assertTrue(event.render == "event: test\ndata: line1\ndata: line2\n\n")
    },
    test("CR-only data: treats carriage returns as line breaks") {
      val event = ServerSentEvent("line1\rline2", "test")
      assertTrue(event.render == "event: test\ndata: line1\ndata: line2\n\n")
    },
    test("All options: renders event type, id, retry, and data") {
      val event = ServerSentEvent("data", "evt").withId("42").withRetry(3000)
      assertTrue(event.render == "event: evt\nid: 42\nretry: 3000\ndata: data\n\n")
    },
    test("fromOptions supports Option metadata") {
      val event = ServerSentEvent.fromOptions("data", event = Some("evt"), id = Some("42"), retry = Some(3000))
      assertTrue(event.render == "event: evt\nid: 42\nretry: 3000\ndata: data\n\n")
    },
    test("Empty string data: renders event type and empty data line") {
      val event = ServerSentEvent("", "test")
      assertTrue(event.render == "event: test\ndata: \n\n")
    },
    test("Chunk payload writes one data line per chunk entry") {
      val event = ServerSentEvent(Chunk("line1", "line2"), "test")
      assertTrue(event.render == "event: test\ndata: line1\ndata: line2\n\n")
    },
    test("Custom payload uses SseDataEncoder") {
      val event = ServerSentEvent(Payload("x"), "test")
      assertTrue(event.render == "event: test\ndata: payload:x\n\n")
    },
    test("event with newline is rejected") {
      val result = scala.util.Try(ServerSentEvent("data", "bad\nname"))
      assertTrue(result.isFailure)
    },
    test("id with carriage return is rejected") {
      val result = scala.util.Try(ServerSentEvent("data", "evt").withId("bad\rid"))
      assertTrue(result.isFailure)
    }
  )
}
