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
 * Reads and writes trace context in Zipkin's B3 headers. Use it to exchange
 * traces with services that speak B3 rather than W3C Trace Context:
 * [[B3Propagator.single]] puts everything in one `b3` header,
 * [[B3Propagator.multi]] uses one `X-B3-*` header per field.
 *
 * Both write the 32-digit trace id, the span id and the sampling decision (`1`
 * or `0`), and nothing else: no parent span id, no debug flag and no trace
 * state. Both read a 16- or 32-digit trace id (a 16-digit one is padded with
 * zeros on the left) and ids in upper or lower case, and mark the context
 * `isRemote`. Like [[W3CTraceContextPropagator]], they accept a `+` at the
 * start of an id, a known bug.
 *
 * To call a B3 service from a request that arrived with W3C headers:
 *
 * {{{
 * val get = (headers: Map[String, String], name: String) => headers.get(name)
 * val set = (headers: Map[String, String], name: String, value: String) => headers + (name -> value)
 *
 * val incoming = Map("traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
 * val parent   = W3CTraceContextPropagator.extract(incoming, get)
 *
 * parent.map(ctx => B3Propagator.single.inject(ctx, Map.empty[String, String], set))
 * // Some(Map(b3 -> 4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1))
 * parent.map(ctx => B3Propagator.multi.inject(ctx, Map.empty[String, String], set))
 * // Some(Map(X-B3-TraceId -> 4bf92f3577b34da6a3ce929d0e0e4736, X-B3-SpanId -> 00f067aa0ba902b7, X-B3-Sampled -> 1))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 * @see
 *   [[https://github.com/openzipkin/b3-propagation B3 Propagation]]
 */
object B3Propagator {

  /**
   * The single-header format,
   * `b3: {traceId}-{spanId}-{sampling}-{parentSpanId}`, where the last two
   * fields are optional. `inject` writes `{traceId}-{spanId}-1`, or `-0` when
   * the context is not sampled. `extract` takes `1` or `d` (debug) as sampled
   * and any other value, or no third field, as not sampled, and ignores the
   * parent span id.
   *
   * A header that holds only a sampling decision (`b3: 0`, `1` or `d`) gives
   * `Some` of an invalid context with zero ids instead of `None`, a known bug:
   * check `SpanContext#isValid` before you use the result as a parent.
   *
   * {{{
   * val get = (headers: Map[String, String], name: String) => headers.get(name)
   *
   * val zipkin = Map("b3" -> "a3ce929d0e0e4736-00f067aa0ba902b7-1-05e3ac9a4f6e3b90")
   * B3Propagator.single.extract(zipkin, get).map(ctx => (ctx.traceIdHex, ctx.isSampled))
   * // Some((0000000000000000a3ce929d0e0e4736,true))
   *
   * B3Propagator.single.extract(Map("b3" -> "0"), get).map(_.isValid) // Some(false), a known bug
   * }}}
   */
  val single: Propagator = B3SinglePropagator

  /**
   * The multi-header format. Injects the `X-B3-TraceId`, `X-B3-SpanId` and
   * `X-B3-Sampled` headers. Extraction also reads `X-B3-Flags`: a value of `1`
   * (debug) samples the span. Only `1` counts as sampled in `X-B3-Sampled`, so
   * the legacy value `true` does not. `X-B3-ParentSpanId` is neither written
   * nor read.
   *
   * The getter is asked for the names in this exact case, so over HTTP headers
   * it should ignore case:
   *
   * {{{
   * val incoming = Map(
   *   "x-b3-traceid" -> "4bf92f3577b34da6a3ce929d0e0e4736",
   *   "x-b3-spanid"  -> "00f067aa0ba902b7",
   *   "x-b3-sampled" -> "0",
   *   "x-b3-flags"   -> "1"
   * )
   * val ignoringCase = (headers: Map[String, String], name: String) => headers.get(name.toLowerCase)
   * B3Propagator.multi.extract(incoming, ignoringCase).map(_.isSampled) // Some(true)
   * B3Propagator.multi.extract[Map[String, String]](incoming, (headers, name) => headers.get(name)) // None
   * }}}
   */
  val multi: Propagator = B3MultiPropagator

  /**
   * Lower-cases a 16- or 32-character trace id, pads a 16-character one with
   * zeros on the left, and parses it.
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
              val parts = value.split('-')
              if (parts.length < 2) None
              else {
                for {
                  (tidHi, tidLo) <- normalizeTraceId(parts(0))
                  if TraceId.isValid(tidHi, tidLo)
                  spanId <- SpanId.fromHex(parts(1).toLowerCase(java.util.Locale.ROOT))
                  if spanId.isValid
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
