---
id: telemetry-guide
title: "Telemetry: Architecture, Patterns, and Real-World Usage"
description: "Understand how the logging, tracing and metrics pipeline works, then wire it into a production service with sampling, exporters and ordered shutdown."
---

`zio-blocks-telemetry` is an effect-free, zero-allocation observability library that gives you structured logging, distributed tracing, and metrics without pulling in the OpenTelemetry SDK or any effect system. This guide explains why it works the way it does, shows what happens inside each operation, and walks through patterns for production systems.

New to telemetry? Start with the tutorial [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md), which builds up a small batch job one pillar at a time, then come back here for the architecture and trade-offs.

If you're looking for API signatures and quick copy-paste snippets, the [Telemetry Reference](../reference/telemetry/index.md) has those. This guide focuses on the "how" and "why": architecture, design trade-offs, and the kind of understanding that helps when things go wrong at 3 AM.

## Installation

```scala
// Core: logging, tracing, metrics
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "@VERSION@"

// OTLP JSON export over HTTP (JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "@VERSION@"
```

On the JVM, the module needs JDK 21 or newer. The core module also cross-builds for Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-telemetry" % "@VERSION@"
```

---

## Part 1: Architecture and Design Philosophy

This part explains why the library is effect-free and how a signal travels from a call site to an exporter.

### Why effect-free?

Most Scala observability libraries wrap logging or tracing calls in an effect type. ZIO Logging needs `ZIO[R, E, Unit]`. Odin uses `F[Unit]`. Even scribe's direct API leans on Cats effect in production setups.

Effect-wrapping is a real cost. You can't call `log.info(...)` from a callback, a `Future`, a background thread, or a Java library without threading the effect runtime through. You can't log in a constructor, an `AutoCloseable.close()`, or any other place where effects are inconvenient.

`zio-blocks-telemetry` takes a different position: logging and metrics are inherently synchronous, low-level operations. They should work anywhere, with no ceremony. You import the package, call `log.info(...)`, and it works. The library is responsible for making that cheap, not for constraining where you can use it.

This is the same approach `logback` and `java.util.logging` take, but with structured attributes, zero boxing on the fast path, and a modern Scala macro interface.

### The provider-instrument-processor pipeline

Every signal (log, span, metric) flows through the same three-stage pipeline:

```
Call site
    |
    v
[Provider]  -- creates and configures instruments --
    |                                                  LoggerProvider
    |                                                  TracerProvider
    |                                                  MeterProvider
    v
[Instrument]  -- the thing you call at call sites --
    |                                                  Logger  (wraps LogRecord)
    |                                                  Tracer  (wraps Span)
    |                                                  Counter / Histogram / Gauge
    v
[Processor]  -- receives the completed signal --
    |                                                  LogRecordProcessor
    |                                                  SpanProcessor
    |                                                  MetricReader (pull-based)
    v
[Exporter]  -- sends data out of process --
    |                                                  OtlpJsonLogExporter
    |                                                  OtlpJsonTraceExporter
    |                                                  OtlpJsonMetricExporter
    v
OTLP collector / file / console
```

Processors are composable: you can chain several. A `LoggerProvider` built with `.addLogRecordProcessor(console).addLogRecordProcessor(otelExporter)` will call both processors for every log record.

The `log` object and the `Global*` singletons sit in front of this pipeline. They hold a reference to the current provider (an `AtomicReference`) and forward calls through it. You can swap the provider at runtime via `install()` without touching any call sites.

### Zero-allocation design

Two separate things must both be cheap: the fast path when a level is disabled, and the slow path when it's enabled.

**Disabled level fast path.** The first check in every `log.*` call is:

```scala
if (severity.number >= GlobalLogState.globalMinLevel) { ... }
```

`globalMinLevel` is a `@volatile Int` held inside the library-private `GlobalLogState` (you set it through `log.install`, `log.setMinSeverity`, and `log.withMinSeverity` rather than reading it directly). Reading it costs roughly one memory fence. If the level is disabled, the compiler has already eliminated all argument evaluation (the arguments are passed by-name or inlined via macro). No string is built, no object is allocated.

**Enabled level slow path.** When a level is enabled, attribute pairs need to be assembled into a `LogRecord`. The library uses a pooled `AttributeBuilder` (one per thread, reused), backed by parallel primitive arrays. Primitive values (Long, Double, Boolean) are stored as unboxed longs using type discriminator bytes. A `(String, Long)` attribute doesn't box the Long.

For metrics, `Counter` uses a `ConcurrentHashMap[Attributes, LongAdder]`. `LongAdder` is Java's striped counter, designed for high-concurrency increment without contention. `Gauge` uses `AtomicLong` storing the double bits directly. `Histogram` uses `ReentrantLock` per attribute set because it needs to update count, sum, min, max, and bucket counts atomically.

**What actually allocates on the slow path:**
- `Attributes.empty` is a singleton: no allocation
- Attribute builders are pooled: no allocation per record
- The `LogRecord` itself is allocated (unavoidably), but only when the level is enabled
- `SpanContext` stores trace ID as two `Long` fields, not a `UUID` object

### Global singletons vs explicit provider wiring

The three global entry points (`log`, `trace`, `metric`) work without any setup. On first use, each creates a sensible default:

- `log` creates a `ConsoleLogRecordProcessor` that writes to stdout
- `trace` creates an `InMemorySpanProcessor` that stores spans in a ring buffer (useful for testing)
- `metric` creates a basic `MeterProvider` with a `MetricReader`

For tests and exploratory code, the defaults are exactly right. For production, call `install()` once at startup to replace each default with your configured provider.

The global singletons are intentionally not safe to use across threads during `install()`. Call `install()` before starting any request processing. After installation, the `AtomicReference` read on every call is safe and cheap.

---

## Part 2: Logging Deep Dive

This part covers the logging API, from the first call to multiple outputs and testing.

### Getting started

The simplest possible start:

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.info("server started")
```

No imports beyond the package. No provider setup. Output goes to stdout as human-readable text. This is intentional: the zero-configuration default is production-safe for development.

Moving to a more realistic setup:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Structured attributes alongside the message
log.info("user signed in", "user_id" -> "u-7890", "method" -> "oauth2")

// Numbers are unboxed on the fast path
log.info("payment processed", "amount_cents" -> 4999L, "currency" -> "EUR")

// Exceptions get type, message, and stacktrace automatically.
// Bind to a Throwable-typed val; an inline `new SomeException(...)` does not resolve.
val declined: Throwable = new IllegalStateException("card declined")
log.error("payment failed", declined)

// Mix freely
val timeout: Throwable = new RuntimeException("timeout")
log.warn(
  "upstream degraded",
  "service" -> "inventory",
  "latency_ms" -> 850L,
  timeout
)
```

Adding a file output at startup. The module ships stdout, stderr, and no-op writers; a file destination is a few lines of your own:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import java.io.PrintWriter

final class AppFileWriter(path: String) extends LogWriter {
  private val out = new PrintWriter(path)
  def write(content: CharSequence): Unit = out.println(content)
  override def flush(): Unit             = out.flush()
  override def close(): Unit             = out.close()
}

// Human-readable text to one file, OTLP JSON to another
log.writer(TextLogFormatter, new AppFileWriter("logs/app.log"))
log.writer(JsonLogFormatter, new AppFileWriter("logs/app.jsonl"))
```

Both outputs are active simultaneously, alongside the default console output — `writer()` is additive.

### Macro mechanics explained

Every `log.*` method is a Scala inline macro. The macro generates something like this for `log.info("msg", "key" -> 42L)`:

```
Conceptual expansion of:
  log.info("request received", "user_id" -> userId, "attempt" -> 3)

Expands to (pseudocode):
  if (Severity.Info.number >= GlobalLogState.globalMinLevel) {
    val state = log.getState()
    if (state != null && Severity.Info.number >= state.effectiveLevel(<current class name>)) {
      val now = EpochClock.epochNanos()
      val builder = AttributeBuilderPool.get()
        .put("code.filepath", "<source file>")
        .put("code.namespace", "<current package>")
        .put("code.function", "<current method>")
        .put("code.lineno", <line number>.toLong)
      // for each enrichment argument, the macro calls the right builder method:
      builder.put("user_id", userId)      // resolved as (String, String)
      builder.put("attempt", 3.toLong)    // resolved as (String, Int) -> toLong
      // reads annotations from scoped context
      val annotations = LogAnnotations.get()
      // reads span context from ContextStorage
      val spanCtx = state.logger.currentSpanContext()
      state.logger.emitRaw(now, Severity.Info, "INFO", "request received",
                           builder, traceIdHi, traceIdLo, spanId, traceFlags,
                           resource, scope, None)
    }
  }
```

The important things the macro does:
- Captures `code.filepath`, `code.namespace`, `code.function`, `code.lineno` at compile time
- Resolves each enrichment argument type at compile time and calls the specific builder method (no runtime dispatch on the fast path)
- For rate-limited variants (`infoEvery`, `warnAtMost`), hashes the source position into a `siteId` constant so each call site gets its own rate-limit slot

For `*Every(N, msg)`, the macro emits a guard against a shared global table:

```scala
// siteId is a compile-time constant: (sourcePath + ":" + line).hashCode
if (LogRateLimit.shouldLogEvery(siteId, N)) { log.emit(...) }

// LogRateLimit keeps one AtomicLongArray of 4096 counters:
//   counters.incrementAndGet(siteId & 4095) % N == 0
```

For `*AtMost(windowMs, msg)`, it's the same shape against a parallel timestamp array:

```scala
if (LogRateLimit.shouldLogAtMost(siteId, windowMs)) { log.emit(...) }

// timestamps.get(idx); if (now - last >= windowMs) timestamps.compareAndSet(idx, last, now)
```

Two consequences of the shared table: `*Every(N, …)` emits on the *Nth* call rather than the first (the counter is incremented before the modulo), and two call sites whose `siteId` collides modulo 4096 share one slot.

### Custom enrichment types

The `LogEnrichment[A]` typeclass lets the macro delegate argument resolution to your code.

Say you want to log `UUID` values directly:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import java.util.UUID

// The implicit must be in scope at log call sites
implicit val uuidEnrichment: LogEnrichment[(String, UUID)] =
  new LogEnrichment[(String, UUID)] {
    def enrich(record: LogRecord, value: (String, UUID)): LogRecord =
      record.copy(
        attributes = record.attributes ++ Attributes.of(
          AttributeKey.string(value._1), value._2.toString
        )
      )
  }

// Now this compiles and works
val requestId = UUID.randomUUID()
log.info("request started", "request_id" -> requestId)
```

For `java.time.Instant`:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import java.time.Instant

implicit val instantEnrichment: LogEnrichment[(String, Instant)] =
  new LogEnrichment[(String, Instant)] {
    def enrich(record: LogRecord, value: (String, Instant)): LogRecord =
      record.copy(
        attributes = record.attributes ++ Attributes.of(
          AttributeKey.string(value._1), value._2.toString
        )
      )
  }

log.info("event occurred", "timestamp" -> Instant.now())
```

**Performance note.** The built-in enrichments for `(String, String)`, `(String, Long)`, `(String, Int)`, `(String, Double)`, and `(String, Boolean)` go through the attribute builder's primitive path: no `AttributeValue` boxing. Custom enrichments through `LogEnrichment` go through `record.copy(attributes = ...)`, which does allocate an `Attributes` merge. For very hot paths, prefer the built-in types.

### Multiple outputs simultaneously

Every call to `log.writer(formatter, writer)` adds another output. The state is maintained inside the `log` object and composited into the active logger:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import java.io.PrintWriter

final class AppFileWriter(path: String) extends LogWriter {
  private val out = new PrintWriter(path)
  def write(content: CharSequence): Unit = out.println(content)
  override def flush(): Unit             = out.flush()
  override def close(): Unit             = out.close()
}

// Setup at startup; all three are active simultaneously
log.writer(TextLogFormatter, new AppFileWriter("logs/app.log"))
log.writer(JsonLogFormatter, new AppFileWriter("logs/app.jsonl"))
// The OTEL exporter is a LogRecordProcessor, added via LoggerProvider (see Part 5)
```

Internally, `log.writer(...)` creates a `FormattedLogRecordProcessor` (which pairs a formatter with a writer) and calls `rebuildState()`, which reconstructs the active `Logger` with the accumulated processor list. The original provider-based processors are preserved alongside the writer processors.

To remove all file writers:

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.clearWriters()
```

This shuts down each `FormattedLogRecordProcessor` cleanly and reverts to processor-only routing.

### Log output format comparison

The same call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.warn("slow query", "table" -> "orders", "duration_ms" -> 450L)
```

The text formatter writes one line per record:
```
2026-05-14T09:30:00.123Z WARN  [PaymentService.handleRequest:87] slow query {table="orders", duration_ms=450}
```

The JSON formatter writes the same record in the OTLP log data model:
```json
{"timeUnixNano":"1747216200123000000","severityNumber":13,"severityText":"WARN","body":{"stringValue":"slow query"},"attributes":[{"key":"code.filepath","value":{"stringValue":"src/main/scala/com/example/PaymentService.scala"}},{"key":"code.namespace","value":{"stringValue":"com.example.PaymentService"}},{"key":"code.function","value":{"stringValue":"handleRequest"}},{"key":"code.lineno","value":{"intValue":"87"}},{"key":"table","value":{"stringValue":"orders"}},{"key":"duration_ms","value":{"intValue":"450"}}]}
```

The JSON format follows the OTLP log data model directly, making it compatible with any OTLP-aware log processor (Grafana Loki, OpenTelemetry Collector, etc.).

The text formatter caches the timestamp prefix per second (the part before milliseconds) using two `@volatile` fields, so it avoids repeated date formatting work on high-throughput paths.

### Testing with telemetry

For unit tests, you usually want to assert that specific log calls happened. The cleanest approach is a custom `LogRecordProcessor`:

```scala
import zio.blocks.telemetry._
import scala.collection.concurrent.TrieMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturingLogProcessor extends LogRecordProcessor {
  val records = new CopyOnWriteArrayList[LogRecord]()

  def onEmit(record: LogRecord): Unit = records.add(record)
  def shutdown(): Unit = ()
  def forceFlush(): Unit = ()
}

// In your test setup:
val capturing = new CapturingLogProcessor()

val loggerProvider = LoggerProvider.builder
  .addLogRecordProcessor(capturing)
  .build()

log.install(loggerProvider.get("test"))

// Call the code under test
MyService.processPayment(...)

// Assert on the captured records
val warns = capturing.records.toArray.collect {
  case r: LogRecord if r.severity == Severity.Warn => r
}
assert(warns.exists(_.body.value.contains("payment")))
```

Don't clean up the global logger with `log.removeAll()`. It silences logging, and in version 0.0.56 a later `log.addProcessor` or `log.writer` does not bring it back, so every test after it captures nothing (a known bug in that version). Instead, install a fresh capturing logger with `log.install` at the start of each test, and run tests that share the global logger one at a time. Passing a `Logger` to the code under test avoids the shared state altogether. [How to Test Code That Logs, Traces and Records Metrics](./test-telemetry.md) shows both patterns.

---

## Part 3: Distributed Tracing Deep Dive

This part covers how spans are created, sampled, propagated and tested.

### Trace lifecycle

When you call `tracer.span("name") { span => ... }`, here's the complete sequence:

1. **Read parent context.** `contextStorage.get()` returns `Option[SpanContext]`. If a span is already active on this thread (set by a parent `span` call), it becomes the parent.

2. **Sampler decision.** `sampler.shouldSample(...)` is called with the parent context, new trace ID bits, span name, and kind. The result is one of `Drop`, `RecordOnly`, or `RecordAndSample`.

3. **Drop path.** If `Drop`, your block runs with `Span.NoOp`. All `setAttribute`, `addEvent`, `setStatus` calls are no-ops, and no processor is notified. A dropped span is still cheap but not free: a fresh `SpanId` and `SpanContext` are allocated and scoped so that nested spans and log records stay on the same trace.

4. **Record path.** A `RecordingSpan` is built with a new `SpanContext`. The span gets:
   - The parent's `traceIdHi`/`traceIdLo` if a valid parent exists, or a fresh random pair
   - A fresh random `spanId`
   - `traceFlags` set to `sampled` (for `RecordAndSample`) or `none` (for `RecordOnly`)

5. **Processors on start.** Each `SpanProcessor.onStart(span)` is called.

6. **Context storage scoping.** `contextStorage.scoped(Some(span.spanContext)) { f(span) }` runs your block. While your block is executing, any inner `span` calls will see this span as their parent.

7. **End and export.** When the block exits (normally or via exception), `span.end()` records the end timestamp. Then `SpanProcessor.onEnd(spanData)` is called on each processor. The OTLP exporter's `onEnd` enqueues the `SpanData` in the `BatchProcessor`.

```
tracer.span("checkout") { span =>
    |
    +-- sampler.shouldSample() -> RecordAndSample
    |
    +-- SpanProcessor.onStart(span)  [each processor]
    |
    +-- contextStorage.scoped(Some(span.spanContext)) {
    |       tracer.span("validate-cart") { ... }  // sees checkout as parent
    |       tracer.span("charge-card")  { ... }   // sees checkout as parent
    |   }
    |
    +-- span.end()
    |
    +-- SpanProcessor.onEnd(spanData)  [each processor]
         |
         +-- OtlpJsonTraceExporter.onEnd(spanData)
              |
              +-- BatchProcessor.enqueue(spanData)
                   |
                   (background flush every 5s, draining maxBatchSize per request)
                   |
                   +-- HTTP POST /v1/traces to OTLP collector
```

### Sampling strategies

**`AlwaysOnSampler`** returns `RecordAndSample` for every span. Use this in development or when you have low enough traffic that 100% sampling is affordable. It's the default.

**`AlwaysOffSampler`** returns `Drop` for every span. Useful when you want tracing code paths to exist but not actually produce data, for instance in a test that doesn't care about traces.

**`ParentBasedSampler(root)`** defers to the parent's sampling decision. If there's no parent, it falls back to `root`. If there is a parent:
- Parent sampled (`traceFlags.isSampled == true`) → `RecordAndSample`
- Parent not sampled → `Drop`

This is the right choice for most production services. The gateway or entry service decides sampling, and all downstream services follow automatically. Configure it with `AlwaysOnSampler` as the root if you want the gateway to sample everything, or with a `RateLimitedSampler` (if you implement one) for head-based sampling:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracerProvider = TracerProvider.builder
  .setSampler(ParentBasedSampler(root = AlwaysOnSampler))
  .addSpanProcessor(SpanProcessor.noop)  // your exporter here
  .build()

trace.install(tracerProvider)
```

### SpanContext internals

```scala
final case class SpanContext(
  traceIdHi: Long,    // high 64 bits of the 128-bit trace ID
  traceIdLo: Long,    // low  64 bits of the 128-bit trace ID
  spanId: SpanId,     // AnyVal wrapping a Long
  traceFlags: TraceFlags, // AnyVal wrapping a Byte
  traceState: String,
  isRemote: Boolean
)
```

The 128-bit trace ID is stored as two `Long` fields rather than a `UUID` object. A `UUID` would be 32 bytes of heap allocation per span, plus GC pressure. Two `Long` fields on a case class cost nothing beyond the case class itself, and they're JIT-friendly (no dereference).

A fresh trace ID is generated by `TraceId.random()`, which draws two `Long`s from `scala.util.Random`, retrying in the vanishingly unlikely case that both come out zero (an all-zero trace ID is invalid by the spec).

`SpanId` is an `AnyVal` wrapping a single `Long`, generated the same way and likewise retried if it comes out zero. At runtime on the JVM, `AnyVal`s are unboxed wherever possible, so a `SpanId` in a `SpanContext` field costs nothing extra.

`traceIdHex` is computed on demand by `TraceId.toHex(traceIdHi, traceIdLo)`, which formats the two longs into 32 hex characters. No caching: hex formatting is only needed when building headers or log output.

### Context propagation across process boundaries

Here's a complete round trip. The server receives an HTTP request with W3C `traceparent`, continues the trace, and the client injects context into an outbound call.

**HTTP server (receiving a trace):**

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

// Simulate incoming HTTP headers
val incomingHeaders = Map(
  "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
)

// Extract SpanContext from the traceparent header
val parentCtx: Option[SpanContext] =
  W3CTraceContextPropagator.extract(incomingHeaders, (m, k) => m.get(k))

// Wire the extracted context as the parent by scoping it in ContextStorage
// A provider's own contextStorage is internal, so create one and pass it in
val contextStorage = ContextStorage.create[Option[SpanContext]](None)

val tracerProvider = TracerProvider.builder
  .addSpanProcessor(SpanProcessor.noop)  // your exporter here
  .setContextStorage(contextStorage)
  .build()

trace.install(tracerProvider)

// Take the tracer after trace.install: it binds to the provider installed when it runs
val tracer = trace.get("api-service")

contextStorage.scoped(parentCtx) {
  tracer.span("handle-checkout", SpanKind.Server) { span =>
    span.setAttribute("http.method", "POST")
    span.setAttribute("http.route", "/checkout")
    // Your handler logic here
    ()  // your handler logic here
  }
}
```

**HTTP client (injecting trace into outbound call):**

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val tracer = trace.get("api-service")

tracer.span("call-inventory", SpanKind.Client) { span =>
  // Inject the current span's context into outbound headers
  val headers = W3CTraceContextPropagator.inject(
    span.spanContext,
    Map.empty[String, String],
    (carrier, k, v) => carrier + (k -> v)
  )

  // headers now contains: "traceparent" -> "00-<traceId>-<spanId>-01"
  headers  // hand these to your HTTP client
}
```

The `traceparent` header encodes: `{version}-{traceId32hex}-{spanId16hex}-{flags2hex}`. The inventory service receives this, calls `extract`, gets back a `SpanContext` with `isRemote = true`, and starts its own child span under the same trace.

For Zipkin/B3 compatibility, swap `W3CTraceContextPropagator` for `B3Propagator.single` or `B3Propagator.multi`.

### Testing traces

The default `trace` provider stores spans in an in-memory processor:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Reset before each test
trace.clearSpans()

// Call code under test
val tracer = trace.get("test")
tracer.span("outer") { _ =>
  tracer.span("inner") { span =>
    span.setAttribute("result", "ok")
  }
}

// Inspect what was recorded
val spans = trace.collectedSpans

assert(spans.size == 2)
val inner = spans.find(_.name == "inner").get
assert(inner.attributes.get(AttributeKey.string("result")) == Some("ok"))

// Check parent-child relationship
val outer = spans.find(_.name == "outer").get
assert(inner.parentSpanContext.spanId == outer.spanContext.spanId)
```

`trace.collectedSpans` returns a `List[SpanData]` in the order spans ended. No setup needed; this works out of the box.

### Span-log correlation explained

When you log inside an active span, the logger automatically attaches `traceId` and `spanId` to the record. This works because both the `Logger` and the `Tracer` share the same `ContextStorage[Option[SpanContext]]`.

At provider creation time:

```scala
// TracerProvider.builder.build() falls back to ContextStorage.defaultSpanContextStorage
// unless you pass one; here we create an explicit instance to share
val cs = ContextStorage.create[Option[SpanContext]](None)
val tracerProvider = TracerProvider.builder
  .setContextStorage(cs)
  .build()

// LoggerProvider can share the same ContextStorage
val loggerProvider = LoggerProvider.builder
  .setContextStorage(cs)  // <-- same instance
  .build()
```

When `tracer.span(...)` runs, it calls `contextStorage.scoped(Some(span.spanContext)) { ... }`. This scopes the span context for the duration of the block using `ScopedValue` on the JVM. On Scala.js there are no threads, so the storage is a plain mutable variable saved and restored around the block. Inside that block, `logger.currentSpanContext()` reads from the same storage and sees the active span. No explicit linkage code at the call site.

This works out of the box with the global singletons: a provider built without an explicit `ContextStorage` falls back to the same `ContextStorage.defaultSpanContextStorage` singleton, so the default tracer and the default logger already read and write the same storage. You only need to pass one explicitly when you want the opposite — isolating correlation between test cases, or fitting a custom runtime.

---

## Part 4: Metrics Deep Dive

This part covers choosing, pre-binding and collecting metric instruments.

### Instrument selection guide

| What you're measuring | Instrument | Example |
|---|---|---|
| Monotonically increasing total | `Counter` | Total requests served |
| Value that can go up or down | `UpDownCounter` | Active connections, queue depth |
| Distribution of values | `Histogram` | Request latency, payload size |
| Current point-in-time value | `Gauge` | CPU usage, memory used, pool size |

More specifically:

- Use **`Counter`** when you're counting things that only ever increase: requests processed, errors encountered, bytes sent. Negative deltas are silently ignored.
- Use **`UpDownCounter`** when the value can decrease: pending jobs in a queue (enqueue = +1, dequeue = -1), active WebSocket connections.
- Use **`Histogram`** when you care about the distribution, not just the total: latency percentiles (p50, p95, p99), request sizes. Histograms record min, max, sum, count, and configurable bucket counts.
- Use **`Gauge`** when you're sampling the current value of something external: a JVM heap usage check, a pool size read from a connection pool object. Last-write wins.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val requests     = metric.counter("http.requests.total")
val activeConns  = metric.upDownCounter("http.connections.active")
val latency      = metric.histogram("http.request.duration_ms")
val heapUsed     = metric.gauge("jvm.heap.used_bytes")

// Counter: only increases
requests.add(1, "method" -> "GET", "status" -> "200")

// UpDownCounter: tracks net change
activeConns.add(1)   // connection opened
activeConns.add(-1)  // connection closed

// Histogram: records the value into the appropriate bucket
latency.record(42.5, "method" -> "GET")

// Gauge: always reflects the current value
heapUsed.record(Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory())
```

### Labeled instruments with pre-bound attributes

When you record against the same label combination at high frequency, pre-binding avoids the `Attributes` lookup on every call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val requests = metric.counter("http.requests.total")

// Bind once, typically at initialization
val getOk   = requests.bind(Attributes.builder.put("method", "GET").put("status", "200").build)
val get404  = requests.bind(Attributes.builder.put("method", "GET").put("status", "404").build)
val postOk  = requests.bind(Attributes.builder.put("method", "POST").put("status", "201").build)

// Hot path: just add to the pre-bound LongAdder
def onGetOk():  Unit = getOk.add(1)
def onGet404(): Unit = get404.add(1)
def onPostOk(): Unit = postOk.add(1)
```

A `BoundCounter` holds a direct reference to the `LongAdder` for that attribute set. The call `add(1)` is just `adder.add(1)`: no map lookup, no attribute construction.

The same pattern works for histograms and gauges:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val latency    = metric.histogram("http.request.duration_ms")
val getLatency = latency.bind(Attributes.builder.put("method", "GET").build)

val poolSize     = metric.gauge("db.pool.size")
val primaryPool  = poolSize.bind(Attributes.builder.put("pool", "primary").build)

// Hot paths
def recordGetLatency(ms: Double): Unit = getLatency.record(ms)
def updatePrimaryPoolSize(n: Double): Unit = primaryPool.record(n)
```

### MetricData and collection

`Counter.collect()`, `Histogram.collect()`, and `Gauge.collect()` snapshot the current state of the instrument without resetting it:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val requests = metric.counter("http.requests.total")
requests.add(42, "method" -> "GET", "status" -> "200")
requests.add(7,  "method" -> "POST", "status" -> "201")

val data = requests.collect()
// data: MetricData.SumData(List(
//   SumDataPoint(Attributes{method="GET",status="200"}, 0L, nowNanos, 42),
//   SumDataPoint(Attributes{method="POST",status="201"}, 0L, nowNanos, 7)
// ))
// startTimeNanos is always 0 — the instruments do not track a start time.
```

Each `SumDataPoint` contains the full attribute set plus the accumulated value. `MetricData` doesn't carry the instrument's name, so to export a snapshot, pair each one with its name, description and unit in a `NamedMetric` and encode the batch with `OtlpJsonEncoder.encodeMetrics`:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val requests = metric.counter("http.requests.total")
val latency  = metric.histogram("http.request.duration_ms")
val poolSize = metric.gauge("db.pool.size")

def snapshot(): Seq[NamedMetric] = Seq(
  NamedMetric("http.requests.total", "Total HTTP requests", "1", requests.collect()),
  NamedMetric("http.request.duration_ms", "Request duration", "ms", latency.collect()),
  NamedMetric("db.pool.size", "DB pool size", "1", poolSize.collect())
)

val payload: Array[Byte] =
  OtlpJsonEncoder.encodeMetrics(snapshot(), Resource.empty, InstrumentationScope("my-service"))
```

Nothing collects metrics in the background: a snapshot happens only when you call `collect()`. Part 5 takes the snapshot on the same `PlatformExecutor` schedule that flushes the other signals.

### Thread safety internals

| Instrument | Internal structure | Thread safety |
|---|---|---|
| `Counter` | `ConcurrentHashMap[Attributes, LongAdder]` | Lock-free reads, CAS on first write per label set |
| `UpDownCounter` | same as Counter, allows negative | same |
| `Gauge` | `ConcurrentHashMap[Attributes, AtomicLong]` | Lock-free: `AtomicLong.set(doubleToLongBits(v))` |
| `Histogram` | `ConcurrentHashMap[Attributes, State]` + `ReentrantLock` per State | `ReentrantLock` per label set during `record` and `collect` |

`LongAdder` is the right choice for `Counter` because it reduces contention under high concurrency: rather than competing for a single `AtomicLong`, threads maintain per-stripe counters and sum them on read. At high add rates, `LongAdder` outperforms `AtomicLong.addAndGet` significantly.

`Histogram` needs a lock because updating count, sum, min, max, and bucket counts is a multi-field operation. No single atomic primitive covers all of them.

---

## Part 5: Production Setup

This part wires an exporter built from the public OTLP pieces into a production service and covers tuning and shutdown.

### Full production wiring

The module's built-in OTLP exporters (`OtlpJsonTraceExporter`, `OtlpJsonLogExporter` and `OtlpJsonMetricExporter`) are `private[otel]`, so user code can't construct them. The pieces they are built from are public: `OtlpJsonEncoder` turns spans, log records and metrics into OTLP JSON, `HttpSender` posts the bytes, and `ExportResult` says whether the collector accepted them. [Building an Exporter](../reference/telemetry/otel/custom-exporter.md) covers each piece; this section wires them into a service.

:::warning[Don't declare the library's package]
Scala lets your own file declare `package zio.blocks.telemetry.otel` and call the `private[otel]` exporters directly. That is an unsupported workaround, not API: their constructors can change or disappear in any release, without notice. Build on the public pieces instead.
:::

`OtlpBuffer` is the exporter. It queues one signal, drops the oldest item once `ExporterConfig#maxQueueSize` is reached, and on each flush sends what it holds in chunks of `ExporterConfig#maxBatchSize`. `Telemetry` creates one buffer per signal, feeds the span and log buffers from a `SpanProcessor` and a `LogRecordProcessor`, installs the providers, and flushes all three buffers every `ExporterConfig#flushIntervalMillis` on a `PlatformExecutor`:

```scala mdoc:silent
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

// Queues one signal and posts it as OTLP JSON to `<endpoint><path>`.
final class OtlpBuffer[A](
  config: ExporterConfig,
  sender: HttpSender,
  path: String,
  encode: Seq[A] => Array[Byte]
) {
  private val url     = config.endpoint + path
  private val headers = config.headers + ("Content-Type" -> "application/json")
  private val queue   = new ArrayBlockingQueue[A](config.maxQueueSize)

  def add(item: A): Unit =
    while (!queue.offer(item)) queue.poll() // full: drop the oldest item

  def flush(): Unit = {
    val drained = new java.util.ArrayList[A]()
    queue.drainTo(drained)
    drained.asScala.toSeq.grouped(config.maxBatchSize).foreach(send)
  }

  private def send(batch: Seq[A]): Unit = {
    val result =
      try ExportResult.fromHttpResponse(sender.send(url, headers, encode(batch)))
      catch {
        case NonFatal(e) => ExportResult.Failure(retryable = true, message = String.valueOf(e))
      }
    result match {
      case ExportResult.Success             => ()
      case ExportResult.Failure(_, message) => System.err.println(s"OTLP export to $url failed: $message")
    }
  }
}

final class Telemetry(config: ExporterConfig, resource: Resource, metrics: () => Seq[NamedMetric]) {
  private val scope    = InstrumentationScope("my-service")
  private val sender   = HttpSender.jdk(config.timeout)
  private val executor = PlatformExecutor.create()

  private val spans =
    new OtlpBuffer[SpanData](config, sender, "/v1/traces", OtlpJsonEncoder.encodeTraces(_, resource, scope))
  private val logs =
    new OtlpBuffer[LogRecord](config, sender, "/v1/logs", OtlpJsonEncoder.encodeLogs(_, resource, scope))
  private val metricBuffer =
    new OtlpBuffer[NamedMetric](config, sender, "/v1/metrics", OtlpJsonEncoder.encodeMetrics(_, resource, scope))

  // Shared ContextStorage so log records inside spans get traceId/spanId
  private val contextStorage = ContextStorage.create[Option[SpanContext]](None)

  // --- Tracing ---
  private val spanProcessor = new SpanProcessor {
    def onStart(span: Span): Unit       = ()
    def onEnd(spanData: SpanData): Unit = spans.add(spanData)
    def forceFlush(): Unit              = spans.flush()
    def shutdown(): Unit                = spans.flush()
  }

  val tracerProvider: TracerProvider = TracerProvider.builder
    .setSampler(ParentBasedSampler(root = AlwaysOnSampler))
    .addSpanProcessor(spanProcessor)
    .setResource(resource)
    .setContextStorage(contextStorage)
    .build()

  trace.install(tracerProvider)

  // --- Logging ---
  private val logProcessor = new LogRecordProcessor {
    def onEmit(logRecord: LogRecord): Unit = logs.add(logRecord)
    def forceFlush(): Unit                 = logs.flush()
    def shutdown(): Unit                   = logs.flush()
  }

  val loggerProvider: LoggerProvider = LoggerProvider.builder
    .addLogRecordProcessor(logProcessor)
    .setContextStorage(contextStorage) // same storage for correlation
    .setResource(resource)
    .build()

  log.install(loggerProvider.get("my-service"), minSeverity = Severity.Info)

  // Optional: also emit JSON on stderr (a file needs your own LogWriter, see Part 2)
  log.writer(JsonLogFormatter, StderrWriter)

  // --- Metrics ---
  val meterProvider: MeterProvider = MeterProvider.builder
    .setResource(resource)
    .build()

  metric.install(meterProvider)

  private def flushMetrics(): Unit = {
    metrics().foreach(metricBuffer.add)
    metricBuffer.flush()
  }

  // --- Periodic flush ---
  private val scheduledFlush = executor.schedule(config.flushIntervalMillis, config.flushIntervalMillis, TimeUnit.MILLISECONDS) { () =>
    try {
      spans.flush()
      logs.flush()
      flushMetrics()
    } catch {
      case NonFatal(e) => System.err.println(s"OTLP flush failed: $e") // an escaping exception stops the schedule
    }
  }

  // Call at JVM shutdown, in the order explained under "Graceful shutdown order"
  def shutdown(): Unit = {
    scheduledFlush.cancel(false) // no new periodic flushes
    executor.shutdown()
    executor.executor.awaitTermination(config.timeout.toMillis, TimeUnit.MILLISECONDS) // let a running flush finish
    loggerProvider.shutdown()    // final log flush
    tracerProvider.shutdown()    // final span flush
    flushMetrics()               // last metric snapshot
    meterProvider.shutdown()
    sender.shutdown()
    log.clearWriters()
  }
}

object Telemetry {
  def fromEnv(metrics: () => Seq[NamedMetric]): Telemetry = {
    val config = ExporterConfig(
      endpoint            = sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
      headers             = parseHeaders(sys.env.getOrElse("OTEL_EXPORTER_OTLP_HEADERS", "")),
      timeout             = java.time.Duration.ofSeconds(10),
      maxQueueSize        = 4096,
      maxBatchSize        = 512,
      flushIntervalMillis = 5000L
    )

    val resource = Resource.create(
      Attributes.builder
        .put("service.name", sys.env.getOrElse("OTEL_SERVICE_NAME", "my-service"))
        .put("service.version", sys.env.getOrElse("SERVICE_VERSION", "unknown"))
        .put("deployment.environment", sys.env.getOrElse("ENVIRONMENT", "development"))
        .build
    )

    new Telemetry(config, resource, metrics)
  }

  private def parseHeaders(raw: String): Map[String, String] =
    if (raw.isEmpty) Map.empty
    else
      raw
        .split(',')
        .flatMap { pair =>
          pair.split('=') match {
            case Array(k, v) => Some(k.trim -> v.trim)
            case _           => None
          }
        }
        .toMap
}
```

`OtlpBuffer` reports a failed batch on standard error and drops it. To retry the failures that `ExportResult` marks retryable, and for the other duties of the internal batch processor, see [What You Are Reimplementing](../reference/telemetry/otel/custom-exporter.md#what-you-are-reimplementing). `MetricData` doesn't carry the instrument's name, so `Telemetry` takes a function that pairs each instrument's snapshot with its name in a `NamedMetric`.

The application supplies that function from the instruments it creates, and shuts telemetry down when the JVM stops:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

object AppMetrics {
  val requests = metric.counter("http.requests.total")
  val latency  = metric.histogram("http.request.duration_ms")

  def snapshot(): Seq[NamedMetric] = Seq(
    NamedMetric("http.requests.total", "Total HTTP requests", "1", requests.collect()),
    NamedMetric("http.request.duration_ms", "Request duration", "ms", latency.collect())
  )
}

object Main {
  def main(args: Array[String]): Unit = {
    val telemetry = Telemetry.fromEnv(() => AppMetrics.snapshot())
    sys.addShutdownHook(telemetry.shutdown())

    log.info("application started")
    AppMetrics.requests.add(1, "method" -> "GET", "status" -> "200")
    // ... your app
  }
}
```

### ExporterConfig tuning guide

The default values work for most services. Here's when to change them:

**High-throughput services (>1000 RPS):**
```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

ExporterConfig(
  maxQueueSize        = 8192,   // larger buffer for spikes
  maxBatchSize        = 1024,   // send bigger payloads less often
  flushIntervalMillis = 5000L   // keep at 5s; collector handles large batches fine
)
```

**Low-latency services (real-time data required):**
```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

ExporterConfig(
  maxQueueSize        = 1024,
  maxBatchSize        = 128,    // smaller batches = lower end-to-end latency
  flushIntervalMillis = 1000L   // flush more frequently
)
```

**Development / debugging:**
```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

ExporterConfig(
  maxQueueSize        = 256,
  maxBatchSize        = 32,
  flushIntervalMillis = 500L    // see data almost immediately
)
```

`ExporterConfig#maxQueueSize` and `ExporterConfig#maxBatchSize` interact: if records arrive faster than you export, the queue fills, and once it is full the oldest items are dropped. Set `ExporterConfig#maxQueueSize` to at least four times `ExporterConfig#maxBatchSize` to absorb transient spikes without dropping.

`timeout` is per HTTP request. If your OTLP collector is slow or flaky, a low timeout means you drop data rather than blocking. 10 seconds is usually right for LAN deployments; 30 seconds for cross-region.

### BatchProcessor behavior

The built-in exporters run on an internal `BatchProcessor`. `OtlpBuffer` in the wiring above copies its queue, chunking and interval rules but not its retries. The internal processor behaves like this:

It schedules its flush on the `ScheduledExecutorService` it is handed — `PlatformExecutor.create()` supplies a single-threaded scheduled pool whose threads are virtual (`Executors.newScheduledThreadPool(1, Thread.ofVirtual()…factory())`). This means:

- The scheduled flush (every `ExporterConfig#flushIntervalMillis`) runs on a virtual thread
- Export HTTP calls and their retry sleeps run on that same virtual thread, so backoff doesn't pin a platform thread — but the pool has one thread, so a retrying export delays every other flush sharing the executor. Give a high-volume signal its own `PlatformExecutor` if that matters.
- A full batch does not trigger a send: `ExporterConfig#maxBatchSize` only chunks what a flush drains. Data leaves on the interval, on `forceFlush()`, or on `shutdown()`.
- Queue overflow drops the oldest item, prints to stderr, and continues

The retry schedule uses exponential backoff: attempt 0 waits 1s, attempt 1 waits 2s, attempt 2 waits 4s... up to 30s maximum. `maxRetries` (default 5) counts retries, so a permanently failing batch is attempted 6 times before it is dropped. Non-retryable failures (e.g., 400 Bad Request from the collector) are dropped immediately.

On `shutdown()`, `BatchProcessor` cancels the periodic flush and does a final synchronous flush of all pending items. Retries are skipped once shutdown has started — a failing batch is dropped with a message on stderr rather than delaying exit. The executor is not owned by the processor; shut down the `PlatformExecutor` yourself, after the providers.

### Graceful shutdown order

Shut down in this order; `Telemetry#shutdown` in the wiring above does steps 2 to 6:

1. **Stop accepting new work.** Close your HTTP listener or message consumer.
2. **Stop the periodic flush.** Cancel the scheduled task, shut down the `PlatformExecutor` and wait for a running flush to finish, so it doesn't race the final one.
3. **`loggerProvider.shutdown()`.** Calls the log processor's `shutdown`, which sends the remaining log records.
4. **`tracerProvider.shutdown()`.** Calls the span processor's `shutdown`, which sends the remaining spans.
5. **Take a last metric snapshot, then `meterProvider.shutdown()`.** Sends the final cumulative values.
6. **`HttpSender#shutdown`.** Releases the sender.

The built-in exporters need a different order: their `BatchProcessor` flushes and retries on the executor, so shut the executor down last, after the providers.

### Environment-based configuration

The standard OTLP environment variables map naturally:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val config = ExporterConfig(
  endpoint = sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
  headers  = sys.env.get("OTEL_EXPORTER_OTLP_HEADERS")
    .map(parseOtlpHeaders)
    .getOrElse(Map.empty),
  timeout  = sys.env.get("OTEL_EXPORTER_OTLP_TIMEOUT")
    .map(s => java.time.Duration.ofMillis(s.toLong))
    .getOrElse(java.time.Duration.ofSeconds(30))
)

val serviceName = sys.env.getOrElse("OTEL_SERVICE_NAME", "my-service")

// OTLP headers format: "key1=value1,key2=value2"
def parseOtlpHeaders(raw: String): Map[String, String] =
  raw.split(',').flatMap(_.split('=') match {
    case Array(k, v) => Some(k.trim -> v.trim)
    case _           => None
  }).toMap
```

For minimum log level:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val minLevel = sys.env.get("LOG_LEVEL").flatMap {
  case "TRACE" => Some(Severity.Trace)
  case "DEBUG" => Some(Severity.Debug)
  case "INFO"  => Some(Severity.Info)
  case "WARN"  => Some(Severity.Warn)
  case "ERROR" => Some(Severity.Error)
  case _       => None
}.getOrElse(Severity.Info)

log.install(LoggerProvider.builder.build().get("my-service"), minSeverity = minLevel)
```

---

## Part 6: Integration Patterns

This part shows how to connect telemetry to HTTP frameworks, Java logging and Scala.js.

### With HTTP frameworks

A typical middleware pattern extracts incoming trace context, starts a server span, and injects context into outbound calls. Here's the conceptual shape (adapting to your specific HTTP library):

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

// Incoming request middleware
// `storage` is the ContextStorage passed to the installed provider's setContextStorage
def traceIncoming[Req, Resp](
  request: Req,
  getHeader: (Req, String) => Option[String],
  storage: ContextStorage[Option[SpanContext]],
  handle: Req => Resp
): Resp = {
  val parentCtx = W3CTraceContextPropagator.extract(request, getHeader)
  val tracer    = trace.get("http-server")

  // Scope the remote parent in the tracer's storage,
  // then start a child span under it
  storage.scoped(parentCtx) {
    tracer.span("http.request", SpanKind.Server) { span =>
      // Attach standard HTTP attributes
      // ... then run the handler
      handle(request)
    }
  }
}

// Outbound call wrapper
def traceOutgoing[Resp](
  name: String,
  call: Map[String, String] => Resp
): Resp = {
  val tracer = trace.get("http-client")
  tracer.span(name, SpanKind.Client) { span =>
    val headers = W3CTraceContextPropagator.inject(
      span.spanContext,
      Map.empty[String, String],
      (c, k, v) => c + (k -> v)
    )
    call(headers)
  }
}
```

For the incoming case, the remote parent has to be scoped in the same `ContextStorage` the tracer reads. A provider's own `contextStorage` field is library-private, so build one with `ContextStorage.create` and hand it to the builder via `setContextStorage` — as the server example above does — then wrap the handler in `contextStorage.scoped(parentCtx) { … }`. [How to Correlate Logs and Traces Across Services](./correlate-logs-and-traces.md) builds this pattern end to end with the http-model `Request`, including the log lines of both services.

### With existing Java logging (SLF4J/JUL)

There's no bridge today, but the shape would be a `SLF4JLogRecordProcessor` that consumes `LogRecord` from `zio-blocks-telemetry` and forwards it to the SLF4J backend, or vice versa. If you need Java library logs captured alongside your Scala logs, the simplest current approach is a Logback appender that writes to your file alongside `zio-blocks-telemetry`'s file output.

### Cross-platform considerations

The core telemetry module (`zio-blocks-telemetry`) compiles for JVM and Scala.js. What works on both:

- `log.*`, `trace.*`, `metric.*`
- `TracerProvider`, `LoggerProvider`, `MeterProvider` builders
- `Sampler`, `SpanContext`, `Attributes`, `MetricData`
- `TextLogFormatter`, `JsonLogFormatter`
- All the in-memory processors and default providers

JVM-only:
- File output: a custom `LogWriter` backed by `java.io` or `FileChannel` works on the JVM only
- `ContextStorage` using `ScopedValue`: final in JDK 25 and a preview API in JDK 21 to 24, which the module calls without needing `--enable-preview`; on Scala.js, a simpler mutable-variable implementation is used
- `PlatformExecutor`: uses `ScheduledExecutorService` with virtual threads
- The entire `zio-blocks-telemetry-otel` module (OTLP HTTP export, `BatchProcessor`)

If you're writing cross-platform code that uses telemetry, keep your call sites in the shared module and put all provider wiring in JVM-specific code.

---

## Part 7: FAQ / Troubleshooting

**"Why don't I see any log output?"**

Three common causes:

1. The global minimum level is filtering it out. The default floor is `Severity.Trace` (severity number 1), so everything passes. If you called `log.install(logger, minSeverity = Severity.Info)` or `log.setMinSeverity(Severity.Info)`, then trace and debug calls are suppressed.

2. The logger was installed but the processor chain is empty. Verify your `LoggerProvider` builder has at least one processor:
   ```scala
   LoggerProvider.builder
     .addLogRecordProcessor(new ConsoleLogRecordProcessor)  // don't forget this
     .build()
   ```

3. A namespace override is suppressing your package. Check for `log.setMinSeverity(...)` calls.

**"Why is my log call not compiling?"**

You're probably passing a type that has no `LogEnrichment` instance. Common culprits:
- `Float` (convert to `Double` with `.toDouble`)
- `Short`, `Byte`, `Char` (convert to `Long` or `String`)
- `UUID`, `Instant` (provide a `LogEnrichment[(String, YourType)]` implicit)
- Custom case classes (same: provide an implicit)

The compiler error will say something like `no implicit value for LogEnrichment[(String, UUID)]`.

**"How do I log a UUID, Instant, or custom type?"**

Define an implicit in your package object or companion:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import java.util.UUID

implicit val uuidEnrichment: LogEnrichment[(String, UUID)] =
  (record, kv) => record.copy(
    attributes = record.attributes ++ Attributes.of(AttributeKey.string(kv._1), kv._2.toString)
  )
```

Then `log.info("action", "id" -> someUuid)` compiles and works.

**"What's the overhead of a disabled log level?"**

Exactly one volatile read (the global minimum level) plus, if the level passes that check, one array scan through the namespace overrides (usually empty, so O(1)). Both happen before any argument is evaluated. No string formatting, no object allocation, no method calls on your arguments.

**"Can I use this with ZIO, Cats Effect, or any other effect system?"**

Yes. The library is effect-free. `log.info(...)` is a plain synchronous call. Call it from anywhere: inside `ZIO.succeed`, inside `IO.apply`, inside a `Future`, inside a background thread. The only threading concern is that `ContextStorage` uses `ScopedValue` for span correlation, which is scoped to the current call stack. If you hop threads between starting a span and logging inside it, the log may not see the active span context. Most effect systems either stay on one thread or have ways to propagate context.

**"Why are the OTEL exporter classes private[otel]?"**

The module doesn't offer them as public API yet, so their constructors can change between releases. Code that declares the `zio.blocks.telemetry.otel` package can still reach them, but that is an unsupported workaround. To export telemetry today, build an exporter from the public `OtlpJsonEncoder`, `HttpSender` and `ExportResult`, as Part 5 does; [Building an Exporter](../reference/telemetry/otel/custom-exporter.md) covers each piece. Everything downstream uses the installed global providers and never needs the exporter types.

**"How do I test my logging and tracing?"**

For logging: use a custom `LogRecordProcessor` that stores records (see "Testing with telemetry" in Part 2), install it via `log.install(...)`, run your code, assert on the captured records.

For tracing: use `trace.collectedSpans` and `trace.clearSpans()`. The default in-memory processor accumulates spans automatically.

**"What happens when the export queue fills up?"**

When `queueSize` exceeds `ExporterConfig#maxQueueSize`, `BatchProcessor#enqueue` polls the head of the queue (the oldest item), decrements the size, and prints a warning to stderr:

```
[zio-blocks-telemetry] BatchProcessor queue full (2048). Dropping oldest item.
```

The new item is still enqueued. This is a best-effort, head-dropping strategy. You won't get backpressure; callers are never blocked. If you see frequent drops, increase `maxQueueSize` or decrease `flushIntervalMillis`.

**"Is this virtual-thread safe?"**

Yes, in the sense that a span's context is bound to the current call stack whether that stack belongs to a platform or a virtual thread: `ContextStorage` on the JVM uses `ScopedValue`, and reads and writes from the running thread are safe. Inheritance is narrower than an `InheritableThreadLocal`, though — a `ScopedValue` binding reaches a child thread only when the child is forked from a `StructuredTaskScope`. A plain `Thread.ofVirtual().start(...)` inside a span's block sees no span context, so records logged there are not correlated; open the span inside the child, or pass the `SpanContext` across explicitly. The `PlatformExecutor` that drives OTLP export runs its tasks on virtual threads, so retry sleeps don't pin platform threads.

---

## Where to Go Next

- **Complete API reference:** [Telemetry Reference](../reference/telemetry/index.md) for all types, methods, and parameter documentation
- **Installation and quick start:** same reference doc, Installation section
- **Context and dependency injection:** [Context Reference](../reference/context.md) for integrating `OtelContext` with the Context module
- **Resource management:** [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) — the telemetry module has no dependency on `Scope`; the providers are plain `AutoCloseable`s you shut down yourself, as in the wiring recipe above

## See Also

- [TracerProvider](../reference/telemetry/tracing/tracer-provider.md) — configure and install the tracing pipeline
- [LoggerProvider](../reference/telemetry/logging/logger-provider.md) — configure and install the logging pipeline
- [MeterProvider](../reference/telemetry/metrics/meter-provider.md) — configure and install the metrics pipeline
- [Attributes](../reference/telemetry/common/attributes.md) — the unboxed key-value type carried by every signal
- [Common Types](../reference/telemetry/common/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
