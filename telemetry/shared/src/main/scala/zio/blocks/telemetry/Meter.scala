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

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The meters of one [[MeterProvider]], in the order they were created;
 * `collectAll` collects their instruments in that order.
 */
private[telemetry] final class MeterRegistry {
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
 * Creates the instruments of one component, under the component's name. Get one
 * from `MeterProvider#get`, or `metric.get` in application code; library code
 * should take a `Meter` as a parameter.
 *
 * Every instrument a meter builds is registered with its provider, so the
 * provider's `reader` collects it. Each `build()` registers a new instrument,
 * even for a name used before, so build each instrument once and keep it. The
 * collected [[MetricData]] carries neither the meter's scope nor the
 * instrument's name, description or unit.
 *
 * {{{
 * val meter = MeterProvider.builder.build().get("com.example.checkout")
 * val latency = meter.histogramBuilder("checkout.latency").setUnit("ms").setDescription("Checkout time").build()
 *
 * latency.record(42.5, "route" -> "/checkout")
 * latency.record(180.0, "route" -> "/checkout")
 *
 * val counts = latency.collect() match {
 *   case MetricData.HistogramData(points) => points.map(p => (p.count, p.sum))
 *   case _                                => Nil
 * }
 * counts.headOption // Some((2,222.5))
 * }}}
 *
 * @param instrumentationScope
 *   the name and optional version that `MeterProvider#get` was called with
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/meter/ Meter]]
 */
final class Meter private[telemetry] (
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

  /**
   * Returns a builder for a [[Counter]] named `name`, or, through
   * `buildWithCallback`, an [[ObservableCounter]].
   */
  def counterBuilder(name: String): CounterBuilder =
    new CounterBuilder(name, this)

  /**
   * Returns a builder for an [[UpDownCounter]] named `name`, or, through
   * `buildWithCallback`, an [[ObservableUpDownCounter]].
   */
  def upDownCounterBuilder(name: String): UpDownCounterBuilder =
    new UpDownCounterBuilder(name, this)

  /**
   * Returns a builder for a [[Histogram]] named `name`, with the default bucket
   * boundaries; the builder cannot change them.
   */
  def histogramBuilder(name: String): HistogramBuilder =
    new HistogramBuilder(name, this)

  /**
   * Returns a builder for a [[Gauge]] named `name`, or, through
   * `buildWithCallback`, an [[ObservableGauge]].
   */
  def gaugeBuilder(name: String): GaugeBuilder =
    new GaugeBuilder(name, this)

  /**
   * Creates and registers a [[Counter]] named `name`, wrapped in a
   * [[LabeledCounter]] that takes label values in the order of `labels`.
   *
   * {{{
   * val meter = MeterProvider.builder.build().get("com.example.api")
   * val requests = meter.labeledCounter("http.requests", "method", "status")
   *
   * requests.add(1L, "GET", "200")
   *
   * requests.labelNames.toList // List(method, status)
   * }}}
   */
  def labeledCounter(name: String, labels: String*): LabeledCounter = {
    val counter = counterBuilder(name).build()
    new LabeledCounter(counter, labels.toArray)
  }

  /**
   * Creates and registers a [[Histogram]] named `name`, with the default bucket
   * boundaries, wrapped in a [[LabeledHistogram]] that takes label values in
   * the order of `labels`.
   */
  def labeledHistogram(name: String, labels: String*): LabeledHistogram = {
    val histogram = histogramBuilder(name).build()
    new LabeledHistogram(histogram, labels.toArray)
  }

  /**
   * Creates and registers a [[Gauge]] named `name`, wrapped in a
   * [[LabeledGauge]] that takes label values in the order of `labels`.
   */
  def labeledGauge(name: String, labels: String*): LabeledGauge = {
    val gauge = gaugeBuilder(name).build()
    new LabeledGauge(gauge, labels.toArray)
  }

  private[telemetry] def registerCounter(c: Counter): Unit = {
    instruments.add(SyncCounter(c))
    ()
  }

  private[telemetry] def registerUpDownCounter(c: UpDownCounter): Unit = {
    instruments.add(SyncUpDownCounter(c))
    ()
  }

  private[telemetry] def registerHistogram(h: Histogram): Unit = {
    instruments.add(SyncHistogram(h))
    ()
  }

  private[telemetry] def registerGauge(g: Gauge): Unit = {
    instruments.add(SyncGauge(g))
    ()
  }

  private[telemetry] def registerObservableCounter(c: ObservableCounter): Unit = {
    instruments.add(ObsCounter(c))
    ()
  }

  private[telemetry] def registerObservableUpDownCounter(c: ObservableUpDownCounter): Unit = {
    instruments.add(ObsUpDownCounter(c))
    ()
  }

  private[telemetry] def registerObservableGauge(g: ObservableGauge): Unit = {
    instruments.add(ObsGauge(g))
    ()
  }

  private[telemetry] def collectInstruments(out: java.util.ArrayList[MetricData]): Unit = {
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
 * Sets the description and unit of a [[Counter]] or an [[ObservableCounter]]
 * before `Meter#counterBuilder` registers it. Each setter changes the builder
 * and returns it.
 */
final class CounterBuilder private[telemetry] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  /**
   * Sets the description; the default is empty. The instrument keeps it in
   * `description`, and the collected [[MetricData]] does not include it.
   */
  def setDescription(desc: String): CounterBuilder = {
    this.description = desc
    this
  }

  /**
   * Sets the unit, such as `ms`, `By` or `{request}`; the default is empty. The
   * instrument keeps it in `unit`, and the collected [[MetricData]] does not
   * include it.
   */
  def setUnit(u: String): CounterBuilder = {
    this.unit = u
    this
  }

  /**
   * Creates a [[Counter]] and registers it with the meter. Each call registers
   * a new one, even with the same name.
   */
  def build(): Counter = {
    val counter = Counter(name, description, unit)
    meter.registerCounter(counter)
    counter
  }

  /**
   * Creates an [[ObservableCounter]] that runs `callback` on each collection,
   * and registers it with the meter; `callback` does not run now.
   */
  def buildWithCallback(callback: ObservableCallback => Unit): ObservableCounter = {
    val obs = ObservableCounter(name, description, unit)(callback)
    meter.registerObservableCounter(obs)
    obs
  }
}

/**
 * Sets the description and unit of an [[UpDownCounter]] or an
 * [[ObservableUpDownCounter]] before `Meter#upDownCounterBuilder` registers it.
 * Each setter changes the builder and returns it.
 */
final class UpDownCounterBuilder private[telemetry] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  /**
   * Sets the description; the default is empty. The instrument keeps it in
   * `description`, and the collected [[MetricData]] does not include it.
   */
  def setDescription(desc: String): UpDownCounterBuilder = {
    this.description = desc
    this
  }

  /**
   * Sets the unit, such as `ms`, `By` or `{request}`; the default is empty. The
   * instrument keeps it in `unit`, and the collected [[MetricData]] does not
   * include it.
   */
  def setUnit(u: String): UpDownCounterBuilder = {
    this.unit = u
    this
  }

  /**
   * Creates an [[UpDownCounter]] and registers it with the meter. Each call
   * registers a new one, even with the same name.
   */
  def build(): UpDownCounter = {
    val counter = UpDownCounter(name, description, unit)
    meter.registerUpDownCounter(counter)
    counter
  }

  /**
   * Creates an [[ObservableUpDownCounter]] that runs `callback` on each
   * collection, and registers it with the meter; `callback` does not run now.
   */
  def buildWithCallback(callback: ObservableCallback => Unit): ObservableUpDownCounter = {
    val obs = ObservableUpDownCounter(name, description, unit)(callback)
    meter.registerObservableUpDownCounter(obs)
    obs
  }
}

/**
 * Sets the description and unit of a [[Histogram]] before
 * `Meter#histogramBuilder` registers it. Each setter changes the builder and
 * returns it.
 */
final class HistogramBuilder private[telemetry] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  /**
   * Sets the description; the default is empty. The instrument keeps it in
   * `description`, and the collected [[MetricData]] does not include it.
   */
  def setDescription(desc: String): HistogramBuilder = {
    this.description = desc
    this
  }

  /**
   * Sets the unit, such as `ms`, `By` or `{request}`; the default is empty. The
   * instrument keeps it in `unit`, and the collected [[MetricData]] does not
   * include it.
   */
  def setUnit(u: String): HistogramBuilder = {
    this.unit = u
    this
  }

  /**
   * Creates a [[Histogram]] and registers it with the meter. Each call
   * registers a new one, even with the same name.
   */
  def build(): Histogram = {
    val histogram = Histogram(name, description, unit)
    meter.registerHistogram(histogram)
    histogram
  }
}

/**
 * Sets the description and unit of a [[Gauge]] or an [[ObservableGauge]] before
 * `Meter#gaugeBuilder` registers it. Each setter changes the builder and
 * returns it.
 */
final class GaugeBuilder private[telemetry] (
  private val name: String,
  private val meter: Meter,
  private var description: String = "",
  private var unit: String = ""
) {

  /**
   * Sets the description; the default is empty. The instrument keeps it in
   * `description`, and the collected [[MetricData]] does not include it.
   */
  def setDescription(desc: String): GaugeBuilder = {
    this.description = desc
    this
  }

  /**
   * Sets the unit, such as `ms`, `By` or `{request}`; the default is empty. The
   * instrument keeps it in `unit`, and the collected [[MetricData]] does not
   * include it.
   */
  def setUnit(u: String): GaugeBuilder = {
    this.unit = u
    this
  }

  /**
   * Creates a [[Gauge]] and registers it with the meter. Each call registers a
   * new one, even with the same name.
   */
  def build(): Gauge = {
    val gauge = Gauge(name, description, unit)
    meter.registerGauge(gauge)
    gauge
  }

  /**
   * Creates an [[ObservableGauge]] that runs `callback` on each collection, and
   * registers it with the meter; `callback` does not run now.
   */
  def buildWithCallback(callback: ObservableCallback => Unit): ObservableGauge = {
    val obs = ObservableGauge(name, description, unit)(callback)
    meter.registerObservableGauge(obs)
    obs
  }
}
