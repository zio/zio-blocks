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

object log extends LogVersionSpecific {

  /**
   * Run a block with additional contextual annotations attached to all log
   * records.
   */
  def annotated[A](annotations: (String, String)*)(f: => A): A =
    LogAnnotations.scoped(annotations.toMap)(f)

  private[otel] def emit(severity: Severity, message: String, location: SourceLocation): Unit = {
    val state = GlobalLogState.get()
    if (state != null && severity.number >= state.effectiveLevel(location.namespace)) {
      val now     = System.nanoTime()
      val builder = AttributeBuilderPool
        .get()
        .put("code.filepath", location.filePath)
        .put("code.namespace", location.namespace)
        .put("code.function", location.methodName)
        .put("code.lineno", location.lineNumber.toLong)

      // Merge scoped annotations
      val annotations = LogAnnotations.get()
      if (annotations.nonEmpty) {
        annotations.foreach { case (k, v) => builder.put(k, v) }
      }

      val record = LogRecord(
        timestampNanos = now,
        observedTimestampNanos = now,
        severity = severity,
        severityText = severity.text,
        body = message,
        attributes = builder.build,
        traceId = None,
        spanId = None,
        traceFlags = None,
        resource = Resource.empty,
        instrumentationScope = InstrumentationScope(name = "zio.blocks.otel.log")
      )

      state.logger.emit(record)
    }
  }

  private[otel] def baseRecord(
    severity: Severity,
    message: String,
    location: SourceLocation
  ): LogRecord = {
    val now     = System.nanoTime()
    val builder = AttributeBuilderPool
      .get()
      .put("code.filepath", location.filePath)
      .put("code.namespace", location.namespace)
      .put("code.function", location.methodName)
      .put("code.lineno", location.lineNumber.toLong)

    // Merge scoped annotations
    val annotations = LogAnnotations.get()
    if (annotations.nonEmpty) {
      annotations.foreach { case (k, v) => builder.put(k, v) }
    }

    LogRecord(
      timestampNanos = now,
      observedTimestampNanos = now,
      severity = severity,
      severityText = severity.text,
      body = message,
      attributes = builder.build,
      traceId = None,
      spanId = None,
      traceFlags = None,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope(name = "zio.blocks.otel.log")
    )
  }
}
