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

import zio.test._

object B3PropagatorSpec extends ZIOSpecDefault {

  private type Headers = Map[String, String]

  private val getter: (Headers, String) => Option[String] = (carrier, key) => carrier.get(key)

  private val setter: (Headers, String, String) => Headers = (carrier, key, value) => carrier + (key -> value)

  private val traceIdHex = "4bf92f3577b34da6a3ce929d0e0e4736"
  private val spanIdHex  = "00f067aa0ba902b7"

  def spec = suite("B3Propagator")(
    suite("B3SinglePropagator")(
      singleFieldsSuite,
      singleExtractSuite,
      singleInjectSuite,
      singleRoundtripSuite
    ),
    suite("B3MultiPropagator")(
      multiFieldsSuite,
      multiExtractSuite,
      multiInjectSuite,
      multiRoundtripSuite
    )
  )

  private val singleFieldsSuite = suite("fields")(
    test("returns b3") {
      assertTrue(B3Propagator.single.fields == Seq("b3"))
    }
  )

  private val singleExtractSuite = suite("extract")(
    test("parses valid full format with sampling and parentSpanId") {
      val headers = Map("b3" -> s"$traceIdHex-$spanIdHex-1-0000000000000001")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == traceIdHex &&
          result.get.spanId.toHex == spanIdHex &&
          result.get.traceFlags.isSampled &&
          result.get.isRemote
      )
    },
    test("parses valid format without sampling") {
      val headers = Map("b3" -> s"$traceIdHex-$spanIdHex")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == traceIdHex &&
          result.get.spanId.toHex == spanIdHex &&
          !result.get.traceFlags.isSampled &&
          result.get.isRemote
      )
    },
    test("parses valid format with sampling but without parentSpanId") {
      val headers = Map("b3" -> s"$traceIdHex-$spanIdHex-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceFlags.isSampled
      )
    },
    test("parses 16-char traceId by padding to 32 chars") {
      val shortTraceId = "a3ce929d0e0e4736"
      val headers      = Map("b3" -> s"$shortTraceId-$spanIdHex-1")
      val result       = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == "0000000000000000a3ce929d0e0e4736"
      )
    },
    test("parses deny/drop single value '0'") {
      val headers = Map("b3" -> "0")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("parses debug flag 'd' as sampled") {
      val headers = Map("b3" -> s"$traceIdHex-$spanIdHex-d")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceFlags.isSampled
      )
    },
    test("parses sampling '0' as not sampled") {
      val headers = Map("b3" -> s"$traceIdHex-$spanIdHex-0")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          !result.get.traceFlags.isSampled
      )
    },
    test("returns None for missing b3 header") {
      val headers = Map.empty[String, String]
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for empty b3 header") {
      val headers = Map("b3" -> "")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for malformed traceId (non-hex)") {
      val headers = Map("b3" -> s"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-$spanIdHex-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for malformed spanId (non-hex)") {
      val headers = Map("b3" -> s"$traceIdHex-zzzzzzzzzzzzzzzz-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for invalid traceId length") {
      val headers = Map("b3" -> s"4bf92f35-$spanIdHex-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for invalid spanId length") {
      val headers = Map("b3" -> s"$traceIdHex-00f067aa-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for all-zero traceId") {
      val headers = Map("b3" -> s"00000000000000000000000000000000-$spanIdHex-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for all-zero spanId") {
      val headers = Map("b3" -> s"$traceIdHex-0000000000000000-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("handles uppercase hex") {
      val headers = Map("b3" -> s"${traceIdHex.toUpperCase}-${spanIdHex.toUpperCase}-1")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == traceIdHex &&
          result.get.spanId.toHex == spanIdHex
      )
    },
    test("returns None for whitespace-only value") {
      val headers = Map("b3" -> "   ")
      val result  = B3Propagator.single.extract(headers, getter)
      assertTrue(result.isEmpty)
    }
  )

  private val singleInjectSuite = suite("inject")(
    test("formats correctly with sampled flag") {
      val traceId = TraceId.fromHex(traceIdHex).get
      val spanId  = SpanId.fromHex(spanIdHex).get
      val ctx     = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)

      val headers = B3Propagator.single.inject(ctx, Map.empty[String, String], setter)
      assertTrue(headers("b3") == s"$traceIdHex-$spanIdHex-1")
    },
    test("formats correctly with unsampled flag") {
      val traceId = TraceId.fromHex(traceIdHex).get
      val spanId  = SpanId.fromHex(spanIdHex).get
      val ctx     = SpanContext.create(traceId, spanId, TraceFlags.none, "", isRemote = false)

      val headers = B3Propagator.single.inject(ctx, Map.empty[String, String], setter)
      assertTrue(headers("b3") == s"$traceIdHex-$spanIdHex-0")
    },
    test("does not inject invalid span context") {
      val headers = B3Propagator.single.inject(SpanContext.invalid, Map.empty[String, String], setter)
      assertTrue(headers.isEmpty)
    },
    test("preserves existing carrier entries") {
      val traceId         = TraceId.fromHex(traceIdHex).get
      val spanId          = SpanId.fromHex(spanIdHex).get
      val ctx             = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)
      val existingHeaders = Map("x-custom" -> "keep-me")

      val headers = B3Propagator.single.inject(ctx, existingHeaders, setter)
      assertTrue(
        headers("x-custom") == "keep-me" &&
          headers.contains("b3")
      )
    }
  )

  private val singleRoundtripSuite = suite("roundtrip")(
    test("inject then extract produces equivalent SpanContext") {
      val traceId  = TraceId.fromHex(traceIdHex).get
      val spanId   = SpanId.fromHex(spanIdHex).get
      val original = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)

      val headers  = B3Propagator.single.inject(original, Map.empty[String, String], setter)
      val restored = B3Propagator.single.extract(headers, getter)

      assertTrue(
        restored.isDefined &&
          restored.get.traceId == original.traceId &&
          restored.get.spanId == original.spanId &&
          restored.get.traceFlags == original.traceFlags &&
          restored.get.isRemote
      )
    },
    test("roundtrip with unsampled") {
      val traceId  = TraceId.fromHex(traceIdHex).get
      val spanId   = SpanId.fromHex(spanIdHex).get
      val original = SpanContext.create(traceId, spanId, TraceFlags.none, "", isRemote = false)

      val headers  = B3Propagator.single.inject(original, Map.empty[String, String], setter)
      val restored = B3Propagator.single.extract(headers, getter)

      assertTrue(
        restored.isDefined &&
          restored.get.traceId == original.traceId &&
          restored.get.spanId == original.spanId &&
          !restored.get.traceFlags.isSampled
      )
    }
  )

  private val multiFieldsSuite = suite("fields")(
    test("returns all B3 multi-header field names") {
      assertTrue(
        B3Propagator.multi.fields == Seq(
          "X-B3-TraceId",
          "X-B3-SpanId",
          "X-B3-Sampled",
          "X-B3-ParentSpanId",
          "X-B3-Flags"
        )
      )
    }
  )

  private val multiExtractSuite = suite("extract")(
    test("parses all headers present") {
      val headers = Map(
        "X-B3-TraceId"      -> traceIdHex,
        "X-B3-SpanId"       -> spanIdHex,
        "X-B3-Sampled"      -> "1",
        "X-B3-ParentSpanId" -> "0000000000000001"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == traceIdHex &&
          result.get.spanId.toHex == spanIdHex &&
          result.get.traceFlags.isSampled &&
          result.get.isRemote
      )
    },
    test("parses with missing optional Sampled header (defaults to not sampled)") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> spanIdHex
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          !result.get.traceFlags.isSampled
      )
    },
    test("parses with Sampled=0 as not sampled") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "0"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          !result.get.traceFlags.isSampled
      )
    },
    test("parses Flags=1 as sampled (debug implies sampled)") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Flags"   -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceFlags.isSampled
      )
    },
    test("Flags=1 overrides Sampled=0") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "0",
        "X-B3-Flags"   -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceFlags.isSampled
      )
    },
    test("returns None for missing TraceId") {
      val headers = Map("X-B3-SpanId" -> spanIdHex, "X-B3-Sampled" -> "1")
      val result  = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for missing SpanId") {
      val headers = Map("X-B3-TraceId" -> traceIdHex, "X-B3-Sampled" -> "1")
      val result  = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for malformed TraceId") {
      val headers = Map(
        "X-B3-TraceId" -> "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for malformed SpanId") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> "zzzzzzzzzzzzzzzz",
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for all-zero traceId") {
      val headers = Map(
        "X-B3-TraceId" -> "00000000000000000000000000000000",
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("returns None for all-zero spanId") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex,
        "X-B3-SpanId"  -> "0000000000000000",
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    },
    test("parses 16-char traceId by padding to 32 chars") {
      val shortTraceId = "a3ce929d0e0e4736"
      val headers      = Map(
        "X-B3-TraceId" -> shortTraceId,
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == "0000000000000000a3ce929d0e0e4736"
      )
    },
    test("handles uppercase hex") {
      val headers = Map(
        "X-B3-TraceId" -> traceIdHex.toUpperCase,
        "X-B3-SpanId"  -> spanIdHex.toUpperCase,
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(
        result.isDefined &&
          result.get.traceId.toHex == traceIdHex &&
          result.get.spanId.toHex == spanIdHex
      )
    },
    test("returns None for empty TraceId") {
      val headers = Map(
        "X-B3-TraceId" -> "",
        "X-B3-SpanId"  -> spanIdHex,
        "X-B3-Sampled" -> "1"
      )
      val result = B3Propagator.multi.extract(headers, getter)
      assertTrue(result.isEmpty)
    }
  )

  private val multiInjectSuite = suite("inject")(
    test("sets correct headers with sampled") {
      val traceId = TraceId.fromHex(traceIdHex).get
      val spanId  = SpanId.fromHex(spanIdHex).get
      val ctx     = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)

      val headers = B3Propagator.multi.inject(ctx, Map.empty[String, String], setter)
      assertTrue(
        headers("X-B3-TraceId") == traceIdHex &&
          headers("X-B3-SpanId") == spanIdHex &&
          headers("X-B3-Sampled") == "1"
      )
    },
    test("sets correct headers with unsampled") {
      val traceId = TraceId.fromHex(traceIdHex).get
      val spanId  = SpanId.fromHex(spanIdHex).get
      val ctx     = SpanContext.create(traceId, spanId, TraceFlags.none, "", isRemote = false)

      val headers = B3Propagator.multi.inject(ctx, Map.empty[String, String], setter)
      assertTrue(
        headers("X-B3-TraceId") == traceIdHex &&
          headers("X-B3-SpanId") == spanIdHex &&
          headers("X-B3-Sampled") == "0"
      )
    },
    test("does not inject invalid span context") {
      val headers = B3Propagator.multi.inject(SpanContext.invalid, Map.empty[String, String], setter)
      assertTrue(headers.isEmpty)
    },
    test("preserves existing carrier entries") {
      val traceId         = TraceId.fromHex(traceIdHex).get
      val spanId          = SpanId.fromHex(spanIdHex).get
      val ctx             = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)
      val existingHeaders = Map("x-custom" -> "keep-me")

      val headers = B3Propagator.multi.inject(ctx, existingHeaders, setter)
      assertTrue(
        headers("x-custom") == "keep-me" &&
          headers.contains("X-B3-TraceId")
      )
    }
  )

  private val multiRoundtripSuite = suite("roundtrip")(
    test("inject then extract produces equivalent SpanContext") {
      val traceId  = TraceId.fromHex(traceIdHex).get
      val spanId   = SpanId.fromHex(spanIdHex).get
      val original = SpanContext.create(traceId, spanId, TraceFlags.sampled, "", isRemote = false)

      val headers  = B3Propagator.multi.inject(original, Map.empty[String, String], setter)
      val restored = B3Propagator.multi.extract(headers, getter)

      assertTrue(
        restored.isDefined &&
          restored.get.traceId == original.traceId &&
          restored.get.spanId == original.spanId &&
          restored.get.traceFlags == original.traceFlags &&
          restored.get.isRemote
      )
    },
    test("roundtrip with unsampled") {
      val traceId  = TraceId.fromHex(traceIdHex).get
      val spanId   = SpanId.fromHex(spanIdHex).get
      val original = SpanContext.create(traceId, spanId, TraceFlags.none, "", isRemote = false)

      val headers  = B3Propagator.multi.inject(original, Map.empty[String, String], setter)
      val restored = B3Propagator.multi.extract(headers, getter)

      assertTrue(
        restored.isDefined &&
          restored.get.traceId == original.traceId &&
          restored.get.spanId == original.spanId &&
          !restored.get.traceFlags.isSampled
      )
    }
  )
}
