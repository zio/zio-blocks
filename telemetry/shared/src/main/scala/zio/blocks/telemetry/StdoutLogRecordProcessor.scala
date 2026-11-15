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
 * Prints records as text lines; [[ConsoleLogRecordProcessor]] delegates to it.
 */
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

    // Body first — escaped so a multi-line message cannot forge extra lines.
    val body = logRecord.body.value
    if (body != null && body.nonEmpty) {
      sb.append(' ')
      TextLogFormatter.appendBodyEscaped(sb, body)
    }

    // Source location from dedicated fields — never from user attributes. The
    // captured file path is preserved exactly as emitted at compile time.
    TextLogLayout.appendLocation(sb, logRecord.sourceFile, logRecord.sourceFunction, logRecord.sourceLine.toLong)

    // User attributes via the shared raw-typed loop: no boxing, no callbacks.
    val attrs = logRecord.userAttributes
    TextLogFormatter.appendUserAttributesText(
      sb,
      attrs.keys,
      attrs.types,
      attrs.longs,
      attrs.strings,
      attrs.seqs,
      attrs.len,
      logRecord.sourceFile,
      logRecord.sourceFunction,
      logRecord.sourceLine
    )

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
