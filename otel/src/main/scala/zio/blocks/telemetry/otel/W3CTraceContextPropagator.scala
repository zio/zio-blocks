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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

/**
 * Reads and writes trace context in the W3C Trace Context headers,
 * `traceparent` and `tracestate`, the format OpenTelemetry uses by default. Use
 * it unless the services you call expect B3 ([[B3Propagator]]).
 *
 * `traceparent` holds the version, the trace id, the parent span id and the
 * trace flags: `00-<32 hex digits>-<16 hex digits>-<2 hex digits>`.
 * `tracestate` holds vendor data, which this propagator copies to and from
 * `SpanContext#traceState` without reading it.
 *
 * Version handling follows the forward-compatibility rule: version `"00"` must
 * be exactly 55 characters; future versions (any 2-hex version other than
 * `"ff"`) are accepted when they carry at least the 55-character version-00
 * prefix, and trailing fields are ignored. `"ff"` is always rejected.
 *
 * tracestate is validated and bounded per the spec (at most 512 characters of
 * comma-separated `key=value` members). An absent, overlong, or malformed
 * tracestate is dropped to `""` — the traceparent context still extracts.
 *
 * {{{
 * val get = (headers: Map[String, String], name: String) => headers.get(name)
 * val set = (headers: Map[String, String], name: String, value: String) => headers + (name -> value)
 *
 * val incoming = Map(
 *   "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
 *   "tracestate"  -> "checkout=eu-west"
 * )
 * val parent = W3CTraceContextPropagator.extract(incoming, get)
 * parent.map(ctx => (ctx.traceIdHex, ctx.isSampled, ctx.isRemote))
 * // Some((4bf92f3577b34da6a3ce929d0e0e4736,true,true))
 *
 * val outgoing = parent.map(ctx => W3CTraceContextPropagator.inject(ctx, Map.empty[String, String], set))
 * // Some(Map(traceparent -> 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01, tracestate -> checkout=eu-west))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 * @see
 *   [[https://www.w3.org/TR/trace-context/ W3C Trace Context]]
 */
object W3CTraceContextPropagator extends Propagator {

  private val TraceparentHeader = "traceparent"
  private val TracestateHeader  = "tracestate"
  private val Version           = "00"
  private val TraceparentLength = 55 // 2 + 1 + 32 + 1 + 16 + 1 + 2

  /** Maximum tracestate length in characters (W3C trace-context bound). */
  private[otel] final val MaxTracestateLength = 512

  /** Returns `traceparent` and `tracestate`. */
  override val fields: Seq[String] = Seq(TraceparentHeader, TracestateHeader)

  /**
   * Returns the context in the `traceparent` header of `carrier`, with the
   * `tracestate` header (trimmed, or empty when absent) as its trace state and
   * `isRemote` set to `true`. Surrounding whitespace is ignored. Returns `None`
   * when `traceparent` is absent, shorter than 55 characters, has its dashes
   * elsewhere, has a version other than `00`, has an all-zero or non-hex trace
   * or span id, or has non-hex flags.
   *
   * The parser departs from the W3C specification both ways, a known bug. It
   * rejects every version other than `00`, where the specification says to read
   * the first four fields of a higher version. It accepts some headers the
   * specification rejects: upper-case hex digits, extra characters after the
   * flags, a `+` at the start of the span id, of either half of the trace id or
   * of the flags, and a `-` at the start of the flags (`-1` gives the flags
   * `ff`).
   *
   * {{{
   * def spanIdOf(traceparent: String): Option[String] =
   *   W3CTraceContextPropagator
   *     .extract[Map[String, String]](Map("traceparent" -> traceparent), (headers, name) => headers.get(name))
   *     .map(_.spanId.toHex)
   *
   * spanIdOf("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00") // Some(00f067aa0ba902b7)
   * spanIdOf("00-00000000000000000000000000000000-00f067aa0ba902b7-01") // None
   * spanIdOf("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01") // None, a known bug
   * spanIdOf("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra") // Some(00f067aa0ba902b7), a known bug
   * }}}
   */
  override def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext] =
    for {
      raw        <- getter(carrier, TraceparentHeader)
      traceparent = raw.trim
      _          <- if (traceparent.length >= TraceparentLength) Some(()) else None
      _          <- if (traceparent.charAt(2) == '-' && traceparent.charAt(35) == '-' && traceparent.charAt(52) == '-') Some(())
           else None
      version         = traceparent.substring(0, 2)
      _              <- if (isSupportedVersion(version, traceparent.length)) Some(()) else None
      traceIdHex      = traceparent.substring(3, 35).toLowerCase
      (tidHi, tidLo) <- TraceId.fromHex(traceIdHex)
      _              <- if (TraceId.isValid(tidHi, tidLo)) Some(()) else None
      spanIdHex       = traceparent.substring(36, 52).toLowerCase
      spanId         <- SpanId.fromHex(spanIdHex)
      _              <- if (spanId.isValid) Some(()) else None
      flagsHex        = traceparent.substring(53, 55).toLowerCase
      flags          <- TraceFlags.fromHex(flagsHex)
    } yield {
      val traceState =
        normalizeTraceState(getter(carrier, TracestateHeader).map(_.trim).getOrElse(""))
      SpanContext.create(tidHi, tidLo, spanId, flags, traceState, isRemote = true)
    }

  /**
   * Accepts version `"00"` at exactly 55 characters, and any other 2-hex
   * version (except `"ff"`) at 55+ characters so future versions with the same
   * shape keep extracting.
   */
  private def isSupportedVersion(version: String, length: Int): Boolean =
    if (version.length != 2 || !isHexByte(version) || version == "ff") false
    else if (version == Version) length == TraceparentLength
    else length >= TraceparentLength

  private def isHexByte(s: String): Boolean =
    isHexChar(s.charAt(0)) && isHexChar(s.charAt(1))

  private def isHexChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  /**
   * Bounds tracestate to 512 characters and checks the `key=value` member
   * shape. Returns `""` for absent, overlong, or malformed values.
   */
  private[otel] def normalizeTraceState(state: String): String =
    if (state.isEmpty || state.length > MaxTracestateLength) ""
    else if (isValidTraceState(state)) state
    else ""

  private def isValidTraceState(state: String): Boolean = {
    var i        = 0
    val len      = state.length
    var valid    = true
    var keyEmpty = true
    var hasEq    = false
    while (i < len && valid) {
      val c = state.charAt(i)
      if (c == ',') {
        // End of member: must have had key=value with a non-empty key.
        if (!hasEq || keyEmpty) valid = false
        else {
          keyEmpty = true
          hasEq = false
        }
      } else if (c == '=' && !hasEq) {
        hasEq = true
      } else if (!hasEq && !c.isWhitespace) {
        keyEmpty = false
      }
      i += 1
    }
    valid && hasEq && !keyEmpty
  }

  /**
   * Returns `carrier` with `traceparent` set from `spanContext` (version `00`,
   * the trace id, the span id and all eight flag bits), and with `tracestate`
   * set when the context's trace state is not empty. Returns `carrier`
   * unchanged when `spanContext` is invalid. It never removes a header, so a
   * `tracestate` already in `carrier` stays when the context has none.
   *
   * {{{
   * import zio.blocks.telemetry._
   *
   * val set     = (headers: Map[String, String], name: String, value: String) => headers + (name -> value)
   * val request = Map("accept" -> "application/json")
   *
   * val ctx = SpanContext.create(
   *   traceIdHi = 0x4bf92f3577b34da6L,
   *   traceIdLo = 0xa3ce929d0e0e4736L,
   *   spanId = SpanId(0x00f067aa0ba902b7L),
   *   traceFlags = TraceFlags.none,
   *   traceState = "",
   *   isRemote = false
   * )
   * W3CTraceContextPropagator.inject(ctx, request, set)
   * // Map(accept -> application/json, traceparent -> 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00)
   * W3CTraceContextPropagator.inject(SpanContext.invalid, request, set) // Map(accept -> application/json)
   * }}}
   */
  override def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C =
    if (!spanContext.isValid) carrier
    else {
      val traceparent =
        s"$Version-${spanContext.traceIdHex}-${spanContext.spanId.toHex}-${spanContext.traceFlags.toHex}"
      val withParent = setter(carrier, TraceparentHeader, traceparent)
      if (spanContext.traceState.nonEmpty) setter(withParent, TracestateHeader, spanContext.traceState)
      else withParent
    }
}
