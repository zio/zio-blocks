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
 * The part of a span that travels to other services: the 128-bit trace id, as
 * two `Long` halves, the [[SpanId]], the [[TraceFlags]] and the W3C trace
 * state. A propagator from the `otel` module writes it into request headers and
 * reads it back on the other side, with `isRemote` set. [[SpanContext.invalid]]
 * stands for no span.
 *
 * {{{
 * val incoming = SpanContext.create(
 *   traceIdHi = 0x4bf92f3577b34da6L,
 *   traceIdLo = 0xa3ce929d0e0e4736L,
 *   spanId = SpanId(0x00f067aa0ba902b7L),
 *   traceFlags = TraceFlags.sampled,
 *   traceState = "vendor=1",
 *   isRemote = true
 * )
 *
 * incoming.traceIdHex // 4bf92f3577b34da6a3ce929d0e0e4736
 * incoming.isValid // true
 * SpanContext.invalid.isValid // false
 * }}}
 *
 * @param traceIdHi
 *   the high 64 bits of the trace identifier
 * @param traceIdLo
 *   the low 64 bits of the trace identifier
 * @param spanId
 *   the span identifier
 * @param traceFlags
 *   the trace flags; bit 0 is the sampled flag
 * @param traceState
 *   the W3C `tracestate` value, such as `vendor=1`, or empty
 * @param isRemote
 *   `true` when the context was read from another process, such as from the
 *   headers of an incoming request
 */
final case class SpanContext(
  traceIdHi: Long,
  traceIdLo: Long,
  spanId: SpanId,
  traceFlags: TraceFlags,
  traceState: String,
  isRemote: Boolean
) {

  /** Returns `true` when both the trace id and the span id are non-zero. */
  def isValid: Boolean = TraceId.isValid(traceIdHi, traceIdLo) && spanId.isValid

  /** Returns `true` when the sampled bit of `traceFlags` is set. */
  def isSampled: Boolean = traceFlags.isSampled

  /**
   * Returns the trace id as 32 lowercase hex digits, with leading zeros.
   */
  def traceIdHex: String = TraceId.toHex(traceIdHi, traceIdLo)
}

/** Holds [[SpanContext.invalid]] and a named-parameter constructor. */
object SpanContext {

  /**
   * The context that stands for no span: both ids are zero, no flag is set, the
   * trace state is empty and `isRemote` is `false`.
   */
  val invalid: SpanContext = SpanContext(
    traceIdHi = 0L,
    traceIdLo = 0L,
    spanId = SpanId.invalid,
    traceFlags = TraceFlags.none,
    traceState = "",
    isRemote = false
  )

  /** Creates a span context, as the case-class constructor does. */
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
