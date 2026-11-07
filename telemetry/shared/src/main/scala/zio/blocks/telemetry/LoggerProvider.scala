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
 * Creates [[Logger]]s that share one [[Resource]], one list of
 * [[LogRecordProcessor]]s and one [[ContextStorage]]. Build it once at start-up
 * with `LoggerProvider.builder`, and call `shutdown` (or `close`) at exit so
 * that the processors flush what they hold.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/logger-provider/ LoggerProvider]]
 */
final class LoggerProvider private[telemetry] (
  resource: Resource,
  private val processors: Array[LogRecordProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) extends AutoCloseable {

  /**
   * Returns a new [[Logger]] whose instrumentation scope has this `name` and,
   * when `version` is not empty, this `version`. Each call creates a new
   * logger, so keep the result.
   */
  def get(name: String, version: String = ""): Logger = {
    val scope = InstrumentationScope(
      name = name,
      version = if (version.isEmpty) None else Some(version)
    )
    new Logger(scope, resource, processors, contextStorage)
  }

  /**
   * Calls `shutdown` on every processor, in the order they were added. An
   * exception from one processor propagates, and the later ones are not shut
   * down.
   */
  def shutdown(): Unit = {
    var i = 0
    while (i < processors.length) {
      processors(i).shutdown()
      i += 1
    }
  }

  override def close(): Unit = shutdown()
}

/** Creates [[LoggerProviderBuilder]]s. */
object LoggerProvider {

  /**
   * Returns a builder with `Resource.default`, no processors and the span
   * storage that the global `log` and `trace` share.
   */
  def builder: LoggerProviderBuilder = new LoggerProviderBuilder(
    resource = Resource.default,
    processors = Seq.empty,
    contextStorage = None
  )
}

/**
 * Configures a [[LoggerProvider]]. Each setter changes this builder and returns
 * it.
 */
final class LoggerProviderBuilder private[telemetry] (
  private var resource: Resource,
  private var processors: Seq[LogRecordProcessor],
  private var contextStorage: Option[ContextStorage[Option[SpanContext]]] = None
) {

  /** Sets the resource that every record of the provider's loggers carries. */
  def setResource(resource: Resource): LoggerProviderBuilder = {
    this.resource = resource
    this
  }

  /**
   * Adds a processor; each record goes to the processors in the order they were
   * added. Without one, the loggers drop every record.
   */
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder = {
    this.processors = this.processors :+ processor
    this
  }

  /**
   * Sets the storage that the loggers read the current span from.
   *
   * Pass the same instance to `TracerProviderBuilder#setContextStorage`: when
   * the tracer and the logger use different storages, log records carry no
   * trace or span ids, and nothing warns about it (a known bug, since the
   * shared default storage is not public).
   */
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder = {
    this.contextStorage = Some(contextStorage)
    this
  }

  /**
   * Builds the provider. Without `setContextStorage`, it uses the span storage
   * that the global `log` and `trace` share.
   */
  def build(): LoggerProvider = {
    val cs = contextStorage.getOrElse(ContextStorage.defaultSpanContextStorage)
    new LoggerProvider(resource, processors.toArray, cs)
  }
}
