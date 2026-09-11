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
   *
   * Note: the debug (`"d"`) sampling marker sets the sampled flag, but
   * debug-ness itself is not propagated — [[zio.blocks.telemetry.SpanContext]]
   * has no debug field, so debug degrades to sampled.
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
   * Sampling marker for the B3 single-header third segment: `"1"` (accept) and
   * `"d"` (debug, degrades to sampled — see [[single]]) enable sampling;
   * anything else, including absence, means unsampled.
   */
  private def samplingFlags(sampling: String): TraceFlags =
    sampling match {
      case "1" | "d" => TraceFlags.sampled
      case _         => TraceFlags.none
    }

  private def isTruthySampled(value: String): Boolean =
    value == "1" || value.equalsIgnoreCase("true")

  /**
   * Normalizes a trace ID hex string. Accepts 16 or 32 hex characters. 16-char
   * IDs are left-padded with zeros to 32 characters.
   */
  private[otel] def normalizeTraceId(hex: String): Option[(Long, Long)] = {
    val lower  = hex.toLowerCase(java.util.Locale.ROOT)
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
      getter(carrier, B3Header).flatMap { raw =>
        val value = raw.trim
        if (value.isEmpty) None
        else {
          value match {
            case "0" =>
              Some(SpanContext.create(0L, 0L, SpanId.invalid, TraceFlags.none, traceState = "", isRemote = true))
            case "1" | "d" =>
              Some(SpanContext.create(0L, 0L, SpanId.invalid, TraceFlags.sampled, traceState = "", isRemote = true))
            case _ =>
              // Hand-split on dash indices (no per-extract regex). Accepts
              // {traceId}-{spanId} with optional -{sampling} and
              // -{parentSpanId}; extra segments are ignored.
              val firstDash = value.indexOf('-')
              if (firstDash < 0) None
              else {
                val secondDash   = value.indexOf('-', firstDash + 1)
                val spanEnd      = if (secondDash < 0) value.length else secondDash
                val samplingDash = if (secondDash < 0) -1 else value.indexOf('-', secondDash + 1)
                val samplingEnd  = if (samplingDash < 0) value.length else samplingDash
                val sampling     =
                  if (secondDash < 0) ""
                  else value.substring(secondDash + 1, samplingEnd)
                for {
                  (tidHi, tidLo) <- normalizeTraceId(value.substring(0, firstDash))
                  if TraceId.isValid(tidHi, tidLo)
                  spanId <- SpanId.fromHex(value.substring(firstDash + 1, spanEnd).toLowerCase(java.util.Locale.ROOT))
                  if spanId.isValid
                } yield {
                  SpanContext.create(tidHi, tidLo, spanId, samplingFlags(sampling), traceState = "", isRemote = true)
                }
              }
          }
        }
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
    private val TraceIdHeader = "X-B3-TraceId"
    private val SpanIdHeader  = "X-B3-SpanId"
    private val SampledHeader = "X-B3-Sampled"
    private val FlagsHeader   = "X-B3-Flags"

    override val fields: Seq[String] = Seq(TraceIdHeader, SpanIdHeader, SampledHeader, FlagsHeader)

    override def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext] =
      for {
        traceIdRaw     <- getter(carrier, TraceIdHeader)
        (tidHi, tidLo) <- normalizeTraceId(traceIdRaw.trim)
        _              <- if (TraceId.isValid(tidHi, tidLo)) Some(()) else None
        spanIdRaw      <- getter(carrier, SpanIdHeader)
        spanId         <- SpanId.fromHex(spanIdRaw.trim.toLowerCase(java.util.Locale.ROOT))
        _              <- if (spanId.isValid) Some(()) else None
      } yield {
        // X-B3-Flags "1" means debug, which implies sampled (debug-ness
        // itself is not propagated — see [[single]]). X-B3-Sampled accepts
        // both "1" and the spec's "true" form (case-insensitive).
        val debug   = getter(carrier, FlagsHeader).exists(_.trim == "1")
        val sampled =
          debug || getter(carrier, SampledHeader).exists(v => isTruthySampled(v.trim))
        val flags = if (sampled) TraceFlags.sampled else TraceFlags.none
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
