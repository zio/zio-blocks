---
id: ringbuffer
title: "Ring Buffer"
---

# ZIO Blocks — Ring Buffer (`zio.blocks.ringbuffer`)

`zio.blocks.ringbuffer` is a family of **high-performance, bounded ring buffers** for the JVM (and Scala.js). Each variant is optimized for a specific producer/consumer threading pattern—pick the one that matches your use case and get the fastest possible inter-thread communication with zero dependencies.

Lock-free variants expose `offer` (returns `false` if full) and `take` (returns `null` if empty)—both non-blocking. Blocking variants expose `offer`/`take` (block until space/element is available) and `tryOffer`/`tryTake` (non-blocking, same semantics as the lock-free API). Capacity must be a **power of two** (enables bitwise masking instead of modulo). Elements must be **non-null reference types** (`A <: AnyRef`).

## Ring buffer variants

| Type | Producers | Consumers | Blocking | Algorithm |
|------|-----------|-----------|----------|-----------|
| `SpscRingBuffer` | 1 | 1 | No | FastFlow (null/non-null signaling) |
| `SpmcRingBuffer` | 1 | Many | No | Index-based capacity + CAS consumers |
| `MpscRingBuffer` | Many | 1 | No | CAS producers + relaxed poll |
| `MpmcRingBuffer` | Many | Many | No | Vyukov/Dmitry sequence buffer |
| `BlockingSpscRingBuffer` | 1 | 1 | Yes | `LockSupport.park/unpark` (Dekker) |
| `BlockingSpmcRingBuffer` | 1 | Many | Yes | `LockSupport` producer + `ReentrantLock` consumers |
| `BlockingMpscRingBuffer` | Many | 1 | Yes | `ReentrantLock` producers + `LockSupport` consumer |
| `BlockingMpmcRingBuffer` | Many | Many | Yes | Dual `ReentrantLock` (like `LinkedBlockingQueue`) |

**Naming convention:** `S` = single, `M` = multi, `p` = producer, `c` = consumer. `BlockingXxxRingBuffer` wraps the corresponding lock-free `XxxRingBuffer` and adds blocking `offer`/`take`.

---

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "@VERSION@"
```

---

## Quick start

### Non-blocking (SPSC)

```scala
import zio.blocks.ringbuffer.SpscRingBuffer

val buf = SpscRingBuffer[String](1024) // capacity must be a power of 2

// Producer thread
buf.offer("hello") // true if inserted, false if full

// Consumer thread
val msg: String = buf.take() // element or null if empty
```

### Blocking (MPMC)

```scala
import zio.blocks.ringbuffer.BlockingMpmcRingBuffer

val buf = BlockingMpmcRingBuffer[String](1024)

// Any producer thread — blocks until space is available
buf.offer("hello")

// Any consumer thread — blocks until an element is available
val msg: String = buf.take()
```

---

## Non-blocking API

Every non-blocking ring buffer provides:

```scala
def offer(a: A): Boolean   // insert; returns false if full
def take(): A               // remove; returns null if empty
def size: Int               // approximate element count
def isEmpty: Boolean        // approximate emptiness check
def isFull: Boolean         // approximate fullness check
```

`SpscRingBuffer` additionally provides batch operations:

```scala
def drain(consumer: A => Unit, limit: Int): Int  // drain up to limit elements
def fill(supplier: () => A, limit: Int): Int      // fill up to limit slots
```

All `size`/`isEmpty`/`isFull` values are **approximate** under concurrency—they are snapshots that may be stale by the time the caller acts on them.

---

## Blocking API

Each `BlockingXxxRingBuffer` wraps the corresponding lock-free variant and adds:

```scala
def offer(a: A): Unit        // blocks until space is available; throws InterruptedException
def take(): A                 // blocks until an element is available; throws InterruptedException
def tryOffer(a: A): Boolean   // non-blocking insert; returns false if full
def tryTake(): A              // non-blocking remove; returns null if empty
```

The non-blocking `tryOffer`/`tryTake` methods are available on blocking variants for try-once semantics.

**Fast-path optimization:** `offer` attempts `inner.offer` before engaging any blocking machinery. If it succeeds, no lock is acquired. Similarly, `take` attempts `inner.take` first. The blocking slow path is only entered when the buffer is actually full (for `offer`) or empty (for `take`).

---

## Choosing a variant

Use the most constrained variant that fits your threading model:

| Scenario | Recommended |
|----------|-------------|
| Dedicated pipeline: one writer thread, one reader thread | `SpscRingBuffer` / `BlockingSpscRingBuffer` |
| Fan-in: many writers, one reader (e.g., logging, event aggregation) | `MpscRingBuffer` / `BlockingMpscRingBuffer` |
| Fan-out: one writer, many readers (e.g., work distribution) | `SpmcRingBuffer` / `BlockingSpmcRingBuffer` |
| General purpose: any number of writers and readers | `MpmcRingBuffer` / `BlockingMpmcRingBuffer` |

Use the **non-blocking** variant when you can spin, batch, or back-pressure at a higher level. Use the **blocking** variant when threads should sleep until work is available.

---

## Usage examples

### SPSC with drain/fill

```scala
import zio.blocks.ringbuffer.SpscRingBuffer

val buf = SpscRingBuffer[java.lang.Integer](64)

// Producer: batch-fill from a data source
var seq = 0
val filled = buf.fill(() => { seq += 1; Integer.valueOf(seq) }, 32)
println(s"Filled $filled elements")

// Consumer: batch-drain into a processor
val drained = buf.drain(e => println(s"Processing $e"), 32)
println(s"Drained $drained elements")
```

### MPSC fan-in (multiple producers, single consumer)

```scala
import zio.blocks.ringbuffer.BlockingMpscRingBuffer

val buf = BlockingMpscRingBuffer[String](256)

// Multiple producer threads
for (i <- 0 until 4) {
  new Thread(() => {
    for (j <- 0 until 100)
      buf.offer(s"producer-$i: message-$j")
  }).start()
}

// Single consumer thread
new Thread(() => {
  for (_ <- 0 until 400)
    println(buf.take())
}).start()
```

### SPMC fan-out (single producer, multiple consumers)

```scala
import zio.blocks.ringbuffer.BlockingSpmcRingBuffer

val buf = BlockingSpmcRingBuffer[String](256)

// Single producer thread
new Thread(() => {
  for (i <- 0 until 400)
    buf.offer(s"task-$i")
}).start()

// Multiple consumer (worker) threads
for (w <- 0 until 4) {
  new Thread(() => {
    for (_ <- 0 until 100)
      println(s"worker-$w: ${buf.take()}")
  }).start()
}
```

### Non-blocking try-once with fallback

```scala
import zio.blocks.ringbuffer.MpmcRingBuffer

val buf = MpmcRingBuffer[String](64)

// Try to offer without blocking; handle backpressure yourself
if (!buf.offer("data")) {
  // buffer is full — drop, log, or retry later
  println("Buffer full, applying backpressure")
}

// Try to take without blocking
val result = buf.take()
if (result != null) {
  println(s"Got: $result")
} else {
  // buffer is empty — do other work
}
```

---

## Design notes

### FastFlow pattern (SPSC)

`SpscRingBuffer` uses the FastFlow algorithm: the **null/non-null state of an array slot** is the synchronization signal. The producer never reads `consumerIndex`; the consumer never reads `producerIndex`. This minimizes cross-core cache traffic to a single cache line per operation.

A **look-ahead step** (`capacity/4`, capped at 4096) lets the producer batch-check multiple future slots at once, further reducing the frequency of slow-path reads.

### Vyukov/Dmitry sequence buffer (MPMC)

`MpmcRingBuffer` uses a parallel `Long` sequence buffer alongside the data array. Each slot carries a stamp indicating whether it is available for writing or reading. Both `producerIndex` and `consumerIndex` are advanced via CAS, allowing any number of threads on both sides. The minimum capacity is 2 (the algorithm requires at least 2 slots to distinguish written from consumed).

### CAS-based producers (MPSC)

`MpscRingBuffer` follows the JCTools `MpscArrayQueue` design: producers claim a slot via CAS on `producerIndex`, then write the element with release semantics. A cached `producerLimit` avoids reading `consumerIndex` on every offer, reducing cross-core traffic. The consumer side uses relaxed poll semantics—a `null` slot means either empty or a producer mid-write.

### Index-based SPMC

`SpmcRingBuffer` uses index-based capacity checking on the producer side (no CAS needed for a single producer). Consumers use a CAS loop on `consumerIndex`. Consumers read the element *before* the CAS to avoid a race with the producer overwriting the slot. Consumers do not null array slots after reading—the producer overwrites them on the next lap.

### Cache-line padding

All ring buffers use **128-byte padding regions** (16 `Long` fields) between producer and consumer fields. This prevents false sharing on all architectures, including Apple Silicon which uses 128-byte cache lines (most x86 CPUs use 64-byte lines).

The padding is implemented via a class hierarchy:

```
Pad0 → ProducerFields → Pad1 → ConsumerFields → Pad2
```

### VarHandle for memory ordering

All JVM implementations use `java.lang.invoke.VarHandle` (Java 9+) for acquire/release semantics instead of `sun.misc.Unsafe`. This is the recommended modern approach for lock-free data structures on the JVM.

### Power-of-two masking

Capacity must be a power of two. This allows `index & (capacity - 1)` instead of `index % capacity`, which is significantly faster because bitwise AND compiles to a single CPU instruction.

---

## Blocking design

Each blocking variant wraps the corresponding lock-free ring buffer and adds a blocking layer optimized for the specific threading pattern:

**Single-waiter sides** (e.g., the single consumer in `BlockingMpscRingBuffer`, or both sides in `BlockingSpscRingBuffer`) use `volatile Thread` + `LockSupport.park/unpark` with a Dekker-like protocol:

1. Store `Thread.currentThread()` in a volatile field
2. Re-check the buffer (prevents lost wakeups)
3. Park if still full/empty
4. The opposite side reads the volatile field and calls `unpark` if non-null

This is zero-allocation, zero-CAS for the blocking path—just a volatile write and a park/unpark.

**Multi-waiter sides** (e.g., the multiple producers in `BlockingMpscRingBuffer`, or both sides in `BlockingMpmcRingBuffer`) use `ReentrantLock` + `Condition`:

- `notFull` condition for producers to await when the buffer is full
- `notEmpty` condition for consumers to await when the buffer is empty

**Loom-friendly:** All blocking variants use `LockSupport.park` (which correctly unmounts virtual threads) and `ReentrantLock` (no `synchronized` blocks). They work with both platform and virtual threads.

---

## Thread-safety contract

Violating the threading contract (e.g., calling `take` from multiple threads on an `SpscRingBuffer`) results in **undefined behavior**. No runtime check is performed—this is enforced by contract for maximum performance.

| Type | `offer` / `tryOffer` | `take` / `tryTake` |
|------|------------------|-----------------|
| `*Spsc*` | Single producer thread only | Single consumer thread only |
| `*Spmc*` | Single producer thread only | Any number of consumer threads |
| `*Mpsc*` | Any number of producer threads | Single consumer thread only |
| `*Mpmc*` | Any number of producer threads | Any number of consumer threads |

---

## Performance characteristics

| Operation | Non-blocking | Blocking (fast path) | Blocking (slow path) |
|-----------|-------------|---------------------|---------------------|
| `offer` (non-blocking) | Lock-free (SPSC/SPMC: wait-free) | N/A (`tryOffer`) | N/A |
| `take` (non-blocking) | Lock-free (SPSC/MPSC: wait-free) | N/A (`tryTake`) | N/A |
| `offer` (blocking) | N/A | Lock-free fast path | Park/unpark or lock + await |
| `take` (blocking) | N/A | Lock-free fast path | Park/unpark or lock + await |

**SPSC** is the fastest: no CAS, no locks, minimal cache-line traffic. Use it whenever your threading model allows a dedicated producer-consumer pair.

---

## Cross-platform support

On **Scala.js**, all ring buffer types compile and provide the same API surface. Since Scala.js is single-threaded, the JS implementations use plain reads and writes with no memory ordering primitives or locks. The blocking variants throw `UnsupportedOperationException` for `offer`/`take` (blocking is not meaningful in a single-threaded runtime). Use `tryOffer`/`tryTake` on Scala.js.

| Platform | Lock-free variants | Blocking `tryOffer`/`tryTake` | Blocking `offer`/`take` |
|----------|-------------------|------------------------|----------------------|
| JVM | Full concurrency support | Full concurrency support (`tryOffer`/`tryTake`) | Full concurrency support |
| Scala.js | Sequential (same API) | Sequential (same API, `tryOffer`/`tryTake`) | `UnsupportedOperationException` |
