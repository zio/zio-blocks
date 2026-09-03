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
    renderHead(sb, timestampNanos, severityText)

    // Source location — matched by key over all slots. The macro path always
    // writes the four code.* attributes first, but direct Logger calls never
    // write them, so position-based matching would misread user attributes.
    val keys    = builder.builderKeys
    val longs   = builder.builderLongs
    val strings = builder.builderStrings
    val len     = builder.builderLen

    var namespace = ""
    var method    = ""
    var lineNo    = 0L
    var i         = 0
    while (i < len) {
      keys(i) match {
        case "code.namespace" => if (strings(i) != null) namespace = strings(i)
        case "code.function"  => if (strings(i) != null) method = strings(i)
        case "code.lineno"    => lineNo = longs(i)
        case _                => ()
      }
      i += 1
    }
    renderLocation(sb, namespace, method, lineNo)

    // Body
    sb.append(body)

    // User attributes — everything that is not a code.* attribute, in slot
    // order (same rule as formatRecord).
    val types        = builder.builderTypes
    val seqs         = builder.builderSeqs
    var hasUserAttrs = false
    i = 0
    while (i < len) {
      if (!keys(i).startsWith("code.")) {
        if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
        else sb.append(", ")
        sb.append(keys(i)); sb.append('=')
        val tpe = types(i)
        renderTextAttrValue(sb, tpe, longs(i), strings(i), if (tpe >= 4) seqValueAt(seqs, i) else null)
      }
      i += 1
    }
    if (hasUserAttrs) sb.append('}')

    renderThrowable(sb, throwable)
  }

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    renderHead(sb, record.timestampNanos, record.severityText)

    // Source location from record attributes
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
    renderLocation(sb, namespace, method, lineNo)

    // Body
    sb.append(record.body.value)

    // User attributes (skip code.* attributes)
    var hasUserAttrs = false
    record.attributes.accept(new AttributeVisitor {
      private def nextAttr(key: String): Unit = {
        if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
        else sb.append(", ")
        sb.append(key).append('=')
      }

      override def visitString(key: String, value: String): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 0, 0L, value, null)
        }

      override def visitLong(key: String, value: Long): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 1, value, null, null)
        }

      override def visitDouble(key: String, value: Double): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 2, java.lang.Double.doubleToRawLongBits(value), null, null)
        }

      override def visitBoolean(key: String, value: Boolean): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 3, if (value) 1L else 0L, null, null)
        }

      override def visitStringSeq(key: String, value: Seq[String]): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 4, 0L, null, value)
        }

      override def visitLongSeq(key: String, value: Seq[Long]): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 5, 0L, null, value)
        }

      override def visitDoubleSeq(key: String, value: Seq[Double]): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 6, 0L, null, value)
        }

      override def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit =
        if (!key.startsWith("code.")) {
          nextAttr(key)
          renderTextAttrValue(sb, 7, 0L, null, value)
        }
    })
    if (hasUserAttrs) sb.append('}')

    renderThrowable(sb, record.throwable)
  }

  /** Shared head: timestamp plus severity, used by both entry points. */
  private def renderHead(sb: StringBuilder, timestampNanos: Long, severityText: String): Unit = {
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
  }

  /** Shared source-location rendering, used by both entry points. */
  private def renderLocation(sb: StringBuilder, namespace: String, method: String, lineNo: Long): Unit = {
    sb.append(" [")
    if (namespace.nonEmpty) {
      val lastDot = namespace.lastIndexOf('.')
      if (lastDot >= 0) sb.append(namespace, lastDot + 1, namespace.length)
      else sb.append(namespace)
    }
    if (method.nonEmpty) { sb.append('.'); sb.append(method) }
    if (lineNo > 0) { sb.append(':'); sb.append(lineNo) }
    sb.append("] ")
  }

  /**
   * Shared attribute-value rendering, used by both entry points. Scalar slots
   * arrive unboxed (`long`/`str`); `seq` is only consulted for seq-typed
   * attributes and may be `null` otherwise.
   */
  private def renderTextAttrValue(sb: StringBuilder, tpe: Byte, long: Long, str: String, seq: Seq[Any]): Unit =
    (tpe: @scala.annotation.switch) match {
      case 0 /* STRING */      => sb.append('"'); sb.append(str); sb.append('"')
      case 1 /* LONG */        => sb.append(long)
      case 2 /* DOUBLE */      => sb.append(java.lang.Double.longBitsToDouble(long))
      case 3 /* BOOLEAN */     => sb.append(if (long != 0L) "true" else "false")
      case 4 /* STRING_SEQ */  => appendStringSeq(sb, if (seq == null) Seq.empty else seq)
      case 5 /* LONG_SEQ */    => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case 6 /* DOUBLE_SEQ */  => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case 7 /* BOOLEAN_SEQ */ => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case _                   => sb.append("?")
    }

  /** Shared throwable rendering, used by both entry points. */
  private def renderThrowable(sb: StringBuilder, throwable: Option[Throwable]): Unit =
    throwable.foreach { t =>
      sb.append('\n')
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      sb.append(sw.toString)
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

  /**
   * Reads a Seq value from the raw side-array, tolerating a null array or slot
   * (which never occur for a real seq-typed attribute, but keeps the hot path
   * defensive). Seq attributes are rare, so allocation here is acceptable.
   */
  private def seqValueAt(seqs: Array[AnyRef], i: Int): Seq[Any] =
    if (seqs == null || seqs(i) == null) Seq.empty
    else seqs(i).asInstanceOf[Seq[Any]]

  /** Renders a String seq as `["a", "b"]` with each element double-quoted. */
  private def appendStringSeq(sb: StringBuilder, value: Seq[Any]): Unit = {
    sb.append('[')
    var first = true
    value.foreach { v =>
      if (first) first = false else sb.append(", ")
      sb.append('"').append(v.toString).append('"')
    }
    sb.append(']')
  }

  /** Renders a scalar (Long/Double/Boolean) seq as `[1, 2, 3]`, unquoted. */
  private def appendScalarSeq(sb: StringBuilder, value: Seq[Any]): Unit = {
    sb.append('[')
    var first = true
    value.foreach { v =>
      if (first) first = false else sb.append(", ")
      sb.append(v.toString)
    }
    sb.append(']')
  }
}

/**
 * JSON format compatible with OTLP log data model:
 * {"timeUnixNano":...,"severityNumber":...,"severityText":"INFO","body":{"stringValue":"message"},"attributes":[...]}
 */
object JsonLogFormatter extends LogFormatter {
  private sealed trait JsonAttrValue
  private object JsonAttrValue {
    final case class StringValue(value: String)             extends JsonAttrValue
    final case class IntValue(value: Long)                  extends JsonAttrValue
    final case class DoubleValue(value: Double)             extends JsonAttrValue
    final case class BoolValue(value: Boolean)              extends JsonAttrValue
    final case class ArrayValue(values: Seq[JsonAttrValue]) extends JsonAttrValue
  }

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
    renderJsonHead(sb, timestampNanos, severity.number, severityText, body)

    var hasAttrs = false

    // Attributes
    val keys    = builder.builderKeys
    val types   = builder.builderTypes
    val longs   = builder.builderLongs
    val strings = builder.builderStrings
    val seqs    = builder.builderSeqs
    val len     = builder.builderLen
    if (len > 0) {
      hasAttrs = true
      sb.append(",\"attributes\":[")
      var i = 0
      while (i < len) {
        if (i > 0) sb.append(',')
        val value: JsonAttrValue = (types(i): @scala.annotation.switch) match {
          case 0 => JsonAttrValue.StringValue(strings(i))
          case 1 => JsonAttrValue.IntValue(longs(i))
          case 2 => JsonAttrValue.DoubleValue(java.lang.Double.longBitsToDouble(longs(i)))
          case 3 => JsonAttrValue.BoolValue(longs(i) != 0L)
          case 4 => jsonStringSeq(seqValueAt(seqs, i))
          case 5 => jsonLongSeq(seqValueAt(seqs, i))
          case 6 => jsonDoubleSeq(seqValueAt(seqs, i))
          case 7 => jsonBooleanSeq(seqValueAt(seqs, i))
          case _ => JsonAttrValue.StringValue("?")
        }
        appendJsonAttribute(sb, keys(i), value)
        i += 1
      }
      sb.append(']')
    }

    // Trace context
    renderJsonTrace(sb, traceIdHi, traceIdLo, spanId)

    // Throwable as exception.stacktrace attribute
    throwable.foreach { t =>
      hasAttrs = renderJsonThrowableAttr(sb, t, hasAttrs)
    }

    sb.append('}')
  }

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    renderJsonHead(sb, record.timestampNanos, record.severity.number, record.severityText, record.body.value)

    // Attributes from record
    var hasAttrs = record.attributes.size > 0
    if (hasAttrs) {
      sb.append(",\"attributes\":[")
      var first = true
      record.attributes.accept(new AttributeVisitor {
        override def visitString(key: String, value: String): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, JsonAttrValue.StringValue(value))
        }

        override def visitLong(key: String, value: Long): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, JsonAttrValue.IntValue(value))
        }

        override def visitDouble(key: String, value: Double): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, JsonAttrValue.DoubleValue(value))
        }

        override def visitBoolean(key: String, value: Boolean): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, JsonAttrValue.BoolValue(value))
        }

        override def visitStringSeq(key: String, value: Seq[String]): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, jsonStringSeq(value))
        }

        override def visitLongSeq(key: String, value: Seq[Long]): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, jsonLongSeq(value))
        }

        override def visitDoubleSeq(key: String, value: Seq[Double]): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, jsonDoubleSeq(value))
        }

        override def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = {
          if (!first) sb.append(',')
          first = false
          appendJsonAttribute(sb, key, jsonBooleanSeq(value))
        }
      })
      sb.append(']')
    }

    // Trace context
    renderJsonTrace(sb, record.traceIdHi, record.traceIdLo, record.spanId)

    // Throwable as exception.stacktrace attribute
    record.throwable.foreach { t =>
      hasAttrs = renderJsonThrowableAttr(sb, t, hasAttrs)
    }

    sb.append('}')
  }

  /** Shared JSON head: envelope, timestamp, severity, and body. */
  private def renderJsonHead(
    sb: StringBuilder,
    timestampNanos: Long,
    severityNumber: Int,
    severityText: String,
    body: String
  ): Unit = {
    sb.append('{')

    // Timestamp
    sb.append("\"timeUnixNano\":\""); sb.append(timestampNanos); sb.append('"')

    // Severity
    sb.append(",\"severityNumber\":"); sb.append(severityNumber)
    sb.append(",\"severityText\":\""); sb.append(severityText); sb.append('"')

    // Body
    sb.append(",\"body\":{\"stringValue\":\"")
    writeJsonStringContent(sb, body)
    sb.append("\"}")
  }

  /**
   * Shared JSON trace-correlation block (hand-rolled hex, no String.format).
   */
  private def renderJsonTrace(sb: StringBuilder, traceIdHi: Long, traceIdLo: Long, spanId: Long): Unit = {
    if (traceIdHi != 0L || traceIdLo != 0L) {
      sb.append(",\"traceId\":\"")
      sb.append(TraceId.toHex(traceIdHi, traceIdLo))
      sb.append('"')
    }
    if (spanId != 0L) {
      sb.append(",\"spanId\":\"")
      Hex.appendLong16(sb, spanId)
      sb.append('"')
    }
  }

  /**
   * Shared JSON throwable rendering as an `exception.stacktrace` attribute.
   * Returns the updated `hasAttrs` flag.
   */
  private def renderJsonThrowableAttr(sb: StringBuilder, t: Throwable, hasAttrs: Boolean): Boolean = {
    val sw = new java.io.StringWriter()
    t.printStackTrace(new java.io.PrintWriter(sw))
    val updated =
      if (!hasAttrs) {
        sb.append(",\"attributes\":[")
        true
      } else {
        sb.setLength(sb.length - 1)
        sb.append(',')
        hasAttrs
      }
    appendJsonAttribute(sb, "exception.stacktrace", JsonAttrValue.StringValue(sw.toString))
    sb.append(']')
    updated
  }

  private def appendJsonAttribute(sb: StringBuilder, key: String, value: JsonAttrValue): Unit = {
    sb.append("{\"key\":\"")
    writeJsonStringContent(sb, key)
    sb.append("\",\"value\":{")
    value match {
      case JsonAttrValue.StringValue(stringValue) =>
        sb.append("\"stringValue\":\"")
        writeJsonStringContent(sb, stringValue)
        sb.append('"')
      case JsonAttrValue.IntValue(intValue) =>
        sb.append("\"intValue\":\"")
        sb.append(intValue)
        sb.append('"')
      case JsonAttrValue.DoubleValue(doubleValue) =>
        sb.append("\"doubleValue\":")
        sb.append(doubleValue)
      case JsonAttrValue.BoolValue(boolValue) =>
        sb.append("\"boolValue\":")
        sb.append(boolValue)
      case JsonAttrValue.ArrayValue(values) =>
        sb.append("\"arrayValue\":{\"values\":[")
        var first = true
        values.foreach { element =>
          if (first) first = false else sb.append(',')
          sb.append('{')
          appendJsonScalarValue(sb, element)
          sb.append('}')
        }
        sb.append("]}")
    }
    sb.append("}}")
  }

  /** Appends the inner `"kind":value` fragment for a scalar JsonAttrValue. */
  private def appendJsonScalarValue(sb: StringBuilder, value: JsonAttrValue): Unit =
    value match {
      case JsonAttrValue.StringValue(stringValue) =>
        sb.append("\"stringValue\":\"")
        writeJsonStringContent(sb, stringValue)
        sb.append('"')
      case JsonAttrValue.IntValue(intValue) =>
        sb.append("\"intValue\":\"")
        sb.append(intValue)
        sb.append('"')
      case JsonAttrValue.DoubleValue(doubleValue) =>
        sb.append("\"doubleValue\":")
        sb.append(doubleValue)
      case JsonAttrValue.BoolValue(boolValue) =>
        sb.append("\"boolValue\":")
        sb.append(boolValue)
      case JsonAttrValue.ArrayValue(_) => ()
    }

  private def jsonStringSeq(value: Seq[Any]): JsonAttrValue =
    JsonAttrValue.ArrayValue(value.map(v => JsonAttrValue.StringValue(v.toString)))

  private def jsonLongSeq(value: Seq[Any]): JsonAttrValue =
    JsonAttrValue.ArrayValue(value.map(v => JsonAttrValue.IntValue(v.asInstanceOf[Long])))

  private def jsonDoubleSeq(value: Seq[Any]): JsonAttrValue =
    JsonAttrValue.ArrayValue(value.map(v => JsonAttrValue.DoubleValue(v.asInstanceOf[Double])))

  private def jsonBooleanSeq(value: Seq[Any]): JsonAttrValue =
    JsonAttrValue.ArrayValue(value.map(v => JsonAttrValue.BoolValue(v.asInstanceOf[Boolean])))

  private def seqValueAt(seqs: Array[AnyRef], i: Int): Seq[Any] =
    if (seqs == null || seqs(i) == null) Seq.empty
    else seqs(i).asInstanceOf[Seq[Any]]

  private[telemetry] def writeJsonStringContent(sb: StringBuilder, s: String): Unit = {
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
            Hex.appendChar4(sb, c.toInt)
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
  }
}
