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
 * The trace ID is inlined as two Long fields (traceIdHi, traceIdLo) for zero
 * heap allocation. SpanId and TraceFlags are AnyVal types, also zero-alloc.
 *
 * @param traceIdHi
 *   the high 64 bits of the trace identifier
 * @param traceIdLo
 *   the low 64 bits of the trace identifier
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
  traceIdHi: Long,
  traceIdLo: Long,
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
  def isValid: Boolean = TraceId.isValid(traceIdHi, traceIdLo) && spanId.isValid

  /**
   * Checks if this span context is sampled.
   *
   * Returns true if the sampled flag in traceFlags is set.
   */
  def isSampled: Boolean = traceFlags.isSampled

  /**
   * Returns the trace ID as a 32-character lowercase hex string.
   */
  def traceIdHex: String = TraceId.toHex(traceIdHi, traceIdLo)
}

object SpanContext {

  /**
   * The invalid/zero span context (represents "no span context").
   *
   * All identifiers are zero, traceState is empty, and isRemote is false.
   */
  val invalid: SpanContext = SpanContext(
    traceIdHi = 0L,
    traceIdLo = 0L,
    spanId = SpanId.invalid,
    traceFlags = TraceFlags.none,
    traceState = "",
    isRemote = false
  )

  /**
   * Creates a new SpanContext with the provided values.
   */
  def create(
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: SpanId,
    traceFlags: TraceFlags,
    traceState: String,
    isRemote: Boolean
  ): SpanContext =
    SpanContext(
      traceIdHi = traceIdHi,
      traceIdLo = traceIdLo,
      spanId = spanId,
      traceFlags = traceFlags,
      traceState = traceState,
      isRemote = isRemote
    )
}
