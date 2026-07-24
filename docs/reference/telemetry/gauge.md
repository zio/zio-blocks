---
id: gauge
title: "Gauge"
description: "Last-value instrument in the ZIO Blocks Telemetry metrics pillar — records the most recent Double measurement per attribute set using atomic raw-bits storage."
keywords:
  - "Gauge"
  - "Metrics Instrument"
  - "Last-Write-Wins"
  - "AtomicLong"
  - "BoundGauge"
  - "Double Raw Bits"
---

`Gauge` records the most recent `Double` value observed for each attribute set. It is backed by a `ConcurrentHashMap[Attributes, AtomicLong]` where each `Double` is stored as raw long bits via `java.lang.Double.doubleToRawLongBits`. The last write wins — a new `record` call unconditionally replaces the previous value for that attribute set. Use a `Gauge` for quantities that represent a current state rather than an accumulation: CPU usage, memory pressure, temperature, or queue depth sampled at export time.

Obtain a `Gauge` from a `Meter` builder or from the global `metric` singleton:

```scala
final class Gauge private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
)
```

## Usage

The following example records the current queue depth for two named queues and collects the latest values:

```scala
import zio.blocks.telemetry._

val meter: Meter    = metric.get("com.example")
val queueDepth: Gauge =
  meter.gaugeBuilder("queue.depth").setUnit("1").build()

// Record current depth for the "orders" queue
queueDepth.record(42.0, "name" -> "orders")

// Record current depth for the "notifications" queue
queueDepth.record(7.0, "name" -> "notifications")

// Bind once for a specific queue to skip the map lookup on each poll
val bound: BoundGauge =
  queueDepth.bind(Attributes.of(AttributeKey.string("name"), "orders"))
bound.record(38.0)

// Collect the latest value per attribute set
val snapshot: MetricData = queueDepth.collect()
```

`record` is an unconditional atomic write. Concurrent calls for the same attribute set may interleave, but each write is individually atomic — no partial states are visible.

## Key Operations

| Method | Description |
|---|---|
| `record(value: Double, attributes: Attributes): Unit` | Stores `value` (as raw long bits) as the current value for the given attribute set. Last write wins. |
| `record(value: Double, attrs: (String, Any)*): Unit` | Builds `Attributes` from vararg tuples then delegates to the typed overload. |
| `bind(attributes: Attributes): BoundGauge` | Pre-registers the `AtomicLong` slot and returns a handle for lower-overhead recording. |
| `collect(): MetricData` | Returns a `MetricData.GaugeData` snapshot with one `GaugeDataPoint` per unique attribute set, containing the most recently recorded value. |
| `BoundGauge#record(value: Double): Unit` | Atomically stores `value` (as raw long bits) into the pre-bound slot. |

:::note
Because `Gauge` stores double bits as raw longs, `NaN` values are preserved exactly without any special-casing. All finite and infinite `Double` values are also stored without loss.
:::
