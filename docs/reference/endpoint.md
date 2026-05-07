---
id: endpoint
title: "Endpoint"
---

`Endpoint[PathInput, Input, Err, Output, Auth]` is a pure descriptor for HTTP endpoints. It combines a route pattern with typed request input, typed error output, typed success output, authentication requirements, and documentation metadata.

At a high level, the DSL is designed to stay close to zio-http where that improves ergonomics:

```scala
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
)
```

## Overview

Endpoints are pure data. They describe an HTTP surface without committing to server or client interpretation up front.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val getUser = Endpoint(Method.GET / "users" / PathCodec.int("userId"))
  .query("verbose", Schema.boolean)
  .out(Schema.string)
  .outError(Status.NotFound, Schema.string)
  .auth(AuthType.Bearer)
```

## Route DSL

The primary route syntax is method-first:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val route = Method.GET / "users" / PathCodec.uuid("userId")
```

This keeps the route readable and matches the shape users already expect from zio-http.

## Additive request and response builders

Request inputs, outputs, and error outputs are additive. Calling a builder adds another part instead of replacing the previous one.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val endpoint = Endpoint(Method.POST / "users")
  .in(Schema.string)
  .header("X-Trace", Schema.string)
  .out(Status.Created, Schema.int)
  .outError(Status.BadRequest, Schema.string)
```

## Scala 3 union errors with `orOutError`

On Scala 3, `orOutError` lets you accumulate error outputs into a real union type instead of nested `Either`s.

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val endpoint = Endpoint(Method.GET / "users")
  .orOutError(Status.BadRequest, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = endpoint
```

This improves on zio-http's current Scala 3-only error DSL by making the fallback combinator itself union-aware internally instead of only relying on type inference at the call site.

Two constraints are intentional:

- `outError(...)` remains the cross-version additive API
- `orOutError(...)` rejects overlapping union members such as `String | String`

## Typed authentication

Authentication is part of the endpoint type. Built-in auth constructors keep the request requirement precise:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val secured = Endpoint(Method.GET / "me").auth(AuthType.Bearer)

val authCodec = secured.auth.codec
```

This makes it possible to require bearer, basic, or digest auth without dropping down to raw string headers.
