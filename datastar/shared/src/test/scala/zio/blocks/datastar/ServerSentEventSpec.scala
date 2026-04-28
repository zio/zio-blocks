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

import zio.test._

object ServerSentEventSpec extends ZIOSpecDefault {
  def spec = suite("ServerSentEvent")(
    test("Basic: renders event type and single-line data") {
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
    test("Multi-line data: splits on newlines and prefixes each with data:") {
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
    test("Empty data: renders event type and empty data line") {
      val event = ServerSentEvent("", "test")
      assertTrue(event.render == "event: test\ndata: \n\n")
    }
  )
}
