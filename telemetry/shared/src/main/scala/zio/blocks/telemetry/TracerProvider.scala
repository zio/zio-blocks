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
 * Holds the tracing configuration of a program: the [[Resource]] that names the
 * service, the [[Sampler]], the [[SpanProcessor]]s that receive spans, and the
 * `ContextStorage` that holds the current span. Every [[Tracer]] it returns
 * shares them. Build one with `TracerProvider.builder`, install it with
 * `trace.install`, and call `shutdown` when the program exits.
 *
 * A provider built without a processor records spans and then discards them:
 * the in-memory buffer behind `trace.collectedSpans` belongs to the default
 * provider of the global `trace`.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/tracer-provider/ TracerProvider]]
 */
final class TracerProvider private[telemetry] (
  resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  private[telemetry] val contextStorage: ContextStorage[Option[SpanContext]]
) extends AutoCloseable {

  /**
   * Returns a new [[Tracer]] whose spans carry the scope `name`, with `version`
   * unless it is empty. Each call creates a new tracer, so take one per
   * component and keep it.
   */
  def get(name: String, version: String = ""): Tracer = {
    val scope = InstrumentationScope(
      name = name,
      version = if (version.isEmpty) None else Some(version)
    )
    new Tracer(scope, resource, sampler, processors, contextStorage)
  }

  /**
   * Calls `shutdown` on every processor, in the order they were added. The
   * provider keeps working afterwards: its tracers still open spans and pass
   * them to the processors, so call it last.
   */
  def shutdown(): Unit =
    processors.foreach(_.shutdown())

  /**
   * Calls `forceFlush` on every processor, in the order they were added, so
   * that buffered spans are exported now.
   */
  def forceFlush(): Unit =
    processors.foreach(_.forceFlush())

  override def close(): Unit = shutdown()
}

/** Creates [[TracerProviderBuilder]]s. */
object TracerProvider {

  /**
   * Returns a builder with `Resource.default`, [[AlwaysOnSampler]], no
   * processors, and the context storage that the global `log` and the other
   * providers share.
   */
  def builder: TracerProviderBuilder = new TracerProviderBuilder(
    resource = Resource.default,
    sampler = AlwaysOnSampler,
    processors = Seq.empty,
    contextStorage = None
  )
}

/**
 * Configures a [[TracerProvider]]. Each setter changes this builder and returns
 * it.
 */
final class TracerProviderBuilder private[telemetry] (
  private var resource: Resource,
  private var sampler: Sampler,
  private var processors: Seq[SpanProcessor],
  private var contextStorage: Option[ContextStorage[Option[SpanContext]]] = None
) {

  /**
   * Sets the resource that every span of the provider carries. The default is
   * `Resource.default`, whose `service.name` is `unknown_service`.
   */
  def setResource(resource: Resource): TracerProviderBuilder = {
    this.resource = resource
    this
  }

  /**
   * Sets the sampler that decides, for each span that `Tracer#span` opens,
   * whether to record it. The default is [[AlwaysOnSampler]]. Spans started
   * with a [[SpanBuilder]] skip it.
   */
  def setSampler(sampler: Sampler): TracerProviderBuilder = {
    this.sampler = sampler
    this
  }

  /**
   * Adds `processor` after the ones added before; the provider calls its
   * processors in that order.
   */
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder = {
    this.processors = this.processors :+ processor
    this
  }

  /**
   * Replaces the storage that holds the current span. By default every provider
   * and the global `log` share one storage, so log records carry the ids of the
   * current span. With a storage of your own, pass the same instance to
   * `LoggerProviderBuilder#setContextStorage`: otherwise log records carry no
   * trace or span ids and nothing warns about it, a known bug.
   */
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder = {
    this.contextStorage = Some(contextStorage)
    this
  }

  /** Returns a new provider with the current settings. */
  def build(): TracerProvider = {
    val cs = contextStorage.getOrElse(ContextStorage.defaultSpanContextStorage)
    new TracerProvider(resource, sampler, processors, cs)
  }
}
