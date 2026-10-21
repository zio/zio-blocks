---
id: test-telemetry
title: "How to Test Code That Logs, Traces and Records Metrics"
description: "Capture spans, log records and counter values in memory during tests, assert on them, and keep tests from leaking into each other."
keywords:
  - "Testing"
  - "Testing Telemetry"
  - "Observability"
  - "SpanProcessor"
  - "LogRecordProcessor"
  - "Counter"
---

In this guide, we test the telemetry of a bookstore's order service: when a payment fails, the service must log an `ERROR`, mark its span as failed and add one to the `orders.failed` counter. We capture every span, log record and counter value in memory while a test runs, then assert on them like any other value. By the end, you will have a ZIO Test spec whose tests each record their own telemetry, so they can run in parallel without seeing each other's spans, logs or counts.

We take an incremental approach:

- Record the service's spans and assert on their names, attributes, status and parent.
- Capture its log records and assert on their severity, message and fields.
- Read its counters back and assert on their totals.
- Bundle the three into a per-test kit and use it in a ZIO Test spec.
- Test code that uses the global `log`, `trace` and `metric` objects, and reset them safely between tests.

## The Problem

Telemetry is often a real requirement. "When a payment fails, log an `ERROR` and bump `orders.failed`" is a rule that an on-call engineer's alert depends on. Yet it usually goes untested, because the output vanishes into the console or a monitoring system, and the only way to check it is to read text that was meant for people.

For example, a test of an order service that logs with `println` has to capture standard output and search it:

```scala mdoc:compile-only
import java.io.ByteArrayOutputStream

object OrderLog {
  def placeOrder(orderId: String, declined: Boolean): Unit =
    if (declined) println(s"ERROR Payment failed for $orderId")
    else println(s"INFO Order placed: $orderId")
}

val out = new ByteArrayOutputStream
Console.withOut(out)(OrderLog.placeOrder("order-2", declined = true))
assert(out.toString.contains("ERROR"))
```

This test breaks when someone changes the line format, passes when "ERROR" appears anywhere in the output, and sees the output of every other test that prints at the same time. It also can't check anything that isn't text: how long a step took, which step a line belongs to, or how many orders failed.

In this guide, we solve this by giving the code under test a tracer, a logger and a meter that record into memory, so a test reads back typed values: `SpanData`, `LogRecord` and `MetricData`.

## Prerequisites

Add the `telemetry` module and ZIO Test to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-telemetry" % "@VERSION@"
scalacOptions += "-experimental"

// Either way
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"     % "2.1.27" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
)
```

The `telemetry` module needs **JDK 21 or newer** on the JVM, to compile as well as to run. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names and the JDK each module needs.

The order service and its tests compile on Scala 2.13 and Scala 3. The section on the global `log` object is Scala 3 only: in version 0.0.56, the `log.*` calls don't compile on Scala 2.13 outside the library's own package, a known bug in that version.

We assume you have seen the `log`, `trace` and `metric` objects before. If not, the tutorial [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md) introduces them.

## The Order Service Under Test

The service places one order: it charges the customer through a `PaymentGateway`, then records the outcome three ways. It runs inside a `placeOrder` span with a `charge` child span, logs one line, and adds one to `orders.placed` or `orders.failed`.

The key design choice is that the service receives its `Tracer`, `Logger` and `Meter` as constructor parameters instead of reaching for the global objects. The [tracing reference](../reference/telemetry/tracing/index.md) recommends this for library code, and the [`Logger`](../reference/telemetry/logging/logger.md) page says why: then telemetry is an ordinary dependency, and a test can pass in one whose output it inspects.

Here is the model and the service:

```scala mdoc:silent
import zio.blocks.telemetry._

final case class Order(id: String, title: String, copies: Int, priceCents: Long) {
  def totalCents: Long = copies * priceCents
}

trait PaymentGateway {
  def charge(orderId: String, amountCents: Long): Either[String, String]
}

object PaymentGateway {
  val approveAll: PaymentGateway = (orderId, _) => Right(s"receipt-$orderId")

  def declineAll(reason: String): PaymentGateway = (_, _) => Left(reason)
}

final class OrderService(payments: PaymentGateway, tracer: Tracer, logger: Logger, meter: Meter) {
  val ordersPlaced: Counter = meter.counterBuilder("orders.placed").build()
  val ordersFailed: Counter = meter.counterBuilder("orders.failed").build()

  def placeOrder(order: Order): Either[String, String] =
    tracer.span("placeOrder") { span =>
      span.setAttribute("order.id", order.id)
      span.setAttribute("book.title", order.title)

      val charged = tracer.span("charge") { chargeSpan =>
        chargeSpan.setAttribute("amount.cents", order.totalCents)
        payments.charge(order.id, order.totalCents)
      }

      charged match {
        case Right(receipt) =>
          ordersPlaced.add(1L, "title" -> order.title)
          logger.info(
            "Order placed",
            "order.id" -> AttributeValue.StringValue(order.id),
            "receipt"  -> AttributeValue.StringValue(receipt)
          )
          span.setStatus(SpanStatus.Ok)
        case Left(reason) =>
          ordersFailed.add(1L, "reason" -> reason)
          logger.error(
            "Payment failed",
            "order.id" -> AttributeValue.StringValue(order.id),
            "reason"   -> AttributeValue.StringValue(reason)
          )
          span.setStatus(SpanStatus.Error(reason))
      }
      charged
    }
}
```

The code above:

- `PaymentGateway.approveAll` and `PaymentGateway.declineAll` are two fake gateways for the tests; a real one would call a payment provider.
- The counters are public `val`s, created once from the meter. A test reads them back, and keeping one instance per counter matters: every call to a counter builder registers a new instrument.
- `Logger#info` and `Logger#error` take their fields as `(String, AttributeValue)` pairs. Unlike the global `log.*` calls, they are plain methods, so they also compile on Scala 2.13.
- `SpanStatus.Error(reason)` marks the span as failed. Nothing sets it for us: a span whose block throws still ends with the status `Unset`.

In production, the service gets the global tracer and meter, and a logger that prints to standard output:

```scala mdoc:compile-only
val consoleLogger: Logger =
  LoggerProvider.builder
    .addLogRecordProcessor(new ConsoleLogRecordProcessor)
    .build()
    .get("bookstore.orders")

val orders = new OrderService(
  PaymentGateway.approveAll,
  trace.get("bookstore.orders"),
  consoleLogger,
  metric.get("bookstore.orders")
)
```

`trace.get` and `metric.get` return a tracer and a meter from whatever provider is installed when they run, so build the service after any `trace.install` or `metric.install` call at startup.

## Record Spans in Memory

A `TracerProvider` sends every finished span to its `SpanProcessor`s. The default provider behind `trace` keeps them in an in-memory buffer, but that buffer is internal and shared by the whole program. A test needs a buffer of its own, so we write a processor that keeps the spans in a queue:

```scala mdoc:silent
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

final class SpanRecorder extends SpanProcessor {
  private val ended = new ConcurrentLinkedQueue[SpanData]()

  def onStart(span: Span): Unit       = ()
  def onEnd(spanData: SpanData): Unit = { ended.add(spanData); () }
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()

  def spans: List[SpanData] = ended.asScala.toList

  def tree: List[(String, Option[String])] = {
    val nameOf = spans.map(s => s.spanContext.spanId -> s.name).toMap
    spans.map(s => s.name -> nameOf.get(s.parentSpanContext.spanId))
  }
}
```

The code above:

- `onEnd` receives each span as a `SpanData` once it has ended. We ignore `onStart`, because a test only cares about finished spans.
- `ConcurrentLinkedQueue` keeps the spans in the order they ended and is safe to write from any thread.
- `SpanRecorder#tree` pairs each span's name with its parent's name, or `None` for a root span. Ids and timestamps change on every run, so this leaves only what a test should assert on.

Next, we build a tracer that sends its spans to a recorder, give it to the service, and place an order that the gateway declines. The logger and meter here record nothing; we use them in the next sections:

```scala mdoc:silent
val spanRecorder = new SpanRecorder

val tracedOrders = new OrderService(
  PaymentGateway.declineAll("card declined"),
  TracerProvider.builder.addSpanProcessor(spanRecorder).build().get("test"),
  LoggerProvider.builder.build().get("test"),
  MeterProvider.builder.build().get("test")
)

val bookTitle = AttributeKey.string("book.title")

def placeOrderSpan: Option[SpanData] = spanRecorder.spans.find(_.name == "placeOrder")
```

`AttributeKey.string("book.title")` is a typed key: `Attributes#get` with it returns an `Option[String]`. Use `AttributeKey.long` for a `Long` attribute such as `amount.cents`.

Now let's place the order and look at the spans it recorded:

```scala mdoc
tracedOrders.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

println(placeOrderSpan.map(_.status))
println(placeOrderSpan.flatMap(_.attributes.get(bookTitle)))
println(spanRecorder.tree)
```

The code above:

- `SpanData#status` is the status the service set: `Error(card declined)`.
- `spanRecorder.tree` lists `charge` first, because a child span ends before its parent, and shows that `charge` is a child of `placeOrder`.

In a ZIO Test spec, each of these becomes one line of an `assertTrue`, such as `placeOrderSpan.map(_.status) == Some(SpanStatus.Error("card declined"))`. The [`SpanData`](../reference/telemetry/tracing/span-data.md) page lists everything else a span records, including its events and start and end times.

:::warning
Only spans started with `Tracer#span` (or `trace.span`) reach the processors. A span started by hand with `Tracer#spanBuilder` and `SpanBuilder#startSpan` is never recorded, so a test can't see it.
:::

## Capture Log Records

A `LoggerProvider` sends every log record to its `LogRecordProcessor`s, so capturing logs works the same way as capturing spans. The processor receives the whole `LogRecord`, not a formatted line, so a test can assert on each part separately:

```scala mdoc:silent
final class LogRecorder extends LogRecordProcessor {
  private val emitted = new ConcurrentLinkedQueue[LogRecord]()

  def onEmit(logRecord: LogRecord): Unit = { emitted.add(logRecord); () }
  def shutdown(): Unit                   = ()
  def forceFlush(): Unit                 = ()

  def records: List[LogRecord] = emitted.asScala.toList
}
```

A `LogWriter` would also work as an output, but it receives the formatted text, and asserting on text brings back every problem from the start of this guide.

This time we record both spans and logs, so we can also check that the log line belongs to the `placeOrder` span:

```scala mdoc:silent
val logSpans    = new SpanRecorder
val logRecorder = new LogRecorder

val loggedOrders = new OrderService(
  PaymentGateway.declineAll("card declined"),
  TracerProvider.builder.addSpanProcessor(logSpans).build().get("test"),
  LoggerProvider.builder.addLogRecordProcessor(logRecorder).build().get("test"),
  MeterProvider.builder.build().get("test")
)

val reason = AttributeKey.string("reason")

def placeOrderContext: Option[SpanContext] =
  logSpans.spans.find(_.name == "placeOrder").map(_.spanContext)
```

Let's place the declined order and read the log record back:

```scala mdoc
loggedOrders.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

logRecorder.records.foreach { r =>
  val inSpan = placeOrderContext.exists { s =>
    r.spanId == s.spanId.value && r.traceIdHi == s.traceIdHi && r.traceIdLo == s.traceIdLo
  }
  println(s"${r.severity} ${r.body.value} reason=${r.attributes.get(reason)}")
  println(s"in placeOrder span: $inSpan")
}
```

The code above:

- `LogRecord#severity` is a `Severity` value, here `Severity.Error`, so a test compares it with `==` instead of parsing a level name.
- `LogRecord#body` holds the message; `body.value` is its text.
- `LogRecord#attributes` holds the fields, read with the same typed keys as span attributes.
- `LogRecord#spanId`, `LogRecord#traceIdHi` and `LogRecord#traceIdLo` identify the span that was active when the line was written. The tracer and the logger share the active span without any setup, so the record carries the ids of `placeOrder`.

Assert on the severity, the message and the fields, never on a formatted line. Formatted lines also carry a timestamp, and in version 0.0.56 the text formatter prints a class inside a package with its full name and two numbers in the location brackets, a known bug in that version.

## Read a Counter's Value

A counter keeps a running total per distinct set of attributes. `Counter#collect` returns a snapshot of those totals as `MetricData.SumData`, with one point per series. We add the points that match the attributes we care about:

```scala mdoc:silent
def total(counter: Counter, attributes: (String, String)*): Long =
  counter.collect() match {
    case MetricData.SumData(points) =>
      points.filter { p =>
        attributes.forall { case (key, value) => p.attributes.get(AttributeKey.string(key)).contains(value) }
      }
        .map(_.value)
        .sum
    case _ => 0L
  }
```

We read the counter through its own instance because `MetricData` carries no instrument name. `MeterProvider#reader` returns the data of every instrument on the provider, and nothing in it says which point belongs to `orders.failed`.

Next, we give a service a meter of its own, so its counters start at zero, and place three orders, one of them declined:

```scala mdoc:silent
val countedOrders = new OrderService(
  (orderId, _) => if (orderId == "order-2") Left("card declined") else Right(s"receipt-$orderId"),
  TracerProvider.builder.build().get("test"),
  LoggerProvider.builder.build().get("test"),
  MeterProvider.builder.build().get("test")
)
```

Let's place the orders and read the totals:

```scala mdoc
countedOrders.placeOrder(Order("order-1", "Zionomicon", 1, 4500L))
countedOrders.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))
countedOrders.placeOrder(Order("order-3", "Functional Programming in Scala", 1, 4800L))

println(s"placed: ${total(countedOrders.ordersPlaced)}")
println(s"placed, Zionomicon: ${total(countedOrders.ordersPlaced, "title" -> "Zionomicon")}")
println(s"failed, card declined: ${total(countedOrders.ordersFailed, "reason" -> "card declined")}")
```

The gateway here is a lambda that declines only `order-2`. Each counter's total is now exact, because nothing else in the program writes to it. A histogram works the same way: `Histogram#collect` returns `MetricData.HistogramData`, whose points carry a `count`, a `sum` and the bucket counts. The [`MetricData`](../reference/telemetry/metrics/metric-data.md) page describes every point type.

## Give Every Test Its Own Telemetry

ZIO Test runs the tests of a suite in parallel by default. Tests that share one span buffer, one logger or one counter see each other's output, and fail at random depending on timing. The fix is to build the tracer, logger and meter inside each test, so each test owns everything it records. We bundle them into one class:

```scala mdoc:silent
final class TestTelemetry {
  val spanRecorder = new SpanRecorder
  val logRecorder  = new LogRecorder

  val tracer: Tracer = TracerProvider.builder.addSpanProcessor(spanRecorder).build().get("test")
  val logger: Logger = LoggerProvider.builder.addLogRecordProcessor(logRecorder).build().get("test")
  val meter: Meter   = MeterProvider.builder.build().get("test")

  def spans: List[SpanData]                    = spanRecorder.spans
  def logs: List[LogRecord]                    = logRecorder.records
  def spanTree: List[(String, Option[String])] = spanRecorder.tree
}
```

To show that two kits don't share anything, let's create two:

```scala mdoc:silent
val first  = new TestTelemetry
val second = new TestTelemetry
```

Then we run one order through each and print what each kit recorded:

```scala mdoc
new OrderService(PaymentGateway.approveAll, first.tracer, first.logger, first.meter)
  .placeOrder(Order("order-1", "Zionomicon", 1, 4500L))
new OrderService(PaymentGateway.declineAll("card declined"), second.tracer, second.logger, second.meter)
  .placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

println(s"first:  ${first.logs.map(_.body.value)} ${first.spans.map(_.status)}")
println(s"second: ${second.logs.map(_.body.value)} ${second.spans.map(_.status)}")
```

Each kit holds only the telemetry of its own order. In a spec, every test starts with `val telemetry = new TestTelemetry` and builds the service from it; nothing needs to be reset afterwards, because nothing outlives the test. The complete spec is in [Putting It Together](#putting-it-together).

:::warning
The active span is tracked per call stack and is not passed to other threads. If the code under test starts a span on a plain `Thread` or a thread pool, that span still reaches the recorder, but as the root of a new trace instead of a child of the span that started the work. Assert on the tree only for work that runs on the calling thread.
:::

## Test Code That Uses the Global `log`, `trace` and `metric`

Application code often uses the global objects directly instead of taking a tracer, logger and meter. Such code can still be tested, but every test then shares one span buffer, one logger and every counter, so the tests must reset what they read and must not run at the same time. Here is a checkout written that way:

```scala mdoc:silent
object Checkout {
  val ordersFailed: Counter = metric.counter("checkout.orders.failed")

  def placeOrder(order: Order, payments: PaymentGateway): Either[String, String] =
    trace.span("checkout") { span =>
      span.setAttribute("order.id", order.id)
      val charged = payments.charge(order.id, order.totalCents)
      charged match {
        case Right(receipt) =>
          log.info("Order placed", "order.id" -> order.id, "receipt" -> receipt)
          span.setStatus(SpanStatus.Ok)
        case Left(reason) =>
          ordersFailed.add(1L, "reason" -> reason)
          log.error("Payment failed", "order.id" -> order.id, "reason" -> reason)
          span.setStatus(SpanStatus.Error(reason))
      }
      charged
    }
}
```

`Checkout.ordersFailed` is created once, in a `val`. Each call to `metric.counter` registers a new instrument, so a counter created inside `placeOrder` would scatter the count over many instruments and leave the test reading the wrong one.

Each test prepares the three globals in its own way. It empties the global span buffer with `trace.clearSpans()`, installs a logger that records into a fresh `LogRecorder` with `log.install`, and reads the counter's total before the call, because a global counter can't be reset:

```scala mdoc:silent
trace.clearSpans()
val globalLogs = new LogRecorder
log.install(LoggerProvider.builder.addLogRecordProcessor(globalLogs).build().get("test"))
val failedBefore = total(Checkout.ordersFailed)
```

Now let's run the checkout and read what it recorded:

```scala mdoc
Checkout.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L), PaymentGateway.declineAll("card declined"))

println(trace.collectedSpans.map(s => s.name -> s.status))
println(globalLogs.records.map(r => r.severity -> r.body.value))
println(s"orders.failed went up by ${total(Checkout.ordersFailed) - failedBefore}")
```

`trace.collectedSpans` returns the spans in the global buffer, which holds only this test's span because we cleared it first. `log.install` replaces the global logger, so the `log.error` call reaches the recorder and nothing is printed.

In a ZIO Test spec, these tests share state, so the suite must run them one at a time. Add `@@ TestAspect.sequential` to the suite, as `GlobalTelemetrySpec` in the companion examples does, and keep every test that touches the globals in that one suite.

:::danger
Never "clean up" the globals with `removeAll` or `install` the wrong way. Each of these breaks every test that runs after it in the same JVM:

- `trace.install(...)` and `trace.removeAll()` disconnect `trace.collectedSpans` for good. Spans go to the new provider (or nowhere, after `removeAll`), and no public call reconnects the in-memory buffer, so later tests read an empty list.
- `log.removeAll()` silences logging, and in version 0.0.56 a later `log.addProcessor` or `log.writer` doesn't bring it back, a known bug in that version. Use `log.install` at the start of each test instead; it replaces the previous test's logger.
:::

Two more limits apply to the global buffer. It holds the last 1,024 spans and drops the oldest, so read it right after the code under test, not at the end of a long suite. And `log.install` replaces the default console output for the rest of the run; if later code should print again, install a logger with a `ConsoleLogRecordProcessor`.

```scala mdoc:invisible
// Leave the globals as the next page expects them: an empty span buffer and console logging.
trace.clearSpans()
log.install(LoggerProvider.builder.addLogRecordProcessor(new ConsoleLogRecordProcessor).build().get("default"))
```

## Putting It Together

The complete spec tests the order service's telemetry for an approved order and a declined one. Every test builds its own `TestTelemetry`, so ZIO Test runs them in parallel. The helpers it uses, `SpanRecorder`, `LogRecorder`, `TestTelemetry` and `Metrics.total`, are in `TestTelemetry.scala` (see [Running the Examples](#running-the-examples)):

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/OrderServiceSpec.scala
```

## Running the Examples

All the code from this guide is available as runnable examples in the `telemetry-examples` module. They need JDK 21 or newer.

To run the examples, clone the ZIO Blocks repository and navigate to the examples module:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/telemetry-examples
```

Each example below is one file. Expand one to see its code, then run it with the command beneath it.

<details>
<summary><strong>1. The order service and its production wiring</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/testtelemetry/OrderService.scala:show-line-numbers
```

</details>

This places one approved and one declined order and prints their log lines:

```bash
sbt "runMain testtelemetry.OrderServiceApp"
```

<details>
<summary><strong>2. The in-memory recorders and the per-test kit</strong></summary>

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/TestTelemetry.scala:show-line-numbers
```

</details>

The specs below use these helpers; the file has nothing to run on its own.

<details>
<summary><strong>3. Record spans in memory</strong></summary>

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/RecordSpansSpec.scala:show-line-numbers
```

</details>

```bash
sbt "testOnly testtelemetry.RecordSpansSpec"
```

<details>
<summary><strong>4. Capture log records</strong></summary>

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/CaptureLogsSpec.scala:show-line-numbers
```

</details>

```bash
sbt "testOnly testtelemetry.CaptureLogsSpec"
```

<details>
<summary><strong>5. Read a counter's value</strong></summary>

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/ReadCountersSpec.scala:show-line-numbers
```

</details>

```bash
sbt "testOnly testtelemetry.ReadCountersSpec"
```

<details>
<summary><strong>6. Test code that uses the global objects</strong></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/testtelemetry/Checkout.scala:show-line-numbers
```

```scala mdoc:embed:telemetry-examples/src/test/scala/testtelemetry/GlobalTelemetrySpec.scala:show-line-numbers
```

</details>

```bash
sbt "testOnly testtelemetry.GlobalTelemetrySpec"
```

To run every spec, including the complete `OrderServiceSpec`:

```bash
sbt test
```

## Going Further

- **Learn the three signals step by step.** The tutorial [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md) adds logs, spans, a counter and a histogram to a batch job.
- **Look up the types.** The reference pages for [`SpanProcessor`](../reference/telemetry/tracing/span-processor.md), [`LogRecordProcessor`](../reference/telemetry/logging/log-record-processor.md), [`TracerProvider`](../reference/telemetry/tracing/tracer-provider.md), [`LoggerProvider`](../reference/telemetry/logging/logger-provider.md) and [`MeterProvider`](../reference/telemetry/metrics/meter-provider.md) list every method used here.
- **See how the module works inside.** The [Telemetry Guide](./telemetry-guide.md) explains sampling, span context propagation and what is safe across threads.
- **Test the rest of the service.** [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) gives every test its own database in the same per-test style.
