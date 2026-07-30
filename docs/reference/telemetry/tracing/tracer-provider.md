---
id: tracer-provider
title: "TracerProvider"
description: "Root factory for distributed tracing: produces Tracer instances sharing a Resource, Sampler, SpanProcessors, and ContextStorage."
keywords:
  - "TracerProvider tracing factory"
  - "TracerProviderBuilder configuration"
  - "SpanProcessor registration"
  - "trace-log correlation ContextStorage"
  - "Sampler head sampling"
  - "Tracer instrumentation scope"
  - "distributed tracing ZIO Blocks"
---

`TracerProvider` is the root factory for distributed tracing in the telemetry module. It holds a shared `Resource`, `Sampler`, ordered sequence of `SpanProcessor`s, and `ContextStorage[Option[SpanContext]]`, and produces `Tracer` instances that all inherit that configuration. Its primary constructor is package-private; use `TracerProvider.builder` and the returned `TracerProviderBuilder` to configure and create a provider.

- **Immutable after construction** — Once `build()` is called, the provider's resource, sampler, processor list, and context storage are fixed for its lifetime.
- **Builder pattern** — `TracerProvider.builder` returns a `TracerProviderBuilder`; call the configuration methods in any order and finish with `build()`.
- **`AutoCloseable`** — `close()` delegates to `shutdown()`, so the provider can be used in `scala.util.Using.resource` blocks or Java `try`-with-resources.
- **Platform-neutral** — Shared sources compile on both JVM and Scala.js. The `ContextStorage` implementation is platform-specific: JVM uses JDK 25 `ScopedValue`; Scala.js substitutes an equivalent alternative.

The two cooperating classes share the following public surface:

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
  // Builder — Configuration
  def setResource(resource: Resource): TracerProviderBuilder
  def setSampler(sampler: Sampler): TracerProviderBuilder
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder

  def build(): TracerProvider
}
```

## Usage

The following block shows the complete lifecycle of a `TracerProvider` — building it, obtaining a `Tracer`, opening a `Span`, and shutting down:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .setSampler(AlwaysOnSampler)
  .addSpanProcessor(SpanProcessor.noop)
  .build()

val tracer = provider.get("com.example.payments")

tracer.span("process-payment") { span =>
  span.setAttribute("payment.id", "pay-001")
  span.setAttribute("currency", "USD")
}

provider.shutdown()
```

The global `trace` singleton wraps this pattern behind a zero-setup API. Call `trace.install(provider)` once at application startup to replace its built-in in-memory provider with a production-configured one.

## Creating Values

We build a `TracerProvider` in two steps: start with `TracerProvider.builder` to get a `TracerProviderBuilder`, configure it, then call `build()`.

### Starting with `TracerProvider.builder`

`TracerProvider.builder` is the sole public entry point for construction. It returns a fresh `TracerProviderBuilder` pre-populated with `Resource.default` (service name `"unknown_service"` plus SDK attributes), `AlwaysOnSampler`, an empty processor list, and the platform's default `ContextStorage`:

```scala
object TracerProvider {
  def builder: TracerProviderBuilder
}
```

We obtain a builder and immediately verify that its defaults are sane before adding any configuration:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val builder = TracerProvider.builder
// builder: TracerProviderBuilder — ready for chained configuration calls
val minimalProvider = builder.build()
minimalProvider.shutdown()
```

### Building with `TracerProviderBuilder.build`

`TracerProviderBuilder.build()` resolves the accumulated configuration and returns an immutable `TracerProvider`. If `setContextStorage` was never called, it substitutes `ContextStorage.defaultSpanContextStorage` — the shared singleton used for trace–log correlation:

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def build(): TracerProvider
}
```

The following example assembles a fully specified provider for an `"order-service"`:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .addSpanProcessor(SpanProcessor.noop)
  .build()

provider.shutdown()
```

## Core Operations

The full API of `TracerProvider` and `TracerProviderBuilder` falls into three categories: the builder's four configuration methods, the single method that produces a `Tracer`, and the three lifecycle methods that release processor resources.

### Builder — Configuration

The builder methods `setResource`, `setSampler`, `addSpanProcessor`, and `setContextStorage` control every aspect of a provider before it is built. Each method mutates the builder in place and returns `this`, enabling a fluent chain.

#### Describing the service entity

`TracerProviderBuilder.setResource` replaces the `Resource` that the provider stamps on every span. A `Resource` wraps a typed `Attributes` collection; the most common attribute is `service.name`, accessed via the predefined `Attributes.ServiceName` key. If `setResource` is never called, the builder uses `Resource.default`, which carries `service.name = "unknown_service"` alongside SDK identification attributes:

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): TracerProviderBuilder
}
```

We override the resource when multiple services share a single JVM process and need distinct identities in the trace backend:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "inventory-service")))
  .build()

provider.shutdown()
```

#### Controlling sampling behavior

`TracerProviderBuilder.setSampler` sets the `Sampler` consulted on every new span. Three built-in samplers cover the common strategies: `AlwaysOnSampler` records every span (the default), `AlwaysOffSampler` drops all spans and returns a `Span.NoOp` with zero allocation overhead, and `ParentBasedSampler` follows the incoming trace's sampling flag for child spans and delegates to a root sampler when no parent exists:

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setSampler(sampler: Sampler): TracerProviderBuilder
}
```

We configure OpenTelemetry-compatible head sampling by wrapping `AlwaysOnSampler` as the root of a `ParentBasedSampler`:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

provider.shutdown()
```

#### Attaching span processors

`TracerProviderBuilder.addSpanProcessor` appends a `SpanProcessor` to the provider's ordered list. A `SpanProcessor` receives `onStart` and `onEnd` callbacks for every sampled span. Multiple calls accumulate processors; the first added is the first called during both event dispatches and during `shutdown()` or `forceFlush()`:

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
}
```

We register two processors in insertion order — for example, an exporter alongside a local inspector:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .addSpanProcessor(SpanProcessor.noop) // replace with an OTLP exporter
  .addSpanProcessor(SpanProcessor.noop) // replace with a metrics bridge
  .build()

provider.shutdown()
```

:::caution
Processors are invoked in insertion order. If an earlier processor throws, later processors in the chain do not receive the callback for that span.
:::

#### Sharing the context store

`TracerProviderBuilder.setContextStorage` overrides the `ContextStorage[Option[SpanContext]]` used to propagate the active span context through the call stack. When not set, the builder uses `ContextStorage.defaultSpanContextStorage`, backed by JDK 25 `ScopedValue` on JVM. Override this when integrating with a custom async runtime or when you need to share the same storage instance with a `LoggerProvider` to enable trace–log correlation:

```scala
final class TracerProviderBuilder private[telemetry] (...) {
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder
}
```

The following example creates a single `ContextStorage` instance and passes it to both a `TracerProvider` and a `LoggerProvider` so that every `LogRecord` emitted inside a span carries that span's trace and span identifiers:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val sharedStorage = ContextStorage.create[Option[SpanContext]](None)

val tp = TracerProvider.builder.setContextStorage(sharedStorage).build()
val lp = LoggerProvider.builder.setContextStorage(sharedStorage).build()

trace.install(tp)
log.install(lp.get("com.example"))

tp.shutdown()
```

### Tracer Access

`TracerProvider.get` produces a `Tracer` bound to a named instrumentation scope. Pass a scope name — typically a package or library name — and an optional version string. The returned `Tracer` inherits the provider's resource, sampler, processor list, and context storage; every span it opens is stamped with the given `InstrumentationScope`:

```scala
final class TracerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Tracer
}
```

We retrieve a versioned tracer for a specific component alongside an unversioned one for general use:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()

val tracer  = provider.get("com.example.checkout")
val tracerV = provider.get("com.example.checkout", "3.1.0")

tracer.span("validate-cart") { span =>
  span.setAttribute("cart.items", 5L)
}

provider.shutdown()
```

:::note
`TracerProvider.get` allocates a new `Tracer` on every call. Assign the result to a `val` at startup and reuse it throughout the service lifetime rather than calling `get` per request.
:::

### Lifecycle

`TracerProvider.shutdown`, `TracerProvider.forceFlush`, and `TracerProvider.close` manage the provider's operational lifetime. All three iterate the registered processor list and delegate to the corresponding method on each processor.

`TracerProvider.shutdown` calls `shutdown()` on every processor in insertion order, releasing their resources; call it once when the application exits. `TracerProvider.forceFlush` calls `forceFlush()` on each processor, asking them to export any buffered span data immediately; use it before a scheduled checkpoint or a graceful drain. `TracerProvider.close` is an alias for `shutdown()` that satisfies `AutoCloseable`:

```scala
final class TracerProvider private[telemetry] (...) extends AutoCloseable {
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit
}
```

We use `scala.util.Using.resource` to guarantee `shutdown()` even if span processing throws:

```scala mdoc:compile-only
import scala.util.Using
import zio.blocks.telemetry._

Using.resource(TracerProvider.builder.addSpanProcessor(SpanProcessor.noop).build()) { provider =>
  val tracer = provider.get("com.example")
  tracer.span("startup-check") { _ => () }
}
```

## Subtypes and Related Types

Two closely associated types complete the `TracerProvider` story: the mutable builder that produces it, and the global `trace` singleton that holds a reference to one.

| Type                    | Relationship                                                                                                                                                                              |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TracerProviderBuilder` | Mutable builder returned by `TracerProvider.builder`. Accumulates resource, sampler, processor, and context-storage settings; call `build()` to produce an immutable `TracerProvider`.    |
| `trace` (object)        | Global singleton backed by an `AtomicReference[TracerProvider]`. Ships with an in-memory default; call `trace.install(provider)` to replace it with a production-configured provider.     |

The `trace` object is the most common integration point for application code. We install a configured provider at startup and remove the setup burden from every individual call site:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

trace.install(provider)

trace.span("list-products", SpanKind.Server) { span =>
  span.setAttribute("page.size", 20L)
}

provider.shutdown()
```

## Comparison: OpenTelemetry Java `SdkTracerProvider`

Both `SdkTracerProvider` and `TracerProvider` follow the same conceptual pattern: builder → provider → tracer → span. The following table summarises the key differences:

| Aspect                  | OpenTelemetry Java `SdkTracerProvider`                                         | ZIO Blocks `TracerProvider`                                                               |
|-------------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| **Dependencies**        | Requires `opentelemetry-sdk` and its transitive closure                        | Zero external dependencies                                                                |
| **Platform**            | JVM only                                                                       | JVM and Scala.js via shared sources                                                       |
| **Builder entry point** | `SdkTracerProvider.builder()` (static factory)                                 | `TracerProvider.builder` (companion object method)                                        |
| **Context propagation** | `Context` / `ContextKey` from the OTel API                                     | `ContextStorage[Option[SpanContext]]` — ScopedValue semantics, no OTel API dependency     |
| **Scope identity**      | `InstrumentationLibrary` registered via service loader or SDK config           | `InstrumentationScope(name, version)` passed directly to `get`                            |
| **Resource defaults**   | Populated via `ResourceProvider` SPI                                           | `Resource.default` with `service.name`, SDK name, language, and version attributes        |
| **Shutdown result**     | `close()` returns `CompletableResultCode`                                      | `shutdown()` is synchronous `Unit`                                                        |

If you need to forward spans to an OpenTelemetry-compatible collector, implement `SpanProcessor` to translate `SpanData` to the OTLP wire format and register it via `addSpanProcessor`. This keeps the application free of the OTel SDK dependency while remaining wire-compatible with any OTLP-capable backend.

## Integration

`TracerProvider` sits at the head of the Tracing type chain. It produces `Tracer` instances; each `Tracer` opens and closes `Span` scopes, consults the shared `Sampler`, dispatches `onStart` and `onEnd` to the shared `SpanProcessor` list, and stores the active `SpanContext` in the shared `ContextStorage` for nested-span parent resolution.

The most important cross-pillar integration point is trace–log correlation. When a `TracerProvider` and a `LoggerProvider` share the same `ContextStorage[Option[SpanContext]]` instance, every `LogRecord` emitted while a span is active automatically carries that span's `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` as unboxed primitive fields — no boxing, no string formatting until a `LogFormatter` runs. Both providers default to the same internal `ContextStorage.defaultSpanContextStorage` singleton, so correlation is automatic when both are installed with their default settings. Pass an explicit shared instance via `setContextStorage` when you need to isolate correlation in tests or when integrating with a custom async runtime.

For a complete picture of how `TracerProvider`, `LoggerProvider`, and `MeterProvider` interconnect across all three observability pillars, see the [Telemetry module index](./index.md).
