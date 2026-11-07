---
id: index
title: "What Is ZIO Blocks?"
sidebar_label: "What Is ZIO Blocks?"
description: "A short tour of ZIO Blocks: what it is, the ideas behind it, and which block solves which problem."
keywords:
  - "Getting Started"
  - "Introduction"
  - "Modules"
---

ZIO Blocks is a family of small, independent Scala libraries. Each one solves one common problem: describing data and turning it into JSON, closing resources, wiring services together, streaming data, loading configuration, talking to a database, and more. You add the blocks you need to your build and call them from ordinary Scala code.

This Getting Started section is a short tour. It teaches the core ideas, not every method: each page takes a few minutes, and links to the reference when you want the full details.

## Why ZIO Blocks Exists

Most Scala libraries for these jobs come tied to an ecosystem. A JSON library expects you to use its effect type for streaming, a resource library expects a runtime, a database library expects a particular effect system. Picking one library often means adopting a whole stack.

ZIO Blocks takes the opposite approach. A block needs no effect system and no runtime: its methods are plain Scala functions that return plain values. That is why the same block works in a ZIO application, a cats-effect application, an Akka or Ox application, or a plain `main` method.

## The Ideas Behind It

Every block follows the same few principles:

- **No lock-in.** No block depends on ZIO, cats-effect or any other effect system.
- **Modular.** Each block is its own artifact, so your build contains only what you use.
- **Cross-platform.** Most blocks build for Scala 2.13 and Scala 3, on the JVM and on Scala.js, from the same source.
- **Checked at compile time.** Macros derive codecs, check resource lifetimes and build dependency graphs while your code compiles, so mistakes show up as compile errors.
- **Fast.** Implementations avoid boxing and needless allocation on hot paths.

[How ZIO Blocks Thinks](./how-zio-blocks-thinks.md) explains these ideas in more detail and shows how the blocks build on each other.

## Which Block Do I Need?

Start from the problem you have. This table lists the most common problems and the block that solves each one; the [overview](../index.md) has the full catalog of every block:

| You need to...                                         | Use this block                                                  | Artifact                        |
| ------------------------------------------------------ | --------------------------------------------------------------- | ------------------------------- |
| Turn a case class into JSON (or Avro, YAML, CSV...)    | [Schema](../reference/schema/index.md)                          | `zio-blocks-schema`             |
| Open files or connections and always close them        | [Scope](../reference/resource-management/index.md)              | `zio-blocks-scope`              |
| Build services and their dependencies                  | [Wire](../reference/resource-management/wire.md)                | `zio-blocks-scope`              |
| Process data that does not fit in memory               | [Streams](../reference/streams/index.md)                        | `zio-blocks-streams`            |
| Load typed configuration from files and environment    | [Config](../reference/config/index.md)                          | `zio-blocks-config`             |
| Query a database with type-safe SQL                    | [SQL](../reference/sql/index.md)                                | `zio-blocks-sql`                |
| Describe HTTP endpoints as data, for OpenAPI and more  | [Endpoint](../reference/endpoint/index.md)                      | `zio-blocks-endpoint`           |
| Render HTML safely                                     | [HTML](../reference/html.md)                                    | `zio-blocks-html`               |
| Record logs, traces and metrics                        | [Telemetry](../reference/telemetry/index.md)                    | `zio-blocks-telemetry`          |
| Store sequences of primitives without boxing           | [Chunk](../reference/chunk.md)                                  | `zio-blocks-chunk`              |
| Run asynchronous code in direct style                  | [Async](../reference/async.md)                                  | `zio-blocks-async`              |

## How to Read This Section

The pages build on each other, so read them in order:

1. [Installation](./installation.md) adds a block to your sbt or scala-cli build.
2. [Your First Five Minutes](./first-five-minutes.md) runs one small program that uses three blocks together.
3. [How ZIO Blocks Thinks](./how-zio-blocks-thinks.md) explains the design that all blocks share.
4. The core concept pages introduce the four blocks most applications start with: [Schema](./concepts/schema.md), [Scope](./concepts/scope.md), [Wire and Context](./concepts/wire-and-context.md) and [Streams](./concepts/streams.md).
5. [Use ZIO Blocks with ZIO, cats-effect or Plain Scala](./use-with-effect-systems.md) shows where ZIO Blocks calls go in an application that already has an effect system.
6. [Where Next](./where-next.md) points you to the tutorials, how-to guides and reference.

If you are only evaluating the library, the first three pages are enough to decide whether it fits your project.
