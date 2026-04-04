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
 * Global meter that works without any setup ceremony. Creates instruments
 * directly without MeterProvider wiring. Install a real MeterProvider later via
 * `install` to wire production exporters.
 */
object GlobalMeter {
  private val ref: AtomicReference[MeterProvider] = new AtomicReference[MeterProvider](null)

  private lazy val defaultProvider: MeterProvider = MeterProvider.builder.build()

  /** Returns a Meter for the given instrumentation scope name. */
  def get(name: String = "default"): Meter = {
    val provider = ref.get()
    if (provider != null) provider.get(name)
    else defaultProvider.get(name)
  }

  /** Replaces the default MeterProvider with a user-configured one. */
  def install(provider: MeterProvider): Unit = ref.set(provider)

  /** Reverts to the default MeterProvider. */
  def uninstall(): Unit = ref.set(null)

  /** Returns the MetricReader for the default provider. */
  def reader: MetricReader = defaultProvider.reader

  /** Creates a Counter directly on the default meter. */
  def counter(name: String): Counter =
    get().counterBuilder(name).build()

  /** Creates an UpDownCounter directly on the default meter. */
  def upDownCounter(name: String): UpDownCounter =
    get().upDownCounterBuilder(name).build()

  /** Creates a Histogram directly on the default meter. */
  def histogram(name: String): Histogram =
    get().histogramBuilder(name).build()

  /** Creates a Gauge directly on the default meter. */
  def gauge(name: String): Gauge =
    get().gaugeBuilder(name).build()
}
