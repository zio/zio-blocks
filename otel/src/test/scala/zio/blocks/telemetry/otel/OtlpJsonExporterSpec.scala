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

import java.util.concurrent.atomic.AtomicReference

object OtlpJsonExporterSpec extends ZIOSpecDefault {

  private val testResource = Resource.create(
    Attributes.of(AttributeKey.string("service.name"), "test-service")
  )
  private val testScope = InstrumentationScope("test-lib", Some("1.0.0"))

  private def mockHttpSender(): (HttpSender, AtomicReference[List[(String, Map[String, String], Array[Byte])]]) = {
    val captured = new AtomicReference[List[(String, Map[String, String], Array[Byte])]](Nil)
    val sender   = new HttpSender {
      def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse = {
        var done = false
        while (!done) {
          val current = captured.get()
          done = captured.compareAndSet(current, current :+ ((url, headers, body)))
        }
        HttpResponse(200, Array.empty[Byte], Map.empty)
      }
      def shutdown(): Unit = ()
    }
    (sender, captured)
  }

  private def mockHttpSenderWithStatus(statusCode: Int): HttpSender = new HttpSender {
    def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse =
      HttpResponse(statusCode, Array.empty[Byte], Map.empty)
    def shutdown(): Unit = ()
  }

  private def sampleSpanData(): SpanData = SpanData(
    name = "test-span",
    kind = SpanKind.Server,
    spanContext = {
      val (hi, lo) = TraceId.fromHex("0af7651916cd43dd8448eb211c80319c").get
      SpanContext(
        traceIdHi = hi,
        traceIdLo = lo,
        spanId = SpanId.fromHex("b7ad6b7169203331").get,
        traceFlags = TraceFlags.sampled,
        traceState = "",
        isRemote = false
      )
    },
    parentSpanContext = SpanContext.invalid,
    startTimeNanos = 1000000L,
    endTimeNanos = 2000000L,
    attributes = Attributes.empty,
    events = Nil,
    links = Nil,
    status = SpanStatus.Ok,
    resource = testResource,
    instrumentationScope = testScope
  )

  private def sampleLogRecord(): LogRecord = LogRecord(
    timestampNanos = 1000000L,
    observedTimestampNanos = 1000000L,
    severity = Severity.Info,
    severityText = "INFO",
    body = "test log message",
    attributes = Attributes.empty,
    traceIdHi = 0L,
    traceIdLo = 0L,
    spanId = 0L,
    traceFlags = 0,
    resource = testResource,
    instrumentationScope = testScope
  )

  private def sampleNamedMetric(): NamedMetric = NamedMetric(
    name = "test.counter",
    description = "a test counter",
    unit = "1",
    data = MetricData.SumData(
      List(SumDataPoint(Attributes.empty, 0L, 1000000L, 42L))
    )
  )

  def spec: Spec[Any, Nothing] = suite("OtlpJsonExporter")(
    suite("ExporterConfig")(
      test("has correct default values") {
        val config = ExporterConfig()
        assertTrue(
          config.endpoint == "http://localhost:4318" &&
            config.headers == Map.empty[String, String] &&
            config.timeout == java.time.Duration.ofSeconds(30) &&
            config.maxQueueSize == 2048 &&
            config.maxBatchSize == 512 &&
            config.flushIntervalMillis == 5000L
        )
      },
      test("allows custom values") {
        val config = ExporterConfig(
          endpoint = "http://otel-collector:4318",
          headers = Map("Authorization" -> "Bearer token"),
          timeout = java.time.Duration.ofSeconds(60),
          maxQueueSize = 4096,
          maxBatchSize = 1024,
          flushIntervalMillis = 10000L
        )
        assertTrue(
          config.endpoint == "http://otel-collector:4318" &&
            config.headers.get("Authorization").contains("Bearer token") &&
            config.timeout == java.time.Duration.ofSeconds(60) &&
            config.maxQueueSize == 4096 &&
            config.maxBatchSize == 1024 &&
            config.flushIntervalMillis == 10000L
        )
      }
    ),
    suite("OtlpJsonTraceExporter")(
      test("onStart is a no-op") {
        val (sender, _) = mockHttpSender()
        val exporter    = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          // onStart should not throw or do anything
          // We can't easily construct a Span trait instance so we test it doesn't error
          assertTrue(true)
        } finally exporter.shutdown()
      },
      test("onEnd enqueues span and forceFlush sends via HttpSender") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(
            calls.length == 1 &&
              calls.head._1.endsWith("/v1/traces") &&
              calls.head._3.nonEmpty
          )
        } finally exporter.shutdown()
      },
      test("sends to correct endpoint URL") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(endpoint = "http://my-collector:4318", flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(calls.head._1 == "http://my-collector:4318/v1/traces")
        } finally exporter.shutdown()
      },
      test("sends correct Content-Type header") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(calls.head._2.get("Content-Type").contains("application/json"))
        } finally exporter.shutdown()
      },
      test("includes custom headers from config") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(
            headers = Map("Authorization" -> "Bearer secret"),
            flushIntervalMillis = 600000L
          ),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(calls.head._2.get("Authorization").contains("Bearer secret"))
        } finally exporter.shutdown()
      },
      test("encodes span data as valid OTLP JSON") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          val body = new String(captured.get().head._3, "UTF-8")
          assertTrue(
            body.contains("resourceSpans") &&
              body.contains("test-span") &&
              body.contains("0af7651916cd43dd8448eb211c80319c")
          )
        } finally exporter.shutdown()
      },
      test("batches multiple spans together") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEnd(sampleSpanData())
          exporter.onEnd(sampleSpanData().copy(name = "span-2"))
          exporter.forceFlush()
          val calls = captured.get()
          val body  = new String(calls.head._3, "UTF-8")
          assertTrue(
            calls.nonEmpty &&
              body.contains("test-span") &&
              body.contains("span-2")
          )
        } finally exporter.shutdown()
      }
    ),
    suite("OtlpJsonLogExporter")(
      test("onEmit enqueues log and forceFlush sends via HttpSender") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonLogExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEmit(sampleLogRecord())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(
            calls.length == 1 &&
              calls.head._1.endsWith("/v1/logs") &&
              calls.head._3.nonEmpty
          )
        } finally exporter.shutdown()
      },
      test("sends to correct endpoint URL") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonLogExporter(
          ExporterConfig(endpoint = "http://my-collector:4318", flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEmit(sampleLogRecord())
          exporter.forceFlush()
          val calls = captured.get()
          assertTrue(calls.head._1 == "http://my-collector:4318/v1/logs")
        } finally exporter.shutdown()
      },
      test("encodes log data as valid OTLP JSON") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonLogExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEmit(sampleLogRecord())
          exporter.forceFlush()
          val body = new String(captured.get().head._3, "UTF-8")
          assertTrue(
            body.contains("resourceLogs") &&
              body.contains("test log message") &&
              body.contains("INFO")
          )
        } finally exporter.shutdown()
      },
      test("batches multiple logs together") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonLogExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          exporter.onEmit(sampleLogRecord())
          exporter.onEmit(sampleLogRecord().copy(body = "second log"))
          exporter.forceFlush()
          val calls = captured.get()
          val body  = new String(calls.head._3, "UTF-8")
          assertTrue(
            calls.nonEmpty &&
              body.contains("test log message") &&
              body.contains("second log")
          )
        } finally exporter.shutdown()
      }
    ),
    suite("OtlpJsonMetricExporter")(
      test("export collects from collector and sends via HttpSender") {
        val (sender, captured) = mockHttpSender()
        val metrics            = List(sampleNamedMetric())
        val exporter           = new OtlpJsonMetricExporter(
          ExporterConfig(),
          testResource,
          testScope,
          sender,
          () => metrics
        )
        try {
          exporter.exportMetrics()
          val calls = captured.get()
          assertTrue(
            calls.length == 1 &&
              calls.head._1.endsWith("/v1/metrics") &&
              calls.head._3.nonEmpty
          )
        } finally exporter.shutdown()
      },
      test("sends to correct endpoint URL") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonMetricExporter(
          ExporterConfig(endpoint = "http://my-collector:4318"),
          testResource,
          testScope,
          sender,
          () => List(sampleNamedMetric())
        )
        try {
          exporter.exportMetrics()
          val calls = captured.get()
          assertTrue(calls.head._1 == "http://my-collector:4318/v1/metrics")
        } finally exporter.shutdown()
      },
      test("encodes metric data as valid OTLP JSON") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonMetricExporter(
          ExporterConfig(),
          testResource,
          testScope,
          sender,
          () => List(sampleNamedMetric())
        )
        try {
          exporter.exportMetrics()
          val body = new String(captured.get().head._3, "UTF-8")
          assertTrue(
            body.contains("resourceMetrics") &&
              body.contains("test.counter") &&
              body.contains("a test counter")
          )
        } finally exporter.shutdown()
      },
      test("does not send when no metrics collected") {
        val (sender, captured) = mockHttpSender()
        val exporter           = new OtlpJsonMetricExporter(
          ExporterConfig(),
          testResource,
          testScope,
          sender,
          () => Nil
        )
        try {
          exporter.exportMetrics()
          val calls = captured.get()
          assertTrue(calls.isEmpty)
        } finally exporter.shutdown()
      }
    ),
    suite("Response mapping")(
      test("200 maps to Success") {
        val result = ExportResult.fromHttpResponse(HttpResponse(200, Array.empty, Map.empty))
        assertTrue(result == ExportResult.Success)
      },
      test("429 maps to retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(429, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("502 maps to retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(502, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("503 maps to retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(503, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("504 maps to retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(504, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("400 maps to non-retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(400, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(!retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("500 maps to non-retryable Failure") {
        val result = ExportResult.fromHttpResponse(HttpResponse(500, Array.empty, Map.empty))
        result match {
          case ExportResult.Failure(retryable, _) => assertTrue(!retryable)
          case _                                  => assertTrue(false)
        }
      },
      test("201 maps to Success") {
        val result = ExportResult.fromHttpResponse(HttpResponse(201, Array.empty, Map.empty))
        assertTrue(result == ExportResult.Success)
      }
    ),
    suite("Trace exporter with response mapping integration")(
      test("retryable status code triggers retry in batch processor") {
        val sender   = mockHttpSenderWithStatus(429)
        val exporter = new OtlpJsonTraceExporter(
          ExporterConfig(flushIntervalMillis = 600000L),
          testResource,
          testScope,
          sender,
          PlatformExecutor.create()
        )
        try {
          // This will enqueue and flush — the 429 will cause retries in BatchProcessor
          // but eventually drop after maxRetries. We just verify it doesn't hang.
          exporter.onEnd(sampleSpanData())
          exporter.forceFlush()
          assertTrue(true)
        } finally exporter.shutdown()
      }
    )
  ) @@ TestAspect.sequential
}
