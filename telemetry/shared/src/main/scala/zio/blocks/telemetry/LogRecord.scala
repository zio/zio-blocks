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
 * The message of a [[LogRecord]]: a ready string ([[LogMessage.Simple]]) or a
 * template whose string is built on first use ([[LogMessage.Templated]]).
 */
sealed trait LogMessage {

  /** Returns the message text. */
  def value: String
}

/** Creates [[LogMessage]]s. */
object LogMessage {

  /** A message that is already a string. */
  final case class Simple(text: String) extends LogMessage {

    /** Returns `text`. */
    def value: String             = text
    override def toString: String = text
  }

  /**
   * A message kept as text `parts` and the `args` between them, joined into a
   * string on the first call to `value` and cached. The built-in formatters
   * read only `value`.
   *
   * It equals another `Templated` or a [[LogMessage.Simple]] with the same
   * text. A known bug: `Simple#equals` does not return `true` in the other
   * direction.
   */
  final class Templated(val parts: Array[String], val args: Array[Any]) extends LogMessage {
    @volatile private var cached: String = null

    /**
     * Returns `parts(0) + args(0) + parts(1) + ...`, built on the first call;
     * parts without a matching arg are appended as they are, and extra args are
     * ignored.
     */
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

  /**
   * Converts a string to a [[LogMessage.Simple]] where a `LogMessage` is
   * expected.
   */
  implicit def fromString(s: String): LogMessage = Simple(s)

  /** Returns a [[LogMessage.Simple]] with the text `s`. */
  def apply(s: String): LogMessage = Simple(s)
}

/**
 * One log entry: the message, its severity, when it happened, its attributes
 * and the span it was logged in. A [[LogRecordProcessor]] receives it and a
 * [[LogFormatter]] renders it; `log.*` calls and [[Logger]]s build it for you.
 *
 * The trace fields are plain numbers, with 0 meaning "no span": check
 * `hasTraceId` and `hasSpanId`. Records from `log.*` calls carry their source
 * location as the `code.filepath`, `code.namespace`, `code.function` and
 * `code.lineno` attributes.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/log-record/ LogRecord]]
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

  /** Returns `true` when either half of the trace id is not 0. */
  def hasTraceId: Boolean = traceIdHi != 0L || traceIdLo != 0L

  /** Returns `true` when the span id is not 0. */
  def hasSpanId: Boolean = spanId != 0L
}

/** Creates [[LogRecordBuilder]]s. */
object LogRecord {

  /** Returns a builder with the defaults that [[LogRecordBuilder]] lists. */
  def builder: LogRecordBuilder = LogRecordBuilder()
}

/**
 * Builds a [[LogRecord]] step by step; each setter returns a new builder.
 *
 * The defaults: both timestamps are the time of `build`, the severity is
 * `Severity.Info`, the body is empty, there are no attributes and no trace ids,
 * the resource is `Resource.empty` and the instrumentation scope is named
 * `"unknown"`. The severity text always comes from the severity, and the record
 * has no exception.
 *
 * {{{
 * val record = LogRecord.builder
 *   .setSeverity(Severity.Warn)
 *   .setBody("disk almost full")
 *   .setAttribute(AttributeKey.long("disk.free_mb"), 512L)
 *   .build
 *
 * record.severity.text // WARN
 * record.attributes.get(AttributeKey.long("disk.free_mb")) // Some(512)
 * record.hasTraceId // false
 * }}}
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
   * Adds an attribute. A key that is already there stays, and the new value
   * wins on lookup, as with `Attributes#++`.
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

  /** Builds the record, using the current time for any timestamp not set. */
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
