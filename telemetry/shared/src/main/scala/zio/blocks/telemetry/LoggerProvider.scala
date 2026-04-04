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

final class LoggerProvider(
  resource: Resource,
  processors: Seq[LogRecordProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {

  def get(name: String, version: String = ""): Logger = {
    val scope = InstrumentationScope(
      name = name,
      version = if (version.isEmpty) None else Some(version)
    )
    new Logger(scope, resource, processors, contextStorage)
  }

  def shutdown(): Unit =
    processors.foreach(_.shutdown())
}

object LoggerProvider {

  def builder: LoggerProviderBuilder = new LoggerProviderBuilder(
    resource = Resource.default,
    processors = Seq.empty,
    contextStorage = None
  )
}

final class LoggerProviderBuilder private[telemetry] (
  private var resource: Resource,
  private var processors: Seq[LogRecordProcessor],
  private var contextStorage: Option[ContextStorage[Option[SpanContext]]] = None
) {

  def setResource(resource: Resource): LoggerProviderBuilder = {
    this.resource = resource
    this
  }

  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder = {
    this.processors = this.processors :+ processor
    this
  }

  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder = {
    this.contextStorage = Some(contextStorage)
    this
  }

  def build(): LoggerProvider = {
    val cs = contextStorage.getOrElse(ContextStorage.create[Option[SpanContext]](None))
    new LoggerProvider(resource, processors, cs)
  }
}
