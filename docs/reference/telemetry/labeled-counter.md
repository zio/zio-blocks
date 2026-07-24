---
id: labeled-counter
title: "LabeledCounter"
description: "Counter with pre-declared label names in the ZIO Blocks Telemetry metrics pillar — label values are passed positionally and validated for arity at runtime."
keywords:
  - "LabeledCounter"
  - "Pre-declared Labels"
  - "Counter"
  - "Metrics Instrument"
  - "Positional Labels"
  - "BoundCounter"
---

`LabeledCounter` wraps a `Counter` with a fixed ordered list of label names. Instead of constructing `Attributes` objects at each call site, callers pass label values positionally and `LabeledCounter` builds the `Attributes` internally. Arity is validated at runtime — passing the wrong number of label values throws an `IllegalArgumentException` with a clear message.

Obtain a `LabeledCounter` from `Meter#labeledCounter`:

```scala
final class LabeledCounter private[telemetry] (
  private val counter: Counter,
  val labelNames: Array[String]
)
```

## Usage

The following example declares an HTTP request counter with `method` and `status` labels, increments it for two request outcomes, and binds a handle for the hot path:

```scala
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example")

val reqs = meter.labeledCounter("http.requests", "method", "status")

// Positional label values — order matches labelNames
reqs.add(1L, "GET", "200")
reqs.add(1L, "POST", "201")
reqs.add(1L, "GET", "500")

// Bind once for a frequently-used label combination
val bound: BoundCounter = reqs.bind("GET", "200")
bound.add(1L)

val snapshot: MetricData = reqs.collect()
```

Passing a wrong number of label values — for example `reqs.add(1L, "GET")` when two labels are expected — throws `IllegalArgumentException: Expected 2 label values, got 1`.

## Key Operations

| Method | Description |
|---|---|
| `add(value: Long, labelValues: Any*): Unit` | Builds `Attributes` from positional label values and increments the underlying `Counter`. Validates arity. Negative values are silently ignored. |
| `bind(labelValues: Any*): BoundCounter` | Pre-registers the `LongAdder` for the given label combination and returns a low-overhead handle. Validates arity. |
| `collect(): MetricData` | Delegates to the underlying `Counter#collect` — returns a `MetricData.SumData` snapshot. |

:::note
Label values can be `String`, `Long`, `Int`, `Double`, or `Boolean`. `Int` values are widened to `Long`. Any other type is converted via `toString`.
:::
