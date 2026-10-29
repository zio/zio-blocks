---
id: use-with-effect-systems
title: "Use ZIO Blocks with ZIO, cats-effect or Plain Scala"
sidebar_label: "Use with ZIO, cats-effect or Plain Scala"
description: "Where ZIO Blocks calls go in a plain Scala program and in an application built on ZIO or cats-effect: pure calls, blocking calls, resources, Async and streams."
keywords:
  - "Getting Started"
  - "ZIO"
  - "cats-effect"
  - "Effect System Interop"
---

ZIO Blocks needs no effect system, so the question for most projects is not whether a block fits, but where its calls go. This page answers it in one table for each kind of call. [How to Use ZIO Blocks in a ZIO or cats-effect Application](../guides/use-zio-blocks-in-zio-or-cats-effect.md) shows every case with complete, runnable code.

## In Plain Scala

In a plain Scala program, there is nothing to arrange: call a block where you need it. Decoding returns an `Either`, a scope closes its resources when its block ends, and a stream runs when you call one of its run methods. Every example in this Getting Started section is plain Scala.

The one choice you still make is about threads. Some calls block the calling thread until they finish, such as a database query or `Stream#runCollect` on the JVM. In a command-line tool or a batch job that is fine; in a server, run them on a thread pool meant for blocking work, or use the `Async` variants that the blocks provide.

## In a ZIO or cats-effect Application

In an application that already runs on ZIO or cats-effect, each kind of ZIO Blocks call has one place where it belongs. Pure calls stay as they are, blocking calls move to the effect system's blocking pool, and resources and streams are wrapped once so that interruption and cleanup work across the boundary:

| Kind of call                                       | Example                                         | In ZIO                                     | In cats-effect                             |
| -------------------------------------------------- | ----------------------------------------------- | ------------------------------------------ | ------------------------------------------ |
| Pure and fast                                      | `toJsonString`, `fromJson`, `Chunk` operations  | Call it directly, or `ZIO.succeed`         | Call it directly, or `IO.pure`             |
| Returns an `Either`                                | `fromJson`, a decode with any format codec      | `ZIO.fromEither`                           | `IO.fromEither`                            |
| Blocks a thread                                    | SQL queries, `Stream#runCollect` on the JVM     | `ZIO.attemptBlocking`                      | `IO.blocking`                              |
| Owns a resource                                    | A `Resource` from Scope                         | Wrap it in `ZIO.acquireRelease`            | Wrap it in `Resource.make`                 |
| Runs asynchronously                                | An `Async` value                                | Bridge it with `ZIO.asyncInterrupt`        | Bridge it with `IO.async`                  |
| Produces a stream                                  | A ZIO Blocks `Stream`                           | Turn it into a `ZStream`                   | Turn it into an fs2 `Stream`               |

ZIO has library help for one case: the `sql-zio` module returns ZIO effects that already run on the blocking pool and provides the database as a `ZLayer` ([How to Use the sql Module from a ZIO Application](../guides/sql-from-zio.md)). Every other bridge is a few lines of your own code, and the full guide gives you those lines for both effect systems.

## Why It Works This Way

Because a block does its work when you call it, it cannot know whether the calling thread may block, or how your effect system cancels work. Keeping that decision in your code means the same block works in ZIO, cats-effect, Kyo, Ox, Akka or a plain `main` method, and you never adapt your application to a runtime that ZIO Blocks would otherwise bring along.

## Learn More

[How to Use ZIO Blocks in a ZIO or cats-effect Application](../guides/use-zio-blocks-in-zio-or-cats-effect.md) builds a small orders service that covers every row of the table, with cancellation and cleanup tested in both effect systems. If you are moving an existing code base, the [migration guides](../guides/index.md) map common Scala libraries to their ZIO Blocks counterparts. Finally, [Where Next](./where-next.md) suggests what to read after this section.
