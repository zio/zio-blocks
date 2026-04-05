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

object SamplerSpec extends ZIOSpecDefault {

  private val validTraceIdHi = 1L
  private val validTraceIdLo = 2L
  private val validSpanId    = SpanId(42L)

  private val sampledParent = SpanContext(
    traceIdHi = validTraceIdHi,
    traceIdLo = validTraceIdLo,
    spanId = validSpanId,
    traceFlags = TraceFlags.sampled,
    traceState = "",
    isRemote = false
  )

  private val unsampledParent = SpanContext(
    traceIdHi = validTraceIdHi,
    traceIdLo = validTraceIdLo,
    spanId = validSpanId,
    traceFlags = TraceFlags.none,
    traceState = "",
    isRemote = false
  )

  def spec = suite("Sampler")(
    suite("AlwaysOnSampler")(
      test("always returns RecordAndSample") {
        val result = AlwaysOnSampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.RecordAndSample)
      },
      test("returns RecordAndSample regardless of parent context") {
        val result = AlwaysOnSampler.shouldSample(
          parentContext = Some(unsampledParent),
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Server,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.RecordAndSample)
      },
      test("returns empty attributes and trace state") {
        val result = AlwaysOnSampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.attributes.isEmpty && result.traceState == "")
      },
      test("has correct description") {
        assertTrue(AlwaysOnSampler.description == "AlwaysOnSampler")
      }
    ),
    suite("AlwaysOffSampler")(
      test("always returns Drop") {
        val result = AlwaysOffSampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.Drop)
      },
      test("returns Drop regardless of parent context") {
        val result = AlwaysOffSampler.shouldSample(
          parentContext = Some(sampledParent),
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Client,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.Drop)
      },
      test("returns empty attributes and trace state") {
        val result = AlwaysOffSampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "test-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.attributes.isEmpty && result.traceState == "")
      },
      test("has correct description") {
        assertTrue(AlwaysOffSampler.description == "AlwaysOffSampler")
      }
    ),
    suite("ParentBasedSampler")(
      test("delegates to root sampler when no parent context") {
        val sampler = ParentBasedSampler(root = AlwaysOnSampler)
        val result  = sampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "root-span",
          kind = SpanKind.Server,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.RecordAndSample)
      },
      test("delegates to root sampler (AlwaysOff) when no parent context") {
        val sampler = ParentBasedSampler(root = AlwaysOffSampler)
        val result  = sampler.shouldSample(
          parentContext = None,
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "root-span",
          kind = SpanKind.Server,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.Drop)
      },
      test("returns RecordAndSample when parent is sampled") {
        val sampler = ParentBasedSampler(root = AlwaysOffSampler)
        val result  = sampler.shouldSample(
          parentContext = Some(sampledParent),
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "child-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.RecordAndSample)
      },
      test("returns Drop when parent is not sampled") {
        val sampler = ParentBasedSampler(root = AlwaysOnSampler)
        val result  = sampler.shouldSample(
          parentContext = Some(unsampledParent),
          traceIdHi = validTraceIdHi,
          traceIdLo = validTraceIdLo,
          name = "child-span",
          kind = SpanKind.Internal,
          attributes = Attributes.empty,
          links = Seq.empty
        )
        assertTrue(result.decision == SamplingDecision.Drop)
      },
      test("has correct description") {
        val sampler = ParentBasedSampler(root = AlwaysOnSampler)
        assertTrue(sampler.description == "ParentBasedSampler(root=AlwaysOnSampler)")
      }
    ),
    suite("SamplingResult")(
      test("carries attributes") {
        val attrs  = Attributes.of(AttributeKey.string("sampler.key"), "value")
        val result = SamplingResult(
          decision = SamplingDecision.RecordAndSample,
          attributes = attrs,
          traceState = ""
        )
        assertTrue(result.attributes.get(AttributeKey.string("sampler.key")).contains("value"))
      },
      test("carries trace state") {
        val result = SamplingResult(
          decision = SamplingDecision.RecordOnly,
          attributes = Attributes.empty,
          traceState = "vendor1=value1"
        )
        assertTrue(result.traceState == "vendor1=value1")
      }
    ),
    suite("SamplingDecision")(
      test("Drop is accessible") {
        val _: SamplingDecision = SamplingDecision.Drop
        assertTrue(true)
      },
      test("RecordOnly is accessible") {
        val _: SamplingDecision = SamplingDecision.RecordOnly
        assertTrue(true)
      },
      test("RecordAndSample is accessible") {
        val _: SamplingDecision = SamplingDecision.RecordAndSample
        assertTrue(true)
      }
    )
  )
}
