---
id: correlate-logs-and-traces
title: "How to Correlate Logs and Traces Across Services"
description: "Carry the trace id from a checkout service to a payment service in HTTP headers, so every log line of one request shares one trace id."
keywords:
  - "Distributed Tracing"
  - "Log Correlation"
  - "Trace Context Propagation"
  - "W3C traceparent"
  - "B3"
  - "OtelContext"
---

```scala mdoc:invisible
// Page plumbing, not part of the guide. The telemetry module writes log lines to
// System.out, which mdoc does not capture, so this routes it into the page. It also
// replaces the values that change on every run (timestamps, trace and span ids) with
// stable placeholders, numbering each distinct id. The `println` below does the same
// for the page's own output, so ids printed by the examples get the same labels.
import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8

final class PageOutput extends OutputStream {
  // Captured once: the payment service and the email worker log from other threads.
  private val target = Console.out
  private val line   = new ByteArrayOutputStream
  private val labels = scala.collection.mutable.Map.empty[String, String]

  private def label(kind: String, id: String): String =
    labels.getOrElseUpdate(id, s"<$kind-${labels.values.count(_.startsWith(s"<$kind-")) + 1}>")

  private def mask(text: String): String = {
    val noTime  = text.replaceAll("\"timeUnixNano\":\"\\d+\"", "\"timeUnixNano\":\"<time>\"")
    val noTrace = "\\b[0-9a-f]{32}\\b".r.replaceAllIn(noTime, m => label("trace-id", m.matched))
    "\\b[0-9a-f]{16}\\b".r.replaceAllIn(noTrace, m => label("span-id", m.matched))
  }

  override def write(b: Int): Unit = synchronized {
    if (b == '\n') { target.println(mask(new String(line.toByteArray, UTF_8))); line.reset() }
    else line.write(b)
  }
}

System.getProperties.put("docs.telemetry.realOut", System.out)
val pageStream = new PrintStream(new PageOutput, true, "UTF-8")
System.setOut(pageStream)
def println(x: Any): Unit = pageStream.println(x)
```

One request to a shop crosses two services: the checkout service takes the order and calls the payment service over HTTP. In this guide, we connect the logs and spans of both services, so that every log line written while handling one order carries the same trace id, whichever service wrote it. We use the `telemetry` module for logs and spans, the `otel` module's propagators to carry the trace context in HTTP headers, and the `http-model` module's `Request` as the HTTP message.

We take it in five moves:

- Share one context storage between the tracer and the logger, so log lines carry the active span's ids.
- Write the trace context into the checkout service's outgoing request as a W3C `traceparent` header.
- Read it back in the payment service and continue the same trace.
- Carry the span context to work that runs on another thread, with `OtelContext`.
- Switch the header format to B3, and handle requests that arrive without a usable header.

## The Problem

A log line from the payment service says "Card charged", and a log line from the checkout service says "Order received". When a customer reports a failed order, you need every line that belongs to that one order, from both services, and nothing else. Timestamps don't do it: under load, hundreds of orders interleave in the same second.

The usual home-grown fix is a request id that every function passes along by hand and every service copies into a custom header:

```scala mdoc:compile-only
import java.util.UUID

object Checkout {
  def placeOrder(orderId: String): Unit = {
    val requestId = UUID.randomUUID().toString
    println(s"[$requestId] checkout: order $orderId received")
    Payment.charge(orderId, Map("X-Request-Id" -> requestId))
    println(s"[$requestId] checkout: payment answered")
  }
}

object Payment {
  def charge(orderId: String, headers: Map[String, String]): Unit = {
    val requestId = headers.getOrElse("X-Request-Id", UUID.randomUUID().toString)
    println(s"[$requestId] payment: card charged for $orderId")
    Fraud.check(orderId, requestId)
  }
}

object Fraud {
  def check(orderId: String, requestId: String): Unit =
    println(s"[$requestId] fraud: order $orderId looks fine")
}
```

Every method signature now carries `requestId`, and every log call has to remember to print it. One forgotten parameter and that code's lines drop out of the search. The id says nothing about which step ran inside which, or how long each step took, and `X-Request-Id` is a private convention: a service written by another team, in another language, won't read it.

Tracing already solves the "which steps belong together" half: a trace is one id shared by all the spans of one request. What remains is to make the log lines carry the active span's ids, and to make the trace id cross the HTTP boundary in a header that every tracing system understands.

## Prerequisites

Add the `telemetry`, `telemetry-otel` and `http-model` modules to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-telemetry"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-telemetry-otel" % "@VERSION@",
  "dev.zio" %% "zio-blocks-http-model"     % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-telemetry"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-telemetry-otel" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-http-model"     % "@VERSION@"
)
scalacOptions += "-experimental"
```

`telemetry-otel` exists only for the JVM, and the `telemetry` module needs JDK 21 or newer. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names. The code in this guide is Scala 3.

:::caution
In version 0.0.56, the `log.*` calls don't compile on Scala 2.13 outside the library's own package. This is a known bug in that version. The propagators, `OtelContext` and spans work on Scala 2.13.
:::

We assume you know how to write log lines, open spans with `trace.span`, and switch the log output to JSON, as shown in [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md). The code below uses these imports:

```scala mdoc:silent
import java.util.concurrent.{CompletableFuture, Executors}
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._
import zio.http._
```

## Share One Context Storage Between Logs and Spans

A log line picks up trace and span ids from a `ContextStorage`: while a span's block runs, the tracer stores the span's context there, and the logger reads it each time it writes a line. The global `log` and `trace` objects already share a built-in storage, but that storage is private to the library. Continuing a trace that arrives in a request means putting the caller's context into the storage yourself, so create your own and hand the same instance to both the tracer and the logger:

```scala mdoc:silent
object Telemetry {
  val storage: ContextStorage[Option[SpanContext]] =
    ContextStorage.create[Option[SpanContext]](None)

  val tracer: Tracer =
    TracerProvider.builder.setContextStorage(storage).build().get("shop")

  def installLogger(): Unit = {
    log.install(LoggerProvider.builder.setContextStorage(storage).build().get("shop"))
    log.writer(JsonLogFormatter, StdoutWriter)
  }
}
```

The `Telemetry` object holds the three pieces both services use:

- `ContextStorage.create[Option[SpanContext]](None)` creates the storage. It starts empty: `None` means no span is active.
- `TracerProviderBuilder#setContextStorage` makes the tracer store each span's context in it, and `TracerProvider#get` returns a `Tracer` named `"shop"`.
- `LoggerProviderBuilder#setContextStorage` makes the logger read from the same storage, and `log.install` makes that logger the one behind the `log.*` calls.
- `log.writer(JsonLogFormatter, StdoutWriter)` writes each line as JSON. The default text format leaves the ids out, so a format that keeps them, such as JSON, is part of the setup.

We keep the tracer in a value and call `Tracer#span` on it. If your code calls the global `trace.span` instead, pass the same provider to `trace.install`; the global `trace` object then uses your storage too.

Install the logger and write a log line inside a span:

```scala mdoc
Telemetry.installLogger()

Telemetry.tracer.span("checkout.placeOrder") { _ =>
  log.info("Order received", "order.id" -> "order-1042")
}
```

The line ends with `traceId` and `spanId`: the ids of the `checkout.placeOrder` span, read from the shared storage. We didn't pass them to `log.info`. (Real ids are random hexadecimal strings; this page shows them as numbered placeholders, and placeholders with the same number stand for the same id.)

:::warning
The tracer and the logger must get the same `ContextStorage` instance. If you give a storage only to the tracer, the logger keeps reading the built-in one, which never holds your spans, and every log line comes out without `traceId` and `spanId`, with no error. Also call `log.install` before `log.writer`: in version 0.0.56, `log.install` silently drops the writers added before it.
:::

## Write the Trace Context into an Outgoing Request

Inside the checkout service, the shared storage is enough. The payment service runs in another process, though, and nothing in memory crosses that boundary: the trace context has to travel in the request. A `Propagator` writes a span's context into a carrier, such as a request, and reads it back on the other side. `W3CTraceContextPropagator` uses the W3C `traceparent` header, which OpenTelemetry and most tracing backends expect.

A propagator works with any carrier type, so it takes two functions to read and write headers. These two values are the glue between the `otel` module and the `http-model` module's `Request`:

```scala mdoc:silent
object HttpGlue {
  val getHeader: (Request, String) => Option[String] =
    (request, name) => request.headers.rawGet(name)

  val setHeader: (Request, String, String) => Request =
    (request, name, value) => request.setHeader(name, value)
}
```

`Headers#rawGet` reads a header's raw text, ignoring the case of its name, and `Request#setHeader` returns a copy of the request with the header set. The request itself is immutable, so `Request#setHeader` returns the new request rather than changing the old one.

The checkout service sends the order to the payment service as a POST request:

```scala mdoc:silent
object PaymentApi {
  val chargesUrl: URL = URL.parse("http://payment.internal/charges").toOption.get

  def chargeRequest(orderId: String, amountCents: Long): Request =
    Request.post(chargesUrl, Body.fromString(s"""{"orderId":"$orderId","amountCents":$amountCents}"""))
}
```

To send the trace along, open a client span for the call and inject its context into the request:

```scala mdoc
Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
  val request = W3CTraceContextPropagator.inject(
    span.spanContext,
    PaymentApi.chargeRequest("order-1042", 5990),
    HttpGlue.setHeader
  )
  println(request.headers)
  log.info("Calling the payment service")
}
```

`W3CTraceContextPropagator.inject` takes the span's `SpanContext`, the request, and the setter, and returns the request with one new header. The `traceparent` value has four parts separated by dashes: the format version `00`, the trace id, the id of the span that makes the call, and the flags, where `01` means the trace is sampled. The ids in the header are the same as the ids in the log line. When a span context carries vendor data in its `traceState`, `W3CTraceContextPropagator.inject` also writes it as a `tracestate` header.

Using `SpanKind.Client` marks this span as the caller's side of a remote call; the receiving side uses `SpanKind.Server`.

## Continue the Trace in the Payment Service

The payment service reads the header back with `W3CTraceContextPropagator.extract`, which returns the caller's `SpanContext`, or `None` when there is no usable header. To make it the parent of the payment service's span, run the span inside `ContextStorage#scoped`, the same storage the tracer reads:

```scala mdoc:silent
object PaymentService {
  def handle(request: Request): Response = {
    val parent = W3CTraceContextPropagator.extract(request, HttpGlue.getHeader)
    Telemetry.storage.scoped(parent) {
      Telemetry.tracer.span("payment.authorize", SpanKind.Server) { _ =>
        log.annotated("service.name" -> "payment") {
          log.info("Card charged")
        }
        Response.text("charged")
      }
    }
  }
}
```

`Telemetry.storage.scoped(parent) { ... }` makes `parent` the active context while the block runs and restores the previous one afterward. `Tracer#span` takes its parent from the storage, so `payment.authorize` reuses the caller's trace id, gets a new span id of its own, and records the caller's span as its parent. `log.annotated("service.name" -> "payment")` adds a `service.name` field to the lines written inside it, so we can tell the two services' lines apart. `OtelContext.withSpan(span, storage)` is not an alternative here: it takes a `Span`, not the `SpanContext` that `W3CTraceContextPropagator.extract` returns, and a span you start yourself with `Tracer#spanBuilder` is local only and never reaches the span processors, so `ContextStorage#scoped` around `Tracer#span` is the way to continue a remote trace.

To run both services in one program, we need a stand-in for the network. A real call crosses a process boundary, and the receiving service gets nothing from the caller's memory. Running the payment handler on a thread of its own gives the same guarantee: the storage is bound to the thread that runs a block, so the payment thread starts with an empty storage, and the request is the only thing that crosses over:

```scala mdoc:silent
// Stand-in for the network: the payment service runs on its own thread,
// so the only thing it receives from checkout is the request.
object Network {
  private val paymentThread = Executors.newSingleThreadExecutor()

  def send(request: Request): Response =
    CompletableFuture.supplyAsync(() => PaymentService.handle(request), paymentThread).join()

  def shutdown(): Unit = paymentThread.shutdown()
}
```

We chose this stand-in over a real HTTP server because the propagation code is the same either way: inject on the way out, extract on the way in. [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) shows how to put an http-model handler such as `PaymentService.handle` behind the JDK's HTTP server.

The checkout service now injects the header into the request it sends:

```scala mdoc:silent
object CheckoutService {
  def placeOrder(orderId: String, amountCents: Long): Unit =
    log.annotated("service.name" -> "checkout") {
      Telemetry.tracer.span("checkout.placeOrder") { _ =>
        log.info("Order received", "order.id" -> orderId)
        Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
          val request = W3CTraceContextPropagator.inject(
            span.spanContext,
            PaymentApi.chargeRequest(orderId, amountCents),
            HttpGlue.setHeader
          )
          val response = Network.send(request)
          log.info("Payment answered", "status" -> response.status.code)
        }
      }
    }
}
```

Place an order:

```scala mdoc
CheckoutService.placeOrder("order-1042", 5990)
```

All three lines carry the same `traceId`, although the middle one was written by the payment service on another thread. A log search for that trace id returns the whole order, from both services. The `spanId` values show where each line was written: "Order received" in `checkout.placeOrder`, "Card charged" in `payment.authorize`, and "Payment answered" in `checkout.callPayment`.

<details>
  <summary>Runnable version, with the setup and services from the earlier sections: <code>Step3ContinueTraceExample.scala</code></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/correlatelogsandtraces/Step3ContinueTraceExample.scala:show-line-numbers
```

</details>

## Carry the Span Context to Another Thread

The storage follows the call stack of one thread. That is what kept the payment service's context separate in the previous section, and it is also what loses the context when your own code hands work to a thread pool: a task running on a pool thread sees an empty storage. `OtelContext` is a snapshot of the storage's current value that you can pass along like any other parameter, and restore with `ContextStorage#scoped` where the work runs.

Here the checkout service sends the confirmation email on a worker thread, once without the context and once with an `OtelContext` snapshot:

```scala mdoc:silent
object Emails {
  private val worker = Executors.newSingleThreadExecutor()

  def sendWithoutContext(orderId: String): Unit =
    CompletableFuture.runAsync(() => log.info("Confirmation email sent", "order.id" -> orderId), worker).join()

  def send(orderId: String, context: OtelContext): Unit =
    CompletableFuture
      .runAsync(
        () =>
          Telemetry.storage.scoped(context.spanContext) {
            log.info("Confirmation email sent", "order.id" -> orderId)
          },
        worker
      )
      .join()

  def shutdown(): Unit = worker.shutdown()
}
```

`OtelContext.current(storage)` takes the snapshot on the calling thread, while the span is still active:

```scala mdoc
Telemetry.tracer.span("checkout.placeOrder") { _ =>
  log.info("Order received", "order.id" -> "order-1042")
  Emails.sendWithoutContext("order-1042")
  Emails.send("order-1042", OtelContext.current(Telemetry.storage))
}

Emails.shutdown()
```

The first email line has no `traceId` or `spanId`, so a search by trace id never finds it. The second one carries the ids of `checkout.placeOrder`, because `Emails.send` restored the snapshot before logging. Only the span context travels this way; fields added with `log.annotated` are bound to the calling thread too, so add them again on the worker when you need them there.

When you hold the `Span` itself, for example inside the block of `Tracer#span`, `OtelContext.withSpan(span, Telemetry.storage) { ... }` restores its context in one call. `OtelContext` also works as a service in a [`Context`](../reference/context.md), so code that already threads a `Context` through its calls can carry the span context with it.

<details>
  <summary>Runnable version, with the setup from the first section: <code>Step4CarrySpanContextExample.scala</code></summary>

```scala mdoc:embed:telemetry-examples/src/main/scala/correlatelogsandtraces/Step4CarrySpanContextExample.scala:show-line-numbers
```

</details>

## Switch to B3 Headers

Services instrumented with Zipkin often expect B3 headers instead of `traceparent`. `B3Propagator` has two variants with the same `Propagator` methods: `B3Propagator.single` writes one `b3` header, and `B3Propagator.multi` writes one header per field. To see what each writes for the same span:

```scala mdoc
Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
  val request = PaymentApi.chargeRequest("order-1042", 5990)
  println(B3Propagator.single.inject(span.spanContext, request, HttpGlue.setHeader).headers)
  println(B3Propagator.multi.inject(span.spanContext, request, HttpGlue.setHeader).headers)
}
```

The single header holds the trace id, the span id and the sampling decision (`1` for sampled) separated by dashes. The multi-header variant writes `X-B3-TraceId`, `X-B3-SpanId` and `X-B3-Sampled`, which http-model stores in lower case. `Propagator#fields` lists the header names a propagator reads, which is what a proxy or a CORS allow-list must let through.

Both sides must agree on the format: `W3CTraceContextPropagator.extract` finds nothing in a request that carries only B3 headers, and the receiver starts a new trace. A receiver that serves callers of both kinds can try the formats in turn:

```scala mdoc:silent
def extractParent(request: Request): Option[SpanContext] =
  W3CTraceContextPropagator
    .extract(request, HttpGlue.getHeader)
    .orElse(B3Propagator.multi.extract(request, HttpGlue.getHeader))
```

With the fallback, the payment service finds the caller's trace id in a B3 request:

```scala mdoc
Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
  val request = B3Propagator.multi.inject(span.spanContext, PaymentApi.chargeRequest("order-1042", 5990), HttpGlue.setHeader)
  println(s"W3C only: ${W3CTraceContextPropagator.extract(request, HttpGlue.getHeader).map(_.traceIdHex)}")
  println(s"with B3 fallback: ${extractParent(request).map(_.traceIdHex)}")
  println(s"checkout span: ${span.spanContext.traceIdHex}")
}
```

To make the payment service accept both formats, call `extractParent(request)` in `PaymentService.handle` where it calls `W3CTraceContextPropagator.extract`.

## Handle Missing or Invalid Headers

A request can arrive with no trace header at all, from a client that isn't instrumented, or with a malformed one. `W3CTraceContextPropagator.extract` returns `None` in both cases, and it rejects the values the W3C specification calls invalid, such as an all-zero trace id or the forbidden version `ff`. Starting from a charge request with no trace header:

```scala mdoc:silent
val untraced = PaymentApi.chargeRequest("order-1043", 1250)
```

To try `W3CTraceContextPropagator.extract` on that request and on three broken `traceparent` values:

```scala mdoc
List(
  "no header"         -> untraced,
  "all-zero trace id" -> untraced.setHeader("traceparent", "00-00000000000000000000000000000000-00f067aa0ba902b7-01"),
  "version ff"        -> untraced.setHeader("traceparent", "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
  "cut short"         -> untraced.setHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-01")
).foreach { case (label, request) =>
  println(s"$label: ${W3CTraceContextPropagator.extract(request, HttpGlue.getHeader)}")
}
```

`PaymentService.handle` needs no special case for this: `scoped(None)` leaves the storage empty, and `Tracer#span` starts a new trace with a fresh trace id. To see it, send the request without injecting any header:

```scala mdoc
Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { _ =>
  log.info("Calling the payment service without trace headers")
  val response = Network.send(untraced)
  log.info("Payment answered", "status" -> response.status.code)
}

Network.shutdown()
```

The payment service's line has a different trace id from the two checkout lines: its work is still traced and its lines still correlate with each other, but they no longer join the caller's trace. That is the right outcome for a header you can't trust.

:::tip
A tracer built with the default sampler records every span, even when the caller's flags say the trace isn't sampled (`-00` at the end of `traceparent`). To follow the caller's decision, build the provider with `TracerProviderBuilder#setSampler(ParentBasedSampler(AlwaysOnSampler))`. Log lines written in an unsampled span still carry its trace id.
:::

:::caution
In version 0.0.56, `W3CTraceContextPropagator.extract` is more lenient than the W3C specification in a few places: it accepts upper-case hex digits and extra characters after a version `00` header, and it rejects headers with a future version such as `01` instead of reading their first four fields. `B3Propagator.single` returns a context with zero ids, rather than `None`, for a `b3` header that holds only a sampling decision such as `0`. These are known bugs in that version.
:::

```scala mdoc:invisible
// Restore what the page changed, so later pages in the same mdoc run start from the defaults.
log.clearWriters()
log.install(LoggerProvider.builder.addLogRecordProcessor(new ConsoleLogRecordProcessor).build().get("default"))
System.setOut(System.getProperties.get("docs.telemetry.realOut").asInstanceOf[java.io.PrintStream])
```

## Putting It Together

The complete example places two orders. For each one, the checkout service calls the payment service with a `traceparent` header, the payment service continues the trace, and the confirmation email is logged on a worker thread from an `OtelContext` snapshot. It adds nothing the sections don't show; the services, `Telemetry`, `HttpGlue`, `PaymentApi` and `Network` live in the other files of the same package (`Network` sits in `PaymentService.scala`):

```scala mdoc:embed:telemetry-examples/src/main/scala/correlatelogsandtraces/CompleteExample.scala
```

Each order has its own trace id, and all four lines of an order carry it, from both services and from the worker thread (output from one run, with the times, file paths and ids replaced by placeholders):

```text
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Order received"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.CheckoutService$"}},{"key":"code.function","value":{"stringValue":"placeOrder"}},{"key":"code.lineno","value":{"intValue":"38"}},{"key":"service.name","value":{"stringValue":"checkout"}},{"key":"order.id","value":{"stringValue":"order-1042"}}],"traceId":"<trace-id-1>","spanId":"<span-id-1>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Card charged"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/PaymentService.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.PaymentService$"}},{"key":"code.function","value":{"stringValue":"handle"}},{"key":"code.lineno","value":{"intValue":"19"}},{"key":"service.name","value":{"stringValue":"payment"}}],"traceId":"<trace-id-1>","spanId":"<span-id-2>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Payment answered"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.CheckoutService$"}},{"key":"code.function","value":{"stringValue":"placeOrder"}},{"key":"code.lineno","value":{"intValue":"46"}},{"key":"service.name","value":{"stringValue":"checkout"}},{"key":"status","value":{"intValue":"200"}}],"traceId":"<trace-id-1>","spanId":"<span-id-3>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Confirmation email sent"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.Emails$"}},{"key":"code.function","value":{"stringValue":"send"}},{"key":"code.lineno","value":{"intValue":"25"}},{"key":"order.id","value":{"stringValue":"order-1042"}}],"traceId":"<trace-id-1>","spanId":"<span-id-1>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Order received"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.CheckoutService$"}},{"key":"code.function","value":{"stringValue":"placeOrder"}},{"key":"code.lineno","value":{"intValue":"38"}},{"key":"service.name","value":{"stringValue":"checkout"}},{"key":"order.id","value":{"stringValue":"order-1043"}}],"traceId":"<trace-id-2>","spanId":"<span-id-4>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Card charged"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/PaymentService.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.PaymentService$"}},{"key":"code.function","value":{"stringValue":"handle"}},{"key":"code.lineno","value":{"intValue":"19"}},{"key":"service.name","value":{"stringValue":"payment"}}],"traceId":"<trace-id-2>","spanId":"<span-id-5>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Payment answered"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.CheckoutService$"}},{"key":"code.function","value":{"stringValue":"placeOrder"}},{"key":"code.lineno","value":{"intValue":"46"}},{"key":"service.name","value":{"stringValue":"checkout"}},{"key":"status","value":{"intValue":"200"}}],"traceId":"<trace-id-2>","spanId":"<span-id-6>"}
{"timeUnixNano":"<time>","severityNumber":9,"severityText":"INFO","body":{"stringValue":"Confirmation email sent"},"attributes":[{"key":"code.filepath","value":{"stringValue":"<path>/CompleteExample.scala"}},{"key":"code.namespace","value":{"stringValue":"correlatelogsandtraces.CompleteExample$.Emails$"}},{"key":"code.function","value":{"stringValue":"send"}},{"key":"code.lineno","value":{"intValue":"25"}},{"key":"order.id","value":{"stringValue":"order-1043"}}],"traceId":"<trace-id-2>","spanId":"<span-id-4>"}
```

## Running the Examples

All runnable code from this guide is in [`telemetry-examples/src/main/scala/correlatelogsandtraces/`](https://github.com/zio/zio-blocks/tree/main/telemetry-examples/src/main/scala/correlatelogsandtraces). The examples need JDK 21 or newer. Run any of them from `telemetry-examples` with `sbt "runMain correlatelogsandtraces.<ObjectName>"`, for example `sbt "runMain correlatelogsandtraces.CompleteExample"`.

## Going Further

- The [OTLP Export](../reference/telemetry/otel/index.md) reference page describes the `Propagator` trait and `OtelContext`, and [SpanContext](../reference/telemetry/tracing/span-context.md) describes the ids and flags a propagator carries.
- [How to Test Code That Logs, Traces and Records Metrics](./test-telemetry.md) shows how to assert that a log record carries the ids of the span it was written in.
- [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md) runs an http-model handler on a real HTTP server.
- [Telemetry: Architecture, Patterns, and Real-World Usage](./telemetry-guide.md) explains how sampling, context storage and the log macros work inside the module.
