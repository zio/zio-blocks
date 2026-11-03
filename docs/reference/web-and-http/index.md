---
id: index
title: "Web & HTTP"
description: "Introduction to the Web & HTTP blocks of ZIO Blocks: HTTP data model, endpoint descriptions, OpenAPI, JWT, HTML templating, Datastar and HTMX."
---

## Introduction

Building a web service involves several separate concerns: representing requests and responses, describing the API, documenting it, authenticating callers and producing pages. The blocks in this group cover each of these as immutable data types. Most of them describe their subject without committing to a particular server or client, and all eight run on the JVM and in Scala.js.

## The Blocks

The eight blocks are listed below in the order the docs present them.

| Block                                   | Artifact                       | What It Is For                                                                              |
| --------------------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------- |
| [MediaType](../media-type.md)           | `zio-blocks-mediatype`         | Type-safe IANA media types with 2,600+ predefined types                                     |
| [HTTP Model](../http-model/index.md)    | `zio-blocks-http-model`        | Pure HTTP data model with URL parsing, headers, cookies, and forms                          |
| [Endpoint](../endpoint/index.md)        | `zio-blocks-endpoint`          | Type-safe HTTP endpoint descriptors with composable codecs and typed auth                   |
| [OpenAPI](../openapi.md)                | `zio-blocks-openapi`           | Type-safe OpenAPI 3.1 specification generation and rendering                                |
| [JWT](../jwt.md)                        | `zio-blocks-jwt`               | Zero-dependency JWT signing and verification with HMAC, RSA, ECDSA and EdDSA support        |
| [HTML](../html.md)                      | `zio-blocks-html`              | Type-safe HTML templating with XSS protection                                               |
| [Datastar](../datastar/index.md)        | `zio-blocks-datastar`          | Typed Datastar attribute and signal DSL, plus the SSE events that patch a live page         |
| [HTMX](../htmx/index.md)                | `zio-blocks-http-htmx`         | Typed HTMX DSL for compile-time-checked HTMX attributes                                     |

## How the Blocks Fit Together

[MediaType](../media-type.md), [HTTP Model](../http-model/index.md) and [JWT](../jwt.md) are the protocol layer. The HTTP model represents requests, responses, headers, URLs and cookies and keeps protocol concerns apart from effects such as sending a message. `MediaType` supplies the content types those messages carry, and `JWT` signs and verifies tokens without any dependency.

[Endpoint](../endpoint/index.md) and [OpenAPI](../openapi.md) describe an API. An endpoint is a description of routes, parameters, headers, bodies, errors and authentication, written once and independent of any server or client implementation. The OpenAPI block is a data model of an OpenAPI 3.1 document that can be built in Scala and exported as JSON for tools such as Swagger UI.

[HTML](../html.md), [Datastar](../datastar/index.md) and [HTMX](../htmx/index.md) produce pages. `HTML` builds markup with automatic XSS protection. `HTMX` and `Datastar` add typed attributes for the two hypermedia libraries, and `Datastar` also produces the server-sent events that patch a live page. Both are available on Scala 3 only.

## Where to Start

Start with [HTTP Model](../http-model/index.md) to see how messages are represented, then [Endpoint](../endpoint/index.md) to describe an API. Pick [HTML](../html.md) first when the goal is to render pages.
