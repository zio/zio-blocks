---
id: ringbuffer
title: "Ring Buffer"
---

`RingBuffer[A]` is a **fixed-size, lock-free queue** for efficiently exchanging elements between producer and consumer threads with minimal contention and cache-line effects. Ring buffers use a circular array to recycle memory, eliminating garbage collection pressure from transient allocations. The module provides four specialized implementations tuned for different producer/consumer thread patterns.

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

| Property              | RingBuffer (ZIO Blocks) | `java.util.Queue` (ConcurrentLinkedQueue) | `scala.collection.concurrent.TrieMap` | Array + manual index | Disruptor |
|----------------------|------------------------|-------------------------------------------|---------------------------------------|----------------------|-----------|
| **Allocation** | Single upfront        | Per-element nodes                          | Per-node allocations                  | Single upfront       | Single upfront |
| **Lock-free** | Yes (CAS-based)       | Yes                                        | Yes (CASes on trie nodes)            | Yes (if single-threaded) | Yes (CAS) |
| **GC pressure** | Minimal (reuses slots) | High (node garbage)                        | High (trie node garbage)             | Minimal              | Minimal |
| **Bounded** | Fixed capacity        | Unbounded                                  | Unbounded                            | Fixed                | Fixed (bounded capacity) |
| **Thread patterns** | Four variants (SPSC, MPSC, SPMC, MPMC) | Multi-producer/multi-consumer | Multi-reader/multi-writer | Limited (see impl) | Multi-producer/consumer |
| **Predictable latency** | High (no GC)         | Medium (GC pauses)                         | Medium (GC pauses)                   | High (if no contention) | High |

RingBuffer is ideal when you control both producer and consumer thread counts and want maximum performance. `java.util.Queue` is better if you need unbounded capacity; Disruptor is a comparable JVM alternative with similar guarantees.

## Four Implementations

ZIO Blocks provides four ring buffer implementations, each optimized for a specific producer/consumer thread pattern:

| Implementation | Producers | Consumers | Algorithm | Use Case |
|---|---|---|---|---|
| **`SpscRingBuffer`** | Single | Single | FastFlow (null-based signaling) | Bounded FIFO channel; fastest for 1:1 communication |
| **`SpmcRingBuffer`** | Single | Multiple | Index-based with CAS on consumer index | One producer batching to many workers |
| **`MpscRingBuffer`** | Multiple | Single | CAS on producer index + cached producer limit | Many producers, single aggregator |
| **`MpmcRingBuffer`** | Multiple | Multiple | Vyukov/Dmitry with sequence buffer | General-purpose multi-producer/consumer queue |

**Using the wrong implementation for your thread pattern may result in data races, silent data loss, or crashes.** Always match the number of threads to the ring buffer variant.

## Creating Instances

### SpscRingBuffer — Single Producer, Single Consumer

`SpscRingBuffer[A]` uses the FastFlow pattern: elements signal their presence via null/non-null slots in the array. The producer never reads `consumerIndex`, and the consumer never reads `producerIndex`, minimizing cross-core cache traffic.

```scala
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](16)  // capacity must be power of 2
```

### SpmcRingBuffer — Single Producer, Multiple Consumers

`SpmcRingBuffer[A]` allows a single producer thread to offer elements while multiple consumer threads concurrently take elements via compare-and-swap on the consumer index.

```scala
import zio.blocks.ringbuffer.SpmcRingBuffer

val rb = SpmcRingBuffer[Int](64)
```

### MpscRingBuffer — Multiple Producers, Single Consumer

`MpscRingBuffer[A]` allows multiple producer threads to offer elements concurrently via compare-and-swap on the producer index, while a single consumer thread takes elements efficiently.

```scala
import zio.blocks.ringbuffer.MpscRingBuffer

val rb = MpscRingBuffer[Long](32)
```

### MpmcRingBuffer — Multiple Producers, Multiple Consumers

`MpmcRingBuffer[A]` is the fully general-purpose implementation supporting any number of producers and consumers. It uses the Vyukov/Dmitry algorithm with a parallel sequence buffer to coordinate access safely.

```scala
import zio.blocks.ringbuffer.MpmcRingBuffer

val rb = MpmcRingBuffer[String](128)
```

### Capacity Requirements

All implementations require capacity to be a positive power of two:

```scala
SpscRingBuffer[String](16)   // OK: 2^4 = 16
SpscRingBuffer[String](1024) // OK: 2^10 = 1024
SpscRingBuffer[String](15)   // Error: not power of 2
SpscRingBuffer[String](0)    // Error: must be > 0
```

`MpmcRingBuffer` additionally requires capacity >= 2 to distinguish between empty and full states in its sequence buffer algorithm.

## Core Operations

### Insert Elements with `offer`

The `offer` method attempts to insert an element without blocking. It returns `true` if the element was inserted, `false` if the buffer is full.

`SpscRingBuffer#offer` — Insert an element (SPSC):

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def offer(a: A): Boolean
}
```

Throws `NullPointerException` if the element is `null`. Must be called from the producer thread only.

`SpmcRingBuffer#offer` — Insert an element (SPMC):

```scala
trait SpmcRingBuffer[A <: AnyRef] {
  def offer(a: A): Boolean
}
```

Throws `NullPointerException` if the element is `null`. May be called from the single producer thread only; concurrent calls from multiple threads cause undefined behavior.

`MpscRingBuffer#offer` — Insert an element (MPSC):

```scala
trait MpscRingBuffer[A <: AnyRef] {
  def offer(a: A): Boolean
}
```

Throws `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

`MpmcRingBuffer#offer` — Insert an element (MPMC):

```scala
trait MpmcRingBuffer[A <: AnyRef] {
  def offer(a: A): Boolean
}
```

Throws `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

We can offer elements and check the return value to handle backpressure:

```scala
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](4)

// When the buffer becomes full, `offer` returns `false`:
val result1 = rb.offer("a")  // true
val result2 = rb.offer("b")  // true
val result3 = rb.offer("c")  // true
val result4 = rb.offer("d")  // true
val result5 = rb.offer("e")  // false (Buffer is full, capacity = 4)
```

### Remove Elements with `take`

The `take` method retrieves and removes an element from the front of the buffer. It returns the element, or `null` if the buffer is empty. Returns immediately without blocking.

`SpscRingBuffer#take` — Remove an element (SPSC):

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def take(): A
}
```

Must be called from the consumer thread only. Returns the element, or `null` if the buffer is empty.

`SpmcRingBuffer#take` — Remove an element (SPMC):

```scala
trait SpmcRingBuffer[A <: AnyRef] {
  def take(): A
}
```

Thread-safe; multiple consumer threads may call this concurrently.

`MpscRingBuffer#take` — Remove an element (MPSC):

```scala
trait MpscRingBuffer[A <: AnyRef] {
  def take(): A
}
```

Must be called from the single consumer thread only.

`MpmcRingBuffer#take` — Remove an element (MPMC):

```scala
trait MpmcRingBuffer[A <: AnyRef] {
  def take(): A
}
```

Thread-safe; multiple consumer threads may call this concurrently.

We can take elements in FIFO order:

```scala
rb.take()  // "a"
rb.take()  // "b"
rb.take()  // "c"
rb.take()  // null (buffer is empty)
```

### Check State: `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may have modified the buffer.

`SpscRingBuffer#size` — Approximate element count:

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def size: Int
}
```

Returns the approximate number of elements currently in the buffer (approximate because of concurrent access). O(1).

`SpscRingBuffer#isEmpty` — Check if empty:

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def isEmpty: Boolean
}
```

Returns `true` if the buffer contains no elements (approximate). O(1).

`SpscRingBuffer#isFull` — Check if full:

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def isFull: Boolean
}
```

Returns `true` if the buffer is at capacity (approximate). O(1).

All four implementations provide the same three methods. We can check state after operations:

```scala
val rb2 = SpscRingBuffer[String](8)
rb2.offer("x")
rb2.offer("y")

// State queries are cheap but approximate under concurrency:
rb2.size      // 2
rb2.isEmpty   // false
rb2.isFull    // false
```

### Batch Operations: `drain` and `fill` (SPSC only)

`SpscRingBuffer` provides `drain` and `fill` for batch operations, optimized for the single-threaded case.

`SpscRingBuffer#drain` — Consume up to N elements:

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def drain(consumer: A => Unit, limit: Int): Int
}
```

Removes up to `limit` elements from the buffer, passing each to the `consumer` callback. Returns the number of elements actually drained. Must be called from the consumer thread only. O(n) where n is the number of elements drained.

`SpscRingBuffer#fill` — Produce up to N elements:

```scala
trait SpscRingBuffer[A <: AnyRef] {
  def fill(supplier: () => A, limit: Int): Int
}
```

Inserts up to `limit` elements by calling the `supplier` for each new element. Returns the number of elements actually inserted. Must be called from the producer thread only. O(n) where n is the number of elements inserted.

Batch operations help amortize synchronization costs. We can drain multiple elements at once:

```scala
val rb3 = SpscRingBuffer[Int](16)
(1 to 5).foreach(i => rb3.offer(i))

// Drain collects elements into a buffer and invokes the callback for each:
val collected = scala.collection.mutable.Buffer[Int]()
val drained = rb3.drain(collected += _, 10)  // drained = 5

// Fill avoids repeated `offer` calls when producing elements:
val rb4 = SpscRingBuffer[String](8)
var counter = 0
val filled = rb4.fill(() => { counter += 1; s"item-$counter" }, 5)  // filled = 5
```

## Thread Safety and Correctness

Ring buffers are **lock-free** but must be used correctly:

- **Wrong thread access causes undefined behavior**: Using `SpscRingBuffer` from multiple producer threads results in data races, silent data loss, or crashes. Always use the implementation matching your thread pattern.
- **`SpscRingBuffer#offer` and `SpscRingBuffer#take` must not be called from the same thread**: The producer and consumer must be on separate threads to avoid deadlock or missed updates.
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

```scala
val queue = MpmcRingBuffer[Task](256)

// Worker threads
def worker(): Unit = {
  while (true) {
    val task = queue.take()
    if (task ne null) {
      processTask(task)
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

## Examples

All examples below are self-contained and can be run individually.

### SPSC: Producer-Consumer Ping-Pong

In a single-producer, single-consumer setup, use `SpscRingBuffer` for maximum throughput:

```scala
import zio.blocks.ringbuffer.SpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object SpscExample {
  def main(args: Array[String]): Unit = {
    val buffer = SpscRingBuffer[String](8)
    val latch = new CountDownLatch(1)

    val producer = new Thread(() => {
      for (i <- 1 to 5) {
        buffer.offer(s"message-$i")
      }
    })

    val consumer = new Thread(() => {
      for (_ <- 1 to 5) {
        var msg: String = null
        while ({ msg = buffer.take(); msg.eq(null) }) {}
        println(s"Received: $msg")
      }
      latch.countDown()
    })

    producer.start()
    consumer.start()
    latch.await()
    println("Done")
  }
}
```

### MPSC: Multiple Producers, Single Aggregator

When multiple threads produce work for a single processor, use `MpscRingBuffer`:

```scala
import zio.blocks.ringbuffer.MpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object MpscExample {
  def main(args: Array[String]): Unit = {
    val buffer = MpscRingBuffer[Int](16)
    val latch = new CountDownLatch(1)

    val producers = (0 until 3).map { id =>
      new Thread(() => {
        for (i <- 1 to 4) {
          buffer.offer(id * 100 + i)
        }
      })
    }

    val consumer = new Thread(() => {
      var received = 0
      while (received < 12) {
        val item = buffer.take()
        if (!item.eq(null)) {
          println(s"Processed: $item")
          received += 1
        }
      }
      latch.countDown()
    })

    producers.foreach(_.start())
    consumer.start()
    producers.foreach(_.join())
    latch.await()
    println("All items processed")
  }
}
```

### MPMC: General-Purpose Queue

For workloads with multiple producers and consumers, use `MpmcRingBuffer`:

```scala
import zio.blocks.ringbuffer.MpmcRingBuffer
import java.util.concurrent.{CountDownLatch, Thread, atomic}

object MpmcExample {
  def main(args: Array[String]): Unit = {
    val buffer = MpmcRingBuffer[String](32)
    val processed = new atomic.AtomicInteger(0)
    val latch = new CountDownLatch(1)

    val producers = (0 until 2).map { id =>
      new Thread(() => {
        for (i <- 1 to 5) {
          buffer.offer(s"task-$id-$i")
        }
      })
    }

    val consumers = (0 until 2).map { _ =>
      new Thread(() => {
        while (processed.get() < 10) {
          val task = buffer.take()
          if (!task.eq(null)) {
            println(s"Worker processing: $task")
            processed.incrementAndGet()
          }
        }
      })
    }

    (producers ++ consumers).foreach(_.start())
    producers.foreach(_.join())
    while (processed.get() < 10) Thread.sleep(1)
    latch.countDown()
    println("All tasks completed")
  }
}
```

### Batch Fill and Drain (SPSC)

Use `fill` and `drain` for efficient batch operations:

```scala
import zio.blocks.ringbuffer.SpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object BatchExample {
  def main(args: Array[String]): Unit = {
    val buffer = SpscRingBuffer[Int](64)
    val latch = new CountDownLatch(1)

    val producer = new Thread(() => {
      var batch = 1
      while (batch <= 3) {
        val count = buffer.fill(() => { batch * 100 + batch; batch += 1; batch - 1 }, 10)
        println(s"Filled $count items in batch")
        batch += 1
      }
    })

    val consumer = new Thread(() => {
      val items = scala.collection.mutable.Buffer[Int]()
      while (items.size < 30) {
        val drained = buffer.drain(items += _, 10)
        if (drained > 0) println(s"Drained $drained items")
      }
      println(s"Total items consumed: ${items.size}")
      latch.countDown()
    })

    producer.start()
    consumer.start()
    latch.await()
    println("Done")
  }
}
```
