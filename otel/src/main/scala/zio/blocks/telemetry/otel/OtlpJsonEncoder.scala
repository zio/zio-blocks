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

/**
 * A metric's data with the name, description and unit that [[OtlpJsonEncoder]]
 * writes for it. `MetricData` carries no name, so pair each instrument's
 * `collect()` result with the instrument's name yourself.
 *
 * @param name
 *   the metric name, such as `http.server.duration`
 * @param description
 *   what the metric measures, for people reading it in a backend
 * @param unit
 *   the unit of the values, such as `ms`, `By` or `1`
 * @param data
 *   the points, as an instrument's `collect()` or a `MetricReader` returns them
 */
final case class NamedMetric(
  name: String,
  description: String,
  unit: String,
  data: MetricData
)

/**
 * Encodes spans, log records and metrics as OTLP/HTTP JSON request bodies,
 * ready to `POST` to a collector's `/v1/traces`, `/v1/logs` or `/v1/metrics`
 * with `Content-Type: application/json`. Use it with an [[HttpSender]] to
 * export telemetry from your own loop; the module's own exporters are internal.
 * It uses no JSON library.
 *
 * Each call writes one resource and one instrumentation scope, the ones you
 * pass, for all its items: the `resource` and `instrumentationScope` that a
 * `SpanData` or a `LogRecord` carries are not written. The output follows the
 * protobuf JSON mapping: trace and span ids as lowercase hex, 64-bit integers
 * (timestamps, counts, `intValue` attributes) as quoted strings, enums as
 * numbers and field names in camelCase. Control characters and every UTF-16
 * surrogate are written as Unicode escapes, so an emoji becomes an escaped
 * pair. Attributes are written in order, a repeated key as many times as it
 * appears.
 *
 * A `Double` that is `NaN` or infinite, in a gauge or histogram point or in a
 * `Double` attribute, is written as a bare `NaN` or `Infinity`, which is not
 * valid JSON, a known bug.
 *
 * {{{
 * import zio.blocks.telemetry._
 * import java.nio.charset.StandardCharsets
 *
 * val resource = Resource.create(Attributes.of(Attributes.ServiceName, "checkout"))
 * val bytes    = OtlpJsonEncoder.encodeTraces(Nil, resource, InstrumentationScope("checkout.http", Some("1.4.0")))
 * new String(bytes, StandardCharsets.UTF_8)
 * // {"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"checkout"}}]},
 * //   "scopeSpans":[{"scope":{"name":"checkout.http","version":"1.4.0"},"spans":[]}]}]}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/custom-exporter/ Building an Exporter]]
 */
object OtlpJsonEncoder {

  /**
   * The top-level [[zio.blocks.telemetry.otel.NamedMetric]], under this object
   * too.
   */
  type NamedMetric = zio.blocks.telemetry.otel.NamedMetric

  /**
   * The companion of the top-level [[zio.blocks.telemetry.otel.NamedMetric]],
   * under this object too.
   */
  val NamedMetric: zio.blocks.telemetry.otel.NamedMetric.type = zio.blocks.telemetry.otel.NamedMetric

  /**
   * Encodes `spans` as an OTLP `ExportTraceServiceRequest` under `resource` and
   * `scope`, as UTF-8 bytes. Each span keeps its trace and span ids, its parent
   * span id (`0000000000000000` for a root span), name, kind, start and end
   * times, attributes, events, links and status.
   *
   * {{{
   * import zio.blocks.telemetry._
   * import java.nio.charset.StandardCharsets
   * import scala.collection.mutable.ListBuffer
   *
   * val ended = ListBuffer.empty[SpanData]
   * val tracer = TracerProvider.builder
   *   .addSpanProcessor(new SpanProcessor {
   *     def onStart(span: Span): Unit   = ()
   *     def onEnd(span: SpanData): Unit = ended += span
   *     def shutdown(): Unit            = ()
   *     def forceFlush(): Unit          = ()
   *   })
   *   .build()
   *   .get("checkout")
   * tracer.span("GET /cart", SpanKind.Server) { span =>
   *   span.setAttribute("http.route", "/cart")
   *   span.setStatus(SpanStatus.Ok)
   * }
   *
   * val bytes = OtlpJsonEncoder.encodeTraces(ended.toList, Resource.empty, InstrumentationScope("checkout"))
   * val json  = new String(bytes, StandardCharsets.UTF_8)
   * json.substring(json.indexOf("\"attributes\":[{"))
   * // "attributes":[{"key":"http.route","value":{"stringValue":"/cart"}}],"events":[],"links":[],"status":{"code":1}}]}]}]}
   * }}}
   */
  def encodeTraces(spans: Seq[SpanData], resource: Resource, scope: InstrumentationScope): Array[Byte] = {
    val sb = new StringBuilder(256)
    sb.append("{\"resourceSpans\":[{")
    writeResource(sb, resource)
    sb.append(",\"scopeSpans\":[{")
    writeScope(sb, scope)
    sb.append(",\"spans\":[")
    writeSeq(sb, spans)(writeSpan)
    sb.append("]}]}]}")
    sb.toString.getBytes("UTF-8")
  }

  /**
   * Encodes `metrics` as an OTLP `ExportMetricsServiceRequest` under `resource`
   * and `scope`, as UTF-8 bytes. A `SumData` becomes a `sum` with integer
   * points (`asInt`), a `GaugeData` a `gauge` with `asDouble` points, and a
   * `HistogramData` a `histogram` with explicit bucket bounds. The instruments
   * set the start time of sum and histogram points to `0`, so their
   * `startTimeUnixNano` is `"0"`.
   *
   * Sums and histograms are written without an `aggregationTemporality`, and
   * every sum is marked monotonic, the sums of an `UpDownCounter` included, a
   * known bug. A histogram point that holds no value yet (a series opened by
   * `Histogram#bind`) is written with a `min` of `Double.MaxValue` and a `max`
   * of `Double.MinValue`.
   *
   * {{{
   * import zio.blocks.telemetry._
   * import java.nio.charset.StandardCharsets
   *
   * val orders = MeterProvider.builder.build().get("checkout").counterBuilder("orders.placed").build()
   * orders.add(3L)
   *
   * val named = NamedMetric("orders.placed", "Orders placed", "1", orders.collect())
   * val bytes = OtlpJsonEncoder.encodeMetrics(Seq(named), Resource.empty, InstrumentationScope("checkout"))
   * val json  = new String(bytes, StandardCharsets.UTF_8)
   * json.substring(json.indexOf("\"asInt\"")) // "asInt":"3"}],"isMonotonic":true}}]}]}]}
   * }}}
   */
  def encodeMetrics(
    metrics: Seq[NamedMetric],
    resource: Resource,
    scope: InstrumentationScope
  ): Array[Byte] = {
    val sb = new StringBuilder(256)
    sb.append("{\"resourceMetrics\":[{")
    writeResource(sb, resource)
    sb.append(",\"scopeMetrics\":[{")
    writeScope(sb, scope)
    sb.append(",\"metrics\":[")
    writeSeq(sb, metrics)(writeNamedMetric)
    sb.append("]}]}]}")
    sb.toString.getBytes("UTF-8")
  }

  /**
   * Encodes `logs` as an OTLP `ExportLogsServiceRequest` under `resource` and
   * `scope`, as UTF-8 bytes. The body is always a `stringValue`; a record's
   * throwable becomes an `exception.stacktrace` attribute holding its stack
   * trace; a record without a trace or span id gets an empty `traceId` or
   * `spanId`.
   *
   * {{{
   * import zio.blocks.telemetry._
   * import java.nio.charset.StandardCharsets
   *
   * val record = LogRecord.builder
   *   .setTimestamp(1755990000000000000L)
   *   .setObservedTimestamp(1755990000000000000L)
   *   .setSeverity(Severity.Warn)
   *   .setBody("payment retried")
   *   .build
   * val bytes = OtlpJsonEncoder.encodeLogs(Seq(record), Resource.empty, InstrumentationScope("checkout"))
   * new String(bytes, StandardCharsets.UTF_8)
   * // {"resourceLogs":[{"resource":{"attributes":[]},"scopeLogs":[{"scope":{"name":"checkout"},"logRecords":[
   * //   {"timeUnixNano":"1755990000000000000","observedTimeUnixNano":"1755990000000000000","severityNumber":13,
   * //   "severityText":"WARN","body":{"stringValue":"payment retried"},"attributes":[],"traceId":"","spanId":"",
   * //   "flags":0}]}]}]}
   * }}}
   */
  def encodeLogs(logs: Seq[LogRecord], resource: Resource, scope: InstrumentationScope): Array[Byte] = {
    val sb = new StringBuilder(256)
    sb.append("{\"resourceLogs\":[{")
    writeResource(sb, resource)
    sb.append(",\"scopeLogs\":[{")
    writeScope(sb, scope)
    sb.append(",\"logRecords\":[")
    writeSeq(sb, logs)(writeLogRecord)
    sb.append("]}]}]}")
    sb.toString.getBytes("UTF-8")
  }

  // --- JSON primitives ---

  private def writeJsonString(sb: StringBuilder, s: String): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case _    =>
          if (c < 0x20 || (c >= 0xd800 && c <= 0xdfff)) {
            sb.append("\\u")
            sb.append(String.format("%04x", c.toInt))
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
    sb.append('"')
  }

  private def writeKey(sb: StringBuilder, key: String): Unit = {
    sb.append('"')
    sb.append(key) // keys are always safe ASCII
    sb.append("\":")
  }

  private def writeKeyString(sb: StringBuilder, key: String, value: String): Unit = {
    writeKey(sb, key)
    writeJsonString(sb, value)
  }

  private def writeKeyLong(sb: StringBuilder, key: String, value: Long): Unit = {
    writeKey(sb, key)
    sb.append('"')
    sb.append(value)
    sb.append('"')
  }

  private def writeKeyInt(sb: StringBuilder, key: String, value: Int): Unit = {
    writeKey(sb, key)
    sb.append(value)
  }

  private def writeKeyDouble(sb: StringBuilder, key: String, value: Double): Unit = {
    writeKey(sb, key)
    sb.append(value)
  }

  private def writeKeyBool(sb: StringBuilder, key: String, value: Boolean): Unit = {
    writeKey(sb, key)
    sb.append(value)
  }

  private def writeSeq[A](sb: StringBuilder, items: Seq[A])(write: (StringBuilder, A) => Unit): Unit = {
    var first = true
    items.foreach { item =>
      if (!first) sb.append(',')
      write(sb, item)
      first = false
    }
  }

  private def writeList[A](sb: StringBuilder, items: List[A])(write: (StringBuilder, A) => Unit): Unit = {
    var first = true
    items.foreach { item =>
      if (!first) sb.append(',')
      write(sb, item)
      first = false
    }
  }

  // --- Resource & Scope ---

  private def writeResource(sb: StringBuilder, resource: Resource): Unit = {
    sb.append("\"resource\":{\"attributes\":[")
    writeAttributes(sb, resource.attributes)
    sb.append("]}")
  }

  private def writeScope(sb: StringBuilder, scope: InstrumentationScope): Unit = {
    sb.append("\"scope\":{")
    writeKeyString(sb, "name", scope.name)
    scope.version.foreach { v =>
      sb.append(',')
      writeKeyString(sb, "version", v)
    }
    sb.append('}')
  }

  // --- Attributes ---

  private def writeAttributes(sb: StringBuilder, attrs: Attributes): Unit = {
    var first = true
    attrs.foreach { (key, value) =>
      if (!first) sb.append(',')
      sb.append("{\"key\":")
      writeJsonString(sb, key)
      sb.append(",\"value\":{")
      writeAttributeValue(sb, value)
      sb.append("}}")
      first = false
    }
  }

  private def writeAttributeValue(sb: StringBuilder, v: AttributeValue): Unit = v match {
    case AttributeValue.StringValue(s) =>
      writeKeyString(sb, "stringValue", s)
    case AttributeValue.BooleanValue(b) =>
      writeKeyBool(sb, "boolValue", b)
    case AttributeValue.LongValue(l) =>
      writeKeyLong(sb, "intValue", l)
    case AttributeValue.DoubleValue(d) =>
      writeKeyDouble(sb, "doubleValue", d)
    case AttributeValue.StringSeqValue(seq) =>
      sb.append("\"arrayValue\":{\"values\":[")
      var first = true
      seq.foreach { s =>
        if (!first) sb.append(',')
        sb.append("{\"stringValue\":")
        writeJsonString(sb, s)
        sb.append('}')
        first = false
      }
      sb.append("]}")
    case AttributeValue.LongSeqValue(seq) =>
      sb.append("\"arrayValue\":{\"values\":[")
      var first = true
      seq.foreach { l =>
        if (!first) sb.append(',')
        sb.append("{\"intValue\":\"")
        sb.append(l)
        sb.append("\"}")
        first = false
      }
      sb.append("]}")
    case AttributeValue.DoubleSeqValue(seq) =>
      sb.append("\"arrayValue\":{\"values\":[")
      var first = true
      seq.foreach { d =>
        if (!first) sb.append(',')
        sb.append("{\"doubleValue\":")
        sb.append(d)
        sb.append('}')
        first = false
      }
      sb.append("]}")
    case AttributeValue.BooleanSeqValue(seq) =>
      sb.append("\"arrayValue\":{\"values\":[")
      var first = true
      seq.foreach { b =>
        if (!first) sb.append(',')
        sb.append("{\"boolValue\":")
        sb.append(b)
        sb.append('}')
        first = false
      }
      sb.append("]}")
  }

  // --- Span ---

  private def spanKindToInt(kind: SpanKind): Int = kind match {
    case SpanKind.Internal => 1
    case SpanKind.Server   => 2
    case SpanKind.Client   => 3
    case SpanKind.Producer => 4
    case SpanKind.Consumer => 5
  }

  private def statusCodeToInt(status: SpanStatus): Int = status match {
    case SpanStatus.Unset    => 0
    case SpanStatus.Ok       => 1
    case SpanStatus.Error(_) => 2
  }

  private def writeSpan(sb: StringBuilder, span: SpanData): Unit = {
    sb.append('{')
    writeKeyString(sb, "traceId", span.spanContext.traceIdHex)
    sb.append(',')
    writeKeyString(sb, "spanId", span.spanContext.spanId.toHex)
    sb.append(',')
    writeKeyString(sb, "parentSpanId", span.parentSpanContext.spanId.toHex)
    sb.append(',')
    writeKeyString(sb, "name", span.name)
    sb.append(',')
    writeKeyInt(sb, "kind", spanKindToInt(span.kind))
    sb.append(',')
    writeKeyLong(sb, "startTimeUnixNano", span.startTimeNanos)
    sb.append(',')
    writeKeyLong(sb, "endTimeUnixNano", span.endTimeNanos)
    sb.append(',')

    // attributes
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, span.attributes)
    sb.append(']')
    sb.append(',')

    // events
    writeKey(sb, "events")
    sb.append('[')
    writeList(sb, span.events)(writeSpanEvent)
    sb.append(']')
    sb.append(',')

    // links
    writeKey(sb, "links")
    sb.append('[')
    writeList(sb, span.links)(writeSpanLink)
    sb.append(']')
    sb.append(',')

    // status
    writeKey(sb, "status")
    sb.append('{')
    writeKeyInt(sb, "code", statusCodeToInt(span.status))
    span.status match {
      case SpanStatus.Error(desc) =>
        sb.append(',')
        writeKeyString(sb, "message", desc)
      case _ => ()
    }
    sb.append('}')

    sb.append('}')
  }

  private def writeSpanEvent(sb: StringBuilder, event: SpanEvent): Unit = {
    sb.append('{')
    writeKeyString(sb, "name", event.name)
    sb.append(',')
    writeKeyLong(sb, "timeUnixNano", event.timestampNanos)
    sb.append(',')
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, event.attributes)
    sb.append(']')
    sb.append('}')
  }

  private def writeSpanLink(sb: StringBuilder, link: SpanLink): Unit = {
    sb.append('{')
    writeKeyString(sb, "traceId", link.spanContext.traceIdHex)
    sb.append(',')
    writeKeyString(sb, "spanId", link.spanContext.spanId.toHex)
    sb.append(',')
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, link.attributes)
    sb.append(']')
    sb.append('}')
  }

  // --- Metrics ---

  private def writeNamedMetric(sb: StringBuilder, nm: NamedMetric): Unit = {
    sb.append('{')
    writeKeyString(sb, "name", nm.name)
    sb.append(',')
    writeKeyString(sb, "description", nm.description)
    sb.append(',')
    writeKeyString(sb, "unit", nm.unit)
    sb.append(',')

    nm.data match {
      case MetricData.SumData(points) =>
        writeKey(sb, "sum")
        sb.append('{')
        writeKey(sb, "dataPoints")
        sb.append('[')
        writeList(sb, points)(writeSumDataPoint)
        sb.append(']')
        sb.append(',')
        writeKeyBool(sb, "isMonotonic", true)
        sb.append('}')

      case MetricData.HistogramData(points) =>
        writeKey(sb, "histogram")
        sb.append('{')
        writeKey(sb, "dataPoints")
        sb.append('[')
        writeList(sb, points)(writeHistogramDataPoint)
        sb.append(']')
        sb.append('}')

      case MetricData.GaugeData(points) =>
        writeKey(sb, "gauge")
        sb.append('{')
        writeKey(sb, "dataPoints")
        sb.append('[')
        writeList(sb, points)(writeGaugeDataPoint)
        sb.append(']')
        sb.append('}')
    }

    sb.append('}')
  }

  private def writeSumDataPoint(sb: StringBuilder, pt: SumDataPoint): Unit = {
    sb.append('{')
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, pt.attributes)
    sb.append(']')
    sb.append(',')
    writeKeyLong(sb, "startTimeUnixNano", pt.startTimeNanos)
    sb.append(',')
    writeKeyLong(sb, "timeUnixNano", pt.timeNanos)
    sb.append(',')
    writeKeyLong(sb, "asInt", pt.value)
    sb.append('}')
  }

  private def writeHistogramDataPoint(sb: StringBuilder, pt: HistogramDataPoint): Unit = {
    sb.append('{')
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, pt.attributes)
    sb.append(']')
    sb.append(',')
    writeKeyLong(sb, "startTimeUnixNano", pt.startTimeNanos)
    sb.append(',')
    writeKeyLong(sb, "timeUnixNano", pt.timeNanos)
    sb.append(',')
    writeKeyLong(sb, "count", pt.count)
    sb.append(',')
    writeKeyDouble(sb, "sum", pt.sum)
    sb.append(',')
    writeKeyDouble(sb, "min", pt.min)
    sb.append(',')
    writeKeyDouble(sb, "max", pt.max)
    sb.append(',')

    // bucketCounts as quoted strings
    writeKey(sb, "bucketCounts")
    sb.append('[')
    var i = 0
    while (i < pt.bucketCounts.length) {
      if (i > 0) sb.append(',')
      sb.append('"')
      sb.append(pt.bucketCounts(i))
      sb.append('"')
      i += 1
    }
    sb.append(']')
    sb.append(',')

    // explicitBounds as doubles
    writeKey(sb, "explicitBounds")
    sb.append('[')
    i = 0
    while (i < pt.boundaries.length) {
      if (i > 0) sb.append(',')
      sb.append(pt.boundaries(i))
      i += 1
    }
    sb.append(']')

    sb.append('}')
  }

  private def writeGaugeDataPoint(sb: StringBuilder, pt: GaugeDataPoint): Unit = {
    sb.append('{')
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, pt.attributes)
    sb.append(']')
    sb.append(',')
    writeKeyLong(sb, "timeUnixNano", pt.timeNanos)
    sb.append(',')
    writeKeyDouble(sb, "asDouble", pt.value)
    sb.append('}')
  }

  // --- Log Records ---

  private def writeLogRecord(sb: StringBuilder, log: LogRecord): Unit = {
    sb.append('{')
    writeKeyLong(sb, "timeUnixNano", log.timestampNanos)
    sb.append(',')
    writeKeyLong(sb, "observedTimeUnixNano", log.observedTimestampNanos)
    sb.append(',')
    writeKeyInt(sb, "severityNumber", log.severity.number)
    sb.append(',')
    writeKeyString(sb, "severityText", log.severityText)
    sb.append(',')

    // body as AnyValue
    writeKey(sb, "body")
    sb.append('{')
    writeKeyString(sb, "stringValue", log.body.value)
    sb.append('}')
    sb.append(',')

    // attributes (including deferred throwable stacktrace)
    writeKey(sb, "attributes")
    sb.append('[')
    writeAttributes(sb, log.attributes)
    log.throwable.foreach { t =>
      if (!log.attributes.isEmpty) sb.append(',')
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      sb.append("{\"key\":")
      writeJsonString(sb, "exception.stacktrace")
      sb.append(",\"value\":{")
      writeKeyString(sb, "stringValue", sw.toString)
      sb.append("}}")
    }
    sb.append(']')
    sb.append(',')

    // trace correlation
    writeKeyString(sb, "traceId", if (log.hasTraceId) TraceId.toHex(log.traceIdHi, log.traceIdLo) else "")
    sb.append(',')
    writeKeyString(sb, "spanId", if (log.hasSpanId) String.format("%016x", log.spanId) else "")
    sb.append(',')
    writeKeyInt(sb, "flags", log.traceFlags.toInt & 0xff)

    sb.append('}')
  }
}
