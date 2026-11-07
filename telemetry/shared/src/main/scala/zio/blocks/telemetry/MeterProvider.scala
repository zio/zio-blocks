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
 * Holds the metrics configuration of a program: the [[Resource]] that names the
 * service, and one registry that every meter it returns joins, read through
 * `reader`. Build one with `MeterProvider.builder`, and install it with
 * `metric.install` before creating instruments.
 *
 * The collected [[MetricData]] does not carry the resource; an exporter reads
 * `resource` and attaches it.
 *
 * {{{
 * val provider = MeterProvider.builder
 *   .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
 *   .build()
 * val payments = provider.get("com.example.payments").counterBuilder("payments.processed").build()
 *
 * payments.add(3L, "currency" -> "EUR")
 *
 * provider.reader.collectAllMetrics().size // 1
 * provider.resource.attributes.get(Attributes.ServiceName) // Some(payments)
 * }}}
 *
 * @param resource
 *   the resource that names the service, which exporters attach to the data
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/meter-provider/ MeterProvider]]
 */
final class MeterProvider private[telemetry] (
  val resource: Resource,
  private val meterRegistry: MeterRegistry
) extends AutoCloseable {
  private val meters = new ConcurrentHashMap[InstrumentationScope, Meter]()

  /**
   * The reader of this provider, the same for its whole life:
   * `collectAllMetrics()` returns one [[MetricData]] per instrument that its
   * meters registered, meter by meter in the order the meters were created, and
   * within a meter in the order its instruments were built.
   */
  val reader: MetricReader = new MetricReaderImpl(meterRegistry)

  /**
   * Returns the meter for the scope `name`, with `version` unless it is empty,
   * creating and registering it on the first call; later calls with the same
   * name and version return the same meter.
   */
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

  /**
   * Calls `reader.shutdown()`, which does nothing for the built-in reader: the
   * provider and its instruments keep working afterwards.
   */
  def shutdown(): Unit =
    reader.shutdown()

  override def close(): Unit = shutdown()
}

/** Creates [[MeterProviderBuilder]]s. */
object MeterProvider {

  /** Returns a builder whose resource is `Resource.default`. */
  def builder: MeterProviderBuilder = new MeterProviderBuilder(
    resource = Resource.default
  )
}

/**
 * Configures a [[MeterProvider]]; the resource is its only setting.
 */
final class MeterProviderBuilder private[telemetry] (
  private var resource: Resource
) {

  /**
   * Sets the resource of the provider. The default is `Resource.default`, whose
   * `service.name` is `unknown_service`.
   */
  def setResource(resource: Resource): MeterProviderBuilder = {
    this.resource = resource
    this
  }

  /** Returns a new provider with an empty registry. */
  def build(): MeterProvider = {
    val registry = new MeterRegistry()
    new MeterProvider(resource, registry)
  }
}
