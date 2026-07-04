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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.ArrayBuffer

object AdditionalCoverageSpec extends ZIOSpecDefault {

  private final class TestLogProcessor extends LogRecordProcessor {
    val emitted: ArrayBuffer[LogRecord] = ArrayBuffer.empty
    val shutdowns: AtomicInteger        = new AtomicInteger(0)
    val flushes: AtomicInteger          = new AtomicInteger(0)

    def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
    def shutdown(): Unit                   = shutdowns.incrementAndGet()
    def forceFlush(): Unit                 = flushes.incrementAndGet()
  }

  private final class TestSpanProcessor extends SpanProcessor {
    val started: ArrayBuffer[Span]   = ArrayBuffer.empty
    val ended: ArrayBuffer[SpanData] = ArrayBuffer.empty
    val shutdowns: AtomicInteger     = new AtomicInteger(0)
    val forceFlushes: AtomicInteger  = new AtomicInteger(0)

    def onStart(span: Span): Unit       = started += span
    def onEnd(spanData: SpanData): Unit = ended += spanData
    def shutdown(): Unit                = shutdowns.incrementAndGet()
    def forceFlush(): Unit              = forceFlushes.incrementAndGet()
  }

  private final class RecordingLogWriter extends LogWriter {
    val writes: ArrayBuffer[String] = ArrayBuffer.empty
    val flushes: AtomicInteger      = new AtomicInteger(0)
    val closes: AtomicInteger       = new AtomicInteger(0)

    def write(content: CharSequence): Unit = writes += content.toString
    override def flush(): Unit             = flushes.incrementAndGet()
    override def close(): Unit             = closes.incrementAndGet()
  }

  private object BodyOnlyFormatter extends LogFormatter {
    def format(
      sb: StringBuilder,
      timestampNanos: Long,
      severity: Severity,
      severityText: String,
      body: String,
      builder: Attributes.AttributesBuilder,
      traceIdHi: Long,
      traceIdLo: Long,
      spanId: Long,
      traceFlags: Byte,
      throwable: Option[Throwable]
    ): Unit = sb.append(severityText).append(':').append(body)

    def formatRecord(sb: StringBuilder, record: LogRecord): Unit =
      sb.append(record.severityText).append(':').append(record.body.value)
  }

  private val regionKey     = AttributeKey.string("region")
  private val serviceKey    = Attributes.ServiceName
  private val componentKey  = AttributeKey.string("component")
  private val versionKey    = AttributeKey.string("version")
  private val enabledKey    = AttributeKey.boolean("enabled")
  private val answerKey     = AttributeKey.long("answer")
  private val ratioKey      = AttributeKey.double("ratio")
  private val templateParts = Array("Hello ", " world ", "!")
  private val templateArgs  = Array[Any]("big", 42)

  private def withRestoredGlobalLogState[A](f: => A): A = {
    val originalState = GlobalLogState.get()
    try f
    finally {
      log.clearWriters()
      log.clearAllOverrides()
      log.removeAll()
      GlobalLogState.set(originalState)
    }
  }

  def spec: Spec[Any, Nothing] = suite("AdditionalCoverage")(
    suite("LogRecord and LogMessage")(
      test("LogRecordBuilder setters and build populate all fields") {
        val scope     = InstrumentationScope("builder-scope", Some("1.0.0"), Attributes.of(componentKey, "tests"))
        val resource  = Resource.create(Attributes.of(serviceKey, "builder-service"))
        val templated = new LogMessage.Templated(templateParts.clone(), templateArgs.clone())
        val record    = LogRecord.builder
          .setTimestamp(111L)
          .setObservedTimestamp(222L)
          .setSeverity(Severity.Warn)
          .setBody("ignored")
          .setBody(templated)
          .setAttribute(regionKey, "eu-west-1")
          .setTraceId(10L, 20L)
          .setSpanId(30L)
          .setTraceFlags(TraceFlags.sampled.byte)
          .setResource(resource)
          .setInstrumentationScope(scope)
          .build

        assertTrue(
          record.timestampNanos == 111L,
          record.observedTimestampNanos == 222L,
          record.severity == Severity.Warn,
          record.severityText == Severity.Warn.text,
          record.body eq templated,
          record.body.value == "Hello big world 42!",
          record.attributes.get(regionKey).contains("eu-west-1"),
          record.traceIdHi == 10L,
          record.traceIdLo == 20L,
          record.spanId == 30L,
          record.traceFlags == TraceFlags.sampled.byte,
          record.resource == resource,
          record.instrumentationScope == scope,
          record.hasTraceId,
          record.hasSpanId
        )
      },
      test("LogRecord trace and span presence helpers handle absent values") {
        val built = LogRecord.builder.build

        assertTrue(
          built.timestampNanos > 0L,
          built.observedTimestampNanos == built.timestampNanos,
          !built.hasTraceId,
          !built.hasSpanId
        )
      },
      test("LogMessage.Templated supports value equality hashCode toString and caching") {
        val templated   = new LogMessage.Templated(templateParts.clone(), templateArgs.clone())
        val sameValue   = new LogMessage.Templated(templateParts.clone(), templateArgs.clone())
        val simple      = LogMessage.Simple("Hello big world 42!")
        val firstValue  = templated.value
        val secondValue = templated.value

        assertTrue(
          firstValue == "Hello big world 42!",
          firstValue.asInstanceOf[AnyRef] eq secondValue.asInstanceOf[AnyRef],
          templated == sameValue,
          (templated: LogMessage) == (simple: LogMessage),
          templated.hashCode() == sameValue.hashCode(),
          templated.hashCode() == firstValue.hashCode,
          templated.toString == firstValue
        )
      }
    ),
    suite("metric facade")(
      test("counter histogram gauge upDownCounter get install removeAll and reader work") {
        val provider = MeterProvider.builder
          .setResource(Resource.create(Attributes.of(serviceKey, "metric-facade")))
          .build()

        try {
          metric.install(provider)

          val meter     = metric.get("facade-meter")
          val sameMeter = provider.get("facade-meter")
          val counter   = metric.counter("requests")
          val histogram = metric.histogram("latency")
          val gauge     = metric.gauge("temperature")
          val upDown    = metric.upDownCounter("queue-size")

          counter.add(3L)
          histogram.record(12.5)
          gauge.record(7.5)
          upDown.add(-2L)

          val installedReader = metric.reader
          val collected       = installedReader.collectAllMetrics()

          metric.removeAll()

          assertTrue(
            meter eq sameMeter,
            installedReader eq provider.reader,
            collected.size == 4,
            metric.reader.collectAllMetrics().isEmpty
          )
        } finally metric.removeAll()
      }
    ),
    suite("trace facade")(
      test("span overloads get collectedSpans and clearSpans work on default provider") {
        trace.clearSpans()
        try {
          val tracer = trace.get("facade-default")

          trace.span("simple")(_.setAttribute("mode", "simple"))
          trace.span("server", SpanKind.Server)(_.setAttribute("mode", "server"))
          trace.span("client", SpanKind.Client, Attributes.of(regionKey, "us-east-1"))(_.setStatus(SpanStatus.Ok))

          val spans = trace.collectedSpans
          trace.clearSpans()

          assertTrue(
            tracer.instrumentationScope.name == "facade-default",
            spans.map(_.name).toSet == Set("simple", "server", "client"),
            spans.exists(_.kind == SpanKind.Server),
            spans.exists(_.kind == SpanKind.Client),
            trace.collectedSpans.isEmpty
          )
        } finally {
          trace.clearSpans()
          trace.removeAll()
        }
      },
      test("install and removeAll switch tracing behavior") {
        trace.clearSpans()
        val processor = new TestSpanProcessor
        val provider  = TracerProvider.builder.addSpanProcessor(processor).build()

        try {
          trace.install(provider)
          trace.get("installed").span("installed-span")(_ => ())
          trace.removeAll()

          var noOpRecording = true
          trace.span("removed-span") { span =>
            noOpRecording = span.isRecording
          }

          assertTrue(
            processor.ended.map(_.name).toList == List("installed-span"),
            !noOpRecording
          )
        } finally {
          trace.clearSpans()
          trace.removeAll()
        }
      }
    ),
    suite("providers")(
      test("LoggerProvider builder get and shutdown use custom context and processors") {
        val processor = new TestLogProcessor
        val parent    = SpanContext.create(1L, 2L, SpanId(3L), TraceFlags.sampled, "state", isRemote = true)
        val storage   = ContextStorage.create[Option[SpanContext]](Some(parent))
        val resource  = Resource.create(Attributes.of(serviceKey, "logger-provider"))
        val provider  = LoggerProvider.builder
          .setResource(resource)
          .setContextStorage(storage)
          .addLogRecordProcessor(processor)
          .build()
        val logger = provider.get("logger-scope", "1.2.3")

        logger.info("hello")
        provider.shutdown()

        val record = processor.emitted.head
        assertTrue(
          processor.emitted.size == 1,
          record.body.value == "hello",
          record.resource == resource,
          record.instrumentationScope.name == "logger-scope",
          record.instrumentationScope.version.contains("1.2.3"),
          record.traceIdHi == 1L,
          record.traceIdLo == 2L,
          record.spanId == 3L,
          processor.shutdowns.get() == 1,
          logger.currentSpanContext().contains(parent)
        )
      },
      test("MeterProvider builder get shutdown and close keep custom resource") {
        val resource = Resource.create(Attributes.of(serviceKey, "meter-provider"))
        val provider = MeterProvider.builder.setResource(resource).build()
        val meterA   = provider.get("meter-scope", "2.0.0")
        val meterB   = provider.get("meter-scope", "2.0.0")
        val counter  = meterA.counterBuilder("count").build()

        counter.add(11L)
        val collected = provider.reader.collectAllMetrics()
        provider.shutdown()
        provider.close()

        assertTrue(
          provider.resource == resource,
          meterA eq meterB,
          meterA.instrumentationScope.name == "meter-scope",
          meterA.instrumentationScope.version.contains("2.0.0"),
          collected.size == 1
        )
      },
      test("TracerProvider builder get shutdown forceFlush and close honor resource sampler and context storage") {
        val processor    = new TestSpanProcessor
        val parent       = SpanContext.create(100L, 200L, SpanId(300L), TraceFlags.sampled, "ctx=1", isRemote = true)
        val storage      = ContextStorage.create[Option[SpanContext]](Some(parent))
        val samplerAttrs = Attributes.of(versionKey, "sampled")
        val sampler      = new Sampler {
          def shouldSample(
            parentContext: Option[SpanContext],
            traceIdHi: Long,
            traceIdLo: Long,
            name: String,
            kind: SpanKind,
            attributes: Attributes,
            links: Seq[SpanLink]
          ): SamplingResult =
            SamplingResult(SamplingDecision.RecordAndSample, samplerAttrs, "trace-state")

          val description: String = "test-sampler"
        }
        val resource = Resource.create(Attributes.of(serviceKey, "tracer-provider"))
        val provider = TracerProvider.builder
          .setResource(resource)
          .setSampler(sampler)
          .setContextStorage(storage)
          .addSpanProcessor(processor)
          .build()
        val tracer         = provider.get("tracer-scope", "3.0.0")
        var activeCtxMatch = false

        tracer.span("provider-span", SpanKind.Consumer, Attributes.of(componentKey, "api")) { _ =>
          activeCtxMatch =
            tracer.currentSpan.exists(ctx => ctx.traceIdHi == 100L && ctx.traceIdLo == 200L && ctx.isSampled)
        }

        provider.forceFlush()
        provider.shutdown()
        provider.close()

        val spanData = processor.ended.head
        assertTrue(
          activeCtxMatch,
          spanData.name == "provider-span",
          spanData.kind == SpanKind.Consumer,
          spanData.parentSpanContext == parent,
          spanData.resource == resource,
          spanData.instrumentationScope.name == "tracer-scope",
          spanData.instrumentationScope.version.contains("3.0.0"),
          spanData.attributes.get(versionKey).contains("sampled"),
          spanData.attributes.get(componentKey).contains("api"),
          spanData.spanContext.traceState == "trace-state",
          processor.forceFlushes.get() == 1,
          processor.shutdowns.get() == 2
        )
      }
    ),
    suite("resource and span builder")(
      test("Resource create default and merge behave as expected") {
        val left    = Resource.create(Attributes.of(serviceKey, "left-service"))
        val right   = Resource.create(Attributes.of(componentKey, "worker"))
        val merged  = left.merge(right)
        val default = Resource.default

        assertTrue(
          left.attributes.get(serviceKey).contains("left-service"),
          default.attributes.get(serviceKey).contains("unknown_service"),
          default.attributes.get(AttributeKey.string("telemetry.sdk.name")).contains("zio-blocks"),
          default.attributes.get(AttributeKey.string("telemetry.sdk.language")).contains("scala"),
          default.attributes.get(AttributeKey.string("telemetry.sdk.version")).isDefined,
          merged.attributes.get(serviceKey).contains("left-service"),
          merged.attributes.get(componentKey).contains("worker")
        )
      },
      test("SpanBuilder supports kind start timestamp links resource scope and explicit trace ids") {
        val parent   = SpanContext.create(7L, 8L, SpanId(9L), TraceFlags.sampled, "parent", isRemote = true)
        val linkCtx  = SpanContext.create(50L, 60L, SpanId(70L), TraceFlags.none, "linked", isRemote = true)
        val link     = SpanLink(linkCtx, Attributes.of(regionKey, "linked-region"))
        val resource = Resource.create(Attributes.of(serviceKey, "builder-span"))
        val scope    = InstrumentationScope("builder", Some("4.0.0"), Attributes.of(componentKey, "span-tests"))

        val generated = SpanBuilder("generated-span").startSpan()
        val explicit  = SpanBuilder("explicit-span")
          .setParent(parent)
          .setKind(SpanKind.Server)
          .setAttribute(enabledKey, true)
          .setStartTimestamp(123456789L)
          .addLink(link)
          .setResource(resource)
          .setInstrumentationScope(scope)
          .startSpan(11L, 22L)

        explicit.end(123456999L)
        val data = explicit.toSpanData

        assertTrue(
          generated.spanContext.isValid,
          data.name == "explicit-span",
          data.kind == SpanKind.Server,
          data.parentSpanContext == parent,
          data.startTimeNanos == 123456789L,
          data.endTimeNanos == 123456999L,
          data.attributes.get(enabledKey).contains(true),
          data.links == List(link),
          data.resource == resource,
          data.instrumentationScope == scope,
          data.spanContext.traceIdHi == 11L,
          data.spanContext.traceIdLo == 22L,
          data.spanContext.traceFlags == parent.traceFlags
        )
      }
    ),
    suite("miscellaneous telemetry")(
      test("LogRateLimit shouldLogEvery handles every one every three and zero") {
        val everyOneSite   = 11001
        val everyThreeSite = 11003
        val zeroSite       = 11000

        val everyThree = List(
          LogRateLimit.shouldLogEvery(everyThreeSite, 3),
          LogRateLimit.shouldLogEvery(everyThreeSite, 3),
          LogRateLimit.shouldLogEvery(everyThreeSite, 3),
          LogRateLimit.shouldLogEvery(everyThreeSite, 3),
          LogRateLimit.shouldLogEvery(everyThreeSite, 3),
          LogRateLimit.shouldLogEvery(everyThreeSite, 3)
        )

        assertTrue(
          LogRateLimit.shouldLogEvery(everyOneSite, 1),
          LogRateLimit.shouldLogEvery(everyOneSite, 1),
          everyThree == List(false, false, true, false, false, true),
          !LogRateLimit.shouldLogEvery(zeroSite, 0)
        )
      },
      test("NoopWriter StdoutWriter and StderrWriter accept basic writes") {
        val outBytes = new ByteArrayOutputStream()
        val errBytes = new ByteArrayOutputStream()
        val oldOut   = System.out
        val oldErr   = System.err

        try {
          System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8.name()))
          System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8.name()))

          NoopWriter.write("ignored")
          StdoutWriter.write("hello-out")
          StderrWriter.write("hello-err")

          assertTrue(
            outBytes.toString(StandardCharsets.UTF_8.name()).contains("hello-out"),
            errBytes.toString(StandardCharsets.UTF_8.name()).contains("hello-err")
          )
        } finally {
          System.setOut(oldOut)
          System.setErr(oldErr)
        }
      },
      test("Span.NoOp exposes stable empty behavior for all methods") {
        val noop = Span.NoOp

        noop.setAttribute(regionKey, "ignored")
        noop.setAttribute("string", "ignored")
        noop.setAttribute("long", 1L)
        noop.setAttribute("double", 2.0)
        noop.setAttribute("boolean", value = true)
        noop.addEvent("event")
        noop.addEvent("event", Attributes.of(regionKey, "ignored"))
        noop.addEvent("event", 10L, Attributes.empty)
        noop.setStatus(SpanStatus.Error("ignored"))
        noop.end()
        noop.end(12L)

        val data = noop.toSpanData
        assertTrue(
          noop.spanContext == SpanContext.invalid,
          noop.name.isEmpty,
          noop.kind == SpanKind.Internal,
          !noop.isRecording,
          data.name.isEmpty,
          data.spanContext == SpanContext.invalid,
          data.parentSpanContext == SpanContext.invalid,
          data.startTimeNanos == 0L,
          data.endTimeNanos == 0L,
          data.attributes.isEmpty,
          data.events.isEmpty,
          data.links.isEmpty,
          data.status == SpanStatus.Unset,
          data.resource == Resource.empty,
          data.instrumentationScope == InstrumentationScope("noop")
        )
      },
      test("InstrumentationScope stores version and attributes") {
        val attrs = Attributes.of(componentKey, "instrumentation") ++ Attributes.of(versionKey, "1")
        val scope = InstrumentationScope("scope-name", Some("9.9.9"), attrs)

        assertTrue(
          scope.name == "scope-name",
          scope.version.contains("9.9.9"),
          scope.attributes.get(componentKey).contains("instrumentation"),
          scope.attributes.get(versionKey).contains("1")
        )
      }
    ),
    suite("global log state and facade extras")(
      test("GlobalLogState setLevel clearLevel clearAllLevels and effectiveLevel honor longest prefix") {
        val logger = LoggerProvider.builder.addLogRecordProcessor(new TestLogProcessor).build().get("global-levels")

        withRestoredGlobalLogState {
          GlobalLogState.install(logger, Severity.Info)
          GlobalLogState.setLevel("com.example", Severity.Debug)
          GlobalLogState.setLevel("com.example.deep", Severity.Error)

          val stateWithOverrides = GlobalLogState.get()
          val generalLevel       = stateWithOverrides.effectiveLevel("com.example.Service")
          val specificLevel      = stateWithOverrides.effectiveLevel("com.example.deep.Service")
          val fallbackLevel      = stateWithOverrides.effectiveLevel("org.other.Service")

          GlobalLogState.clearLevel("com.example.deep")
          val afterClear = GlobalLogState.get().effectiveLevel("com.example.deep.Service")

          GlobalLogState.clearAllLevels()
          val afterClearAll = GlobalLogState.get().effectiveLevel("com.example.Service")

          assertTrue(
            generalLevel == Severity.Debug.number,
            specificLevel == Severity.Error.number,
            fallbackLevel == Severity.Info.number,
            afterClear == Severity.Debug.number,
            afterClearAll == Severity.Info.number
          )
        }
      },
      test("GlobalLogState removeAll switches to silent state") {
        val logger = LoggerProvider.builder.addLogRecordProcessor(new TestLogProcessor).build().get("global-remove")

        withRestoredGlobalLogState {
          GlobalLogState.install(logger, Severity.Debug)
          GlobalLogState.setLevel("zio.blocks", Severity.Trace)
          GlobalLogState.removeAll()

          val state = GlobalLogState.get()
          log.info("silenced")

          assertTrue(
            state.minSeverity == Int.MaxValue,
            state.effectiveLevel("zio.blocks.telemetry.Test") == Int.MaxValue,
            state.logger.processors.isEmpty
          )
        }
      },
      test("log writer clearWriters withMinSeverity and addProcessor work together") {
        val baseProcessor  = new TestLogProcessor
        val extraProcessor = new TestLogProcessor
        val writer         = new RecordingLogWriter
        val logger         = LoggerProvider.builder.addLogRecordProcessor(baseProcessor).build().get("log-facade")

        withRestoredGlobalLogState {
          log.install(logger, Severity.Warn)
          log.addProcessor(extraProcessor)
          log.writer(BodyOnlyFormatter, writer)

          log.debug("outside")
          log.withMinSeverity(Severity.Debug) {
            log.debug("inside")
          }
          log.debug("outside-again")
          log.clearWriters()
          log.warn("after-clear")

          assertTrue(
            baseProcessor.emitted.map(_.body.value).toList == List("inside", "after-clear"),
            extraProcessor.emitted.map(_.body.value).toList == List("inside", "after-clear"),
            writer.writes.toList == List("DEBUG:inside"),
            writer.closes.get() >= 1
          )
        }
      },
      test("LogEnrichment attributesEnrichment and severityEnrichment update records") {
        val base = LogRecord.builder
          .setBody("base")
          .setSeverity(Severity.Info)
          .build
        val attrs = Attributes.of(componentKey, "enriched") ++ Attributes.of(answerKey, 42L)

        val withAttributes = LogEnrichment.attributesEnrichment.enrich(base, attrs)
        val withSeverity   = LogEnrichment.severityEnrichment.enrich(withAttributes, Severity.Fatal)

        assertTrue(
          withAttributes.attributes.get(componentKey).contains("enriched"),
          withAttributes.attributes.get(answerKey).contains(42L),
          withSeverity.severity == Severity.Fatal,
          withSeverity.severityText == Severity.Fatal.text,
          withSeverity.body.value == "base"
        )
      }
    )
  ) @@ TestAspect.sequential
}
