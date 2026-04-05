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
 * An event associated with a span, capturing a point-in-time occurrence.
 *
 * @param name
 *   human-readable name of the event
 * @param timestampNanos
 *   epoch nanoseconds when the event occurred
 * @param attributes
 *   attributes associated with this event
 */
final case class SpanEvent(
  name: String,
  timestampNanos: Long,
  attributes: Attributes
)

/**
 * A link to another span, used to represent causal relationships across traces.
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
 * Immutable snapshot of span data for export.
 *
 * Created by calling `toSpanData` on a `Span`. Contains all information
 * collected during the span's lifetime.
 *
 * @param name
 *   the span name
 * @param kind
 *   the span kind
 * @param spanContext
 *   the span's own context
 * @param parentSpanContext
 *   the parent span's context (invalid if root span)
 * @param startTimeNanos
 *   epoch nanoseconds when the span started
 * @param endTimeNanos
 *   epoch nanoseconds when the span ended
 * @param attributes
 *   attributes set on the span
 * @param events
 *   events recorded during the span
 * @param links
 *   links to other spans
 * @param status
 *   the span's completion status
 * @param resource
 *   the resource producing this span
 * @param instrumentationScope
 *   the instrumentation scope that created this span
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
