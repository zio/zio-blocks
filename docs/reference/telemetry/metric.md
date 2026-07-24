---
id: metric
title: "metric"
description: "Reference for the `metric` global metrics singleton — counter, histogram, gauge creation, and MeterProvider configuration."
keywords:
  - "Global Metrics Entry Point"
  - "Counter Creation"
  - "Histogram Creation"
  - "Gauge Creation"
  - "MeterProvider Configuration"
  - "UpDownCounter"
  - "Zero-Setup Metrics"
---

`metric` is the global metrics entry point for the ZIO Blocks Telemetry module — a singleton you import and call directly to create instruments (`Counter`, `UpDownCounter`, `Histogram`, `Gauge`), record measurements, and export them to a monitoring backend. 

Its public API groups into four categories:

```scala
object metric {
  // Instrument creation — delegates to the default Meter
  def counter(name: String): Counter
  def upDownCounter(name: String): UpDownCounter
  def histogram(name: String): Histogram
  def gauge(name: String): Gauge

  // Meter retrieval — named instrumentation scopes
  def get(name: String): Meter

  // Configuration — mutate the global MeterProvider
  def install(provider: MeterProvider): Unit
  def removeAll(): Unit

  // Inspection — access metric readings
  def reader: MetricReader
}
```

`metric` sits at the top of the Telemetry module's metrics pillar. It wraps a `MeterProvider`, which creates `Meter` instances, which in turn build `Counter`, `UpDownCounter`, `Histogram`, and `Gauge` instruments. The relationship looks like this:

```
metric
  └── MeterProvider   (installed via metric.install)
        └── Meter     (obtained via metric.get; used internally by shortcut methods)
              ├── Counter          (monotonically increasing cumulative counter)
              ├── UpDownCounter    (bidirectional counter for values that go up and down)
              ├── Histogram        (latency and size distribution recorder)
              └── Gauge            (point-in-time gauge for instantaneous measurements)
```

The `MeterProvider` reference is held in a `java.util.concurrent.atomic.AtomicReference`, making it safe to replace exactly once at startup. App code calls `metric` directly; library code should accept a `Meter` parameter obtained via `metric.get` so it remains independent of the global singleton.

- **Zero-setup** — a default `MeterProvider` is wired before the first call; no configuration is required.
- **Thread-safe** — the `MeterProvider` reference is stored in an `AtomicReference`.
- **Global singleton** — `metric.install` and `metric.removeAll` mutate shared state; call each at most once at startup.
- **Delegates lifecycle** — `metric` never manages instrument state directly; it delegates to `MeterProvider` and `Meter`.

## Usage

The following example shows the core workflow in one place: create instruments via `metric`, record values, then inspect accumulated data via `metric.reader`:

```scala
import zio.blocks.telemetry._

// Create instruments from the global singleton
val requests = metric.counter("http.requests")
val latency  = metric.histogram("http.request.latency")
val active   = metric.upDownCounter("http.active.connections")
val memory   = metric.gauge("process.memory.bytes")

// Record values
requests.add(1)
latency.record(42.5)
active.add(1)
memory.record(256_000_000.0)
active.add(-1)

// Read back all accumulated data
val snapshot: Seq[MetricData] = metric.reader.read()
```

### Named Instrumentation Scopes

Library code should avoid using the global `metric` object directly. Accept a `Meter` instead, obtained from a `MeterProvider` passed through dependency injection:

```scala
import zio.blocks.telemetry._

class HttpServer(meter: Meter) {
  private val requests = meter.counterBuilder("http.requests").build()
  private val errors   = meter.counterBuilder("http.errors").build()

  def handleRequest(): Unit = {
    requests.add(1)
    // ... handle the request
  }
}

// In application startup code, wire together:
val server = new HttpServer(metric.get("com.example.http"))
```

This pattern keeps library code testable — inject a `Meter` from a test `MeterProvider` to capture metric readings without touching the global singleton.

### Production Wiring

Replace the default provider at startup to export metrics to a real backend:

```scala
import zio.blocks.telemetry._

// Build a provider with a custom exporter (e.g. Prometheus, OTLP)
val provider = MeterProvider.builder
  .withReader(MyExporterReader)
  .build()

metric.install(provider)

// All subsequent metric.counter / metric.get calls route through the new provider
val requests = metric.counter("http.requests")
```

### Labeled Metrics

For metrics that vary by dimension (HTTP method, status code, endpoint), use the `Meter`-level builders to get labeled instruments:

```scala
import zio.blocks.telemetry._

val meter    = metric.get("com.example.http")
val requests = meter.labeledCounter("http.requests", "method", "status")
val latency  = meter.labeledHistogram("http.request.latency", "endpoint")

// Record values with positional label values
requests.add(1, "GET", "200")
requests.add(1, "POST", "500")
latency.record(38.2, "/api/orders")
```

## API Reference

### Instrument Creation

All four shortcut methods create an instrument on the `"default"` `Meter`. For labeled or named-scope instruments, use `metric.get(name)` to obtain a `Meter` first.

| Method | Return Type | Description |
|---|---|---|
| `counter(name)` | `Counter` | Monotonically increasing cumulative counter |
| `upDownCounter(name)` | `UpDownCounter` | Bidirectional counter for values that go up and down |
| `histogram(name)` | `Histogram` | Latency and size distribution recorder |
| `gauge(name)` | `Gauge` | Point-in-time instantaneous measurement |

### Meter Retrieval

```scala
def get(name: String): Meter
```

Returns a `Meter` for the given instrumentation scope name. The returned `Meter` is reused across calls with the same name — the `MeterProvider` is responsible for deduplicating scopes.

### Configuration

```scala
def install(provider: MeterProvider): Unit
def removeAll(): Unit
```

`install` replaces the global provider atomically. Call it exactly once at startup, before any metric instruments are created. `removeAll` restores the default provider — useful in tests to reset state between test cases.

### Inspection

```scala
def reader: MetricReader
```

Returns the `MetricReader` for the current provider. Use it in tests to assert on recorded values without configuring an exporter:

```scala
import zio.blocks.telemetry._

metric.removeAll() // reset to default in-memory provider

val hits = metric.counter("cache.hits")
hits.add(3)
hits.add(7)

val data = metric.reader.read()
// data contains a MetricData entry for "cache.hits" with cumulative sum 10
```

## See Also

- [Telemetry Reference](./index.md) — Module overview and all three signal pillars (tracing, logging, metrics)
- [MeterProvider](./meter-provider.md) — Factory for `Meter` instances; configure once at startup to wire exporters
- [Meter](./meter.md) — Per-library scope-bound metric factory; documents the Counter, UpDownCounter, Histogram, and Gauge instruments. Prefer over `metric` in library code
- [MetricData](./metric-data.md) — Snapshot of a metric instrument's collected data
- [Telemetry Guide](../../guides/telemetry-guide.md) — Architecture and real-world patterns for the telemetry module
