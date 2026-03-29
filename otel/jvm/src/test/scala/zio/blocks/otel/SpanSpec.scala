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

package zio.blocks.otel

import zio.test._

object SpanSpec extends ZIOSpecDefault {

  def spec = suite("Span")(
    suite("SpanEvent")(
      test("stores name, timestamp, and attributes") {
        val attrs = Attributes.of(AttributeKey.string("key"), "value")
        val event = SpanEvent("test-event", 12345L, attrs)
        assertTrue(
          event.name == "test-event" &&
            event.timestampNanos == 12345L &&
            event.attributes.get(AttributeKey.string("key")).contains("value")
        )
      },
      test("supports empty attributes") {
        val event = SpanEvent("empty-event", 0L, Attributes.empty)
        assertTrue(event.attributes.isEmpty)
      }
    ),
    suite("SpanLink")(
      test("stores span context and attributes") {
        val ctx = {
          val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false)
        }
        val link = SpanLink(ctx, Attributes.empty)
        assertTrue(link.spanContext.isValid && link.attributes.isEmpty)
      }
    ),
    suite("SpanData")(
      test("is an immutable snapshot") {
        val ctx = {
          val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false)
        }
        val spanData = SpanData(
          name = "test-span",
          kind = SpanKind.Internal,
          spanContext = ctx,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 100L,
          endTimeNanos = 200L,
          attributes = Attributes.empty,
          events = List.empty,
          links = List.empty,
          status = SpanStatus.Unset,
          resource = Resource.empty,
          instrumentationScope = InstrumentationScope("test")
        )
        assertTrue(
          spanData.name == "test-span" &&
            spanData.kind == SpanKind.Internal &&
            spanData.startTimeNanos == 100L &&
            spanData.endTimeNanos == 200L &&
            spanData.parentSpanContext == SpanContext.invalid
        )
      }
    ),
    suite("RecordingSpan lifecycle")(
      test("start → setAttribute → addEvent → setStatus → end") {
        val span = SpanBuilder("lifecycle-span")
          .setKind(SpanKind.Server)
          .startSpan()

        val recording = span.isRecording
        val spanName  = span.name
        val spanKind  = span.kind

        span.setAttribute(AttributeKey.string("http.method"), "GET")
        span.addEvent("processing-started")
        span.setStatus(SpanStatus.Ok)
        span.end()

        val data = span.toSpanData
        assertTrue(
          recording &&
            spanName == "lifecycle-span" &&
            spanKind == SpanKind.Server &&
            !span.isRecording &&
            data.attributes.get(AttributeKey.string("http.method")).contains("GET") &&
            data.events.size == 1 &&
            data.events.head.name == "processing-started" &&
            data.status == SpanStatus.Ok &&
            data.endTimeNanos > 0L
        )
      },
      test("setAttribute convenience methods work") {
        val span = SpanBuilder("convenience-span").startSpan()

        span.setAttribute("str-key", "str-val")
        span.setAttribute("long-key", 42L)
        span.setAttribute("double-key", 3.14)
        span.setAttribute("bool-key", true)
        span.end()

        val data = span.toSpanData
        assertTrue(
          data.attributes.get(AttributeKey.string("str-key")).contains("str-val") &&
            data.attributes.get(AttributeKey.long("long-key")).contains(42L) &&
            data.attributes.get(AttributeKey.double("double-key")).contains(3.14) &&
            data.attributes.get(AttributeKey.boolean("bool-key")).contains(true)
        )
      },
      test("addEvent with attributes") {
        val span  = SpanBuilder("event-span").startSpan()
        val attrs = Attributes.of(AttributeKey.string("detail"), "info")
        span.addEvent("my-event", attrs)
        span.end()
        val data = span.toSpanData
        assertTrue(
          data.events.size == 1 &&
            data.events.head.name == "my-event" &&
            data.events.head.attributes.get(AttributeKey.string("detail")).contains("info")
        )
      },
      test("addEvent with explicit timestamp") {
        val span = SpanBuilder("ts-event-span").startSpan()
        span.addEvent("timed-event", 99999L, Attributes.empty)
        span.end()
        val data = span.toSpanData
        assertTrue(
          data.events.head.timestampNanos == 99999L
        )
      },
      test("end with explicit timestamp") {
        val span = SpanBuilder("explicit-end").startSpan()
        span.end(555555L)
        val data = span.toSpanData
        assertTrue(data.endTimeNanos == 555555L)
      },
      test("end is idempotent — second end is ignored") {
        val span = SpanBuilder("idempotent-end").startSpan()
        span.end(100L)
        span.end(200L)
        val data = span.toSpanData
        assertTrue(data.endTimeNanos == 100L)
      },
      test("setAttribute after end is ignored") {
        val span = SpanBuilder("post-end").startSpan()
        span.setAttribute("before", "yes")
        span.end()
        span.setAttribute("after", "no")
        val data = span.toSpanData
        assertTrue(
          data.attributes.get(AttributeKey.string("before")).contains("yes") &&
            data.attributes.get(AttributeKey.string("after")).isEmpty
        )
      },
      test("spanContext is valid and has correct trace ID from parent") {
        val parentCtx = {
          val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false)
        }
        val span = SpanBuilder("child-span")
          .setParent(parentCtx)
          .startSpan()
        span.end()
        assertTrue(
          span.spanContext.isValid &&
            span.spanContext.traceIdHi == parentCtx.traceIdHi && span.spanContext.traceIdLo == parentCtx.traceIdLo &&
            span.spanContext.spanId != parentCtx.spanId
        )
      },
      test("spanContext gets new trace ID when no parent") {
        val span = SpanBuilder("root-span").startSpan()
        span.end()
        assertTrue(span.spanContext.isValid && TraceId.isValid(span.spanContext.traceIdHi, span.spanContext.traceIdLo))
      },
      test("toSpanData captures parent span context") {
        val parentCtx = {
          val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false)
        }
        val span = SpanBuilder("child").setParent(parentCtx).startSpan()
        span.end()
        val data = span.toSpanData
        assertTrue(data.parentSpanContext == parentCtx)
      },
      test("toSpanData uses actual resource and instrumentationScope") {
        val customResource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "test-svc")
        )
        val customScope = InstrumentationScope("my-lib", Some("1.0"))
        val span        = SpanBuilder("resource-scope-span")
          .setResource(customResource)
          .setInstrumentationScope(customScope)
          .startSpan()
        span.end()
        val data = span.toSpanData
        assertTrue(
          data.resource == customResource &&
            data.instrumentationScope == customScope
        )
      }
    ),
    suite("Span.NoOp")(
      test("spanContext returns invalid") {
        assertTrue(Span.NoOp.spanContext == SpanContext.invalid)
      },
      test("isRecording returns false") {
        assertTrue(!Span.NoOp.isRecording)
      },
      test("all mutating methods are no-ops") {
        Span.NoOp.setAttribute(AttributeKey.string("k"), "v")
        Span.NoOp.setAttribute("k", "v")
        Span.NoOp.setAttribute("k", 1L)
        Span.NoOp.setAttribute("k", 1.0)
        Span.NoOp.setAttribute("k", true)
        Span.NoOp.addEvent("e")
        Span.NoOp.addEvent("e", Attributes.empty)
        Span.NoOp.addEvent("e", 0L, Attributes.empty)
        Span.NoOp.setStatus(SpanStatus.Ok)
        Span.NoOp.end()
        Span.NoOp.end(0L)
        assertTrue(true)
      },
      test("toSpanData returns empty snapshot") {
        val data = Span.NoOp.toSpanData
        assertTrue(
          data.name == "" &&
            data.spanContext == SpanContext.invalid &&
            data.attributes.isEmpty &&
            data.events.isEmpty &&
            data.links.isEmpty
        )
      },
      test("name returns empty string") {
        assertTrue(Span.NoOp.name == "")
      },
      test("kind returns Internal") {
        val k: SpanKind = Span.NoOp.kind
        assertTrue(k == SpanKind.Internal)
      }
    ),
    suite("SpanBuilder")(
      test("fluent API builds a span") {
        val parentCtx = {
          val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false)
        }
        val link = SpanLink(
          { val (h, l) = TraceId.random(); SpanContext.create(h, l, SpanId.random, TraceFlags.sampled, "", false) },
          Attributes.empty
        )
        val span = SpanBuilder("built-span")
          .setKind(SpanKind.Client)
          .setParent(parentCtx)
          .setAttribute(AttributeKey.string("builder-attr"), "val")
          .addLink(link)
          .startSpan()

        span.end()
        val data = span.toSpanData
        assertTrue(
          data.name == "built-span" &&
            data.kind == SpanKind.Client &&
            data.parentSpanContext == parentCtx &&
            data.spanContext.traceIdHi == parentCtx.traceIdHi && data.spanContext.traceIdLo == parentCtx.traceIdLo &&
            data.attributes.get(AttributeKey.string("builder-attr")).contains("val") &&
            data.links.size == 1
        )
      },
      test("setStartTimestamp overrides default start time") {
        val span = SpanBuilder("ts-span")
          .setStartTimestamp(42L)
          .startSpan()
        span.end()
        val data = span.toSpanData
        assertTrue(data.startTimeNanos == 42L)
      }
    ),
    suite("thread safety")(
      test("concurrent setAttribute does not lose updates") {
        import java.util.concurrent.{CountDownLatch, Executors}
        val span      = SpanBuilder("concurrent-span").startSpan()
        val nThreads  = 10
        val perThread = 10
        val executor  = Executors.newFixedThreadPool(nThreads)
        val latch     = new CountDownLatch(nThreads)

        (0 until nThreads).foreach { t =>
          executor.submit(new Runnable {
            def run(): Unit = {
              (0 until perThread).foreach { i =>
                span.setAttribute(s"t$t-i$i", s"v$t-$i")
              }
              latch.countDown()
            }
          })
        }
        latch.await()
        executor.shutdown()
        span.end()

        val data = span.toSpanData
        assertTrue(data.attributes.size == nThreads * perThread)
      }
    )
  )
}
