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
 * Renders one log record as text, appended to a `StringBuilder`; `log.writer`
 * pairs a formatter with a [[LogWriter]]. The built-in ones are
 * [[TextLogFormatter]] and [[JsonLogFormatter]].
 *
 * Each call receives a new, empty `StringBuilder`, which the caller writes out
 * afterwards. An output added with `log.writer` calls only `formatRecord`.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/log-formatter/ LogFormatter]]
 */
trait LogFormatter {

  /**
   * Renders a log call from its raw values, without a [[LogRecord]]. Only the
   * library's default console output calls it, always with
   * [[TextLogFormatter]], so a custom formatter can mirror `formatRecord` for
   * the scalar fields here.
   */
  def format(
    sb: StringBuilder,
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit

  /**
   * Appends `record`, rendered, to `sb`; outputs added with `log.writer` call
   * it.
   */
  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}

/**
 * Renders a record as one human-readable line: the UTC timestamp, the severity
 * text padded to five characters, the source location, the message and the
 * other attributes, as in
 * `2026-11-06T18:43:36.815Z WARN  [Checkout.reserve:42] stock low {sku="sku-42", left=3}`.
 * A record with an exception adds its stack trace on the following lines.
 *
 * Human-readable text format: "2026-03-31T17:30:00.123Z INFO message
 * [MyClass.method] /captured/path/MyClass.scala:42 {key=val}"
 *
 * The body comes first so the message reads naturally; the compact function
 * renders separately in brackets and the captured compile-time file path
 * follows as a contiguous `path:line` token for terminal navigation. The path
 * is preserved exactly as captured (never absolutized, never shortened) and the
 * method never appears inside the `path:line` token. Without a path the compact
 * `[Class.method:line]` fallback stays informational; a bare line renders as
 * `[line:42]`; with no location at all nothing renders.
 *
 * The date and time up to the second are cached between calls. A known bug: the
 * cache is updated in two steps, so when two threads format records from
 * different seconds at the same moment, a line can carry the date and time of
 * the other record, down to the second.
 *
 * Source metadata comes from dedicated fields — never from scanning user
 * attributes. String attribute values render double-quoted. A value containing
 * `"`, `\`, or a line break would otherwise blur where the value ends, so those
 * characters are backslash-escaped (`\"`, `\\`, `\n`, `\r`, `\t`) — in both
 * scalar strings and string-seq elements, on both entry points. Location
 * file/function text escapes CR, LF, tab, and other control characters while
 * leaving everything else literal — including `\`, so Windows `C:\...`
 * separators survive intact — and normal path separators and spaces untouched.
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
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit =
    appendRecordText(
      sb,
      timestampNanos,
      severityText,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      builder.builderKeys,
      builder.builderTypes,
      builder.builderLongs,
      builder.builderStrings,
      builder.builderSeqs,
      builder.builderLen,
      throwable
    )

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    val attrs = record.userAttributes
    appendRecordText(
      sb,
      record.timestampNanos,
      record.severityText,
      record.body.value,
      record.sourceFile,
      record.sourceFunction,
      record.sourceLine,
      attrs.keys,
      attrs.types,
      attrs.longs,
      attrs.strings,
      attrs.seqs,
      attrs.len,
      record.throwable
    )
  }

  /**
   * Shared text rendering core for the builder path, the retained-record path,
   * and the stdout processor: head, body, source location from dedicated
   * fields, raw user attributes, throwable. No boxing, no callbacks, no
   * per-record allocations beyond what the caller owns.
   */
  private def appendRecordText(
    sb: StringBuilder,
    timestampNanos: Long,
    severityText: String,
    body: String,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    keys: Array[String],
    types: Array[Byte],
    longs: Array[Long],
    strings: Array[String],
    seqs: Array[AnyRef],
    len: Int,
    throwable: Option[Throwable]
  ): Unit = {
    renderHead(sb, timestampNanos, severityText)

    // Body first — CR/LF escaped so a multi-line message cannot forge extra lines.
    if (body != null && body.nonEmpty) {
      sb.append(' ')
      appendBodyEscaped(sb, body)
    }

    // Source location from dedicated fields — no attribute scan. Absent
    // fields ("" / 0) render nothing, per TextLogLayout.
    TextLogLayout.appendLocation(sb, sourceFile, sourceFunction, sourceLine.toLong)

    appendUserAttributesText(sb, keys, types, longs, strings, seqs, len, sourceFile, sourceFunction, sourceLine)

    renderThrowable(sb, throwable)
  }

  /**
   * Package-internal raw user-attribute loop over the typed parallel arrays: no
   * `AttributeValue` wrappers, no visitor object, no per-attribute allocation.
   * A user attribute colliding with a canonical source key is suppressed only
   * when that source field is present (see LogRecord.isSourceCollision);
   * otherwise it renders as an ordinary attribute. Shared with the stdout
   * processor.
   */
  private[telemetry] def appendUserAttributesText(
    sb: StringBuilder,
    keys: Array[String],
    types: Array[Byte],
    longs: Array[Long],
    strings: Array[String],
    seqs: Array[AnyRef],
    len: Int,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int
  ): Unit = {
    var hasUserAttrs = false
    var i            = 0
    while (i < len) {
      if (!LogRecord.isSourceCollision(keys(i), sourceFile, sourceFunction, sourceLine)) {
        if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
        else sb.append(", ")
        appendTextKeyEscaped(sb, keys(i)); sb.append('=')
        val tpe = types(i)
        renderTextAttrValue(sb, tpe, longs(i), strings(i), if (tpe >= 4) seqValueAt(seqs, i) else null)
      }
      i += 1
    }
    if (hasUserAttrs) sb.append('}')
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

  /**
   * Shared attribute-value rendering, used by both entry points and the stdout
   * processor. Scalar slots arrive unboxed (`long`/`str`); `seq` is only
   * consulted for seq-typed attributes and may be `null` otherwise.
   */
  private[telemetry] def renderTextAttrValue(
    sb: StringBuilder,
    tpe: Byte,
    long: Long,
    str: String,
    seq: Seq[Any]
  ): Unit =
    (tpe: @scala.annotation.switch) match {
      case 0 /* STRING */      => sb.append('"'); appendTextEscaped(sb, str); sb.append('"')
      case 1 /* LONG */        => sb.append(long)
      case 2 /* DOUBLE */      => sb.append(java.lang.Double.longBitsToDouble(long))
      case 3 /* BOOLEAN */     => sb.append(if (long != 0L) "true" else "false")
      case 4 /* STRING_SEQ */  => appendStringSeq(sb, if (seq == null) Seq.empty else seq)
      case 5 /* LONG_SEQ */    => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case 6 /* DOUBLE_SEQ */  => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case 7 /* BOOLEAN_SEQ */ => appendScalarSeq(sb, if (seq == null) Seq.empty else seq)
      case _                   => sb.append("?")
    }

  /**
   * Appends a string attribute value with `"`, `\`, and line-break characters
   * backslash-escaped so quoted values stay unambiguous. A null value keeps the
   * historical rendering. Shared with the stdout processor.
   */
  private[telemetry] def appendTextEscaped(sb: StringBuilder, s: String): Unit =
    if (s == null) sb.append(s)
    else {
      var i = 0
      while (i < s.length) {
        val c = s.charAt(i)
        c match {
          case '"'  => sb.append("\\\"")
          case '\\' => sb.append("\\\\")
          case '\n' => sb.append("\\n")
          case '\r' => sb.append("\\r")
          case '\t' => sb.append("\\t")
          case _    => sb.append(c)
        }
        i += 1
      }
    }

  /**
   * Appends a text attribute key with `"`, `\`, line breaks, and `=` escaped so
   * a user-controlled key cannot forge extra lines or fake `key=value` pairs.
   * Ordinary keys (no special characters) render unchanged. Shared with the
   * stdout processor.
   */
  private[telemetry] def appendTextKeyEscaped(sb: StringBuilder, s: String): Unit =
    if (s == null) sb.append(s)
    else {
      var i = 0
      while (i < s.length) {
        val c = s.charAt(i)
        c match {
          case '"'  => sb.append("\\\"")
          case '\\' => sb.append("\\\\")
          case '\n' => sb.append("\\n")
          case '\r' => sb.append("\\r")
          case '\t' => sb.append("\\t")
          case '='  => sb.append("\\=")
          case _    => sb.append(c)
        }
        i += 1
      }
    }

  /**
   * Appends a log body with `\`, CR, and LF escaped so a multi-line message
   * cannot forge extra physical lines. Ordinary single-line bodies render
   * unchanged. Shared with the stdout processor.
   */
  private[telemetry] def appendBodyEscaped(sb: StringBuilder, s: String): Unit =
    if (s == null) sb.append(s)
    else {
      var i = 0
      while (i < s.length) {
        val c = s.charAt(i)
        c match {
          case '\\' => sb.append("\\\\")
          case '\n' => sb.append("\\n")
          case '\r' => sb.append("\\r")
          case _    => sb.append(c)
        }
        i += 1
      }
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

  /**
   * Renders a String seq as `["a", "b"]` with each element double-quoted and
   * escaped.
   */
  private def appendStringSeq(sb: StringBuilder, value: Seq[Any]): Unit = {
    sb.append('[')
    var first = true
    value.foreach { v =>
      if (first) first = false else sb.append(", ")
      sb.append('"')
      val element = if (v == null) null else v.toString
      appendTextEscaped(sb, element)
      sb.append('"')
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
 * Renders a record as one JSON object in the shape of the OTLP log data model,
 * such as
 * `{"timeUnixNano":"1793990790777118658","severityNumber":9,"severityText":"INFO","body":{"stringValue":"order placed"},"attributes":[...]}`.
 *
 * Every attribute is listed, the `code.*` source location included, followed by
 * `traceId` and `spanId` when the record has them. An exception becomes an
 * `exception.stacktrace` attribute. A known bug: when a record has attributes,
 * a trace or span id and an exception, the line is not valid JSON, because the
 * closing quote of the last id is dropped and the stack trace attribute follows
 * it. A `Double` attribute that is `NaN` or infinite is written as a bare `NaN`
 * or `Infinity`, which is not valid JSON either, a known bug.
 *
 * Source metadata is emitted first as canonical attributes (`code.file.path`,
 * `code.function.name`, `code.line.number`), followed by user attributes and
 * finally the throwable's `exception.stacktrace`; all of them share one
 * `"attributes"` array, which is closed once — before the `traceId`/`spanId`
 * fields. A user attribute colliding with a canonical key is suppressed only
 * when that source field is present (see `LogRecord.isSourceCollision`);
 * otherwise it passes through unchanged.
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
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit = {
    renderJsonHead(sb, timestampNanos, severity.number, severityText, body)

    var hasAttrs = false

    // Canonical source attributes first, then user attributes.
    hasAttrs = appendJsonSource(sb, sourceFile, sourceFunction, sourceLine, hasAttrs)

    hasAttrs = appendUserAttributesJson(
      sb,
      builder.builderKeys,
      builder.builderTypes,
      builder.builderLongs,
      builder.builderStrings,
      builder.builderSeqs,
      builder.builderLen,
      sourceFile,
      sourceFunction,
      sourceLine,
      hasAttrs
    )

    // Throwable as exception.stacktrace attribute, inside the same array.
    throwable.foreach { t =>
      hasAttrs = renderJsonThrowableAttr(sb, t, hasAttrs)
    }

    // The attributes array stays open until every source/user/throwable entry
    // is appended, so it is closed exactly once here — before the trace fields.
    if (hasAttrs) sb.append(']')

    // Trace context
    renderJsonTrace(sb, traceIdHi, traceIdLo, spanId)

    sb.append('}')
  }

  override def formatRecord(sb: StringBuilder, record: LogRecord): Unit = {
    renderJsonHead(sb, record.timestampNanos, record.severity.number, record.severityText, record.body.value)

    var hasAttrs = false

    // Canonical source attributes first, then user attributes.
    hasAttrs = appendJsonSource(sb, record.sourceFile, record.sourceFunction, record.sourceLine, hasAttrs)

    val attrs = record.userAttributes
    hasAttrs = appendUserAttributesJson(
      sb,
      attrs.keys,
      attrs.types,
      attrs.longs,
      attrs.strings,
      attrs.seqs,
      attrs.len,
      record.sourceFile,
      record.sourceFunction,
      record.sourceLine,
      hasAttrs
    )

    // Throwable as exception.stacktrace attribute, inside the same array.
    record.throwable.foreach { t =>
      hasAttrs = renderJsonThrowableAttr(sb, t, hasAttrs)
    }

    // The attributes array stays open until every source/user/throwable entry
    // is appended, so it is closed exactly once here — before the trace fields.
    if (hasAttrs) sb.append(']')

    // Trace context
    renderJsonTrace(sb, record.traceIdHi, record.traceIdLo, record.spanId)

    sb.append('}')
  }

  /**
   * Appends canonical source attributes. Returns the updated `hasAttrs` flag.
   * Emits nothing when no source field is present.
   */
  private def appendJsonSource(
    sb: StringBuilder,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    hasAttrs: Boolean
  ): Boolean = {
    var wrote = hasAttrs
    if (sourceFile != null && sourceFile.nonEmpty) {
      wrote = beginJsonAttribute(sb, wrote)
      appendJsonAttribute(sb, SourceLocation.CodeFilePath, JsonAttrValue.StringValue(sourceFile))
    }
    if (sourceFunction != null && sourceFunction.nonEmpty) {
      wrote = beginJsonAttribute(sb, wrote)
      appendJsonAttribute(sb, SourceLocation.CodeFunctionName, JsonAttrValue.StringValue(sourceFunction))
    }
    if (sourceLine != 0) {
      wrote = beginJsonAttribute(sb, wrote)
      appendJsonAttribute(sb, SourceLocation.CodeLineNumber, JsonAttrValue.IntValue(sourceLine.toLong))
    }
    wrote
  }

  /**
   * Appends user attributes from the raw typed parallel arrays — no
   * `AttributeValue` wrappers, no visitor object, no per-attribute allocation.
   * Canonical-key collisions suppress only when the matching source field is
   * present. Returns the updated `hasAttrs` flag.
   */
  private def appendUserAttributesJson(
    sb: StringBuilder,
    keys: Array[String],
    types: Array[Byte],
    longs: Array[Long],
    strings: Array[String],
    seqs: Array[AnyRef],
    len: Int,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    hasAttrs: Boolean
  ): Boolean = {
    var wrote = hasAttrs
    var i     = 0
    while (i < len) {
      if (!LogRecord.isSourceCollision(keys(i), sourceFile, sourceFunction, sourceLine)) {
        wrote = beginJsonAttribute(sb, wrote)
        appendJsonAttribute(sb, keys(i), jsonValueFor(types(i), longs(i), strings(i), seqValueAt(seqs, i)))
      }
      i += 1
    }
    wrote
  }

  /**
   * Builds the JSON value for one raw slot without boxing through
   * `AttributeValue`.
   */
  private def jsonValueFor(tpe: Byte, long: Long, str: String, seq: Seq[Any]): JsonAttrValue =
    (tpe: @scala.annotation.switch) match {
      case 0 => JsonAttrValue.StringValue(str)
      case 1 => JsonAttrValue.IntValue(long)
      case 2 => JsonAttrValue.DoubleValue(java.lang.Double.longBitsToDouble(long))
      case 3 => JsonAttrValue.BoolValue(long != 0L)
      case 4 => jsonStringSeq(seq)
      case 5 => jsonLongSeq(seq)
      case 6 => jsonDoubleSeq(seq)
      case 7 => jsonBooleanSeq(seq)
      case _ => JsonAttrValue.StringValue("?")
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
   * Starts or continues the open attributes array and leaves it open; the
   * caller closes it once after the last entry (see [[beginJsonAttribute]]).
   * Returns the updated `hasAttrs` flag.
   */
  private def renderJsonThrowableAttr(sb: StringBuilder, t: Throwable, hasAttrs: Boolean): Boolean = {
    val sw = new java.io.StringWriter()
    t.printStackTrace(new java.io.PrintWriter(sw))
    beginJsonAttribute(sb, hasAttrs)
    appendJsonAttribute(sb, "exception.stacktrace", JsonAttrValue.StringValue(sw.toString))
    true
  }

  /**
   * Starts or continues the shared `"attributes"` array: opens it (with the
   * leading comma) when no entry has been written yet, otherwise appends the
   * separator comma. Always reports `true` so callers thread a single
   * `hasAttrs` flag through source, user and throwable entries without ever
   * splicing the already-emitted JSON.
   */
  private def beginJsonAttribute(sb: StringBuilder, hasAttrs: Boolean): Boolean = {
    if (hasAttrs) sb.append(',')
    else sb.append(",\"attributes\":[")
    true
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
