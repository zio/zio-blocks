---
id: instrument-a-program-with-logs-traces-and-metrics
title: "Instrument a Program with Logs, Traces and Metrics"
description: "Add structured logs, nested spans, a counter and a histogram to a batch job, switch to JSON logs, and test the spans."
keywords:
  - "Observability"
  - "Getting Started with Telemetry"
  - "Structured Logging"
  - "Tracing"
  - "Metrics"
  - "OpenTelemetry"
---

```scala mdoc:invisible
// Page plumbing, not part of the lesson. The telemetry module writes log lines to
// System.out and System.err, which mdoc does not capture, so this routes both into
// the page. It also replaces the values that change on every run (timestamps, trace
// and span ids) with stable placeholders, numbering each distinct id.
import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8

final class PageOutput extends OutputStream {
  private val line   = new ByteArrayOutputStream
  private val labels = scala.collection.mutable.Map.empty[String, String]

  private def label(kind: String, id: String): String =
    labels.getOrElseUpdate(id, s"<$kind-${labels.values.count(_.startsWith(s"<$kind-")) + 1}>")

  private def mask(text: String): String = {
    val noTime = text
      .replaceAll("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z", "<timestamp>")
      .replaceAll("\"timeUnixNano\":\"\\d+\"", "\"timeUnixNano\":\"<time>\"")
    val noTrace = "\"traceId\":\"([0-9a-f]{32})\"".r
      .replaceAllIn(noTime, m => s""""traceId":"${label("trace-id", m.group(1))}"""")
    "\"spanId\":\"([0-9a-f]{16})\"".r
      .replaceAllIn(noTrace, m => s""""spanId":"${label("span-id", m.group(1))}"""")
  }

  override def write(b: Int): Unit =
    if (b == '\n') { Console.out.println(mask(new String(line.toByteArray, UTF_8))); line.reset() }
    else line.write(b)
}

System.getProperties.put("docs.telemetry.realOut", System.out)
System.getProperties.put("docs.telemetry.realErr", System.err)
val pageStream = new PrintStream(new PageOutput, true, "UTF-8")
System.setOut(pageStream)
System.setErr(pageStream)
```

Welcome! This tutorial is for Scala 3 developers who are new to the ZIO Blocks `telemetry` module. You need to know how to write an `object` with a `def main` and how to add a dependency to `build.sbt`. You don't need any prior experience with logging libraries, tracing or OpenTelemetry to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How the global `log`, `trace` and `metric` objects let you record telemetry with zero setup
- How to attach structured fields to log lines, and how to switch the output to JSON on standard error
- How spans measure the steps of a job, how they nest, and how every log line written inside a span carries the span's ids
- How a counter and a histogram summarize what happened across many runs
- How to read recorded spans back with `trace.collectedSpans` and assert on them, so instrumentation can't silently break

We learn these concepts by instrumenting a bookstore's nightly restock job, one step at a time. The job restocks three books: Zionomicon, Programming in Scala, and Functional Programming in Scala (the counts are made up).

1. Your first log line
2. Add structured fields
3. Switch to JSON logs on standard error
4. Trace the job with spans
5. Count restocked books
6. Time each step
7. Test the instrumentation

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the `telemetry` module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-telemetry" % "@VERSION@"
scalacOptions += "-experimental"
```

On the JVM, the `telemetry` module needs **JDK 21 or newer**. It is compiled for Java 21, so on JDK 17 the compiler fails with `java.lang.UnsupportedClassVersionError` as soon as it expands a `log.*` call (the log macros run inside the compiler), and code without log calls compiles but fails the same way when it runs. JDK 21 needs no extra flags such as `--enable-preview`. [How to Add ZIO Blocks to Your Build](../getting-started/installation.md) explains the two artifact names and the JDK each module needs. The code in this tutorial is Scala 3.

:::caution
In version 0.0.56, the `log.*` calls don't compile on Scala 2.13 outside the library's own package: the compiler reports `object GlobalLogState in package telemetry cannot be accessed`. This is a known bug in that version. Spans and metrics are not affected.
:::

## 2. Background: Three Signals

When a program runs unattended — a nightly job, a server, a queue consumer — you can't watch it with a debugger. You find out what it did from the records it leaves behind. Observability tools split those records into three kinds of signal, each answering a different question:

- A **log** is a timestamped line that says *what happened*: "restock started", "title restocked".
- A **trace** says *where the time went*. It is a tree of **spans**: one span per unit of work, with a start time, an end time and a parent. A span for the whole job contains one span for each of its steps.
- A **metric** says *how many* or *how much*, summed over time: books restocked per title, or how long each step usually takes.

The ZIO Blocks `telemetry` module follows the OpenTelemetry data model for all three, and gives you one global entry point per signal: `log`, `trace` and `metric`. They work as soon as the module is on the classpath. Logs go to standard output, and spans and metrics are kept in memory, where your program and your tests can read them. The [Telemetry Guide](./telemetry-guide.md) explains how the pieces fit together; this tutorial focuses on using them.

## 3. Your First Log Line

Let's start the restock job with a single log line. The `log` object has one method per severity level — `trace`, `debug`, `info`, `warn`, `error` and `fatal` — and each one takes the message as a `String`:

```scala mdoc:reset
import zio.blocks.telemetry._

object RestockJob {
  def run(): Unit =
    log.info("Nightly restock started")
}

RestockJob.run()
```

The code above:

- `import zio.blocks.telemetry._` brings `log`, `trace` and `metric` into scope, along with every type this tutorial uses.
- `log.info("Nightly restock started")` writes one line at the `INFO` level. There is no logger to create and nothing to configure first.

The line goes to standard output, shown above as a comment under the call. Each line has four parts: the time in UTC (shown here as `<timestamp>`, because it changes on every run), the severity padded to five characters, the source location in square brackets, and the message. The `log.*` methods are macros, so the source location is filled in at compile time — the class, the method and the line number of the call — at no runtime cost.

:::caution
The brackets should hold the short class name, the method and the line, such as `[RestockJob$.run:5]`. In version 0.0.56, a class inside a package prints its full name followed by two numbers instead, as in `[(bookstore.RestockJob$,10,21).run:5]`. On this page, the class also sits inside the `repl.MdocSession` wrapper that renders the examples. This is a known bug in that version; the message and fields are not affected.
:::

## 4. Add Structured Fields

A message such as "Restocked 12 copies of Zionomicon" is easy for a person to read, but a log search tool has to parse the text to find the title. Structured fields keep the values separate. Pass them after the message as `key -> value` pairs; keys are strings, and values can be a `String`, an `Int`, a `Long`, a `Double` or a `Boolean`:

```scala mdoc:reset
import zio.blocks.telemetry._

object RestockJob {
  val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

  def run(): Unit =
    log.annotated("job.id" -> "restock-2026-10-21") {
      log.info("Nightly restock started")
      stock.foreach { case (title, copies) =>
        log.info("Restocked a title", "title" -> title, "copies" -> copies)
      }
    }
}

RestockJob.run()
```

The code above:

- `stock` lists each title with the number of copies to add.
- `log.annotated("job.id" -> "restock-2026-10-21") { ... }` attaches the `job.id` field to every log line written while the block runs, including lines written by methods it calls. Annotations take `String` values.
- `log.info("Restocked a title", "title" -> title, "copies" -> copies)` adds two fields to one line. The `Int` value is stored as a whole number, not as text.

Notice that the fields appear in braces after the message: strings in quotes, numbers without them. The annotation comes first, followed by the fields of the call itself. Annotations are the way to tag a whole unit of work, such as a job run or a request, without passing the id to every method.

## 5. Switch to JSON Logs on Standard Error

Text lines are pleasant in a terminal, but log collectors prefer one JSON object per line, and many deployments keep standard output for the program's real output. Two calls switch the global log output to JSON on standard error:

```scala mdoc:reset
import zio.blocks.telemetry._

log.install(LoggerProvider.builder.build().get("bookstore.restock"))
log.writer(JsonLogFormatter, StderrWriter)

object RestockJob {
  def run(): Unit =
    log.info("Nightly restock started", "titles" -> 3)
}

RestockJob.run()
```

The code above:

- `LoggerProvider.builder.build().get("bookstore.restock")` creates a logger with no outputs of its own; `"bookstore.restock"` names the part of the program it logs for.
- `log.install(...)` makes that logger the global one. This removes the default text output to standard output.
- `log.writer(JsonLogFormatter, StderrWriter)` adds an output that formats each line as JSON and writes it to standard error.

Each line is now an OpenTelemetry log record in JSON. The time is in `timeUnixNano`, in nanoseconds since 1970 (shown here as `<time>`). The message is under `body`, the severity is under `severityNumber` and `severityText`, and the source location becomes the `code.*` attributes, next to your own `titles` field. Whole numbers are written as strings (`"intValue":"3"`), as the OpenTelemetry JSON encoding requires.

:::note
`log.writer` adds an output; it never replaces one. Without the `log.install` call first, the default text output would stay, and every line would be printed twice: once as text and once as JSON. The setup is global, so the rest of this tutorial logs JSON to standard error.
:::

## 6. Trace the Job with Spans

Logs tell us what happened, but not how long each step took or which step a line belongs to. A **span** answers both. `trace.span(name) { span => ... }` starts a span, runs the block, and ends the span when the block finishes, even if it throws. A span started inside another span's block becomes its child, so the code's nesting becomes the trace's tree.

Let's give the restock job one root span and one child span per step:

```scala mdoc:reset
import zio.blocks.telemetry._

object RestockJob {
  val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

  def run(): Unit =
    trace.span("restock") { _ =>
      trace.span("load-inventory") { _ =>
        log.info("Inventory loaded", "titles" -> stock.size)
      }
      trace.span("restock-titles") { span =>
        span.setAttribute("copies", stock.map(_._2).sum.toLong)
      }
      trace.span("write-report") { span =>
        log.info("Report written")
        span.setStatus(SpanStatus.Ok)
      }
    }
}

RestockJob.run()
```

The code above:

- `trace.span("restock") { _ => ... }` is the root span; it covers the whole job.
- The three inner `trace.span` calls are its children: one for each step.
- `span.setAttribute("copies", ...)` records a value on the span itself; spans take the same kinds of values as log fields.
- `span.setStatus(SpanStatus.Ok)` marks the step as successful. A span you don't mark keeps the status `Unset`, which also counts as "no error"; `SpanStatus.Error("reason")` marks a failure.

Look closely at the two log lines. Both carry the same `traceId`, because they belong to the same run of the job. Each one carries the `spanId` of the span it was written in, and the ids differ because the lines came from different steps. (Real ids are random hexadecimal strings; this page shows them as numbered placeholders, and placeholders with the same number stand for the same id.) We didn't pass any id to `log.info`: the active span is tracked for the current call stack, and the log macros read it. That is what lets a log tool show "all the lines of this run" or "the lines of this step".

Now let's look at the spans themselves. They are not printed anywhere yet: by default, the global tracer keeps finished spans in memory, and `trace.collectedSpans` returns them as `SpanData` values:

```scala mdoc
def printSpans(): Unit = {
  val spans  = trace.collectedSpans
  val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap

  spans.foreach { s =>
    val parent = nameOf.getOrElse(s.parentSpanContext.spanId, "none")
    val timed  = s.endTimeNanos >= s.startTimeNanos
    println(s"${s.name}: parent=$parent, status=${s.status}, timed=$timed, attributes=${s.attributes.toMap}")
  }
}

printSpans()
```

The code above:

- `trace.collectedSpans` returns the finished spans, in the order they ended.
- `nameOf` maps each span's id to its name, so we can print a parent's name instead of its id.
- `s.parentSpanContext.spanId` is the id of the parent span. The root span has no parent, so its parent id matches no span and we print `none`.
- `timed` checks that the span recorded both a start and an end time. We don't print the durations themselves, because they differ on every run.

Notice the order: the children come first, because each child ends before its parent does. Every span shares the root's trace id, and each child's parent is `restock`.

## 7. Count Restocked Books

Spans describe one run. Many questions are about all runs together: how many copies of Zionomicon did we restock this month? A **counter** answers that. It is a running total that only goes up, and each `Counter#add` call can carry attributes, so one counter keeps a separate total for each title.

Let's count the restocked copies:

```scala mdoc:reset
import zio.blocks.telemetry._

object RestockJob {
  val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

  val booksRestocked: Counter = metric.counter("bookstore.books.restocked")

  def run(): Unit =
    trace.span("restock") { _ =>
      trace.span("load-inventory") { _ =>
        log.info("Inventory loaded", "titles" -> stock.size)
      }
      trace.span("restock-titles") { _ =>
        stock.foreach { case (title, copies) =>
          booksRestocked.add(copies.toLong, "title" -> title)
        }
      }
      trace.span("write-report") { span =>
        log.info("Report written")
        span.setStatus(SpanStatus.Ok)
      }
    }
}

RestockJob.run()
```

The code above:

- `metric.counter("bookstore.books.restocked")` creates a counter. We create it once, as a `val`, and reuse it: every call to `metric.counter` registers a new instrument, even with a name that already exists.
- `booksRestocked.add(copies.toLong, "title" -> title)` adds the copies to the total for that title. The `title` attribute splits the counter into one series per title.

To read the totals, ask the counter for its current data. A counter's data is a `MetricData.SumData` with one point per distinct set of attributes:

```scala mdoc
val titleKey = AttributeKey.string("title")

RestockJob.booksRestocked.collect() match {
  case MetricData.SumData(points) =>
    points.sortBy(_.attributes.get(titleKey)).foreach { p =>
      println(s"${p.attributes.get(titleKey).getOrElse("?")}: ${p.value}")
    }
  case other => println(s"unexpected: $other")
}
```

The code above:

- `AttributeKey.string("title")` is a typed key: `attributes.get(titleKey)` returns an `Option[String]`.
- `Counter#collect` returns a snapshot of the counter's totals.
- `points.sortBy(...)` sorts the series by title, so the output has a stable order.

Each title has its own total. Run the job again and the totals grow: a counter keeps adding up for as long as the program runs.

## 8. Time Each Step

Our spans already record how long each step took in one run. To know how long a step *usually* takes — and how often it is slow — we need the durations of many runs summarized. A **histogram** does that: each recorded value falls into a bucket, and the histogram keeps the count, sum, minimum and maximum of the values.

Let's time each step with a small helper that wraps a step in its span and records its duration in milliseconds:

```scala mdoc:reset
import zio.blocks.telemetry._

object RestockJob {
  val stock = List("Zionomicon" -> 12, "Programming in Scala" -> 5, "Functional Programming in Scala" -> 8)

  val booksRestocked: Counter = metric.counter("bookstore.books.restocked")
  val stepDuration: Histogram = metric.histogram("bookstore.restock.step.duration.ms")

  def step[A](name: String)(body: Span => A): A =
    trace.span(name) { span =>
      val start = System.nanoTime()
      try body(span)
      finally stepDuration.record((System.nanoTime() - start) / 1e6, "step" -> name)
    }

  def run(): Unit =
    trace.span("restock") { _ =>
      step("load-inventory") { _ =>
        log.info("Inventory loaded", "titles" -> stock.size)
      }
      step("restock-titles") { _ =>
        stock.foreach { case (title, copies) =>
          booksRestocked.add(copies.toLong, "title" -> title)
        }
      }
      step("write-report") { span =>
        log.info("Report written")
        span.setStatus(SpanStatus.Ok)
      }
    }
}

RestockJob.run()
RestockJob.run()
```

The code above:

- `metric.histogram("bookstore.restock.step.duration.ms")` creates a histogram once, like the counter. We put the unit in the name, because `metric.histogram` takes only a name.
- `step(name) { span => ... }` opens a span for the step and measures the time the body takes. The `finally` records the duration even when the body throws.
- `stepDuration.record(ms, "step" -> name)` adds one value to the series for that step.
- We run the job twice, so each step records two durations.

Now let's read the histogram:

```scala mdoc
val stepKey = AttributeKey.string("step")

RestockJob.stepDuration.collect() match {
  case MetricData.HistogramData(points) =>
    points.sortBy(_.attributes.get(stepKey)).foreach { p =>
      println(s"${p.attributes.get(stepKey).getOrElse("?")}: count=${p.count}, buckets=${p.boundaries.length + 1}")
    }
  case other => println(s"unexpected: $other")
}
```

The code above:

- `MetricData.HistogramData(points)` holds one point per step.
- `p.count` is the number of recorded values: two per step, one per run.
- `p.boundaries` are the bucket limits. The defaults suit milliseconds, from 0 up to 10,000; there is one more bucket than there are boundaries, for values above the last limit. We don't print `p.sum`, `p.min` or `p.max` here, because the durations differ on every run.

The histogram doesn't keep the individual durations. It keeps enough to answer "how many runs, how long in total, and how many fell in each range" for any number of runs, in a fixed amount of memory.

## 9. Test the Instrumentation

Instrumentation is code, and code breaks. Someone renames a step or moves a call outside its span, and dashboards go quiet without any error. Because spans are kept in memory, a test can run the job and check the trace's shape.

Let's write that check as a plain function and run it:

```scala mdoc:silent
def spanTree(): List[(String, String)] = {
  val spans  = trace.collectedSpans
  val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap
  spans.map(s => s.name -> nameOf.getOrElse(s.parentSpanContext.spanId, "none"))
}

val expected = List(
  "load-inventory" -> "restock",
  "restock-titles" -> "restock",
  "write-report"   -> "restock",
  "restock"        -> "none"
)
```

The code above:

- `spanTree()` turns the collected spans into `(name, parent name)` pairs, which leave out everything that changes between runs: ids, timestamps and durations.
- `expected` is the tree we want: three steps, each a child of `restock`, listed in the order they end.

Now let's run the job and check its trace:

```scala mdoc
trace.clearSpans()
RestockJob.run()

assert(spanTree() == expected, s"unexpected spans: ${spanTree()}")
println(s"Instrumentation check passed: ${expected.size} spans")
```

The code above:

- `trace.clearSpans()` empties the in-memory store first, so spans from earlier runs don't leak into the check. Call it at the start of every test that reads `trace.collectedSpans`.
- `assert(...)` compares the whole tree. If a step is renamed, dropped or moved out of `restock`, the check fails and prints the tree it found.

In a ZIO Test spec, the same check is `assertTrue(spanTree() == expected)`.

```scala mdoc:invisible
// Restore the streams the page plumbing replaced.
System.setOut(System.getProperties.get("docs.telemetry.realOut").asInstanceOf[java.io.PrintStream])
System.setErr(System.getProperties.get("docs.telemetry.realErr").asInstanceOf[java.io.PrintStream])
```

## 10. Putting It Together

This example combines all the concepts we've learned: a restock job with structured JSON logs on standard error, a span per step, a counter per title, a histogram per step, and a check on the trace's shape.

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/CompleteExample.scala
```

## 11. Running the Examples

All the code from this tutorial is available as runnable examples in the `telemetry-examples` module. They need JDK 21 or newer.

To run the examples, clone the ZIO Blocks repository and navigate to the examples module:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/telemetry-examples
```

Each example below is self-contained. Expand one to see its code, then run it with the command beneath it.

<details>
<summary><strong>1. Your first log line</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept1FirstLogExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept1FirstLogExample"
```

<details>
<summary><strong>2. Add structured fields</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept2StructuredFieldsExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept2StructuredFieldsExample"
```

<details>
<summary><strong>3. Switch to JSON logs on standard error</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept3JsonLogsExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept3JsonLogsExample"
```

<details>
<summary><strong>4. Trace the job with spans</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept4SpansExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept4SpansExample"
```

<details>
<summary><strong>5. Count restocked books</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept5CounterExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept5CounterExample"
```

<details>
<summary><strong>6. Time each step</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept6HistogramExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept6HistogramExample"
```

<details>
<summary><strong>7. Test the instrumentation</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/instrumentaprogramwithlogstracesandmetrics/Concept7TestSpansExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.Concept7TestSpansExample"
```

To run the complete example:

```bash
sbt "runMain instrumentaprogramwithlogstracesandmetrics.CompleteExample"
```

To check that every example compiles without running any of them:

```bash
sbt compile
```

## 12. What You've Learned

In this tutorial, you learned:

- How the global `log`, `trace` and `metric` objects record telemetry with zero setup
- How to attach structured fields with `key -> value` pairs and tag a whole unit of work with `log.annotated`
- How `log.install` and `log.writer` switch the output to JSON on standard error
- How `trace.span` measures each step, how spans nest into a tree, and how log lines written inside a span carry its trace and span ids
- How a counter keeps a total per attribute, and how a histogram summarizes durations into buckets
- How to read spans back with `trace.collectedSpans`, reset them with `trace.clearSpans`, and assert on the shape of a trace

You now have a solid foundation in recording and inspecting telemetry. To ship it to a backend such as an OpenTelemetry Collector, you write a small exporter of your own: the module's built-in OTLP exporters aren't public yet, but the pieces they are built from are. [Building an Exporter](../reference/telemetry/otel/custom-exporter.md) shows how to encode spans, logs and metrics with `OtlpJsonEncoder` and send them with `HttpSender`.

## 13. Where to Go Next

- **Want to know how it works inside?** The [Telemetry Guide](./telemetry-guide.md) is a deep dive into the module's architecture: how the log macros keep disabled levels nearly free, how sampling decides which spans are recorded, how span context propagates, and what is safe across threads.
- **Want to dive deeper into the API?** Start at the [Telemetry reference](../reference/telemetry/index.md), then read the pages for [logging](../reference/telemetry/logging/index.md), [tracing](../reference/telemetry/tracing/index.md) and [metrics](../reference/telemetry/metrics/index.md).
- **Need labels bound up front?** The [labeled instruments](../reference/telemetry/metrics/labeled-instruments.md) page shows counters and histograms that take attribute values by position.
