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

private[otel] class StdoutLogRecordProcessor extends LogRecordProcessor {

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

    // Source location — [Namespace.method:line]
    sb.append(" [")
    var namespace = ""
    var method    = ""
    var line      = 0L
    logRecord.attributes.foreach { (key, value) =>
      key match {
        case "code.namespace" =>
          value match { case AttributeValue.StringValue(v) => namespace = v; case _ => }
        case "code.function" =>
          value match { case AttributeValue.StringValue(v) => method = v; case _ => }
        case "code.lineno" =>
          value match { case AttributeValue.LongValue(v) => line = v; case _ => }
        case _ => ()
      }
    }
    if (namespace.nonEmpty) {
      val lastDot = namespace.lastIndexOf('.')
      if (lastDot >= 0) sb.append(namespace.substring(lastDot + 1))
      else sb.append(namespace)
    }
    if (method.nonEmpty) { sb.append('.'); sb.append(method) }
    if (line > 0) { sb.append(':'); sb.append(line) }
    sb.append("] ")

    // Body
    sb.append(logRecord.body)

    // User attributes (skip source location keys)
    var hasUserAttrs = false
    logRecord.attributes.foreach { (key, value) =>
      if (
        key != "code.filepath" && key != "code.namespace" &&
        key != "code.function" && key != "code.lineno"
      ) {
        if (!hasUserAttrs) { sb.append(" {"); hasUserAttrs = true }
        else sb.append(", ")
        sb.append(key)
        sb.append('=')
        value match {
          case AttributeValue.StringValue(v)  => sb.append('"'); sb.append(v); sb.append('"')
          case AttributeValue.LongValue(v)    => sb.append(v)
          case AttributeValue.DoubleValue(v)  => sb.append(v)
          case AttributeValue.BooleanValue(v) => sb.append(v)
          case other                          => sb.append(other.toString)
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
