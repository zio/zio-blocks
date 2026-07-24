---
id: index
title: "Telemetry"
description: "Module reference for the zero-dependency, OpenTelemetry-aligned tracing, logging, and metrics stack."
keywords:
  - "telemetry module"
  - "tracing spans"
  - "structured logging"
  - "metric instruments"
  - "TracerProvider builder"
  - "trace-log correlation"
  - "Attributes parallel arrays"
---

The telemetry module provides a zero-dependency, OpenTelemetry-aligned observability stack covering the three standard pillars: tracing, logging, and metrics. Three global singletons — `trace`, `log`, and `metric` — work without any configuration and keep spans and log records in-memory by default; the full provider and builder API ([`TracerProvider`](./tracer-provider.md), [`LoggerProvider`](./logger-provider.md), [`MeterProvider`](./meter-provider.md)) supports production wiring with custom exporters, samplers, and processors. A shared attribute vocabulary — [`Attributes`](./attributes.md), [`AttributeKey`](./attribute-key.md), [`Resource`](./resource.md), and [`InstrumentationScope`](./instrumentation-scope.md) — is reused across all three pillars, and a shared `ContextStorage[Option[`[`SpanContext`](./span-context.md)`]]` enables automatic trace-log correlation so every log record emitted inside a span carries the span's trace and span IDs.

The module is organized into three symmetric layers:

```scala
import zio.blocks.telemetry._

// Layer 1 — global singletons, backed by in-memory processors by default
object trace {
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def get(name: String): Tracer
  def install(provider: TracerProvider): Unit
}
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
  // info/debug/warn/error/fatal/trace — macro-generated, capture source location at compile time
}
object metric {
  def counter(name: String): Counter
  def get(name: String): Meter
  def install(provider: MeterProvider): Unit
}

// Layer 2 — provider/factory types, configured once at application startup
final class TracerProvider { def get(name: String, version: String = ""): Tracer }
final class LoggerProvider { def get(name: String, version: String = ""): Logger }
final class MeterProvider  { def get(name: String, version: String = ""): Meter  }

// Layer 3 — scope-bound signal emitters, one per instrumentation library
final class Tracer { def span[A](name: String)(f: Span => A): A         }
final class Logger { /* severity methods emit through LogRecordProcessor */ }
final class Meter  { def counterBuilder(name: String): CounterBuilder    }
```

## Motivation

Most Scala observability solutions require a heavy runtime (ZIO, Cats Effect, Akka) or a concrete logging backend (Logback, Log4j), and metrics registries tend to be globally mutable and framework-specific. The telemetry module addresses these constraints with a self-contained design:

- It has zero library dependencies — no effect system, no SLF4J binding, no Micrometer registry.
- It cross-compiles to Scala.js, so the same instrumentation code works in browser and server contexts.
- Compared to the OpenTelemetry Java SDK, it mirrors the same data model but avoids boxing: trace IDs are two `Long` fields in `SpanContext`, `SpanId` and `TraceFlags` are `AnyVal` wrappers, and `Attributes` uses parallel primitive arrays with a byte discriminator.
- Compared to SLF4J and Logback, source location (`code.filepath`, `code.namespace`, `code.function`, `code.lineno`) is captured at compile time by macros rather than at runtime by stack-walking, and every log record natively carries trace correlation fields.
- Compared to Micrometer, instruments are built directly on a `Meter` retrieved from a `MeterProvider` — no global default registry — and the same typed `AttributeKey[A]` system used in tracing is reused for metric dimensions.

## Installation

Add the following dependency to your build:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-telemetry" % "@VERSION@"
```

Use `%%%` for cross-platform (JVM + Scala.js) projects. For JVM-only projects, `%%` also works. No further configuration is needed to start using the global API.

## Overview

The module's types are organized into eight groups. The first three groups form the core API that most application code touches directly; the remaining groups contain supporting types that appear when building custom processors, exporters, or advanced configurations.

### Global Entry Points

The three global singletons are the entry points for most application code. They are backed by in-memory processors by default, so they work immediately in tests and development without any configuration:

- [**`trace`**](./trace.md) is the global tracing singleton. It stores spans in an in-memory ring buffer by default, so `trace.span("name") { span => … }` works immediately in tests and development. Call `trace.install` to replace the default provider with a production-configured `TracerProvider`.
- [**`log`**](./log.md) is the global structured-logging singleton. Its severity methods — `log.info`, `log.warn`, `log.error`, and so on — are macro-generated: the macro captures the call-site file path, namespace, method name, and line number at compile time and adds them as `code.*` attributes to every record. Severity filtering, per-namespace overrides, rate-limiting (`log.infoEvery`), and scoped annotations (`log.annotated`) are all built in.
- [**`metric`**](./metric.md) is the global metrics singleton. Convenience factory methods such as `metric.counter` and `metric.histogram` create instruments on the default meter without touching the provider directly. Call `metric.install` to wire a production `MeterProvider`, and `metric.reader` to collect snapshots.

### Tracing

The tracing pillar is built around three core types: `TracerProvider`, `Tracer`, and `Span`. They are the entry points for most application code:

- [**`TracerProvider`**](./tracer-provider.md) is the factory for `Tracer` instances. It holds the shared `Resource`, `Sampler`, `SpanProcessor` chain, and `ContextStorage`, so all tracers created from the same provider share the same sampling policy and export pipeline. Build one with `TracerProvider.builder` and install it via `trace.install`.
- [**`Tracer`**](./tracer.md) creates spans within a single instrumentation scope. It consults the `Sampler` to decide whether to record a span, stores the active `SpanContext` in `ContextStorage` for the duration of the block, and notifies each `SpanProcessor` on span start and end. Obtain a `Tracer` from `TracerProvider.get` or `trace.get`.
- [**`Span`**](./span.md) is a mutable, thread-safe unit of work in a trace. It tracks attributes, events, links, and status; once `Span#end` is called all mutating methods become no-ops. When sampling drops a span, `Tracer` substitutes `Span.NoOp` — a zero-allocation singleton — so hot paths never pay for dropped spans.

### Logging

The logging pillar is built around two core types: `LoggerProvider` and `Logger`. They are the entry points for most application code:

- [**`LoggerProvider`**](./logger-provider.md) is the factory for `Logger` instances. It holds the shared `Resource`, `LogRecordProcessor` array, and `ContextStorage`, making it the logging equivalent of `TracerProvider`. Build one with `LoggerProvider.builder` and pass loggers obtained from `LoggerProvider.get` to `log.install`.
- [**`Logger`**](./logger.md) emits `LogRecord`s through the configured processor chain. At construction time it automatically selects a fast `FormattedLogEmitter` path when the only processor is a `ConsoleLogRecordProcessor`, bypassing `LogRecord` allocation entirely. It also reads the active `SpanContext` from its `ContextStorage` and stamps `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` into each record as primitives, enabling automatic trace-log correlation.

### Metrics

The metrics pillar is built around `MeterProvider`, `Meter`, and the instrument types (`Counter`, `UpDownCounter`, `Histogram`, and `Gauge`). They are the entry points for most application code:

- [**`MeterProvider`**](./meter-provider.md) is the factory for `Meter` instances, backed by a shared `MeterRegistry`. It exposes a `MetricReader` via its `reader` field that can collect snapshots from all registered meters at any time. Build one with `MeterProvider.builder`.
- [**`Meter`**](./meter.md) creates and registers metric instruments for a given instrumentation scope. Builder methods such as `Meter#counterBuilder`, `Meter#histogramBuilder`, and `Meter#gaugeBuilder` return fluent builders; shortcut methods such as `Meter#labeledCounter` create `LabeledCounter` instruments with pre-declared label names. Obtain a `Meter` from `MeterProvider.get` or `metric.get`.

The instrument types are the leaf nodes of the metrics pillar, each with a different data model: `Counter` (cumulative), `UpDownCounter` (bidirectional), `Histogram` (distribution), and `Gauge` (last-write). All four are created by a `Meter` and documented in the [Meter](./meter.md) reference.

### Shared Vocabulary

The whole module shares a common vocabulary of typed key-value pairs, resource descriptors, and instrumentation scope identifiers. These types are used in all three pillars to describe the entity producing telemetry, the library or component that produced a signal, and the attributes associated with each signal.

- [**`Attributes`**](./attributes.md) is the immutable collection of typed key-value pairs shared across all three pillars. Its parallel-array implementation stores primitive values (`Long`, `Double`, `Boolean`) unboxed and uses a byte-type discriminator to select the right slot, eliminating `AttributeValue` boxing on hot paths. Merge two instances with `Attributes#++`.
- [**`AttributeKey`**](./attribute-key.md) is the type-safe key for `Attributes` storage. Each key binds a name string to a specific value type `A`; the eight supported types are `String`, `Boolean`, `Long`, `Double`, and four `Seq` variants. Factory methods `AttributeKey.string`, `AttributeKey.long`, and so on enforce consistency between key and value at compile time.
- [**`Resource`**](./resource.md) describes the entity producing telemetry — the service, container, or host. `Resource.default` auto-populates `service.name`, `telemetry.sdk.name`, `telemetry.sdk.language`, and `telemetry.sdk.version` from build metadata. Combine two resources with the `merge` extension method.
- [**`InstrumentationScope`**](./instrumentation-scope.md) identifies the library or component that produced a signal. It carries a `name`, an optional `version`, and optional `Attributes`, and is stamped into every `Span`, `SpanData`, and `LogRecord` to distinguish signals from different instrumentation libraries running in the same process.

### Tracing Support

The tracing pillar has some additional supporting types that appear when building custom exporters, processors, or advanced configurations:

- [**`SpanContext`**](./span-context.md) is the propagatable identity of a span — the part that travels across process boundaries. The trace ID is stored as two `Long` fields (`traceIdHi` and `traceIdLo`) for zero heap allocation. `SpanContext.invalid` is the sentinel for "no active span".
- [**`SpanData`**](./span-data.md) is the immutable snapshot of all span data produced when `Span#end` is called. It carries the name, kind, start and end timestamps in nanoseconds, attributes, events, links, status, resource, and instrumentation scope. `SpanProcessor.onEnd` receives `SpanData`, and `trace.collectedSpans` returns a list of it for test assertions.
- [**`SpanProcessor`**](./span-processor.md) is the open trait that receives `onStart` and `onEnd` lifecycle hooks from a `Tracer`. Custom exporters implement `SpanProcessor` to ship spans to backends such as OTLP, Zipkin, or Jaeger. The built-in `InMemorySpanProcessor` is used by the global `trace` singleton for development and testing.
- [**`Sampler`**](./sampler.md) decides whether a new span should be dropped, recorded-only, or recorded-and-sampled. Three built-ins cover the common cases: `AlwaysOnSampler`, `AlwaysOffSampler`, and `ParentBasedSampler(root)`, which defers to the parent span's decision and falls back to `root` for root spans.

### Logging Support

The logging pillar has some additional supporting types that appear when building custom processors, formatters, or advanced configurations:

- [**`LogRecord`**](./log-record.md) is the immutable snapshot of a single log emission. It carries timestamp, observed timestamp, severity, body, attributes, and trace correlation fields (`traceIdHi`, `traceIdLo`, `spanId`, `traceFlags`) stored as primitives with `0` as the sentinel for absent. The `hasTraceId` and `hasSpanId` helpers reflect whether a real span context was present.
- [**`LogRecordProcessor`**](./log-record-processor.md) is the open trait that receives `onEmit(logRecord: LogRecord)` from a `Logger`. Implement it to forward records to any backend. The `minimumLevel` method lets the `Logger` short-circuit below-threshold records before constructing them, enabling zero-overhead filtering.
- [**`LogFormatter`**](./log-formatter.md) is a stateless singleton that converts log data to text or JSON. Two built-in implementations cover the common cases: `TextLogFormatter` produces human-readable output and `JsonLogFormatter` produces OTLP-compatible JSON. Formatters append to a pooled `StringBuilder` and carry no per-instance state.
- [**`Severity`**](./severity.md) is a sealed trait with 24 levels following the OpenTelemetry log data model, grouped into six categories: Trace (1–4), Debug (5–8), Info (9–12), Warn (13–16), Error (17–20), and Fatal (21–24). Parse a level from an integer with `Severity.fromNumber` or from a string with `Severity.fromText`.
- [**`LogEnrichment`**](./log-enrichment.md) is the typeclass consumed by macro-generated log calls. Built-in instances handle `Throwable`, `String`, `Attributes`, `Severity`, and `(String, V)` key-value pairs, letting callers pass mixed enrichment arguments such as `log.error("payment failed", ex, "orderId" -> id)`.

### Metrics Support

The metrics pillar has some additional supporting types that appear when building custom readers, exporters, or advanced configurations:

- [**`MetricData`**](./metric-data.md) is the sealed trait for aggregated metric snapshots. Its three variants — `SumData`, `HistogramData`, and `GaugeData` — are returned by `MetricReader#collectAllMetrics` and are the output of `Counter#collect`, `Histogram#collect`, and `Gauge#collect` respectively.
- **Labeled instruments** — `LabeledCounter`, `LabeledHistogram`, and `LabeledGauge` wrap a base instrument with pre-declared label names, so callers pass label values positionally instead of building `Attributes` by hand. They are created via `Meter#labeledCounter` / `labeledHistogram` / `labeledGauge` and documented in the [Meter](./meter.md) reference.

## How They Work Together

The three pillars share a structural pattern — a global singleton wraps a provider that is a factory for scope-bound signal emitters — and two shared concerns cross pillar boundaries: the `Attributes`-based vocabulary and the `ContextStorage` that connects tracing to logging.

The typical workflow for a production-configured application proceeds in four steps:

1. **Configure** a `Resource` describing the service and build providers for each pillar, passing the same `ContextStorage` instance to `TracerProvider` and `LoggerProvider` so spans and log records are correlated automatically.
2. **Install** the providers into the global singletons with `trace.install`, `log.install`, and `metric.install` at application startup.
3. **Emit** signals from application and library code using `trace.span`, the severity methods on `log`, and `Counter#add` / `Histogram#record` / `Gauge#record` on instruments obtained from `metric`.
4. **Collect** when ready: `SpanProcessor.onEnd` ships spans eagerly; `metric.reader.collectAllMetrics()` pulls a snapshot of all registered instruments on demand.

The relationships and data flows among the types are shown below:

```
  Shared Vocabulary
  ┌──────────────────────────────────────────────────────────────────┐
  │  Attributes · AttributeKey · Resource · InstrumentationScope     │
  └──────────────────────────────────────────────────────────────────┘
         │ stamped into every Span, LogRecord, and MetricData

  TRACING PILLAR                  LOGGING PILLAR                METRICS PILLAR
  ──────────────────────          ───────────────────────       ─────────────────────────
  trace (global)                  log (global)                  metric (global)
    │ wraps                         │ wraps                       │ wraps
  TracerProvider                  LoggerProvider                MeterProvider
    │ creates                        │ creates                     │ creates
  Tracer ──────────────────────► Logger                          Meter
    │ consults                    │ reads SpanContext              │ creates
  Sampler          ContextStorage[Option[SpanContext]]          Counter
    │ creates       (shared by both providers)                  Histogram
  Span                             │ emits                       Gauge
    │ notifies                   LogRecord ─────────────────► UpDownCounter
  SpanProcessor                    │ fans out to
    │ snapshots                  LogRecordProcessor
  SpanData                         │ formatted by
                                 LogFormatter
                                                               reader
  trace.collectedSpans             log.annotated              metric.reader
  (test / dev)                     (scoped context)           .collectAllMetrics()
                                                                 │ yields
                                                               MetricData
```

**Tracing data flow:** `trace.span("name") { span => … }` delegates to `Tracer#span`, which consults the `Sampler`. If the decision is `RecordAndSample`, the tracer creates a `RecordingSpan` via `SpanBuilder`, calls `SpanProcessor#onStart`, scopes the new `SpanContext` into `ContextStorage`, runs the user block, calls `span.end()`, snapshots all data into `SpanData`, and calls `SpanProcessor#onEnd`. If the decision is `Drop`, the tracer substitutes `Span.NoOp` — the user block runs with zero overhead for span management.

**Logging data flow:** A macro-generated `log.info(…)` call checks `GlobalLogState.globalMinLevel` and any per-namespace level override. If the record passes, the macro adds `code.*` attributes at the call site, merges any active `LogAnnotations`, reads the current `SpanContext` from `ContextStorage`, and dispatches to the `Logger`'s emitter. When exactly one `ConsoleLogRecordProcessor` is configured, `Logger` selects the `FormattedLogEmitter` fast path that formats directly from builder arrays without constructing a `LogRecord` object.

**Metrics data flow:** `meter.counterBuilder("name").build()` registers a `Counter` in `Meter.instruments`. The `Meter` is registered in the `MeterRegistry` backing the `MeterProvider`. When `metric.reader.collectAllMetrics()` is called, it iterates all registered meters, calls each instrument's `collect()`, and returns cumulative `MetricData` snapshots.

**Cross-pillar correlation:** `TracerProvider` and `LoggerProvider` share the same `ContextStorage[Option[SpanContext]]`. When a `Tracer` opens a span, it stores the live `SpanContext` in that storage. When the `Logger` emits inside the same thread-local scope, it reads the context and stamps `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` into the `LogRecord` as primitive fields, requiring no heap allocation for the correlation data.

## Common Patterns

The seven patterns below cover the scenarios that most teams encounter when adopting the module. We name each pattern so it can be referenced concisely in code review and architecture discussions.

### Zero-Setup Global API

Import `zio.blocks.telemetry._` and use the global singletons immediately. Spans are stored in the in-memory `InMemorySpanProcessor`; log records are not emitted anywhere until you add a writer or processor. This pattern is ideal for unit tests and development, where you want no I/O side effects:

```scala
import zio.blocks.telemetry._

trace.span("process-order", SpanKind.Server) { span =>
  span.setAttribute("order.id", orderId)
  log.info("processing order")
  processOrder(orderId)
}

val spans = trace.collectedSpans  // inspect collected spans in tests
```

Call `trace.clearSpans()` between tests to reset the in-memory store.

### Production Provider Installation

Build configured providers at application startup and install them into the global singletons. We use `log.writer` for the simplest case — adding a formatted console output without a full `LoggerProvider`:

```scala
import zio.blocks.telemetry._

val myResource = Resource.default.merge(
  Resource.create(Attributes.of(Attributes.ServiceName, "order-service"))
)

// Tracing: always-on sampler, custom exporter
val tracerProvider = TracerProvider.builder
  .setResource(myResource)
  .setSampler(AlwaysOnSampler)
  .addSpanProcessor(myOtlpExporter)
  .build()

// Logging: human-readable output to stdout
log.writer(TextLogFormatter, StdoutWriter)

// Metrics: default provider with custom resource
val meterProvider = MeterProvider.builder
  .setResource(myResource)
  .build()

trace.install(tracerProvider)
metric.install(meterProvider)
```

Call `tracerProvider.shutdown()` and `meterProvider.shutdown()` when the application exits to flush any buffered data.

### Shared ContextStorage for Trace-Log Correlation

When you build both a `TracerProvider` and a `LoggerProvider`, pass the same `ContextStorage` instance to both builders. The `Logger` then reads from the same storage that `Tracer` writes to, so every log record emitted inside a span automatically carries that span's `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags`:

```scala
import zio.blocks.telemetry._

val cs = ContextStorage.defaultSpanContextStorage

val tracerProvider = TracerProvider.builder
  .setContextStorage(cs)
  .addSpanProcessor(myExporter)
  .build()

val loggerProvider = LoggerProvider.builder
  .setContextStorage(cs)
  .addLogRecordProcessor(myLogProcessor)
  .build()

trace.install(tracerProvider)
log.install(loggerProvider.get("com.example"))

// Inside a span, log records carry the active SpanContext automatically:
trace.span("checkout") { _ =>
  log.info("payment initiated")  // LogRecord.traceIdHi/Lo set from the active span
}
```

### Library DI Pattern

Library code should accept `Tracer` and/or `Logger` as constructor parameters rather than calling the global singletons directly. This keeps libraries testable and decoupled from application-level provider configuration:

```scala
import zio.blocks.telemetry._

final class OrderService(tracer: Tracer, logger: Logger) {
  def placeOrder(id: String): Unit =
    tracer.span("order.place", SpanKind.Internal) { span =>
      span.setAttribute("order.id", id)
      logger.info("placing order", "orderId" -> AttributeValue.StringValue(id))
    }
}

// In application startup, supply instances from the global providers:
val service = new OrderService(
  tracer = trace.get("com.example.orders"),
  logger = loggerProvider.get("com.example.orders")
)
```

### Scoped Log Annotations

`log.annotated` propagates key-value pairs to all `log.*` calls within its block. Use this to attach a request ID, tenant ID, or user ID to all log records produced during a request without threading the value through every call site:

```scala
import zio.blocks.telemetry._

def handleRequest(requestId: String): Unit =
  log.annotated("requestId" -> requestId) {
    log.info("started")   // carries requestId -> requestId
    processRequest()
    log.info("finished")  // carries requestId -> requestId
  }
```

### Rate-Limited Logging

`log.infoEvery` and its severity siblings suppress repeated log calls, emitting at most once every N invocations per call site. Use this for heartbeats, polling loops, and any code path where emitting on every iteration would be too noisy:

```scala
import zio.blocks.telemetry._

// Emits at most once every 1000 calls — call site is the discriminator
while (running) {
  log.infoEvery(1000, "worker heartbeat")
  doWork()
}
```

### Labeled Instruments for Prometheus-style Metrics

`Meter#labeledCounter`, `Meter#labeledHistogram`, and `Meter#labeledGauge` declare label names once at instrument creation and accept positional label values when recording. This mirrors the Prometheus / OpenMetrics pattern and avoids constructing `Attributes` at each record site:

```scala
import zio.blocks.telemetry._

val meter    = metric.get("com.example.http")
val requests = meter.labeledCounter("http.requests", "method", "status")
val latency  = meter.labeledHistogram("http.request.latency", "endpoint")

// Record values with positional label values — arity is validated at runtime:
requests.add(1, "GET", "200")
requests.add(1, "POST", "500")
latency.record(42.5, "/api/orders")
```

## Integration Points

The telemetry module integrates with the rest of the ZIO Blocks library and with external systems at the following points.

The module depends on the `context` module for its `ContextStorage[A]` abstraction. On the JVM, the default implementation uses a `ScopedValue`; on Scala.js, a simple mutable cell. Passing `ContextStorage.defaultSpanContextStorage` to both builders is the standard way to enable trace-log correlation without writing platform-specific code.

The `otel` sub-project (a separate artifact) provides an OpenTelemetry Java SDK bridge. Use it when you need to interoperate with the Java OTel ecosystem — for example, to reuse an existing OTLP exporter or an SDK-based sampling configuration — without taking the Java SDK as a direct dependency in the telemetry module itself.

`SpanProcessor` and `LogRecordProcessor` are open traits. Implement either to ship telemetry to any backend: OTLP over gRPC or HTTP, Zipkin, Jaeger, a database, or an in-process collector. On the JVM, `FileLogWriter` is available as a platform-specific `LogWriter` implementation; on Scala.js, a compatible writer targets browser-friendly output.

The `LogEnrichment` typeclass is designed for extension. Adding a `LogEnrichment[MyType]` instance makes `MyType` values passable as enrichment arguments to any `log.*` call, and the macro picks them up transparently without any change to call sites.

## See Also

- [Telemetry Guide](../../guides/telemetry-guide.md) — Architecture, design philosophy, and real-world usage patterns for this module
- [Context Reference](../context.md) — The `ContextStorage` abstraction used for trace-log correlation
- [Scope Reference](../resource-management/scope.md) — Resource-safe provider lifecycle management
- [Async Reference](../async.md) — Integration with the zero-allocation async effect type
