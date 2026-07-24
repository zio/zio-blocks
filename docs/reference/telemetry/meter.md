---
id: meter
title: "Meter"
description: "Instrument factory in the ZIO Blocks Telemetry metrics pillar — creates and registers Counters, UpDownCounters, Histograms, and Gauges for a named InstrumentationScope."
keywords:
  - "Meter"
  - "Metrics Factory"
  - "InstrumentationScope"
  - "CounterBuilder"
  - "HistogramBuilder"
  - "LabeledCounter"
  - "MeterProvider"
---

A `Meter` is what you use to create metric instruments — [counters](./counter.md), [up/down counters](./up-down-counter.md), [histograms](./histogram.md), and [gauges](./gauge.md) — for one part of your application. Each `Meter` carries a name (its [`InstrumentationScope`](./instrumentation-scope.md)) identifying the library or component the metrics come from, so measurements stay grouped by their source. 

```scala
final class Meter private[telemetry] (
  val instrumentationScope: InstrumentationScope
)
```

You get a `Meter` from [`MeterProvider#get`](./meter-provider.md), or from the global [`metric.get`](./metric.md) shortcut.

## Usage

The following example obtains a `Meter`, builds a counter with a unit annotation, increments it, and retrieves the snapshot:

```scala
import zio.blocks.telemetry._

val meter: Meter  = metric.get("com.example")
val c: Counter    = meter.counterBuilder("requests").setUnit("1").build()

c.add(1L)

val snapshot: MetricData = c.collect()
```

Instruments created through the builder methods are automatically registered with the `Meter`'s internal registry. The `MetricReader` returned by `MeterProvider#reader` collects from every registered instrument when `collectAllMetrics()` is called.

## Key Operations

### Builder factories

Each factory returns a fluent builder pre-wired to this `Meter`. Call `setDescription`, `setUnit`, then `build` (or `buildWithCallback` for observable instruments):

| Method | Returns | Description |
|---|---|---|
| `counterBuilder(name: String): CounterBuilder` | `CounterBuilder` | Starts a `Counter` builder. |
| `upDownCounterBuilder(name: String): UpDownCounterBuilder` | `UpDownCounterBuilder` | Starts an `UpDownCounter` builder. |
| `histogramBuilder(name: String): HistogramBuilder` | `HistogramBuilder` | Starts a `Histogram` builder. |
| `gaugeBuilder(name: String): GaugeBuilder` | `GaugeBuilder` | Starts a `Gauge` builder. |

All four builder types expose `setDescription(desc: String)`, `setUnit(u: String)`, and `build()`. The counter, up-down counter, and gauge builders also expose `buildWithCallback(callback: ObservableCallback => Unit)` to create push-style observable instruments.

### Labeled shortcuts

Pre-declared label names eliminate per-call attribute construction and validate arity at runtime:

| Method | Returns | Description |
|---|---|---|
| `labeledCounter(name: String, labels: String*): LabeledCounter` | [`LabeledCounter`](./labeled-counter.md) | Creates a `Counter` with fixed label names. |
| `labeledHistogram(name: String, labels: String*): LabeledHistogram` | [`LabeledHistogram`](./labeled-histogram.md) | Creates a `Histogram` with fixed label names. |
| `labeledGauge(name: String, labels: String*): LabeledGauge` | [`LabeledGauge`](./labeled-gauge.md) | Creates a `Gauge` with fixed label names. |

```scala
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")

val reqs = meter.labeledCounter("http.requests", "method", "status")
reqs.add(1L, "GET", "200")
reqs.add(1L, "POST", "201")
```

:::note
`metric.counter(name)`, `metric.histogram(name)`, and `metric.gauge(name)` are convenience wrappers that call `metric.get("default")` and invoke the corresponding builder in a single call. Use `metric.get(name)` directly when you need a named instrumentation scope or want to build multiple instruments from the same `Meter`.
:::
