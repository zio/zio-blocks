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
import zio.test._

object W3CTraceContextSpec extends ZIOSpecDefault {

  private val propagator: Propagator = W3CTraceContextPropagator

  private type Headers = Map[String, String]

  private val getter: (Headers, String) => Option[String] = (carrier, key) => carrier.get(key)

  private val setter: (Headers, String, String) => Headers = (carrier, key, value) => carrier + (key -> value)

  def spec = suite("W3CTraceContextPropagator")(
    suite("fields")(
      test("returns traceparent and tracestate") {
        assertTrue(propagator.fields == Seq("traceparent", "tracestate"))
      }
    ),
    suite("extract")(
      test("parses valid traceparent with sampled flag") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceIdHex == "4bf92f3577b34da6a3ce929d0e0e4736" &&
            result.get.spanId.toHex == "00f067aa0ba902b7" &&
            result.get.traceFlags.isSampled &&
            result.get.isRemote
        )
      },
      test("parses valid traceparent without sampled flag") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            !result.get.traceFlags.isSampled
        )
      },
      test("parses traceparent with tracestate") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
          "tracestate"  -> "congo=t61rcWkgMzE,rojo=00f067aa0ba902b7"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceState == "congo=t61rcWkgMzE,rojo=00f067aa0ba902b7"
        )
      },
      test("sets empty traceState when tracestate header is absent") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceState == ""
        )
      },
      test("rejects missing traceparent header") {
        val headers = Map.empty[String, String]
        val result  = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects unknown version") {
        val headers = Map(
          "traceparent" -> "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects all-zero trace ID") {
        val headers = Map(
          "traceparent" -> "00-00000000000000000000000000000000-00f067aa0ba902b7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects all-zero span ID") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects wrong total length") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-0"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects non-hex characters in trace ID") {
        val headers = Map(
          "traceparent" -> "00-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-00f067aa0ba902b7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects non-hex characters in span ID") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-zzzzzzzzzzzzzzzz-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects non-hex characters in flags") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-zz"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("rejects missing dashes") {
        val headers = Map(
          "traceparent" -> "004bf92f3577b34da6a3ce929d0e0e473600f067aa0ba902b701"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      },
      test("handles uppercase hex in traceparent") {
        val headers = Map(
          "traceparent" -> "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceIdHex == "4bf92f3577b34da6a3ce929d0e0e4736" &&
            result.get.spanId.toHex == "00f067aa0ba902b7"
        )
      },
      test("parses trace flags with non-sampled bits") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-ff"
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceFlags.isSampled &&
            result.get.traceFlags.toHex == "ff"
        )
      },
      test("rejects empty traceparent value") {
        val headers = Map("traceparent" -> "")
        val result  = propagator.extract(headers, getter)
        assertTrue(result.isEmpty)
      }
    ),
    suite("inject")(
      test("formats traceparent correctly") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val ctx            = SpanContext.create(tidHi, tidLo, spanId, TraceFlags.sampled, "", isRemote = false)

        val headers = propagator.inject(ctx, Map.empty[String, String], setter)
        assertTrue(headers("traceparent") == "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
      },
      test("injects tracestate when non-empty") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val ctx            =
          SpanContext.create(
            tidHi,
            tidLo,
            spanId,
            TraceFlags.sampled,
            "congo=t61rcWkgMzE,rojo=00f067aa0ba902b7",
            isRemote = false
          )

        val headers = propagator.inject(ctx, Map.empty[String, String], setter)
        assertTrue(
          headers("traceparent") == "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" &&
            headers("tracestate") == "congo=t61rcWkgMzE,rojo=00f067aa0ba902b7"
        )
      },
      test("does not inject tracestate when empty") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val ctx            = SpanContext.create(tidHi, tidLo, spanId, TraceFlags.sampled, "", isRemote = false)

        val headers = propagator.inject(ctx, Map.empty[String, String], setter)
        assertTrue(
          headers.contains("traceparent") &&
            !headers.contains("tracestate")
        )
      },
      test("formats unsampled flags as 00") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val ctx            = SpanContext.create(tidHi, tidLo, spanId, TraceFlags.none, "", isRemote = false)

        val headers = propagator.inject(ctx, Map.empty[String, String], setter)
        assertTrue(headers("traceparent") == "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00")
      },
      test("does not inject invalid span context") {
        val headers = propagator.inject(SpanContext.invalid, Map.empty[String, String], setter)
        assertTrue(headers.isEmpty)
      }
    ),
    suite("roundtrip")(
      test("inject then extract produces equivalent SpanContext") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val original       =
          SpanContext.create(tidHi, tidLo, spanId, TraceFlags.sampled, "congo=t61rcWkgMzE", isRemote = false)

        val headers  = propagator.inject(original, Map.empty[String, String], setter)
        val restored = propagator.extract(headers, getter)

        assertTrue(
          restored.isDefined &&
            restored.get.traceIdHi == original.traceIdHi && restored.get.traceIdLo == original.traceIdLo &&
            restored.get.spanId == original.spanId &&
            restored.get.traceFlags == original.traceFlags &&
            restored.get.traceState == original.traceState &&
            restored.get.isRemote
        )
      },
      test("roundtrip with empty tracestate") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val original       = SpanContext.create(tidHi, tidLo, spanId, TraceFlags.none, "", isRemote = false)

        val headers  = propagator.inject(original, Map.empty[String, String], setter)
        val restored = propagator.extract(headers, getter)

        assertTrue(
          restored.isDefined &&
            restored.get.traceIdHi == original.traceIdHi && restored.get.traceIdLo == original.traceIdLo &&
            restored.get.spanId == original.spanId &&
            restored.get.traceFlags == original.traceFlags &&
            restored.get.traceState == ""
        )
      },
      test("roundtrip with multiple tracestate entries") {
        val (tidHi, tidLo) = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId         = SpanId.fromHex("00f067aa0ba902b7").get
        val state          = "vendor1=value1,vendor2=value2,vendor3=value3"
        val original       =
          SpanContext.create(tidHi, tidLo, spanId, TraceFlags.sampled, state, isRemote = true)

        val headers  = propagator.inject(original, Map.empty[String, String], setter)
        val restored = propagator.extract(headers, getter)

        assertTrue(
          restored.isDefined &&
            restored.get.traceState == state
        )
      }
    ),
    suite("edge cases")(
      test("extract with whitespace-trimmed traceparent") {
        val headers = Map(
          "traceparent" -> "  00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01  "
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceIdHex == "4bf92f3577b34da6a3ce929d0e0e4736"
        )
      },
      test("extract with whitespace-trimmed tracestate") {
        val headers = Map(
          "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
          "tracestate"  -> "  congo=t61rcWkgMzE  "
        )
        val result = propagator.extract(headers, getter)
        assertTrue(
          result.isDefined &&
            result.get.traceState == "congo=t61rcWkgMzE"
        )
      },
      test("inject preserves existing carrier entries") {
        val (tidHi, tidLo)  = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736").get
        val spanId          = SpanId.fromHex("00f067aa0ba902b7").get
        val ctx             = SpanContext.create(tidHi, tidLo, spanId, TraceFlags.sampled, "", isRemote = false)
        val existingHeaders = Map("x-custom" -> "keep-me")

        val headers = propagator.inject(ctx, existingHeaders, setter)
        assertTrue(
          headers("x-custom") == "keep-me" &&
            headers.contains("traceparent")
        )
      }
    )
  )
}
