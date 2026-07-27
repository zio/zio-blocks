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

The telemetry module is a zero-dependency, OpenTelemetry-aligned observability stack. It covers the **three standard pillars** of observability:

- **[Tracing](./tracing/index.md)** — the causal path of a request through your system, recorded as nested spans.
- **[Logging](./logging/index.md)** — structured, timestamped records of discrete events.
- **[Metrics](./metrics/index.md)** — numeric measurements aggregated over time, such as counters and latency histograms.

"OpenTelemetry-aligned" means the data model and naming follow the [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/), so signals map cleanly onto OTLP exporters and familiar backends — but without pulling in the OpenTelemetry SDK or any effect-system dependency.

Each pillar has a **global singleton** — `trace`, `log`, and `metric` — that you import and call directly. These need no setup: by default they keep spans and records **in-memory**, so instrumentation works immediately in tests and development. When you are ready for production, the **provider/builder API** ([`TracerProvider`](./tracing/tracer-provider.md), [`LoggerProvider`](./logging/logger-provider.md), [`MeterProvider`](./metrics/meter-provider.md)) lets you wire real export pipelines — custom exporters, samplers, and processors — and install them behind the same global entry points, so instrumentation code never changes.

Two things are shared across all three pillars:

1. A common **attribute vocabulary** so every span, log record, and metric is described the same way — see [Shared Vocabulary](#shared-vocabulary) below.
2. A shared `ContextStorage[Option[`[`SpanContext`](./tracing/span-context.md)`]]` holding the currently-active span, which enables automatic **trace-log correlation**: any log record emitted inside a span is stamped with that span's trace and span IDs, so you can pivot from a log line to the trace it belongs to.

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

## Shared Vocabulary

The whole module shares a common vocabulary of typed key-value pairs, resource descriptors, and instrumentation scope identifiers. These types are used in all three pillars to describe the entity producing telemetry, the library or component that produced a signal, and the attributes associated with each signal.

- [**`Attributes`**](./attributes.md) is the immutable collection of typed key-value pairs shared across all three pillars. Its parallel-array implementation stores primitive values (`Long`, `Double`, `Boolean`) unboxed and uses a byte-type discriminator to select the right slot, eliminating `AttributeValue` boxing on hot paths. Merge two instances with `Attributes#++`.
- [**`AttributeKey`**](./attribute-key.md) is the type-safe key for `Attributes` storage. Each key binds a name string to a specific value type `A`; the eight supported types are `String`, `Boolean`, `Long`, `Double`, and four `Seq` variants. Factory methods `AttributeKey.string`, `AttributeKey.long`, and so on enforce consistency between key and value at compile time.
- [**`Resource`**](./resource.md) describes the entity producing telemetry — the service, container, or host. `Resource.default` auto-populates `service.name`, `telemetry.sdk.name`, `telemetry.sdk.language`, and `telemetry.sdk.version` from build metadata. Combine two resources with the `merge` extension method.
- [**`InstrumentationScope`**](./instrumentation-scope.md) identifies the library or component that produced a signal. It carries a `name`, an optional `version`, and optional `Attributes`, and is stamped into every `Span`, `SpanData`, and `LogRecord` to distinguish signals from different instrumentation libraries running in the same process.

## How They Work Together

The three pillars share a structural pattern — a zero-config **global singleton** wraps a **provider** (built once at startup, owning the export pipeline) that is a factory for **scope-bound signal emitters** (one per instrumentation scope, producing the actual signals):

| Layer                                  | Tracing           | Logging                | Metrics               |
|----------------------------------------|-------------------|------------------------|-----------------------|
| Global singleton                       | `trace`           | `log`                  | `metric`              |
| Provider (factory, owns pipeline)      | `TracerProvider`  | `LoggerProvider`       | `MeterProvider`       |
| Scope-bound emitter (produces signals) | `Tracer` → `Span` | `Logger` → `LogRecord` | `Meter` → instruments |

Two concerns cross pillar boundaries: the `Attributes`-based vocabulary describes signals in all three pillars, and a shared `ContextStorage` holding the active `SpanContext` connects tracing to logging (so log records carry the active span's IDs — metrics is not involved).

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
