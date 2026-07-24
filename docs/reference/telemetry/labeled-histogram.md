---
id: labeled-histogram
title: "LabeledHistogram"
description: "Histogram with pre-declared label names in the ZIO Blocks Telemetry metrics pillar — label values are passed positionally and validated for arity at runtime."
keywords:
  - "LabeledHistogram"
  - "Pre-declared Labels"
  - "Histogram"
  - "Metrics Instrument"
  - "Positional Labels"
  - "BoundHistogram"
---

`LabeledHistogram` wraps a `Histogram` with a fixed ordered list of label names. Callers pass label values positionally at each `record` call, and `LabeledHistogram` builds the `Attributes` internally. Arity is validated at runtime — a mismatch throws `IllegalArgumentException` with a descriptive message.

Obtain a `LabeledHistogram` from `Meter#labeledHistogram`:

```scala
final class LabeledHistogram private[telemetry] (
  private val histogram: Histogram,
  val labelNames: Array[String]
)
```

## Usage

The following example declares a per-endpoint request latency histogram, records observations, and binds a handle for the hot path:

```scala
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")

val latency = meter.labeledHistogram("request.latency", "endpoint")

// Record with positional label value
latency.record(42.5, "/api/orders")
latency.record(18.3, "/api/search")

// Bind once for a frequently-used label combination
val bound: BoundHistogram = latency.bind("/api/orders")
bound.record(37.1)

val snapshot: MetricData = latency.collect()
```

Passing a wrong number of label values — for example `latency.record(42.5, "/api/orders", "extra")` when only one label is expected — throws `IllegalArgumentException: Expected 1 label values, got 2`.

## Key Operations

| Method | Description |
|---|---|
| `record(value: Double, labelValues: Any*): Unit` | Builds `Attributes` from positional label values and records `value` into the underlying `Histogram`. Validates arity. |
| `bind(labelValues: Any*): BoundHistogram` | Pre-registers the bucket state for the given label combination and returns a low-overhead handle. Validates arity. |
| `collect(): MetricData` | Delegates to the underlying `Histogram#collect` — returns a `MetricData.HistogramData` snapshot. |

:::note
Label values can be `String`, `Long`, `Int`, `Double`, or `Boolean`. `Int` values are widened to `Long`. Any other type is converted via `toString`.
:::
