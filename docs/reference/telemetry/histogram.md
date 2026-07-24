---
id: histogram
title: "Histogram"
description: "Distribution-recording instrument in the ZIO Blocks Telemetry metrics pillar — buckets Double measurements for percentile and latency analysis."
keywords:
  - "Histogram"
  - "Metrics Instrument"
  - "Latency Distribution"
  - "Bucket Boundaries"
  - "BoundHistogram"
  - "ReentrantLock"
---

`Histogram` records `Double` measurements into configurable fixed-width buckets, enabling percentile estimation, latency distribution, and SLA analysis. It is backed by a `ConcurrentHashMap[Attributes, Histogram.State]` where each `State` holds per-bucket counts, a running sum, minimum, and maximum, all protected by a `ReentrantLock` per attribute set. The default bucket boundaries are `[0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000]`.

Obtain a `Histogram` from a `Meter` builder or from the global `metric` singleton:

```scala
final class Histogram private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  val boundaries: Array[Double]
)
```

## Usage

The following example records request latency measurements and collects the aggregated distribution:

```scala
import zio.blocks.telemetry._

val meter: Meter     = metric.get("com.example")
val latency: Histogram =
  meter.histogramBuilder("request.latency").setUnit("ms").build()

// Record a single observation with attribute context
latency.record(42.5, "endpoint" -> "/api/orders")

// Bind once for a specific attribute set used repeatedly
val bound: BoundHistogram =
  latency.bind(Attributes.of(AttributeKey.string("endpoint"), "/api/search"))
bound.record(18.3)

// Collect the distribution snapshot
val snapshot: MetricData = latency.collect()
```

Each call to `record` locks the per-attribute-set state for the duration of the bucket update, which typically completes in nanoseconds. The `bind` variant skips the map lookup on every hot-path call.

## Key Operations

| Method | Description |
|---|---|
| `record(value: Double, attributes: Attributes): Unit` | Records `value` into the appropriate bucket for the given attribute set. |
| `record(value: Double, attrs: (String, Any)*): Unit` | Builds `Attributes` from vararg tuples then delegates to the typed overload. |
| `bind(attributes: Attributes): BoundHistogram` | Pre-registers the bucket state and returns a handle for lower-overhead recording. |
| `collect(): MetricData` | Returns a `MetricData.HistogramData` snapshot with one `HistogramDataPoint` per unique attribute set, containing bucket counts, sum, min, and max. |
| `BoundHistogram#record(value: Double): Unit` | Records `value` into the pre-bound bucket state, bypassing the map lookup. |

:::note
Custom bucket boundaries can be supplied at build time — there is no builder setter for them via `HistogramBuilder` in the current API; call `Histogram(name, description, unit, boundaries)` directly when non-default boundaries are required.
:::
