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

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Internal thread-safe registry that tracks all instruments across all meters.
 */
private[otel] final class MeterRegistry {
  private val meters = new CopyOnWriteArrayList[Meter]()

  def register(meter: Meter): Unit = {
    meters.add(meter)
    ()
  }

  def collectAll(): Seq[MetricData] = {
    val result = new java.util.ArrayList[MetricData]()
    val it     = meters.iterator()
    while (it.hasNext) {
      val meter = it.next()
      meter.collectInstruments(result)
    }
    SyncInstrumentsHelper.listFromJava(result)
  }
}

/**
 * A Meter creates and manages metric instruments for a given
 * InstrumentationScope. All instruments created through a Meter are registered
 * internally so the MetricReader can collect from them.
 *
 * @param instrumentationScope
 *   the scope identifying the instrumentation library
 */
final class Meter private[otel] (
  val instrumentationScope: InstrumentationScope
) {
  private sealed trait Collectible
  private final case class SyncCounter(c: Counter)                      extends Collectible
  private final case class SyncUpDownCounter(c: UpDownCounter)          extends Collectible
  private final case class SyncHistogram(h: Histogram)                  extends Collectible
  private final case class SyncGauge(g: Gauge)                          extends Collectible
  private final case class ObsCounter(c: ObservableCounter)             extends Collectible
  private final case class ObsUpDownCounter(c: ObservableUpDownCounter) extends Collectible
  private final case class ObsGauge(g: ObservableGauge)                 extends Collectible
  private val instruments = new CopyOnWriteArrayList[Collectible]()

  def counterBuilder(name: String): CounterBuilder =
    new CounterBuilder(name, this)

  def upDownCounterBuilder(name: String): UpDownCounterBuilder =
    new UpDownCounterBuilder(name, this)

  def histogramBuilder(name: String): HistogramBuilder =
    new HistogramBuilder(name, this)

  def gaugeBuilder(name: String): GaugeBuilder =
    new GaugeBuilder(name, this)

  def labeledCounter(name: String, labels: String*): LabeledCounter = {
    val counter = counterBuilder(name).build()
    new LabeledCounter(counter, labels.toArray)
  }

  def labeledHistogram(name: String, labels: String*): LabeledHistogram = {
    val histogram = histogramBuilder(name).build()
    new LabeledHistogram(histogram, labels.toArray)
  }

  def labeledGauge(name: String, labels: String*): LabeledGauge = {
    val gauge = gaugeBuilder(name).build()
    new LabeledGauge(gauge, labels.toArray)
  }

  private[otel] def registerCounter(c: Counter): Unit = {
    instruments.add(SyncCounter(c))
    ()
  }

  private[otel] def registerUpDownCounter(c: UpDownCounter): Unit = {
    instruments.add(SyncUpDownCounter(c))
    ()
  }

  private[otel] def registerHistogram(h: Histogram): Unit = {
    instruments.add(SyncHistogram(h))
    ()
  }

  private[otel] def registerGauge(g: Gauge): Unit = {
    instruments.add(SyncGauge(g))
    ()
  }

  private[otel] def registerObservableCounter(c: ObservableCounter): Unit = {
    instruments.add(ObsCounter(c))
    ()
  }

  private[otel] def registerObservableUpDownCounter(c: ObservableUpDownCounter): Unit = {
    instruments.add(ObsUpDownCounter(c))
    ()
  }

  private[otel] def registerObservableGauge(g: ObservableGauge): Unit = {
    instruments.add(ObsGauge(g))
    ()
  }

  private[otel] def collectInstruments(out: java.util.ArrayList[MetricData]): Unit = {
    val it = instruments.iterator()
    while (it.hasNext) {
      val data = (it.next(): @unchecked) match {
        case SyncCounter(c)       => c.collect()
        case SyncUpDownCounter(c) => c.collect()
        case SyncHistogram(h)     => h.collect()
        case SyncGauge(g)         => g.collect()
        case ObsCounter(c)        => c.collect()
        case ObsUpDownCounter(c)  => c.collect()
        case ObsGauge(g)          => g.collect()
      }
      out.add(data)
      ()
    }
  }
}

/**
 * Builder for Counter instruments.
 */
final class CounterBuilder private[otel] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  def setDescription(desc: String): CounterBuilder = {
    this.description = desc
    this
  }

  def setUnit(u: String): CounterBuilder = {
    this.unit = u
    this
  }

  def build(): Counter = {
    val counter = Counter(name, description, unit)
    meter.registerCounter(counter)
    counter
  }

  def buildWithCallback(callback: ObservableCallback => Unit): ObservableCounter = {
    val obs = ObservableCounter(name, description, unit)(callback)
    meter.registerObservableCounter(obs)
    obs
  }
}

/**
 * Builder for UpDownCounter instruments.
 */
final class UpDownCounterBuilder private[otel] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  def setDescription(desc: String): UpDownCounterBuilder = {
    this.description = desc
    this
  }

  def setUnit(u: String): UpDownCounterBuilder = {
    this.unit = u
    this
  }

  def build(): UpDownCounter = {
    val counter = UpDownCounter(name, description, unit)
    meter.registerUpDownCounter(counter)
    counter
  }

  def buildWithCallback(callback: ObservableCallback => Unit): ObservableUpDownCounter = {
    val obs = ObservableUpDownCounter(name, description, unit)(callback)
    meter.registerObservableUpDownCounter(obs)
    obs
  }
}

/**
 * Builder for Histogram instruments.
 */
final class HistogramBuilder private[otel] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  def setDescription(desc: String): HistogramBuilder = {
    this.description = desc
    this
  }

  def setUnit(u: String): HistogramBuilder = {
    this.unit = u
    this
  }

  def build(): Histogram = {
    val histogram = Histogram(name, description, unit)
    meter.registerHistogram(histogram)
    histogram
  }
}

/**
 * Builder for Gauge instruments.
 */
final class GaugeBuilder private[otel] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  def setDescription(desc: String): GaugeBuilder = {
    this.description = desc
    this
  }

  def setUnit(u: String): GaugeBuilder = {
    this.unit = u
    this
  }

  def build(): Gauge = {
    val gauge = Gauge(name, description, unit)
    meter.registerGauge(gauge)
    gauge
  }

  def buildWithCallback(callback: ObservableCallback => Unit): ObservableGauge = {
    val obs = ObservableGauge(name, description, unit)(callback)
    meter.registerObservableGauge(obs)
    obs
  }
}
