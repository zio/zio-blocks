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

import java.util.concurrent.ConcurrentHashMap

/**
 * Provides Meter instances keyed by instrumentation scope. A MeterProvider
 * creates a shared MeterRegistry and MetricReader that collects from all
 * meters.
 *
 * @param resource
 *   the resource describing the entity producing telemetry
 */
final class MeterProvider private[telemetry] (
  val resource: Resource,
  private val meterRegistry: MeterRegistry
) extends AutoCloseable {
  private val meters = new ConcurrentHashMap[InstrumentationScope, Meter]()

  val reader: MetricReader = new MetricReaderImpl(meterRegistry)

  def get(name: String, version: String = ""): Meter = {
    val scope = InstrumentationScope(
      name = name,
      version = if (version.isEmpty) None else Some(version)
    )
    meters.computeIfAbsent(
      scope,
      { s =>
        val meter = new Meter(s)
        meterRegistry.register(meter)
        meter
      }
    )
  }

  def shutdown(): Unit =
    reader.shutdown()

  override def close(): Unit = shutdown()
}

object MeterProvider {

  def builder: MeterProviderBuilder = new MeterProviderBuilder(
    resource = Resource.default
  )
}

/**
 * Builder for MeterProvider.
 */
final class MeterProviderBuilder private[telemetry] (
  private var resource: Resource
) {

  def setResource(resource: Resource): MeterProviderBuilder = {
    this.resource = resource
    this
  }

  def build(): MeterProvider = {
    val registry = new MeterRegistry()
    new MeterProvider(resource, registry)
  }
}
