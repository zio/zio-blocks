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
 * Starts a span by hand, with settings that `Tracer#span` cannot take: a start
 * time, links to other spans, or an explicit parent. Get one from
 * `Tracer#spanBuilder`, which presets the tracer's resource and scope, or from
 * `SpanBuilder(name)`. Each setter changes the builder and returns it.
 *
 * The span it starts reaches no sampler and no processor: it is never exported,
 * and `trace.collectedSpans` does not include it. It does not read the current
 * span either, so it starts a new trace unless `setParent` gives it a parent,
 * and you call `end` yourself. Use `trace.span` or `Tracer#span` for spans that
 * should reach a backend.
 *
 * {{{
 * val upstream = SpanContext.create(
 *   traceIdHi = 0x4bf92f3577b34da6L,
 *   traceIdLo = 0xa3ce929d0e0e4736L,
 *   spanId = SpanId(0x00f067aa0ba902b7L),
 *   traceFlags = TraceFlags.sampled,
 *   traceState = "",
 *   isRemote = true
 * )
 * val consume = SpanBuilder("consume-order")
 *   .setKind(SpanKind.Consumer)
 *   .setParent(upstream)
 *   .setStartTimestamp(1700000000000000000L)
 *   .startSpan()
 * consume.end(1700000000250000000L)
 *
 * consume.spanContext.traceIdHex // 4bf92f3577b34da6a3ce929d0e0e4736
 * consume.toSpanData.endTimeNanos - consume.toSpanData.startTimeNanos // 250000000
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/span-builder/ SpanBuilder]]
 */
final class SpanBuilder private (
  private val spanName: String,
  private var spanKind: SpanKind,
  private var parentContext: SpanContext,
  private val attributesBuilder: Attributes.AttributesBuilder,
  private var links: List[SpanLink],
  private var startTimestamp: Long,
  private var resource: Resource,
  private var instrumentationScope: InstrumentationScope
) {

  /** Sets the span kind; the default is `SpanKind.Internal`. */
  def setKind(kind: SpanKind): SpanBuilder = {
    spanKind = kind
    this
  }

  /**
   * Sets the parent of the span; the default, [[SpanContext.invalid]], makes a
   * root span. When `parentContext` is valid, the span copies its trace flags
   * and trace state, and `startSpan()` also takes its trace id.
   */
  def setParent(parentContext: SpanContext): SpanBuilder = {
    this.parentContext = parentContext
    this
  }

  /**
   * Adds the attribute `key`, replacing an earlier value with the same name.
   */
  def setAttribute[A](key: AttributeKey[A], value: A): SpanBuilder = {
    attributesBuilder.put(key, value)
    this
  }

  /**
   * Adds a link to another span, such as the one that sent the message this
   * span handles; links keep the order they were added in.
   */
  def addLink(link: SpanLink): SpanBuilder = {
    links = links :+ link
    this
  }

  /**
   * Sets the start time in epoch nanoseconds. A value of `0` or less, like the
   * default, means the time `startSpan` is called.
   */
  def setStartTimestamp(nanos: Long): SpanBuilder = {
    startTimestamp = nanos
    this
  }

  /** Sets the resource of the span; the default is `Resource.empty`. */
  def setResource(resource: Resource): SpanBuilder = {
    this.resource = resource
    this
  }

  /**
   * Sets the instrumentation scope of the span; the default is a scope named
   * `default`.
   */
  def setInstrumentationScope(scope: InstrumentationScope): SpanBuilder = {
    this.instrumentationScope = scope
    this
  }

  /**
   * Starts the span in the trace whose 128-bit id has the halves `traceIdHi`
   * and `traceIdLo`, with a new random span id, even when the parent is in
   * another trace. Without a valid parent, the span is marked sampled.
   */
  def startSpan(traceIdHi: Long, traceIdLo: Long): Span = {

    val spanId = SpanId.random

    val traceFlags =
      if (parentContext.isValid) parentContext.traceFlags
      else TraceFlags.sampled

    val spanContext = SpanContext.create(
      traceIdHi = traceIdHi,
      traceIdLo = traceIdLo,
      spanId = spanId,
      traceFlags = traceFlags,
      traceState = if (parentContext.isValid) parentContext.traceState else "",
      isRemote = false
    )

    val actualStart =
      if (startTimestamp > 0L) startTimestamp
      else EpochClock.epochNanos()

    new RecordingSpan(
      spanContext = spanContext,
      name = spanName,
      kind = spanKind,
      parentSpanContext = parentContext,
      startTimeNanos = actualStart,
      initialAttributes = attributesBuilder.build,
      initialLinks = links,
      resource = resource,
      instrumentationScope = instrumentationScope
    )
  }

  /**
   * Starts the span with a new random span id, in the parent's trace or,
   * without a valid parent, in a new random trace, marked sampled.
   */
  def startSpan(): Span = {
    val (traceIdHi, traceIdLo) =
      if (parentContext.isValid) (parentContext.traceIdHi, parentContext.traceIdLo)
      else TraceId.random()

    startSpan(traceIdHi, traceIdLo)
  }
}

/** Creates [[SpanBuilder]]s. */
object SpanBuilder {

  /**
   * Returns a builder for a span named `name`, of kind `Internal`, with no
   * parent, no resource and the scope `default`. `Tracer#spanBuilder` presets
   * the tracer's resource and scope instead.
   */
  def apply(name: String): SpanBuilder =
    new SpanBuilder(
      spanName = name,
      spanKind = SpanKind.Internal,
      parentContext = SpanContext.invalid,
      attributesBuilder = Attributes.builder,
      links = List.empty,
      startTimestamp = -1L,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope("default")
    )
}
