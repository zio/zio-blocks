---
id: index
title: "Concurrency"
description: "Introduction to the Concurrency blocks of ZIO Blocks: Async, Mux and RingBuffer."
---

## Introduction

The blocks in this group deal with work that overlaps in time: computations that wait, streams that share one connection and queues that hand elements between threads. All three run on the JVM and in Scala.js.

## The Blocks

The group has three blocks, one for each of those problems.

| Block                              | Artifact                 | What It Is For                                                                   |
| ---------------------------------- | ------------------------ | -------------------------------------------------------------------------------- |
| [Async](../async.md)               | `zio-blocks-async`       | Zero-allocation asynchronous effect type with direct-style `await`               |
| [Mux](../mux.mdx)                  | `zio-blocks-mux`         | Thread-safe multiplexer for HTTP/2, QUIC, and WebSocket-style protocols          |
| [RingBuffer](../ringbuffer/index.mdx) | `zio-blocks-ringbuffer` | Lock-free bounded ring buffers (SPSC, SPMC, MPSC, MPMC)                          |

## How the Blocks Differ

`Async` is a small asynchronous effect type. Building an `Async` value runs it, and only a computation that has to wait for something is left pending.

`Mux` manages many independent streams over one shared transport, each identified by an ID and each with its own inbound and outbound queues. It targets protocols such as HTTP/2 and QUIC.

`RingBuffer` is a family of fixed-size, lock-free queues with four variants for different producer and consumer patterns, which recycle memory instead of creating garbage.

## Where to Start

Start with [Async](../async.md) to write asynchronous code in a direct style. Read [RingBuffer](../ringbuffer/index.mdx) when threads must exchange elements with little contention, and [Mux](../mux.mdx) when building a protocol implementation over a multiplexed connection.
