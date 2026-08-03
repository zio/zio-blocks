---
id: meter
title: "Meter"
description: "Creates and registers metric instruments (Counter, UpDownCounter, Histogram, Gauge) for a named InstrumentationScope."
keywords:
  - "Application Metrics"
  - "Instrument Registration"
  - "Metric Instruments"
  - "Meter"
---

A `Meter` makes [instruments](./instruments.md) — the counters, histograms, and gauges you record measurements through. It's tied to one name, the component or library it belongs to, and a [`MeterProvider`](./meter-provider.md) hands you one; you never construct it yourself.

Two reasons to go through a `Meter` rather than `metric.counter("…")`. The name marks which part of the system a measurement came from, so metrics from a library you depend on stay distinguishable from your own. And its builders let you say more about an instrument than its name — a unit and a description, so a dashboard can label an axis instead of guessing, or a fixed set of label names for a hot path.

Building through a `Meter` is also what makes an instrument *collectable*. `build()` registers the instrument on its meter, and the provider's registry already holds that meter — so reading metrics reaches it, along with every instrument from every scope of that provider, and you needn't keep a reference for collection's sake.

Three ways to lose measurements to that wiring:

1. **Constructing an instrument directly.** It compiles, because the companion `apply` is public — `Counter("http.requests", "", "")` records perfectly well into an object nothing registered, so `collectAllMetrics()` never sees it.
2. **Building the same instrument twice.** Calling `metric.counter("http.requests")` twice builds two registered counters, splitting one logical metric across two series that a consumer cannot merge. Call it once and keep the result; nothing unregisters an instrument.
3. **Crossing providers.** An instrument reaches only the reader of the provider whose meter built it. `metric.install(...)` swaps in a provider with an empty registry, so create your instruments after installing, and don't expect `metric.reader` to see instruments built from a provider you constructed yourself.

```scala
final class Meter private[telemetry] (val instrumentationScope: InstrumentationScope) {
  // Builder factories — chain setDescription/setUnit, then build()
  def counterBuilder(name: String):       CounterBuilder
  def upDownCounterBuilder(name: String):  UpDownCounterBuilder
  def histogramBuilder(name: String):      HistogramBuilder
  def gaugeBuilder(name: String):          GaugeBuilder

  // Labeled shortcuts — declare label names once, record positionally
  def labeledCounter(name: String, labels: String*):   LabeledCounter
  def labeledHistogram(name: String, labels: String*): LabeledHistogram
  def labeledGauge(name: String, labels: String*):     LabeledGauge
}
```

## Usage

Obtain a `Meter` from a provider (or `metric.get` in application code), then use a builder to create an instrument with a description and unit and record through it. Identical `(name, version)` scopes return the same cached `Meter`, so instruments created at different call sites in one library land under a single scope:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example.server")

val requests = meter.counterBuilder("http.requests")
  .setDescription("Total HTTP requests")
  .setUnit("1")
  .build()
requests.add(1L, "method" -> "GET", "status" -> "200")

val latency = meter.histogramBuilder("request.latency").setUnit("ms").build()
latency.record(42.5, "route" -> "/api/orders")
```

When a hot path records the same label names over and over, `labeledCounter`, `labeledHistogram`, and `labeledGauge` declare those names once so callers pass values positionally instead of allocating tuples each time:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example")

val byRoute = meter.labeledCounter("http.requests", "method", "status")
byRoute.add(1L, "GET", "200")
```

Each builder also exposes an asynchronous path — `buildWithCallback` on the counter, up-down counter, and gauge builders creates a pull-based instrument whose value is read from a callback at collection time, rather than pushed with `add`/`record`. See [Instruments](./instruments.md) for the full recording API, [Labeled Instruments](./labeled-instruments.md) for positional recording, and [MetricData](./metric-data.md) for what collection returns.
