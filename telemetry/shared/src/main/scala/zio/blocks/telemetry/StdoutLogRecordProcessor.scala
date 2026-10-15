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

private[telemetry] class StdoutLogRecordProcessor extends LogRecordProcessor {

  override def onEmit(logRecord: LogRecord): Unit = {
    val sb = new StringBuilder(128)

    // Timestamp — cross-platform ISO-8601 formatting via java.util.Calendar
    val epochMillis = logRecord.timestampNanos / 1000000L
    val cal         = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.setTimeInMillis(epochMillis)
    sb.append(
      String.format(
        "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
        cal.get(java.util.Calendar.YEAR): Integer,
        (cal.get(java.util.Calendar.MONTH) + 1): Integer,
        cal.get(java.util.Calendar.DAY_OF_MONTH): Integer,
        cal.get(java.util.Calendar.HOUR_OF_DAY): Integer,
        cal.get(java.util.Calendar.MINUTE): Integer,
        cal.get(java.util.Calendar.SECOND): Integer,
        cal.get(java.util.Calendar.MILLISECOND): Integer
      )
    )

    // Severity — padded to 5 chars
    sb.append(' ')
    val sevText = logRecord.severityText
    sb.append(sevText)
    var pad = 5 - sevText.length
    while (pad > 0) { sb.append(' '); pad -= 1 }

    // Source location — scanned by stable key from the record attributes. The
    // captured file path is preserved exactly as emitted at compile time.
    var filePath     = ""
    var functionName = ""
    var line         = 0L
    logRecord.attributes.foreach { (key, value) =>
      key match {
        case SourceLocation.CodeFilePath =>
          value match {
            case AttributeValue.StringValue(v) => filePath = v
            case _                             => ()
          }
        case SourceLocation.CodeFunctionName =>
          value match {
            case AttributeValue.StringValue(v) => functionName = v
            case _                             => ()
          }
        case SourceLocation.CodeLineNumber =>
          value match {
            case AttributeValue.LongValue(v)   => line = v
            case AttributeValue.StringValue(v) =>
              if (v != null) v.toLongOption.foreach(parsed => line = parsed)
            case _ => ()
          }
        case _ => ()
      }
    }

    // Body first — escaped so a multi-line message cannot forge extra lines.
    val body = logRecord.body.value
    if (body != null && body.nonEmpty) {
      sb.append(' ')
      TextLogFormatter.appendBodyEscaped(sb, body)
    }

    // Location after the body: compact function separately, then the
    // contiguous captured `path:line` token.
    TextLogLayout.appendLocation(sb, filePath, functionName, line)

    // User attributes (skip source location keys, except an unparseable
    // String-typed code.line.number which stays visible instead of being
    // dropped — same rule as TextLogFormatter).
    var hasUserAttrs = false
    logRecord.attributes.foreach { (key, value) =>
      val visible = value match {
        case AttributeValue.StringValue(v) => TextLogFormatter.showRecordStringAsUserAttr(key, v)
        case _                             => !TextLogFormatter.isCodeKey(key)
      }
      if (visible) {
        if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
        else sb.append(", ")
        TextLogFormatter.appendTextKeyEscaped(sb, key)
        sb.append('=')
        value match {
          case AttributeValue.StringValue(v) => TextLogFormatter.renderTextAttrValue(sb, 0, 0L, v, null)
          case AttributeValue.LongValue(v)   => TextLogFormatter.renderTextAttrValue(sb, 1, v, null, null)
          case AttributeValue.DoubleValue(v) =>
            TextLogFormatter.renderTextAttrValue(sb, 2, java.lang.Double.doubleToRawLongBits(v), null, null)
          case AttributeValue.BooleanValue(v) =>
            TextLogFormatter.renderTextAttrValue(sb, 3, if (v) 1L else 0L, null, null)
          case AttributeValue.StringSeqValue(vs)  => TextLogFormatter.renderTextAttrValue(sb, 4, 0L, null, vs)
          case AttributeValue.LongSeqValue(vs)    => TextLogFormatter.renderTextAttrValue(sb, 5, 0L, null, vs)
          case AttributeValue.DoubleSeqValue(vs)  => TextLogFormatter.renderTextAttrValue(sb, 6, 0L, null, vs)
          case AttributeValue.BooleanSeqValue(vs) => TextLogFormatter.renderTextAttrValue(sb, 7, 0L, null, vs)
        }
      }
    }
    if (hasUserAttrs) sb.append('}')

    // Throwable (if present)
    logRecord.throwable.foreach { t =>
      sb.append('\n')
      val sw = new java.io.StringWriter()
      t.printStackTrace(new java.io.PrintWriter(sw))
      sb.append(sw.toString)
    }

    println(sb.toString)
  }

  override def shutdown(): Unit   = ()
  override def forceFlush(): Unit = ()
}
