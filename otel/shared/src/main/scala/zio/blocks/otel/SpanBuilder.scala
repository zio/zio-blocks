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

/**
 * Fluent builder for creating spans.
 *
 * Configured via chained method calls, then `startSpan()` creates the recording
 * span. The builder creates a new SpanId and inherits the TraceId from the
 * parent context (or generates a new one for root spans).
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

  def setKind(kind: SpanKind): SpanBuilder = {
    spanKind = kind
    this
  }

  def setParent(parentContext: SpanContext): SpanBuilder = {
    this.parentContext = parentContext
    this
  }

  def setAttribute[A](key: AttributeKey[A], value: A): SpanBuilder = {
    attributesBuilder.put(key, value)
    this
  }

  def addLink(link: SpanLink): SpanBuilder = {
    links = links :+ link
    this
  }

  def setStartTimestamp(nanos: Long): SpanBuilder = {
    startTimestamp = nanos
    this
  }

  def setResource(resource: Resource): SpanBuilder = {
    this.resource = resource
    this
  }

  def setInstrumentationScope(scope: InstrumentationScope): SpanBuilder = {
    this.instrumentationScope = scope
    this
  }

  def startSpan(): Span = {
    val traceId =
      if (parentContext.isValid) parentContext.traceId
      else TraceId.random

    val spanId = SpanId.random

    val traceFlags =
      if (parentContext.isValid) parentContext.traceFlags
      else TraceFlags.sampled

    val spanContext = SpanContext.create(
      traceId = traceId,
      spanId = spanId,
      traceFlags = traceFlags,
      traceState = if (parentContext.isValid) parentContext.traceState else "",
      isRemote = false
    )

    val actualStart =
      if (startTimestamp > 0L) startTimestamp
      else System.nanoTime()

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
}

object SpanBuilder {

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
