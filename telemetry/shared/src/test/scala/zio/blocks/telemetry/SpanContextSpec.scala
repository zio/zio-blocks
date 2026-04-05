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

import zio.test._

object SpanContextSpec extends ZIOSpecDefault {

  private def randomCtx(
    traceFlags: TraceFlags = TraceFlags.none,
    traceState: String = "",
    isRemote: Boolean = false
  ): SpanContext = {
    val (hi, lo) = TraceId.random()
    SpanContext.create(hi, lo, SpanId.random, traceFlags, traceState, isRemote)
  }

  def spec = suite("SpanContext")(
    suite("invalid")(
      test("has zero trace ID") {
        assertTrue(SpanContext.invalid.traceIdHi == 0L && SpanContext.invalid.traceIdLo == 0L)
      },
      test("has zero span ID") {
        assertTrue(SpanContext.invalid.spanId == SpanId.invalid)
      },
      test("has zero trace flags") {
        assertTrue(SpanContext.invalid.traceFlags.byte == 0.toByte)
      },
      test("has empty trace state") {
        assertTrue(SpanContext.invalid.traceState == "")
      },
      test("is not remote") {
        assertTrue(!SpanContext.invalid.isRemote)
      },
      test("isValid returns false") {
        assertTrue(!SpanContext.invalid.isValid)
      }
    ),
    suite("create")(
      test("creates span context with provided values") {
        val (tidHi, tidLo) = TraceId.random()
        val spanId         = SpanId.random
        val traceFlags     = TraceFlags.sampled
        val traceState     = "key1=value1"
        val isRemote       = true

        val ctx = SpanContext.create(tidHi, tidLo, spanId, traceFlags, traceState, isRemote)

        assertTrue(
          ctx.traceIdHi == tidHi &&
            ctx.traceIdLo == tidLo &&
            ctx.spanId == spanId &&
            ctx.traceFlags == traceFlags &&
            ctx.traceState == traceState &&
            ctx.isRemote == isRemote
        )
      },
      test("creates valid span context when trace ID and span ID are valid") {
        val ctx = randomCtx(traceFlags = TraceFlags.sampled)
        assertTrue(ctx.isValid)
      }
    ),
    suite("isValid")(
      test("returns true when both trace ID and span ID are valid") {
        val ctx = randomCtx()
        assertTrue(ctx.isValid)
      },
      test("returns false when trace ID is invalid") {
        val ctx = SpanContext.create(0L, 0L, SpanId.random, TraceFlags.none, "", false)
        assertTrue(!ctx.isValid)
      },
      test("returns false when span ID is invalid") {
        val ctx = {
          val (hi, lo) = TraceId.random()
          SpanContext.create(hi, lo, SpanId.invalid, TraceFlags.none, "", false)
        }
        assertTrue(!ctx.isValid)
      },
      test("returns false when both IDs are invalid") {
        assertTrue(!SpanContext.invalid.isValid)
      }
    ),
    suite("isSampled")(
      test("returns true when sampled flag is set") {
        val ctx = randomCtx(traceFlags = TraceFlags.sampled)
        assertTrue(ctx.isSampled)
      },
      test("returns false when sampled flag is not set") {
        val ctx = randomCtx(traceFlags = TraceFlags.none)
        assertTrue(!ctx.isSampled)
      }
    ),
    suite("traceState")(
      test("stores and retrieves trace state") {
        val state = "key1=value1,key2=value2"
        val ctx   = randomCtx(traceState = state)
        assertTrue(ctx.traceState == state)
      },
      test("handles empty trace state") {
        val ctx = randomCtx(traceState = "")
        assertTrue(ctx.traceState == "")
      }
    ),
    suite("isRemote")(
      test("identifies remote span contexts") {
        val ctx = randomCtx(isRemote = true)
        assertTrue(ctx.isRemote)
      },
      test("identifies local span contexts") {
        val ctx = randomCtx(isRemote = false)
        assertTrue(!ctx.isRemote)
      }
    ),
    suite("equality")(
      test("equal contexts compare equal") {
        val (tidHi, tidLo) = TraceId.random()
        val spanId         = SpanId.random
        val traceFlags     = TraceFlags.sampled
        val ctx1           = SpanContext.create(tidHi, tidLo, spanId, traceFlags, "state", true)
        val ctx2           = SpanContext.create(tidHi, tidLo, spanId, traceFlags, "state", true)
        assertTrue(ctx1 == ctx2)
      },
      test("different contexts compare not equal (different trace ID)") {
        val ctx1 = randomCtx()
        val ctx2 = randomCtx()
        assertTrue(ctx1 != ctx2)
      }
    ),
    suite("immutability")(
      test("is a final case class") {
        val ctx1 = randomCtx()
        val ctx2 = ctx1
        assertTrue(ctx1 eq ctx2)
      }
    )
  )
}
