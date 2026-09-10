---
id: concurrent-operators
title: "Concurrent Operators"
---

ZIO Blocks Streams has two concurrency families. `mapPar`, `mergeAll`, and `flatMapPar` select their reader implementation when the stream is materialized: asynchronous terminals use the cross-platform `AsyncConcurrentReaders`, while JVM plain terminals use blocking concurrent readers backed by virtual threads (or daemon platform threads on older JDKs). `mapParAsync` accepts native `Async` callbacks; use `Stream.unwrap` when `flatMapPar` children are produced asynchronously.

| Operator | Purpose |
|---|---|
| `Stream#mapPar(n)(f)` | Apply `f` to elements with up to `n` operations active. Output is **unordered** (arrival order, not input order). |
| `Stream.mergeAll(n)(streams)` | Drain up to `n` inner streams concurrently; interleave their elements as they become available. |
| `Stream#flatMapPar(n)(f)` | Per element, produce a sub-stream via `f`; drain up to `n` sub-streams concurrently. |
| `Stream#mapParAsync(n)(f)` | Keep at most `n` `Async` callbacks in flight and emit results in completion order. |

With an asynchronous terminal such as `runCollectAsync`, both platforms use native asynchronous concurrent readers for these operators, even when the upstream and callback are synchronous. `mergeAll` and `flatMapPar` may then consume a dynamic mixture of synchronous and asynchronous inner streams; synchronous readers are adapted to async readers as each child is opened. On Scala.js this provides interleaved asynchronous progress and bounded in-flight work on the event loop, **not CPU parallelism**. It is therefore inaccurate to describe `mergeAll` as universally sequential on Scala.js: only its internal synchronous-reader fallback is sequential, and public Scala.js consumption uses asynchronous terminals.

When `n` is one, `mapPar`, `mapParAsync`, `mergeAll`, and `flatMapPar`
degrade to their corresponding sequential operator and avoid allocating
concurrent coordination machinery.

Use `mapParAsync` when an element callback returns `Async`. For asynchronous
child construction, use `flatMapPar(n)(a => Stream.unwrap(f(a)))`; the callback
and installed child share one of the `n` slots. For sequential asynchronous
children, use `flatMap(a => Stream.unwrap(f(a)))`.

## Semantics

**Output order.** Concurrent output is **unordered** with respect to input position. Elements arrive as operations complete or children produce them, not in input order. If you need input order, use sequential `map` / `flatMap`.

**Error propagation.** The first observed typed source/child error terminates the operation and surfaces as `Left(e)`. A failed `Async` callback is a defect and fails the outer terminal effect. Remaining workers/callbacks are cancelled; already completed output may have been emitted because ordering is completion-based.

**Resource safety.** All worker threads, asynchronous children, selectors, and queues are cleaned up when the consumer closes, fails, or cancels. Closing is idempotent and waits for cleanup; a cleanup failure is attached to a primary failure rather than replacing it.

**Primitive specialization.** JVM blocking concurrent readers preserve primitive specialization — `Int`, `Long`, `Float`, and `Double` streams use specialized lock-free queues internally, avoiding boxing in the handoff between worker threads. Async readers also carry the output `JvmType` through materialization.

## Buffer sizing

Concurrent operators use internal ring-buffer queues (default size **64**). Override with `Stream.bufferSize(n) { ... }` where `n` is a positive power of two:

```
Stream.bufferSize(256) {
  Stream.range(0, 1_000_000).mapPar(8)(heavyComputation)
}.runCollect
```

Larger buffers help when producers are bursty; smaller buffers reduce memory when many concurrent streams are active. The default is fine for most workloads.

`Pipeline.buffer(n)` inserts a bounded buffer between upstream and downstream. It participates in the native asynchronous reader graph on both platforms.

## Examples

The three plain-terminal examples below are **JVM-only** because Scala.js exposes asynchronous terminals rather than blocking ones. In shared code, replace the terminal with (for example) `runCollectAsync` or `runFoldAsync`; the plain concurrent operators still use the cross-platform async implementation. Use `mapParAsync` for an `Async` element callback and `Stream.unwrap` for an asynchronously produced child stream.

### `mapPar`

```
// Apply an expensive function using 8 virtual threads.
// Output order varies between runs.
val result = Stream.range(0, 1000)
  .mapPar(8)(n => { Thread.sleep(1); n * 2 })
  .runCollect
// result: Right(Chunk(...)) -- all 1000 elements, but not in 0,2,4,... order
```

### `mergeAll`

```
// Drain 10 streams concurrently, up to 4 at a time.
val streams = Stream.fromIterable(
  (0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100))
)
val merged = Stream.mergeAll(4)(streams).runFold(0L)(_ + _)
// merged: Right(499500) -- all elements consumed, order interleaved
```

### `flatMapPar`

```
// Each element spawns a sub-stream; up to 8 drained concurrently.
val flat = Stream.range(0, 50)
  .flatMapPar(8)(i => Stream.range(i * 20, (i + 1) * 20))
  .runFold(0L)(_ + _)
// flat: Right(499500)
```

### Error behaviour

```
// Typed error in a worker terminates all workers
val err1 = Stream.range(0, 1000)
  .flatMap(n => if (n == 500) Stream.fail("bad element") else Stream.succeed(n))
  .mapPar(4)(identity)
  .runCollect
// err1: Left("bad element")

// Error in one inner stream terminates mergeAll
val err2 = Stream.mergeAll(4)(Stream.fromIterable(
  List(Stream.range(0, 100), Stream.fail("inner error"), Stream.range(200, 300))
)).runCollect
// err2: Left("inner error")
```

## Guidelines

- **Use `mapPar(n)(f)` for expensive per-element work.** JVM blocking terminals can parallelize CPU-bound computation and blocking I/O. With async terminals, use it to bound and interleave work, remembering that Scala.js still executes synchronous callbacks on one event-loop thread. Do not use it for trivially cheap functions (e.g. `_ + 1`); coordination overhead exceeds the benefit.
- **Use `mergeAll(n)(streams)` for concurrent fan-in** — draining multiple independent sources (files, connections, partitions) simultaneously. Use `flatMapPar(n)(f)` when each input element produces a sub-stream to drain concurrently.
- **Concurrent output is unordered.** If you need sorted results, apply `.runCollect.map(_.sorted)` or accumulate into a structure that handles ordering. If you need input-order preservation, use sequential `map` / `flatMap`.
- **Choose the terminal for the platform.** JVM plain terminals materialize blocking concurrent readers. Async terminals materialize native `AsyncConcurrentReaders` on both JVM and Scala.js; on JS they overlap asynchronous progress on one event-loop thread rather than parallelizing CPU work.
- **Mixed inner kinds are supported by async fan-in.** `mergeAll` / `flatMapPar` can open synchronous and asynchronous inner streams dynamically when consumed by an async terminal. The Scala.js-only synchronous fallback cannot drive an asynchronous inner.

## Comparison with other libraries

| Feature | ZB Streams | fs2 | Kyo | Ox | Pekko |
|---|---|---|---|---|---|
| Concurrent operators | `mapPar`, `mergeAll`, `flatMapPar` | `parEvalMap` | `mapParUnordered`* | `mapPar` | `mapAsync`, `flatMapMerge` |
| Effect system required | No | Yes (cats-effect) | Yes (Kyo) | No (virtual threads) | Yes (Akka) |
| Typed errors | `Either[E, Z]` | ApplicativeError | Kyo effects | Exceptions | No |

\* Kyo's `mapParUnordered` forks a fiber per element (very slow for large streams). Kyo's `collectAll` merges streams but does not parallelize pure computation within them.
