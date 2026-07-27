---
id: meter-provider
title: "MeterProvider"
description: "Entry point for the metrics pillar — creates and caches Meter instances, backs them with a shared MeterRegistry, and exposes a MetricReader."
keywords:
  - "MeterProvider Builder"
  - "Meter Factory"
  - "MetricReader Collection"
  - "Instrumentation Scope"
  - "Metrics Lifecycle"
  - "MeterRegistry"
  - "Telemetry Metrics Setup"
---

`MeterProvider` is the entry point to the metrics pillar in ZIO Blocks Telemetry: you build one, then ask it for [`Meter`](./meter.md) instances. It returns a `Meter` per instrumentation scope — reusing the same instance for a given scope — and exposes a single `MetricReader` that collects everything recorded through those meters.

- **Thread-safe instrument registry** — `Meter` instances are stored in a `ConcurrentHashMap`, so calling `get` from concurrent threads requires no external synchronization.
- **`AutoCloseable` lifecycle** — `close()` delegates to `shutdown()`, enabling use with `scala.util.Using.resource` or a JVM shutdown hook.
- **Single `MetricReader` per provider** — one `MetricReader` aggregates snapshot data from all meters registered in this provider.
- **Meters deduplicated by `InstrumentationScope`** — calling `get` with the same name and version always returns the identical `Meter` instance.

The full public surface of `MeterProvider`, its companion, and `MeterProviderBuilder` is:

```scala
final class MeterProvider private[telemetry] (
  val resource: Resource,
  private val meterRegistry: MeterRegistry
) extends AutoCloseable {

  // Collection
  val reader: MetricReader

  // Meter Access
  def get(name: String, version: String = ""): Meter

  // Lifecycle
  def shutdown(): Unit
  override def close(): Unit
}

object MeterProvider {
  def builder: MeterProviderBuilder
}

final class MeterProviderBuilder private[telemetry] (private var resource: Resource) {
  // Builder Configuration — call before build()
  def setResource(resource: Resource): MeterProviderBuilder

  def build(): MeterProvider
}
```

`MeterProvider` sits in the Metrics group alongside `Meter`, `Counter`, `UpDownCounter`, `Histogram`, and `Gauge`. The global `metric` singleton holds an `AtomicReference[MeterProvider]` and delegates all instrument-creation calls to the currently installed provider:

```
metric (global singleton — AtomicReference[MeterProvider])
  └── MeterProvider             ← this type
        ├── MetricReader        (collects snapshots from all meters)
        └── Meter               (obtained via MeterProvider#get, cached by scope)
              └── Counter / UpDownCounter / Histogram / Gauge
```

## Motivation

In a real application, many different components — your own modules and the third-party libraries you depend on — all want to record metrics, and every one of them needs a `Meter` to do so. Without a central owner, each component would have to know how metrics are collected and exported, and asking for the "same" meter twice could hand back two unrelated instances whose data never lines up.

`MeterProvider` exists to be that single owner: it hands out `Meter` instances keyed by instrumentation scope and deduplicates them, so requesting the same scope always yields the identical meter, and it backs every meter with one shared `MetricReader` that collects across all of them. This lets application startup configure collection in exactly one place, while library and feature code simply calls `get` to obtain a scope-bound `Meter` and stays decoupled from how the data is ultimately read and exported. The payoff is a clear separation of concerns — instrumentation code focuses on recording measurements, and the provider owns the collection pipeline and its lifecycle.

## Usage

The following example shows the complete lifecycle: build a provider with a custom resource, obtain a `Meter`, record a value through a `Counter`, collect the result, then shut down:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(
  MeterProvider.builder
    .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "billing")))
    .build()
) { provider =>
  val meter: Meter = provider.get("billing-service", "1.0.0")
  val counter      = meter.counterBuilder("invoices.processed").build()
  counter.add(1L, Attributes.empty)

  val data: Seq[MetricData] = provider.reader.collectAllMetrics()
  assert(data.nonEmpty)
}
```

`Using.resource` guarantees that `provider.close()` — and therefore `shutdown()` — is called when the block exits, even on exception.

## Construction / Creating Instances

Because `MeterProvider`'s primary constructor is package-private, the builder is the only supported construction path. We start with `MeterProvider.builder`, call zero or more configuration methods on the returned `MeterProviderBuilder`, and finish with `build()`. Every configuration method returns `this`, so calls chain fluently.

### `MeterProvider.builder` — Start a new builder

Returns a `MeterProviderBuilder` pre-populated with `Resource.default` — which includes `service.name = "unknown_service"` and SDK identification attributes.

```scala
object MeterProvider {
  def builder: MeterProviderBuilder
}
```

We always begin provider construction with this call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val builder: MeterProviderBuilder = MeterProvider.builder
```

The builder is mutable internally, but its state is not visible outside. Once `build()` is called, the resulting `MeterProvider` is immutable.

### `MeterProviderBuilder.build` — Finalise configuration

Constructs the `MeterProvider` from the accumulated builder state, creating a fresh `MeterRegistry` and a bound `MetricReader`. The resulting provider is immutable.

```scala
final class MeterProviderBuilder private[telemetry] (private var resource: Resource) {
  def build(): MeterProvider
}
```

We call `MeterProviderBuilder#build` once at the end of the chain, after all configuration methods:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: MeterProvider = MeterProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "my-service")))
  .build()

provider.shutdown()
```

After calling `build()`, the builder instance should be discarded. Re-using or further mutating the builder after `build()` is not supported.

## Core Operations

The `MeterProvider` API groups into four categories: accessing `Meter` instances, collecting metric data, configuring the builder before construction, and managing the provider's lifecycle.

### Meter Access

The `get` method is the bridge between `MeterProvider` and the `Meter` instances that library and application code use to create and record against instruments.

#### `get` — Obtain a cached meter

Returns the `Meter` for the given instrumentation scope `name` and optional `version`. `Meter` instances are cached in a `ConcurrentHashMap`: calling `get` with the same arguments always returns the identical instance. The cache key is an `InstrumentationScope` derived from the name and, when non-empty, the version string.

```scala
final class MeterProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Meter
}
```

We call `MeterProvider#get` once per instrumentation scope at initialization and reuse the result, since the caching guarantee makes repeated calls safe and cheap:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = MeterProvider.builder.build()
val meter    = provider.get("my-lib", "1.0.0")
assert(meter eq provider.get("my-lib", "1.0.0")) // identical instance

provider.shutdown()
```

A new `Meter` is created and registered in the `MeterRegistry` on the first call for a given scope. When `version` is the empty string (the default), no version is recorded in the `InstrumentationScope`.

### Collection

The `MeterProvider#reader` value exposes the `MetricReader` bound to this provider, so callers can query `MetricReader#collectAllMetrics` to retrieve a point-in-time snapshot of every instrument's data.

#### `reader` — Access the metric reader

The `MetricReader` bound to this provider. Calling `reader.collectAllMetrics()` traverses every registered `Meter` and aggregates their instrument data into a flat `Seq[MetricData]`.

```scala
final class MeterProvider private[telemetry] (...) {
  val reader: MetricReader
}
```

We use `provider.reader` to pull a snapshot of all instrument data — for example, to export metrics at a scheduled interval:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = MeterProvider.builder.build()
val meter    = provider.get("svc")
val counter  = meter.counterBuilder("requests").build()
counter.add(5L, Attributes.empty)

val data: Seq[MetricData] = provider.reader.collectAllMetrics()

provider.shutdown()
```

Each element in the returned `Seq` is a `MetricData` variant — `MetricData.SumData`, `MetricData.HistogramData`, or `MetricData.GaugeData` — carrying the data points recorded since the provider was created.

### Builder

The `MeterProviderBuilder#setResource` method is called before `build()` to describe the service producing telemetry. It returns `this` so that calls chain fluently.

#### `setResource` — Identify the producing service

Sets the `Resource` describing the service or component producing telemetry. A `Resource` wraps `Attributes` such as `service.name`, `service.version`, and deployment environment. If `setResource` is not called, the builder uses `Resource.default`, which includes `service.name = "unknown_service"` and SDK identification attributes.

```scala
final class MeterProviderBuilder private[telemetry] (private var resource: Resource) {
  def setResource(resource: Resource): MeterProviderBuilder
}
```

We build a `Resource` using `Resource.create` and `Attributes.of` with the predefined `Attributes.ServiceName` key:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = MeterProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "my-svc")))
  .build()

provider.shutdown()
```

The `resource` field on the resulting `MeterProvider` holds the value set here. Export backends that follow the OpenTelemetry specification attach resource attributes to every metric series they emit.

### Lifecycle

The lifecycle operations release resources held by the provider's `MetricReader`. We call them once when the application is shutting down or when the provider is no longer needed.

#### `shutdown` — Release reader resources

Shuts down the provider by delegating to `reader.shutdown()`. After `shutdown()` returns, the `MetricReader` will no longer collect data.

```scala
final class MeterProvider private[telemetry] (...) {
  def shutdown(): Unit
}
```

We call `MeterProvider#shutdown` once during application teardown:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = MeterProvider.builder.build()
// ... application runs ...
provider.shutdown()
```

#### `close` — `AutoCloseable` delegation

Implements the `AutoCloseable` contract by delegating to `shutdown()`. This allows `MeterProvider` to be managed with `scala.util.Using.resource` or a JVM shutdown hook.

```scala
final class MeterProvider private[telemetry] (...) {
  override def close(): Unit
}
```

Using `scala.util.Using.resource` guarantees that `shutdown()` is called even when an exception propagates out of the block:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(MeterProvider.builder.build()) { provider =>
  val meter = provider.get("scope")
  ()
}
// provider.close() — and therefore shutdown() — is called automatically here
```
