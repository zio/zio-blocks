---
id: up-down-counter
title: "UpDownCounter"
description: "Bidirectional counter instrument in the ZIO Blocks Telemetry metrics pillar — accepts positive and negative deltas to track values that both rise and fall."
keywords:
  - "Bidirectional Counter"
  - "Metrics Instrument"
  - "LongAdder"
  - "BoundUpDownCounter"
  - "MetricData"
  - "Active Connections"
---

`UpDownCounter` is a metric instrument that accumulates `Long` deltas in both directions — positive increments and negative decrements are both accepted. Like `Counter`, it is backed by a `ConcurrentHashMap[Attributes, LongAdder]` for lock-free thread safety. The canonical use case is tracking a quantity that both rises and falls, such as active connections, queue depth, or in-flight requests.

Obtain an `UpDownCounter` from a `Meter` builder or from the global `metric` singleton:

```scala
final class UpDownCounter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
)
```

## Usage

The following example tracks active connection count, incrementing on connect and decrementing on disconnect:

```scala
import zio.blocks.telemetry._

val meter: Meter        = metric.get("com.example")
val active: UpDownCounter =
  meter.upDownCounterBuilder("active.connections").setUnit("1").build()

// Client connected
active.add(1L)

// Client disconnected
active.add(-1L)

// Snapshot the current value per attribute set
val snapshot: MetricData = active.collect()
```

`add` accepts any `Long` value, positive or negative. The `bind` method pre-registers the underlying `LongAdder` for a specific attribute set, eliminating map-lookup overhead on tight loops.

## Key Operations

| Method | Description |
|---|---|
| `add(value: Long, attributes: Attributes): Unit` | Adds `value` (positive or negative) to the running total for the given attribute set. |
| `add(value: Long, attrs: (String, Any)*): Unit` | Builds `Attributes` from vararg tuples then delegates to the typed overload. |
| `bind(attributes: Attributes): BoundUpDownCounter` | Pre-registers the `LongAdder` and returns a handle for lower-overhead increments. |
| `collect(): MetricData` | Returns a `MetricData.SumData` snapshot with one `SumDataPoint` per unique attribute set. |
| `BoundUpDownCounter#add(value: Long): Unit` | Adds `value` to the pre-bound adder. Both positive and negative values are accepted. |

:::note
`UpDownCounter` differs from `Counter` only in that it accepts negative values. Choose `Counter` when the quantity can only increase (e.g., total requests processed); choose `UpDownCounter` when it can decrease (e.g., items currently in a queue).
:::
