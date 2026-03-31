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

/**
 * Stateless log formatter. Appends formatted output to a StringBuilder.
 * Formatters are singletons — no per-instance state, zero allocation. The
 * StringBuilder is owned and pooled by the emitter.
 */
trait LogFormatter {
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
  ): Unit

  /** Format from a LogRecord (used by FormattedLogRecordProcessor). */
  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}

/**
 * Human-readable text format: "2026-03-31T17:30:00.123Z INFO
 * [MyClass.method:42] message {key=val}"
 */
object TextLogFormatter extends LogFormatter {

  @volatile private var cachedSecond: Long   = 0L
  @volatile private var cachedPrefix: String = ""

  override def format(
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
  ): Unit = {
    // Timestamp — manual UTC formatting, cached per second
    val epochMillis = timestampNanos / 1000000L
    val epochSecond = epochMillis / 1000L
    val millis      = (epochMillis % 1000L).toInt

    if (epochSecond != cachedSecond) {
      cachedSecond = epochSecond
      cachedPrefix = formatDateTimePrefix(epochSecond)
    }
    sb.append(cachedPrefix)
    appendPadded(sb, millis, 3)
    sb.append("Z ")

    // Severity — pad to 5
    sb.append(severityText)
    var pad = 5 - severityText.length
    while (pad > 0) { sb.append(' '); pad -= 1 }

    // Source location from builder arrays
    sb.append(" [")
    val keys    = builder.builderKeys
    val longs   = builder.builderLongs
    val strings = builder.builderStrings
    val len     = builder.builderLen

    var namespace = ""
    var method    = ""
    var lineNo    = 0L
    var i         = 0
    while (i < len && i < 4) {
      keys(i) match {
        case "code.namespace" => if (strings(i) != null) namespace = strings(i)
        case "code.function"  => if (strings(i) != null) method = strings(i)
        case "code.lineno"    => lineNo = longs(i)
        case _                => ()
      }
      i += 1
    }

    if (namespace.nonEmpty) {
      val lastDot = namespace.lastIndexOf('.')
      if (lastDot >= 0) sb.append(namespace, lastDot + 1, namespace.length)
      else sb.append(namespace)
    }
    if (method.nonEmpty) { sb.append('.'); sb.append(method) }
    if (lineNo > 0) { sb.append(':'); sb.append(lineNo) }
    sb.append("] ")

    // Body
    sb.append(body)

    // User attributes (after source location, index 4+)
    val types        = builder.builderTypes
    var hasUserAttrs = false
    i = 4
    while (i < len) {
      if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
      else sb.append(", ")
      sb.append(keys(i)); sb.append('=')
      (types(i): @scala.annotation.switch) match {
        case 0 /* STRING */  => sb.append('"'); sb.append(strings(i)); sb.append('"')
        case 1 /* LONG */    => sb.append(longs(i))
        case 2 /* DOUBLE */  => sb.append(java.lang.Double.longBitsToDouble(longs(i)))
        case 3 /* BOOLEAN */ => sb.append(if (longs(i) != 0L) "true" else "false")
        case _               => sb.append("?")
      }
      i += 1
    }
    if (hasUserAttrs) sb.append('}')

    // Throwable
    throwable.foreach { t =>
      sb.append('\n')
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      sb.append(sw.toString)
    }
  }

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    // Timestamp — manual UTC formatting, cached per second
    val epochMillis = record.timestampNanos / 1000000L
    val epochSecond = epochMillis / 1000L
    val millis      = (epochMillis % 1000L).toInt

    if (epochSecond != cachedSecond) {
      cachedSecond = epochSecond
      cachedPrefix = formatDateTimePrefix(epochSecond)
    }
    sb.append(cachedPrefix)
    appendPadded(sb, millis, 3)
    sb.append("Z ")

    // Severity — pad to 5
    sb.append(record.severityText)
    var pad = 5 - record.severityText.length
    while (pad > 0) { sb.append(' '); pad -= 1 }

    // Source location from record attributes
    sb.append(" [")
    var namespace = ""
    var method    = ""
    var lineNo    = 0L
    record.attributes.accept(new AttributeVisitor {
      override def visitString(key: String, value: String): Unit =
        key match {
          case "code.namespace" => namespace = value
          case "code.function"  => method = value
          case _                => ()
        }

      override def visitLong(key: String, value: Long): Unit =
        if (key == "code.lineno") lineNo = value

      override def visitDouble(key: String, value: Double): Unit           = ()
      override def visitBoolean(key: String, value: Boolean): Unit         = ()
      override def visitStringSeq(key: String, value: Seq[String]): Unit   = ()
      override def visitLongSeq(key: String, value: Seq[Long]): Unit       = ()
      override def visitDoubleSeq(key: String, value: Seq[Double]): Unit   = ()
      override def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = ()
    })

    if (namespace.nonEmpty) {
      val lastDot = namespace.lastIndexOf('.')
      if (lastDot >= 0) sb.append(namespace, lastDot + 1, namespace.length)
      else sb.append(namespace)
    }
    if (method.nonEmpty) { sb.append('.'); sb.append(method) }
    if (lineNo > 0) { sb.append(':'); sb.append(lineNo) }
    sb.append("] ")

    // Body
    sb.append(record.body)

    // User attributes (skip code.* attributes)
    var hasUserAttrs = false
    record.attributes.accept(new AttributeVisitor {
      override def visitString(key: String, value: String): Unit =
        if (!key.startsWith("code.")) {
          if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
          else sb.append(", ")
          sb.append(key).append("=\"").append(value).append('"')
        }

      override def visitLong(key: String, value: Long): Unit =
        if (!key.startsWith("code.")) {
          if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
          else sb.append(", ")
          sb.append(key).append('=').append(value)
        }

      override def visitDouble(key: String, value: Double): Unit =
        if (!key.startsWith("code.")) {
          if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
          else sb.append(", ")
          sb.append(key).append('=').append(value)
        }

      override def visitBoolean(key: String, value: Boolean): Unit =
        if (!key.startsWith("code.")) {
          if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
          else sb.append(", ")
          sb.append(key).append('=').append(value)
        }

      override def visitStringSeq(key: String, value: Seq[String]): Unit   = ()
      override def visitLongSeq(key: String, value: Seq[Long]): Unit       = ()
      override def visitDoubleSeq(key: String, value: Seq[Double]): Unit   = ()
      override def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = ()
    })
    if (hasUserAttrs) sb.append('}')

    // Throwable
    record.throwable.foreach { t =>
      sb.append('\n')
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      sb.append(sw.toString)
    }
  }

  private def formatDateTimePrefix(epochSecond: Long): String = {
    // Civil date calculation from epoch seconds (Rata Die / Howard Hinnant algorithm)
    val totalDays = Math.floorDiv(epochSecond, 86400L).toInt + 719468
    val era       = (if (totalDays >= 0) totalDays else totalDays - 146096) / 146097
    val doe       = totalDays - era * 146097
    val yoe       = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y         = yoe + era * 400
    val doy       = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp        = (5 * doy + 2) / 153
    val d         = doy - (153 * mp + 2) / 5 + 1
    val m         = if (mp < 10) mp + 3 else mp - 9
    val year      = if (m <= 2) y + 1 else y

    val secondOfDay = ((epochSecond % 86400L) + 86400L) % 86400L
    val hour        = (secondOfDay / 3600).toInt
    val minute      = ((secondOfDay % 3600) / 60).toInt
    val second      = (secondOfDay  % 60).toInt

    val csb = new StringBuilder(24)
    appendPadded(csb, year, 4); csb.append('-')
    appendPadded(csb, m, 2); csb.append('-')
    appendPadded(csb, d, 2); csb.append('T')
    appendPadded(csb, hour, 2); csb.append(':')
    appendPadded(csb, minute, 2); csb.append(':')
    appendPadded(csb, second, 2); csb.append('.')
    csb.toString
  }

  private def appendPadded(sb: StringBuilder, value: Int, width: Int): Unit = {
    val s = value.toString
    var i = s.length
    while (i < width) { sb.append('0'); i += 1 }
    sb.append(s)
  }
}

/**
 * JSON format compatible with OTLP log data model:
 * {"timeUnixNano":...,"severityNumber":...,"severityText":"INFO","body":{"stringValue":"message"},"attributes":[...]}
 */
object JsonLogFormatter extends LogFormatter {
  override def format(
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
  ): Unit = {
    sb.append('{')

    // Timestamp
    sb.append("\"timeUnixNano\":\""); sb.append(timestampNanos); sb.append('"')

    // Severity
    sb.append(",\"severityNumber\":"); sb.append(severity.number)
    sb.append(",\"severityText\":\""); sb.append(severityText); sb.append('"')

    // Body
    sb.append(",\"body\":{\"stringValue\":\"")
    writeJsonStringContent(sb, body)
    sb.append("\"}")

    // Attributes
    val keys    = builder.builderKeys
    val types   = builder.builderTypes
    val longs   = builder.builderLongs
    val strings = builder.builderStrings
    val len     = builder.builderLen
    if (len > 0) {
      sb.append(",\"attributes\":[")
      var i = 0
      while (i < len) {
        if (i > 0) sb.append(',')
        sb.append("{\"key\":\""); sb.append(keys(i)); sb.append("\",\"value\":{")
        (types(i): @scala.annotation.switch) match {
          case 0 =>
            sb.append("\"stringValue\":\""); writeJsonStringContent(sb, strings(i)); sb.append('"')
          case 1 => sb.append("\"intValue\":\""); sb.append(longs(i)); sb.append('"')
          case 2 => sb.append("\"doubleValue\":"); sb.append(java.lang.Double.longBitsToDouble(longs(i)))
          case 3 => sb.append("\"boolValue\":"); sb.append(if (longs(i) != 0L) "true" else "false")
          case _ => sb.append("\"stringValue\":\"?\"")
        }
        sb.append("}}")
        i += 1
      }
      sb.append(']')
    }

    // Trace context
    if (traceIdHi != 0L || traceIdLo != 0L) {
      sb.append(",\"traceId\":\"")
      sb.append(TraceId.toHex(traceIdHi, traceIdLo))
      sb.append('"')
    }
    if (spanId != 0L) {
      sb.append(",\"spanId\":\"")
      sb.append(String.format("%016x", spanId: java.lang.Long))
      sb.append('"')
    }

    // Throwable as exception.stacktrace attribute
    throwable.foreach { t =>
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      if (len == 0) sb.append(",\"attributes\":[") else sb.append(',')
      sb.append("{\"key\":\"exception.stacktrace\",\"value\":{\"stringValue\":\"")
      writeJsonStringContent(sb, sw.toString)
      sb.append("\"}}")
      if (len == 0) sb.append(']')
    }

    sb.append('}')
  }

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    sb.append('{')

    // Timestamp
    sb.append("{\"timeUnixNano\":\""); sb.append(record.timestampNanos); sb.append('"')

    // Severity
    sb.append(",\"severityNumber\":"); sb.append(record.severity.number)
    sb.append(",\"severityText\":\""); sb.append(record.severityText); sb.append('"')

    // Body
    sb.append(",\"body\":{\"stringValue\":\"")
    writeJsonStringContent(sb, record.body)
    sb.append("\"}")

    // Attributes from record
    val hasAttrs = record.attributes.size > 0
    if (hasAttrs) {
      sb.append(",\"attributes\":[")
      var first = true
      record.attributes.accept(new AttributeVisitor {
        override def visitString(key: String, value: String): Unit = {
          if (!first) sb.append(',')
          first = false
          sb.append("{\"key\":\""); sb.append(key); sb.append("\",\"value\":{\"")
          sb.append("{\"stringValue\":\""); writeJsonStringContent(sb, value); sb.append('"')
          sb.append("}}")
        }

        override def visitLong(key: String, value: Long): Unit = {
          if (!first) sb.append(',')
          first = false
          sb.append("{\"key\":\""); sb.append(key); sb.append("\",\"value\":{\"")
          sb.append("{\"intValue\":\""); sb.append(value); sb.append('"')
          sb.append("}}")
        }

        override def visitDouble(key: String, value: Double): Unit = {
          if (!first) sb.append(',')
          first = false
          sb.append("{\"key\":\""); sb.append(key); sb.append("\",\"value\":{\"")
          sb.append("{\"doubleValue\": "); sb.append(value)
          sb.append("}}")
        }

        override def visitBoolean(key: String, value: Boolean): Unit = {
          if (!first) sb.append(',')
          first = false
          sb.append("{\"key\":\""); sb.append(key); sb.append("\",\"value\":{\"")
          sb.append("{\"boolValue\":"); sb.append(value)
          sb.append("}}")
        }

        override def visitStringSeq(key: String, value: Seq[String]): Unit   = ()
        override def visitLongSeq(key: String, value: Seq[Long]): Unit       = ()
        override def visitDoubleSeq(key: String, value: Seq[Double]): Unit   = ()
        override def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = ()
      })
      sb.append(']')
    }

    // Trace context
    if (record.traceIdHi != 0L || record.traceIdLo != 0L) {
      sb.append(",\"traceId\":\"")
      sb.append(TraceId.toHex(record.traceIdHi, record.traceIdLo))
      sb.append('"')
    }
    if (record.spanId != 0L) {
      sb.append(",\"spanId\":\"")
      sb.append(String.format("%016x", record.spanId: java.lang.Long))
      sb.append('"')
    }

    // Throwable as exception.stacktrace attribute
    record.throwable.foreach { t =>
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      if (!hasAttrs) sb.append(",\"attributes\":[") else sb.append(',')
      sb.append("{\"key\":\"exception.stacktrace\",\"value\":{\"stringValue\":\"")
      writeJsonStringContent(sb, sw.toString)
      sb.append("\"}}}")
      if (!hasAttrs) sb.append(']')
    }

    sb.append('}')
  }

  private[otel] def writeJsonStringContent(sb: StringBuilder, s: String): Unit = {
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
  }
}
