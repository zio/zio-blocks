---
id: index
title: "Tracing"
description: "The tracing pillar of ZIO Blocks Telemetry — the global trace object for spans, with provider configuration and in-memory inspection."
keywords:
  - "tracing"
  - "Global Tracing Entry Point"
  - "Span Creation"
  - "TracerProvider Configuration"
  - "Zero-Setup Tracing"
  - "Distributed Tracing Singleton"
---

The **tracing** pillar records causal [`Span`](./span.md)s of work across a request or operation. A [`TracerProvider`](./tracer-provider.md) builds [`Tracer`](./tracer.md)s (one per instrumentation scope), each `Tracer` opens spans — consulting a [`Sampler`](./sampler.md) and notifying a [`SpanProcessor`](./span-processor.md) chain on start/end — and a finished span is exported as immutable [`SpanData`](./span-data.md). Most code never touches those types directly — it calls the global `trace` object, which wraps a default in-memory `TracerProvider`.

## Types in this pillar

**Core**
- [`trace`](#the-trace-object) — global entry point; documented below.
- [`TracerProvider`](./tracer-provider.md) — factory for `Tracer`s; holds the shared sampler, processor chain, resource, and context storage.
- [`Tracer`](./tracer.md) — creates spans within one instrumentation scope.
- [`Span`](./span.md) — a mutable, thread-safe unit of work: attributes, events, links, status.

**Support**
- [`SpanContext`](./span-context.md) — the propagatable trace/span identity carried across boundaries.
- [`SpanData`](./span-data.md) — the immutable snapshot a finished span exports.
- [`SpanProcessor`](./span-processor.md) — the `onStart`/`onEnd` hook; implement it to export spans.
- [`Sampler`](./sampler.md) — the policy deciding which spans are recorded.

## The `trace` object

`trace` is the global tracing entry point for the ZIO Blocks Telemetry module — a companion-style singleton that requires zero configuration to use. Backed by an `InMemorySpanProcessor` out of the box, it records every completed span in a fixed-size ring buffer. Call `trace.install` once at application startup to replace the default provider with one that exports to a real backend; until then, all spans land in memory and are inspectable via `trace.collectedSpans`.

Its public API groups into four categories:

```scala
object trace {
  // Span Creation — delegates to the default Tracer
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A

  // Tracer Retrieval — named instrumentation scopes
  def get(name: String): Tracer

  // Configuration — mutate the global TracerProvider
  def install(provider: TracerProvider): Unit
  def removeAll(): Unit

  // Inspection — access the in-memory buffer
  def collectedSpans: List[SpanData]
  def clearSpans(): Unit
}
```

`trace` sits at the top of the Telemetry module's tracing pillar. It wraps a `TracerProvider`, which creates `Tracer` instances, which in turn create and manage `Span` values. Completed spans are snapshotted into `SpanData` and handed off to one or more `SpanProcessor` implementations. The relationship looks like this:

```
trace
  └── TracerProvider   (installed via trace.install)
        └── Tracer     (obtained via trace.get; used internally by span overloads)
              └── Span (the active unit of work in a trace)
                    └── SpanData (immutable export snapshot passed to processors)
```

The `TracerProvider` reference is held in a `java.util.concurrent.atomic.AtomicReference`, making it safe to replace exactly once at startup. App code calls `trace` directly; library code should accept a `Tracer` parameter obtained via `trace.get` so it remains independent of the global singleton.

- **Zero-setup** — an `InMemorySpanProcessor` is wired before the first call; no configuration is required.
- **Thread-safe** — the `TracerProvider` reference is stored in an `AtomicReference`.
- **Global singleton** — `trace.install` and `trace.removeAll` mutate shared state; call each at most once at startup.
- **Delegates lifecycle** — `trace` never manages span state directly; it delegates to `TracerProvider`, `Tracer`, and `Span`.

## Usage

The following example shows the core workflow in one place: wrap work in `trace.span`, enrich the live span with attributes, then inspect the completed span via `trace.collectedSpans`:

```scala
import zio.blocks.telemetry.trace
import zio.blocks.telemetry.{AttributeKey, Span, SpanData}

val result: Int = trace.span("fetch-user") { span =>
  span.setAttribute(AttributeKey.string("user.id"), "u42")
  span.setAttribute(AttributeKey.long("db.rows"), 1L)
  42
}
// result == 42

val spans: List[SpanData] = trace.collectedSpans
// spans.head.name == "fetch-user"
// spans.head.attributes.get(AttributeKey.string("user.id")) == Some("u42")
```

For production use, swap out the default provider by supplying a `TracerProvider` configured with a real exporter:

```scala
import zio.blocks.telemetry.{trace, TracerProvider, SpanProcessor}

// myExportProcessor sends spans to an OTLP endpoint (from zio-blocks-telemetry-otel)
val provider = TracerProvider.builder
  .addSpanProcessor(myExportProcessor)
  .build()
trace.install(provider)
```

## Construction / Creating Instances

`trace` is a Scala `object` — there is nothing to instantiate. We import and use it directly:

```scala
import zio.blocks.telemetry.trace
```

The object declaration is:

```scala
object trace {
  // see Core Operations below for all members
}
```

At module initialization, `trace` creates a private `InMemorySpanProcessor`, wires it into a default `TracerProvider` through `TracerProvider.builder`, and stores that provider in an `AtomicReference`. All of this happens before the first call; no user code is needed to trigger it.

## Core Operations

The `trace` singleton groups its API into span creation, tracer retrieval, global provider configuration, and in-memory buffer inspection.

### Span Creation

The span creation overloads — `span`, `span` with kind, and `span` with kind and attributes — all wrap a block of user code in a tracing span, calling the configured `SpanProcessor`'s `onStart` and `onEnd` hooks automatically so the caller never manages span lifecycle manually.

#### `span` — Create an internal span

Creates a `SpanKind.Internal` span around a block of user code using the default tracer. The span is started before the block executes and ended after it returns — even on exception. The block receives the live `Span` and may attach attributes, events, or a status to it.

```scala
object trace {
  def span[A](name: String)(f: Span => A): A
}
```

The return value of the block is returned directly. We never call `Span#end` manually; `trace.span` handles that:

```scala
import zio.blocks.telemetry.trace
import zio.blocks.telemetry.{AttributeKey, Span}

val count: Int = trace.span("compute") { span =>
  span.setAttribute(AttributeKey.long("items"), 100L)
  100
}
// count == 100
```

#### `span` — Create a span with an explicit kind

Accepts a `SpanKind` parameter, allowing you to mark the span as `Client`, `Server`, `Producer`, or `Consumer` instead of the default `Internal`. The kind is recorded in `SpanData` and used by distributed tracing backends and OpenTelemetry conventions to infer the span's role in the trace topology.

```scala
object trace {
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
}
```

Marking an outbound HTTP call as `SpanKind.Client` lets the tracing backend correlate it with the matching `SpanKind.Server` span on the receiving service:

```scala
import zio.blocks.telemetry.trace
import zio.blocks.telemetry.{SpanKind, AttributeKey}

trace.span("http-get-users", SpanKind.Client) { span =>
  span.setAttribute(AttributeKey.string("http.method"), "GET")
  // perform the HTTP request
}
```

#### `span` — Create a span with kind and initial attributes

The full overload accepts `name`, `kind`, and an `Attributes` collection as initial attributes. Providing attributes at creation time rather than via `Span#setAttribute` inside the block allows the configured `Sampler` to inspect them before deciding whether to record the span.

```scala
object trace {
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A
}
```

We build the initial attributes using `Attributes.of` or `Attributes.builder`, both from the same `zio.blocks.telemetry` package:

```scala
import zio.blocks.telemetry.trace
import zio.blocks.telemetry.{SpanKind, Attributes, AttributeKey}

val initial = Attributes.of(AttributeKey.string("db.name"), "users")
trace.span("db-query", SpanKind.Client, initial) { span =>
  span.setAttribute(AttributeKey.long("db.rows_affected"), 3L)
}
```

### Tracer Retrieval

`trace.get` is the bridge between the global singleton and the `Tracer` abstraction that library code accepts via dependency injection.

#### `get` — Obtain a named tracer

Returns a `Tracer` for the given instrumentation scope name from the currently installed `TracerProvider`. Library authors call `trace.get` once at initialization and accept the resulting `Tracer` throughout, keeping library code decoupled from the global singleton and independently testable.

```scala
object trace {
  def get(name: String): Tracer
}
```

We acquire the `Tracer` once at module startup and reuse it — every call to `trace.get` creates a new `Tracer` instance, so calling it on the hot path is wasteful:

```scala
import zio.blocks.telemetry.{trace, Tracer}

// Acquire once at startup or module initialization
val tracer: Tracer = trace.get("my-library")

tracer.span("db-query") { span =>
  // library work with span
}
```

:::tip
Library code should accept a `Tracer` parameter rather than calling `trace.get` internally. This allows callers to pass a no-op `Tracer` in tests, or to wire the library into a different `TracerProvider` without touching the global `trace` singleton.
:::

### Configuration

The configuration operations replace the global `TracerProvider` atomically. We call `trace.install` exactly once at startup to wire production exporters, and `trace.removeAll` to silence all tracing — for instance in test suites that need to suppress tracing overhead.

#### `install` — Replace the TracerProvider

Atomically replaces the installed `TracerProvider` with a user-configured one. Any subsequent `trace.span` or `trace.get` call uses the new provider immediately. Spans already in flight at the moment of the swap continue on the old provider.

```scala
object trace {
  def install(provider: TracerProvider): Unit
}
```

We construct the provider using `TracerProvider.builder`, which accepts a `Resource`, a `Sampler`, and one or more `SpanProcessor` implementations:

```scala
import zio.blocks.telemetry.{trace, TracerProvider, Resource}

val provider = TracerProvider.builder
  .setResource(Resource.default)
  .addSpanProcessor(myExportProcessor)
  .build()
trace.install(provider)
```

:::caution
`trace.install` mutates global state. If called concurrently with active span creation, spans crossing the swap boundary may be processed by either the old or new provider. Call it exactly once, before your application starts processing requests.
:::

#### `removeAll` — Disable tracing globally

Replaces the installed provider with one that uses `AlwaysOffSampler`. All subsequent spans become no-ops: the user block still executes, but no attributes are recorded, no processors are notified, and `trace.collectedSpans` returns an empty list.

```scala
object trace {
  def removeAll(): Unit
}
```

`trace.removeAll` is useful in test suites that want to suppress all tracing for a test that does not need span assertions:

```scala
import zio.blocks.telemetry.trace

trace.removeAll()
// All trace.span calls are now no-ops; blocks still execute normally
```

:::note
Dropped spans still propagate a `SpanContext` with `isSampled == false`, so context propagation continues to work correctly. Child spans of a dropped parent receive a valid trace ID but are also dropped by the sampler.
:::

### Inspection

The inspection methods expose the in-memory ring buffer managed by the built-in `InMemorySpanProcessor`. They are most valuable in unit tests: create spans, then assert against the collected `SpanData` without standing up a real exporter.

#### `collectedSpans` — Read the in-memory buffer

Returns all `SpanData` instances collected by the default `InMemorySpanProcessor` since the last `trace.clearSpans` call. Each `SpanData` is an immutable snapshot of a completed span's name, kind, `SpanContext`, attributes, events, links, and status.

```scala
object trace {
  def collectedSpans: List[SpanData]
}
```

After a span completes, its data is available immediately in the returned list:

```scala
import zio.blocks.telemetry.{trace, SpanData, AttributeKey}

trace.span("validate") { span =>
  span.setAttribute(AttributeKey.string("input"), "42")
}

val spans: List[SpanData] = trace.collectedSpans
val s = spans.head
// s.name == "validate"
// s.attributes.get(AttributeKey.string("input")) == Some("42")
```

:::note
`trace.collectedSpans` only reflects spans collected by the built-in `InMemorySpanProcessor`. When you call `trace.install` with a custom provider, new spans flow to that provider's processors and are **not** visible through `collectedSpans`.
:::

#### `clearSpans` — Reset the in-memory buffer

Clears all spans from the `InMemorySpanProcessor` by zeroing its ring buffer and resetting the write index and element count. We call `trace.clearSpans` between test cases to prevent spans from one test leaking into assertions in the next.

```scala
object trace {
  def clearSpans(): Unit
}
```

A typical test pattern clears the buffer before each test and inspects it after exercising the code under test:

```scala
import zio.blocks.telemetry.trace

// before each test
trace.clearSpans()

// exercise the code under test
trace.span("my-op") { _ => () }

// assert
assert(trace.collectedSpans.nonEmpty)
assert(trace.collectedSpans.head.name == "my-op")
```

## `trace` vs. OpenTelemetry Java SDK (`GlobalOpenTelemetry`)

Both `trace` and `GlobalOpenTelemetry` are global singletons installed once at startup, and both delegate to a provider that creates instrumentation-scope-specific tracers. The practical differences are:

| Aspect                   | `trace`                                           | `GlobalOpenTelemetry`                               |
|--------------------------|---------------------------------------------------|-----------------------------------------------------|
| Zero-setup default       | In-memory buffer; spans visible immediately       | No-op until an `SdkTracerProvider` is registered    |
| Span lifecycle           | Higher-order function; `end` called automatically | `span.end()` required in a `finally` block          |
| Effect system dependency | None — purely synchronous                         | None in the Java SDK; ZIO wrappers exist separately |
| Language idiom           | Scala object with higher-order functions          | Java-style builder + try/finally                    |

The `trace` object mirrors the `GlobalOpenTelemetry` pattern but avoids the "noop until configured" footgun: every span is recorded in the in-memory buffer from the first call, so tests and development environments work without any additional setup. The higher-order function design also eliminates the need for a `try`/`finally` block at every call site.


