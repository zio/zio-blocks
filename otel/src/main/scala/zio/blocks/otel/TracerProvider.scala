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

final class TracerProvider(
  resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  private[otel] val contextStorage: ContextStorage[Option[SpanContext]]
) {

  def get(name: String, version: String = ""): Tracer = {
    val scope = InstrumentationScope(
      name = name,
      version = if (version.isEmpty) None else Some(version)
    )
    new Tracer(scope, resource, sampler, processors, contextStorage)
  }

  def shutdown(): Unit =
    processors.foreach(_.shutdown())

  def forceFlush(): Unit =
    processors.foreach(_.forceFlush())
}

object TracerProvider {

  def builder: TracerProviderBuilder = new TracerProviderBuilder(
    resource = Resource.default,
    sampler = AlwaysOnSampler,
    processors = Seq.empty,
    contextStorage = None
  )
}

final class TracerProviderBuilder private[otel] (
  private var resource: Resource,
  private var sampler: Sampler,
  private var processors: Seq[SpanProcessor],
  private var contextStorage: Option[ContextStorage[Option[SpanContext]]] = None
) {

  def setResource(resource: Resource): TracerProviderBuilder = {
    this.resource = resource
    this
  }

  def setSampler(sampler: Sampler): TracerProviderBuilder = {
    this.sampler = sampler
    this
  }

  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder = {
    this.processors = this.processors :+ processor
    this
  }

  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder = {
    this.contextStorage = Some(contextStorage)
    this
  }

  def build(): TracerProvider = {
    val cs = contextStorage.getOrElse(ContextStorage.create[Option[SpanContext]](None))
    new TracerProvider(resource, sampler, processors, cs)
  }
}
