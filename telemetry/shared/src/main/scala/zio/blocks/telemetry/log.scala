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

object log extends LogVersionSpecific {

  private[telemetry] val logInstrumentationScope: InstrumentationScope =
    InstrumentationScope(name = "zio.blocks.telemetry.log")

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
      // Create a new logger with updated processors, preserving existing context storage and resource
      val logger = new Logger(
        currentState.logger.instrumentationScope,
        currentState.logger.resource,
        currentState.logger.baseProcessors ++ _writerProcessors,
        currentState.logger.contextStorage
      )
      GlobalLogState.set(LogState(logger, currentState.minSeverity, currentState.levelOverridesMap))
    }
  }

  private[telemetry] def emit(severity: Severity, message: String, location: SourceLocation): Unit =
    if (severity.number >= GlobalLogState.globalMinLevel) {
      val state = GlobalLogState.get()
      if (state != null && severity.number >= state.effectiveLevel(location.namespace)) {
        val now     = EpochClock.epochNanos()
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

        val spanCtx                 = state.logger.currentSpanContext()
        val (tidHi, tidLo, sid, tf) = spanCtx match {
          case Some(sc) => (sc.traceIdHi, sc.traceIdLo, sc.spanId.value, sc.traceFlags.byte)
          case None     => (0L, 0L, 0L, 0.toByte)
        }

        state.logger.emitRaw(
          now,
          severity,
          severity.text,
          message,
          builder,
          tidHi,
          tidLo,
          sid,
          tf,
          Resource.empty,
          logInstrumentationScope,
          None
        )
      }
    }

  private[telemetry] def baseRecord(
    severity: Severity,
    message: String,
    location: SourceLocation
  ): LogRecord = {
    val now     = EpochClock.epochNanos()
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

    val attrs = builder.build
    builder.clear()
    LogRecord(
      timestampNanos = now,
      observedTimestampNanos = now,
      severity = severity,
      severityText = severity.text,
      body = LogMessage(message),
      attributes = attrs,
      traceIdHi = 0L,
      traceIdLo = 0L,
      spanId = 0L,
      traceFlags = 0,
      resource = Resource.empty,
      instrumentationScope = logInstrumentationScope
    )
  }
}
