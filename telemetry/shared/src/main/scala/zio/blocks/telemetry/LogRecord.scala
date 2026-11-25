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
 * Source metadata (file, fully-qualified function, line) is stored in private
 * scalar fields, separate from user attributes — the native read/write API
 * never aliases `attribute("code.file.path")` (or its siblings) into these
 * fields. Exporters combine both at the boundary using the stable OpenTel keys
 * (`code.file.path`, `code.function.name`, `code.line.number`); see
 * `LogRecord.isSourceCollision` for the collision policy.
 *
 * User attributes live in a private type-specialized store. They are readable
 * only through `LogRecord.attribute` (single lookup) and
 * [[LogRecord.foreachAttribute]] (ordered enumeration of user attributes only,
 * never source metadata). The container itself is never exposed.
 *
 * Absence convention: `sourceFile` / `sourceFunction` are `""` and `sourceLine`
 * is `0` when no source was captured (direct `Logger` calls, builder defaults).
 */
final class LogRecord private (
  val timestampNanos: Long,
  val observedTimestampNanos: Long,
  val severity: Severity,
  val severityText: String,
  val body: LogMessage,
  val traceIdHi: Long,
  val traceIdLo: Long,
  val spanId: Long,
  val traceFlags: Byte,
  val resource: Resource,
  val instrumentationScope: InstrumentationScope,
  val throwable: Option[Throwable],
  private val sourceFileValue: String,
  private val sourceFunctionValue: String,
  private val sourceLineValue: Int,
  private[telemetry] val userAttributes: Attributes
) {

  /**
   * Whether this log record has a trace ID set.
   */
  def hasTraceId: Boolean = traceIdHi != 0L || traceIdLo != 0L

  /**
   * Whether this log record has a span ID set.
   */
  def hasSpanId: Boolean = spanId != 0L

  /**
   * Compile-time captured source file path, or `""` when absent. Never
   * absolutized or shortened — exactly what the macro captured.
   */
  def sourceFile: String = sourceFileValue

  /**
   * Compile-time captured fully-qualified function name
   * (`namespace.methodName`, without arguments), or `""` when absent.
   */
  def sourceFunction: String = sourceFunctionValue

  /**
   * Compile-time captured line number, or `0` when absent.
   */
  def sourceLine: Int = sourceLineValue

  /**
   * Typed user-attribute lookup. Returns `None` when absent. Canonical source
   * keys read back only user-supplied values here — never source metadata.
   *
   * The key kind must match the stored kind: mismatched-kind lookup is
   * unsupported and can fail when the value is extracted. Prefer the
   * `AttributeKey` matching how the value was written; for input whose kind is
   * unknown, use the `String` overload, which reports the stored kind.
   */
  def attribute[A](key: AttributeKey[A]): Option[A] =
    userAttributes.get(key)

  /**
   * Untyped user-attribute lookup. Returns `None` when absent. Canonical source
   * keys read back only user-supplied values here — never source metadata.
   */
  def attribute(key: String): Option[AttributeValue] =
    userAttributes.findRaw(key)

  /**
   * Enumerates user attributes in insertion order as `(key, value)` pairs.
   * Source metadata is never enumerated here — exporters write it explicitly
   * from [[sourceFile]], [[sourceFunction]], and [[sourceLine]].
   *
   * This is the external convenience entry point: every pair arrives as an
   * `AttributeValue` wrapper, so per-attribute boxing applies (primitives box
   * on the way out). Hot exporters iterate the typed store directly instead;
   * use this for inspection, tests, and custom sinks where clarity beats the
   * last nanosecond.
   */
  def foreachAttribute(f: (String, AttributeValue) => Unit): Unit =
    userAttributes.foreach(f)

  /**
   * Returns a copy with one additional user attribute. This is the extension
   * point for custom [[LogEnrichment]] instances. Source metadata is never
   * touched: a key that collides with a canonical source key is stored as an
   * ordinary user attribute, and exporters apply the source-wins collision
   * policy at the boundary.
   */
  def withAttribute[A](key: AttributeKey[A], value: A): LogRecord =
    addingUserAttributes(Attributes.of(key, value))

  /**
   * Returns a copy with a different body. Source and user attributes stay.
   * Public so custom `LogEnrichment` instances defined outside this package can
   * replace the body while preserving everything else.
   */
  def withBody(body: LogMessage): LogRecord =
    copy(body = body)

  /**
   * Returns a copy with a different severity (text follows the level). Source
   * and user attributes stay. Public so custom `LogEnrichment` instances
   * defined outside this package can escalate the level while preserving
   * everything else.
   */
  def withSeverity(severity: Severity): LogRecord =
    copy(severity = severity, severityText = severity.text)

  /**
   * Returns a copy with a different throwable. Source and user attributes stay.
   * Public so custom `LogEnrichment` instances defined outside this package can
   * attach a cause while preserving everything else.
   */
  def withThrowable(throwable: Option[Throwable]): LogRecord =
    copy(throwable = throwable)

  /**
   * Returns a copy with `attrs` merged into the user attributes (right side
   * wins on conflicts). Source metadata stays. Package-internal: external code
   * adds user attributes through [[withAttribute]] instead, so the container
   * type never crosses the public record API.
   */
  private[telemetry] def addingUserAttributes(attrs: Attributes): LogRecord =
    copy(userAttributes = userAttributes ++ attrs)

  /**
   * Private copy helper: every field defaults to its current value, so the
   * public operation methods stay one-liners without exposing the container (or
   * a public `copy`) on the record API.
   */
  private def copy(
    timestampNanos: Long = timestampNanos,
    observedTimestampNanos: Long = observedTimestampNanos,
    severity: Severity = severity,
    severityText: String = severityText,
    body: LogMessage = body,
    traceIdHi: Long = traceIdHi,
    traceIdLo: Long = traceIdLo,
    spanId: Long = spanId,
    traceFlags: Byte = traceFlags,
    resource: Resource = resource,
    instrumentationScope: InstrumentationScope = instrumentationScope,
    throwable: Option[Throwable] = throwable,
    sourceFile: String = sourceFileValue,
    sourceFunction: String = sourceFunctionValue,
    sourceLine: Int = sourceLineValue,
    userAttributes: Attributes = userAttributes
  ): LogRecord =
    new LogRecord(
      timestampNanos,
      observedTimestampNanos,
      severity,
      severityText,
      body,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      instrumentationScope,
      throwable,
      sourceFile,
      sourceFunction,
      if (sourceLine <= 0) 0 else sourceLine,
      userAttributes
    )

  override def equals(obj: Any): Boolean =
    (this eq obj.asInstanceOf[AnyRef]) || (obj match {
      case other: LogRecord =>
        timestampNanos == other.timestampNanos &&
        observedTimestampNanos == other.observedTimestampNanos &&
        severity == other.severity &&
        severityText == other.severityText &&
        body == other.body &&
        traceIdHi == other.traceIdHi &&
        traceIdLo == other.traceIdLo &&
        spanId == other.spanId &&
        traceFlags == other.traceFlags &&
        resource == other.resource &&
        instrumentationScope == other.instrumentationScope &&
        throwable == other.throwable &&
        sourceFileValue == other.sourceFileValue &&
        sourceFunctionValue == other.sourceFunctionValue &&
        sourceLineValue == other.sourceLineValue &&
        userAttributes == other.userAttributes
      case _ => false
    })

  override def hashCode(): Int = {
    var h = timestampNanos.hashCode()
    h = 31 * h + observedTimestampNanos.hashCode()
    h = 31 * h + severity.hashCode()
    h = 31 * h + severityText.hashCode
    h = 31 * h + body.hashCode()
    h = 31 * h + traceIdHi.hashCode()
    h = 31 * h + traceIdLo.hashCode()
    h = 31 * h + spanId.hashCode()
    h = 31 * h + traceFlags.hashCode()
    h = 31 * h + resource.hashCode()
    h = 31 * h + instrumentationScope.hashCode()
    h = 31 * h + throwable.hashCode()
    h = 31 * h + sourceFileValue.hashCode
    h = 31 * h + sourceFunctionValue.hashCode
    h = 31 * h + sourceLineValue.hashCode()
    h = 31 * h + userAttributes.hashCode()
    h
  }

  override def toString: String =
    s"LogRecord($severityText, ${body.value}, source=$sourceFunctionValue:$sourceLineValue)"
}

object LogRecord {

  /**
   * Creates a new LogRecordBuilder for constructing LogRecord instances. The
   * builder's state is fully encapsulated: no constructor, field, or `copy` on
   * it exposes the attribute container — only the typed setters below and
   * `build`.
   */
  def builder: LogRecordBuilder = LogRecordBuilder.empty

  /**
   * Exporter collision policy for canonical source keys.
   *
   * A user attribute whose key equals a canonical source key (`code.file.path`,
   * `code.function.name`, `code.line.number`) is suppressed at export
   * boundaries only when the matching source field is present — source wins per
   * field, never duplicated. When the source field is absent, the user
   * attribute passes through unchanged, so external input routed through user
   * attributes is never lost silently.
   */
  private[telemetry] def isSourceCollision(
    key: String,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int
  ): Boolean =
    (key == SourceLocation.CodeFilePath && sourceFile != null && sourceFile.nonEmpty) ||
      (key == SourceLocation.CodeFunctionName && sourceFunction != null && sourceFunction.nonEmpty) ||
      (key == SourceLocation.CodeLineNumber && sourceLine != 0)

  /**
   * Internal fast-path constructor. Null source strings coerce to absent
   * (`""`), non-positive lines coerce to absent (`0`); the caller owns
   * `userAttributes` exclusively after this call (zero-copy handoff, e.g. from
   * `AttributesBuilder.buildAndReset`).
   */
  private[telemetry] def create(
    timestampNanos: Long,
    observedTimestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: LogMessage,
    sourceFile: String,
    sourceFunction: String,
    sourceLine: Int,
    userAttributes: Attributes,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    resource: Resource,
    instrumentationScope: InstrumentationScope,
    throwable: Option[Throwable]
  ): LogRecord =
    new LogRecord(
      timestampNanos,
      observedTimestampNanos,
      severity,
      severityText,
      body,
      traceIdHi,
      traceIdLo,
      spanId,
      traceFlags,
      resource,
      instrumentationScope,
      throwable,
      if (sourceFile == null) "" else sourceFile,
      if (sourceFunction == null) "" else sourceFunction,
      if (sourceLine <= 0) 0 else sourceLine,
      userAttributes
    )
}
