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

The backend is a chain of `LogRecordProcessor` instances. Two concerns: adding outputs, and replacing or tearing the chain down.

### Adding outputs

`writer(formatter, logWriter)` wraps a `LogFormatter` + `LogWriter` into a `FormattedLogRecordProcessor` and appends it; `addProcessor(processor)` appends any `LogRecordProcessor` directly. Both leave existing outputs in place, so repeated calls install multiple independent sinks.

```scala
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
  def addProcessor(processor: LogRecordProcessor): Unit
}
```

We send records to a human-readable and a JSON sink at once, and attach a processor that counts errors for internal metrics:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{TextLogFormatter, JsonLogFormatter, StdoutWriter, StderrWriter}
import zio.blocks.telemetry.{LogRecordProcessor, LogRecord}
import java.util.concurrent.atomic.LongAdder

log.writer(TextLogFormatter, StdoutWriter)
log.writer(JsonLogFormatter, StderrWriter)

val errorCount = new LongAdder()
log.addProcessor(new LogRecordProcessor {
  def onEmit(record: LogRecord): Unit =
    if (record.severity.number >= 17) errorCount.increment()
  def shutdown(): Unit   = ()
  def forceFlush(): Unit = ()
})
```

:::caution
Each `writer` call appends a new processor — N calls means N format-and-write operations per record. Call `clearWriters()` first if you need to replace an existing output.
:::

### Replacing and tearing down

`install(logger, minSeverity)` atomically swaps the whole backend for a provided `Logger` and sets the global floor (`minSeverity` defaults to `Severity.Trace`, so all records pass). `clearWriters()` shuts down only the outputs added via `writer`, leaving `addProcessor`/`install` processors intact; `removeAll()` shuts down *everything* and resets to a no-op state, after which records are discarded until a new output is installed.

```scala
object log {
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
  def clearWriters(): Unit
  def removeAll(): Unit
}
```

We install a provider-built `Logger` at startup, swap writers without duplicating records, and flush everything at shutdown:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.{Logger, Severity, JsonLogFormatter, StdoutWriter}

def initLogging(logger: Logger): Unit =
  log.install(logger, Severity.Info)

log.clearWriters()                          // drop writer-based outputs...
log.writer(JsonLogFormatter, StdoutWriter)  // ...now the only active writer

// At JVM shutdown, flush and close all channels
Runtime.getRuntime.addShutdownHook(new Thread(() => log.removeAll()))
```

:::caution
`install` does not shut down the previously installed backend's processors — call `removeAll()` first for a clean transition.
:::

## Core Operations

### Basic Logging

The six basic logging methods — `trace`, `debug`, `info`, `warn`, `error`, and `fatal` — emit a record at the named severity. Every call is macro-expanded at compile time so that `code.filepath`, `code.namespace`, `code.function`, and `code.lineno` attributes are injected statically. The call is skipped entirely when the record's severity falls below the current global minimum.

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

### Rate-Limited Logging

When the same log call sits in a hot path, two families throttle it — both keyed to the call site (see the caution below). Choose by how you want to limit: **Every-N** caps by invocation count, **Time-Window** caps by elapsed time.

#### Every-N

The every-N family — `traceEvery`, `debugEvery`, `infoEvery`, `warnEvery`, `errorEvery`, `fatalEvery` — limits emission to at most once every `every` invocations of the same call site. Each method takes the sampling integer `every` as its first argument, followed by the message and any enrichments, with the same macro-expansion guarantee as the basic methods:

```scala
trait LogVersionSpecific { self: log.type =>
  def infoEvery(every: Int, message: String, enrichments: Any*): Unit
  // traceEvery / debugEvery / warnEvery / errorEvery / fatalEvery follow the same shape
}
```

A heartbeat log should appear roughly once per hundred loop iterations, not on every tick. We use `log.infoEvery` to suppress the flood while keeping the signal:

```scala mdoc:compile-only
import zio.blocks.telemetry.log

var tick = 0
while (true) {
  val uptimeSeconds = tick.toLong * 5
  log.infoEvery(100, "heartbeat", "uptime" -> uptimeSeconds)
  tick += 1
  Thread.sleep(5000)
}
```

#### Time-Window

The time-window family — `traceAtMost`, `debugAtMost`, `infoAtMost`, `warnAtMost`, `errorAtMost`, `fatalAtMost` — limits emission to at most once per `intervalMillis` milliseconds from the same call site, regardless of how many times the surrounding code runs. Each method takes the interval in milliseconds as its first argument, followed by the message and enrichments:

```scala
trait LogVersionSpecific { self: log.type =>
  def warnAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit
  // traceAtMost / debugAtMost / infoAtMost / errorAtMost / fatalAtMost follow the same shape
}
```

A slow-query warning that fires inside a hot request handler could produce thousands of records per second. We use `log.warnAtMost` to keep at most one warning per five-second window:

```scala mdoc:compile-only
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
The time window uses `System.currentTimeMillis()`, whose granularity depends on the platform's system clock — often coarser than 1 ms. Because the clock advances in discrete ticks, calls within one tick read the same value, so the elapsed-time check can see zero (over-suppressing) and then jump a whole tick at once (letting a burst through). Intervals near or below that granularity therefore behave inconsistently — prefer intervals comfortably above that threshold (e.g., 10–20 ms) for more predictable suppression.
:::

:::caution
Rate limiting is best-effort. Each rate-limited call site is identified at compile time by hashing its source location (`file:line`), and that hash picks one slot in a fixed, process-wide table of 4096 counters (`siteId & 4095`). "Same call site" therefore means the same physical line — a call inside a loop shares one counter across all iterations (which is the point), while the same call copied onto two different lines gets two independent counters.

Two different call sites can collide onto one slot — either because their hashes are equal (rare) or, more commonly, because two distinct hashes fold to the same slot; the odds of some collision grow as the number of rate-limited call sites in your app passes a few dozen. When two sites share a slot they share a counter, so one site's calls advance — and can trip — the other's limit. The table is never reset and the checks are lock-free, so under heavy concurrency the every-N / interval boundary is approximate, not exact.
:::

### Annotations

`log.annotated` attaches contextual key/value pairs to every record emitted within a lexical scope. It stores the given `(String, String)` pairs in thread-local state and merges them into the `Attributes` of every record emitted inside the block `f`; when the block exits — normally or by exception — the annotations are removed via the thread-local scope:

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

Which records are emitted is governed at two levels: a global floor that applies to every call, and per-package overrides that tighten or relax specific namespaces. Records below the applicable floor are discarded before any object is allocated.

#### The global floor

`setMinSeverity(severity)` sets the process-wide minimum; `withMinSeverity(severity)(f)` sets it only for the duration of a block, restoring the previous floor via `try/finally` whether `f` returns or throws.

```scala
object log {
  def setMinSeverity(severity: Severity): Unit
  def withMinSeverity[A](severity: Severity)(f: => A): A
}
```

In production we raise the floor to `Warn` globally, then use the scoped form to capture verbose output from one operation without changing global state:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.Severity

log.setMinSeverity(Severity.Warn) // suppresses Trace, Debug, Info globally

val result = log.withMinSeverity(Severity.Debug) {
  log.debug("entering diagnostic mode")
  computeResult()
}
// previous floor restored here
```

:::note
`setMinSeverity(severity)` also clears every per-package override — it reinstalls the backend with a fresh, empty override map. Set the global floor first, then add per-package overrides, not the other way around.
:::

:::caution
The severity floor is a single setting shared by the whole program. `withMinSeverity` changes it for the duration of your block, then puts the old value back when the block finishes. Two things to watch:

- **It is not private to your thread.** While your block runs, every other thread that logs sees the changed floor too.
- **The restore can undo someone else's change.** When your block ends, it writes back the value it saw when it *started*. If another thread — or a nested `withMinSeverity` — changed the floor in between, that change is overwritten and lost.

So use `withMinSeverity` only when nothing else is logging at the same time, such as a single-threaded script or an isolated test. To lower verbosity for one part of a running app without disturbing the rest, use a per-package override instead: it targets code by its package name and leaves the shared floor untouched.
:::

#### Per-package overrides

`setMinSeverity(prefix, severity)` installs a floor for every call whose compile-time namespace starts with `prefix` (most-specific prefix wins over the global floor and less-specific prefixes); `clearMinSeverity(prefix)` removes one override, `clearAllOverrides()` removes them all.

```scala
object log {
  def setMinSeverity(prefix: String, severity: Severity): Unit
  def clearMinSeverity(prefix: String): Unit
  def clearAllOverrides(): Unit
}
```

We silence one noisy subsystem while keeping everything else at `Debug`, then clean up afterward:

```scala
import zio.blocks.telemetry.log
import zio.blocks.telemetry.Severity

log.setMinSeverity(Severity.Debug)                     // global floor
log.setMinSeverity("com.example.noisy", Severity.Warn) // quiet this package

log.clearMinSeverity("com.example.noisy")              // remove one override
log.clearAllOverrides()                                // or remove every override
```

:::note
Prefix matching uses `String#startsWith`, not glob or regex — `"com.example"` matches both `com.example.Foo` and `com.example.util.Bar`.
:::

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