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

/**
 * Opens the spans of one instrumentation scope: each span carries the tracer's
 * `instrumentationScope` (a name and an optional version) and `resource`, so a
 * backend can tell which component did the work. Get one from
 * [[TracerProvider#get]], or from `trace.get` in application code; library code
 * should take a `Tracer` as a parameter rather than use the global `trace`.
 *
 * `span` asks the provider's [[Sampler]] whether to record, makes the new span
 * a child of the current one, runs the block, and passes the span to the
 * provider's [[SpanProcessor]]s when it starts and ends. The current span lives
 * in the provider's `ContextStorage`. Unless a provider is given its own, every
 * provider and the global `log` share one, so spans from different tracers
 * nest, and log records carry the ids of the current span.
 *
 * {{{
 * import scala.collection.mutable.ListBuffer
 *
 * val finished = ListBuffer.empty[SpanData]
 * val inMemory = new SpanProcessor {
 *   def onStart(span: Span): Unit = ()
 *   def onEnd(spanData: SpanData): Unit = finished += spanData
 *   def shutdown(): Unit = ()
 *   def forceFlush(): Unit = ()
 * }
 * val tracer = TracerProvider.builder.addSpanProcessor(inMemory).build().get("com.example.orders", "1.4.0")
 *
 * tracer.span("process-order", SpanKind.Server) { _ =>
 *   tracer.span("persist-order", SpanKind.Client)(_.setAttribute("db.system", "postgresql"))
 * }
 *
 * finished.map(_.name) // ListBuffer(persist-order, process-order)
 * finished.head.instrumentationScope.version // Some(1.4.0)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/tracer/ Tracer]]
 */
final class Tracer private[telemetry] (
  val instrumentationScope: InstrumentationScope,
  val resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {

  /**
   * Returns a [[SpanBuilder]] for a span named `name`, preset with this
   * tracer's resource and scope, for a span you start and end yourself.
   *
   * The span it starts skips the sampler and the processors, so it is never
   * exported and `trace.collectedSpans` does not include it. It does not read
   * the current span either: it starts a new trace unless
   * `SpanBuilder#setParent` gives it a parent.
   */
  def spanBuilder(name: String): SpanBuilder =
    SpanBuilder(name).setResource(resource).setInstrumentationScope(instrumentationScope)

  /**
   * Returns the context of the innermost span open on this thread in this
   * tracer's storage, or `None` outside every span. Inside a span that the
   * sampler dropped, it returns that span's context, with the sampled flag
   * cleared.
   */
  def currentSpan: Option[SpanContext] =
    contextStorage.get()

  /**
   * Opens a span named `name` of kind `Internal` and runs `f` with it; see the
   * three-parameter `span` for the details.
   */
  def span[A](name: String)(f: Span => A): A =
    span(name, SpanKind.Internal, Attributes.empty)(f)

  /**
   * Opens a span named `name` of kind `kind` and runs `f` with it; see the
   * three-parameter `span` for the details.
   */
  def span[A](name: String, kind: SpanKind)(f: Span => A): A =
    span(name, kind, Attributes.empty)(f)

  /**
   * Opens a span named `name` of kind `kind` that starts with `attributes`,
   * runs `f` with it as the current span, and returns the result of `f`.
   *
   * The new span is a child of the current span, in its trace, or starts a new
   * trace when there is none. The sampler sees `name`, `kind` and `attributes`,
   * but not the attributes `f` sets later. When it drops the span, `f` receives
   * [[Span.NoOp]] and no processor hears of it. Otherwise every processor's
   * `onStart` runs before `f`; when `f` returns or throws, the span ends and
   * every processor's `onEnd` receives its [[SpanData]], on the calling thread.
   * An exception from `f` then propagates, and the span's status stays `Unset`
   * unless `f` set one.
   *
   * Attributes that the sampler returns are added to the span before
   * `attributes`, which win on a name clash.
   */
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A = {
    val parentCtx              = contextStorage.get()
    val (traceIdHi, traceIdLo) = parentCtx match {
      case Some(p) if p.isValid => (p.traceIdHi, p.traceIdLo)
      case _                    => TraceId.random()
    }

    val result = sampler.shouldSample(
      parentCtx,
      traceIdHi,
      traceIdLo,
      name,
      kind,
      attributes,
      Seq.empty
    )

    result.decision match {
      case SamplingDecision.Drop =>
        val droppedContext =
          SpanContext.create(
            traceIdHi = traceIdHi,
            traceIdLo = traceIdLo,
            spanId = SpanId.random,
            traceFlags = TraceFlags.none,
            traceState = result.traceState,
            isRemote = false
          )
        contextStorage.scoped(Some(droppedContext)) {
          f(Span.NoOp)
        }

      case SamplingDecision.RecordOnly =>
        val recordOnlySpan =
          buildSpan(
            name = name,
            kind = kind,
            parentCtx = parentCtx,
            attributes = attributes,
            samplingAttributes = result.attributes,
            traceFlags = TraceFlags.none,
            traceState = result.traceState,
            traceIdHi = traceIdHi,
            traceIdLo = traceIdLo
          )

        processors.foreach(_.onStart(recordOnlySpan))

        try
          contextStorage.scoped(Some(recordOnlySpan.spanContext)) {
            f(recordOnlySpan)
          }
        finally {
          recordOnlySpan.end()
          val spanData = recordOnlySpan.toSpanData
          processors.foreach(_.onEnd(spanData))
        }

      case SamplingDecision.RecordAndSample =>
        val finalSpan =
          buildSpan(
            name = name,
            kind = kind,
            parentCtx = parentCtx,
            attributes = attributes,
            samplingAttributes = result.attributes,
            traceFlags = TraceFlags.sampled,
            traceState = result.traceState,
            traceIdHi = traceIdHi,
            traceIdLo = traceIdLo
          )

        processors.foreach(_.onStart(finalSpan))

        try
          contextStorage.scoped(Some(finalSpan.spanContext)) {
            f(finalSpan)
          }
        finally {
          finalSpan.end()
          val spanData = finalSpan.toSpanData
          processors.foreach(_.onEnd(spanData))
        }
    }
  }

  private def buildSpan(
    name: String,
    kind: SpanKind,
    parentCtx: Option[SpanContext],
    attributes: Attributes,
    samplingAttributes: Attributes,
    traceFlags: TraceFlags,
    traceState: String,
    traceIdHi: Long,
    traceIdLo: Long
  ): RecordingSpan = {
    val builder =
      SpanBuilder(name).setKind(kind).setResource(resource).setInstrumentationScope(instrumentationScope)

    parentCtx.foreach(p => builder.setParent(p))

    samplingAttributes.foreach { (k, v) =>
      putAttribute(builder, k, v)
    }

    attributes.foreach { (k, v) =>
      putAttribute(builder, k, v)
    }

    val span = builder.startSpan(traceIdHi, traceIdLo)
    new RecordingSpan(
      spanContext = span.spanContext.copy(
        traceFlags = traceFlags,
        traceState = if (traceState.nonEmpty) traceState else span.spanContext.traceState
      ),
      name = span.name,
      kind = span.kind,
      parentSpanContext = span.toSpanData.parentSpanContext,
      startTimeNanos = span.toSpanData.startTimeNanos,
      initialAttributes = span.toSpanData.attributes,
      initialLinks = span.toSpanData.links,
      resource = resource,
      instrumentationScope = instrumentationScope
    )
  }

  private def putAttribute(builder: SpanBuilder, key: String, value: AttributeValue): Unit =
    value match {
      case AttributeValue.StringValue(s)      => builder.setAttribute(AttributeKey.string(key), s)
      case AttributeValue.LongValue(l)        => builder.setAttribute(AttributeKey.long(key), l)
      case AttributeValue.DoubleValue(d)      => builder.setAttribute(AttributeKey.double(key), d)
      case AttributeValue.BooleanValue(b)     => builder.setAttribute(AttributeKey.boolean(key), b)
      case AttributeValue.StringSeqValue(xs)  => builder.setAttribute(AttributeKey.stringSeq(key), xs)
      case AttributeValue.LongSeqValue(xs)    => builder.setAttribute(AttributeKey.longSeq(key), xs)
      case AttributeValue.DoubleSeqValue(xs)  => builder.setAttribute(AttributeKey.doubleSeq(key), xs)
      case AttributeValue.BooleanSeqValue(xs) => builder.setAttribute(AttributeKey.booleanSeq(key), xs)
    }
}
