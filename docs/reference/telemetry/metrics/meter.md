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

`Meter` is the instrument factory for a single instrumentation scope — a named library or component. Every instrument it builds ([`Counter`](./instruments.md), `UpDownCounter`, `Histogram`, or `Gauge`) is registered behind the owning [`MeterProvider`](./meter-provider.md) the moment you call `build()`, so `provider.reader.collectAllMetrics()` sees it with no extra wiring. Library code takes a `Meter` — obtained from a `MeterProvider`, or the global `metric.get(scope)` in application code — to attribute its metrics to its own scope; a `Meter` is always produced by a provider and cannot be constructed directly.

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
