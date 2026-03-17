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

final class Logger(
  instrumentationScope: InstrumentationScope,
  resource: Resource,
  processors: Seq[LogRecordProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {

  def emit(logRecord: LogRecord): Unit =
    processors.foreach(_.onEmit(logRecord))

  def trace(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Trace, body, attrs)

  def debug(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Debug, body, attrs)

  def info(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Info, body, attrs)

  def warn(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Warn, body, attrs)

  def error(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Error, body, attrs)

  def fatal(body: String, attrs: (String, AttributeValue)*): Unit =
    log(Severity.Fatal, body, attrs)

  private def log(severity: Severity, body: String, attrs: Seq[(String, AttributeValue)]): Unit = {
    val now        = System.nanoTime()
    val spanCtxOpt = contextStorage.get()

    val attrBuilder = Attributes.builder
    attrs.foreach { case (k, v) =>
      v match {
        case AttributeValue.StringValue(s)  => attrBuilder.put(k, s)
        case AttributeValue.BooleanValue(b) => attrBuilder.put(k, b)
        case AttributeValue.LongValue(l)    => attrBuilder.put(k, l)
        case AttributeValue.DoubleValue(d)  => attrBuilder.put(k, d)
        case _                              => attrBuilder.put(k, v.toString)
      }
    }

    val (traceId, spanId, traceFlags) = spanCtxOpt match {
      case Some(ctx) if ctx.isValid =>
        (Some(ctx.traceId), Some(ctx.spanId), Some(ctx.traceFlags))
      case _ =>
        (None, None, None)
    }

    val record = LogRecord(
      timestampNanos = now,
      observedTimestampNanos = now,
      severity = severity,
      severityText = severity.text,
      body = body,
      attributes = attrBuilder.build,
      traceId = traceId,
      spanId = spanId,
      traceFlags = traceFlags,
      resource = resource,
      instrumentationScope = instrumentationScope
    )

    emit(record)
  }
}
