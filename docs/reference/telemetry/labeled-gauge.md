---
id: labeled-gauge
title: "LabeledGauge"
description: "Gauge with pre-declared label names in the ZIO Blocks Telemetry metrics pillar — label values are passed positionally and validated for arity at runtime."
keywords:
  - "LabeledGauge"
  - "Pre-declared Labels"
  - "Gauge"
  - "Metrics Instrument"
  - "Positional Labels"
  - "BoundGauge"
---

`LabeledGauge` wraps a `Gauge` with a fixed ordered list of label names. Callers pass label values positionally at each `record` call, and `LabeledGauge` builds the `Attributes` internally. Arity is validated at runtime — a mismatch throws `IllegalArgumentException` with a descriptive message. Each unique combination of label values tracks a distinct last-value series.

Obtain a `LabeledGauge` from `Meter#labeledGauge`:

```scala
final class LabeledGauge private[telemetry] (
  private val gauge: Gauge,
  val labelNames: Array[String]
)
```

## Usage

The following example declares a per-region temperature gauge, records readings, and binds a handle for the hot path:

```scala
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")

val temp = meter.labeledGauge("temperature", "region")

// Record with positional label value
temp.record(72.1, "us-east-1")
temp.record(68.4, "eu-west-1")

// Bind once for a label combination used on a hot poll loop
val bound: BoundGauge = temp.bind("us-east-1")
bound.record(71.8)

val snapshot: MetricData = temp.collect()
```

Passing a wrong number of label values — for example `temp.record(72.1)` when one label is expected — throws `IllegalArgumentException: Expected 1 label values, got 0`.

## Key Operations

| Method | Description |
|---|---|
| `record(value: Double, labelValues: Any*): Unit` | Builds `Attributes` from positional label values and records `value` into the underlying `Gauge` (last write wins). Validates arity. |
| `bind(labelValues: Any*): BoundGauge` | Pre-registers the `AtomicLong` slot for the given label combination and returns a low-overhead handle. Validates arity. |
| `collect(): MetricData` | Delegates to the underlying `Gauge#collect` — returns a `MetricData.GaugeData` snapshot. |

:::note
Label values can be `String`, `Long`, `Int`, `Double`, or `Boolean`. `Int` values are widened to `Long`. Any other type is converted via `toString`.
:::
