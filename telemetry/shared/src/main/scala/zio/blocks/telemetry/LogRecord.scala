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

import scala.language.implicitConversions

/**
 * Log message body — either a simple string or a structured template.
 * Structured templates defer string construction until formatting.
 */
sealed trait LogMessage {

  /**
   * Get the formatted string representation. Lazy for Templated.
   */
  def value: String
}

object LogMessage {

  /**
   * Simple string message — already formatted.
   */
  final case class Simple(text: String) extends LogMessage {
    def value: String             = text
    override def toString: String = text
  }

  /**
   * Structured template — parts and args stored separately. String is
   * constructed lazily on first access to `value`. Formatters can access
   * parts/args directly for structured output.
   */
  final class Templated(val parts: Array[String], val args: Array[Any]) extends LogMessage {
    @volatile private var cached: String = null

    def value: String = {
      var v = cached
      if (v == null) {
        val sb = new StringBuilder(64)
        var i  = 0
        while (i < parts.length) {
          sb.append(parts(i))
          if (i < args.length) sb.append(args(i))
          i += 1
        }
        v = sb.toString
        cached = v
      }
      v
    }

    override def toString: String = value

    override def equals(obj: Any): Boolean = obj match {
      case other: Templated => value == other.value
      case Simple(text)     => value == text
      case _                => false
    }

    override def hashCode(): Int = value.hashCode
  }

  implicit def fromString(s: String): LogMessage = Simple(s)

  def apply(s: String): LogMessage = Simple(s)
}

/**
 * Represents an immutable log record with trace correlation support.
 *
 * A LogRecord contains a log message, its severity level, timing information,
 * and optional trace context for distributed tracing correlation. Trace fields
 * use primitive sentinel values (0 = absent) instead of Option wrappers for
 * zero allocation on the logging hot path.
 *
 * @param timestampNanos
 *   The time when the event occurred, in nanoseconds since Unix epoch
 * @param observedTimestampNanos
 *   The time when the event was observed/recorded, in nanoseconds since Unix
 *   epoch
 * @param severity
 *   The severity level of the log record
 * @param severityText
 *   The text representation of the severity level
 * @param body
 *   The log message body
 * @param attributes
 *   Additional attributes/metadata associated with the log record
 * @param traceIdHi
 *   High 64 bits of trace ID (0 with traceIdLo == 0 means absent)
 * @param traceIdLo
 *   Low 64 bits of trace ID (0 with traceIdHi == 0 means absent)
 * @param spanId
 *   Span ID as Long (0 means absent)
 * @param traceFlags
 *   Trace flags as Byte (0 means none/absent)
 * @param resource
 *   The resource that generated this log record
 * @param instrumentationScope
 *   The instrumentation scope (library/tool) that generated this log record
 * @param throwable
 *   Optional throwable whose stack trace is deferred to export time
 */
final case class LogRecord(
  timestampNanos: Long,
  observedTimestampNanos: Long,
  severity: Severity,
  severityText: String,
  body: LogMessage,
  attributes: Attributes,
  traceIdHi: Long,
  traceIdLo: Long,
  spanId: Long,
  traceFlags: Byte,
  resource: Resource,
  instrumentationScope: InstrumentationScope,
  throwable: Option[Throwable] = None
) {

  /**
   * Whether this log record has a trace ID set.
   */
  def hasTraceId: Boolean = traceIdHi != 0L || traceIdLo != 0L

  /**
   * Whether this log record has a span ID set.
   */
  def hasSpanId: Boolean = spanId != 0L
}

object LogRecord {

  /**
   * Creates a new LogRecordBuilder for constructing LogRecord instances.
   */
  def builder: LogRecordBuilder = LogRecordBuilder()
}

/**
 * Mutable builder for constructing LogRecord instances with fluent API.
 *
 * The builder provides sensible defaults for all fields:
 *   - timestamps default to current system time in nanoseconds
 *   - severity defaults to Info
 *   - severityText defaults to "INFO"
 *   - body defaults to empty string
 *   - attributes defaults to empty Attributes
 *   - trace fields default to 0 (absent)
 *   - resource defaults to Resource.empty
 *   - instrumentationScope defaults to InstrumentationScope with name "unknown"
 */
final case class LogRecordBuilder(
  timestampNanos: Option[Long] = None,
  observedTimestampNanos: Option[Long] = None,
  severity: Severity = Severity.Info,
  body: LogMessage = LogMessage.Simple(""),
  attributes: Attributes = Attributes.empty,
  traceIdHi: Long = 0L,
  traceIdLo: Long = 0L,
  spanId: Long = 0L,
  traceFlags: Byte = 0,
  resource: Resource = Resource.empty,
  instrumentationScope: InstrumentationScope = InstrumentationScope(name = "unknown")
) {

  /**
   * Sets the timestamp (time when the event occurred).
   */
  def setTimestamp(nanos: Long): LogRecordBuilder =
    copy(timestampNanos = Some(nanos))

  /**
   * Sets the observed timestamp (time when the event was recorded).
   */
  def setObservedTimestamp(nanos: Long): LogRecordBuilder =
    copy(observedTimestampNanos = Some(nanos))

  /**
   * Sets the severity level of the log record.
   */
  def setSeverity(sev: Severity): LogRecordBuilder =
    copy(severity = sev)

  /**
   * Sets the log message body.
   */
  def setBody(msg: String): LogRecordBuilder =
    copy(body = LogMessage(msg))

  /**
   * Sets the log message body from a LogMessage.
   */
  def setBody(msg: LogMessage): LogRecordBuilder =
    copy(body = msg)

  /**
   * Adds an attribute to the log record.
   */
  def setAttribute[A](key: AttributeKey[A], value: A): LogRecordBuilder =
    copy(attributes = attributes ++ Attributes.of(key, value))

  /**
   * Sets the trace ID for correlation with distributed traces.
   */
  def setTraceId(hi: Long, lo: Long): LogRecordBuilder =
    copy(traceIdHi = hi, traceIdLo = lo)

  /**
   * Sets the span ID for correlation with distributed traces.
   */
  def setSpanId(id: Long): LogRecordBuilder =
    copy(spanId = id)

  /**
   * Sets the trace flags (e.g., sampled flag).
   */
  def setTraceFlags(flags: Byte): LogRecordBuilder =
    copy(traceFlags = flags)

  /**
   * Sets the resource that generated this log record.
   */
  def setResource(res: Resource): LogRecordBuilder =
    copy(resource = res)

  /**
   * Sets the instrumentation scope.
   */
  def setInstrumentationScope(scope: InstrumentationScope): LogRecordBuilder =
    copy(instrumentationScope = scope)

  /**
   * Builds the final LogRecord.
   *
   * Uses current system time for any timestamps not explicitly set.
   */
  def build: LogRecord = {
    val now = EpochClock.epochNanos()
    LogRecord(
      timestampNanos = timestampNanos.getOrElse(now),
      observedTimestampNanos = observedTimestampNanos.getOrElse(now),
      severity = severity,
      severityText = severity.text,
      body = body,
      attributes = attributes,
      traceIdHi = traceIdHi,
      traceIdLo = traceIdLo,
      spanId = spanId,
      traceFlags = traceFlags,
      resource = resource,
      instrumentationScope = instrumentationScope
    )
  }
}
