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

import java.util.concurrent.atomic.AtomicReference

/**
 * The global meter. `metric.counter("http.requests")` and the other factories
 * create an instrument on the meter `default` with no set-up, and
 * `metric.reader` reads back what every registered instrument recorded.
 *
 * A known bug: each factory call creates and registers a new instrument, even
 * for a name it has seen before, so two `metric.counter("http.requests")` calls
 * give two separate series. Create each instrument once and keep it in a `val`.
 *
 * `metric.install` replaces the [[MeterProvider]], for instance to set the
 * [[Resource]] that names the service. Nothing exports metrics by itself: an
 * exporter reads `metric.reader` on its own schedule. Application code calls
 * `metric` directly; library code should take a [[Meter]] as a parameter
 * instead.
 *
 * {{{
 * val requests = metric.counter("http.requests")
 * requests.add(1L, "method" -> "GET", "status" -> "200")
 * requests.add(1L, "method" -> "GET", "status" -> "200")
 * requests.add(1L, "method" -> "POST", "status" -> "201")
 *
 * val method = AttributeKey.string("method")
 * val totals = requests.collect() match {
 *   case MetricData.SumData(points) => points.map(p => p.attributes.get(method) -> p.value).toMap
 *   case _                          => Map.empty[Option[String], Long]
 * }
 * totals(Some("GET")) // 2
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/ Metrics]]
 */
object metric {
  private val defaultProvider: MeterProvider = MeterProvider.builder.build()

  private val ref: AtomicReference[MeterProvider] = new AtomicReference[MeterProvider](defaultProvider)

  private def provider: MeterProvider = ref.get()

  /**
   * Returns the [[Meter]] of the installed provider for the scope `name`;
   * asking again for the same name returns the same meter. A meter taken before
   * `metric.install` stays with the old provider.
   */
  def get(name: String): Meter = provider.get(name)

  /**
   * Creates a [[Counter]] named `name`, with no description or unit, on the
   * meter `default` of the installed provider, and registers it so that
   * `metric.reader` collects it.
   *
   * A known bug: every call creates and registers a new counter, even for the
   * same name, so call it once and keep the counter. Use
   * `metric.get(...).counterBuilder` to set a description or unit.
   */
  def counter(name: String): Counter =
    provider.get("default").counterBuilder(name).build()

  /**
   * Creates an [[UpDownCounter]] named `name` on the meter `default` and
   * registers it, as `metric.counter` does; each call creates a new one.
   */
  def upDownCounter(name: String): UpDownCounter =
    provider.get("default").upDownCounterBuilder(name).build()

  /**
   * Creates a [[Histogram]] named `name`, with the default bucket boundaries,
   * on the meter `default` and registers it, as `metric.counter` does; each
   * call creates a new one.
   */
  def histogram(name: String): Histogram =
    provider.get("default").histogramBuilder(name).build()

  /**
   * Creates a [[Gauge]] named `name` on the meter `default` and registers it,
   * as `metric.counter` does; each call creates a new one.
   */
  def gauge(name: String): Gauge =
    provider.get("default").gaugeBuilder(name).build()

  /**
   * Replaces the provider behind `metric` with `provider`, for every thread.
   * Instruments and meters created before keep recording into the old provider,
   * which `metric.reader` no longer reads, so install before creating
   * instruments. The old provider is not shut down.
   *
   * {{{
   * val provider = MeterProvider.builder
   *   .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
   *   .build()
   * metric.install(provider)
   *
   * metric.counter("orders.placed").add(1L)
   *
   * metric.reader.eq(provider.reader) // true
   * metric.reader.collectAllMetrics().size // 1
   * }}}
   */
  def install(provider: MeterProvider): Unit = ref.set(provider)

  /**
   * Installs a new, empty provider with `Resource.default`. Instruments created
   * before keep recording, but `metric.reader` no longer returns their data,
   * and no call brings the first default provider back. The old provider is not
   * shut down.
   */
  def removeAll(): Unit = ref.set(MeterProvider.builder.build())

  /**
   * Returns the [[MetricReader]] of the installed provider:
   * `collectAllMetrics()` returns one [[MetricData]] per instrument registered
   * on any of its meters.
   */
  def reader: MetricReader = provider.reader
}
