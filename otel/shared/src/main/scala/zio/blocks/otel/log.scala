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

  private[otel] val logInstrumentationScope: InstrumentationScope =
    InstrumentationScope(name = "zio.blocks.otel.log")

  def annotated[A](annotations: (String, String)*)(f: => A): A =
    LogAnnotations.scoped(annotations.toMap)(f)

  @volatile private var _writerProcessors: List[FormattedLogRecordProcessor] = Nil

  /** Add a formatted output. Additive — each call adds another output. */
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit = synchronized {
    _writerProcessors = _writerProcessors :+ new FormattedLogRecordProcessor(formatter, logWriter)
    rebuildState()
  }

  /**
   * Remove all outputs added via writer(). Reverts to processor-based emit
   * only.
   */
  def clearWriters(): Unit = synchronized {
    _writerProcessors.foreach(p =>
      try p.shutdown()
      catch { case _: Throwable => () }
    )
    _writerProcessors = Nil
    rebuildState()
  }

  private def rebuildState(): Unit = {
    val currentState = GlobalLogState.get()
    if (currentState != null && currentState.logger != null) {
      // Create a new logger with updated processors
      val cs     = ContextStorage.create[Option[SpanContext]](None)
      val logger = new Logger(
        logInstrumentationScope,
        Resource.empty,
        currentState.logger.baseProcessors ++ _writerProcessors,
        cs
      )
      GlobalLogState.set(new LogState(logger, currentState.minSeverity, currentState.levelOverrides))
    }
  }

  private[otel] def emit(severity: Severity, message: String, location: SourceLocation): Unit =
    if (severity.number >= GlobalLogState.globalMinLevel) {
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

        state.logger.emitRaw(
          now,
          severity,
          severity.text,
          message,
          builder,
          0L,
          0L,
          0L,
          0.toByte,
          Resource.empty,
          logInstrumentationScope,
          None
        )
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
      attributes = builder.buildAndReset(),
      traceIdHi = 0L,
      traceIdLo = 0L,
      spanId = 0L,
      traceFlags = 0,
      resource = Resource.empty,
      instrumentationScope = logInstrumentationScope
    )
  }
}
