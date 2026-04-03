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

  private[otel] val baseProcessors: Seq[LogRecordProcessor] =
    processors.filterNot(_.isInstanceOf[FormattedLogRecordProcessor])

  private val processorMinLevel: Int =
    if (processors.isEmpty) Int.MaxValue
    else {
      var min  = Int.MaxValue
      val iter = processors.iterator
      while (iter.hasNext) {
        val level = iter.next().minimumLevel
        if (level < min) min = level
      }
      min
    }

  // Emitter strategy — chosen once at construction.
  // When the only processor is ConsoleLogRecordProcessor, use the fast
  // FormattedLogEmitter that formats directly from builder arrays (no LogRecord).
  private[otel] val emitter: LogEmitter =
    if (processors.length == 1 && processors.head.isInstanceOf[ConsoleLogRecordProcessor])
      new FormattedLogEmitter(TextLogFormatter, StdoutWriter)
    else
      new StandardLogEmitter(processors, processorMinLevel)

  /**
   * Raw emit — called by macro-generated code. Passes raw values to the
   * emitter.
   */
  private[otel] def emitRaw(
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    resource: Resource,
    instrumentationScope: InstrumentationScope,
    throwable: Option[Throwable]
  ): Unit = emitter.emit(
    timestampNanos,
    severity,
    severityText,
    body,
    builder,
    traceIdHi,
    traceIdLo,
    spanId,
    traceFlags,
    resource,
    instrumentationScope,
    throwable
  )

  def emit(logRecord: LogRecord): Unit =
    if (logRecord.severity.number >= processorMinLevel) {
      try processors.foreach(_.onEmit(logRecord))
      catch {
        case e: Throwable =>
          System.err.println(s"[zio-blocks-otel] logging error: ${e.getMessage}")
      }
    }

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

    val (tidHi, tidLo, sid, tf) = spanCtxOpt match {
      case Some(ctx) if ctx.isValid =>
        (ctx.traceIdHi, ctx.traceIdLo, ctx.spanId.value, ctx.traceFlags.byte)
      case _ =>
        (0L, 0L, 0L, 0.toByte)
    }

    val record = LogRecord(
      timestampNanos = now,
      observedTimestampNanos = now,
      severity = severity,
      severityText = severity.text,
      body = LogMessage(body),
      attributes = attrBuilder.build,
      traceIdHi = tidHi,
      traceIdLo = tidLo,
      spanId = sid,
      traceFlags = tf,
      resource = resource,
      instrumentationScope = instrumentationScope
    )

    emit(record)
  }
}
