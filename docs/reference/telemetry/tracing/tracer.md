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

## Obtaining a Tracer

`Tracer`'s constructor is package-private — you never build one directly. Get a named tracer from [`TracerProvider#get`](./tracer-provider.md) (production, with your configured provider) or [`trace.get`](./index.md) (a quick tracer off the global provider). A new instance is built per call, so cache it at initialization rather than calling on a hot path.

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
