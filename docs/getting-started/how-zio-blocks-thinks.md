---
id: how-zio-blocks-thinks
title: "How ZIO Blocks Thinks"
sidebar_label: "How ZIO Blocks Thinks"
description: "The design every ZIO Blocks module shares: libraries instead of a framework, plain values instead of an effect type, one artifact per block, and checks at compile time."
keywords:
  - "Getting Started"
  - "Design Principles"
  - "Architecture"
  - "Modules"
---

Every block in ZIO Blocks follows the same design. Once you know it, each new block feels familiar: you already know how to add it, how to call it and how it reports errors. This page explains that design in six ideas, then shows how the blocks build on each other.

## A Block Is a Library, Not a Framework

You call a block; it never calls you. There is no application base class to extend, no runtime to start and no main loop to hand control to. A block is a set of types and methods, and your code decides when to use them.

This is why ZIO Blocks fits into an existing project one block at a time. You can replace a JSON library with [Schema](./concepts/schema.md) in one module and leave the rest of the code base untouched.

## Plain Values Instead of an Effect Type

Effect systems such as ZIO and cats-effect describe work as values that run later. ZIO Blocks does not require one: its methods do the work when you call them and return ordinary Scala values. Decoding JSON returns an `Either`, running a [stream](./concepts/streams.md) on the JVM returns an `Either` with the collected elements, and a database query returns its rows.

Expected failures are values too. A malformed JSON document comes back as the `Left` side of an `Either`, with a message that says what went wrong and where, and a stream declares the type of its errors in its own type, `Stream[E, A]`.

When you do want to run work without blocking a thread, the [Async](../reference/async.md) block provides a small asynchronous type, and blocks such as Streams offer `Async` variants of their run methods. If your application already has an effect system, [Use ZIO Blocks with ZIO, cats-effect or Plain Scala](./use-with-effect-systems.md) shows where these calls go.

## One Artifact per Block

Each block is published as its own artifact under the `dev.zio` organization, and blocks depend on each other only where they share a real need. Adding `zio-blocks-scope` to a build does not pull in JSON codecs, and adding `zio-blocks-schema` does not pull in a streaming library.

The blocks form layers. Small foundation blocks at the bottom hold the shared data types; three hub blocks in the middle describe data, own resources and move data; the application blocks at the top combine them. Each arrow means "builds on":

```text
 Application blocks   sql      config    endpoint    html     telemetry
                        │         │          │          │          │
                        ▼         ▼          ▼          ▼          │
 Hub blocks          schema ◄──────────── describes your data      │
                     streams ◄─────────── moves data in pieces     │
                     scope ◄───────────── owns resources           │
                        │                                          │
                        ▼                                          ▼
 Foundation blocks   chunk    maybe    typeid    context    async  combinators
```

For example, the SQL block uses Schema to map case classes to rows, Streams to read large results and Scope to close connections; the Config block decodes your configuration into case classes through Schema.

## Mistakes Become Compile Errors

Many problems that other libraries report at run time are compile errors in ZIO Blocks, because macros check them while your code compiles:

- **Codecs.** `Schema.derived` inspects your case class or sealed trait and builds its schema at compile time, so there is no reflection at run time and a type that cannot be described fails to compile.
- **Resource lifetimes.** [Scope](./concepts/scope.md) tags every resource with the region that owns it, so returning an open file or connection from that region does not compile.
- **Dependency graphs.** [Wire](./concepts/wire-and-context.md) reads your constructors and assembles the services your application needs; a dependency that it cannot build is a compile error that names the type.

## Fast Without Extra Work

The blocks avoid work that slows down common Scala code. [Chunk](../reference/chunk.md) stores `Int`, `Long`, `Double` and the other primitives in primitive arrays instead of boxing each element, and Streams keeps primitive elements unboxed as they flow through a pipeline. You get this by using the default APIs; there is no separate fast mode to turn on.

## The Same Code on Every Platform

Most blocks build for Scala 2.13 and Scala 3, on the JVM and on Scala.js, from the same source code. You can move a project from Scala 2.13 to Scala 3 without changing how you call a block. The few blocks that are narrower, such as SQL, which needs Scala 3, say so in the catalog on the [overview](../index.md).

## Putting the Ideas Together

These ideas explain why the program in [Your First Five Minutes](./first-five-minutes.md) needs no setup: Schema, Scope and Chunk are plain libraries, they return plain values, they share the `Chunk` type, and the compiler checks that the file reader never leaves the scope that owns it.

The next four pages introduce the blocks most applications start with, beginning with [Schema](./concepts/schema.md).
