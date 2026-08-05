---
id: http-sender
title: "HttpSender"
description: "The transport an exporter sends through — a JDK HTTP client by default, or your own for proxies, signing, or tests."
keywords:
  - "Telemetry Export"
  - "Http Transport"
  - "Test Double"
  - "HttpSender"
sidebar_label: "HttpSender"
---

`HttpSender` is the one piece of the export path that actually touches the network. An exporter hands it a URL, headers, and a body of OTLP bytes; it performs the request and returns the response.

It's a separate trait so the network is replaceable. Real deployments need things a fixed HTTP client can't know about — an outbound proxy, request signing, a corporate TLS setup — and tests need the opposite: no network at all, just a record of what would have been sent.

```scala
trait HttpSender {
  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse
  def shutdown(): Unit
}

object HttpSender {
  def jdk(timeout: Duration = Duration.ofSeconds(30)): HttpSender
}
```

## Using the Built-In Sender

`HttpSender.jdk` wraps `java.net.http.HttpClient`, which ships with the JVM, so nothing is added to your dependencies:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val sender = HttpSender.jdk(Duration.ofSeconds(10))
```

The timeout applies to both connecting and completing a request. Call `shutdown()` at exit to release the client's resources.

## Inspecting a Response

`send` returns an `HttpResponse` rather than throwing, so an exporter can decide whether a failure is worth retrying:

```scala
final case class HttpResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, Seq[String]]
) {
  def firstHeader(name: String): Option[String]
}
```

`firstHeader` looks a header up case-insensitively, which is what you need for a `Retry-After` on a `429` or `503`.

## Writing Your Own

Implement the two methods. Return a `2xx` status for a send the exporter should treat as delivered, and anything else to mark it failed. Most custom senders aren't replacements but wrappers: do your own work — sign the request, pick a proxy, count attempts — then delegate to `HttpSender.jdk(...)` and pass its response back. A test double is the exception, returning `HttpResponse(200, Array.emptyByteArray, Map.empty)` without touching the network.

## See Also

- [ExporterConfig](./exporter-config.md) — the endpoint and headers a sender receives
- [OTLP Export](./index.md) — how transport fits into the export path
