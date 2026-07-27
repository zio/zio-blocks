---
id: tracer-provider
title: "TracerProvider"
description: "Factory for Tracer instances — configures Resource, Sampler, SpanProcessors, and ContextStorage for distributed tracing."
keywords:
  - "TracerProvider Builder"
  - "Tracer Factory"
  - "SpanProcessor Configuration"
  - "Distributed Tracing Setup"
  - "ContextStorage Propagation"
  - "Sampler Configuration"
  - "Telemetry Provider Lifecycle"
---

`TracerProvider` is a factory for `Tracer` instances that share a common `Resource`, `Sampler`, ordered list of `SpanProcessor`s, and `ContextStorage` for active-span propagation. It is the production configuration entry point for the tracing pillar of the ZIO Blocks Telemetry module — positioned between the global `trace` singleton (which holds a reference to the installed provider) and the per-scope `Tracer` instances that library code receives via `TracerProvider#get`.

- **Immutable after construction** — all settings are fixed when `build()` is called; no field can be mutated at runtime.
- **Builder pattern** — the primary constructor is package-private; `TracerProvider.builder` is the only public entry point.
- **`AutoCloseable`** — `close()` delegates to `shutdown()`, so a provider can be managed with `scala.util.Using.resource` or a JVM shutdown hook.
- **Thread-safe on JVM** — the default `ContextStorage` uses `ScopedValue`-backed scoped bindings, which are inherently per-call-stack.

The public surface of `TracerProvider` and its companion builder is:

```scala
final class TracerProvider private[telemetry] (
  resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  private[telemetry] val contextStorage: ContextStorage[Option[SpanContext]]
) extends AutoCloseable {

  // Tracer Access
  def get(name: String, version: String = ""): Tracer

  // Lifecycle
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit
}

object TracerProvider {
  def builder: TracerProviderBuilder
}

final class TracerProviderBuilder private[telemetry] (...) {
  // Builder Configuration — call before build()
  def setResource(resource: Resource): TracerProviderBuilder
  def setSampler(sampler: Sampler): TracerProviderBuilder
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder

  def build(): TracerProvider
}
```

`TracerProvider` sits in the Tracing group alongside `Tracer` and `Span`. The relationship across the full tracing pillar looks like this:

```
trace (global singleton — AtomicReference[TracerProvider])
  └── TracerProvider   ← this type
        └── Tracer     (obtained via TracerProvider#get)
              └── Span (active unit of work)
                    └── SpanData (immutable export snapshot → SpanProcessor)
```

## Usage

The following example shows the complete lifecycle: configure a provider with a custom resource and processor, obtain a `Tracer`, record a span, then shut down:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(
  TracerProvider.builder
    .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
    .setSampler(ParentBasedSampler(AlwaysOnSampler))
    .addSpanProcessor(SpanProcessor.noop)
    .build()
) { provider =>
  val tracer: Tracer = provider.get("payments-service", "1.0.0")

  val result: Int = tracer.span("process-payment") { span =>
    span.setAttribute(AttributeKey.long("amount_cents"), 4200L)
    42
  }

  result // 42
}
```

`Using.resource` calls `provider.close()` — which delegates to `shutdown()` — automatically when the block exits, even on exception.

## Construction / Creating Instances

Because `TracerProvider`'s primary constructor is package-private, the builder is the only supported construction path. We start with `TracerProvider.builder`, call zero or more configuration methods on the returned `TracerProviderBuilder`, and finish with `build()`. Every configuration method returns `this`, so calls chain fluently.

### `TracerProvider.builder` — Start a new builder

Returns a `TracerProviderBuilder` pre-populated with the following defaults: `Resource.default` (SDK attributes plus `service.name = "unknown_service"`), `AlwaysOnSampler`, an empty processor list, and `ContextStorage.create[Option[SpanContext]](None)` (a `ScopedValue`-backed store on JVM).

```scala
object TracerProvider {
  def builder: TracerProviderBuilder
}
```

We always begin provider construction with this call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val builder: TracerProviderBuilder = TracerProvider.builder
```

The builder is mutable internally but its mutations are not visible outside the builder: once `build()` is called the resulting `TracerProvider` is immutable.

### `setResource` — Identify the producing service

Sets the `Resource` that is attached to every span this provider emits. A `Resource` wraps `Attributes` that describe the service, container, or host producing the telemetry. If not called, the builder uses `Resource.default`, which includes SDK identification attributes and `service.name = "unknown_service"`.

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): TracerProviderBuilder
}
```

We build a `Resource` from a single `Attributes.of` call, using the predefined `Attributes.ServiceName` key:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .build()
```

Passing descriptive resource attributes is important for backends such as Jaeger or Zipkin, which use them to group spans under the correct service name.

### `setSampler` — Control span recording

Sets the `Sampler` consulted when a `Tracer` starts a new span. The sampler receives the parent context, trace ID, span name, kind, and initial attributes, and returns a `SamplingResult` indicating whether to drop, record-only, or record-and-sample the span. If not called, the builder uses `AlwaysOnSampler`.

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setSampler(sampler: Sampler): TracerProviderBuilder
}
```

`ParentBasedSampler` is the most common production choice: it follows the parent span's sampling decision for remote spans and delegates to a root sampler for new traces:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()
```

Other built-in samplers are `AlwaysOnSampler` (record everything) and `AlwaysOffSampler` (drop everything, useful in tests).

### `addSpanProcessor` — Wire exporters and observers

Appends a `SpanProcessor` to the provider's ordered list. Each processor receives `onStart` and `onEnd` callbacks as spans are created and completed. Processors are invoked in insertion order; call `addSpanProcessor` multiple times to attach several processors.

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
}
```

The following example wires two processors — one for in-memory inspection during tests and one that exports spans to a backend:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val inMemory = SpanProcessor.noop

val provider = TracerProvider.builder
  .addSpanProcessor(inMemory)
  .addSpanProcessor(SpanProcessor.noop) // replace with a real exporter in production
  .build()
```

:::caution
Processors are called in insertion order on the thread that ends the span. A slow or blocking processor (for example, a synchronous HTTP exporter) will slow every span's end. Prefer batch processors that hand off to a background thread.
:::

### `setContextStorage` — Override active-span propagation

Overrides the `ContextStorage[Option[SpanContext]]` used to propagate the currently active span through the call stack. When not called, the builder resolves to `ContextStorage.create[Option[SpanContext]](None)`, which on JVM is backed by a `ScopedValue`.

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder
}
```

Pass the same `ContextStorage` instance to both `TracerProvider` and `LoggerProvider` to enable automatic trace-log correlation — log records will carry the active `SpanContext` without any additional instrumentation:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val sharedStorage: ContextStorage[Option[SpanContext]] =
  ContextStorage.create[Option[SpanContext]](None)

val tracerProvider = TracerProvider.builder
  .setContextStorage(sharedStorage)
  .build()

val loggerProvider = LoggerProvider.builder
  .setContextStorage(sharedStorage)
  .build()
```

:::note
Override `ContextStorage` only when integrating with a custom async runtime whose threading model makes `ScopedValue` propagation unreliable, or when sharing context explicitly with `LoggerProvider` as shown above.
:::

### `TracerProviderBuilder.build` — Finalise configuration

Constructs the `TracerProvider` from the accumulated builder state. If `setContextStorage` was not called, `build()` supplies `ContextStorage.create[Option[SpanContext]](None)` automatically. The resulting provider is immutable.

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def build(): TracerProvider
}
```

We call `build()` once at the end of the chain after all configuration methods:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: TracerProvider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "my-service")))
  .setSampler(AlwaysOnSampler)
  .addSpanProcessor(SpanProcessor.noop)
  .build()

provider.shutdown()
```

After calling `build()`, the builder instance should be discarded. Re-using or further mutating the builder after `build()` is not supported.

## Core Operations

Once a `TracerProvider` is built, its API groups into two categories: obtaining `Tracer` instances and managing the provider's lifecycle.

### Tracer Access

The `get` method is the bridge between `TracerProvider` configuration and the `Tracer` instances that library code uses to create spans.

#### `get` — Obtain a named tracer

Returns a `Tracer` for the given instrumentation scope `name` and optional `version`. The returned `Tracer` carries the provider's shared `Resource`, `Sampler`, processors, and `ContextStorage`, scoped under an `InstrumentationScope` identified by the provided name and version. A new `Tracer` instance is constructed on every call.

```scala
final class TracerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Tracer
}
```

We call `TracerProvider#get` once at library or module initialization and reuse the resulting `Tracer` throughout, since each call allocates a new instance:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()

// Acquire once — both name and version are recorded in every span's InstrumentationScope
val tracer: Tracer = provider.get("payments-service", "1.0.0")

// Use the Tracer to create spans
val result: Int = tracer.span("charge") { span =>
  span.setAttribute(AttributeKey.long("amount_cents"), 4200L)
  42
}
```

When `version` is an empty string (the default), the `InstrumentationScope` records no version. Supply a semantic version string whenever the instrumentation scope is a versioned library component so that tracing backends can correlate spans with library releases.

:::caution
`get` constructs a new `Tracer` on every invocation. Cache the result at module initialization rather than calling `get` on the hot path.
:::

### Lifecycle

The lifecycle operations release resources held by the registered `SpanProcessor`s. We call them once when the application is shutting down or when the provider is no longer needed.

#### `shutdown` — Release processor resources

Calls `shutdown()` on every registered `SpanProcessor` in insertion order, releasing any background threads, buffers, or network connections they hold. After `shutdown()` returns, the provider is still structurally valid — spans can still be started — but processors will no longer emit data.

```scala
final class TracerProvider private[telemetry] (...) {
  def shutdown(): Unit
}
```

We call `TracerProvider#shutdown` once during application shutdown:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.addSpanProcessor(SpanProcessor.noop).build()
// ... application runs ...
provider.shutdown()
```

#### `forceFlush` — Export buffered spans immediately

Calls `forceFlush()` on every registered `SpanProcessor`, asking each to export any spans it is currently buffering rather than waiting for the next scheduled flush. This is useful before a planned shutdown to avoid losing in-flight telemetry.

```scala
final class TracerProvider private[telemetry] (...) {
  def forceFlush(): Unit
}
```

We call `TracerProvider#forceFlush` before `shutdown()` to drain buffered data:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.addSpanProcessor(SpanProcessor.noop).build()
// ... application preparing to shut down ...
provider.forceFlush()
provider.shutdown()
```

#### `close` — `AutoCloseable` delegation

Implements the `AutoCloseable` contract by delegating to `shutdown()`. This allows `TracerProvider` to be used directly with `scala.util.Using.resource` or Java's try-with-resources construct.

```scala
final class TracerProvider private[telemetry] (...) {
  override def close(): Unit
}
```

Using `scala.util.Using.resource` guarantees shutdown even when an exception propagates out of the block:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(TracerProvider.builder.addSpanProcessor(SpanProcessor.noop).build()) { provider =>
  val tracer = provider.get("scope")
  tracer.span("op") { _ => () }
}
// provider.close() — and therefore shutdown() — is called automatically here
```

## Comparisons

### `TracerProvider` vs. OpenTelemetry Java `SdkTracerProvider`

`SdkTracerProvider` from the `io.opentelemetry:opentelemetry-sdk` artifact follows the same conceptual pattern — builder, resource, sampler, span processors — but differs in dependency weight and call-site ergonomics:

| Aspect                     | ZIO Blocks `TracerProvider`                          | OTel Java `SdkTracerProvider`                          |
|----------------------------|------------------------------------------------------|--------------------------------------------------------|
| Dependencies               | Zero — pure Scala, no OTel SDK jars                  | Requires `opentelemetry-sdk` and gRPC/HTTP exporters   |
| Default sampler            | `AlwaysOnSampler`                                    | `ParentBasedSampler(ALWAYS_ON)` by default             |
| Span lifecycle             | Higher-order `tracer.span("op") { ... }`             | Manual `span.end()` in a `finally` block               |
| `AutoCloseable`            | Yes — `close()` delegates to `shutdown()`            | Yes — same pattern                                     |
| Context propagation        | `ScopedValue` on JVM; pluggable via `setContextStorage` | Thread-local `Context` by default; pluggable          |
| Cross-platform             | Compiles on JVM and Scala.js                         | JVM only                                               |

The higher-order `span` API on `Tracer` eliminates the need for a `try`/`finally` block at every call site, which is the most common source of missing `span.end()` calls in OTel Java code.

### `TracerProvider` vs. `MeterProvider` and `LoggerProvider`

`MeterProvider` and `LoggerProvider` are the sibling provider types in the same `zio.blocks.telemetry` package, serving the metrics and logging pillars respectively. All three follow the identical builder pattern and `AutoCloseable` lifecycle:

| Aspect                     | `TracerProvider`                                     | `MeterProvider`                          | `LoggerProvider`                         |
|----------------------------|------------------------------------------------------|------------------------------------------|------------------------------------------|
| Produces                   | `Tracer` → `Span`                                    | `Meter` → instruments (Counter, etc.)    | `Logger` → log records                   |
| Builder method             | `TracerProvider.builder`                             | `MeterProvider.builder`                  | `LoggerProvider.builder`                 |
| Sampler support            | Yes — `setSampler`                                   | No                                       | No                                       |
| `ContextStorage` support   | Yes — `setContextStorage`                            | No                                       | Yes — `setContextStorage`                |
| `AutoCloseable`            | Yes                                                  | Yes                                      | Yes                                      |

Passing the same `ContextStorage` instance to both `TracerProvider` and `LoggerProvider` enables trace-log correlation automatically: log records emitted inside a `tracer.span` block carry the active `SpanContext` without any additional instrumentation. `MeterProvider` does not participate in span context propagation because metrics are aggregated rather than correlated to individual traces.