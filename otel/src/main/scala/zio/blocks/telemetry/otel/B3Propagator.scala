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
 * B3 propagation format (Zipkin's trace context standard).
 *
 * Provides both single-header (`b3`) and multi-header (`X-B3-*`) variants.
 *
 * @see
 *   https://github.com/openzipkin/b3-propagation
 */
object B3Propagator {

  /**
   * Returns a B3 single-header propagator.
   *
   * Single-header format: `{traceId}-{spanId}-{sampling}-{parentSpanId}`
   */
  val single: Propagator = B3SinglePropagator

  /**
   * Returns a B3 multi-header propagator.
   *
   * Uses `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-Sampled`, `X-B3-ParentSpanId`,
   * and `X-B3-Flags` headers.
   */
  val multi: Propagator = B3MultiPropagator

  /**
   * Normalizes a trace ID hex string. Accepts 16 or 32 hex characters. 16-char
   * IDs are left-padded with zeros to 32 characters.
   */
  private[otel] def normalizeTraceId(hex: String): Option[(Long, Long)] = {
    val lower  = hex.toLowerCase
    val padded =
      if (lower.length == 16) "0000000000000000" + lower
      else if (lower.length == 32) lower
      else return None
    TraceId.fromHex(padded)
  }

  private object B3SinglePropagator extends Propagator {
    private val B3Header = "b3"

    override val fields: Seq[String] = Seq(B3Header)

    override def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext] =
      for {
        raw            <- getter(carrier, B3Header)
        value           = raw.trim
        _              <- if (value.isEmpty || value == "0") None else Some(())
        parts           = value.split('-')
        _              <- if (parts.length >= 2) Some(()) else None
        (tidHi, tidLo) <- normalizeTraceId(parts(0))
        _              <- if (TraceId.isValid(tidHi, tidLo)) Some(()) else None
        spanId         <- SpanId.fromHex(parts(1).toLowerCase)
        _              <- if (spanId.isValid) Some(()) else None
      } yield {
        val flags = if (parts.length >= 3) {
          parts(2) match {
            case "1" | "d" => TraceFlags.sampled
            case "0"       => TraceFlags.none
            case _         => TraceFlags.none
          }
        } else TraceFlags.none
        SpanContext.create(tidHi, tidLo, spanId, flags, traceState = "", isRemote = true)
      }

    override def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C =
      if (!spanContext.isValid) carrier
      else {
        val sampling = if (spanContext.traceFlags.isSampled) "1" else "0"
        val value    = s"${spanContext.traceIdHex}-${spanContext.spanId.toHex}-$sampling"
        setter(carrier, B3Header, value)
      }
  }

  private object B3MultiPropagator extends Propagator {
    private val TraceIdHeader      = "X-B3-TraceId"
    private val SpanIdHeader       = "X-B3-SpanId"
    private val SampledHeader      = "X-B3-Sampled"
    private val ParentSpanIdHeader = "X-B3-ParentSpanId"
    private val FlagsHeader        = "X-B3-Flags"

    override val fields: Seq[String] = Seq(TraceIdHeader, SpanIdHeader, SampledHeader, ParentSpanIdHeader, FlagsHeader)

    override def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext] =
      for {
        traceIdRaw     <- getter(carrier, TraceIdHeader)
        (tidHi, tidLo) <- normalizeTraceId(traceIdRaw.trim)
        _              <- if (TraceId.isValid(tidHi, tidLo)) Some(()) else None
        spanIdRaw      <- getter(carrier, SpanIdHeader)
        spanId         <- SpanId.fromHex(spanIdRaw.trim.toLowerCase)
        _              <- if (spanId.isValid) Some(()) else None
      } yield {
        val debug   = getter(carrier, FlagsHeader).exists(_.trim == "1")
        val sampled = debug || getter(carrier, SampledHeader).exists(_.trim == "1")
        val flags   = if (sampled) TraceFlags.sampled else TraceFlags.none
        SpanContext.create(tidHi, tidLo, spanId, flags, traceState = "", isRemote = true)
      }

    override def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C =
      if (!spanContext.isValid) carrier
      else {
        val sampling = if (spanContext.traceFlags.isSampled) "1" else "0"
        val c1       = setter(carrier, TraceIdHeader, spanContext.traceIdHex)
        val c2       = setter(c1, SpanIdHeader, spanContext.spanId.toHex)
        setter(c2, SampledHeader, sampling)
      }
  }
}
