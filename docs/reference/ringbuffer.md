---
id: ringbuffer
title: "RingBuffer"
---

`RingBuffer[A]` is a **fixed-size, lock-free queue** for efficiently exchanging elements between producer and consumer threads with minimal contention and cache-line effects. Ring buffers use a circular array to recycle memory, eliminating garbage collection pressure from transient allocations. The module provides four specialized implementations tuned for different producer/consumer thread patterns.

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
  def drain(consumer: A => Unit, limit: Int): Int
  def fill(supplier: () => A, limit: Int): Int
}

final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
}

final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
  def drain(consumer: A => Unit, limit: Int): Int
}

final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
}
```

## Motivation

Building low-latency systems — trading platforms, game engines, real-time event processors — requires careful control over memory allocation and CPU cache behavior. Standard JVM collections like `LinkedList` or `ArrayDeque` are convenient but have a cost: every enqueue/dequeue pair allocates a node, triggering garbage collection pauses that can destroy millisecond-scale latencies.

A naive approach is to pre-allocate a large `Array[A]` and manually manage head/tail indices:

```scala mdoc:compile-only
var head = 0
var tail = 0
val array = new Array[String](1024)

def offer(x: String): Boolean = {
  if (tail - head >= array.length) false  // full
  else {
    array(tail % array.length) = x
    tail += 1
    true
  }
}

def take(): String = {
  if (head == tail) null  // empty
  else {
    val x = array(head % array.length)
    head += 1
    x
  }
}
```

This works for single-threaded code, but introduces a critical problem under concurrency: both threads read and write `head` and `tail` without synchronization, leading to lost updates, stale reads, and silent data corruption. Adding `synchronized` blocks solves the data race but reintroduces contention and latency pauses.

Ring buffers solve both problems with **lock-free algorithms** and **cache-line padding**. A ring buffer provides:
- **No garbage collection** — reuses the same array forever
- **Lock-free access** — uses atomic compare-and-swap (CAS) for coordination, avoiding mutex contention
- **Predictable latency** — no surprise GC pauses or lock waits
- **Thread-specialized variants** — choose SPSC, MPSC, SPMC, or MPMC based on your thread pattern

This module provides four implementations tuned for maximum throughput and minimal latency across all producer/consumer combinations.

## Overview

Ring buffers are high-performance data structures for:

- **Event queues** in IO, networking, and game engines where throughput and latency matter
- **Thread-safe work queues** with bounded capacity to backpressure senders
- **Inter-thread communication** between producers and consumers with predictable latency
- **Concurrent batch processing** where producers fill elements and consumers drain them

### Core Principles

- **Fixed capacity** — allocated upfront, no dynamic resizing or garbage collection churn
- **Lock-free** — no mutexes or blocking, uses atomic compare-and-swap (CAS) for coordination
- **Circular memory** — slots wrap around when indices exceed the capacity, reusing the same array
- **FIFO ordering** — elements are taken in the order they were offered
- **Non-nullable elements** — all implementations reject `null` inputs

## Why Ring Buffers

Ring buffers excel when you need:

- **Ultra-low latency** — lock-free algorithms and cache-line padding eliminate pauses from contention
- **Predictable throughput** — no GC overhead since the same memory array is reused forever
- **Bounded resources** — fixed capacity prevents runaway memory growth and enables backpressure
- **High concurrency** — multiple implementations optimized for different thread patterns (SPSC, MPMC, etc.)

### Comparison with Java and Scala Alternatives

| Property                | RingBuffer (ZIO Blocks)                | `java.util.Queue` (ConcurrentLinkedQueue) | `scala.collection.concurrent.TrieMap` | Array + manual index     | Disruptor                |
|-------------------------|----------------------------------------|-------------------------------------------|---------------------------------------|--------------------------|--------------------------|
| **Allocation**          | Single upfront                         | Per-element nodes                         | Per-node allocations                  | Single upfront           | Single upfront           |
| **Lock-free**           | Yes (CAS-based)                        | Yes                                       | Yes (CASes on trie nodes)             | Yes (if single-threaded) | Yes (CAS)                |
| **GC pressure**         | Minimal (reuses slots)                 | High (node garbage)                       | High (trie node garbage)              | Minimal                  | Minimal                  |
| **Bounded**             | Fixed capacity                         | Unbounded                                 | Unbounded                             | Fixed                    | Fixed (bounded capacity) |
| **Thread patterns**     | Four variants (SPSC, MPSC, SPMC, MPMC) | Multi-producer/multi-consumer             | Multi-reader/multi-writer             | Limited (see impl)       | Multi-producer/consumer  |
| **Predictable latency** | High (no GC)                           | Medium (GC pauses)                        | Medium (GC pauses)                    | High (if no contention)  | High                     |

RingBuffer is ideal when you control both producer and consumer thread counts and want maximum performance. `java.util.Queue` is better if you need unbounded capacity; Disruptor is a comparable JVM alternative with similar guarantees.

## Four Implementations

ZIO Blocks provides four ring buffer implementations, each optimized for a specific producer/consumer thread pattern:

| Implementation       | Producers | Consumers | Algorithm                                                                                | Use Case                                            |
|----------------------|-----------|-----------|------------------------------------------------------------------------------------------|-----------------------------------------------------|
| **`SpscRingBuffer`** | Single    | Single    | FastFlow + look-ahead read of slot at `pIdx + lookAheadStep`                             | Bounded FIFO channel; fastest for 1:1 communication |
| **`SpmcRingBuffer`** | Single    | Multiple  | Index-based with CAS on consumer index                                                   | One producer batching to many workers               |
| **`MpscRingBuffer`** | Multiple  | Single    | CAS on producer index + cached limit; null-slot reading on consumer (FastFlow semantics) | Many producers, single aggregator                   |
| **`MpmcRingBuffer`** | Multiple  | Multiple  | Vyukov/Dmitry with sequence buffer                                                       | General-purpose multi-producer/consumer queue       |

**Using the wrong implementation for your thread pattern may result in data races, silent data loss, or crashes.** Always match the number of threads to the ring buffer variant.

## Installation

Add the ZIO Blocks Ring Buffer module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "@VERSION@"
```

For Scala.js cross-platform support:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-ringbuffer" % "@VERSION@"
```

## Creating Instances

Ring buffers are instantiated via the companion object's `apply` method:

### `SpscRingBuffer` — Single Producer, Single Consumer

`SpscRingBuffer[A]` uses the FastFlow pattern with a look-ahead cache. On the fast path, the producer checks a cached `producerLimit` to avoid reading `consumerIndex`. When the cached limit is exhausted, the slow path reads the array slot at `producerIndex + lookAheadStep` (where `lookAheadStep = min(capacity/4, 4096)`) — never `consumerIndex` directly. This keeps the producer and consumer cache lines fully independent. The consumer uses null/non-null slot reads (FastFlow semantics). Together, these avoid repeated volatile reads and minimize cross-core cache traffic.

```scala
object SpscRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SpscRingBuffer[A]
}
```

Creates a new SPSC ring buffer with the given capacity. The capacity must be a positive power of two.

We create an SPSC buffer as follows:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](16)  // capacity must be power of 2
```

### `SpmcRingBuffer` — Single Producer, Multiple Consumers

`SpmcRingBuffer[A]` allows a single producer thread to offer elements while multiple consumer threads concurrently take elements via compare-and-swap on the consumer index.

```scala
object SpmcRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A]
}
```

Creates a new SPMC ring buffer with the given capacity. The capacity must be a positive power of two.

We create an SPMC buffer as follows:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.SpmcRingBuffer

val rb = SpmcRingBuffer[java.lang.Integer](64)
```

### `MpscRingBuffer` — Multiple Producers, Single Consumer

`MpscRingBuffer[A]` allows multiple producer threads to offer elements concurrently via compare-and-swap on the producer index, while a single consumer thread takes elements efficiently.

```scala
object MpscRingBuffer {
  def apply[A <: AnyRef](capacity: Int): MpscRingBuffer[A]
}
```

Creates a new MPSC ring buffer with the given capacity. The capacity must be a positive power of two.

We create an MPSC buffer as follows:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpscRingBuffer

val rb = MpscRingBuffer[java.lang.Long](32)
```

### `MpmcRingBuffer` — Multiple Producers, Multiple Consumers

`MpmcRingBuffer[A]` is the fully general-purpose implementation supporting any number of producers and consumers. It uses the Vyukov/Dmitry algorithm with a parallel sequence buffer to coordinate access safely.

```scala
object MpmcRingBuffer {
  def apply[A <: AnyRef](capacity: Int): MpmcRingBuffer[A]
}
```

Creates a new MPMC ring buffer with the given capacity. The capacity must be a power of two >= 2 (the sequence buffer algorithm requires at least 2 slots).

We create an MPMC buffer as follows:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpmcRingBuffer

val rb = MpmcRingBuffer[String](128)
```

### Capacity Requirements

All implementations require capacity to be a positive power of two. Valid and invalid capacities look like:

```scala
SpscRingBuffer[String](16)   // OK: 2^4 = 16
SpscRingBuffer[String](1024) // OK: 2^10 = 1024
SpscRingBuffer[String](15)   // Error: not power of 2
SpscRingBuffer[String](0)    // Error: must be > 0
```

`MpmcRingBuffer` additionally requires capacity >= 2 to distinguish between empty and full states in its sequence buffer algorithm.

## Core Operations

Ring buffers provide the same core API across all four implementations: `offer`, `take`, `size`, `isEmpty`, and `isFull`. The SPSC variant additionally provides batch operations `drain` and `fill`.

### Inserting Elements — `offer`

`SpscRingBuffer#offer` — Insert an element (SPSC) with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` if inserted, `false` if the buffer is full. Throws `NullPointerException` if the element is `null`. Must be called from the producer thread only.

`SpmcRingBuffer#offer` — Insert an element (SPMC) with this signature:

```scala
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` if inserted, `false` if the buffer is full. Throws `NullPointerException` if the element is `null`. May be called from the single producer thread only; concurrent calls from multiple threads cause undefined behavior.

`MpscRingBuffer#offer` — Insert an element (MPSC) with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` if inserted, `false` if the buffer is full. Throws `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

`MpmcRingBuffer#offer` — Insert an element (MPMC) with this signature:

```scala
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` if inserted, `false` if the buffer is full. Throws `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

We can offer elements and check the return value to handle backpressure:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](4)
```

When the buffer becomes full, `offer` returns `false`:

```scala mdoc
val result1 = rb.offer("a")  // true
val result2 = rb.offer("b")  // true
val result3 = rb.offer("c")  // true
val result4 = rb.offer("d")  // true
val result5 = rb.offer("e")  // false (Buffer is full, capacity = 4)
```

### Removing Elements — `take`

`SpscRingBuffer#take` — Remove an element (SPSC) with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking. Returns the element, or `null` if the buffer is empty. Must be called from the consumer thread only.

`SpmcRingBuffer#take` — Remove an element (SPMC) with this signature:

```scala
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking. Returns the element, or `null` if the buffer is empty. Thread-safe; multiple consumer threads may call this concurrently.

`MpscRingBuffer#take` — Remove an element (MPSC) with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking. Returns the element, or `null` if the buffer is empty. Must be called from the single consumer thread only.

`MpmcRingBuffer#take` — Remove an element (MPMC) with this signature:

```scala
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking. Returns the element, or `null` if the buffer is empty. Thread-safe; multiple consumer threads may call this concurrently.

We can take elements in FIFO order from our buffer:

```scala mdoc
rb.take()  // "a"
rb.take()  // "b"
rb.take()  // "c"
rb.take()  // "d"
rb.take()  // null (buffer is empty)
```

### Checking State — `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may have modified the buffer.

`SpscRingBuffer#size` — Approximate element count with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def size: Int
}
```

Returns the approximate number of elements currently in the buffer. Under concurrent access, the result may be stale. O(1).

`SpscRingBuffer#isEmpty` — Check if empty with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def isEmpty: Boolean
}
```

Returns `true` if the buffer contains no elements (approximate). O(1).

`SpscRingBuffer#isFull` — Check if full with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def isFull: Boolean
}
```

Returns `true` if the buffer is at capacity (approximate). O(1).

All four implementations provide the same three methods. We can check state after operations:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb2 = SpscRingBuffer[String](8)
```

State queries are cheap but approximate under concurrency:

```scala mdoc
rb2.offer("x")
rb2.offer("y")

rb2.size      // 2
rb2.isEmpty   // false
rb2.isFull    // false
```

### Batch Operations — `drain` and `fill`

`SpscRingBuffer` provides `drain` and `fill` for batch operations, optimized for the single-threaded case. `MpscRingBuffer` also provides `drain` for batch consumption.

`SpscRingBuffer#drain` — Consume up to N elements (SPSC) with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def drain(consumer: A => Unit, limit: Int): Int
}
```

Removes up to `limit` elements from the buffer, passing each to the `consumer` callback. Returns the number of elements actually drained. Throws `IllegalArgumentException` if `limit` is negative. Must be called from the consumer thread only. O(n) where n is the number of elements drained.

`MpscRingBuffer#drain` — Consume up to N elements (MPSC) with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def drain(consumer: A => Unit, limit: Int): Int
}
```

Removes up to `limit` elements from the buffer, passing each to the `consumer` callback. Returns the number of elements actually drained. Throws `IllegalArgumentException` if `limit` is negative. Must be called from the consumer thread only. O(n) where n is the number of elements drained.

**Note**: Uses relaxed poll semantics and stops at the first `null` slot, which may indicate either an empty buffer or a producer that has claimed a slot but has not yet written its element (mid-write). In the mid-write case, fewer than `limit` elements are returned even though more elements will become available shortly.

`SpscRingBuffer#fill` — Produce up to N elements with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def fill(supplier: () => A, limit: Int): Int
}
```

Inserts up to `limit` elements by calling the `supplier` for each new element. Returns the number of elements actually inserted. Throws `IllegalArgumentException` if `limit` is negative. Must be called from the producer thread only. O(n) where n is the number of elements inserted.

Batch operations help amortize synchronization costs. We can drain multiple elements at once:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb3 = SpscRingBuffer[java.lang.Integer](16)
(1 to 5).foreach(i => rb3.offer(Integer.valueOf(i)))

val collected = scala.collection.mutable.Buffer[java.lang.Integer]()
val drained = rb3.drain(collected += _, 10)  // drained = 5

println(s"Drained items: ${collected.mkString(", ")}")
```

Fill avoids repeated `offer` calls when producing elements:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb4 = SpscRingBuffer[String](8)
var counter = 0
val filled = rb4.fill(() => { counter += 1; s"item-$counter" }, 5)  // filled = 5

println(s"Filled $filled items")
```

## Thread Safety and Correctness

Ring buffers are **lock-free** but must be used correctly:

- **Wrong thread access causes undefined behavior**: Using `SpscRingBuffer` from multiple producer threads results in data races, silent data loss, or crashes. Always use the implementation matching your thread pattern.
- **`SpscRingBuffer#offer` and `SpscRingBuffer#take` thread contract**: The producer thread must be the sole caller of `offer`; the consumer thread must be the sole caller of `take`. They may be the same physical thread (as in single-threaded environments like Scala.js or unit tests) or different threads.
- **State queries are approximate**: Under concurrency, `SpscRingBuffer#size`, `SpscRingBuffer#isEmpty`, and `SpscRingBuffer#isFull` may be stale by the time they return. Do not rely on them for exact synchronization — use `SpscRingBuffer#offer`'s return value for backpressure instead.
- **Null elements are forbidden**: All implementations reject `null` with `NullPointerException`. If you need to store nullable values, wrap them in `Option` or another container.

:::warning
**Critical:** Ring buffers do not enforce thread-safety at runtime. Using the wrong implementation for your thread pattern or calling methods from the wrong thread **does not throw an exception** — it silently corrupts data. Test thoroughly and document your threading contract.
:::

## Advanced Usage: Cache-Line Padding

Ring buffers use **cache-line padding** to prevent false sharing between producer and consumer indices on modern CPUs. The padding is transparent to users but enables dramatically lower latency on multi-core systems.

Each implementation pads its internal index fields (producer index, consumer index) to occupy a full cache line (128 bytes on Apple Silicon, 64 bytes on most other architectures). This ensures that when one thread reads its index, it does not invalidate the cache line holding the other thread's index, eliminating costly cache-coherency traffic.

This optimization is automatic and requires no configuration. Ring buffers are inherently more efficient than comparable Scala and Java implementations because of this padding.

## Designing With Ring Buffers

### Pattern: Producer-Consumer Pipeline

Ring buffers form the backbone of producer-consumer pipelines where one or more producers generate work and one or more consumers process it:

```
┌──────────┐       ┌──────────────┐       ┌──────────┐
│Producer 1├──────>│ RingBuffer   │<──────┤Consumer 1│
│Producer 2├──────>│ (MPMC, cap=N)│<──────┤Consumer 2│
└──────────┘       └──────────────┘       └──────────┘
```

In this pattern:
- Producers call `MpmcRingBuffer#offer` and handle backpressure if `false` is returned (e.g., retry, queue internally, apply rate limiting).
- Consumers call `MpmcRingBuffer#take` in a tight loop, checking for `null` to detect empty buffers.
- Ring buffer capacity bounds memory and provides natural backpressure.

### Pattern: Batch Processing

For workloads where producers batch elements together, use `SpscRingBuffer#fill` (SPSC) or `SpscRingBuffer#offer` in a loop:

```
Producer fills batch of N items
         ↓
   Ring Buffer (growing)
         ↓
Consumer drain()s batch of M items
         ↓
     Process batch
```

Batching reduces per-element synchronization costs.

### Pattern: Work Stealing with Multiple Consumers (MPMC)

When multiple workers consume from the same queue, use `MpmcRingBuffer`. Each worker calls `MpmcRingBuffer#take` to grab the next item atomically:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpmcRingBuffer

case class Task(id: Int, work: String)

val queue = MpmcRingBuffer[Task](256)

def worker(): Unit = {
  while (true) {
    val task = queue.take()
    if (task ne null) {
      println(s"Processing: ${task.work}")
    }
  }
}
```

The CAS loop in `MpmcRingBuffer#take` ensures no two workers grab the same task.

## Performance Characteristics

All ring buffer implementations provide O(1) time complexity for `offer`, `take`, `size`, `isEmpty`, and `isFull` operations.

- **SPSC** (FastFlow) — Fastest: avoids volatile reads on the fast path, minimal cache traffic
- **SPMC** — Fast: producer uses index-based checking; consumers CAS on a shared index
- **MPSC** — Fast: producers CAS on a shared index with a cached limit; consumer uses simple index reads
- **MPMC** — Slightly slower: uses sequence buffer stamps for coordination; all indices use CAS

Actual performance depends on:
- **CPU cache architecture** — 64-byte vs 128-byte cache lines affect padding efficiency
- **Contention level** — high contention increases CAS failure rates and retries
- **Element size** — larger elements may affect cache locality
- **Platform** — JVM JIT warmup, Scala.js compiled code, GraalVM-generated native image

Micro-benchmark your specific workload if latency is critical.

## Integration with Other ZIO Blocks Types

Ring buffers are standalone data structures and do not depend on other ZIO Blocks types. However, they integrate well with:

- **Threading models**: Ring buffers work on raw JVM threads, virtual threads (Loom), or platform-specific threads. Pair with `ZIO.fork` or `Thread` as needed.
- **Reactive streams**: Ring buffers can back reactive sources, where producers feed a `Source` and consumers pull from it. The ring buffer provides natural backpressure via `offer`'s return value.
- **Event loops**: In game engines or event-driven systems, ring buffers connect event producers (input, network) to event dispatchers (main loop) with predictable latency.

## Running the Examples

All code from this guide is available as runnable examples in the `zio-blocks-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### SPSC: Producer-Consumer Ping-Pong

In a single-producer, single-consumer setup, use `SpscRingBuffer` for maximum throughput. This example demonstrates how two threads communicate efficiently using FastFlow signaling.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/SpscExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/SpscExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.SpscExample"
```

### MPSC: Multiple Producers, Single Aggregator

When multiple threads produce work for a single processor, use `MpscRingBuffer`. This example shows how three producer threads safely offer items to a single consumer.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/MpscExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/MpscExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.MpscExample"
```

### MPMC: General-Purpose Queue

For workloads with multiple producers and consumers, use `MpmcRingBuffer`. This example demonstrates how multiple workers coordinate to process tasks from a shared queue.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/MpmcExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/MpmcExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.MpmcExample"
```

### Batch Fill and Drain (SPSC)

Use `fill` and `drain` for efficient batch operations. This example shows how to amortize synchronization costs by processing multiple elements at once.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/BatchExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/BatchExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.BatchExample"
```
