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

object OtlpJsonEncoderSpec extends ZIOSpecDefault {

  private val testResource = Resource(
    Attributes.builder
      .put("service.name", "test-service")
      .build
  )

  private val testScope = InstrumentationScope(
    name = "test-lib",
    version = Some("1.0.0")
  )

  private val testTraceId = TraceId(hi = 0x0123456789abcdefL, lo = 0xfedcba9876543210L)
  private val testSpanId  = SpanId(value = 0x0123456789abcdefL)

  private val parentSpanId = SpanId(value = 0xfedcba9876543210L)

  private val testSpanContext = SpanContext(
    traceId = testTraceId,
    spanId = testSpanId,
    traceFlags = TraceFlags.sampled,
    traceState = "",
    isRemote = false
  )

  private val parentSpanContext = SpanContext(
    traceId = testTraceId,
    spanId = parentSpanId,
    traceFlags = TraceFlags.sampled,
    traceState = "",
    isRemote = false
  )

  private def jsonString(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  def spec = suite("OtlpJsonEncoder")(
    suite("encodeTraces")(
      test("encodes single span with correct OTLP structure") {
        val span = SpanData(
          name = "test-op",
          kind = SpanKind.Server,
          spanContext = testSpanContext,
          parentSpanContext = parentSpanContext,
          startTimeNanos = 1000000000L,
          endTimeNanos = 2000000000L,
          attributes = Attributes.empty,
          events = Nil,
          links = Nil,
          status = SpanStatus.Unset,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"resourceSpans\""),
          json.contains("\"scopeSpans\""),
          json.contains("\"spans\""),
          json.contains("\"name\":\"test-op\""),
          json.contains("\"kind\":2"),
          json.contains("\"traceId\":\"0123456789abcdeffedcba9876543210\""),
          json.contains("\"spanId\":\"0123456789abcdef\""),
          json.contains("\"parentSpanId\":\"fedcba9876543210\""),
          json.contains("\"startTimeUnixNano\":\"1000000000\""),
          json.contains("\"endTimeUnixNano\":\"2000000000\""),
          json.contains("\"status\":{\"code\":0}")
        )
      },
      test("encodes span with attributes") {
        val attrs = Attributes.builder
          .put("http.method", "GET")
          .put("http.status_code", 200L)
          .build

        val span = SpanData(
          name = "http-request",
          kind = SpanKind.Client,
          spanContext = testSpanContext,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 100L,
          endTimeNanos = 200L,
          attributes = attrs,
          events = Nil,
          links = Nil,
          status = SpanStatus.Ok,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"key\":\"http.method\""),
          json.contains("\"stringValue\":\"GET\""),
          json.contains("\"key\":\"http.status_code\""),
          json.contains("\"intValue\":\"200\""),
          json.contains("\"status\":{\"code\":1}")
        )
      },
      test("encodes span with error status and description") {
        val span = SpanData(
          name = "failing-op",
          kind = SpanKind.Internal,
          spanContext = testSpanContext,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 100L,
          endTimeNanos = 200L,
          attributes = Attributes.empty,
          events = Nil,
          links = Nil,
          status = SpanStatus.Error("something went wrong"),
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"status\":{\"code\":2,\"message\":\"something went wrong\"}")
        )
      },
      test("encodes span with events") {
        val event = SpanEvent(
          name = "exception",
          timestampNanos = 150L,
          attributes = Attributes.builder.put("exception.message", "boom").build
        )

        val span = SpanData(
          name = "with-events",
          kind = SpanKind.Internal,
          spanContext = testSpanContext,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 100L,
          endTimeNanos = 200L,
          attributes = Attributes.empty,
          events = List(event),
          links = Nil,
          status = SpanStatus.Unset,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"events\":[{"),
          json.contains("\"name\":\"exception\""),
          json.contains("\"timeUnixNano\":\"150\""),
          json.contains("\"key\":\"exception.message\"")
        )
      },
      test("encodes span with links") {
        val linkedContext = SpanContext(
          traceId = TraceId(hi = 0xaabbccddeeff0011L, lo = 0x2233445566778899L),
          spanId = SpanId(value = 0xaabbccddeeff0011L),
          traceFlags = TraceFlags.sampled,
          traceState = "",
          isRemote = true
        )
        val link = SpanLink(
          spanContext = linkedContext,
          attributes = Attributes.empty
        )

        val span = SpanData(
          name = "with-links",
          kind = SpanKind.Internal,
          spanContext = testSpanContext,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 100L,
          endTimeNanos = 200L,
          attributes = Attributes.empty,
          events = Nil,
          links = List(link),
          status = SpanStatus.Unset,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"links\":[{"),
          json.contains("\"traceId\":\"aabbccddeeff00112233445566778899\""),
          json.contains("\"spanId\":\"aabbccddeeff0011\"")
        )
      },
      test("encodes empty spans list") {
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq.empty, testResource, testScope))

        assertTrue(
          json.contains("\"resourceSpans\":[{"),
          json.contains("\"spans\":[]")
        )
      },
      test("all SpanKind values map to correct OTLP integers") {
        def makeSpan(kind: SpanKind): SpanData = SpanData(
          name = "kind-test",
          kind = kind,
          spanContext = testSpanContext,
          parentSpanContext = SpanContext.invalid,
          startTimeNanos = 0L,
          endTimeNanos = 0L,
          attributes = Attributes.empty,
          events = Nil,
          links = Nil,
          status = SpanStatus.Unset,
          resource = testResource,
          instrumentationScope = testScope
        )

        val internalJson =
          jsonString(OtlpJsonEncoder.encodeTraces(Seq(makeSpan(SpanKind.Internal)), testResource, testScope))
        val serverJson =
          jsonString(OtlpJsonEncoder.encodeTraces(Seq(makeSpan(SpanKind.Server)), testResource, testScope))
        val clientJson =
          jsonString(OtlpJsonEncoder.encodeTraces(Seq(makeSpan(SpanKind.Client)), testResource, testScope))
        val producerJson =
          jsonString(OtlpJsonEncoder.encodeTraces(Seq(makeSpan(SpanKind.Producer)), testResource, testScope))
        val consumerJson =
          jsonString(OtlpJsonEncoder.encodeTraces(Seq(makeSpan(SpanKind.Consumer)), testResource, testScope))

        assertTrue(
          internalJson.contains("\"kind\":1"),
          serverJson.contains("\"kind\":2"),
          clientJson.contains("\"kind\":3"),
          producerJson.contains("\"kind\":4"),
          consumerJson.contains("\"kind\":5")
        )
      }
    ),
    suite("encodeMetrics")(
      test("encodes sum metric (counter) with correct structure") {
        val point  = SumDataPoint(Attributes.empty, 0L, 1000000000L, 42L)
        val metric = MetricData.SumData(List(point))

        val json = jsonString(
          OtlpJsonEncoder.encodeMetrics(
            Seq(OtlpJsonEncoder.NamedMetric("request.count", "", "1", metric)),
            testResource,
            testScope
          )
        )

        assertTrue(
          json.contains("\"resourceMetrics\""),
          json.contains("\"scopeMetrics\""),
          json.contains("\"metrics\""),
          json.contains("\"name\":\"request.count\""),
          json.contains("\"sum\":{"),
          json.contains("\"asInt\":\"42\""),
          json.contains("\"timeUnixNano\":\"1000000000\""),
          json.contains("\"isMonotonic\":true")
        )
      },
      test("encodes histogram metric") {
        val point = HistogramDataPoint(
          attributes = Attributes.empty,
          startTimeNanos = 0L,
          timeNanos = 1000000000L,
          count = 10L,
          sum = 55.5,
          min = 1.0,
          max = 10.0,
          bucketCounts = Array(2L, 3L, 5L),
          boundaries = Array(5.0, 10.0)
        )
        val metric = MetricData.HistogramData(List(point))

        val json = jsonString(
          OtlpJsonEncoder.encodeMetrics(
            Seq(OtlpJsonEncoder.NamedMetric("latency", "request latency", "ms", metric)),
            testResource,
            testScope
          )
        )

        assertTrue(
          json.contains("\"name\":\"latency\""),
          json.contains("\"histogram\":{"),
          json.contains("\"count\":\"10\""),
          json.contains("\"sum\":55.5"),
          json.contains("\"min\":1.0"),
          json.contains("\"max\":10.0"),
          json.contains("\"bucketCounts\":[\"2\",\"3\",\"5\"]"),
          json.contains("\"explicitBounds\":[5.0,10.0]")
        )
      },
      test("encodes gauge metric") {
        val point  = GaugeDataPoint(Attributes.empty, 1000000000L, 73.5)
        val metric = MetricData.GaugeData(List(point))

        val json = jsonString(
          OtlpJsonEncoder.encodeMetrics(
            Seq(OtlpJsonEncoder.NamedMetric("temperature", "current temp", "celsius", metric)),
            testResource,
            testScope
          )
        )

        assertTrue(
          json.contains("\"name\":\"temperature\""),
          json.contains("\"gauge\":{"),
          json.contains("\"asDouble\":73.5")
        )
      },
      test("encodes metric data points with attributes") {
        val attrs  = Attributes.builder.put("region", "us-east-1").build
        val point  = SumDataPoint(attrs, 0L, 1000000000L, 100L)
        val metric = MetricData.SumData(List(point))

        val json = jsonString(
          OtlpJsonEncoder.encodeMetrics(
            Seq(OtlpJsonEncoder.NamedMetric("req", "", "", metric)),
            testResource,
            testScope
          )
        )

        assertTrue(
          json.contains("\"key\":\"region\""),
          json.contains("\"stringValue\":\"us-east-1\"")
        )
      }
    ),
    suite("encodeLogs")(
      test("encodes log record with correct OTLP structure") {
        val log = LogRecord(
          timestampNanos = 1000000000L,
          observedTimestampNanos = 1000000001L,
          severity = Severity.Info,
          severityText = "INFO",
          body = "User logged in",
          attributes = Attributes.empty,
          traceId = Some(testTraceId),
          spanId = Some(testSpanId),
          traceFlags = Some(TraceFlags.sampled),
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        assertTrue(
          json.contains("\"resourceLogs\""),
          json.contains("\"scopeLogs\""),
          json.contains("\"logRecords\""),
          json.contains("\"timeUnixNano\":\"1000000000\""),
          json.contains("\"severityNumber\":9"),
          json.contains("\"severityText\":\"INFO\""),
          json.contains("\"body\":{\"stringValue\":\"User logged in\"}"),
          json.contains("\"traceId\":\"0123456789abcdeffedcba9876543210\""),
          json.contains("\"spanId\":\"0123456789abcdef\"")
        )
      },
      test("encodes log record without trace correlation") {
        val log = LogRecord(
          timestampNanos = 5000L,
          observedTimestampNanos = 5001L,
          severity = Severity.Error,
          severityText = "ERROR",
          body = "disk full",
          attributes = Attributes.empty,
          traceId = None,
          spanId = None,
          traceFlags = None,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        assertTrue(
          json.contains("\"severityNumber\":17"),
          json.contains("\"severityText\":\"ERROR\""),
          json.contains("\"traceId\":\"\""),
          json.contains("\"spanId\":\"\"")
        )
      },
      test("encodes log record with attributes") {
        val attrs = Attributes.builder
          .put("user.id", "u123")
          .put("request.latency", 42.5)
          .build

        val log = LogRecord(
          timestampNanos = 1000L,
          observedTimestampNanos = 1001L,
          severity = Severity.Warn,
          severityText = "WARN",
          body = "slow request",
          attributes = attrs,
          traceId = None,
          spanId = None,
          traceFlags = None,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        assertTrue(
          json.contains("\"key\":\"user.id\""),
          json.contains("\"stringValue\":\"u123\""),
          json.contains("\"key\":\"request.latency\""),
          json.contains("\"doubleValue\":42.5")
        )
      }
    ),
    suite("attribute encoding")(
      test("encodes string attribute") {
        val attrs = Attributes.builder.put("k", "hello").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"stringValue\":\"hello\""))
      },
      test("encodes long attribute as quoted string") {
        val attrs = Attributes.builder.put("k", 42L).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"intValue\":\"42\""))
      },
      test("encodes double attribute") {
        val attrs = Attributes.builder.put("k", 3.14).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"doubleValue\":3.14"))
      },
      test("encodes boolean attribute") {
        val attrs = Attributes.builder.put("k", true).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"boolValue\":true"))
      },
      test("encodes string seq attribute as arrayValue") {
        val attrs = Attributes.of(AttributeKey.stringSeq("tags"), Seq("a", "b"))
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"arrayValue\":{\"values\":["),
          json.contains("\"stringValue\":\"a\""),
          json.contains("\"stringValue\":\"b\"")
        )
      }
    ),
    suite("JSON string escaping")(
      test("escapes double quotes") {
        val attrs = Attributes.builder.put("k", "say \"hello\"").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("say \\\"hello\\\""))
      },
      test("escapes backslash") {
        val attrs = Attributes.builder.put("k", "path\\to\\file").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("path\\\\to\\\\file"))
      },
      test("escapes newline and tab") {
        val attrs = Attributes.builder.put("k", "line1\nline2\ttab").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("line1\\nline2\\ttab")
        )
      },
      test("escapes control characters") {
        val attrs = Attributes.builder.put("k", "null\u0000char").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\\u0000"))
      }
    ),
    suite("empty collections")(
      test("empty attributes produce empty array") {
        val span = makeSimpleSpan(attributes = Attributes.empty)
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"attributes\":[]"))
      },
      test("empty events produce empty array") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"events\":[]"))
      },
      test("empty links produce empty array") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(json.contains("\"links\":[]"))
      }
    ),
    suite("resource and scope encoding")(
      test("resource attributes are encoded") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"resource\":{\"attributes\":["),
          json.contains("\"key\":\"service.name\""),
          json.contains("\"stringValue\":\"test-service\"")
        )
      },
      test("scope name and version are encoded") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"scope\":{\"name\":\"test-lib\",\"version\":\"1.0.0\""),
          json.contains("\"name\":\"test-lib\""),
          json.contains("\"version\":\"1.0.0\"")
        )
      },
      test("scope without version omits version field") {
        val scopeNoVersion = InstrumentationScope(name = "no-version-lib")
        val span           = makeSimpleSpan()
        val json           = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, scopeNoVersion))

        assertTrue(
          json.contains("\"scope\":{\"name\":\"no-version-lib\"}"),
          !json.contains("\"version\"")
        )
      }
    )
  )

  private def makeSimpleSpan(
    attributes: Attributes = Attributes.empty,
    events: List[SpanEvent] = Nil,
    links: List[SpanLink] = Nil
  ): SpanData = SpanData(
    name = "simple",
    kind = SpanKind.Internal,
    spanContext = testSpanContext,
    parentSpanContext = SpanContext.invalid,
    startTimeNanos = 100L,
    endTimeNanos = 200L,
    attributes = attributes,
    events = events,
    links = links,
    status = SpanStatus.Unset,
    resource = testResource,
    instrumentationScope = testScope
  )
}
