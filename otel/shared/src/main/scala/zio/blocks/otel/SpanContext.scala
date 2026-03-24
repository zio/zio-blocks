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
 * Represents an OpenTelemetry SpanContext - the propagatable part of a span.
 *
 * Composes TraceId, SpanId, TraceFlags, and trace state into a context that can
 * be propagated across process boundaries.
 *
 * @param traceId
 *   the trace identifier
 * @param spanId
 *   the span identifier
 * @param traceFlags
 *   the trace flags (sampled flag, etc.)
 * @param traceState
 *   the trace state string (vendor-specific tracing headers)
 * @param isRemote
 *   whether this context originates from a remote parent
 */
final case class SpanContext(
  traceId: TraceId,
  spanId: SpanId,
  traceFlags: TraceFlags,
  traceState: String,
  isRemote: Boolean
) {

  /**
   * Checks if this span context is valid.
   *
   * A span context is valid if both its trace ID and span ID are valid.
   */
  def isValid: Boolean = traceId.isValid && spanId.isValid

  /**
   * Checks if this span context is sampled.
   *
   * Returns true if the sampled flag in traceFlags is set.
   */
  def isSampled: Boolean = traceFlags.isSampled
}

object SpanContext {

  /**
   * The invalid/zero span context (represents "no span context").
   *
   * All identifiers are zero, traceState is empty, and isRemote is false.
   */
  val invalid: SpanContext = SpanContext(
    traceId = TraceId.invalid,
    spanId = SpanId.invalid,
    traceFlags = TraceFlags.none,
    traceState = "",
    isRemote = false
  )

  /**
   * Creates a new SpanContext with the provided values.
   */
  def create(
    traceId: TraceId,
    spanId: SpanId,
    traceFlags: TraceFlags,
    traceState: String,
    isRemote: Boolean
  ): SpanContext =
    SpanContext(
      traceId = traceId,
      spanId = spanId,
      traceFlags = traceFlags,
      traceState = traceState,
      isRemote = isRemote
    )

  /**
   * Unscoped instance - SpanContext is a safe data type that can escape scopes.
   */
}
