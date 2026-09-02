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
import java.util.ArrayList

object ExtraCoverageSpec extends ZIOSpecDefault {

  private final class TestLogProcessor extends LogRecordProcessor {
    private val emittedBuffer = scala.collection.mutable.ArrayBuffer.empty[LogRecord]

    def emitted: List[LogRecord]           = emittedBuffer.toList
    def onEmit(logRecord: LogRecord): Unit = emittedBuffer += logRecord
    def shutdown(): Unit                   = ()
    def forceFlush(): Unit                 = ()
  }

  private val regionKey  = AttributeKey.string("region")
  private val statusKey  = AttributeKey.long("status")
  private val queueKey   = AttributeKey.string("queue")
  private val latencyKey = AttributeKey.string("latency")

  private def sumPoints(data: MetricData): List[SumDataPoint] = data match {
    case MetricData.SumData(points)  => points
    case MetricData.HistogramData(_) => Nil
    case MetricData.GaugeData(_)     => Nil
  }

  private def gaugePoints(data: MetricData): List[GaugeDataPoint] = data match {
    case MetricData.GaugeData(points) => points
    case _                            => Nil
  }

  private def histogramPoints(data: MetricData): List[HistogramDataPoint] = data match {
    case MetricData.HistogramData(points) => points
    case _                                => Nil
  }

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

  def spec: Spec[Any, Nothing] = suite("ExtraCoverage")(
    suite("SpanId")(
      test("supports validity hex bytes string parsing and roundtrip") {
        val spanId      = SpanId(0x0123456789abcdefL)
        val zeroFromHex = SpanId.fromHex("0000000000000000")
        val roundTrip   = SpanId.fromHex(spanId.toHex)
        val random      = SpanId.random

        assertTrue(
          spanId.isValid,
          spanId.toHex == "0123456789abcdef",
          spanId.toByteArray.toList == List(0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef).map(_.toByte),
          spanId.toString.contains("0123456789abcdef"),
          SpanId.invalid.value == 0L,
          !SpanId.invalid.isValid,
          random.isValid,
          random.value != 0L,
          roundTrip.contains(spanId),
          zeroFromHex.isDefined,
          zeroFromHex.exists(_.value == 0L),
          zeroFromHex.exists(span => !span.isValid),
          SpanId.fromHex("abcdef0123456789").exists(_.toHex == "abcdef0123456789"),
          SpanId.fromHex("xyz").isEmpty,
          SpanId.fromHex("abc").isEmpty
        )
      }
    ),
    suite("TraceFlags")(
      test("supports sampled bit hex byte string parsing and roundtrip") {
        val custom = TraceFlags(0x42.toByte)
        val allSet = TraceFlags(0xff.toByte)
        val values = List(0x00.toByte, 0x01.toByte, 0x42.toByte, 0xff.toByte)

        assertTrue(
          !TraceFlags.none.isSampled,
          TraceFlags.sampled.isSampled,
          TraceFlags.none.withSampled(sampled = true).isSampled,
          !TraceFlags.sampled.withSampled(sampled = false).isSampled,
          TraceFlags.none.toHex == "00",
          TraceFlags.sampled.toHex == "01",
          custom.toHex == "42",
          allSet.toHex == "ff",
          TraceFlags.fromHex("01").exists(_.isSampled),
          TraceFlags.fromHex("00").exists(flags => !flags.isSampled),
          TraceFlags.fromHex("xyz").isEmpty,
          TraceFlags.fromHex("0").isEmpty,
          TraceFlags.sampled.toByte == 1.toByte,
          TraceFlags.sampled.toString.contains("01"),
          values.forall(byte => TraceFlags.fromHex(TraceFlags(byte).toHex).exists(_.byte == byte))
        )
      }
    ),
    suite("SyncInstrumentsHelper")(
      test("buildPooledAttributes covers primitive and fallback conversions") {
        val attrs = SyncInstrumentsHelper.buildPooledAttributes(
          Seq(
            "string"  -> "value",
            "long"    -> 42L,
            "int"     -> 7,
            "double"  -> 2.5,
            "boolean" -> true,
            "other"   -> SpanId(10L)
          )
        )

        assertTrue(
          attrs.get(AttributeKey.string("string")).contains("value"),
          attrs.get(AttributeKey.long("long")).contains(42L),
          attrs.get(AttributeKey.long("int")).contains(7L),
          attrs.get(AttributeKey.double("double")).contains(2.5),
          attrs.get(AttributeKey.boolean("boolean")).contains(true),
          attrs.get(AttributeKey.string("other")).contains("SpanId(000000000000000a)")
        )
      },
      test("mapToAttributes covers all AttributeValue variants") {
        val attrs = SyncInstrumentsHelper.mapToAttributes(
          Map(
            "string"   -> AttributeValue.StringValue("value"),
            "boolean"  -> AttributeValue.BooleanValue(true),
            "long"     -> AttributeValue.LongValue(42L),
            "double"   -> AttributeValue.DoubleValue(2.5),
            "strings"  -> AttributeValue.StringSeqValue(Seq("a", "b")),
            "longs"    -> AttributeValue.LongSeqValue(Seq(1L, 2L)),
            "doubles"  -> AttributeValue.DoubleSeqValue(Seq(1.5, 2.5)),
            "booleans" -> AttributeValue.BooleanSeqValue(Seq(true, false))
          )
        )

        assertTrue(
          attrs.get(AttributeKey.string("string")).contains("value"),
          attrs.get(AttributeKey.boolean("boolean")).contains(true),
          attrs.get(AttributeKey.long("long")).contains(42L),
          attrs.get(AttributeKey.double("double")).contains(2.5),
          attrs.get(AttributeKey.stringSeq("strings")).contains(Seq("a", "b")),
          attrs.get(AttributeKey.longSeq("longs")).contains(Seq(1L, 2L)),
          attrs.get(AttributeKey.doubleSeq("doubles")).contains(Seq(1.5, 2.5)),
          attrs.get(AttributeKey.booleanSeq("booleans")).contains(Seq(true, false))
        )
      },
      test("listFromJava preserves order for empty single and multiple values") {
        val empty    = new ArrayList[String]()
        val single   = new ArrayList[String]()
        val multiple = new ArrayList[String]()

        single.add("only")
        multiple.add("first")
        multiple.add("second")
        multiple.add("third")

        assertTrue(
          SyncInstrumentsHelper.listFromJava(empty) == Nil,
          SyncInstrumentsHelper.listFromJava(single) == List("only"),
          SyncInstrumentsHelper.listFromJava(multiple) == List("first", "second", "third")
        )
      }
    ),
    suite("sync instruments")(
      test("counter varargs add and bind paths record values and attributes") {
        val counter = Counter("requests", "Total requests", "1")

        counter.add(1L, "region" -> "us", "status" -> 200)
        counter.bind(Attributes.empty).add(5L)

        val points       = sumPoints(counter.collect())
        val varargsPoint = points.find(_.attributes.get(regionKey).contains("us"))
        val boundPoint   = points.find(_.attributes.isEmpty)

        assertTrue(
          points.size == 2,
          varargsPoint.exists(_.value == 1L),
          varargsPoint.flatMap(_.attributes.get(statusKey)).contains(200L),
          boundPoint.exists(_.value == 5L)
        )
      },
      test("gauge varargs record and bind paths record latest values") {
        val gauge = Gauge("temperature", "Current temperature", "celsius")

        gauge.record(3.5, "region" -> "us")
        gauge.bind(Attributes.empty).record(7.0)

        val points       = gaugePoints(gauge.collect())
        val varargsPoint = points.find(_.attributes.get(regionKey).contains("us"))
        val boundPoint   = points.find(_.attributes.isEmpty)

        assertTrue(
          points.size == 2,
          varargsPoint.exists(_.value == 3.5),
          boundPoint.exists(_.value == 7.0)
        )
      },
      test("upDownCounter varargs add and bind paths record signed values") {
        val counter = UpDownCounter("queue.size", "Queue depth", "1")

        counter.add(-1L, "queue" -> "main")
        counter.bind(Attributes.empty).add(-3L)

        val points       = sumPoints(counter.collect())
        val varargsPoint = points.find(_.attributes.get(queueKey).contains("main"))
        val boundPoint   = points.find(_.attributes.isEmpty)

        assertTrue(
          points.size == 2,
          varargsPoint.exists(_.value == -1L),
          boundPoint.exists(_.value == -3L)
        )
      },
      test("histogram varargs record and bind paths record separate series") {
        val histogram = Histogram("latency", "Request latency", "ms")

        histogram.record(5.0, "latency" -> "p99")
        histogram.bind(Attributes.empty).record(10.0)

        val points       = histogramPoints(histogram.collect())
        val varargsPoint = points.find(_.attributes.get(latencyKey).contains("p99"))
        val boundPoint   = points.find(_.attributes.isEmpty)

        assertTrue(
          points.size == 2,
          varargsPoint.exists(point => point.count == 1L && point.sum == 5.0),
          boundPoint.exists(point => point.count == 1L && point.sum == 10.0)
        )
      }
    ),
    suite("FileLogWriter")(
      test("writes non ASCII and oversized content through encoder path") {
        val dir    = Files.createTempDirectory("file-log-writer-extra")
        val path   = dir.resolve("nested").resolve("app.log")
        val writer = FileLogWriter(path, append = false, bufferSize = 8)
        val lines  = Vector("héllo-世界", "x" * 64)

        try {
          lines.foreach(writer.write)
          writer.flush()
          writer.close()

          val content = Files.readAllLines(path, StandardCharsets.UTF_8)
          assertTrue((0 until content.size()).map(content.get).toVector == lines)
        } finally {
          Files.deleteIfExists(path)
          Files.deleteIfExists(path.getParent)
          Files.deleteIfExists(dir)
        }
      }
    ),
    suite("log facade")(
      test("severity helper methods update and clear global overrides") {
        val processor = new TestLogProcessor
        val logger    = LoggerProvider.builder.addLogRecordProcessor(processor).build().get("log-facade-extra")

        withRestoredGlobalLogState {
          log.install(logger, Severity.Info)
          log.setMinSeverity(Severity.Warn)
          log.setMinSeverity("zio.blocks.telemetry", Severity.Debug)

          val overriddenLevel = GlobalLogState.get().effectiveLevel("zio.blocks.telemetry.ExtraCoverageSpec")
          val defaultLevel    = GlobalLogState.get().effectiveLevel("other.Service")

          log.clearMinSeverity("zio.blocks.telemetry")
          val clearedState = GlobalLogState.get()

          assertTrue(
            overriddenLevel == Severity.Debug.number,
            defaultLevel == Severity.Warn.number,
            !clearedState.levelOverridesMap.contains("zio.blocks.telemetry")
          )
        }
      }
    )
  ) @@ TestAspect.sequential
}
