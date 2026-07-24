---
id: log
title: "log"
description: "Global structured logging singleton for zio-blocks-telemetry with compile-time source location, rate limiting, and pluggable backend."
keywords:
  - "Global Logging Singleton"
  - "Compile-Time Source Location"
  - "Structured Log Enrichments"
  - "Per-Call-Site Rate Limiting"
  - "Severity Floor Fast Path"
  - "Log Record Processor"
  - "Scoped Thread-Local Annotations"
---

`log` is the global structured logging entry point of `zio-blocks-telemetry`. It is declared as `object log extends LogVersionSpecific` — a singleton you import and call directly, with no instantiation or injection. The six severity methods (`trace`, `debug`, `info`, `warn`, `error`, `fatal`) are macro-expanded at compile time on both Scala 2 and Scala 3: source file path, class name, method name, and line number are captured statically and attached as `code.*` attributes on every record, with zero runtime reflection. A single integer comparison against the global minimum severity gates every call, so below-threshold logging costs almost nothing before any allocation occurs.

Within the Telemetry module, `log` is one of three global entry points alongside `trace` (distributed tracing) and `metric` (instrumentation). It wraps an internal `Logger` that routes records through a chain of `LogRecordProcessor` instances. The backend is fully pluggable at runtime: call `log.writer`, `log.install`, or `log.addProcessor` to configure where records go.

Key design properties:

- **Global singleton** — import and call directly; no DI or instantiation required. Library code that needs isolation should accept a `Logger` parameter instead.
- **Compile-time source location** — call-site `code.*` attributes are injected by the macro, not by stack-walking. Zero runtime cost.
- **Global min-severity fast path** — a single `Int` comparison discards below-threshold calls before any object is allocated.
- **Hierarchical severity overrides** — per-package prefix overrides let you silence noisy packages while keeping others verbose; the most-specific prefix wins.
- **Per-call-site rate limiting** — every-N and time-window variants prevent log floods from tight loops without instrumenting the surrounding code.
- **Scoped thread-local annotations** — `log.annotated` attaches key/value pairs to every record emitted within a lexical block.
- **Pluggable backend** — `addProcessor` / `writer` / `install` replace or extend the pipeline at any time.
- **Active-span correlation** — the active `SpanContext` is automatically injected from `ContextStorage` into each record when a trace is in progress.

The declaration shape and primary members are:

```scala
object log extends LogVersionSpecific {
  // Scoped annotation
  def annotated[A](annotations: (String, String)*)(f: => A): A

  // Global severity floor
  def setMinSeverity(severity: Severity): Unit
  def setMinSeverity(prefix: String, severity: Severity): Unit
  def clearMinSeverity(prefix: String): Unit
  def clearAllOverrides(): Unit
  def withMinSeverity[A](severity: Severity)(f: => A): A

  // Backend management
  def addProcessor(processor: LogRecordProcessor): Unit
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
  def removeAll(): Unit
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
  def clearWriters(): Unit
}

// Scala 3 — inline macros in LogVersionSpecific
trait LogVersionSpecific {
  inline def trace(inline message: String, inline enrichments: Any*): Unit
  inline def debug(inline message: String, inline enrichments: Any*): Unit
  inline def info(inline message: String, inline enrichments: Any*): Unit
  inline def warn(inline message: String, inline enrichments: Any*): Unit
  inline def error(inline message: String, inline enrichments: Any*): Unit
  inline def fatal(inline message: String, inline enrichments: Any*): Unit

  inline def traceEvery(every: Int, inline message: String, inline enrichments: Any*): Unit
  inline def infoEvery(every: Int, inline message: String, inline enrichments: Any*): Unit
  // ... debugEvery / warnEvery / errorEvery / fatalEvery follow the same shape

  inline def traceAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit
  inline def infoAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit
  // ... debugAtMost / warnAtMost / errorAtMost / fatalAtMost follow the same shape
}
```

## Usage

The following example shows the core capabilities in a single cohesive snippet: configuring an output writer, logging at two different levels with typed attributes, scoping an annotation block, and temporarily suppressing verbose output:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{Severity, TextLogFormatter, StdoutWriter}

// Direct output to stdout in a human-readable format
log.writer(TextLogFormatter, StdoutWriter)

// Basic logging with typed key/value enrichments
log.info("server started", "port" -> 8080, "env" -> "production")
log.warn("high memory usage", "heapMb" -> 1024L, "threshold" -> 768L)

// All records within this block carry requestId and userId attributes
log.annotated("requestId" -> "req-abc", "userId" -> "u-42") {
  log.info("processing request")
  log.debug("cache miss", "key" -> "products:featured")
}

// Rate-limit a heartbeat log to every 100 invocations of this call site
val uptimeSeconds = 300L
log.infoEvery(100, "heartbeat", "uptime" -> uptimeSeconds)

// Suppress Debug and Info temporarily (e.g., during a known-noisy section)
log.withMinSeverity(Severity.Warn) {
  log.debug("this is suppressed")
  log.warn("this still appears")
}
```

## Predefined Instances

`zio-blocks-telemetry` ships with two ready-to-use `LogFormatter` implementations and two `LogWriter` sinks. We can combine them freely with `log.writer`.

| Type               | Kind           | Description                                                                             |
|--------------------|----------------|-----------------------------------------------------------------------------------------|
| `TextLogFormatter` | `LogFormatter` | Human-readable text: `2026-03-31T17:30:00.123Z INFO [MyClass.method:42] message {k=v}`  |
| `JsonLogFormatter` | `LogFormatter` | OTLP-compatible JSON: `{"timeUnixNano":…,"severityNumber":…,"body":…,"attributes":[…]}` |
| `StdoutWriter`     | `LogWriter`    | Writes each formatted line to `System.out`                                              |
| `StderrWriter`     | `LogWriter`    | Writes each formatted line to `System.err`                                              |

`TextLogFormatter` caches the per-second UTC timestamp prefix to minimize formatting work on the hot path. `JsonLogFormatter` produces output compatible with the OTLP log data model and is the natural choice when forwarding records to an OpenTelemetry collector or a log aggregator that speaks OTLP.

## Configuring the Logging Backend

The three primary methods for this are `log.writer`, `log.install`, and `log.addProcessor`.

### `log.writer` — Add a formatted output sink

`log.writer` appends a `FormattedLogRecordProcessor` that formats each record with the given `LogFormatter` and hands the result to the given `LogWriter`. Calling `log.writer` multiple times installs multiple independent outputs — useful when you want human-readable logs to stdout and JSON logs to a file simultaneously.

```scala
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
}
```

The most common setup pairs `TextLogFormatter` with `StdoutWriter` for local development:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{TextLogFormatter, JsonLogFormatter, StdoutWriter, StderrWriter}

// Human-readable output to stdout
log.writer(TextLogFormatter, StdoutWriter)

// Add a second sink for JSON output to stderr (both are active)
log.writer(JsonLogFormatter, StderrWriter)
```

:::caution
Each call to `log.writer` appends a new processor. Calling it N times means N format-and-write operations per record. Call `log.clearWriters()` first if you need to replace an existing output.
:::

### `log.install` — Replace the entire backend

`log.install` replaces the entire logging backend with a custom `Logger` built externally (typically via `LoggerProvider`) and sets the global minimum severity in one atomic step.

```scala
object log {
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
}
```

This method suits production scenarios where the `Logger` is constructed by an integration layer — for example, a `LoggerProvider` that routes records to an OTLP exporter — and the configuration must be done before the application processes any requests:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{Logger, Severity}

// Replace the default console backend with a custom Logger,
// and raise the global floor to Info
def configureProd(logger: Logger): Unit =
  log.install(logger, Severity.Info)
```

:::caution
`log.install` does not shut down processors attached to the previously installed backend. Call `log.removeAll()` first if you need a clean transition.
:::

### `log.addProcessor` — Attach a processor directly

`log.addProcessor` appends a raw `LogRecordProcessor` to the current backend without replacing any existing processors. This is the right choice when you need fine-grained control over record handling — for example, buffering, batching, or forwarding to a remote endpoint.

```scala
object log {
  def addProcessor(processor: LogRecordProcessor): Unit
}
```

We can implement `LogRecordProcessor` directly to capture records in tests or route them to a custom sink:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{LogRecordProcessor, LogRecord}

val capturingProcessor: LogRecordProcessor = new LogRecordProcessor {
  private val records = scala.collection.mutable.ArrayBuffer.empty[LogRecord]
  def onEmit(record: LogRecord): Unit = records += record
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()
}

log.addProcessor(capturingProcessor)
```

## Core Operations

### Basic Logging

The six basic logging methods — `trace`, `debug`, `info`, `warn`, `error`, and `fatal` — emit a record at the named severity. Every call is macro-expanded at compile time so that `code.filepath`, `code.namespace`, `code.function`, and `code.lineno` attributes are injected statically. The call is skipped entirely when the record's severity falls below the current global minimum.

#### `trace` / `debug` / `info` / `warn` / `error` / `fatal` — Emit a log record

Each method accepts a message string followed by zero or more enrichments. Enrichments can be typed key/value tuples (`(String, String)`, `(String, Long)`, `(String, Double)`, `(String, Boolean)`, `(String, Int)`), a bare `Throwable` (which becomes `exception.type`, `exception.message`, and `exception.stacktrace` attributes), or an `Attributes` value.

Each method is a compile-time macro — `inline def` on Scala 3, a blackbox macro on Scala 2 — but the call syntax is identical across versions:

```scala
trait LogVersionSpecific { self: log.type =>
  def trace(message: String, enrichments: Any*): Unit
  def debug(message: String, enrichments: Any*): Unit
  def info(message: String, enrichments: Any*): Unit
  def warn(message: String, enrichments: Any*): Unit
  def error(message: String, enrichments: Any*): Unit
  def fatal(message: String, enrichments: Any*): Unit
}
```

The enrichments accept a mix of types in one call. Here we log a successful authentication event with a string attribute and then log a failure with a `Throwable`:

```scala
import zio.blocks.telemetry.log

log.info("user authenticated", "userId" -> "u-123", "region" -> "eu-west")
log.error("payment failed", new RuntimeException("gateway timeout"), "orderId" -> 42L)
```

Each of the six methods records at the primary level of its category — `log.info` at `Severity.Info` (numeric value 9), `log.warn` at `Severity.Warn`, and so on. See [`Severity`](./severity.md) for the full 24-level scale.

:::caution
Because these methods are macros, they inspect each enrichment's type *at compile time* (right where you write the call) to decide how it becomes an attribute. That only works when every enrichment is written out directly in the call:

```scala
val fields = Seq("userId" -> "u-123", "region" -> "eu-west")

log.info("ok", "userId" -> "u-123", "region" -> "eu-west") // ✅ written inline
log.info("ok", fields: _*)                                 // ❌ compile error
```

Spreading a pre-built sequence with `fields: _*` hides the individual values from the macro (it sees one runtime `Seq[Any]`, not the literal arguments), so it fails to compile with the message *"log methods require explicit arguments, not `args: _*` syntax"*. If you already have a collection, fold it into a single `Attributes` value and pass that — one value the macro accepts directly, no spread needed:

```scala mdoc:compile-only
import zio.blocks.telemetry.{log, Attributes}

val fields: Seq[(String, String)] = Seq("userId" -> "u-123", "region" -> "eu-west")

val attrs: Attributes =
  fields.foldLeft(Attributes.builder) { case (b, (k, v)) => b.put(k, v) }.build

log.info("user authenticated", attrs)
```
:::

### Rate-Limited Logging (Every-N)

The every-N family — `traceEvery`, `debugEvery`, `infoEvery`, `warnEvery`, `errorEvery`, and `fatalEvery` — limits emission to at most once every `every` invocations of the same call site. The counter is per-call-site: a `log.infoEvery(100, ...)` at line 42 and another at line 87 each maintain independent counters.

#### `traceEvery` / `debugEvery` / `infoEvery` / `warnEvery` / `errorEvery` / `fatalEvery` — Log every N invocations

Each method takes the sampling integer `every` as its first argument, followed by the message and any enrichments, with the same macro-expansion guarantee as the basic methods:

```scala
trait LogVersionSpecific { self: log.type =>
  def infoEvery(every: Int, message: String, enrichments: Any*): Unit
  // traceEvery / debugEvery / warnEvery / errorEvery / fatalEvery follow the same shape
}
```

A heartbeat log should appear roughly once per hundred loop iterations, not on every tick. We use `log.infoEvery` to suppress the flood while keeping the signal:

```scala
import zio.blocks.telemetry.log

var tick = 0
while (true) {
  val uptimeSeconds = tick.toLong * 5
  log.infoEvery(100, "heartbeat", "uptime" -> uptimeSeconds)
  tick += 1
  Thread.sleep(5000)
}
```

:::caution
Rate limiting is best-effort. Call-site identity is tracked by a hash; hash collisions between two different call sites can cause cross-site interference, where one site's counter advances the other's. Collisions are extremely rare in practice.
:::

### Rate-Limited Logging (Time-Window)

The time-window family — `traceAtMost`, `debugAtMost`, `infoAtMost`, `warnAtMost`, `errorAtMost`, and `fatalAtMost` — limits emission to at most once per `intervalMillis` milliseconds from the same call site, regardless of how many times the surrounding code runs.

#### `traceAtMost` / `debugAtMost` / `infoAtMost` / `warnAtMost` / `errorAtMost` / `fatalAtMost` — Log at most once per interval

Each method takes the interval in milliseconds as its first argument, followed by the message and enrichments:

```scala
trait LogVersionSpecific { self: log.type =>
  def warnAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit
  // traceAtMost / debugAtMost / infoAtMost / errorAtMost / fatalAtMost follow the same shape
}
```

A slow-query warning that fires inside a hot request handler could produce thousands of records per second. We use `log.warnAtMost` to keep at most one warning per five-second window:

```scala
import zio.blocks.telemetry.log

def executeQuery(sql: String): Unit = {
  val start   = System.currentTimeMillis()
  // ... execute ...
  val elapsed = System.currentTimeMillis() - start
  if (elapsed > 500)
    log.warnAtMost(5000L, "slow query", "queryMs" -> elapsed, "sql" -> sql)
}
```

:::note
The time window uses `System.currentTimeMillis()` with millisecond precision. Very short intervals (< 10 ms) may behave inconsistently on platforms with coarse system clocks.
:::

### Annotations

This category provides `log.annotated`, the mechanism for attaching contextual key/value pairs to every record emitted within a lexical scope.

#### `annotated` — Attach scoped key/value annotations

`log.annotated` stores the given `(String, String)` pairs in thread-local state and merges them into the `Attributes` of every record emitted inside the block `f`. When the block exits — normally or by exception — the annotations are removed via the thread-local scope:

```scala
object log {
  def annotated[A](annotations: (String, String)*)(f: => A): A
}
```

Annotations compose naturally by nesting. Inner annotations shadow outer ones on key collision. We can attach a request ID and tenant to every log record produced while handling a single HTTP request:

```scala
import zio.blocks.telemetry.log

def handleRequest(requestId: String, tenant: String): Unit =
  log.annotated("requestId" -> requestId, "tenant" -> tenant) {
    log.info("request received")
    // nested block adds userId; requestId and tenant are still present
    log.annotated("userId" -> "u-77") {
      log.debug("user context resolved")
    }
    log.info("request complete")
  }
```

:::caution
Annotations are `String`-to-`String` only. If you need typed numeric or boolean contextual data, add it as an enrichment argument directly on the `log.info(...)` call rather than through `log.annotated`.
:::

### Severity Control

The severity control methods govern which records are emitted. They operate at two levels: a global floor that applies to every call, and per-prefix overrides that let specific packages bypass (or tighten) the global setting. The five methods in this category are `setMinSeverity` (global), `setMinSeverity` (prefix), `clearMinSeverity`, `clearAllOverrides`, and `withMinSeverity`.

#### `setMinSeverity` (global) — Set the global severity floor

`log.setMinSeverity(severity)` sets the global minimum severity. Records below this level are discarded before any object is allocated:

```scala
object log {
  def setMinSeverity(severity: Severity): Unit
}
```

In a production environment where trace and debug output is noise, we raise the floor to `Warn`:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.Severity

log.setMinSeverity(Severity.Warn) // suppresses Trace, Debug, and Info globally
```

#### `setMinSeverity` (prefix) — Set a per-package severity override

`log.setMinSeverity(prefix, severity)` installs a severity override for all `log` calls whose compile-time namespace starts with `prefix`. The most-specific prefix wins over both the global floor and other prefix overrides:

```scala
object log {
  def setMinSeverity(prefix: String, severity: Severity): Unit
}
```

We can silence one particularly verbose subsystem while keeping everything else at `Debug`:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.Severity

log.setMinSeverity(Severity.Debug)                           // global floor
log.setMinSeverity("com.example.noisy", Severity.Warn)       // quiet this package
log.setMinSeverity("com.example", Severity.Debug)            // explicit for the parent
```

:::note
Prefix matching uses `String#startsWith`, not glob or regex. The prefix `"com.example"` matches both `com.example.Foo` and `com.example.util.Bar`.
:::

#### `clearMinSeverity` — Remove a per-prefix override

`log.clearMinSeverity` removes the severity override installed for a specific prefix, restoring that prefix to the global floor:

```scala
object log {
  def clearMinSeverity(prefix: String): Unit
}
```

After a debugging session we remove the per-package override we added for `com.example.noisy`:

```scala
import zio.blocks.telemetry.log

log.clearMinSeverity("com.example.noisy")
```

#### `clearAllOverrides` — Remove all per-prefix overrides

`log.clearAllOverrides` removes every per-prefix severity override in one call, leaving only the global floor in effect:

```scala
object log {
  def clearAllOverrides(): Unit
}
```

We call `log.clearAllOverrides` to reset diagnostic state between integration test suites:

```scala
import zio.blocks.telemetry.log

log.clearAllOverrides()
```

#### `withMinSeverity` — Temporarily adjust the severity floor

`log.withMinSeverity` sets the global severity floor for the duration of block `f` and then restores the previous floor via `try/finally`, regardless of whether `f` completes normally or throws:

```scala
object log {
  def withMinSeverity[A](severity: Severity)(f: => A): A
}
```

We use `log.withMinSeverity` to capture verbose diagnostic output from a specific operation without changing the global configuration permanently:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.Severity

val result = log.withMinSeverity(Severity.Debug) {
  log.debug("entering diagnostic mode")
  computeResult()
}
// The previous floor is restored here
```

:::caution
`log.withMinSeverity` modifies global state. Other threads logging concurrently will observe the temporary floor during the window. For thread-isolated severity control, use per-prefix overrides instead.
:::

### Backend Configuration

The backend configuration methods control which `LogRecordProcessor` instances receive records and how they are shut down. Together, `writer`, `addProcessor`, `install`, `clearWriters`, and `removeAll` form the lifecycle management surface of `log`.

#### `writer` — Add a formatted output sink

`log.writer` wraps a `LogFormatter` and a `LogWriter` into a `FormattedLogRecordProcessor` and appends it to the current backend. Calling `log.writer` multiple times installs multiple independent sinks:

```scala
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
}
```

We add both a human-readable and a JSON sink to direct records to two different destinations:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{TextLogFormatter, JsonLogFormatter, StdoutWriter, StderrWriter}

log.writer(TextLogFormatter, StdoutWriter)
log.writer(JsonLogFormatter, StderrWriter)
```

#### `addProcessor` — Attach a processor

`log.addProcessor` appends any `LogRecordProcessor` implementation to the current backend, leaving existing processors in place:

```scala
object log {
  def addProcessor(processor: LogRecordProcessor): Unit
}
```

We attach a processor that counts records by severity for internal metrics:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{LogRecordProcessor, LogRecord}
import java.util.concurrent.atomic.LongAdder

val errorCount = new LongAdder()

log.addProcessor(new LogRecordProcessor {
  def onEmit(record: LogRecord): Unit =
    if (record.severity.number >= 17) errorCount.increment()
  def shutdown(): Unit   = ()
  def forceFlush(): Unit = ()
})
```

#### `install` — Replace the entire backend

`log.install` atomically replaces the internal `Logger` with the one provided and sets the global minimum severity:

```scala
object log {
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
}
```

The default value for `minSeverity` is `Severity.Trace`, so omitting it means all records pass the global floor check. We call `log.install` at application startup when the `Logger` is built by a provider layer:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{Logger, Severity}

def initLogging(logger: Logger): Unit =
  log.install(logger, Severity.Info)
```

#### `clearWriters` — Remove all writer-based outputs

`log.clearWriters` shuts down every processor added via `log.writer` and removes them from the backend. Processors added with `log.addProcessor` or `log.install` are unaffected:

```scala
object log {
  def clearWriters(): Unit
}
```

We call `log.clearWriters` before reconfiguring outputs to avoid duplicate records:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{JsonLogFormatter, StdoutWriter}

log.clearWriters()
log.writer(JsonLogFormatter, StdoutWriter) // now the only active writer
```

#### `removeAll` — Remove all processors and writers

`log.removeAll` shuts down every processor and writer — including those from `log.addProcessor` and `log.install` — by calling `shutdown()` on each, then resets the backend to a no-op state. After this call, all log invocations are silently discarded until a new processor or writer is installed:

```scala
object log {
  def removeAll(): Unit
}
```

We call `log.removeAll` at application shutdown to flush and close all output channels, or between test suites to guarantee a clean state:

```scala
import zio.blocks.telemetry.log

// At JVM shutdown
Runtime.getRuntime.addShutdownHook(new Thread(() => log.removeAll()))
```

## Comparison

### vs SLF4J / Logback

SLF4J and Logback are the most widely deployed Java logging stack. The table below contrasts them with `log` on the dimensions most relevant to performance-sensitive Scala applications:

| Dimension              | SLF4J / Logback                                               | `log`                                                                  |
|------------------------|---------------------------------------------------------------|------------------------------------------------------------------------|
| Source location        | Stack-walking at runtime (expensive, optional)                | Compile-time macro injection — zero runtime cost                       |
| Severity levels        | 5 (`TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR`)            | 24 fine-grained levels in 6 categories                                 |
| Contextual data        | Thread-local MDC (`String` → `String`)                        | Typed enrichments (`String`, `Long`, `Double`, `Boolean`) + MDC-style annotations |
| Fast-path cost         | Logger-name lookup in `LoggerFactory`, then level check       | Single integer comparison against a volatile `Int`                     |
| Backend configuration  | `logback.xml` / `logback-groovy` at startup                   | Programmatic, hot-reloadable via `log.writer` / `log.addProcessor`     |
| Dependency             | SLF4J API + Logback Classic (~500 KB combined)                | `zio-blocks-telemetry` only, zero transitive dependencies              |

The MDC equivalent in `log` is `log.annotated`, which is scoped to a lexical block rather than a thread-local map that must be manually cleared.

### vs java.util.logging (JUL)

`java.util.logging` is the standard JDK logging API. Its `Logger.log(Level, String)` allocates a `LogRecord` object on every call — even when the logger is disabled — unless the caller guards with `isLoggable`. `log` avoids this allocation by checking the global minimum severity as a raw `Int` comparison before any work begins, and by passing raw primitives directly to the formatter's `StringBuilder` rather than wrapping them in a `LogRecord` first.

| Dimension          | java.util.logging                                          | `log`                                                             |
|--------------------|------------------------------------------------------------|-------------------------------------------------------------------|
| Allocation on call | `new LogRecord(...)` before level check (unless guarded)   | Zero allocation below global min-severity threshold               |
| Structured data    | Not supported — message is a `String`                      | First-class typed key/value attributes on every record            |
| Source location    | `LogRecord` infers caller via stack inspection             | Compile-time macro — no stack inspection                          |
| Configuration      | `logging.properties` file or JMX                          | Programmatic, in-process                                          |
| OTLP / OTel        | Not supported without a handler bridge                     | Native OTLP JSON export via `zio-blocks-telemetry-otel`           |