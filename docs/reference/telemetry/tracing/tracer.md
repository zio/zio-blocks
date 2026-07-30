---
id: tracer
title: "Tracer"
description: "Reference page for Tracer, the span-creation engine in the telemetry module tracing sub-domain."
keywords:
  - "Tracer span creation"
  - "SpanBuilder traced block"
  - "TracerProvider tracing engine"
  - "OpenTelemetry Scala span lifecycle"
  - "ContextStorage parent propagation"
  - "Sampler SpanProcessor hooks"
  - "InstrumentationScope telemetry"
---

`Tracer` is the span-creation engine for a single instrumentation scope in the telemetry module. Call `Tracer#span` to wrap a block of work in a timed, attributed span; the tracer consults the configured `Sampler`, propagates the parent context through `ContextStorage`, and notifies each registered `SpanProcessor` as spans start and end. Every `Tracer` is obtained from a `TracerProvider` — it cannot be constructed directly.

- **Zero-allocation no-ops** — when the `Sampler` returns `Drop`, the traced block runs with `Span.NoOp` and no heap allocation occurs.
- **Automatic parent propagation** — each `span` call reads the current `SpanContext` from `ContextStorage`, builds the child span with its trace ID, and writes the new context back into storage for the duration of the block so that nested spans inherit the correct parent.
- **Thread-safe context** — on JVM, `ContextStorage` is backed by a JDK-25 `ScopedValue`; bindings are immutable between `scoped` boundaries and do not leak across threads. On Scala.js a mutable-variable swap within a single-threaded execution model is used instead.
- **Synchronous** — `Tracer` carries no effect-system dependency and composes naturally into ZIO workflows or plain Scala code.

```scala
final class Tracer private[telemetry] (
  val instrumentationScope: InstrumentationScope, // library / component name + version
  val resource: Resource,                          // entity-level metadata (service name, etc.)
  sampler: Sampler,                               // AlwaysOnSampler, ParentBasedSampler, …
  processors: Seq[SpanProcessor],                 // onStart / onEnd lifecycle hooks
  contextStorage: ContextStorage[Option[SpanContext]] // parent-span propagation carrier
) {
  // Span creation — three progressive overloads
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A

  // Manual span control
  def spanBuilder(name: String): SpanBuilder

  // Active context
  def currentSpan: Option[SpanContext]
}
```

## Usage

The following example creates a `TracerProvider`, obtains a `Tracer`, and exercises its three core capabilities — creating spans, nesting them automatically, and reading the active context:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
  .setSampler(AlwaysOnSampler)
  .build()

val tracer = provider.get("com.example.orders")

// Wrap a block of work in an Internal span
val result = tracer.span("validate-order") { span =>
  span.setAttribute("order.id", "ord-123")
  span.addEvent("validation-passed")
  42
}

// Nested spans — each child inherits the parent trace ID automatically
tracer.span("process-order", SpanKind.Server) { _ =>
  tracer.span("persist-order", SpanKind.Client) { inner =>
    inner.setAttribute("db.system", "postgresql")
  }
  val ctx = tracer.currentSpan // Some(SpanContext) for "process-order"
}
```

## Creating Values

`Tracer` has a package-private constructor and is always obtained from a `TracerProvider`. Two paths exist: a direct call to `TracerProvider#get` for library and production code, and the `trace.get` shortcut for application code that uses the global entry point.

### Via TracerProvider

`TracerProvider#get` returns a `Tracer` scoped to a named instrumentation scope and an optional version string. All tracers from the same provider share its `Resource`, `Sampler`, `SpanProcessor` list, and `ContextStorage`. Library code should accept a `TracerProvider` via dependency injection and call `get` once per instrumentation scope:

```scala
final class TracerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Tracer
}
```

The following example configures a provider with a parent-based sampler and extracts two tracers for different instrumentation scopes:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.default)
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

val tracer          = provider.get("com.example.orders")
val versionedTracer = provider.get("com.example.payments", "2.1.0")
```

### Via the global entry point

`trace.get` delegates to the currently installed `TracerProvider` and returns a named `Tracer`. Application code uses this shortcut; library code should prefer `TracerProvider` directly to avoid coupling to global state:

```scala
object trace {
  def get(name: String): Tracer
}
```

Calling `trace.get` works without any prior configuration — the global `trace` object defaults to a provider backed by an in-memory span store, which is suitable for development and testing:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer = trace.get("com.example.payments")

tracer.span("charge-card") { span =>
  span.setAttribute("amount", 99L)
}
```

## Core Operations

`Tracer`'s public API divides into three groups: the `span` overloads for creating traced blocks, `spanBuilder` for manual span control, and `currentSpan` for reading the active context.

### Span Creation

The three `span` overloads all wrap a user block in a timed span, consulting the `Sampler` and notifying `SpanProcessor` hooks on start and end. Each overload adds one level of control: `span(name)` defaults to `SpanKind.Internal` and empty attributes; `span(name, kind)` sets the span kind; `span(name, kind, attributes)` also injects initial attributes that the sampler may merge with its own before recording begins.

| Signature                                                                      | Default kind         | Initial attributes  |
|--------------------------------------------------------------------------------|----------------------|---------------------|
| `span[A](name: String)(f: Span => A): A`                                       | `SpanKind.Internal`  | `Attributes.empty`  |
| `span[A](name: String, kind: SpanKind)(f: Span => A): A`                       | explicit             | `Attributes.empty`  |
| `span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A` | explicit           | explicit            |

All three share the same lifecycle: if the sampler returns `Drop`, `f` runs with `Span.NoOp` and zero allocation; if `RecordOnly` or `RecordAndSample`, a `RecordingSpan` is built, `SpanProcessor#onStart` fires, `f` executes with the active context set to the new span's `SpanContext`, and `SpanProcessor#onEnd` fires after the block completes:

```scala
final class Tracer private[telemetry] (...) {
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A
}
```

The following example demonstrates all three overloads across a realistic request-handling path:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()
val tracer   = provider.get("com.example")

// Default Internal kind — most common for internal work units
val orderId = tracer.span("validate-request") { span =>
  span.setAttribute("step", "schema-check")
  "ord-123"
}

// Explicit Server kind — marks a service-boundary entry
tracer.span(s"POST /orders/$orderId", SpanKind.Server) { span =>
  span.setAttribute("http.method", "POST")

  // Client kind with initial attributes — marks an outgoing database call
  val dbAttrs = Attributes.builder.put("db.system", "postgresql").build
  tracer.span("db.orders.insert", SpanKind.Client, dbAttrs) { inner =>
    inner.setAttribute("db.statement", "INSERT INTO orders ...")
  }
}
```

:::caution
`Span` mutations after `Span#end` is called are silently ignored. The tracer always calls `end` in a `finally` block, so the span closes even when `f` throws — never call `span.end()` manually inside the block.
:::

:::note
When the sampler returns `Drop`, `Span.NoOp` is passed to `f` and the span's `SpanContext` (with trace flags set to `none`) is still propagated through `ContextStorage`. Nested `span` calls therefore inherit the correct trace ID even for unsampled traces.
:::

### Manual Span Management

`Tracer#spanBuilder` returns a `SpanBuilder` pre-configured with the tracer's `Resource` and `InstrumentationScope`. We use it when the standard `span` lifecycle does not apply — for example, when starting a span at a custom timestamp, or when adding links to spans from other trace contexts:

```scala
final class Tracer private[telemetry] (...) {
  def spanBuilder(name: String): SpanBuilder
}
```

The following example opens a producer span with an explicit start timestamp and a link to an upstream context:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider   = TracerProvider.builder.build()
val tracer     = provider.get("com.example")
val startNanos = System.nanoTime()

val span = tracer.spanBuilder("enqueue-message")
  .setKind(SpanKind.Producer)
  .setStartTimestamp(startNanos)
  .addLink(SpanLink(SpanContext.invalid, Attributes.empty))
  .startSpan()

try {
  span.setAttribute("queue", "orders")
} finally {
  span.end()
}
```

:::caution
Spans created through `SpanBuilder#startSpan` bypass the tracer's `Sampler` and `SpanProcessor` list — no `onStart` or `onEnd` callbacks fire. Use `Tracer#span` for all normal traced work; `spanBuilder` is reserved for cases where the default lifecycle callbacks must not apply.
:::

### Context Access

`Tracer#currentSpan` returns the `SpanContext` currently bound in the tracer's `ContextStorage`, or `None` when no span is active. We use this to inject trace and span identifiers into outgoing HTTP headers, queue message metadata, or log records when automatic trace–log correlation is not sufficient:

```scala
final class Tracer private[telemetry] (...) {
  def currentSpan: Option[SpanContext]
}
```

The following example reads the active trace ID inside a span block and confirms `None` is returned outside of one:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()
val tracer   = provider.get("com.example")

tracer.span("handle-request") { _ =>
  val ctx     = tracer.currentSpan                               // Some(SpanContext(...))
  val traceId = ctx.map(_.traceIdHex).getOrElse("00000000000000000000000000000000")
  println(s"trace=$traceId")
}

val noCtx = tracer.currentSpan                                   // None — outside any span
```

## Comparison with OpenTelemetry Java

`Tracer` mirrors the structural shape of `io.opentelemetry.api.trace.Tracer` from the OpenTelemetry Java SDK — both expose a `spanBuilder(name)` factory and a higher-level traced-block concept — but differ in several important respects:

| Dimension                   | `io.opentelemetry.api.trace.Tracer`                     | `zio.blocks.telemetry.Tracer`                        |
|-----------------------------|---------------------------------------------------------|------------------------------------------------------|
| Context propagation         | Thread-local `io.opentelemetry.context.Context`         | JDK-25 `ScopedValue` via `ContextStorage` (JVM)      |
| Global state                | `OpenTelemetry.getGlobalOpenTelemetry()` singleton      | `trace` object, replaceable via `trace.install`      |
| External dependencies       | OTel SDK JAR and its transitive dependency graph        | Zero external dependencies                           |
| Traced-block shorthand      | `spanBuilder(name).startScopedSpan(…)` (SDK extension) | `tracer.span(name) { span => … }` (built-in)         |
| Sampler integration         | `io.opentelemetry.sdk.trace.samplers.Sampler`           | `zio.blocks.telemetry.Sampler` (same drop/record semantics) |
| Data model alignment        | OTel specification (reference implementation)           | OTel data model, independent implementation          |

The zio-blocks `Tracer` is not a wrapper over the OTel SDK — it is an independent implementation of the OTel data model. Telemetry signals can be forwarded to any OTel-compatible backend by implementing `SpanProcessor` as an OTLP bridge and registering it via `TracerProvider.builder.addSpanProcessor(...)`. For automatic trace–log correlation, the same `ContextStorage` instance must be shared between the `TracerProvider` and the `LoggerProvider`; see the telemetry [module index](./index.md) for the correlation setup pattern.