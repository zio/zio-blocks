---
id: counter
title: "Counter"
description: "Monotonically increasing counter instrument in the ZIO Blocks Telemetry metrics pillar — backed by ConcurrentHashMap and LongAdder for lock-free thread safety."
keywords:
  - "Monotonic Counter"
  - "Metrics Instrument"
  - "LongAdder"
  - "BoundCounter"
  - "MetricData"
  - "Meter Builder"
---

`Counter` is a monotonically increasing metric instrument that records non-negative `Long` deltas. It is backed by a `ConcurrentHashMap[Attributes, LongAdder]`, which gives it lock-free, thread-safe increments without boxing. Negative values are silently ignored — the counter only moves forward. Each unique `Attributes` combination gets its own `LongAdder` entry, so the same `Counter` instance tracks multiple independent time series simultaneously.

Obtain a `Counter` from a `Meter` builder or from the global `metric` singleton:

```scala
final class Counter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
)
```

## Usage

The following example creates a request counter, increments it with ad-hoc tuple attributes, binds a `BoundCounter` for a high-frequency path, and collects a snapshot:

```scala
import zio.blocks.telemetry._

val meter: Meter   = metric.get("com.example")
val c: Counter     = meter.counterBuilder("http.requests").setUnit("1").build()

// Increment with inline attribute tuples
c.add(1L, "method" -> "GET", "status" -> "200")

// Bind once for a hot path — avoids attribute construction per call
val bc: BoundCounter = c.bind(Attributes.of(AttributeKey.string("method"), "POST"))
bc.add(5L)

// Collect an aggregated snapshot
val snapshot: MetricData = c.collect()
```

`c.add(1L, "method" -> "GET")` is equivalent to constructing an `Attributes` value and calling `c.add(1L, attributes)`. The `bind` variant pre-registers the `LongAdder` and skips the `ConcurrentHashMap` lookup on every hot-path call.

## Key Operations

| Method | Description |
|---|---|
| `add(value: Long, attributes: Attributes): Unit` | Increments by `value` for the given attribute set. Values `< 0` are silently ignored. |
| `add(value: Long, attrs: (String, Any)*): Unit` | Builds `Attributes` from vararg tuples then delegates to the typed overload. |
| `bind(attributes: Attributes): BoundCounter` | Pre-registers the `LongAdder` and returns a handle that skips the map lookup on each increment. |
| `collect(): MetricData` | Returns a `MetricData.SumData` snapshot with one `SumDataPoint` per unique attribute set. |
| `BoundCounter#add(value: Long): Unit` | Increments the pre-bound adder. Values `< 0` are silently ignored. |

:::note
`Counter` is obtained exclusively through the builder returned by `Meter#counterBuilder`. Its primary constructor is package-private. Use `metric.counter(name)` for a quick one-liner against the global default meter.
:::
