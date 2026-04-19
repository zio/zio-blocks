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

import scala.collection.mutable.ArrayBuffer

object LoggerSpec extends ZIOSpecDefault {

  private class TestLogProcessor extends LogRecordProcessor {
    val emitted: ArrayBuffer[LogRecord] = ArrayBuffer.empty
    var shutdownCalled: Boolean         = false
    var forceFlushCalled: Boolean       = false

    def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
    def shutdown(): Unit                   = shutdownCalled = true
    def forceFlush(): Unit                 = forceFlushCalled = true
  }

  private class TestSpanProcessor extends SpanProcessor {
    val started: ArrayBuffer[Span]   = ArrayBuffer.empty
    val ended: ArrayBuffer[SpanData] = ArrayBuffer.empty
    var shutdownCalled: Boolean      = false
    var forceFlushCalled: Boolean    = false

    def onStart(span: Span): Unit       = started += span
    def onEnd(spanData: SpanData): Unit = ended += spanData
    def shutdown(): Unit                = shutdownCalled = true
    def forceFlush(): Unit              = forceFlushCalled = true
  }

  def spec = suite("Logger")(
    suite("LogRecordProcessor")(
      test("noop does nothing") {
        val noop = LogRecordProcessor.noop
        noop.onEmit(LogRecord.builder.build)
        noop.shutdown()
        noop.forceFlush()
        assertTrue(true)
      }
    ),
    suite("LoggerProvider.builder")(
      test("builds with defaults") {
        val provider = LoggerProvider.builder.build()
        val logger   = provider.get("test-lib")
        assertTrue(logger != null)
      },
      test("setResource configures resource") {
        val resource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "my-service")
        )
        val provider = LoggerProvider.builder
          .setResource(resource)
          .build()
        val logger = provider.get("test-lib", "1.0.0")
        assertTrue(logger != null)
      },
      test("addLogRecordProcessor registers processor") {
        val processor = new TestLogProcessor
        val provider  = LoggerProvider.builder
          .addLogRecordProcessor(processor)
          .build()
        val logger = provider.get("test-lib")
        logger.info("hello")
        assertTrue(processor.emitted.nonEmpty)
      },
      test("shutdown calls shutdown on all processors") {
        val p1       = new TestLogProcessor
        val p2       = new TestLogProcessor
        val provider = LoggerProvider.builder
          .addLogRecordProcessor(p1)
          .addLogRecordProcessor(p2)
          .build()
        provider.shutdown()
        assertTrue(p1.shutdownCalled && p2.shutdownCalled)
      }
    ),
    suite("Logger.emit")(
      test("sends log record to all processors") {
        val p1     = new TestLogProcessor
        val p2     = new TestLogProcessor
        val logger = makeLogger(Seq(p1, p2))

        val record = LogRecord.builder
          .setSeverity(Severity.Info)
          .setBody("test message")
          .build

        logger.emit(record)

        assertTrue(
          p1.emitted.size == 1 &&
            p2.emitted.size == 1 &&
            p1.emitted.head.body.value == "test message" &&
            p2.emitted.head.body.value == "test message"
        )
      }
    ),
    suite("convenience methods")(
      test("info creates LogRecord with correct severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.info("test info message")

        assertTrue(
          processor.emitted.size == 1 &&
            processor.emitted.head.severity == Severity.Info &&
            processor.emitted.head.severityText == "INFO" &&
            processor.emitted.head.body.value == "test info message"
        )
      },
      test("trace creates LogRecord with Trace severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.trace("trace message")

        assertTrue(
          processor.emitted.head.severity == Severity.Trace &&
            processor.emitted.head.severityText == "TRACE"
        )
      },
      test("debug creates LogRecord with Debug severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.debug("debug message")

        assertTrue(
          processor.emitted.head.severity == Severity.Debug &&
            processor.emitted.head.severityText == "DEBUG"
        )
      },
      test("warn creates LogRecord with Warn severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.warn("warn message")

        assertTrue(
          processor.emitted.head.severity == Severity.Warn &&
            processor.emitted.head.severityText == "WARN"
        )
      },
      test("error creates LogRecord with Error severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.error("error message")

        assertTrue(
          processor.emitted.head.severity == Severity.Error &&
            processor.emitted.head.severityText == "ERROR"
        )
      },
      test("fatal creates LogRecord with Fatal severity") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.fatal("fatal message")

        assertTrue(
          processor.emitted.head.severity == Severity.Fatal &&
            processor.emitted.head.severityText == "FATAL"
        )
      },
      test("convenience methods auto-set timestamp") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        val before = System.nanoTime()
        logger.info("timed message")
        val after = System.nanoTime()

        val record = processor.emitted.head
        assertTrue(
          record.timestampNanos >= before &&
            record.timestampNanos <= after
        )
      },
      test("convenience methods accept attributes") {
        val processor = new TestLogProcessor
        val logger    = makeLogger(Seq(processor))

        logger.info(
          "message with attrs",
          "key1" -> AttributeValue.StringValue("value1"),
          "key2" -> AttributeValue.LongValue(42L)
        )

        val record = processor.emitted.head
        val attrs  = record.attributes.toMap
        assertTrue(
          attrs("key1") == AttributeValue.StringValue("value1") &&
            attrs("key2") == AttributeValue.LongValue(42L)
        )
      },
      test("LogRecord includes resource and instrumentation scope") {
        val resource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "test-svc")
        )
        val processor = new TestLogProcessor
        val provider  = LoggerProvider.builder
          .setResource(resource)
          .addLogRecordProcessor(processor)
          .build()
        val logger = provider.get("my-logger", "2.0.0")

        logger.info("scoped message")

        val record = processor.emitted.head
        assertTrue(
          record.resource == resource &&
            record.instrumentationScope.name == "my-logger" &&
            record.instrumentationScope.version.contains("2.0.0")
        )
      }
    ),
    suite("trace correlation")(
      test("logger auto-injects traceId and spanId when inside a span") {
        val logProcessor  = new TestLogProcessor
        val spanProcessor = new TestSpanProcessor

        // Shared context storage for trace correlation
        val sharedContextStorage = ContextStorage.create[Option[SpanContext]](None)

        // Create Tracer directly with shared context storage
        val scope  = InstrumentationScope(name = "test-tracer")
        val tracer = new Tracer(scope, Resource.default, AlwaysOnSampler, Seq(spanProcessor), sharedContextStorage)

        // Create LoggerProvider with same context storage
        val loggerProvider = LoggerProvider.builder
          .addLogRecordProcessor(logProcessor)
          .setContextStorage(sharedContextStorage)
          .build()
        val logger = loggerProvider.get("test-logger")

        tracer.span("test-span") { span =>
          logger.info("inside span")
          val record = logProcessor.emitted.head
          assertTrue(
            record.traceIdHi == span.spanContext.traceIdHi && record.traceIdLo == span.spanContext.traceIdLo && record.hasTraceId &&
              record.spanId == span.spanContext.spanId.value &&
              record.traceFlags == span.spanContext.traceFlags.byte
          )
        }
      },
      test("logger has no trace context outside of span") {
        val logProcessor = new TestLogProcessor
        val logger       = makeLogger(Seq(logProcessor))

        logger.info("outside span")

        val record = logProcessor.emitted.head
        assertTrue(
          !record.hasTraceId &&
            !record.hasSpanId &&
            record.traceFlags == 0
        )
      }
    ),
    suite("multiple processors")(
      test("all processors receive each log record") {
        val p1     = new TestLogProcessor
        val p2     = new TestLogProcessor
        val p3     = new TestLogProcessor
        val logger = makeLogger(Seq(p1, p2, p3))

        logger.info("broadcast message")

        assertTrue(
          p1.emitted.size == 1 &&
            p2.emitted.size == 1 &&
            p3.emitted.size == 1
        )
      }
    )
  )

  private def makeLogger(
    processors: Seq[LogRecordProcessor],
    contextStorage: ContextStorage[Option[SpanContext]] = ContextStorage.create[Option[SpanContext]](None)
  ): Logger = {
    val builder = LoggerProvider.builder
      .setContextStorage(contextStorage)
    processors.foreach(p => builder.addLogRecordProcessor(p))
    builder.build().get("test-logger")
  }
}
