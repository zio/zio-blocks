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

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.collection.mutable.ArrayBuffer

object BranchCoverageSpec extends ZIOSpecDefault {

  private class TestProcessor extends SpanProcessor {
    val started: ArrayBuffer[Span]      = ArrayBuffer.empty
    val ended: ArrayBuffer[SpanData]    = ArrayBuffer.empty
    def onStart(span: Span): Unit       = started += span
    def onEnd(spanData: SpanData): Unit = ended += spanData
    def shutdown(): Unit                = ()
    def forceFlush(): Unit              = ()
  }

  private class TestLogProcessor extends LogRecordProcessor {
    val emitted: ArrayBuffer[LogRecord]    = ArrayBuffer.empty
    def onEmit(logRecord: LogRecord): Unit = emitted += logRecord
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }

  private class ThrowingLogProcessor extends LogRecordProcessor {
    def onEmit(logRecord: LogRecord): Unit = throw new RuntimeException("boom")
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }

  private val regionKey = AttributeKey.string("region")
  private val usEast    = Attributes.of(regionKey, "us-east-1")

  private def setLogAnnotationsEverUsed(value: Boolean): Unit = {
    val field = LogAnnotations.getClass.getDeclaredField("everUsed")
    field.setAccessible(true)
    field.setBoolean(LogAnnotations, value)
  }

  def spec = suite("BranchCoverage")(
    severitySuite,
    spanSuite,
    tracerSuite,
    meterSuite,
    loggerSuite,
    fileWriterSuite,
    attributesSuite,
    ctxSuite
  ) @@ TestAspect.sequential

  private val severitySuite = suite("Severity.fromNumber all arms")(
    test("fromNumber covers all 24 levels and invalid") {
      val expected = Seq(
        (1, Some(Severity.Trace)),
        (2, Some(Severity.Trace2)),
        (3, Some(Severity.Trace3)),
        (4, Some(Severity.Trace4)),
        (5, Some(Severity.Debug)),
        (6, Some(Severity.Debug2)),
        (7, Some(Severity.Debug3)),
        (8, Some(Severity.Debug4)),
        (9, Some(Severity.Info)),
        (10, Some(Severity.Info2)),
        (11, Some(Severity.Info3)),
        (12, Some(Severity.Info4)),
        (13, Some(Severity.Warn)),
        (14, Some(Severity.Warn2)),
        (15, Some(Severity.Warn3)),
        (16, Some(Severity.Warn4)),
        (17, Some(Severity.Error)),
        (18, Some(Severity.Error2)),
        (19, Some(Severity.Error3)),
        (20, Some(Severity.Error4)),
        (21, Some(Severity.Fatal)),
        (22, Some(Severity.Fatal2)),
        (23, Some(Severity.Fatal3)),
        (24, Some(Severity.Fatal4)),
        (0, None),
        (25, None),
        (-1, None)
      )
      assertTrue(expected.forall { case (n, e) => Severity.fromNumber(n) == e })
    },
    test("all severity sub-level number and text") {
      val all = Seq(
        Severity.Trace2,
        Severity.Trace3,
        Severity.Trace4,
        Severity.Debug2,
        Severity.Debug3,
        Severity.Debug4,
        Severity.Info2,
        Severity.Info3,
        Severity.Info4,
        Severity.Warn2,
        Severity.Warn3,
        Severity.Warn4,
        Severity.Error2,
        Severity.Error3,
        Severity.Error4,
        Severity.Fatal2,
        Severity.Fatal3,
        Severity.Fatal4
      )
      assertTrue(all.forall(s => s.number >= 1 && s.number <= 24 && s.text.nonEmpty))
    }
  )

  private val spanSuite = suite("Span branch coverage")(
    test("setAttribute with typed key after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.setAttribute(AttributeKey.string("k"), "v")
      assertTrue(span.toSpanData.attributes.isEmpty)
    },
    test("setAttribute(String, Long) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.setAttribute("k", 42L)
      assertTrue(span.toSpanData.attributes.isEmpty)
    },
    test("setAttribute(String, Double) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.setAttribute("k", 3.14)
      assertTrue(span.toSpanData.attributes.isEmpty)
    },
    test("setAttribute(String, Boolean) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.setAttribute("k", true)
      assertTrue(span.toSpanData.attributes.isEmpty)
    },
    test("addEvent(String) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.addEvent("ev")
      assertTrue(span.toSpanData.events.isEmpty)
    },
    test("addEvent(String, Attributes) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.addEvent("ev", Attributes.empty)
      assertTrue(span.toSpanData.events.isEmpty)
    },
    test("addEvent(String, Long, Attributes) after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.addEvent("ev", 123L, Attributes.empty)
      assertTrue(span.toSpanData.events.isEmpty)
    },
    test("setStatus after end") {
      val span = SpanBuilder("s").startSpan(); span.end(); span.setStatus(SpanStatus.Error("oops"))
      assertTrue(span.toSpanData.status == SpanStatus.Unset)
    },
    test("end(Long) after already ended") {
      val span = SpanBuilder("s").startSpan(); span.end(100L); span.end(200L)
      assertTrue(span.toSpanData.endTimeNanos == 100L)
    },
    test("toAttributeValue handles all seq types") {
      val span = SpanBuilder("seq-span").startSpan()
      span.setAttribute(AttributeKey.stringSeq("ss"), Seq("a", "b"))
      span.setAttribute(AttributeKey.longSeq("ls"), Seq(1L, 2L))
      span.setAttribute(AttributeKey.doubleSeq("ds"), Seq(1.0, 2.0))
      span.setAttribute(AttributeKey.booleanSeq("bs"), Seq(true, false))
      span.end()
      val map = span.toSpanData.attributes.toMap
      assertTrue(map.contains("ss") && map.contains("ls") && map.contains("ds") && map.contains("bs"))
    }
  )

  private def mkSampler(decision: SamplingDecision, attrs: Attributes, ts: String = ""): Sampler = new Sampler {
    def shouldSample(
      pc: Option[SpanContext],
      tidHi: Long,
      tidLo: Long,
      n: String,
      k: SpanKind,
      a: Attributes,
      l: Seq[SpanLink]
    ): SamplingResult =
      SamplingResult(decision, attrs, ts)
    def description: String = "TestSampler"
  }

  private val tracerSuite = suite("Tracer branch coverage")(
    test("ParentBasedSampler preserves traceState for sampled parents") {
      val parent = SpanContext.create(1L, 2L, SpanId(3L), TraceFlags.sampled, "rojo=1", isRemote = true)
      val result = ParentBasedSampler(AlwaysOffSampler).shouldSample(
        parentContext = Some(parent),
        traceIdHi = 1L,
        traceIdLo = 2L,
        name = "child",
        kind = SpanKind.Internal,
        attributes = Attributes.empty,
        links = Nil
      )
      assertTrue(result.decision == SamplingDecision.RecordAndSample, result.traceState == "rojo=1")
    },
    test("ParentBasedSampler preserves traceState for unsampled parents") {
      val parent = SpanContext.create(1L, 2L, SpanId(3L), TraceFlags.none, "rojo=0", isRemote = true)
      val result = ParentBasedSampler(AlwaysOnSampler).shouldSample(
        parentContext = Some(parent),
        traceIdHi = 1L,
        traceIdLo = 2L,
        name = "child",
        kind = SpanKind.Internal,
        attributes = Attributes.empty,
        links = Nil
      )
      assertTrue(result.decision == SamplingDecision.Drop, result.traceState == "rojo=0")
    },
    test("RecordOnly with all attribute types") {
      val attrs = Attributes.builder
        .put(AttributeKey.string("s"), "v")
        .put(AttributeKey.long("l"), 1L)
        .put(AttributeKey.double("d"), 2.0)
        .put(AttributeKey.boolean("b"), true)
        .build
      val processor = new TestProcessor
      val tracer    = TracerProvider.builder
        .setSampler(mkSampler(SamplingDecision.RecordOnly, attrs))
        .addSpanProcessor(processor)
        .build()
        .get("t")
      val spanAttrs = Attributes.builder
        .put(AttributeKey.string("as"), "av")
        .put(AttributeKey.long("al"), 10L)
        .put(AttributeKey.double("ad"), 20.0)
        .put(AttributeKey.boolean("ab"), false)
        .build
      tracer.span("ro", SpanKind.Internal, spanAttrs) { span =>
        val recording = span.isRecording; assertTrue(recording)
      }
    },
    test("RecordAndSample with all attribute types") {
      val attrs = Attributes.builder
        .put(AttributeKey.string("s"), "v")
        .put(AttributeKey.long("l"), 1L)
        .put(AttributeKey.double("d"), 2.0)
        .put(AttributeKey.boolean("b"), true)
        .build
      val processor = new TestProcessor
      val tracer    = TracerProvider.builder
        .setSampler(mkSampler(SamplingDecision.RecordAndSample, attrs))
        .addSpanProcessor(processor)
        .build()
        .get("t")
      val spanAttrs = Attributes.builder
        .put(AttributeKey.string("as"), "av")
        .put(AttributeKey.long("al"), 10L)
        .put(AttributeKey.double("ad"), 20.0)
        .put(AttributeKey.boolean("ab"), false)
        .build
      tracer.span("ras", SpanKind.Internal, spanAttrs) { span =>
        val recording = span.isRecording; assertTrue(recording)
      }
    },
    test("RecordOnly with seq attribute hits catch-all") {
      val processor = new TestProcessor
      val tracer    = TracerProvider.builder
        .setSampler(
          mkSampler(SamplingDecision.RecordOnly, Attributes.builder.put(AttributeKey.stringSeq("seq"), Seq("a")).build)
        )
        .addSpanProcessor(processor)
        .build()
        .get("t")
      tracer.span(
        "ro-seq",
        SpanKind.Internal,
        Attributes.builder.put(AttributeKey.longSeq("lseq"), Seq(1L, 2L)).build
      ) { span =>
        val recording = span.isRecording; assertTrue(recording)
      }
    },
    test("RecordAndSample with seq attribute hits catch-all") {
      val processor = new TestProcessor
      val tracer    = TracerProvider.builder
        .setSampler(
          mkSampler(
            SamplingDecision.RecordAndSample,
            Attributes.builder.put(AttributeKey.doubleSeq("seq"), Seq(1.0)).build
          )
        )
        .addSpanProcessor(processor)
        .build()
        .get("t")
      tracer.span(
        "ras-seq",
        SpanKind.Internal,
        Attributes.builder.put(AttributeKey.booleanSeq("bseq"), Seq(true)).build
      ) { span =>
        val recording = span.isRecording; assertTrue(recording)
      }
    }
  )

  private val meterSuite = suite("Meter collectInstruments")(
    test("collectAllMetrics covers all 7 instrument types") {
      val provider = MeterProvider.builder.build()
      val meter    = provider.get("cov-lib")
      meter.counterBuilder("c").setDescription("d").setUnit("1").build().add(1L, usEast)
      meter.upDownCounterBuilder("ud").setDescription("d").setUnit("1").build().add(10L, usEast)
      meter.histogramBuilder("h").setDescription("d").setUnit("ms").build().record(5.0, usEast)
      meter.gaugeBuilder("g").setDescription("d").setUnit("%").build().record(50.0, usEast)
      meter.counterBuilder("oc").buildWithCallback(cb => cb.record(100.0, usEast))
      meter.upDownCounterBuilder("oud").buildWithCallback(cb => cb.record(-5.0, usEast))
      meter.gaugeBuilder("og").buildWithCallback(cb => cb.record(42.0, usEast))
      assertTrue(provider.reader.collectAllMetrics().size == 7)
    },
    test("bind creates a fresh gauge slot when attributes are absent") {
      val gauge = Gauge("temperature", "Current temperature", "celsius")
      val bound = gauge.bind(usEast)

      bound.record(21.5)

      val points = gauge.collect() match {
        case MetricData.GaugeData(dataPoints) => dataPoints
        case _                                => Nil
      }

      assertTrue(
        points.size == 1,
        points.head.attributes == usEast,
        points.head.value == 21.5
      )
    },
    test("bind reuses the existing gauge slot for the same attributes") {
      val gauge      = Gauge("temperature", "Current temperature", "celsius")
      val firstBound = gauge.bind(usEast)
      val nextBound  = gauge.bind(usEast)

      firstBound.record(10.0)
      nextBound.record(25.0)

      val points = gauge.collect() match {
        case MetricData.GaugeData(dataPoints) => dataPoints
        case _                                => Nil
      }

      assertTrue(
        points.size == 1,
        points.head.attributes == usEast,
        points.head.value == 25.0
      )
    }
  )

  private val loggerSuite = suite("Logger attribute types")(
    test("log with Boolean attribute") {
      val p = new TestLogProcessor
      LoggerProvider.builder
        .addLogRecordProcessor(p)
        .build()
        .get("t")
        .info("m", "b" -> AttributeValue.BooleanValue(true))
      assertTrue(p.emitted.head.attributes.toMap("b") == AttributeValue.BooleanValue(true))
    },
    test("log with Double attribute") {
      val p = new TestLogProcessor
      LoggerProvider.builder
        .addLogRecordProcessor(p)
        .build()
        .get("t")
        .info("m", "d" -> AttributeValue.DoubleValue(3.14))
      assertTrue(p.emitted.head.attributes.toMap("d") == AttributeValue.DoubleValue(3.14))
    },
    test("log with seq attribute hits catch-all") {
      val p = new TestLogProcessor
      LoggerProvider.builder
        .addLogRecordProcessor(p)
        .build()
        .get("t")
        .info("m", "s" -> AttributeValue.StringSeqValue(Seq("a")))
      assertTrue(p.emitted.head.attributes.toMap.contains("s"))
    },
    test("StandardLogEmitter clears builder when severity is below processor threshold") {
      val builder = Attributes.builder.put("k", "v")
      val emitter = new StandardLogEmitter(Array(new TestLogProcessor), Severity.Error.number)

      emitter.emit(
        timestampNanos = 1L,
        severity = Severity.Debug,
        severityText = Severity.Debug.text,
        body = "ignored",
        builder = builder,
        traceIdHi = 0L,
        traceIdLo = 0L,
        spanId = 0L,
        traceFlags = 0,
        resource = Resource.empty,
        instrumentationScope = InstrumentationScope("test"),
        throwable = None
      )

      assertTrue(builder.builderLen == 0)
    },
    test("StandardLogEmitter catches processor failures and clears builder") {
      val builder = Attributes.builder.put("k", "v")
      val emitter = new StandardLogEmitter(Array(new ThrowingLogProcessor), Severity.Trace.number)

      emitter.emit(
        timestampNanos = 1L,
        severity = Severity.Info,
        severityText = Severity.Info.text,
        body = "boom",
        builder = builder,
        traceIdHi = 0L,
        traceIdLo = 0L,
        spanId = 0L,
        traceFlags = 0,
        resource = Resource.empty,
        instrumentationScope = InstrumentationScope("test"),
        throwable = None
      )

      assertTrue(builder.builderLen == 0)
    }
  )

  private val fileWriterSuite = suite("FileLogWriter branch coverage")(
    test("writes non-ASCII content through encoder slow path") {
      val path    = Files.createTempFile("telemetry-nonascii", ".log")
      val writer  = FileLogWriter(path, append = false, bufferSize = 8)
      val content = "héllø telemetry"

      try {
        writer.write(content)
        writer.close()

        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
        assertTrue(lines.size() == 1, lines.get(0) == content)
      } finally Files.deleteIfExists(path)
    },
    test("writes oversized ASCII content through overflow path") {
      val path    = Files.createTempFile("telemetry-ascii", ".log")
      val writer  = FileLogWriter(path, append = false, bufferSize = 8)
      val content = "x" * 64

      try {
        writer.write(content)
        writer.flush()
        writer.close()

        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
        assertTrue(lines.size() == 1, lines.get(0) == content)
      } finally Files.deleteIfExists(path)
    }
  )

  private val attributesSuite = suite("Attributes branch coverage")(
    test("++ merges with conflict") {
      val merged = Attributes.builder
        .put("k1", "v1")
        .put("k2", "v2")
        .build ++ Attributes.builder.put("k2", "v2b").put("k3", "v3").build
      assertTrue(merged.size == 4 && merged.get(AttributeKey.string("k2")).contains("v2b"))
    },
    test("get StringSeq via valueToType") {
      val k = AttributeKey.stringSeq("ss"); assertTrue(Attributes.of(k, Seq("a", "b")).get(k).contains(Seq("a", "b")))
    },
    test("get LongSeq via valueToType") {
      val k = AttributeKey.longSeq("ls"); assertTrue(Attributes.of(k, Seq(1L, 2L)).get(k).contains(Seq(1L, 2L)))
    },
    test("get DoubleSeq via valueToType") {
      val k = AttributeKey.doubleSeq("ds"); assertTrue(Attributes.of(k, Seq(1.0, 2.0)).get(k).contains(Seq(1.0, 2.0)))
    },
    test("get BooleanSeq via valueToType") {
      val k = AttributeKey.booleanSeq("bs");
      assertTrue(Attributes.of(k, Seq(true, false)).get(k).contains(Seq(true, false)))
    },
    test("builder put same key updates") {
      assertTrue(Attributes.builder.put("k", "v1").put("k", "v2").build.get(AttributeKey.string("k")).contains("v2"))
    },
    test("builder put same key clears stale storage when type changes") {
      val stringAttrs = Attributes.builder.put("k", "v").put("k", 1L).build
      val seqAttrs    = Attributes.builder.put(AttributeKey.stringSeq("k"), Seq("a")).put("k", true).build
      val longAttrs   = Attributes.builder.put("k", 1L).build
      val boolAttrs   = Attributes.builder.put("k", true).build

      assertTrue(
        stringAttrs == longAttrs,
        stringAttrs.hashCode == longAttrs.hashCode,
        seqAttrs == boolAttrs,
        seqAttrs.hashCode == boolAttrs.hashCode,
        stringAttrs.get(AttributeKey.long("k")).contains(1L),
        seqAttrs.get(AttributeKey.boolean("k")).contains(true)
      )
    },
    test("of with Long") {
      assertTrue(Attributes.of(AttributeKey.long("l"), 42L).get(AttributeKey.long("l")).contains(42L))
    },
    test("of with Double") {
      assertTrue(Attributes.of(AttributeKey.double("d"), 3.14).get(AttributeKey.double("d")).contains(3.14))
    },
    test("of with Boolean") {
      assertTrue(Attributes.of(AttributeKey.boolean("b"), true).get(AttributeKey.boolean("b")).contains(true))
    }
  )

  private val ctxSuite = suite("ContextStorage branch coverage")(
    test("implementationName is ScopedValue") {
      assertTrue(ContextStorage.implementationName == "ScopedValue")
    },
    test("JsonLogFormatter formatRecord emits valid attributes array with throwable") {
      val record = LogRecord(
        timestampNanos = 1000L,
        observedTimestampNanos = 1000L,
        severity = Severity.Info,
        severityText = "INFO",
        body = LogMessage("hello"),
        attributes = Attributes.builder.put("k", "v").build,
        traceIdHi = 0L,
        traceIdLo = 0L,
        spanId = 0L,
        traceFlags = 0,
        resource = Resource.empty,
        instrumentationScope = InstrumentationScope("test"),
        throwable = Some(new RuntimeException("boom"))
      )
      val sb = new StringBuilder

      JsonLogFormatter.formatRecord(sb, record)

      val rendered = sb.toString
      assertTrue(
        rendered.startsWith("{\"timeUnixNano\":\"1000\""),
        rendered.contains("\"attributes\":[{\"key\":\"k\",\"value\":{\"stringValue\":\"v\"}},{\"key\":\"exception.stacktrace\""),
        rendered.endsWith("}"),
        !rendered.contains("{{\"timeUnixNano\""),
        !rendered.contains("\"value\":{{")
      )
    },
    test("LogAnnotations get is empty before first use") {
      setLogAnnotationsEverUsed(false)
      assertTrue(LogAnnotations.get().isEmpty)
    },
    test("LogAnnotations scoped merges nested annotations and restores outer scope") {
      setLogAnnotationsEverUsed(false)

      val (outerBeforeNested, nested, outerAfterNested, afterAllScopes) =
        LogAnnotations.scoped(Map("requestId" -> "req-1")) {
          val beforeNested = LogAnnotations.get()
          val nestedState = LogAnnotations.scoped(Map("userId" -> "u-1")) {
            LogAnnotations.get()
          }
          val afterNested = LogAnnotations.get()
          (beforeNested, nestedState, afterNested, ())
        }

      assertTrue(
        outerBeforeNested == Map("requestId" -> "req-1"),
        nested == Map("requestId" -> "req-1", "userId" -> "u-1"),
        outerAfterNested == Map("requestId" -> "req-1"),
        LogAnnotations.get().isEmpty,
        afterAllScopes == ()
      )
    },
    test("scoped restores on normal exit") {
      val s = ContextStorage.create("initial")
      assertTrue(s.scoped("scoped")(s.get()) == "scoped" && s.get() == "initial")
    },
    test("scoped restores on exception") {
      val s = ContextStorage.create("initial")
      try s.scoped("scoped")(throw new RuntimeException("boom"))
      catch { case _: RuntimeException => () }
      assertTrue(s.get() == "initial")
    }
  )
}
