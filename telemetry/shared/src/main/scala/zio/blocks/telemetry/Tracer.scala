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
 * Creates spans within a single instrumentation scope.
 *
 * A tracer consults the configured sampler, manages parent context propagation,
 * and notifies registered span processors as spans start and end.
 */
final class Tracer private[telemetry] (
  val instrumentationScope: InstrumentationScope,
  val resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {

  /**
   * Returns a builder for creating manually managed spans.
   */
  def spanBuilder(name: String): SpanBuilder =
    SpanBuilder(name).setResource(resource).setInstrumentationScope(instrumentationScope)

  /**
   * Returns the currently active span context, if one is bound.
   */
  def currentSpan: Option[SpanContext] =
    contextStorage.get()

  /**
   * Creates an internal span around the supplied block.
   */
  def span[A](name: String)(f: Span => A): A =
    span(name, SpanKind.Internal, Attributes.empty)(f)

  /**
   * Creates a span of the specified kind around the supplied block.
   */
  def span[A](name: String, kind: SpanKind)(f: Span => A): A =
    span(name, kind, Attributes.empty)(f)

  /**
   * Creates a span of the specified kind and attributes around the supplied
   * block.
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
