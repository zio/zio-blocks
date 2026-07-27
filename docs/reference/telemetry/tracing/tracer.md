---
id: tracer
title: "Tracer"
description: "Scope-bound span factory in the ZIO Blocks tracing pillar — creates, samples, and propagates spans automatically."
keywords:
  - "Span Creation"
  - "InstrumentationScope"
  - "Automatic Parent Propagation"
  - "Sampler Consultation"
  - "SpanBuilder Manual Management"
  - "ContextStorage Propagation"
  - "Zero-Alloc No-Op Span"
---

`Tracer` is the scope-bound span factory in the ZIO Blocks Telemetry tracing pillar. Each instance is tied to a single `InstrumentationScope` — a name/version pair that identifies the instrumented library or component — and inherits the shared `Resource`, `Sampler`, ordered list of `SpanProcessor`s, and `ContextStorage` from the [`TracerProvider`](./tracer-provider.md) that created it. Library code should accept `Tracer` as a constructor or method parameter rather than reaching for the global `trace` singleton; the singleton is an application-layer convenience.

- **Thread-safe span lifecycle** — `Tracer#span` ends every span in a `finally` block and notifies `SpanProcessor`s on both start and end; callers write no cleanup code.
- **Automatic parent-context propagation** — the active `SpanContext` is read from `ContextStorage` before each span starts and written back for the duration of the block, so nested `Tracer#span` calls automatically form a parent/child hierarchy without any explicit wiring.
- **Zero-allocation no-op** — when the `Sampler` returns `Drop`, the supplied block is called with `Span.NoOp` (a singleton object), and a non-sampled `SpanContext` is scoped for child-span propagation. No heap allocation occurs on the dropped path.
- **Purely synchronous** — no ZIO or async dependency; `Tracer` is usable in any Scala code, including libraries that must not import an effect system.

The full public surface of `Tracer` groups its methods by purpose:

```scala
final class Tracer private[telemetry] (
  val instrumentationScope: InstrumentationScope,
  val resource: Resource,
  sampler: Sampler,
  processors: Seq[SpanProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) {
  // Span Creation — higher-order; consulted sampler; ends span in finally
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A

  // Manual Span Management — bypasses sampler and processors
  def spanBuilder(name: String): SpanBuilder

  // Context Access — reads the active span from ContextStorage
  def currentSpan: Option[SpanContext]
}
```

`Tracer` sits between `TracerProvider` (which configures and vends it) and [`Span`](./span.md) (which it creates and automatically closes):

```
trace (global singleton — AtomicReference[TracerProvider])
  └── TracerProvider   (resource / sampler / processors / ContextStorage)
        └── Tracer     ← this type
              └── Span (active unit of work, ended automatically)
                    └── SpanData (immutable export snapshot → SpanProcessor)
```

## Usage

The following example shows the two most common patterns together — a root span wrapping a child span — so we can see automatic parent propagation in action:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: TracerProvider = TracerProvider.builder.build()
val tracer: Tracer            = provider.get("checkout-service", "2.1.0")

val total: Double = tracer.span("calculate-total") { rootSpan =>
  rootSpan.setAttribute("cart.size", 3L)

  // This nested call becomes a child of "calculate-total" automatically
  tracer.span("fetch-prices", SpanKind.Client) { childSpan =>
    childSpan.setAttribute("db.system", "postgresql")
    99.95
  }
}

provider.shutdown()
```

`Tracer#span` consults the configured `Sampler`, sets the active `SpanContext` in `ContextStorage`, calls `SpanProcessor#onStart`, then executes the block. The span is always ended in a `finally` clause and `SpanProcessor#onEnd` is called with the resulting `SpanData`.

## Construction / Creating Instances

`Tracer`'s primary constructor is package-private; the two supported entry points are `TracerProvider#get` for production use and `trace.get` for quick application-level access.

### `TracerProvider#get` — Named scope from a configured provider

Returns a `Tracer` whose `InstrumentationScope` is set to the given `name` and optional `version`. The tracer inherits the provider's `Resource`, `Sampler`, `SpanProcessor` list, and `ContextStorage`. A new `Tracer` instance is constructed on every call, so we cache the result at module initialization.

```scala
final class TracerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Tracer
}
```

We call `TracerProvider#get` once during application or library initialization, naming the instrumentation scope after the library or service component:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(
  TracerProvider.builder
    .setSampler(ParentBasedSampler(AlwaysOnSampler))
    .addSpanProcessor(SpanProcessor.noop)
    .build()
) { provider =>
  val tracer: Tracer = provider.get("payments-service", "1.0.0")

  tracer.span("charge") { span =>
    span.setAttribute("amount_cents", 4200L)
  }
}
```

When `version` is an empty string (the default), the `InstrumentationScope` records no version. Supply a semantic version string whenever the instrumentation scope is a versioned library component.

:::caution
`TracerProvider#get` constructs a new `Tracer` on every call. Cache the returned instance at module initialization rather than calling `get` on the hot path.
:::

### `trace.get` — Named scope from the global provider

Returns a `Tracer` scoped to the given `name`, backed by whichever `TracerProvider` is currently installed in the global `trace` singleton. This is the fastest way to get a tracer in application code without managing a `TracerProvider` explicitly.

```scala
object trace {
  def get(name: String): Tracer
}
```

We call `trace.get` once at the top of an application module, before any spans are created:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("order-service")

tracer.span("submit-order") { span =>
  span.setAttribute("order.id", "ORD-7291")
}
```

If `trace.install` has not been called, `trace.get` delegates to the default in-memory provider, which buffers completed spans in `trace.collectedSpans`. This is useful for testing without any exporter setup.

:::note
Library code should accept `Tracer` as a parameter rather than calling `trace.get` inside the library. Depending on the global singleton makes a library non-portable and forces callers to install a global provider to control sampling.
:::

## Core Operations

`Tracer`'s API divides into three categories: the `span` overloads for idiomatic higher-order tracing, `Tracer#spanBuilder` for precise manual control, and `Tracer#currentSpan` for reading the active context.

### Span Creation

The three `span` overloads — `span(name)`, `span(name, kind)`, and `span(name, kind, attributes)` — wrap a block of code with a span, consulting the sampler, propagating context, and ending the span automatically. Each overload delegates to the next, adding an argument.

#### `span(name)` — Internal span with default kind and no initial attributes

Creates an `Internal` span around the supplied block. The sampler is consulted; if the decision is `Drop`, the block is called with `Span.NoOp` and a dropped `SpanContext` is scoped; otherwise a `RecordingSpan` is created, processors are notified, context is propagated, and the block runs.

```scala
final class Tracer private[telemetry] (...) {
  def span[A](name: String)(f: Span => A): A
}
```

We use `Tracer#span` as the primary entry point when the span kind is `Internal` and we have no attributes to set before the block starts:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("parser")

val parsed: String = tracer.span("parse-request") { span =>
  span.setAttribute("parser", "json")
  """{"ok": true}"""
}
```

All mutations on `Span` after `end()` is called are silently ignored. The span is always ended in a `finally` block, so exceptions do not leak open spans.

#### `span(name, kind)` — Span with explicit kind and no initial attributes

Creates a span of the specified `SpanKind` with no initial attributes. The kind is recorded in the resulting `SpanData` and used by tracing backends to categorize the span (for example, `Client` spans render differently from `Server` spans in Jaeger).

```scala
final class Tracer private[telemetry] (...) {
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
}
```

We pass `SpanKind.Client` when the span represents an outbound call to a remote service:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("http-client")

tracer.span("http-get", SpanKind.Client) { span =>
  span.setAttribute("http.method", "GET")
  span.setAttribute("http.url", "https://api.example.com/items")
}
```

#### `span(name, kind, attributes)` — Full-featured span with kind and initial attributes

Creates a span of the specified kind and supplies initial `Attributes` that the sampler sees before it decides whether to record. This is the overload that all other `span` methods delegate to. It reads the parent `SpanContext` from `ContextStorage`, consults the sampler (passing the attributes), then dispatches to the `Drop`, `RecordOnly`, or `RecordAndSample` branch. In the `RecordAndSample` branch the span is stamped with `TraceFlags.sampled`.

```scala
final class Tracer private[telemetry] (...) {
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A
}
```

We supply initial attributes when the sampler needs attribute values to make its decision — for example, a rule-based sampler that samples only database spans:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("db-layer")

val sql = "SELECT * FROM orders WHERE id = $1"

val attrs: Attributes = Attributes.builder
  .put("db.system", "postgresql")
  .build

tracer.span("db-query", SpanKind.Client, attrs) { span =>
  span.setAttribute("db.statement", sql)
}
```

:::note
Attributes set via the `attributes` parameter are passed to the sampler before the span is created. Attributes set via `Span#setAttribute` inside the block are recorded after sampling; the sampler never sees them.
:::

### Manual Span Management

The `spanBuilder` method gives precise control over span lifecycle at the cost of requiring manual `end()` calls.

#### `spanBuilder` — Pre-configured builder for manual spans

Returns a `SpanBuilder` that is pre-configured with this tracer's `Resource` and `InstrumentationScope`. Use it when you need to set a custom start timestamp, add links to other spans, or pass the `Span` across an asynchronous boundary where the higher-order `span` API cannot wrap the work.

```scala
final class Tracer private[telemetry] (...) {
  def spanBuilder(name: String): SpanBuilder
}
```

We use `Tracer#spanBuilder` when we need to record spans that span async boundaries or when we control the start time explicitly:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("async-worker")

val startNanos: Long = System.nanoTime()

val span: Span = tracer.spanBuilder("async-work")
  .setKind(SpanKind.Producer)
  .setStartTimestamp(startNanos)
  .startSpan()

try {
  span.setAttribute("queue.name", "orders")
} finally {
  span.end()
}
```

:::caution
Spans created via `SpanBuilder` bypass the `Tracer`'s sampler and processor pipeline. The sampler is not consulted and processors are not notified — the span is purely local. Prefer `Tracer#span` in all cases where the higher-order API is usable.
:::

### Context Access

The `currentSpan` property reads the `ContextStorage` to expose the active span context without requiring a surrounding `span` block.

#### `currentSpan` — Read the active span context

Returns the `SpanContext` that `ContextStorage` has bound in the current scope, or `None` if no span is active. We use this to attach the active trace and span IDs to log records or outbound HTTP headers without needing a reference to the active `Span` object.

```scala
final class Tracer private[telemetry] (...) {
  def currentSpan: Option[SpanContext]
}
```

We read `Tracer#currentSpan` inside a `span` block to extract the active trace identifiers for manual correlation:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("correlation")

tracer.span("outer") { _ =>
  val ctx: Option[SpanContext] = tracer.currentSpan
  val traceId: String          = ctx.map(_.traceIdHex).getOrElse("")
  val spanId: String           = ctx.map(_.spanId.toHex).getOrElse("")

  // traceId and spanId can now be injected into outbound headers or log MDC
  tracer.span("inner") { _ =>
    // currentSpan here returns the inner span's context
    val innerCtx = tracer.currentSpan
    assert(innerCtx != ctx)
  }
}
```

Outside any active `span` block, `currentSpan` returns `None`. Inside a `Drop`-sampled span it returns `Some` with a non-sampled `SpanContext` (the dropped context that was scoped to preserve propagation).

## Comparisons

### `Tracer` vs. OpenTelemetry Java `io.opentelemetry.api.trace.Tracer`

The OpenTelemetry Java `Tracer` interface and this `Tracer` serve the same conceptual role but differ in dependency weight, context propagation mechanism, and call-site ergonomics:

| Aspect                        | ZIO Blocks `Tracer`                                     | OTel Java `Tracer`                                        |
|-------------------------------|---------------------------------------------------------|-----------------------------------------------------------|
| Dependencies                  | Zero — pure Scala, no OTel SDK jars                     | Requires `opentelemetry-api` (and SDK for processors)     |
| Context propagation           | JDK ScopedValue via pluggable `ContextStorage`          | Thread-local `Context` (pluggable via `ContextStorage`)   |
| Span lifecycle                | Higher-order `tracer.span("op") { ... }` — always ended | Manual `span.end()` in a `try`/`finally` block            |
| Missing `end()` risk          | None — the `finally` block is inside `Tracer`           | High — caller must remember the `finally` block           |
| Sampler integration           | Sampler consulted inside `span`; attributes visible     | Sampler consulted inside `SpanBuilder.startSpan()`        |
| Zero-cost dropped spans       | `Span.NoOp` singleton, no allocation                   | `PropagatedSpan` / `InvalidSpan` singleton, similar cost  |
| Cross-platform                | JVM and Scala.js                                        | JVM only                                                  |

The most significant ergonomic difference is span lifecycle: in OTel Java, every call site must wrap span work in `try { ... } finally { span.end(); }` to guarantee the span is recorded. Missing this block silently produces open spans that clog backends and inflate latency histograms. The higher-order `Tracer#span` API eliminates this class of bug by placing the `finally` block inside `Tracer`, where it cannot be omitted.

```scala mdoc:compile-only
import zio.blocks.telemetry._

// ZIO Blocks — span is always ended; no try/finally at the call site
val tracer: Tracer = trace.get("example")
tracer.span("operation") { span =>
  span.setAttribute("key", "value")
  // end() is called automatically here, even on exception
}
```

The OpenTelemetry Java equivalent requires manual lifecycle management at every instrumentation point, which makes instrumentation code visually heavier and more error-prone.