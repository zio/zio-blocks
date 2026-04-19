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
 * W3C TraceContext propagator implementing the traceparent/tracestate headers.
 *
 * traceparent format:
 * {version:2hex}-{trace-id:32hex}-{span-id:16hex}-{flags:2hex} Only version
 * "00" is supported.
 *
 * @see
 *   https://www.w3.org/TR/trace-context/
 */
object W3CTraceContextPropagator extends Propagator {

  private val TraceparentHeader = "traceparent"
  private val TracestateHeader  = "tracestate"
  private val Version           = "00"
  private val TraceparentLength = 55 // 2 + 1 + 32 + 1 + 16 + 1 + 2

  override val fields: Seq[String] = Seq(TraceparentHeader, TracestateHeader)

  override def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext] =
    for {
      raw        <- getter(carrier, TraceparentHeader)
      traceparent = raw.trim
      _          <- if (traceparent.length == TraceparentLength) Some(()) else None
      _          <- if (traceparent.charAt(2) == '-' && traceparent.charAt(35) == '-' && traceparent.charAt(52) == '-') Some(())
           else None
      version         = traceparent.substring(0, 2)
      _              <- if (version == Version) Some(()) else None
      traceIdHex      = traceparent.substring(3, 35).toLowerCase
      (tidHi, tidLo) <- TraceId.fromHex(traceIdHex)
      _              <- if (TraceId.isValid(tidHi, tidLo)) Some(()) else None
      spanIdHex       = traceparent.substring(36, 52).toLowerCase
      spanId         <- SpanId.fromHex(spanIdHex)
      _              <- if (spanId.isValid) Some(()) else None
      flagsHex        = traceparent.substring(53, 55).toLowerCase
      flags          <- TraceFlags.fromHex(flagsHex)
    } yield {
      val traceState = getter(carrier, TracestateHeader).map(_.trim).getOrElse("")
      SpanContext.create(tidHi, tidLo, spanId, flags, traceState, isRemote = true)
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
