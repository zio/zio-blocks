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

A `Meter` creates metric instruments — counters, up/down counters, histograms, and gauges — for one part of your application. Its name (an [`InstrumentationScope`](../instrumentation-scope.md)) identifies the source, keeping measurements grouped.

Get a `Meter` from [`MeterProvider#get`](./meter-provider.md) or the global [`metric.get`](./index.md) shortcut.

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

## Instrument Builders

Each builder factory is pre-wired to the `Meter`:

```scala
final class Meter {
  def counterBuilder(name: String): CounterBuilder
  def upDownCounterBuilder(name: String): UpDownCounterBuilder
  def histogramBuilder(name: String): HistogramBuilder
  def gaugeBuilder(name: String): GaugeBuilder
}
```

Each factory returns a builder with the same fluent interface — shown here for `CounterBuilder`; the others mirror it, returning their own builder and instrument types. For example, the `CounterBuilder` has `setDescription`, `setUnit`, and `build` methods, plus `buildWithCallback` for pull-style observable counters:

```scala
final class CounterBuilder {
  def setDescription(desc: String): CounterBuilder
  def setUnit(u: String): CounterBuilder
  def build(): Counter
  def buildWithCallback(callback: ObservableCallback => Unit): ObservableCounter
}
```

The following example shows how to create a `Meter`, build a counter with description and unit, and register it:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")
val requests: Counter = meter.counterBuilder("http.requests")
  .setDescription("Total HTTP requests")
  .setUnit("1")
  .build()
```

`setDescription` and `setUnit` attach exporter-facing **metadata** — a documentation string and a UCUM unit like `"ms"`, `"bytes"`, or `"1"` (dimensionless). Both are descriptive only (they never scale or validate recorded values), and each returns the builder so calls chain. `build()` then creates the instrument and registers it with the `Meter`, so a `MetricReader` includes it in `collectAllMetrics()`. 

The counter, up-down counter, and gauge builders also offer `buildWithCallback`, which produces a **pull-style observable** instrument — instead of calling `add`/`record`, you supply a callback the SDK invokes at collection time to read the current value; `HistogramBuilder` has no such method. An observable gauge that samples JVM heap usage on every collection:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")
val rt = Runtime.getRuntime

meter.gaugeBuilder("jvm.memory.used").setUnit("bytes").buildWithCallback { cb =>
  cb.record((rt.totalMemory() - rt.freeMemory()).toDouble, Attributes.empty)
}
```

## Instruments

Each instrument tracks one time series per distinct `Attributes` set. They all share the same shape — `add`/`record` (a typed `Attributes` or vararg `(String, Any)*` tuples), a `bind(attributes)` handle that skips the per-call lookup on a hot path, and `collect(): MetricData` — differing only in value semantics.

### Counter (Cumulative)

A monotonically increasing `Long` total; `add` non-negative deltas — negatives are silently ignored. `collect` returns `MetricData.SumData`.

```scala
final class Counter {
  def add(value: Long, attributes: Attributes): Unit
  def add(value: Long, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundCounter   // BoundCounter#add(value: Long)
  def collect(): MetricData
}
```

### UpDownCounter (Bidirectional)

Like `Counter` but accepts positive *and* negative deltas — for a quantity that rises and falls, such as active connections or queue depth. `collect` returns `MetricData.SumData`.

```scala
final class UpDownCounter {
  def add(value: Long, attributes: Attributes): Unit
  def add(value: Long, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundUpDownCounter
  def collect(): MetricData
}
```

### Histogram (Distribution)

Buckets `Double` samples for percentile and latency analysis; default boundaries are `[0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000]`. `collect` returns `MetricData.HistogramData` (per-bucket counts, sum, min, max).

```scala
final class Histogram {
  def record(value: Double, attributes: Attributes): Unit
  def record(value: Double, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundHistogram
  def collect(): MetricData
}
```

:::note
There is no builder setter for custom bucket boundaries; construct `Histogram(name, description, unit, boundaries)` directly when you need non-default buckets.
:::

### Gauge (Last-Write)

Stores the most recent `Double` per attribute set — the last write wins — for current-state values like CPU usage, memory pressure, or temperature. `collect` returns `MetricData.GaugeData`.

```scala
final class Gauge {
  def record(value: Double, attributes: Attributes): Unit
  def record(value: Double, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundGauge
  def collect(): MetricData
}
```

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
