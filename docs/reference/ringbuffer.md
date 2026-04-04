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

## Why FastFlow?

Understanding the FastFlow algorithm helps explain why `SpscRingBuffer` achieves such high performance.

Imagine two threads sharing data with a traditional lock-based queue like `ArrayBlockingQueue`. They use a mutex (lock) to coordinate:

1. Producer thread: acquires lock, adds element, releases lock
2. Consumer thread: acquires lock, removes element, releases lock

This works, but locks have a cost: when threads contend for the same lock, one thread **blocks** (goes to sleep) while waiting. Waking a thread is expensive (thousands of CPU cycles). Even lock-free queues using `synchronized` or `volatile` reads can cause **cache-coherency traffic**: when one CPU core writes to a variable another core reads, the entire cache line must be invalidated and transferred — a costly operation that slows down both cores.

The fundamental issue: **if the producer has to read what the consumer wrote (or vice versa), their CPU caches constantly fight**. This is called *false sharing* and can reduce throughput by 10x or more on heavily loaded systems.

The problem is that *any* read by the producer of the consumer's state (or vice versa) causes cache line bouncing. So FastFlow's radical idea: **neither side should ever read the other's state**.

Instead, the producer simply **writes data into slots** and marks them non-null. The consumer independently **reads slots** and takes any non-null values. The array slot's null/non-null status itself is the only coordination needed — a *happens-before* relationship written once by the producer, read once by the consumer. No locks, no atomic operations on the fast path, no reading the other side's counters.

The **note-passing analogy**:
- You (producer) have a row of empty desks between you and your friend (consumer)
- Empty desk = `null` (available)
- Note on desk = non-null value (message ready)
- **You never look at your friend's side** — you just place notes on empty desks
- **Your friend never looks at your side** — they just pick up notes they see
- No shouting "are you ready?", no waiting, no lock contention

**This is how FastFlow solves the cache-coherency problem**: since producer and consumer never read each other's counters, there's no cache line bouncing between CPU cores. All coordination happens through the slots themselves, which are written once and read once.

The **look-ahead cache** is a further optimization: the producer maintains a local cached limit (`producerLimit`) so they don't have to check every slot individually. It's like glancing ahead at the next N desks to see if they're empty, without actually bending over to look. This keeps the fast path extremely fast — just a local counter check and an array write.

**Why FastFlow is fast**:
- **No locks** — no thread ever blocks another
- **Minimal cache coordination** — producer and consumer touch separate memory locations; the array slot is written once, read once
- **Write-once, read-once semantics** — the slot's null/non-null status is the handshake
- **Cache-line padding** — producer and consumer indices padded to separate cache lines, eliminating false sharing

The result: **lock-free, wait-free** communication that scales linearly with CPU count and achieves nanosecond-scale latencies. This is why FastFlow is the algorithm of choice for SPSC ring buffers in high-performance systems like trading platforms, game engines, and network stacks.

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

The algorithm ensures that **the producer never reads `consumerIndex` and the consumer never reads `producerIndex`**. Instead, the array slot's null/non-null state is the only coordination mechanism:

- **Producer flow**: Check if `producerIndex < producerLimit`; if so, try to write. Before writing, verify the target slot is still `null` (sequential specification ensures this check is sufficient). Write the element, then increment `producerIndex` with release semantics. If `producerIndex >= producerLimit`, execute slow path: read the slot at `producerIndex + lookAheadStep` to determine remaining capacity and refresh `producerLimit`.
- **Consumer flow**: Read the slot at `consumerIndex`. If `null`, the buffer is empty. If non-null, take the element, write `null` to clear (preventing memory leaks in reference types), then increment `consumerIndex` with acquire semantics.
- **Empty detection**: `consumerIndex == producerIndex` (both use mask wrap-around via `& mask`)
- **Full detection**: Producer knows via `producerLimit` cache; when exhausted, slow path recalculates available slots by examining array contents.

**Concurrency contract**: Exactly one producer thread may call `offer` and exactly one consumer thread may call `take`. Calling from multiple producers or multiple consumers causes data races and undefined behavior. The buffer does not enforce this at runtime — it is the caller's responsibility.

**Why this is fast**: No volatile reads on the fast path (producer reads only its own `producerLimit`, consumer reads only the array). Cache-line padding ensures `producerIndex` and `consumerIndex` reside on separate cache lines, eliminating false sharing. The fast path consists of a local counter check, an array store, and a counter increment — all in registers or private cache.

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

The algorithm uses **index-based capacity coordination** with a cached `producerLimit` on the producer side and **CAS-based claiming** on the consumer side:

- **Producer flow** (single thread, no CAS): The producer maintains `producerIndex` and `producerLimit`. On each `offer`, it checks if `producerIndex < producerLimit`. If not, it refreshes `producerLimit` by reading the volatile `consumerIndex` and adding `capacity - 1` (or equivalently, computing how many slots are currently free). This cached limit avoids reading `consumerIndex` on every offer. When space is available, the producer writes the element to `buffer((producerIndex + 1) & mask)` using release semantics, then increments `producerIndex`. The producer never uses CAS and never clears slots after consumers take.
- **Consumer flow** (multiple threads, CAS required): Each consumer first reads the current `consumerIndex` (volatile). It then computes the offset and reads the element *before* attempting CAS. The consumer then CAS-updates `consumerIndex` from the value it read to `readIndex + 1`. If CAS succeeds, the consumer has claimed that element and may return it. If CAS fails, another consumer won the race; the consumer refreshes `consumerIndex` and retries. Importantly, the consumer does **not** clear the slot to `null` — the producer will overwrite it once a future `producerLimit` refresh shows the slot is no longer in use (because `consumerIndex` has advanced).
- **Validity invariant**: An element is considered present in the buffer if and only if its position `i` (relative to the ring) satisfies `(i - consumerIndex) mod capacity < (producerIndex - consumerIndex) mod capacity`. In practice, the producer only needs to ensure `producerIndex - consumerIndex < capacity` before writing; the consumer relies on the CAS to ensure exclusive access to a slot whose index falls in that range.
- **Empty detection**: A consumer may find `consumerIndex == producerIndex` at the moment it reads `consumerIndex`, meaning no elements are available. However, because multiple consumers race, one may succeed while another fails — each CAS attempt independently validates whether an element exists at the claimed position.
- **Memory ordering**: Producer uses release semantics when writing elements; consumers use acquire semantics when reading elements to establish a happens-before relationship upon CAS success.

**Concurrency contract**: Exactly one producer thread may call `offer`. Any number of consumer threads may call `take` concurrently. Using multiple producers causes data races. The buffer does not enforce these contracts at runtime.

**Key optimization**: The producer's cached `producerLimit` reduces the frequency of volatile `consumerIndex` reads from every offer to approximately every `capacity` offers, significantly lowering cache-coherency traffic. Consumer CAS contention is handled by retry loops; each failed CAS simply means another consumer consumed the element.

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

`MpscRingBuffer[A]` allows multiple producer threads to offer elements concurrently via compare-and-swap on the producer index, while a single consumer thread takes elements efficiently. This is a hybrid design combining multi-producer CAS coordination with FastFlow-style consumer semantics.

The algorithm (based on JCTools `MpscArrayQueue`) works as follows:

- **Producer flow** (multiple threads, CAS required): Multiple producers compete using a CAS loop on `producerIndex`. Each producer reads the current `producerIndex`, computes the prospective write offset, checks if the slot is `null` (sequential spec guarantee), then CAS-updates `producerIndex` from the read value to `readValue + 1`. If CAS succeeds, the producer owns that slot and writes the element with release semantics. If CAS fails, another producer won the race; the failed producer retries with the new `producerIndex`. Producers maintain a cached `producerLimit` (derived from `consumerIndex` on slow path) to avoid reading the volatile `consumerIndex` on every offer. The limit starts at `capacity` (constructor) and is refreshed when exhausted.
- **Consumer flow** (single thread, no CAS): The consumer reads the slot at `consumerIndex` directly. If the slot is `null`, the buffer is empty. If non-null, the consumer takes the element, writes `null` to clear the slot (preventing memory leaks), and increments `consumerIndex`. Critically, **the consumer never reads `producerIndex`**. Instead, it relies on the slot's null/non-null state as the coordination signal — this is the FastFlow pattern. However, because producers may claim a slot via CAS but not yet write the element, a `null` result from `take` can mean either empty or producer mid-write (the so-called "relaxed poll" semantics).
- **Mid-write scenario**: Consider two producers: Producer A CAS-claims slot N but hasn't written yet; Producer B later CAS-claims slot N+1 and writes; Consumer reads slot N and sees `null`. The consumer returns `null` even though slots N+1 and later may contain elements. This is a deliberate trade-off: the consumer cannot distinguish between truly empty and mid-write without reading `producerIndex`, which would break the FastFlow guarantee. Applications that cannot tolerate this should use `SpscRingBuffer` (single producer) or `MpmcRingBuffer` (fully coordinated).
- **Empty detection**: The consumer can only know for certain that the buffer is empty when it reads `consumerIndex` and finds that `consumerIndex == producerIndex` (computed via slow path if needed). However, `take` does not perform this check; it simply returns `null` if the slot read is `null`, which may occur mid-write even when later slots contain elements.
- **Memory ordering**: Producers use release semantics when writing elements; the consumer uses acquire semantics when reading. The cached `producerLimit` is deliberately racy (may be stale) but this is benign: it only causes the producer to unnecessarily enter the slow path, not incorrect behavior.

**Concurrency contract**: Any number of producer threads may call `offer` concurrently. Exactly one consumer thread may call `take`. Using multiple consumers causes data races. The buffer does not enforce these contracts at runtime.

**Key optimization**: The single consumer side is extremely fast — just a slot read and a counter increment, with no CAS, no locking, and no reads of `producerIndex`. The producers bear the coordination cost among themselves, isolating the consumer from contention. This design shines when there are many producer threads (e.g., aggregating events from multiple sources) and one dedicated consumer (e.g., a batched writer or processor).

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

`MpmcRingBuffer[A]` is the fully general-purpose implementation supporting any number of producers and consumers. It uses the **Vyukov/Dmitry bounded MPMC queue algorithm** (also known as the sequence buffer algorithm) to coordinate access safely without locks. This is the only implementation that handles concurrent access on both producer and consumer sides.

The algorithm maintains two indices (`producerIndex`, `consumerIndex`) and a `sequenceBuffer: Array[Long]` parallel to the data `buffer`. Each slot's sequence stamp encodes its state:

- **Producer flow** (multiple threads, CAS required): A producer reads the current `producerIndex` (volatile) and computes the slot offset `(producerIndex & mask)`. It then reads `sequenceBuffer(offset)` and checks whether `seq == producerIndex`. If so, the slot is available. The producer then CAS-updates `producerIndex` from the read value to `readValue + 1`. If CAS succeeds, the producer owns that slot, writes the element to `buffer(offset)` with release semantics, and updates `sequenceBuffer(offset) = producerIndex + 1` (marking the slot as filled). If CAS fails, another producer won the race; retry with the new `producerIndex`.
- **Consumer flow** (multiple threads, CAS required): A consumer reads the current `consumerIndex` (volatile) and computes the offset `(consumerIndex & mask)`. It reads `sequenceBuffer(offset)` and checks whether `seq == consumerIndex + 1`. If so, the element is ready. The consumer then reads the element from `buffer(offset)` with acquire semantics, CAS-updates `consumerIndex` from the read value to `readValue + 1`, and finally writes `null` to `buffer(offset)` to clear (preventing memory leaks). If CAS fails, another consumer won the race; retry. After successful CAS, the consumer also updates `sequenceBuffer(offset) = consumerIndex + capacity` (signaling to producers that this slot is now available for the next cycle).
- **Sequence buffer semantics**: The `sequenceBuffer` stamps act as version counters:
  - Initial state: `sequenceBuffer(i) == i` (available for producer to claim)
  - After producer fills slot: `sequenceBuffer(offset) == producerIndex + 1`
  - After consumer empties slot: `sequenceBuffer(offset) == consumerIndex + capacity`
  - The capacity offset prevents ABA issues across multiple wrap-around cycles.
- **Empty detection**: A consumer may find `sequenceBuffer(offset) != consumerIndex + 1`, meaning no element is ready. This typically occurs when `consumerIndex == producerIndex` (or the consumer is ahead of the producer due to concurrent advancement). The consumer returns `null` or retries depending on the exact API contract. (The `take` method internally handles retries or returns `null` if empty.)
- **Full detection**: A producer may find `sequenceBuffer(offset) != producerIndex`, meaning the slot is not available for claim (consumer hasn't yet processed it enough). The producer returns `false` from `offer` or retries, depending on the variant.
- **Memory ordering**: Both sides use VarHandle acquire/release semantics. The sequence buffer operations establish a total order across all operations, ensuring linearizability.

**Concurrency contract**: Any number of producer threads may call `offer` concurrently. Any number of consumer threads may call `take` concurrently. The algorithm provides full thread safety without external synchronization.

**Why this algorithm**: The Vyukov/Dmitry sequence buffer elegantly solves the MPMC problem without requiring per-slot CAS. Only the indices are CASed, reducing contention overhead. The sequence stamps in the buffer prevent ABA issues and allow producers and consumers to operate on different slots without interfering. This makes `MpmcRingBuffer` the most versatile but also slightly slower than the specialized single-side variants due to CAS on both sides.

**Capacity constraint**: The algorithm requires `capacity >= 2` because the sequence stamp encoding (`consumerIndex + capacity`) needs distinct values from producer stamps to avoid ambiguity. A capacity of 1 would not permit distinguishing full from empty reliably.

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

Common patterns for using ring buffers include:

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
