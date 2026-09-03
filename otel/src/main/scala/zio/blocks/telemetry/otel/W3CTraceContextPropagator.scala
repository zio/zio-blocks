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
 * W3C TraceContext propagator implementing the traceparent/tracestate headers.
 *
 * traceparent format:
 * {version:2hex}-{trace-id:32hex}-{span-id:16hex}-{flags:2hex}
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
 * @see
 *   https://www.w3.org/TR/trace-context/
 */
object W3CTraceContextPropagator extends Propagator {

  private val TraceparentHeader = "traceparent"
  private val TracestateHeader  = "tracestate"
  private val Version           = "00"
  private val TraceparentLength = 55 // 2 + 1 + 32 + 1 + 16 + 1 + 2

  /** Maximum tracestate length in characters (W3C trace-context bound). */
  private[otel] final val MaxTracestateLength = 512

  override val fields: Seq[String] = Seq(TraceparentHeader, TracestateHeader)

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
