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
 * Immutable builder for constructing LogRecord instances with fluent API.
 *
 * All state is encapsulated: the constructor is private, every field is
 * private, and there is no `copy` or field accessor that would expose the
 * user-attribute container. The only way in is the typed setters; the only way
 * out is `build`. Obtain an instance from [[LogRecord.builder]].
 *
 * The builder provides sensible defaults for all fields:
 *   - timestamps default to current system time in nanoseconds
 *   - severity defaults to Info
 *   - body defaults to empty string
 *   - source fields default to absent (`""`, `""`, `0`)
 *   - user attributes default to empty
 *   - trace fields default to 0 (absent)
 *   - throwable defaults to None
 *   - resource defaults to Resource.empty
 *   - instrumentationScope defaults to InstrumentationScope with name "unknown"
 *
 * {{{
 * val record = LogRecord.builder
 *   .setSeverity(Severity.Warn)
 *   .setBody("disk almost full")
 *   .setAttribute(AttributeKey.long("disk.free_mb"), 512L)
 *   .build
 *
 * record.severity.text // WARN
 * record.attribute(AttributeKey.long("disk.free_mb")) // Some(512)
 * record.hasTraceId // false
 * }}}
 */
final class LogRecordBuilder private (
  private val timestampNanos: Option[Long],
  private val observedTimestampNanos: Option[Long],
  private val severity: Severity,
  private val body: LogMessage,
  private val sourceFile: String,
  private val sourceFunction: String,
  private val sourceLine: Int,
  private val userAttributes: Attributes,
  private val traceIdHi: Long,
  private val traceIdLo: Long,
  private val spanId: Long,
  private val traceFlags: Byte,
  private val resource: Resource,
  private val throwable: Option[Throwable],
  private val instrumentationScope: InstrumentationScope
) {

  /**
   * Sets the timestamp (time when the event occurred).
   */
  def setTimestamp(nanos: Long): LogRecordBuilder =
    new LogRecordBuilder(
      Some(nanos),
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the observed timestamp (time when the event was recorded).
   */
  def setObservedTimestamp(nanos: Long): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      Some(nanos),
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the severity level of the log record.
   */
  def setSeverity(sev: Severity): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      sev,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the log message body.
   */
  def setBody(msg: String): LogRecordBuilder =
    setBody(LogMessage(msg))

  /**
   * Sets the log message body from a LogMessage.
   */
  def setBody(msg: LogMessage): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      msg,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the source file path. `""` (the default) means absent.
   */
  def setSourceFile(file: String): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      file,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the fully-qualified source function name. `""` (the default) means
   * absent.
   */
  def setSourceFunction(function: String): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      function,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the source line number. Non-positive values normalize to `0` (absent),
   * matching the record's absence convention.
   */
  def setSourceLine(line: Int): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      if (line <= 0) 0 else line,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Adds an attribute. A key that is already there stays, and the new value
   * wins on lookup, as with `Attributes#++`.
   */
  def setAttribute[A](key: AttributeKey[A], value: A): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes ++ Attributes.of(key, value),
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Merges additional user attributes into the log record (right side wins on
   * conflicts). Source fields are untouched.
   */
  def addAttributes(attrs: Attributes): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes ++ attrs,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the throwable carried by the log record.
   */
  def setThrowable(throwable: Option[Throwable]): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the trace ID for correlation with distributed traces.
   */
  def setTraceId(hi: Long, lo: Long): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      hi,
      lo,
      spanId,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the span ID for correlation with distributed traces.
   */
  def setSpanId(id: Long): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      id,
      traceFlags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the trace flags (e.g., sampled flag).
   */
  def setTraceFlags(flags: Byte): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      flags,
      resource,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the resource that generated this log record.
   */
  def setResource(res: Resource): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      res,
      throwable,
      instrumentationScope
    )

  /**
   * Sets the instrumentation scope.
   */
  def setInstrumentationScope(scope: InstrumentationScope): LogRecordBuilder =
    new LogRecordBuilder(
      timestampNanos,
      observedTimestampNanos,
      severity,
      body,
      sourceFile,
      sourceFunction,
      sourceLine,
      userAttributes,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      throwable,
      scope
    )

  /** Builds the record, using the current time for any timestamp not set. */
  def build: LogRecord = {
    val now = EpochClock.epochNanos()
    LogRecord.create(
      timestampNanos = timestampNanos.getOrElse(now),
      observedTimestampNanos = observedTimestampNanos.getOrElse(now),
      severity = severity,
      severityText = severity.text,
      body = body,
      sourceFile = sourceFile,
      sourceFunction = sourceFunction,
      sourceLine = sourceLine,
      userAttributes = userAttributes,
      traceIdHi = traceIdHi,
      traceIdLo = traceIdLo,
      spanId = spanId,
      traceFlags = traceFlags,
      resource = resource,
      instrumentationScope = instrumentationScope,
      throwable = throwable
    )
  }
}

object LogRecordBuilder {

  /**
   * Empty builder with all defaults. Package-internal: external code starts
   * from [[LogRecord.builder]] and can only reach `build` through the typed
   * setters.
   */
  private[telemetry] def empty: LogRecordBuilder =
    new LogRecordBuilder(
      None,
      None,
      Severity.Info,
      LogMessage.Simple(""),
      "",
      "",
      0,
      Attributes.empty,
      0L,
      0L,
      0L,
      0.toByte,
      Resource.empty,
      None,
      InstrumentationScope(name = "unknown")
    )
}
