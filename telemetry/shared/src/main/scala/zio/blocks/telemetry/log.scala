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

import scala.util.control.NonFatal

/**
 * The global logger. `log.info("order placed", "orderId" -> "ord-123")` and the
 * other level methods record a structured log line from anywhere, with no
 * set-up.
 *
 * Each call records its source location as the `code.filepath`,
 * `code.namespace`, `code.function` and `code.lineno` attributes, adds the
 * pairs of the enclosing `log.annotated` blocks, and takes the trace and span
 * ids of the current span. The arguments after the message add attributes
 * (`"key" -> value`), an exception (a `Throwable`) or a new level (a
 * [[Severity]]); see [[LogEnrichment]] for the full list. A call below the
 * minimum severity returns before it builds anything.
 *
 * Out of the box, each line goes to standard output as text, through a
 * [[ConsoleLogRecordProcessor]]. `log.writer` and `log.addProcessor` add
 * outputs, and `log.install` replaces the whole backend. The configuration is
 * global: it applies to every thread of the program.
 *
 * Three known bugs: on Scala 2.13 the level methods (`log.info` and the others)
 * do not compile outside the package `zio.blocks.telemetry`, because their
 * expansion refers to internal objects (the configuration methods work); on
 * Scala 3 an argument whose static type is a subclass of `Throwable`, such as
 * `java.io.IOException`, fails to compile with "No LogEnrichment instance
 * found", so ascribe it as `Throwable`; and the default text output prints the
 * full class name and two numbers in the location bracket, as in
 * `[(shop.Checkout,5,13).placeOrder:12]`, when the class is in a package.
 *
 * {{{
 * // scala-3-only
 * import scala.collection.mutable.ListBuffer
 *
 * val records = ListBuffer.empty[LogRecord]
 * val inMemory = new LogRecordProcessor {
 *   def onEmit(record: LogRecord): Unit = records += record
 *   def shutdown(): Unit = ()
 *   def forceFlush(): Unit = ()
 * }
 * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("checkout"))
 *
 * log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)
 *
 * records.head.body.value // order placed
 * records.head.severity // Info
 * records.head.attributes.get(AttributeKey.long("amount")) // Some(99)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/ Logging]]
 */
object log extends LogVersionSpecific {

  private[telemetry] val logInstrumentationScope: InstrumentationScope =
    InstrumentationScope(name = "zio.blocks.telemetry.log")

  /**
   * Runs `f` and adds `annotations` as string attributes to every record that
   * `log.*` calls emit inside it, including calls in nested methods.
   *
   * Nested blocks add to the outer pairs, and an inner value replaces an outer
   * one with the same key. The pairs follow the current thread: on the JVM they
   * are bound with a `ScopedValue`, so a thread that `f` starts does not see
   * them, and on Scala.js they are removed when `f` returns, before any callback
   * it scheduled runs. [[Logger]] instances do not read them.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("cart"))
   *
   * log.annotated("requestId" -> "req-42", "tenant" -> "acme") {
   *   log.info("cart loaded")
   * }
   * log.info("outside")
   *
   * records.map(_.attributes.get(AttributeKey.string("requestId"))) // ListBuffer(Some(req-42), None)
   * }}}
   */
  def annotated[A](annotations: (String, String)*)(f: => A): A =
    LogAnnotations.scoped(annotations.toMap)(f)

  /**
   * Sets the global minimum severity: `log.*` calls below it are dropped before
   * they build a record.
   *
   * It also removes every prefix override set with the two-argument
   * `setMinSeverity`, so set the global floor first. It applies to the global
   * `log` only: the level methods of a [[Logger]] ignore it. After
   * `log.removeAll`, it turns logging back on, but adds no output.
   */
  def setMinSeverity(severity: Severity): Unit =
    GlobalLogState.install(GlobalLogState.get().logger, severity)

  /**
   * Sets the minimum severity for `log.*` calls whose enclosing class name
   * starts with `prefix`, such as `"com.acme.payments"`.
   *
   * The override can be lower or higher than the global floor. The match is a
   * plain string prefix, so `"com.acme"` also matches `com.acmecorp`, and the
   * longest matching prefix wins.
   *
   * {{{
   * log.setMinSeverity(Severity.Info)
   * log.setMinSeverity("com.acme.payments", Severity.Debug)
   * log.setMinSeverity("org.apache.kafka", Severity.Warn)
   * }}}
   */
  def setMinSeverity(prefix: String, severity: Severity): Unit =
    GlobalLogState.setLevel(prefix, severity)

  /**
   * Removes the override set for exactly this `prefix`; its calls fall back to
   * a shorter matching prefix or the global floor.
   */
  def clearMinSeverity(prefix: String): Unit =
    GlobalLogState.clearLevel(prefix)

  /** Removes every prefix override, leaving only the global floor. */
  def clearAllOverrides(): Unit =
    GlobalLogState.clearAllLevels()

  /**
   * Adds `processor` to the current backend: every later record reaches it as
   * well as the outputs already registered.
   *
   * `log.install` drops it. After `log.removeAll`, nothing reaches it until
   * `log.setMinSeverity` or `log.install` runs, a known bug.
   */
  def addProcessor(processor: LogRecordProcessor): Unit = synchronized {
    val currentState = GlobalLogState.get()
    val logger       = new Logger(
      currentState.logger.instrumentationScope,
      currentState.logger.resource,
      currentState.logger.processors :+ processor,
      currentState.logger.contextStorage
    )
    GlobalLogState.set(LogState(logger, currentState.minSeverity, currentState.levelOverridesMap))
  }

  /**
   * Replaces the global backend with `logger` and sets the global floor to
   * `minSeverity`.
   *
   * It drops the default console output, the processors added before and every
   * prefix override. Records from `log.*` calls then take the resource,
   * processors and context storage of `logger`, but keep the instrumentation
   * scope `zio.blocks.telemetry.log`: the name given to `LoggerProvider#get`
   * does not reach them. A known bug: it also stops the outputs added with
   * `log.writer`, and the next `log.writer` call brings them back.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * val provider = LoggerProvider.builder
   *   .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "checkout")))
   *   .addLogRecordProcessor(inMemory)
   *   .build()
   * log.install(provider.get("checkout"), Severity.Info)
   *
   * log.debug("dropped")
   * log.info("order placed")
   *
   * records.map(_.body.value) // ListBuffer(order placed)
   * records.head.resource.attributes.get(Attributes.ServiceName) // Some(checkout)
   * records.head.instrumentationScope.name // zio.blocks.telemetry.log
   * }}}
   */
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit =
    GlobalLogState.install(logger, minSeverity)

  /**
   * Shuts down every processor of the current backend, forgets the outputs added
   * with `log.writer`, and turns `log.*` calls into no-ops.
   *
   * `log.install` turns logging back on. A known bug: `log.writer` and
   * `log.addProcessor` alone do not, because the floor stays above every
   * severity; call `log.setMinSeverity` after them.
   */
  def removeAll(): Unit = synchronized {
    val prev = GlobalLogState.get()
    prev.logger.processors.foreach { p =>
      try p.shutdown()
      catch { case NonFatal(_) => () }
    }
    _writerProcessors = Nil
    GlobalLogState.removeAll()
  }

  /**
   * Runs `f` with the global floor set to `severity`, then puts back the backend
   * that was active before.
   *
   * The floor applies to every thread while `f` runs, not only to the caller,
   * and prefix overrides still take precedence inside the block. A known bug:
   * putting the backend back also drops what `f` changed, so a processor,
   * output or prefix override added inside the block stops working when it
   * ends.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val records = ListBuffer.empty[LogRecord]
   * val inMemory = new LogRecordProcessor {
   *   def onEmit(record: LogRecord): Unit = records += record
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * log.install(LoggerProvider.builder.addLogRecordProcessor(inMemory).build().get("cache"), Severity.Info)
   *
   * log.withMinSeverity(Severity.Debug) {
   *   log.debug("cache miss", "key" -> "user:42")
   * }
   * log.debug("dropped again")
   *
   * records.map(_.body.value) // ListBuffer(cache miss)
   * }}}
   */
  def withMinSeverity[A](severity: Severity)(f: => A): A = {
    val prev = GlobalLogState.get()
    GlobalLogState.set(LogState(prev.logger, severity.number, prev.levelOverridesMap))
    try f
    finally GlobalLogState.set(LogState(prev.logger, prev.minSeverity, prev.levelOverridesMap))
  }

  @volatile private var _writerProcessors: List[FormattedLogRecordProcessor] = Nil

  /**
   * Adds an output that renders each record with `formatter` and passes the
   * text to `logWriter`, one call per record.
   *
   * Outputs add up: each call adds another, and the default console output
   * stays. To keep only your outputs, call `log.install` with a logger that has
   * no processors first.
   *
   * {{{
   * // scala-3-only
   * import scala.collection.mutable.ListBuffer
   *
   * val lines = ListBuffer.empty[String]
   * val memory = new LogWriter {
   *   def write(content: CharSequence): Unit = lines += content.toString
   * }
   * log.install(LoggerProvider.builder.build().get("checkout"))
   * log.writer(JsonLogFormatter, memory)
   *
   * log.info("order placed")
   *
   * lines.size // 1
   * lines.head.contains("\"body\":{\"stringValue\":\"order placed\"}") // true
   * }}}
   */
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit = synchronized {
    _writerProcessors = _writerProcessors :+ new FormattedLogRecordProcessor(formatter, logWriter)
    rebuildState()
  }

  /**
   * Removes every output added with `log.writer` and closes its [[LogWriter]];
   * processors and the default console output stay.
   */
  def clearWriters(): Unit = synchronized {
    _writerProcessors.foreach(p =>
      try p.shutdown()
      catch { case NonFatal(_) => () }
    )
    _writerProcessors = Nil
    rebuildState()
  }

  private def rebuildState(): Unit = {
    val currentState = GlobalLogState.get()
    val logger       = new Logger(
      currentState.logger.instrumentationScope,
      currentState.logger.resource,
      currentState.logger.baseProcessors ++ _writerProcessors,
      currentState.logger.contextStorage
    )
    GlobalLogState.set(LogState(logger, currentState.minSeverity, currentState.levelOverridesMap))
  }

  private[telemetry] def emit(severity: Severity, message: String, location: SourceLocation): Unit =
    if (severity.number >= GlobalLogState.globalMinLevel) {
      val state = GlobalLogState.get()
      if (severity.number >= state.effectiveLevel(location.namespace)) {
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
          state.logger.resource,
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
    val state    = GlobalLogState.get()
    val resource = state.logger.resource

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
      resource = resource,
      instrumentationScope = logInstrumentationScope
    )
  }
}
