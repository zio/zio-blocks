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

  private val testTraceIdHi = 0x0123456789abcdefL
  private val testTraceIdLo = 0xfedcba9876543210L
  private val testSpanId    = SpanId(0x0123456789abcdefL)

  private val parentSpanId = SpanId(0xfedcba9876543210L)

  private val testSpanContext = SpanContext(
    traceIdHi = testTraceIdHi,
    traceIdLo = testTraceIdLo,
    spanId = testSpanId,
    traceFlags = TraceFlags.sampled,
    traceState = "",
    isRemote = false
  )

  private val parentSpanContext = SpanContext(
    traceIdHi = testTraceIdHi,
    traceIdLo = testTraceIdLo,
    spanId = parentSpanId,
    traceFlags = TraceFlags.sampled,
    traceState = "",
    isRemote = false
  )

  private def jsonString(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  private def countOccurrences(haystack: String, needle: String): Int = {
    var count = 0
    var from  = 0
    var next  = haystack.indexOf(needle, from)
    while (next >= 0) {
      count += 1
      from = next + needle.length
      next = haystack.indexOf(needle, from)
    }
    count
  }

  private val resourceFragment =
    "\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"test-service\"}}]}"

  private val scopeFragment = "\"scope\":{\"name\":\"test-lib\",\"version\":\"1.0.0\"}"

  private def traceDoc(spansJson: String): String =
    "{\"resourceSpans\":[{" + resourceFragment + ",\"scopeSpans\":[{" + scopeFragment + ",\"spans\":[" +
      spansJson + "]}]}]}"

  private def metricsDoc(metricsJson: String): String =
    "{\"resourceMetrics\":[{" + resourceFragment + ",\"scopeMetrics\":[{" + scopeFragment + ",\"metrics\":[" +
      metricsJson + "]}]}]}"

  private def logsDoc(recordsJson: String): String =
    "{\"resourceLogs\":[{" + resourceFragment + ",\"scopeLogs\":[{" + scopeFragment + ",\"logRecords\":[" +
      recordsJson + "]}]}]}"

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

        // Exact rendered output: envelope, field order, ids, and the single
        // status object are pinned; duplicates or reordered fields fail.
        val expected = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"fedcba9876543210\",\"name\":\"test-op\",\"kind\":2," +
            "\"startTimeUnixNano\":\"1000000000\",\"endTimeUnixNano\":\"2000000000\"," +
            "\"attributes\":[],\"events\":[],\"links\":[],\"status\":{\"code\":0}}"
        )

        assertTrue(json == expected)
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

        val expected = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"0000000000000000\",\"name\":\"http-request\",\"kind\":3," +
            "\"startTimeUnixNano\":\"100\",\"endTimeUnixNano\":\"200\"," +
            "\"attributes\":[{\"key\":\"http.method\",\"value\":{\"stringValue\":\"GET\"}}," +
            "{\"key\":\"http.status_code\",\"value\":{\"intValue\":\"200\"}}]," +
            "\"events\":[],\"links\":[],\"status\":{\"code\":1}}"
        )

        assertTrue(json == expected)
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

        val expected = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"0000000000000000\",\"name\":\"failing-op\",\"kind\":1," +
            "\"startTimeUnixNano\":\"100\",\"endTimeUnixNano\":\"200\"," +
            "\"attributes\":[],\"events\":[],\"links\":[]," +
            "\"status\":{\"code\":2,\"message\":\"something went wrong\"}}"
        )

        assertTrue(json == expected)
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

        val expected = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"0000000000000000\",\"name\":\"with-events\",\"kind\":1," +
            "\"startTimeUnixNano\":\"100\",\"endTimeUnixNano\":\"200\",\"attributes\":[]," +
            "\"events\":[{\"name\":\"exception\",\"timeUnixNano\":\"150\"," +
            "\"attributes\":[{\"key\":\"exception.message\",\"value\":{\"stringValue\":\"boom\"}}]}]," +
            "\"links\":[],\"status\":{\"code\":0}}"
        )

        assertTrue(json == expected)
      },
      test("encodes span with links") {
        val linkedContext = SpanContext(
          traceIdHi = 0xaabbccddeeff0011L,
          traceIdLo = 0x2233445566778899L,
          spanId = SpanId(0xaabbccddeeff0011L),
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

        val expected = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"0000000000000000\",\"name\":\"with-links\",\"kind\":1," +
            "\"startTimeUnixNano\":\"100\",\"endTimeUnixNano\":\"200\",\"attributes\":[],\"events\":[]," +
            "\"links\":[{\"traceId\":\"aabbccddeeff00112233445566778899\"," +
            "\"spanId\":\"aabbccddeeff0011\",\"attributes\":[]}],\"status\":{\"code\":0}}"
        )

        assertTrue(json == expected)
      },
      test("encodes empty spans list") {
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq.empty, testResource, testScope))

        assertTrue(json == traceDoc(""))
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

        def expectedKindDoc(kind: Int): String = traceDoc(
          "{\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\"," +
            "\"parentSpanId\":\"0000000000000000\",\"name\":\"kind-test\",\"kind\":" + kind + "," +
            "\"startTimeUnixNano\":\"0\",\"endTimeUnixNano\":\"0\"," +
            "\"attributes\":[],\"events\":[],\"links\":[],\"status\":{\"code\":0}}"
        )

        assertTrue(
          internalJson == expectedKindDoc(1),
          serverJson == expectedKindDoc(2),
          clientJson == expectedKindDoc(3),
          producerJson == expectedKindDoc(4),
          consumerJson == expectedKindDoc(5)
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

        val expected = metricsDoc(
          "{\"name\":\"request.count\",\"description\":\"\",\"unit\":\"1\"," +
            "\"sum\":{\"dataPoints\":[{\"attributes\":[],\"startTimeUnixNano\":\"0\"," +
            "\"timeUnixNano\":\"1000000000\",\"asInt\":\"42\"}],\"isMonotonic\":true}}"
        )

        assertTrue(json == expected)
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

        val expected = metricsDoc(
          "{\"name\":\"latency\",\"description\":\"request latency\",\"unit\":\"ms\"," +
            "\"histogram\":{\"dataPoints\":[{\"attributes\":[],\"startTimeUnixNano\":\"0\"," +
            "\"timeUnixNano\":\"1000000000\",\"count\":\"10\",\"sum\":55.5,\"min\":1.0,\"max\":10.0," +
            "\"bucketCounts\":[\"2\",\"3\",\"5\"],\"explicitBounds\":[5.0,10.0]}]}}"
        )

        assertTrue(json == expected)
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

        val expected = metricsDoc(
          "{\"name\":\"temperature\",\"description\":\"current temp\",\"unit\":\"celsius\"," +
            "\"gauge\":{\"dataPoints\":[{\"attributes\":[],\"timeUnixNano\":\"1000000000\",\"asDouble\":73.5}]}}"
        )

        assertTrue(json == expected)
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

        val expected = metricsDoc(
          "{\"name\":\"req\",\"description\":\"\",\"unit\":\"\"," +
            "\"sum\":{\"dataPoints\":[" +
            "{\"attributes\":[{\"key\":\"region\",\"value\":{\"stringValue\":\"us-east-1\"}}]," +
            "\"startTimeUnixNano\":\"0\",\"timeUnixNano\":\"1000000000\",\"asInt\":\"100\"}]," +
            "\"isMonotonic\":true}}"
        )

        assertTrue(json == expected)
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
          traceIdHi = testTraceIdHi,
          traceIdLo = testTraceIdLo,
          spanId = testSpanId.value,
          traceFlags = TraceFlags.sampled.byte,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        val expected = logsDoc(
          "{\"timeUnixNano\":\"1000000000\",\"observedTimeUnixNano\":\"1000000001\"," +
            "\"severityNumber\":9,\"severityText\":\"INFO\"," +
            "\"body\":{\"stringValue\":\"User logged in\"},\"attributes\":[]," +
            "\"traceId\":\"0123456789abcdeffedcba9876543210\",\"spanId\":\"0123456789abcdef\",\"flags\":1}"
        )

        assertTrue(json == expected)
      },
      test("encodes log record without trace correlation") {
        val log = LogRecord(
          timestampNanos = 5000L,
          observedTimestampNanos = 5001L,
          severity = Severity.Error,
          severityText = "ERROR",
          body = "disk full",
          attributes = Attributes.empty,
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        val expected = logsDoc(
          "{\"timeUnixNano\":\"5000\",\"observedTimeUnixNano\":\"5001\"," +
            "\"severityNumber\":17,\"severityText\":\"ERROR\"," +
            "\"body\":{\"stringValue\":\"disk full\"},\"attributes\":[]," +
            "\"traceId\":\"\",\"spanId\":\"\",\"flags\":0}"
        )

        assertTrue(json == expected)
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
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0,
          resource = testResource,
          instrumentationScope = testScope
        )

        val json = jsonString(OtlpJsonEncoder.encodeLogs(Seq(log), testResource, testScope))

        val expected = logsDoc(
          "{\"timeUnixNano\":\"1000\",\"observedTimeUnixNano\":\"1001\"," +
            "\"severityNumber\":13,\"severityText\":\"WARN\"," +
            "\"body\":{\"stringValue\":\"slow request\"}," +
            "\"attributes\":[{\"key\":\"user.id\",\"value\":{\"stringValue\":\"u123\"}}," +
            "{\"key\":\"request.latency\",\"value\":{\"doubleValue\":42.5}}]," +
            "\"traceId\":\"\",\"spanId\":\"\",\"flags\":0}"
        )

        assertTrue(json == expected)
      }
    ),
    suite("attribute encoding")(
      test("encodes string attribute") {
        val attrs = Attributes.builder.put("k", "hello").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"hello\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("encodes long attribute as quoted string") {
        val attrs = Attributes.builder.put("k", 42L).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"intValue\":\"42\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("encodes double attribute") {
        val attrs = Attributes.builder.put("k", 3.14).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"doubleValue\":3.14}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("encodes boolean attribute") {
        val attrs = Attributes.builder.put("k", true).build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"boolValue\":true}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("encodes string seq attribute as arrayValue") {
        val attrs = Attributes.of(AttributeKey.stringSeq("tags"), Seq("a", "b"))
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains(
            "\"attributes\":[{\"key\":\"tags\",\"value\":{\"arrayValue\":{\"values\":[{\"stringValue\":\"a\"},{\"stringValue\":\"b\"}]}}}]"
          ),
          countOccurrences(json, "\"key\":\"tags\"") == 1,
          countOccurrences(json, "\"stringValue\":\"a\"") == 1,
          countOccurrences(json, "\"stringValue\":\"b\"") == 1
        )
      }
    ),
    suite("JSON string escaping")(
      test("escapes double quotes") {
        val attrs = Attributes.builder.put("k", "say \"hello\"").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"say \\\"hello\\\"\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("escapes backslash") {
        val attrs = Attributes.builder.put("k", "path\\to\\file").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"path\\\\to\\\\file\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("escapes newline and tab") {
        val attrs = Attributes.builder.put("k", "line1\nline2\ttab").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"line1\\nline2\\ttab\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("escapes control characters") {
        val attrs = Attributes.builder.put("k", "null char").build
        val span  = makeSimpleSpan(attributes = attrs)
        val json  = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"null\\u0000char\"}}]"),
          countOccurrences(json, "\"key\":\"k\"") == 1
        )
      },
      test("escapes user-controlled attribute keys") {
        val trickyKey = "ke\"y\\with\ncontrols\u0001end"
        val attrs     = Attributes.builder.put(trickyKey, "v").build
        val span      = makeSimpleSpan(attributes = attrs)
        val json      = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("{\"key\":\"ke\\\"y\\\\with\\ncontrols\\u0001end\",\"value\":{\"stringValue\":\"v\"}}"),
          countOccurrences(json, "\"key\":\"ke\\\"y\\\\with\\ncontrols\\u0001end\"") == 1,
          countOccurrences(json, "\"stringValue\":\"v\"") == 1,
          !json.contains(trickyKey)
        )
      }
    ),
    suite("empty collections")(
      test("empty attributes produce empty array") {
        val span = makeSimpleSpan(attributes = Attributes.empty)
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"attributes\":[]"),
          countOccurrences(json, "\"attributes\":[]") == 1
        )
      },
      test("empty events produce empty array") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"events\":[]"),
          countOccurrences(json, "\"events\":[]") == 1
        )
      },
      test("empty links produce empty array") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains("\"links\":[]"),
          countOccurrences(json, "\"links\":[]") == 1
        )
      }
    ),
    suite("resource and scope encoding")(
      test("resource attributes are encoded") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains(resourceFragment),
          countOccurrences(json, "\"key\":\"service.name\"") == 1,
          countOccurrences(json, "\"stringValue\":\"test-service\"") == 1
        )
      },
      test("scope name and version are encoded") {
        val span = makeSimpleSpan()
        val json = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, testScope))

        assertTrue(
          json.contains(scopeFragment),
          countOccurrences(json, "\"name\":\"test-lib\"") == 1,
          countOccurrences(json, "\"version\":\"1.0.0\"") == 1
        )
      },
      test("scope without version omits version field") {
        val scopeNoVersion = InstrumentationScope(name = "no-version-lib")
        val span           = makeSimpleSpan()
        val json           = jsonString(OtlpJsonEncoder.encodeTraces(Seq(span), testResource, scopeNoVersion))

        assertTrue(
          json.contains("\"scope\":{\"name\":\"no-version-lib\"}"),
          countOccurrences(json, "\"scope\":{\"name\":\"no-version-lib\"}") == 1,
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
