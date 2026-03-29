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

final class Tracer(
  val instrumentationScope: InstrumentationScope,
  val resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {

  def spanBuilder(name: String): SpanBuilder =
    SpanBuilder(name).setResource(resource).setInstrumentationScope(instrumentationScope)

  def currentSpan: Option[SpanContext] =
    contextStorage.get()

  def span[A](name: String)(f: Span => A): A =
    span(name, SpanKind.Internal, Attributes.empty)(f)

  def span[A](name: String, kind: SpanKind)(f: Span => A): A =
    span(name, kind, Attributes.empty)(f)

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
        f(Span.NoOp)

      case SamplingDecision.RecordOnly =>
        val traceFlags = TraceFlags.none
        val builder    =
          SpanBuilder(name).setKind(kind).setResource(resource).setInstrumentationScope(instrumentationScope)

        parentCtx.foreach(p => builder.setParent(p))

        result.attributes.foreach { (k, v) =>
          v match {
            case AttributeValue.StringValue(s)  => builder.setAttribute(AttributeKey.string(k), s)
            case AttributeValue.LongValue(l)    => builder.setAttribute(AttributeKey.long(k), l)
            case AttributeValue.DoubleValue(d)  => builder.setAttribute(AttributeKey.double(k), d)
            case AttributeValue.BooleanValue(b) => builder.setAttribute(AttributeKey.boolean(k), b)
            case _                              => ()
          }
        }

        attributes.foreach { (k, v) =>
          v match {
            case AttributeValue.StringValue(s)  => builder.setAttribute(AttributeKey.string(k), s)
            case AttributeValue.LongValue(l)    => builder.setAttribute(AttributeKey.long(k), l)
            case AttributeValue.DoubleValue(d)  => builder.setAttribute(AttributeKey.double(k), d)
            case AttributeValue.BooleanValue(b) => builder.setAttribute(AttributeKey.boolean(k), b)
            case _                              => ()
          }
        }

        val span = builder.startSpan()
        // Override traceFlags to none (not sampled) and apply traceState from SamplingResult
        val correctedCtx = span.spanContext.copy(
          traceFlags = traceFlags,
          traceState = if (result.traceState.nonEmpty) result.traceState else span.spanContext.traceState
        )
        val recordOnlySpan = new RecordingSpan(
          spanContext = correctedCtx,
          name = span.name,
          kind = span.kind,
          parentSpanContext = span.toSpanData.parentSpanContext,
          startTimeNanos = span.toSpanData.startTimeNanos,
          initialAttributes = span.toSpanData.attributes,
          initialLinks = span.toSpanData.links,
          resource = resource,
          instrumentationScope = instrumentationScope
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
        val builder =
          SpanBuilder(name).setKind(kind).setResource(resource).setInstrumentationScope(instrumentationScope)

        parentCtx.foreach(p => builder.setParent(p))

        result.attributes.foreach { (k, v) =>
          v match {
            case AttributeValue.StringValue(s)  => builder.setAttribute(AttributeKey.string(k), s)
            case AttributeValue.LongValue(l)    => builder.setAttribute(AttributeKey.long(k), l)
            case AttributeValue.DoubleValue(d)  => builder.setAttribute(AttributeKey.double(k), d)
            case AttributeValue.BooleanValue(b) => builder.setAttribute(AttributeKey.boolean(k), b)
            case _                              => ()
          }
        }

        attributes.foreach { (k, v) =>
          v match {
            case AttributeValue.StringValue(s)  => builder.setAttribute(AttributeKey.string(k), s)
            case AttributeValue.LongValue(l)    => builder.setAttribute(AttributeKey.long(k), l)
            case AttributeValue.DoubleValue(d)  => builder.setAttribute(AttributeKey.double(k), d)
            case AttributeValue.BooleanValue(b) => builder.setAttribute(AttributeKey.boolean(k), b)
            case _                              => ()
          }
        }

        val span = builder.startSpan()
        // Apply traceState from SamplingResult if present
        val finalSpan = if (result.traceState.nonEmpty) {
          val correctedCtx = span.spanContext.copy(traceState = result.traceState)
          new RecordingSpan(
            spanContext = correctedCtx,
            name = span.name,
            kind = span.kind,
            parentSpanContext = span.toSpanData.parentSpanContext,
            startTimeNanos = span.toSpanData.startTimeNanos,
            initialAttributes = span.toSpanData.attributes,
            initialLinks = span.toSpanData.links,
            resource = resource,
            instrumentationScope = instrumentationScope
          )
        } else span

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
}
