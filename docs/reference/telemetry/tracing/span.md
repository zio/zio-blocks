---
id: span
title: "Span"
description: "Mutable, thread-safe unit of work in a distributed trace — attributes, events, status, lifecycle, and zero-alloc no-op sentinel."
keywords:
  - "Recording Span"
  - "Distributed Trace Unit"
  - "Span Lifecycle"
  - "SpanContext Propagation"
  - "Zero-Alloc No-Op"
  - "SpanBuilder Factory"
  - "SpanData Export"
---

`Span` is the mutable, thread-safe unit of work in a distributed trace. Created via `SpanBuilder` or through a `Tracer`, a span records a start time, key–value attributes, named events, and a completion status from the moment it is started until `Span#end` is called. After `end`, all mutating methods become permanent no-ops and the accumulated data is exported as an immutable `SpanData` snapshot. In the telemetry module's Tracing pillar, `Span` sits between the `Tracer` that creates it and the `SpanData` that carries its data to `SpanProcessor` instances.

- **Mutable and thread-safe while recording** — The default `RecordingSpan` implementation is backed by a `CopyOnWriteArrayList` for attributes and events and an `AtomicBoolean` for end-once semantics.
- **Idempotent end** — Only the first call to `end` records the end timestamp; all subsequent calls are silent no-ops.
- **Zero-alloc no-op sentinel** — `Span.NoOp` is a singleton object that imposes no allocation cost and can replace any `Span` reference when tracing is disabled or the sampler dropped the span.
- **Automatic lifecycle via `Tracer`** — When spans are created through `Tracer#span` or `trace.span`, the tracer calls `SpanProcessor#onStart` on creation and `SpanProcessor#onEnd` after the block returns; instrumented code never manages processors directly.

The full public interface groups into identity, attribute mutation, event recording, status, lifecycle, and data export:

```scala
trait Span {
  // Identity — read-only; set at construction; callable at any time
  def spanContext: SpanContext
  def name: String
  def kind: SpanKind

  // Attribute mutation — typed key + convenience primitive overloads
  def setAttribute[A](key: AttributeKey[A], value: A): Unit
  def setAttribute(key: String, value: String): Unit
  def setAttribute(key: String, value: Long): Unit
  def setAttribute(key: String, value: Double): Unit
  def setAttribute(key: String, value: Boolean): Unit

  // Event recording — current-clock and explicit-timestamp overloads
  def addEvent(name: String): Unit
  def addEvent(name: String, attributes: Attributes): Unit
  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit

  // Status
  def setStatus(status: SpanStatus): Unit

  // Lifecycle
  def end(): Unit
  def end(endTimeNanos: Long): Unit
  def isRecording: Boolean

  // Export
  def toSpanData: SpanData
}

object Span {
  object NoOp extends Span  // zero-allocation singleton; all methods are no-ops
}
```

## Usage

The following example shows a complete span lifecycle: building a `Span` via `SpanBuilder`, enriching it with attributes and an event, setting a final status, ending it, and reading the immutable snapshot:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("checkout")
  .setKind(SpanKind.Server)
  .startSpan()

// Enrich while recording
span.setAttribute(AttributeKey.string("user.id"), "u42")
span.setAttribute(AttributeKey.long("items.count"), 3L)
span.addEvent("payment.authorized")
span.setStatus(SpanStatus.Ok)

// End and snapshot
span.end()
val data: SpanData = span.toSpanData

assert(data.name == "checkout")
assert(data.kind == SpanKind.Server)
assert(data.status == SpanStatus.Ok)
assert(!span.isRecording)
```

## Construction / Creating Instances

We create a `Span` in two ways: via `SpanBuilder` for manually managed recording spans, or by referencing `Span.NoOp` for a zero-cost drop-in no-op.

### `SpanBuilder.apply` — Build a recording span

`SpanBuilder.apply` constructs a fluent builder seeded with the given operation name and sensible defaults (`SpanKind.Internal`, no parent context, current time for start). We chain setter methods to configure the span, then call `SpanBuilder#startSpan` to obtain the live `Span`:

```scala
object SpanBuilder {
  def apply(name: String): SpanBuilder
}
```

The builder exposes configuration methods that each return `this` for chaining, followed by two `startSpan` overloads that actually allocate the span:

```scala
final class SpanBuilder {
  def setKind(kind: SpanKind): SpanBuilder
  def setParent(parentContext: SpanContext): SpanBuilder
  def setAttribute[A](key: AttributeKey[A], value: A): SpanBuilder
  def addLink(link: SpanLink): SpanBuilder
  def setStartTimestamp(nanos: Long): SpanBuilder
  def setResource(resource: Resource): SpanBuilder
  def setInstrumentationScope(scope: InstrumentationScope): SpanBuilder
  def startSpan(): Span                                      // generates a new TraceId
  def startSpan(traceIdHi: Long, traceIdLo: Long): Span     // continues an existing trace
}
```

The following example creates a client span with initial attributes set at build time, which allows the configured sampler to inspect them before the span is started:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("http.get /users")
  .setKind(SpanKind.Client)
  .setAttribute(AttributeKey.string("http.method"), "GET")
  .setAttribute(AttributeKey.string("http.url"), "https://api.example.com/users")
  .startSpan()

// use the span...
span.end()
```

:::tip
When spans are created through `Tracer#span` or `trace.span`, the tracer constructs the `SpanBuilder` for you and calls `startSpan` internally. Use `SpanBuilder` directly only when you need precise control over the span's kind, parent context, or start timestamp.
:::

### `Span.NoOp` — Zero-allocation no-op span

`Span.NoOp` is a singleton `object` that implements every `Span` method as a no-op. We use it when tracing is disabled or when the sampler drops a span — passing `Span.NoOp` to the user block avoids a null check at every call site while imposing zero allocation:

```scala
object Span {
  object NoOp extends Span
}
```

`Span.NoOp` always reports `isRecording == false` and `spanContext == SpanContext.invalid`. Calling any mutating method — `setAttribute`, `addEvent`, `setStatus`, or `end` — silently returns without side effects. The `Tracer` passes `Span.NoOp` to the user block whenever the sampler decides to drop the span, so instrumented code never needs to branch on whether tracing is active:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = Span.NoOp

span.setAttribute(AttributeKey.string("key"), "value")  // no-op
span.addEvent("event")                                   // no-op
span.end()                                               // no-op

assert(!span.isRecording)
assert(span.spanContext == SpanContext.invalid)
assert(span.name == "")
assert(span.kind == SpanKind.Internal)
```

## Predefined Instances

The `Span` companion object exposes one predefined instance:

| Instance    | `isRecording` | `spanContext`         | `name` | `kind`              | `toSpanData`                                                                   |
|-------------|---------------|-----------------------|--------|---------------------|--------------------------------------------------------------------------------|
| `Span.NoOp` | `false`       | `SpanContext.invalid` | `""`   | `SpanKind.Internal` | Fixed empty `SpanData` — `0L` timestamps, `Attributes.empty`, empty event list, `SpanStatus.Unset` |

## Core Operations

The `Span` API is organized by the stage at which each method is typically called: reading identity during propagation, adding attributes and events while work is in progress, setting a final status, controlling the span's lifecycle, and exporting the accumulated snapshot.

### Identity

The identity methods — `spanContext`, `name`, and `kind` — are read-only and may be called at any time, including after `end`. They reflect the values fixed at span creation and never change.

#### `spanContext` — The propagatable span identity

`Span#spanContext` returns the `SpanContext` that uniquely identifies this span's trace and position in the trace tree. The context carries a 128-bit trace ID (stored as two `Long` fields for zero allocation), a 64-bit span ID, trace flags (sampled or not), and a vendor-specific trace state string. We pass `spanContext` to downstream services or inject it into message headers for distributed trace propagation:

```scala
trait Span {
  def spanContext: SpanContext
}
```

The following example reads the trace ID hex string from the span's context — the identifier a logging system would use to correlate log lines with a trace:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("op").startSpan()
val ctx: SpanContext = span.spanContext

assert(ctx.isValid)
assert(ctx.traceIdHex.length == 32)  // 128-bit trace ID as 32 lowercase hex chars
span.end()
```

#### `name` — The operation name

`Span#name` returns the human-readable string describing the operation this span represents. The name is set once at span creation via `SpanBuilder.apply` and never changes:

```scala
trait Span {
  def name: String
}
```

We use the name to correlate spans in a waterfall view or to filter trace data in a telemetry backend:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("db.query").startSpan()
assert(span.name == "db.query")
span.end()
```

#### `kind` — The span relationship kind

`Span#kind` returns the `SpanKind` classifying how this span relates to its caller and callee. The kind is set at span creation via `SpanBuilder#setKind` and never changes:

```scala
trait Span {
  def kind: SpanKind
}
```

`SpanKind` is a sealed trait with five cases that map directly to the OpenTelemetry specification:

| Value               | Meaning                                                    |
|---------------------|------------------------------------------------------------|
| `SpanKind.Internal` | Work performed purely within the current process (default) |
| `SpanKind.Server`   | Server-side of an inbound synchronous RPC or HTTP request  |
| `SpanKind.Client`   | Client-side of an outbound synchronous RPC or HTTP request |
| `SpanKind.Producer` | Producer side of an asynchronous message                   |
| `SpanKind.Consumer` | Consumer side of an asynchronous message                   |

Matching on `kind` lets a span exporter apply OpenTelemetry semantic conventions automatically:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("payment.process")
  .setKind(SpanKind.Server)
  .startSpan()

span.kind match {
  case SpanKind.Server   => println("inbound request span")
  case SpanKind.Client   => println("outbound call span")
  case SpanKind.Internal => println("local work span")
  case SpanKind.Producer => println("message publish span")
  case SpanKind.Consumer => println("message consume span")
}

span.end()
```

### Attributes

Attributes annotate a span with key–value metadata — HTTP method, database name, user ID, cache hit flag, and so on — that telemetry backends index for filtering and grouping traces. All `setAttribute` methods are no-ops after `end` is called.

#### `setAttribute[A]` — Typed attribute

`Span#setAttribute[A]` sets a typed attribute using an `AttributeKey[A]`. If an attribute with the same key name was already set, it is replaced. We create typed keys via the `AttributeKey` companion object — `AttributeKey.string`, `AttributeKey.long`, `AttributeKey.double`, `AttributeKey.boolean`, and the `Seq`-typed variants — and the key's type parameter enforces that the value matches at compile time:

```scala
trait Span {
  def setAttribute[A](key: AttributeKey[A], value: A): Unit
}
```

The following example annotates a database span with a string system name, a numeric row count, a boolean cache flag, and a double latency value using typed keys:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("db.select").startSpan()

span.setAttribute(AttributeKey.string("db.system"),      "postgresql")
span.setAttribute(AttributeKey.long("db.rows_returned"), 42L)
span.setAttribute(AttributeKey.boolean("db.cache_hit"),  false)
span.setAttribute(AttributeKey.double("db.latency_ms"),  3.7)

span.end()
```

#### Convenience `setAttribute` overloads — Set primitive attributes by string key

For the common case of plain-string key names, `Span` provides four convenience overloads that accept a `String` key directly and infer the attribute type from the value:

| Signature                                             | Value type |
|-------------------------------------------------------|------------|
| `def setAttribute(key: String, value: String): Unit`  | `String`   |
| `def setAttribute(key: String, value: Long): Unit`    | `Long`     |
| `def setAttribute(key: String, value: Double): Unit`  | `Double`   |
| `def setAttribute(key: String, value: Boolean): Unit` | `Boolean`  |

These overloads are equivalent to creating an `AttributeKey` inline and are convenient for ad-hoc annotations where the key is not shared across multiple call sites:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("http.request").startSpan()

span.setAttribute("http.method",   "POST")
span.setAttribute("http.status",   201L)
span.setAttribute("http.duration", 45.2)
span.setAttribute("http.cached",   false)

span.end()
```

:::caution
All `setAttribute` calls after `Span#end` are silently ignored. Set attributes before ending the span.
:::

### Events

Events record point-in-time occurrences during the span's lifetime — a cache miss, a retry attempt, a checkpoint, or any other notable condition. Like attributes, all `addEvent` overloads are no-ops after `end` is called.

The three overloads share the same contract: they append a `SpanEvent` (name, epoch-nanosecond timestamp, `Attributes`) to the span's internal event list. We choose the overload based on how much context we want to capture:

| Overload                                                                    | Timestamp          | Attributes           |
|-----------------------------------------------------------------------------|--------------------|----------------------|
| `def addEvent(name: String): Unit`                                          | Current wall clock | Empty                |
| `def addEvent(name: String, attributes: Attributes): Unit`                  | Current wall clock | Provided             |
| `def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit` | Explicit (nanos)   | Provided             |

We use the no-argument variant for simple marker events, add attributes when context is needed, and supply an explicit timestamp when replaying events from a log or when the caller controls the clock:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("data.ingest").startSpan()

// Simple marker event — uses current epoch-nanosecond clock
span.addEvent("pipeline.started")

// Event with context attributes
span.addEvent("batch.processed",
  Attributes.of(AttributeKey.long("batch.size"), 500L))

// Event with an explicit epoch-nanosecond timestamp
span.addEvent("legacy.checkpoint",
  System.nanoTime(),
  Attributes.of(AttributeKey.string("source"), "audit-log"))

span.end()
```

### Status

A span's status communicates its final outcome to telemetry backends independently of individual attributes. Most tracing backends give the error status special treatment — for example, flagging the span as failed in waterfall views and alerting dashboards.

#### `setStatus` — Set the completion outcome

`Span#setStatus` sets the span's completion status to one of the three `SpanStatus` variants. The default is `SpanStatus.Unset`, meaning the instrumentation did not explicitly record an outcome:

```scala
trait Span {
  def setStatus(status: SpanStatus): Unit
}
```

`SpanStatus` has three variants: `SpanStatus.Unset` (default — no explicit outcome), `SpanStatus.Ok` (success), and `SpanStatus.Error(description)` (failure with a descriptive message). We call `setStatus` before `end` to record the final outcome:

```scala mdoc:compile-only
import zio.blocks.telemetry._

def process(succeed: Boolean): Unit = {
  val span: Span = SpanBuilder("task").startSpan()
  if (succeed) {
    span.setStatus(SpanStatus.Ok)
  } else {
    span.setStatus(SpanStatus.Error("task failed: timeout after 30s"))
  }
  span.end()
}

process(succeed = true)
process(succeed = false)
```

:::caution
`setStatus` is a no-op after `end`. Set the status before ending the span.
:::

### Lifecycle

The lifecycle methods control recording. We use `isRecording` to inspect whether a span is still active, and the two `end` overloads to seal it.

#### `isRecording` — Check if the span is active

`Span#isRecording` returns `true` while the span is recording and `false` after `end` has been called (or immediately for `Span.NoOp`). We use it to guard expensive attribute computations that are not worth performing for dropped or already-ended spans:

```scala
trait Span {
  def isRecording: Boolean
}
```

The following example shows `isRecording` transitioning from `true` to `false` as the span ends:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("op").startSpan()
assert(span.isRecording == true)
span.end()
assert(span.isRecording == false)
```

#### `end` — Seal the span

`Span` provides two `end` overloads that capture the end timestamp and seal the span. Both are idempotent — only the first call records the end time; subsequent calls are silent no-ops:

```scala
trait Span {
  def end(): Unit
  def end(endTimeNanos: Long): Unit
}
```

`end()` captures the current epoch-nanosecond timestamp automatically. `end(endTimeNanos)` accepts an explicit epoch-nanosecond value, which is useful for testing with fixed timestamps or when the caller controls the clock. The following example uses the explicit overload and verifies that only the first end call is recorded:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("op").startSpan()
val fixedEnd = 1_700_000_000_000_000_000L  // epoch nanoseconds
span.end(fixedEnd)

// Subsequent calls are silently ignored
span.end()
span.end(fixedEnd + 9999L)

val data: SpanData = span.toSpanData
assert(data.endTimeNanos == fixedEnd)
```

:::caution
Once ended, a span cannot be restarted. All mutating methods — `setAttribute`, `addEvent`, `setStatus`, and further calls to `end` — become permanent no-ops.
:::

### Export

After a span ends, we read its accumulated data as an immutable snapshot.

#### `toSpanData` — Snapshot the span

`Span#toSpanData` returns an immutable `SpanData` containing all attributes, events, links, the status, timing, and identity information collected over the span's lifetime. We can call it before `end` — in that case `endTimeNanos` is `0L` — but the canonical pattern is to call it after `end`. The `Tracer` also calls `toSpanData` internally after `end` and passes the result to each registered `SpanProcessor`:

```scala
trait Span {
  def toSpanData: SpanData
}
```

`SpanData` is a `final case class` that carries the complete span record. We call `toSpanData` directly in tests to assert on the recorded data:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val span: Span = SpanBuilder("auth.check").startSpan()
span.setAttribute(AttributeKey.string("user.id"), "u99")
span.addEvent("token.validated")
span.setStatus(SpanStatus.Ok)
span.end()

val data: SpanData = span.toSpanData

assert(data.name == "auth.check")
assert(data.status == SpanStatus.Ok)
assert(data.attributes.get(AttributeKey.string("user.id")) == Some("u99"))
assert(data.events.map(_.name) == List("token.validated"))
assert(data.endTimeNanos > 0L)
```

:::note
For `Span.NoOp`, `toSpanData` always returns a fixed empty `SpanData` with `0L` timestamps, `Attributes.empty`, an empty event list, `SpanStatus.Unset`, and `SpanContext.invalid`.
:::

## Subtypes / Variants

`Span` is a trait with two implementations. Application code never instantiates these directly — `SpanBuilder#startSpan` returns a `RecordingSpan` and the sampler supplies `Span.NoOp` for dropped spans:

| Variant                   | `isRecording` | Description                                                                                                     |
|---------------------------|---------------|-----------------------------------------------------------------------------------------------------------------|
| `RecordingSpan` (private) | `true`        | `CopyOnWriteArrayList`-backed implementation; mutating methods record until the first `end` call                |
| `Span.NoOp`               | `false`       | Companion singleton; all methods return immediately without side effects or allocation                          |

We branch on `isRecording` when we want to distinguish live spans from no-ops without depending on the concrete type:

```scala mdoc:compile-only
import zio.blocks.telemetry._

def describe(span: Span): String =
  if (span.isRecording) "recording: " + span.name
  else "no-op"

val live: Span = SpanBuilder("work").startSpan()
val noop: Span = Span.NoOp

assert(describe(live) == "recording: work")
assert(describe(noop) == "no-op")

live.end()
```

## Comparisons

### `Span` vs. OpenTelemetry Java SDK `io.opentelemetry.api.trace.Span`

Both types represent the same concept — a mutable unit of work in a distributed trace — but they differ in their API style, dependency footprint, and lifecycle management:

| Aspect                   | ZIO Blocks `Span`                                                       | OTel Java SDK `io.opentelemetry.api.trace.Span`                      |
|--------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------|
| Lifecycle management     | Caller manages `end`; `Tracer#span` wraps it automatically              | Caller must call `end()` in a `finally` block at every call site     |
| No-op implementation     | `Span.NoOp` — plain `object`, zero allocation, public type              | SDK-internal `DefaultSpan` / `InvalidSpan` — not a public API type  |
| Attribute API            | Typed `AttributeKey[A]` + four primitive convenience overloads          | Similar, but keys are `io.opentelemetry.api.common.AttributeKey<T>` |
| External dependencies    | Zero — pure Scala, no OTel API jar required                             | Requires `opentelemetry-api` on the classpath                        |
| `toSpanData` availability | Public method on `Span`; available at any time                         | `SpanData` lives in `opentelemetry-sdk`, not the API module          |
| Context propagation      | `SpanContext` stores trace/span IDs as `Long` fields (zero allocation) | `SpanContext` uses `TraceId`/`SpanId` wrapper objects                |

The ZIO Blocks `Span` mirrors OpenTelemetry concepts and naming conventions closely enough that knowledge of the OTel specification transfers directly. The principal practical difference is lifecycle: where the OTel Java SDK requires a `try`/`finally` at every call site, `Tracer#span` handles the `end` call automatically — instrumented code interacts only with the live span and never manages its lifetime explicitly.