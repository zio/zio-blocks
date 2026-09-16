---
id: concurrent-operators
title: "Concurrent Operators"
sidebar_label: "Concurrent Operators"
description: "How mapPar, mapParAsync, flatMapPar, and mergeAll bound concurrency: selector slots, arrival order, and the n = 1 guarantee."
keywords:
  - "Bounded Concurrency"
  - "Concurrent Stream Operators"
  - "Unordered Arrival"
  - "Selector Slots"
  - "Stream"
---

Three of the four operators on this page are not new. `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` arrived in PR #1451, "concurrent stream operators + primitive SPSC ring buffers", as synchronous operators backed by worker threads. What the asynchronous execution work added is narrower and worth separating out: an asynchronous execution path for those three, one genuinely new operator in `Stream#mapParAsync`, and a documented `n = 1` degradation guarantee. A changelog entry that reads "adds `mapPar`" is describing the wrong release.

The distinction matters for more than provenance. The two execution paths are different engines with different bounds, and which one a program gets is decided by the kind of reader its pipeline compiles to — not by which operator it called.

## The Operators

| Operator                            | Element callback                               | What `n` bounds                | Arrived in |
|-------------------------------------|------------------------------------------------|--------------------------------|------------|
| `Stream#mapPar(n)(f)`               | `A => B`                                       | concurrent applications of `f` | PR #1451   |
| `Stream#mapParAsync(n)(f)`          | `A => Async[B]`                                | callbacks in flight            | PR #1672   |
| `Stream#flatMapPar(n)(f)`           | `A => Stream[E1, B]`                           | open inner streams             | PR #1451   |
| `Stream.mergeAll(maxOpen)(streams)` | none; `streams` is a `Stream[E, Stream[E, A]]` | open inner streams             | PR #1451   |

Each of the four begins with `require` on its parallelism argument, so passing zero or a negative number raises `IllegalArgumentException` at description time rather than producing an empty or sequential stream.

### `Stream#mapPar`

```scala
def mapPar[B](n: Int)(f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Applies `f` to each element with up to `n` applications active. Output is unordered: elements leave in the order their applications finish, not the order they entered. `f` is synchronous, so this is the operator for CPU-bound or blocking work on the JVM — and, as [Threading and Platform Behaviour](#threading-and-platform-behaviour) explains, the operator that overlaps nothing at all once the pipeline is on the asynchronous lane.

### `Stream#mapParAsync`

```scala
def mapParAsync[B](n: Int)(f: A => Async[B])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Keeps at most `n` `Async` callbacks in flight and emits each result in completion order. This is the one genuinely new operator, and it is the only member of the family whose callback can suspend: `f` returns a description, so the engine holds `n` unfinished effects rather than `n` busy threads. A failure inside the callback is a defect, not a typed error, and it fails the outer terminal effect.

Unlike `mapPar`, this operator has no synchronous materialization at all. It compiles to the shared asynchronous concurrent reader on both platforms, which is why its `n` counts suspended callbacks rather than workers.

### `Stream#flatMapPar`

```scala
def flatMapPar[E1 >: E, B](n: Int)(f: A => Stream[E1, B])(implicit jtB: JvmType.Infer[B]): Stream[E1, B]
```

Applies `f` to each element to produce an inner stream, then merges up to `n` inner streams concurrently. It has no engine of its own. Past the `n = 1` branch, its body is a single delegation:

```scala
Stream.mergeAll[E1, B](n)(
  this.asInstanceOf[Stream[E1, A]].map(f)(JvmType.Infer.boxed[Stream[E1, B]])
)(jtB)
```

One fan-in engine, not two. Everything below about slots, admission, ordering, and shutdown is stated for `mergeAll`, and `flatMapPar` inherits all of it unchanged.

### `Stream.mergeAll`

```scala
def mergeAll[E, A](maxOpen: Int)(streams: Stream[E, Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Merges up to `maxOpen` inner streams concurrently into one output stream, with elements arriving in completion order. Note the argument: `streams` is a *stream of streams*, not a varargs list, so a fixed collection of sources is fed in through a constructor such as `Stream.fromIterable`.

```scala mdoc:compile-only
import zio.blocks.streams._

val sources: Stream[Nothing, Stream[Nothing, Int]] =
  Stream.fromIterable((0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100)))

val merged: Stream[Nothing, Int] = Stream.mergeAll(4)(sources)
```

## Semantics

### What `n` Means

`n` is a count of selector slots, not of threads. The concurrent reader allocates one `AsyncSelector` with `n + 1` entries: `n` entries for inner work, and one final entry reserved for the outer source.

```
┌────────────────────────────────────────────────────────────────────┐
│ Async.selectorWithCapacity(n + 1)      one selector, n + 1 entries │
├────────────────────────────────────────────────────────────────────┤
│ entry 0     inner work: callback in flight, or an open inner       │
│ entry 1     inner work: callback in flight, or an open inner       │
│   ...       up to n of these; `active` counts the occupied ones    │
│ entry n-1   inner work: callback in flight, or an open inner       │
├────────────────────────────────────────────────────────────────────┤
│ entry n     the outer source        re-armed only while active < n │
└────────────────────────────────────────────────────────────────────┘
```

The extra entry is what keeps the operator from pulling ahead. The source entry is re-armed only while `active < n`, so the reader stops asking upstream for elements the moment every inner slot is taken, and resumes the instant one frees. Nothing queues behind a full set of slots.

Whether a slot corresponds to a thread depends entirely on which engine materialized. On the JVM's synchronous lane each slot does have a worker thread behind it; on the asynchronous lane a slot is one pending `Async` and there are no threads involved.

### Ordering

All four operators are unordered with respect to input position. An element leaves when its work finishes, so output is in arrival order. The property tests compare results with `.toSet` for exactly this reason: there is no input-order assertion available to make.

The single exception is `n = 1`, which is exactly sequential — and the `n = 1` tests do assert exact [`Chunk`](../chunk.md) equality, because at that value the operator is not the concurrent engine at all. See [The `n = 1` Guarantee](#the-n-1-guarantee).

:::warning[Unordered means unordered on Scala.js too]
Single-threaded execution does not restore input order. The concurrent engine runs on Scala.js with immediately-ready effects, and its arrival-order semantics are retained there. Code that depends on Scala.js emitting source order is a bug that will not reproduce on the JVM.
:::

If input order is what you need, use sequential `map`, `mapAsync`, or `flatMap`. Sorting afterwards — `.runCollectAsync.map(_.map(_.sorted))` — recovers *a* total order, but not the input one unless the elements happen to sort that way.

### Boundedness

Two separate things are bounded, and conflating them leads to the wrong buffer size.

The first is the number of open inner streams. Admission is guarded by an active count: `occupied` is a `Boolean` array of length `n` and `active` is the number of `true` entries, and a new element is accepted only into a free index. A full set of slots stops admission at the source rather than accumulating work anywhere.

The second is the number of buffered elements, and it exists only on the JVM's synchronous lane. `ConcurrentMapParReader` allocates `n` input and `n` output `SpscRingBuffer`s, each of `bufferSize` capacity, so a `mapPar(8)` with the default buffer holds at most 1024 elements in transit. The concurrent merge readers allocate one output ring per slot on the same basis.

The asynchronous engine has no rings of its own. `AsyncConcurrentReaders.mapPar` does not even take a buffer size: each slot holds exactly one unfinished effect, so in-flight work is bounded at `n` and nothing more. `AsyncConcurrentReaders.merge` does take one, but spends it on compiling each inner stream (`stream.compile(0, bufferSize)`), where it sizes whatever buffered stages that inner stream contains.

### Admission and Replenishment

A slot is occupied before the work in it begins, and — for merge — freed only after that work has fully closed.

```
  free
    │  outer element arrives: occupied(i) = true, active += 1
    ▼
  constructing      the Async that builds the child runs HERE, in the slot
    │  child installed
    ▼
  draining          elements reach the consumer in arrival order
    │  MapWorkerEnd: slot entry replaced by the child's close effect
    ▼
  closing           slot still held; no successor may be admitted yet
    │  MapWorkerClosed: releaseSlot(i), then armSource()
    ▼
  free
```

That two-phase ending is the part worth remembering. When an inner stream reaches its end the engine does not free the slot; it replaces the slot's selector entry with the inner reader's own close effect, which resolves to a second signal. Only then does `releaseSlot` clear `occupied(i)`, decrement `active`, and re-arm the source. A slot is therefore never handed to a successor before its predecessor's finalizers have run to completion.

`mapPar` and `mapParAsync` have the shorter version of this, because a callback has no reader to close: the slot is released in the same step that hands the value to the consumer, and the source is re-armed there.

The "constructing" phase is where the slot accounting surprises people. With `flatMapPar(n)(a => Stream.unwrap(f(a)))`, the effect `f(a)` that *produces* the child runs inside the slot the child will later occupy — they share one of the `n`, they do not get one each. [Async children and slot accounting](#async-children-and-slot-accounting) below demonstrates this with a running program.

### The `n = 1` Guarantee {#the-n-1-guarantee}

At `n = 1` each operator degrades to its sequential twin. This is a guarantee about the code path, not an optimization note: the branch sits in the public operator body, above every allocation.

```scala
def mapPar[B](n: Int)(f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B] = {
  require(n >= 1, s"mapPar requires n >= 1, got $n")
  if (n == 1) map(f)
  else {
    jtB.jvmType
    new Stream.MapPar[E, A, B](this, n, f, elementRepresentation, jtB.jvmType)
  }
}
```

The other three are shaped identically: `mapParAsync(1)` returns `mapAsync(f)`, `flatMapPar(1)` returns `flatMap(f)`, and `mergeAll(1)` returns `streams.flatMap(identity)` — or, when the outer stream is already a `Mapped` node, the fused `mapped.self.flatMap(mapped.f)` that skips the intermediate stream entirely. No reader, no selector, no thread, and no ring is allocated in any of those cases, because the decision is made before the concurrent node is ever constructed.

The practical consequence is that `n` can be a configuration value that is allowed to be `1`. A deployment that dials concurrency down to one gets the sequential operator, with its exact input ordering, rather than a concurrent engine running at width one.

## Buffer Sizing

Concurrent readers on the synchronous lane use ring-buffer queues sized by the enclosing buffer-size region. The default is 64 (the library-internal `Stream.DefaultBufferSize`, which is not part of the public API).

```scala mdoc:compile-only
import zio.blocks.streams._

def heavyComputation(n: Int): Int = n * n

val sized: Stream[Nothing, Int] =
  Stream.bufferSize(256) {
    Stream.range(0, 1000000).mapPar(8)(heavyComputation)
  }
```

`Stream.bufferSize(n)` requires a positive power of two and rejects anything else with `IllegalArgumentException`:

```scala
require(n >= 1 && (n & (n - 1)) == 0, s"bufferSize must be a positive power of 2, got $n")
```

Nested regions use the innermost size. Larger buffers absorb bursty producers; smaller ones cut memory when many slots are open at once. The default suits most workloads. On the asynchronous lane its reach is narrower: `mapPar` and `mapParAsync` ignore it entirely, because each slot holds exactly one unfinished effect and the bound is the slot count, while `mergeAll` and `flatMapPar` still pass it down, where it sizes the buffered stages inside each compiled inner stream.

`Pipeline.buffer(n)` is a different tool for a different job: it inserts a bounded buffer between two stages rather than resizing the queues inside one concurrent reader, and it participates in the asynchronous reader graph on both platforms.

## Error Behaviour

First failure wins. The concurrent reader commits a terminal state exactly once, and the first typed source or inner-stream error to reach that commit becomes the result; the fold short-circuits and the operation surfaces as `Left(e)`.

```scala mdoc:compile-only
import zio.blocks.streams._

// A typed error anywhere upstream terminates every worker.
val fromUpstream: Stream[String, Int] =
  Stream
    .range(0, 1000)
    .flatMap(n => if (n == 500) Stream.fail("bad element") else Stream.succeed(n))
    .mapPar(4)(identity)

// A typed error in one inner stream terminates the merge.
val fromInner: Stream[String, Int] =
  Stream.mergeAll(4)(
    Stream.fromIterable(
      List(Stream.range(0, 100), Stream.fail("inner error"), Stream.range(200, 300))
    )
  )
```

Both of those collect to a `Left`. Elements that were already emitted stay emitted — ordering is arrival-based, so a consumer may well have seen output from other slots before the failing one reached the commit point.

Once a failure is committed, cleanup runs and every sibling is torn down. A failure *during* that cleanup is attached to the primary failure rather than substituted for it: the engine calls `StreamError.attachCleanupReplay(primary, secondary)`, so the error a caller sees is still the one that caused the termination, with the cleanup problem carried alongside it. The same rule holds on the successful path, where a cleanup failure with no primary becomes the failure via `StreamError.attachCleanup(null, cleanupFailure)`.

:::note[What the tests actually prove]
`MergeInnerErrorSpec` asserts `result.isLeft` across the generic, `Int`, `Long`, `Float`, and `Double` lanes under a ten-second timeout, and again with four coordinated simultaneous failures per lane under a thirty-second one. That establishes the general shape — a failing inner terminates the merge promptly and does not hang — but it does not pin down *which* failure wins when several race at `n > 1`. Do not write code that depends on a particular one of several concurrent errors being the one reported.
:::

A defect is different from a typed error. The `mapParAsync` callback failing, or an `ensuring` finalizer throwing, is a defect and fails the outer terminal effect rather than appearing in the `E` channel.

## Cancellation and Shutdown

Shutdown is cooperative and ordered, and it is driven from the consumer end. Closing, failing, or cancelling the reader runs the same owned-cleanup path.

For `mergeAll` and `flatMapPar` that path is three steps, in order: shut the selector down, close every installed inner reader, then close the outer source. The steps are joined rather than sequenced-and-abandoned, so a failure in one does not skip the others — each later close still runs, and its failure is attached to the first. For `mapPar` and `mapParAsync` there are no inner readers, so it is the selector shutdown followed by the upstream close.

Cancellation goes through the `Async` cancellation protocol rather than thread interruption: the in-flight child run is cancelled with cleanup, and the reader settles its completion from the cancellation's outcome. A cancellation that arrives after the reader is already closed is recognised as stale and does not turn a clean close into a failure.

Closing is idempotent and it waits. The reader will not report closed until the cleanup it owns has finished, which is the property the slot lifecycle above depends on — a slot's successor cannot start while the predecessor's finalizers are still running.

[Cancellation](./async-execution.md#cancellation) covers the protocol these readers participate in, and [Async.Running#cancel](../async.md#runningcancel) documents the primitive underneath it.

## Threading and Platform Behaviour

Which engine a concurrent operator materializes depends on the kind of reader its upstream compiled to, and the two engines differ far more than the two platforms do.

| Materialization                 | JVM reader                              | Scala.js reader                 | What actually overlaps                     |
|---------------------------------|-----------------------------------------|---------------------------------|--------------------------------------------|
| `mapPar`, synchronous upstream  | `ConcurrentMapParReader` family         | `Reader.MappedInt` and siblings | one worker thread per slot                 |
| `mapPar`, asynchronous upstream | `AsyncConcurrentReaders.mapPar`         | `AsyncConcurrentReaders.mapPar` | nothing; `f` is wrapped in `Async.succeed` |
| `mapParAsync`, always           | `AsyncConcurrentReaders.mapPar`         | `AsyncConcurrentReaders.mapPar` | whatever the `Async` callbacks suspend on  |
| `mergeAll` from a `Reader`      | `AsyncConcurrentReaders.merge`          | `AsyncConcurrentReaders.merge`  | whatever the inner streams suspend on      |
| `mergeAll` via the interpreter  | `IntConcurrentMergeReader` and siblings | `Reader.FlatMappedRef`          | one drainer thread per slot on the JVM     |

Read the second row before choosing an operator. Once the upstream compiles to an `AsyncReader`, `Platform.createMapParReaderFromReader` routes `mapPar` to the shared asynchronous engine with the mapping function wrapped as `Async.succeed(f(a))` — an already-complete effect. The slots and the selector are all still there, but there is nothing for them to overlap, on either platform. `mapParAsync` exists precisely because a callback that returns a real `Async` is the only way to get concurrency out of that lane.

### The JVM

Workers on the synchronous lane run on virtual threads where the runtime provides them. `Platform.startVirtualThread` obtains `Thread.ofVirtual()` reflectively, so the module builds and runs on any supported JDK and uses virtual threads on JDK 21 and later; if the reflective lookup fails for any reason it starts a named daemon platform thread instead.

The threads are named, which makes them identifiable in a thread dump. `mapPar` names its workers `zio-blocks-mappar-worker-<n>-<index>` and its dispatcher `zio-blocks-mappar-coordinator-<n>`, where `<n>` counts reader instances and `<index>` identifies the worker within one reader; merge uses `zio-blocks-merge-drainer-<n>-<index>` and `zio-blocks-merge-coordinator-<n>` on the same scheme. Those strings are prefixes rather than final names: the virtual-thread builder is created with `Thread.ofVirtual().name(prefix, 0L)`, whose two-argument form appends a counter, so a virtual worker appears in a dump as `zio-blocks-mappar-worker-<n>-<index>0`. Only the platform-thread fallback, which calls `setName` directly, uses the name verbatim.

### Scala.js

There is no parallelism on Scala.js either way — `Platform.supportsConcurrency` is `false` and `Platform.startVirtualThread` throws `UnsupportedOperationException`. But "no parallelism" resolves into two different mechanisms, and only one of them is sequential in the sense the scaladoc suggests.

When the pipeline compiles end to end to a `SyncReader`, the operator really is sequential: `Platform.createMapParReaderFromReader` builds an ordinary mapped reader (`Reader.MappedIntInt`, `Reader.MappedInt`, `Reader.MappedLong`, and the rest), and the synchronous merge path builds a `Reader.FlatMappedRef` — one that throws `UnsupportedOperationException` if an inner stream turns out to be asynchronous.

When the pipeline is on the asynchronous lane, the concurrent engine runs, with immediately-ready effects standing in for suspension. The effect is still sequential, but the *semantics* are the concurrent engine's: **unordered arrival is retained**.

:::warning[The scaladoc is a throughput claim, not an ordering claim]
The scaladoc on `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` says that on Scala.js each "degrades to sequential `map`" or "sequential `flatMap`". That describes the synchronous-lane case as though it were the whole story. Never read it as a promise about element order.
:::

[Platform Differences](./platform-differences.md#concurrency-and-threading) states the same split from the platform side, including the full capability surface of `Platform`.

## Laws

`ConcurrentLawsSpec` asserts three equalities, all of them as set equality (`.toSet == .toSet`), since neither side of any of them is ordered:

- `Stream.mergeAll(1)(streams)` equals `streams.flatMap(identity)`
- `stream.mapPar(1)(f)` equals `stream.map(f)`
- `stream.flatMapPar(n)(f)` equals `Stream.mergeAll(n)(stream.map(f))`

The first two additionally hold as exact equality, and not because the engine happens to preserve order — at `n = 1` the operator body *returns the sequential operator itself*, so the two sides are the same description. The third holds only as a set equality: both sides run the concurrent engine at width `n`, so both are in arrival order and neither has an input order to compare against.

The third law is also a statement about the implementation rather than a coincidence, since `flatMapPar` is defined as that right-hand side. Reading it as "there is one fan-in engine" is more useful than reading it as a property that had to be checked.

## Examples

### `mapPar`, `mergeAll`, and `flatMapPar` on the JVM

The three snippets below use blocking terminals, which exist only on the JVM. In cross-platform code, swap the terminal for its `*Async` twin — `runCollectAsync`, `runFoldAsync` — and the operator itself is unchanged. [Migration](./migration.md#blocking-terminals-are-now-jvm-only) has the full replacement mapping.

Expensive per-element work across eight slots:

```scala mdoc:compile-only
import zio.blocks.streams._

val doubled = Stream
  .range(0, 1000)
  .mapPar(8) { n =>
    Thread.sleep(1)
    n * 2
  }
  .runCollect
```

`doubled` is a `Right` holding all thousand elements, in arrival order rather than `0, 2, 4, …`.

Ten sources drained four at a time:

```scala mdoc:compile-only
import zio.blocks.streams._

val sources = Stream.fromIterable((0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100)))
val summed  = Stream.mergeAll(4)(sources).runFold(0L)(_ + _)
```

A sum is order-insensitive, which is what makes it a safe thing to compute over an unordered merge: `summed` is `Right(499500)` on every run.

One sub-stream per element, eight drained at a time:

```scala mdoc:compile-only
import zio.blocks.streams._

val flattened = Stream
  .range(0, 50)
  .flatMapPar(8)(i => Stream.range(i * 20, (i + 1) * 20))
  .runFold(0L)(_ + _)
```

Again `Right(499500)`: the same 1000 integers, reached through 50 inner streams instead of 10.

### A Worked `mapParAsync`

The two runnable files below live in the `streams-examples` module. This one makes arrival order visible rather than asserting it: every callback hands back an unresolved `Completer`, and a driver thread then settles the four of them in reverse. The collected chunk comes back reversed with respect to the input.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/MapParAsyncExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.MapParAsyncExample"
```

It prints the two orders side by side:

```
input order:   Chunk(10,20,30,40)
arrival order: Right(Chunk(40,30,20,10))
```

### Async Children and Slot Accounting

`flatMapPar(n)(a => Stream.unwrap(f(a)))` is the idiom for asynchronously produced children, and this example pins down what a slot covers. A gauge is incremented when child construction starts and decremented by the child's finalizer, so it counts exactly the elements occupying a slot; a `CyclicBarrier(2)` forces each construction to wait for a partner, so the program can only finish if two really are in flight at once.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/FlatMapParAsyncChildrenExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.FlatMapParAsyncChildrenExample"
```

```
elements: Right(List(10, 11, 20, 21, 30, 31, 40, 41))
peak slot occupancy: 2 of 2
```

Peak occupancy is 2 and not 4, even though all four outer elements are available immediately, because the construction effect holds the slot its child will use. For sequential asynchronous children, `flatMap(a => Stream.unwrap(f(a)))` is the operator you want instead.

## Comparison with Other Libraries

The concurrent surface is small, and what distinguishes it is less the operator names than what a caller has to bring along to use them.

| Feature              | ZIO Blocks Streams                 | fs2                | Kyo               | Ox                    | Pekko                      |
|----------------------|------------------------------------|--------------------|-------------------|-----------------------|----------------------------|
| Concurrent operators | `mapPar`, `mergeAll`, `flatMapPar` | `parEvalMap`       | `mapParUnordered` | `mapPar`              | `mapAsync`, `flatMapMerge` |
| Effect system        | none required                      | cats-effect        | Kyo               | none; virtual threads | Akka                       |
| Typed errors         | `Either[E, Z]`                     | `ApplicativeError` | Kyo effects       | exceptions            | none                       |

The table compares contracts, not speed. Cross-provider throughput rankings require the specific benchmark classes that were built to compare like with like, and none of the numbers from those runs belong in a row next to a feature name.

## See Also

- [Asynchronous Stream Execution](./async-execution.md) — the `*Async` surface these operators sit inside, and how a description picks its lane
- [Platform Differences](./platform-differences.md#concurrency-and-threading) — the platform capability split, `Platform`, and the Scala.js threading story
- [Migration](./migration.md#at-a-glance) — moving a blocking codebase onto the cross-platform terminals
- [Reader](./reader.md) — the `SyncReader` / `AsyncReader` union that decides which engine materializes
- [Asynchronous I/O](./async-io.md) — asynchronous sources worth merging concurrently
- [Async](../async.md#asyncselector) — `AsyncSelector`, the primitive the slot machinery is built from
