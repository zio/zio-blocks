---
id: index
title: "Endpoint"
---

`zio-blocks-endpoint` is a **pure, type-safe HTTP endpoint descriptor** for building clients, servers, and API documentation from a single source of truth. It provides composable types that describe every part of an HTTP surface — routes, query parameters, headers, request bodies, response bodies, error shapes, and authentication — without committing to any particular server or client implementation.

Core types: `Endpoint`, `HttpCodec`, `RoutePattern`, `PathCodec`, `SegmentCodec`, `AuthType`, `RouteTree`. The top-level descriptor holds all of them together:

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

## Introduction

`zio-blocks-endpoint` separates the **description** of an HTTP surface from its **interpretation**. An `Endpoint` value is plain data — it can be handed to a ZIO HTTP server to generate routes, to a client generator to produce typed API calls, or to an OpenAPI renderer to produce specification documents. None of that interpretation code lives here; this module only describes what an endpoint looks like.

The DSL is designed to stay close to zio-http where that improves ergonomics, while adding precise types for error shapes, authentication, and content negotiation that zio-http does not encode directly.

## Motivation

Without a typed endpoint descriptor, HTTP surface definitions are scattered: routes in one place, request validation in another, error handling in a third. Adding a new endpoint means updating multiple layers by hand and hoping they stay consistent.

`zio-blocks-endpoint` solves this by encoding the full shape of an HTTP endpoint — including error variants, auth requirements, content types, and documentation — into a single composable value:

- **One source of truth**: change the endpoint descriptor and every interpreter (server, client, OpenAPI) updates automatically.
- **Type-safe error channels**: error types are encoded in the `Err` type parameter, not buried in `Either` chains or thrown exceptions.
- **Direction-checked codecs**: `HttpCodec[CodecKind.Request, A]` and `HttpCodec[CodecKind.Response, A]` are distinct types; the compiler prevents accidentally using a response codec where a request codec is expected.
- **Compile-time path validation**: path segment combinations (like `string ~ string`) that would be ambiguous to parse are rejected by the Scala 3 macro in `SegmentCodec` before the code compiles.

## Installation

Add the dependency to your build definition:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-endpoint" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## Overview

These seven types form the complete endpoint DSL:

**[Endpoint](./endpoint.md)** is the top-level descriptor. It holds a route, three codec channels (input, error, output), an auth type, and documentation. Endpoint is pure data with no server or client logic.

**[HttpCodec](./http-codec.md)** is a composable typed descriptor for HTTP request and response parts. Query parameters, headers, bodies, and status codes are all `HttpCodec` nodes, combined with `++` (sequential) or `|` (alternative).

**[RoutePattern](./route-pattern.md)** pairs an HTTP method with a typed path pattern. The primary syntax is `Method.GET / "users" / PathCodec.int("id")`.

**[PathCodec](./path-codec.md)** is a composable path descriptor. Segments are combined with `/`, and literal alternatives with `orElse`. It supports bidirectional path conversion via `decode` and `format`.

**[SegmentCodec](./segment-codec.md)** describes a single URL path segment. It supports typed segment kinds (`SegmentCodec.bool`, `SegmentCodec.int`, `SegmentCodec.long`, `SegmentCodec.string`, `SegmentCodec.uuid`) and intra-segment composition via `~`, with ambiguous combinations rejected at compile time.

**[AuthType](./auth-type.md)** describes an authentication scheme as a first-class type parameter. Built-in variants include `None`, `Basic`, `Bearer`, and `Digest`; custom schemes and `Or` combinations are also supported.

**[RouteTree](./route-tree.md)** is a routing trie keyed by HTTP method and path. The trie matches literals first, then dynamic segments in priority order. Server-side interpreters use it to build efficient route dispatch tables.

## How They Work Together

A typical endpoint definition flows like this:

```
1. Define a RoutePattern       Method.GET / "users" / PathCodec.int("id")
2. Create an Endpoint          Endpoint(route)
3. Describe request input      .query("verbose", Schema.boolean)
                               .header("X-Trace", Schema.string)
                               .in(Schema.requestBody)
4. Describe success output     .out(Schema.string)
                               .out(Status.Created, Schema.int)
5. Describe error output       .outError(Status.NotFound, Schema.string)
                               .orOutError(Status.Conflict, Schema.int)   // Scala 3 unions
6. Set authentication          .auth(AuthType.Bearer)
7. Add documentation           .doc(Doc.paragraph("Returns a user by ID"))
```

The full type-level view:

```
RoutePattern[PathInput]
  └─ method: Method (GET, POST, ...)
  └─ pathCodec: PathCodec[PathInput]
       └─ Segment(SegmentCodec[A])  ──  literal / int / string / uuid / bool / long / trailing
       └─ Concat(left, right)       ──  left ++ right
       └─ Transform(codec, f, g)    ──  bidirectional type mapping

Endpoint[PathInput, Input, Err, Output, Auth]
  ├─ route:  RoutePattern[PathInput]
  ├─ input:  HttpCodec[Request,  Input]   ──  Query | Header | Body  (combined with ++)
  ├─ error:  HttpCodec[Response, Err]     ──  Body + Status          (alternatives with |)
  ├─ output: HttpCodec[Response, Output]  ──  Body + Status          (alternatives with |)
  └─ auth:   Auth <: AuthType             ──  None | Basic | Bearer | Digest | Custom | Or
```

The phantom type `CodecKind` (either `Request` or `Response`) on `HttpCodec` means the compiler rejects mixing the two directions, even before a server interprets the value.

## Common Patterns

Several composition patterns appear regularly when building endpoints.

**Single success response:** Use `Endpoint#out` for a 200 OK response with a body:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val getUser = Endpoint(Method.GET / "users" / PathCodec.int("id"))
  .out(Schema.string)
```

**Multiple success variants:** Chain additional `Endpoint#out` calls to add alternatives. The output type widens to an `Either`-based union:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val createOrUpdate = Endpoint(Method.POST / "users")
  .in(Schema.string)
  .out(Status.Created, Schema.int)
  .out(Status.Ok, Schema.string)
```

**Scala 3 union errors:** Use `Endpoint#orOutError` to accumulate error types as a Scala 3 union rather than nested `Either`s:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val withUnionErrors = Endpoint(Method.GET / "users")
  .orOutError(Status.NotFound, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = withUnionErrors
```

**Path prefixing with `RoutePattern#nest`:** Use `RoutePattern#nest` to prepend a version prefix to an existing pattern without rewriting it:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route  = Method.GET / "users" / PathCodec.int("id")
val versioned = route.nest(PathCodec("/api/v1"))
```

**Auth composition with `|`:** Combine auth types when an endpoint accepts multiple schemes:

```scala mdoc:compile-only
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val flexAuth = Endpoint(Method.GET / "me")
  .auth(AuthType.Basic | AuthType.Bearer)
```

## Integration Points

The endpoint types integrate with each other and with the broader ZIO Blocks ecosystem:

```
Endpoint
  ├─ uses RoutePattern      for routing lookup
  ├─ uses HttpCodec         for all three channels (input, error, output)
  ├─ uses AuthType          to carry the typed auth requirement
  └─ uses Doc               from zio-blocks-docs for API documentation

HttpCodec
  ├─ uses Schema            from zio-blocks-schema for body and header serialization
  ├─ uses MediaType         from zio-blocks-mediatype for content negotiation
  └─ uses Doc               for per-field documentation and examples

RoutePattern
  └─ uses PathCodec         for typed path composition

PathCodec
  └─ uses SegmentCodec      for individual segment descriptors

RouteTree (server-side only)
  └─ uses RoutePattern      to build the routing trie
  └─ uses SegmentSubtree    for per-level trie nodes
```

Cross-module: `zio-blocks-openapi` consumes `Endpoint` values to generate OpenAPI 3.1 specifications. `zio-blocks-schema` provides the `Schema[A]` instances that `HttpCodec.Body` uses for serialization.
