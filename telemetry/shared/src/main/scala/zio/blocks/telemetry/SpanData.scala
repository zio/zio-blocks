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
 * Something that happened at one point during a span, recorded with
 * `Span#addEvent`.
 *
 * @param name
 *   the name of the event
 * @param timestampNanos
 *   the time of the event, in epoch nanoseconds
 * @param attributes
 *   attributes associated with this event
 */
final case class SpanEvent(
  name: String,
  timestampNanos: Long,
  attributes: Attributes
)

/**
 * A link from a span to another span, often in another trace, that caused it or
 * relates to it; add one with `SpanBuilder#addLink`.
 *
 * @param spanContext
 *   the context of the linked span
 * @param attributes
 *   attributes associated with this link
 */
final case class SpanLink(
  spanContext: SpanContext,
  attributes: Attributes
)

/**
 * An immutable snapshot of a span: what `SpanProcessor#onEnd` receives when a
 * span ends, what `trace.collectedSpans` returns, and what `Span#toSpanData`
 * takes at any time.
 *
 * @param name
 *   the span name
 * @param kind
 *   the span kind
 * @param spanContext
 *   the span's own context
 * @param parentSpanContext
 *   the parent span's context, or [[SpanContext.invalid]] for a root span
 * @param startTimeNanos
 *   the start time, in epoch nanoseconds
 * @param endTimeNanos
 *   the end time, in epoch nanoseconds, or `0` in a snapshot taken before the
 *   span ended
 * @param attributes
 *   attributes set on the span
 * @param events
 *   events recorded during the span, in the order they were added
 * @param links
 *   links to other spans
 * @param status
 *   the span's status, `Unset` unless something set it
 * @param resource
 *   the resource producing this span
 * @param instrumentationScope
 *   the instrumentation scope of the tracer that opened the span
 */
final case class SpanData(
  name: String,
  kind: SpanKind,
  spanContext: SpanContext,
  parentSpanContext: SpanContext,
  startTimeNanos: Long,
  endTimeNanos: Long,
  attributes: Attributes,
  events: List[SpanEvent],
  links: List[SpanLink],
  status: SpanStatus,
  resource: Resource,
  instrumentationScope: InstrumentationScope
)
