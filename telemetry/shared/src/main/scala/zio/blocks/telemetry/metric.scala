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
 * Global metrics entry point. Works without setup. Call `install` to wire
 * production exporters.
 *
 * App code uses this directly. Library code should accept `Meter` via DI.
 */
object metric {
  private val defaultProvider: MeterProvider = MeterProvider.builder.build()

  private val ref: AtomicReference[MeterProvider] = new AtomicReference[MeterProvider](defaultProvider)

  private def provider: MeterProvider = ref.get()

  /** Returns a named Meter for a specific instrumentation scope. */
  def get(name: String): Meter = provider.get(name)

  /** Creates a Counter on the default meter. */
  def counter(name: String): Counter =
    provider.get("default").counterBuilder(name).build()

  /** Creates an UpDownCounter on the default meter. */
  def upDownCounter(name: String): UpDownCounter =
    provider.get("default").upDownCounterBuilder(name).build()

  /** Creates a Histogram on the default meter. */
  def histogram(name: String): Histogram =
    provider.get("default").histogramBuilder(name).build()

  /** Creates a Gauge on the default meter. */
  def gauge(name: String): Gauge =
    provider.get("default").gaugeBuilder(name).build()

  /** Replaces the default MeterProvider with a user-configured one. */
  def install(provider: MeterProvider): Unit = ref.set(provider)

  /** Remove the installed MeterProvider. After this, metrics use the default provider. */
  def removeAll(): Unit = ref.set(MeterProvider.builder.build())

  /** Returns the MetricReader for the current provider. */
  def reader: MetricReader = provider.reader
}
