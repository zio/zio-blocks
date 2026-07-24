---
id: meter
title: "Meter"
description: "Creates metric instruments — Counters, UpDownCounters, Histograms, and Gauges — for a named scope in ZIO Blocks Telemetry."
keywords:
  - "Meter"
  - "Metrics Factory"
  - "InstrumentationScope"
  - "MeterProvider"
---

A `Meter` creates metric instruments — [counters](./counter.md), [up/down counters](./up-down-counter.md), [histograms](./histogram.md), and [gauges](./gauge.md) — for one part of your application. Its name (an [`InstrumentationScope`](./instrumentation-scope.md)) identifies the source, keeping measurements grouped.

Get a `Meter` from [`MeterProvider#get`](./meter-provider.md) or the global [`metric.get`](./metric.md) shortcut.

## Usage

Obtain a `Meter`, build a counter, increment it, and read the snapshot:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")
val c: Counter   = meter.counterBuilder("requests").setUnit("1").build()
c.add(1L)
val snapshot: MetricData = c.collect()
```

Every instrument you build is registered with the `Meter` automatically. The `MetricReader` from `MeterProvider#reader` then collects from all of them via `collectAllMetrics()`.

## Builders

Each builder factory is pre-wired to the `Meter`:

```scala
final class Meter {
  def counterBuilder(name: String): CounterBuilder
  def upDownCounterBuilder(name: String): UpDownCounterBuilder
  def histogramBuilder(name: String): HistogramBuilder
  def gaugeBuilder(name: String): GaugeBuilder
}
```

Every builder supports three methods:

- `setDescription(text)` — attach a human-readable description of the instrument.
- `setUnit(unit)` — set the unit of measure (for example, `"ms"` or `"bytes"`). Use `"1"` for dimensionless values such as a plain count or a ratio.
- `build()` — create the instrument and register it with the `Meter`.

The counter, up-down counter, and gauge builders also offer `buildWithCallback(callback)` for observable (push-style) instruments.

## Labeled instruments

Declaring label names up front avoids building `Attributes` on every call and validates their count at runtime. Each factory returns a labeled wrapper around the base instrument:

```scala
final class Meter {
  def labeledCounter(name: String, labels: String*): LabeledCounter
  def labeledHistogram(name: String, labels: String*): LabeledHistogram
  def labeledGauge(name: String, labels: String*): LabeledGauge
}
```

A labeled instrument holds a fixed, ordered list of label names. You pass label values positionally — in that order — and it builds the `Attributes` for you; the wrong count throws `IllegalArgumentException`. `bind(...)` pre-registers one label combination and returns a low-overhead handle for a hot path, and `collect()` returns a `MetricData` snapshot from the underlying instrument.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")
val reqs = meter.labeledCounter("http.requests", "method", "status")

reqs.add(1L, "GET", "200")
reqs.add(1L, "POST", "201")

// Bind once for a hot label combination
val bound = reqs.bind("GET", "200")
bound.add(1L)

val snapshot: MetricData = reqs.collect()
```

All three share the same shape — a `labelNames` list, `bind(labelValues*)` returning a reusable `Bound*` handle for a hot label combination, and `collect()` snapshotting the underlying instrument as `MetricData` — differing only in how a value is recorded.

### LabeledCounter (Cumulative)

A counter with fixed labels; `add` a non-negative `Long` delta, and the running total is kept per label combination.

```scala
final class LabeledCounter {
  val labelNames: Array[String]
  def add(value: Long, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundCounter
  def collect(): MetricData
}
```

### LabeledHistogram (Distribution)

A histogram with fixed labels; `record` a `Double` sample and it lands in the bucketed distribution for that combination.

```scala
final class LabeledHistogram {
  val labelNames: Array[String]
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundHistogram
  def collect(): MetricData
}
```

### LabeledGauge (Last-Write)

A gauge with fixed labels; `record` the current `Double` value, and the latest write for a combination wins.

```scala
final class LabeledGauge {
  val labelNames: Array[String]
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundGauge
  def collect(): MetricData
}
```

:::note
Label values can be `String`, `Long`, `Int`, `Double`, or `Boolean` — `Int` is widened to `Long`, any other type uses `toString`.
:::

:::note
`metric.counter(name)`, `metric.histogram(name)`, and `metric.gauge(name)` are shortcuts that call `metric.get("default")` and build the instrument in one step. Use `metric.get(name)` directly when you need a named scope or want to build several instruments from the same `Meter`.
:::
