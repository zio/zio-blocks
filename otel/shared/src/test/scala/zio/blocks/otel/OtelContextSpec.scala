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
import zio.blocks.context.Context

object OtelContextSpec extends ZIOSpecDefault {

  def spec = suite("OtelContext")(
    suite("construction")(
      test("current snapshots None when no span is active") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val otelCtx = OtelContext.current(storage)
        assertTrue(otelCtx.spanContext.isEmpty)
      },
      test("current snapshots the active SpanContext") {
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val storage = ContextStorage.create[Option[SpanContext]](None)
        storage.set(Some(sc))
        val otelCtx = OtelContext.current(storage)
        assertTrue(otelCtx.spanContext.contains(sc))
      }
    ),
    suite("spanContext accessor")(
      test("returns None when constructed with no span") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val otelCtx = OtelContext.current(storage)
        assertTrue(otelCtx.spanContext.isEmpty)
      },
      test("returns Some when constructed with active span") {
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val otelCtx = OtelContext(Some(sc))
        assertTrue(otelCtx.spanContext.contains(sc) && otelCtx.spanContext.get.isValid)
      }
    ),
    suite("Context[R] integration")(
      test("store and retrieve OtelContext via Context.apply") {
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val otelCtx = OtelContext(Some(sc))
        val ctx     = Context(otelCtx)
        val got     = ctx.get[OtelContext]
        assertTrue(got.spanContext.contains(sc))
      },
      test("store and retrieve via Context.empty.add") {
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val otelCtx = OtelContext(Some(sc))
        val ctx     = Context.empty.add(otelCtx)
        val got     = ctx.get[OtelContext]
        assertTrue(got.spanContext == otelCtx.spanContext)
      },
      test("getOption returns Some for present OtelContext") {
        val otelCtx = OtelContext(None)
        val ctx     = Context(otelCtx)
        assertTrue(ctx.getOption[OtelContext].isDefined)
      },
      test("getOption returns None for absent OtelContext") {
        val ctx = Context.empty
        assertTrue(ctx.getOption[OtelContext].isEmpty)
      },
      test("OtelContext coexists with other types in Context") {
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val otelCtx = OtelContext(Some(sc))
        val ctx     = Context.empty.add(otelCtx).add("hello")
        assertTrue(
          ctx.get[OtelContext].spanContext.contains(sc) &&
            ctx.get[String] == "hello"
        )
      }
    ),
    suite("withSpan")(
      test("makes span's context current during execution") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val span    = makeSpan(sc)

        val captured = OtelContext.withSpan(span, storage) {
          storage.get()
        }

        assertTrue(captured.contains(sc))
      },
      test("restores previous context after execution") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val span    = makeSpan(sc)

        OtelContext.withSpan(span, storage) {
          ()
        }

        assertTrue(storage.get().isEmpty)
      },
      test("restores context after exception") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val span    = makeSpan(sc)

        try {
          OtelContext.withSpan(span, storage) {
            throw new RuntimeException("boom")
          }
        } catch {
          case _: RuntimeException => ()
        }

        assertTrue(storage.get().isEmpty)
      },
      test("returns the block's result") {
        val storage = ContextStorage.create[Option[SpanContext]](None)
        val sc      = SpanContext.create(TraceId.random, SpanId.random, TraceFlags.sampled, "", false)
        val span    = makeSpan(sc)

        val result = OtelContext.withSpan(span, storage) {
          42
        }

        assertTrue(result == 42)
      }
    ),
    suite("Tracer integration")(
      test("OtelContext.current captures span context set by Tracer") {
        val storage   = ContextStorage.create[Option[SpanContext]](None)
        val processor = new TestSpanProcessor
        val tracer    = makeTracer(processor, storage)

        var captured: OtelContext = null
        tracer.span("integration-op") { _ =>
          captured = OtelContext.current(storage)
        }

        assertTrue(
          captured != null &&
            captured.spanContext.isDefined &&
            captured.spanContext.get.isValid
        )
      }
    )
  )

  private class TestSpanProcessor extends SpanProcessor {
    def onStart(span: Span): Unit       = ()
    def onEnd(spanData: SpanData): Unit = ()
    def shutdown(): Unit                = ()
    def forceFlush(): Unit              = ()
  }

  private def makeSpan(sc: SpanContext): Span = new Span {
    val spanContext: SpanContext = sc
    val name: String             = "test"
    val kind: SpanKind           = SpanKind.Internal

    def setAttribute[A](key: AttributeKey[A], value: A): Unit                 = ()
    def setAttribute(key: String, value: String): Unit                        = ()
    def setAttribute(key: String, value: Long): Unit                          = ()
    def setAttribute(key: String, value: Double): Unit                        = ()
    def setAttribute(key: String, value: Boolean): Unit                       = ()
    def addEvent(name: String): Unit                                          = ()
    def addEvent(name: String, attributes: Attributes): Unit                  = ()
    def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit = ()
    def setStatus(status: SpanStatus): Unit                                   = ()
    def end(): Unit                                                           = ()
    def end(endTimeNanos: Long): Unit                                         = ()
    val isRecording: Boolean                                                  = false
    def toSpanData: SpanData                                                  = Span.NoOp.toSpanData
  }

  private def makeTracer(
    processor: SpanProcessor,
    storage: ContextStorage[Option[SpanContext]]
  ): Tracer =
    new Tracer(
      instrumentationScope = InstrumentationScope("test"),
      resource = Resource.empty,
      sampler = AlwaysOnSampler,
      processors = Seq(processor),
      contextStorage = storage
    )
}
