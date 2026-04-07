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

package zio.blocks.telemetry

import zio.test._

import scala.collection.mutable.ArrayBuffer

object TracerSpec extends ZIOSpecDefault {

  private class TestProcessor extends SpanProcessor {
    val started: ArrayBuffer[Span]   = ArrayBuffer.empty
    val ended: ArrayBuffer[SpanData] = ArrayBuffer.empty
    var shutdownCalled: Boolean      = false
    var forceFlushCalled: Boolean    = false

    def onStart(span: Span): Unit       = started += span
    def onEnd(spanData: SpanData): Unit = ended += spanData
    def shutdown(): Unit                = shutdownCalled = true
    def forceFlush(): Unit              = forceFlushCalled = true
  }

  def spec = suite("Tracer")(
    suite("TracerProvider.builder")(
      test("builds with defaults") {
        val provider = TracerProvider.builder.build()
        val tracer   = provider.get("test-lib")
        assertTrue(tracer != null)
      },
      test("setResource and setSampler") {
        val resource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "my-service")
        )
        val provider = TracerProvider.builder
          .setResource(resource)
          .setSampler(AlwaysOnSampler)
          .build()
        val tracer = provider.get("test-lib", "1.0.0")
        assertTrue(tracer != null)
      },
      test("addSpanProcessor registers processor") {
        val processor = new TestProcessor
        val provider  = TracerProvider.builder
          .addSpanProcessor(processor)
          .build()
        val tracer = provider.get("test-lib")
        tracer.span("op")(_ => ())
        assertTrue(processor.started.nonEmpty && processor.ended.nonEmpty)
      },
      test("shutdown calls shutdown on all processors") {
        val p1       = new TestProcessor
        val p2       = new TestProcessor
        val provider = TracerProvider.builder
          .addSpanProcessor(p1)
          .addSpanProcessor(p2)
          .build()
        provider.shutdown()
        assertTrue(p1.shutdownCalled && p2.shutdownCalled)
      },
      test("forceFlush calls forceFlush on all processors") {
        val p1       = new TestProcessor
        val p2       = new TestProcessor
        val provider = TracerProvider.builder
          .addSpanProcessor(p1)
          .addSpanProcessor(p2)
          .build()
        provider.forceFlush()
        assertTrue(p1.forceFlushCalled && p2.forceFlushCalled)
      }
    ),
    suite("Tracer.span scoped block")(
      test("creates span with valid IDs") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        var captured: Span = null
        tracer.span("my-op") { span =>
          captured = span
        }

        assertTrue(
          captured != null &&
            captured.spanContext.isValid &&
            TraceId.isValid(captured.spanContext.traceIdHi, captured.spanContext.traceIdLo) &&
            captured.spanContext.spanId.isValid &&
            captured.name == "my-op"
        )
      },
      test("span is ended after block exits") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        var captured: Span = null
        tracer.span("ending-op") { span =>
          assertTrue(span.isRecording)
          captured = span
        }

        assertTrue(!captured.isRecording)
      },
      test("span block returns the value from f") {
        val tracer = makeTracer(new TestProcessor)
        val result = tracer.span("value-op")(_ => 42)
        assertTrue(result == 42)
      },
      test("SpanProcessor.onStart is called with the span") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        tracer.span("start-op")(_ => ())

        assertTrue(
          processor.started.size == 1 &&
            processor.started.head.name == "start-op"
        )
      },
      test("SpanProcessor.onEnd receives SpanData") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        tracer.span("end-op") { span =>
          span.setAttribute("key", "value")
        }

        assertTrue(
          processor.ended.size == 1 &&
            processor.ended.head.name == "end-op" &&
            processor.ended.head.attributes.get(AttributeKey.string("key")).contains("value")
        )
      },
      test("span with kind") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        tracer.span("server-op", SpanKind.Server) { span =>
          assertTrue(span.kind == SpanKind.Server)
        }
      },
      test("span with kind and attributes") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)
        val attrs     = Attributes.of(AttributeKey.string("http.method"), "GET")

        tracer.span("attr-op", SpanKind.Client, attrs) { span =>
          val data = span.toSpanData
          assertTrue(data.attributes.get(AttributeKey.string("http.method")).contains("GET"))
        }
      },
      test("span ends even if f throws") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        var captured: Span = null
        try
          tracer.span("throwing-op") { span =>
            captured = span
            throw new RuntimeException("boom")
          }
        catch { case _: RuntimeException => () }

        assertTrue(
          !captured.isRecording &&
            processor.ended.size == 1
        )
      }
    ),
    suite("nested spans")(
      test("child inherits parent's traceId but gets new spanId") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        var parentCtx: SpanContext = null
        var childCtx: SpanContext  = null

        tracer.span("parent") { parentSpan =>
          parentCtx = parentSpan.spanContext
          tracer.span("child") { childSpan =>
            childCtx = childSpan.spanContext
          }
        }

        assertTrue(
          parentCtx.traceIdHi == childCtx.traceIdHi && parentCtx.traceIdLo == childCtx.traceIdLo &&
            parentCtx.spanId != childCtx.spanId
        )
      },
      test("child's parentSpanContext points to parent") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        tracer.span("parent") { _ =>
          tracer.span("child")(_ => ())
        }

        val childData  = processor.ended.find(_.name == "child").get
        val parentData = processor.ended.find(_.name == "parent").get

        assertTrue(
          childData.parentSpanContext.spanId == parentData.spanContext.spanId
        )
      },
      test("context is restored after nested span") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)

        tracer.span("outer") { _ =>
          val beforeNested = tracer.currentSpan
          tracer.span("inner")(_ => ())
          val afterNested = tracer.currentSpan
          assertTrue(beforeNested == afterNested)
        }
      }
    ),
    suite("sampler integration")(
      test("AlwaysOffSampler results in Span.NoOp and no processor calls") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor, AlwaysOffSampler)

        var captured: Span = null
        tracer.span("dropped-op") { span =>
          captured = span
        }

        assertTrue(
          captured == Span.NoOp &&
            processor.started.isEmpty &&
            processor.ended.isEmpty
        )
      },
      test("AlwaysOnSampler records span") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor, AlwaysOnSampler)

        var wasRecording = false
        tracer.span("sampled-op") { span =>
          wasRecording = span.isRecording
        }

        assertTrue(wasRecording)
      },
      test("RecordOnly sampler records span with TraceFlags.none") {
        val recordOnlySampler = new Sampler {
          def shouldSample(
            parentContext: Option[SpanContext],
            traceIdHi: Long,
            traceIdLo: Long,
            name: String,
            kind: SpanKind,
            attributes: Attributes,
            links: Seq[SpanLink]
          ): SamplingResult =
            SamplingResult(SamplingDecision.RecordOnly, Attributes.empty, "")
          def description: String = "RecordOnlySampler"
        }
        val processor = new TestProcessor
        val tracer    = makeTracer(processor, recordOnlySampler)

        var captured: Span = null
        tracer.span("record-only-op") { span =>
          captured = span
        }

        assertTrue(
          captured != Span.NoOp &&
            !captured.spanContext.isSampled &&
            captured.spanContext.traceFlags == TraceFlags.none &&
            processor.started.nonEmpty &&
            processor.ended.nonEmpty
        )
      }
    ),
    suite("SpanProcessor.noop")(
      test("all methods are no-ops") {
        val noop = SpanProcessor.noop
        noop.onStart(Span.NoOp)
        noop.onEnd(Span.NoOp.toSpanData)
        noop.shutdown()
        noop.forceFlush()
        assertTrue(true)
      }
    ),
    suite("Tracer.spanBuilder")(
      test("returns a configured SpanBuilder") {
        val processor = new TestProcessor
        val tracer    = makeTracer(processor)
        val builder   = tracer.spanBuilder("builder-span")
        assertTrue(builder != null)
      }
    ),
    suite("Tracer.currentSpan")(
      test("returns None outside of span block") {
        val tracer = makeTracer(new TestProcessor)
        assertTrue(tracer.currentSpan.isEmpty)
      },
      test("returns Some inside span block") {
        val tracer = makeTracer(new TestProcessor)
        tracer.span("current-op") { span =>
          val current = tracer.currentSpan
          assertTrue(current.isDefined && current.get == span.spanContext)
        }
      }
    )
  )

  private def makeTracer(
    processor: SpanProcessor,
    sampler: Sampler = AlwaysOnSampler
  ): Tracer =
    TracerProvider.builder
      .setSampler(sampler)
      .addSpanProcessor(processor)
      .build()
      .get("test-tracer")
}
