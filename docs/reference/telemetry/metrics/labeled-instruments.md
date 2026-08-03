---
id: labeled-instruments
title: "Labeled Instruments"
description: "Metric instruments with pre-declared label names. Record values positionally; runtime arity validation prevents label mismatches."
keywords:
  - "Application Metrics"
  - "Dimensional Metrics"
  - "Prometheus-Style Labels"
  - "Labeled Instruments"
sidebar_label: "Labeled Instruments"
---

A labeled instrument is an ordinary counter, histogram, or gauge that already knows its label *names*. You declare them once — `"method"`, `"status"` — and then pass only values: `add(1, "GET", "200")`.

It exists to save work on a hot path. Recording with `add(1, "method" -> "GET", "status" -> "200")` builds a fresh set of label pairs every single call, and on a path that runs thousands of times a second that's steady work for the garbage collector — all to describe names that never change. Fixing the names up front removes that per-call cost.

The trade is safety for speed. Passing the wrong *number* of values throws right at the recording call, but passing them in the wrong *order* records silently — swap two and you get a plausible-looking series that's simply wrong. So reach for these only where you've measured the allocation and it matters. Everywhere else, the plain [instruments](./instruments.md) with named pairs are simpler and harder to get wrong. A [`Meter`](./meter.md) creates them, through `labeledCounter`, `labeledHistogram`, and `labeledGauge`.

```scala
final class LabeledCounter private[telemetry] (...) {
  def add(value: Long, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundCounter
  def collect(): MetricData
}

final class LabeledHistogram private[telemetry] (...) {
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundHistogram
  def collect(): MetricData
}

final class LabeledGauge private[telemetry] (...) {
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundGauge
  def collect(): MetricData
}
```

## Usage

Declare the label names once, then supply values in that same order on each call. The count of `labelValues` must match the declared `labelNames`; a mismatch throws `IllegalArgumentException` at the recording call site (not at construction), so a wrong arity surfaces immediately in tests.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example")

// "GET" → "method", "200" → "status"
val reqs = meter.labeledCounter("http.requests", "method", "status")
reqs.add(1L, "GET", "200")
reqs.add(1L, "POST", "500")

val lat = meter.labeledHistogram("request.latency", "route")
lat.record(15.0, "/orders")

val depth = meter.labeledGauge("queue.depth", "queue")
depth.record(7.0, "orders")
```

For a set of label values recorded over and over, `bind(labelValues*)` builds the `Attributes` once and returns a bound instrument that skips that work on each later call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val reqs  = MeterProvider.builder.build().get("com.example").labeledCounter("rpc.calls", "service", "method")
val bound = reqs.bind("OrderService", "place")
bound.add(1L)
bound.add(1L)
```

Values flow through the wrapped `Counter`, `Histogram`, or `Gauge` and are collected by `MetricReader.collectAllMetrics()` like any other instrument — see [MetricData](./metric-data.md).
