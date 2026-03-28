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

/**
 * Represents an immutable log record with trace correlation support.
 *
 * A LogRecord contains a log message, its severity level, timing information,
 * and optional trace context (traceId, spanId, traceFlags) for distributed
 * tracing correlation.
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
 * @param traceId
 *   Optional trace ID for correlation with distributed traces
 * @param spanId
 *   Optional span ID for correlation with distributed traces
 * @param traceFlags
 *   Optional trace flags (e.g., sampled flag)
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
  body: String,
  attributes: Attributes,
  traceId: Option[TraceId],
  spanId: Option[SpanId],
  traceFlags: Option[TraceFlags],
  resource: Resource,
  instrumentationScope: InstrumentationScope,
  throwable: Option[Throwable] = None
)

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
 *   - trace fields (traceId, spanId, traceFlags) default to None
 *   - resource defaults to Resource.empty
 *   - instrumentationScope defaults to InstrumentationScope with name "unknown"
 */
final case class LogRecordBuilder(
  timestampNanos: Option[Long] = None,
  observedTimestampNanos: Option[Long] = None,
  severity: Severity = Severity.Info,
  body: String = "",
  attributes: Attributes = Attributes.empty,
  traceId: Option[TraceId] = None,
  spanId: Option[SpanId] = None,
  traceFlags: Option[TraceFlags] = None,
  resource: Resource = Resource.empty,
  instrumentationScope: InstrumentationScope = InstrumentationScope(name = "unknown")
) {

  /**
   * Sets the timestamp (time when the event occurred).
   *
   * @param nanos
   *   The timestamp in nanoseconds since Unix epoch
   * @return
   *   This builder for method chaining
   */
  def setTimestamp(nanos: Long): LogRecordBuilder =
    copy(timestampNanos = Some(nanos))

  /**
   * Sets the observed timestamp (time when the event was recorded).
   *
   * @param nanos
   *   The observed timestamp in nanoseconds since Unix epoch
   * @return
   *   This builder for method chaining
   */
  def setObservedTimestamp(nanos: Long): LogRecordBuilder =
    copy(observedTimestampNanos = Some(nanos))

  /**
   * Sets the severity level of the log record.
   *
   * Also updates the severityText to match the severity's text representation.
   *
   * @param sev
   *   The severity level
   * @return
   *   This builder for method chaining
   */
  def setSeverity(sev: Severity): LogRecordBuilder =
    copy(severity = sev)

  /**
   * Sets the log message body.
   *
   * @param msg
   *   The log message
   * @return
   *   This builder for method chaining
   */
  def setBody(msg: String): LogRecordBuilder =
    copy(body = msg)

  /**
   * Adds an attribute to the log record.
   *
   * @param key
   *   The attribute key
   * @param value
   *   The attribute value
   * @return
   *   This builder for method chaining
   */
  def setAttribute[A](key: AttributeKey[A], value: A): LogRecordBuilder =
    copy(attributes = attributes ++ Attributes.of(key, value))

  /**
   * Sets the trace ID for correlation with distributed traces.
   *
   * @param id
   *   The trace ID
   * @return
   *   This builder for method chaining
   */
  def setTraceId(id: TraceId): LogRecordBuilder =
    copy(traceId = Some(id))

  /**
   * Sets the span ID for correlation with distributed traces.
   *
   * @param id
   *   The span ID
   * @return
   *   This builder for method chaining
   */
  def setSpanId(id: SpanId): LogRecordBuilder =
    copy(spanId = Some(id))

  /**
   * Sets the trace flags (e.g., sampled flag).
   *
   * @param flags
   *   The trace flags
   * @return
   *   This builder for method chaining
   */
  def setTraceFlags(flags: TraceFlags): LogRecordBuilder =
    copy(traceFlags = Some(flags))

  /**
   * Sets the resource that generated this log record.
   *
   * @param res
   *   The resource
   * @return
   *   This builder for method chaining
   */
  def setResource(res: Resource): LogRecordBuilder =
    copy(resource = res)

  /**
   * Sets the instrumentation scope.
   *
   * @param scope
   *   The instrumentation scope
   * @return
   *   This builder for method chaining
   */
  def setInstrumentationScope(scope: InstrumentationScope): LogRecordBuilder =
    copy(instrumentationScope = scope)

  /**
   * Builds the final LogRecord.
   *
   * Uses current system time for any timestamps not explicitly set.
   *
   * @return
   *   The constructed LogRecord
   */
  def build: LogRecord = {
    val now = System.nanoTime()
    LogRecord(
      timestampNanos = timestampNanos.getOrElse(now),
      observedTimestampNanos = observedTimestampNanos.getOrElse(now),
      severity = severity,
      severityText = severity.text,
      body = body,
      attributes = attributes,
      traceId = traceId,
      spanId = spanId,
      traceFlags = traceFlags,
      resource = resource,
      instrumentationScope = instrumentationScope
    )
  }
}
