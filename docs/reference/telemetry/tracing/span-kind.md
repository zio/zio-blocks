---
id: span-kind
title: "SpanKind"
description: "The role a span plays in a trace — Internal, Server, Client, Producer, or Consumer — following the OpenTelemetry SpanKind model."
keywords:
  - "SpanKind"
  - "Span role"
  - "Client Server span"
  - "Producer Consumer span"
  - "OpenTelemetry SpanKind"
---

`SpanKind` records the role a [`Span`](./span.md) plays relative to its remote peers, so tracing backends can lay out the request topology (which side called, which side served). It is a sealed trait with five case objects:

```scala
sealed trait SpanKind
object SpanKind {
  case object Internal extends SpanKind
  case object Server   extends SpanKind
  case object Client   extends SpanKind
  case object Producer extends SpanKind
  case object Consumer extends SpanKind
}
```

| Kind | Role |
|---|---|
| `Internal` | Work performed inside the application — no remote peer. The default. |
| `Server` | The server side of a synchronous RPC (handling an incoming request). |
| `Client` | The client side of a synchronous RPC (making an outbound request). |
| `Producer` | The producer side of asynchronous messaging (sending a message). |
| `Consumer` | The consumer side of asynchronous messaging (receiving a message). |

Pass a kind to [`trace.span`](./index.md#choosing-a-span-kind) (or `Tracer#span`); it defaults to `Internal`.

These semantics follow the OpenTelemetry specification exactly. For the authoritative definitions and guidance on choosing a kind, see the [OpenTelemetry SpanKind spec](https://opentelemetry.io/docs/specs/otel/trace/api/#spankind).
