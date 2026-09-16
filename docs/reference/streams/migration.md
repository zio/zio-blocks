---
id: migration
title: "Migration Guide"
sidebar_label: "Migration"
description: "Every source-breaking change in the async execution release, the compile error it produces, and the code that replaces it."
keywords:
  - "Stream Migration"
  - "Breaking Changes"
  - "Blocking Terminals"
  - "Reader Split"
  - "Primitive Specialization"
---

The release that added asynchronous stream execution also moved, split, and narrowed a good deal of the existing API. Nothing was deprecated first, because none of these changes are deprecations: members were relocated to a platform, a type was split into two, and several signatures moved their evidence from the input side to the output side. The result is that code written against the previous release will, in places, stop compiling rather than start warning.

Two audiences hit different parts of this page. Anyone upgrading at all may meet the specialization signature changes. Anyone whose code cross-builds to Scala.js meets something larger first: sixteen blocking terminals left shared source for a JVM-only trait, so `run`, `runCollect`, `head`, `start` and their siblings no longer exist on Scala.js. That chapter is first because it is the one that breaks the most builds.

## At a Glance

Every source-visible break in the release, the message that reports it, and what to write instead. The rows are ordered by how many codebases each one reaches, not by how deep the change is:

| Breaking change                                                             | What the compiler (or runtime) tells you                                                          | Fix                                                                                 |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| 16 blocking terminals moved to a JVM-only trait                             | `value runCollect is not a member of zio.blocks.streams.Stream` — Scala.js only                   | Rename to the `*Async` twin; the result becomes `Async[Either[E, Z]]`               |
| `Stream#start` is JVM-only, and returns `Reader.SyncReader[A]` there        | `value start is not a member` on Scala.js; `type mismatch` on a `Reader[A]` ascription on the JVM | `Stream#useReaderAsync`, or `Stream#startAsync` if you want ownership               |
| `Sink.create` moved to the JVM companion and narrowed its parameter         | `value create is not a member of object zio.blocks.streams.Sink`                                  | `Sink.createAsync`, or `Sink.createBoth` to keep a native synchronous drain         |
| `Reader[A]` split into `Reader.SyncReader` and `Reader.AsyncReader`         | `value read is not a member of zio.blocks.streams.io.Reader[A]`                                   | Name the kind you mean in the signature; four members remain on the root            |
| `Reader.AsyncReader#toSync` is JVM-only                                     | `value toSync is not a member of zio.blocks.streams.io.Reader.AsyncReader[A]`                     | Drive the `AsyncReader` directly instead of converting it                           |
| `Async#block` compiles everywhere but only works on the JVM                 | No compile error; `IllegalStateException` when a suspension actually waits                        | Keep the `Async` and compose with `map` / `flatMap`                                 |
| The preserving `catchAll` / `catchDefect` overloads were deleted            | `type mismatch: found Stream[E, Any], required Stream[E, Int]`                                    | Make both branches agree on an element type, or widen the annotation                |
| Element-preserving operators stopped asking for input-side evidence         | `method filter does not take more parameters`                                                     | Delete the explicit `JvmType.Infer` argument                                        |
| `JvmType.Infer` is invariant, so evidence for a supertype no longer fits    | `no implicit argument of type JvmType.Infer[B] was found`                                         | Thread evidence for the *result* type, not the source element type                  |
| `Stream.succeed(a: Byte)` is genuinely `Stream[Nothing, Byte]`              | `type mismatch: found Stream[Nothing, Byte], required Stream[Nothing, Int]`                       | Add the widening you were relying on: `.map(_ & 0xff)`                              |
| `Sink#contramap` evidence describes `A0`, not the new input `A2`            | `no implicit argument of type JvmType.Infer[A0] was found`                                        | Drop explicit evidence and let it infer from the callback's result                  |
| `Reader.range` was removed from the companion                               | `value range is not a member of object zio.blocks.streams.io.Reader`                              | `Reader.fromRange`                                                                  |
| `internal.StreamError` is module-internal machinery, not an API             | Nothing — it still compiles, but its parent changed from `ControlThrowable` to `Exception`        | Throw the cause itself and build the stream with `Stream.attempt`                   |
| Long/Double bulk reads no longer reserve a data value for EOF               | Nothing — a restriction was lifted, not added                                                     | Stop reserving a sentinel; read the returned count from `readLongs` / `readDoubles` |
| Binary compatibility was broken deliberately; streams does not enforce MiMa | `NoSuchMethodError` at run time against a stale artifact                                          | Recompile every downstream module; do not drop the jar in                           |

Pre-migration code appears throughout this page in plain code fences. It is shown for recognition, not for copying: most of it no longer compiles against the current release.

## Blocking Terminals Are Now JVM-Only

The error arrives from the Scala.js half of a cross-build, on a line that the JVM half compiles without complaint:

```
value runCollect is not a member of zio.blocks.streams.Stream[Nothing, Int]
```

Nothing is wrong with the call. The member is simply absent on that platform. Sixteen `Stream` members — `count`, `exists`, `find`, `forall`, `foreach`, `head`, `last`, `run`, `runCollect`, `runDrain`, the four `runFold` overloads, `runForeach`, and `start` — moved out of shared source into `StreamPlatformSpecific`, a trait that `Stream` mixes in under a self-type. The JVM copy of that trait declares all sixteen. The Scala.js copy is empty:

```scala
trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] => }
```

A blocking terminal parks the calling thread until another thread completes the pipeline. JavaScript has no other thread, so there is no implementation that both blocks and terminates; rather than ship one that throws, the library removed the members and let the compiler report the call site. [Why Blocking Terminals Are JVM-Only](./platform-differences.md#why-blocking-terminals-are-jvm-only) gives the full argument, and the [availability matrix](./platform-differences.md#availability-matrix) lists every affected member with its result type on each platform.

Three more members relocated the same way and produce the same class of error: `Sink.create`, `Reader.AsyncReader#toSync`, and — with a run-time failure rather than a compile error — `Async#block`.

### The Replacement Mapping

Every blocking terminal has a cross-platform twin whose name ends in `Async`:

| Blocking (JVM only)     | Cross-platform                                 |
|-------------------------|------------------------------------------------|
| `run(sink)`             | `runAsync(sink)`                               |
| `runCollect`            | `runCollectAsync`                              |
| `runDrain`              | `runDrainAsync`                                |
| `runForeach(f)`         | `runForeachAsync(f)`                           |
| `runFold(z)(f)`         | `runFoldAsync(z)(f)`                           |
| `count`                 | `countAsync`                                   |
| `exists(pred)`          | `existsAsync(pred)`                            |
| `find(pred)`            | `findAsync(pred)`                              |
| `forall(pred)`          | `forallAsync(pred)`                            |
| `foreach(f)`            | `foreachAsync(f)`                              |
| `head`                  | `headAsync`                                    |
| `last`                  | `lastAsync`                                    |
| `start`                 | `startAsync`, `useReaderAsync`                 |
| `Sink.create(f)`        | `Sink.createAsync(f)`, `Sink.createBoth(s, a)` |

The rename is not the whole edit. Every `*Async` terminal returns `Async[Either[E, Z]]` rather than `Either[E, Z]`, and every callback-taking replacement takes an *effectful* callback: `exists` wants `A => Boolean`, while `existsAsync` wants `A => Async[Boolean]`.

Pre-migration, in shared source:

```scala
val total: Either[Nothing, Long] =
  Stream(1, 2, 3).map(_ * 2).count

val firstEven: Either[Nothing, Option[Int]] =
  Stream(1, 2, 3).find(_ % 2 == 0)
```

After migration, compiling on both platforms:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

val total: Async[Either[Nothing, Long]] =
  Stream(1, 2, 3).map(_ * 2).countAsync

val firstEven: Async[Either[Nothing, Option[Int]]] =
  Stream(1, 2, 3).findAsync(i => Async.succeed(i % 2 == 0))
```

The asynchronous fold family is one overload wider than the blocking one: `runFoldAsync(z: Float)` has no blocking twin, so a `Float` accumulator is reachable only through the cross-platform API. [Async Terminals](./async-execution.md#async-terminals) documents the whole family, and [The `Async[Either[E, Z]]` Convention](./async-execution.md#the-asynceithere-z-convention) explains how a typed error and a defect differ inside that result.

### `.block` Is the Adapter of Last Resort

A JVM `main`, a test harness, or a synchronous interface you do not own still needs a plain value. `Async#block` parks the calling thread and produces one:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

// Runs on the JVM; on Scala.js this throws as soon as the pipeline suspends.
val total: Either[Nothing, Long] = Stream(1, 2, 3).countAsync.block
```

Use it at the edge of the program and nowhere else. It is declared in shared `async` source, so it compiles on Scala.js and then throws `IllegalStateException` the moment an effect actually has to wait — an already-complete effect returns normally, which makes the failure intermittent rather than immediate. [Driving an `Async` from a JVM main](./async-execution.md#driving-an-async-from-a-jvm-main) covers the pattern.

:::warning[Do not reach for `.block` inside a shared-source helper]
A helper that ends in `.block` compiles on both platforms and then fails at run time on one of them, converting a compile error the release deliberately gave you back into a production bug. Keep the `Async` in the signature and let the caller decide where to stop.
:::

## The `Reader` Split

`Reader[+Elem]` used to be one type carrying `read`, `close`, `readAll`, the eight primitive `read*` methods, the bulk `read*s` methods, and the lifecycle members. Pulling from a `Reader[A]` now fails:

```
value read is not a member of zio.blocks.streams.io.Reader[Int]
```

`Reader` became a root over two kinds, `Reader.SyncReader[Elem]` and `Reader.AsyncReader[Elem]`, and everything that pulls or closes moved down into one of them. A synchronous reader's `read` and `close` return directly; an asynchronous reader's return `Async`. Only four members are left on the root, all of them kind-independent composition:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]

  def jvmType: JvmType
}
```

Code that implemented or accepted an undifferentiated `Reader[A]` must choose a kind or handle both. Pre-migration:

```scala
def firstLong(reader: Reader[Long]): Long =
  reader.readLong(Long.MinValue)
```

After migration, picking the cross-platform kind:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams.io.Reader

def firstLong(reader: Reader.AsyncReader[Long]): Async[Long] =
  reader.readLong(Long.MinValue)
```

A caller holding a `SyncReader` reaches the asynchronous signature through `SyncReader#toAsync`, which is cross-platform and lifecycle-preserving — it unwraps an existing round trip rather than stacking adapters. The reverse direction, `AsyncReader#toSync`, exists only on the JVM, so a shared-source helper should be written in terms of `AsyncReader` and adapted at the call site, never the other way round.

Three smaller changes travel with the split:

- **`Stream#start` narrowed its result** from `scope.$[Reader[A]]` to `scope.$[Reader.SyncReader[A]]`. An explicit `Reader[A]` ascription on a `start` result is now a type mismatch on the JVM as well as a missing member on Scala.js. In practice this is the whole of the change for most codebases: the in-repo example diffs for the reader split were type ascriptions on `start` and nothing else.
- **`Reader.range` was removed** from the companion. `Reader.fromRange(range: Range): SyncReader[Int]` replaces it.
- **Every `object Reader` constructor now states its kind** in the return type — `fromChunk`, `fromIterable`, `single`, `repeat` and the rest return `SyncReader`, and the one native asynchronous constructor, `unfoldAsync`, returns `AsyncReader`.

[The Reader Union](./async-execution.md#the-reader-union) describes how a pipeline is classified into one kind or the other, and [Manual Pull Across Platforms](./platform-differences.md#manual-pull-across-platforms) covers porting a `start`-based protocol loop.

## Custom Sinks Now Have Two Drains

The symptom for a custom sink is a missing factory:

```
value create is not a member of object zio.blocks.streams.Sink
```

`Sink.create` did not disappear; it moved from the shared companion into `SinkCompanionPlatformSpecific`, whose JVM copy declares it and whose Scala.js copy does not. Its parameter narrowed at the same time, from `Reader[A] => Z` to `Reader.SyncReader[A] => Z`, so even on the JVM a callback annotated `Reader[A] => Z` no longer conforms.

Underneath the factory, a `Sink` now carries two drains, one per reader kind, and the terminal selects exactly one of them. Subclassing `Sink` therefore means implementing both — which is why the three factories, not the abstract class, are the supported route:

```scala
object Sink {
  // JVM only — the callback is guaranteed a blocking SyncReader
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]

  // JVM and Scala.js
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](
    sync: Reader.SyncReader[A] => Z,
    async: Reader.AsyncReader[A] => Async[Z]
  ): Sink[E, A, Z]
}
```

Pre-migration, a summing sink in shared source:

```scala
val sum: Sink[Nothing, Int, Long] =
  Sink.create[Nothing, Int, Long] { reader =>
    val scratch = new Array[Int](1)
    var total   = 0L
    var n       = reader.readInts(scratch, 0, 1)
    while (n > 0) {
      total += scratch(0)
      n = reader.readInts(scratch, 0, 1)
    }
    total
  }
```

`Sink.createAsync` is the smallest cross-platform replacement: one callback, sequenced with `Async#flatMap` instead of a loop. `Sink.createBoth` is the one to reach for when the synchronous drain is worth keeping, because the JVM then retains its allocation-free loop while Scala.js gets a working implementation:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

val sum: Sink[Nothing, Int, Long] =
  Sink.createBoth[Nothing, Int, Long](
    sync = { reader =>
      val scratch = new Array[Int](1)
      var total   = 0L
      var n       = reader.readInts(scratch, 0, 1)
      while (n > 0) {
        total += scratch(0)
        n = reader.readInts(scratch, 0, 1)
      }
      total
    },
    async = { reader =>
      val scratch = new Array[Int](1)
      def loop(total: Long): Async[Long] =
        reader.readInts(scratch, 0, 1).flatMap { n =>
          if (n > 0) loop(total + scratch(0)) else Async.succeed(total)
        }
      loop(0L)
    }
  )
```

The two callbacks must agree on how much input they consume and what they produce; the terminal runs one of them and never compares the results. [`Sink.create` and Custom Sinks](./platform-differences.md#sinkcreate-and-custom-sinks) states the platform rule, and [Sink](./sink.md) documents the reader protocol both callbacks drive.

## Specialization Changes

This chapter carries the five rules that the retired specialization migration notes stated as bare assertions, each expanded with the symptom that leads a reader here and the edit that resolves it. They exist because the release generalized primitive specialization: an operation now records the JVM representation of the element type it *produces*, so a pipeline over `Int` stays on the `Int` lane instead of boxing at the first transformation. [Zero-Boxing Streams](./zero-boxing.md) explains the mechanism; this chapter covers only what it broke.

### Evidence Follows Transformed Results

Element-preserving operators — `distinct`, `distinctBy`, `filter`, `tapEach`, their asynchronous twins, and the input sides of `collect`, `flatMap`, `map`, `mapAccum`, `mapPar` and the rest — stopped requesting `JvmType.Infer` for the element type they already have. They take the representation from the source and ask for evidence only when they introduce a new output type.

Passing evidence to one of them is now an arity error:

```
method filter in class Stream does not take more parameters
```

Pre-migration, evidence threaded for the input type:

```scala
def mapped[E, A, B](stream: Stream[E, A])(f: A => B)(implicit jtA: JvmType.Infer[A]): Stream[E, B] =
  stream.map(f)
```

After migration, the same helper asks for the result type instead:

```scala mdoc:compile-only
import zio.blocks.streams._

def mapped[E, A, B](stream: Stream[E, A])(f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B] =
  stream.map(f)
```

The second half of this rule is a variance change, and it produces a different message:

```
no implicit argument of type zio.blocks.streams.JvmType.Infer[B] was found
```

`JvmType.Infer` is declared `sealed trait Infer[A]` — invariant in `A` — precisely so that a fallback instance cannot drive output inference toward `Any` when a primitive lane is available. A helper carrying `Infer[Animal]` no longer satisfies a requirement for `Infer[Dog]`. Adding an `(implicit jt: JvmType.Infer[A])` parameter is always safe, though, because resolution never fails: a low-priority instance supplies `AnyRef` for every reference type.

### `Stream.succeed(Byte)` Is Genuinely `Byte`

`Stream.succeed(a: Byte)` used to return `Stream[Nothing, Int]`, silently applying the unsigned conversion `a & 0xff` to match `Stream.fromInputStream`'s convention. It now returns `Stream[Nothing, Byte]` and records `JvmType.Byte`, driving the Byte lane end to end. Code that depended on the old widening sees:

```
type mismatch: found Stream[Nothing, Byte], required Stream[Nothing, Int]
```

Pre-migration, relying on an implicit widening that no longer happens:

```scala
val unsigned: Stream[Nothing, Int] = Stream.succeed(0xff.toByte)
```

After migration, the conversion is written out. Use `_ & 0xff` to keep the old values exactly; a plain `_.toInt` sign-extends instead, and changes every negative byte:

```scala mdoc:compile-only
import zio.blocks.streams._

val raw: Stream[Nothing, Byte]     = Stream.succeed(0xff.toByte)
val unsigned: Stream[Nothing, Int] = raw.map(_ & 0xff)
```

### Concat, Zip, and Recovery Signatures

This is the sharpest break in the group, because it changes what *infers* rather than what exists.

`concat` and `++`, tuple zip `&&`, `intersperse`, and the recovery operators all gained a separate result type parameter with its own evidence, so that a widened result reaches the correct lane rather than inheriting the left-hand one:

```scala
def concat[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
  errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
  valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
  jtA3: JvmType.Infer[A3]
): Stream[E3, A3]

def &&[E2, E3, B, C](that: Stream[E2, B])(implicit
  errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
  zip: Stream.Zip[A, B, C],
  jtC: JvmType.Infer[C]
): Stream[E3, C]

def catchAll[E2, A2, A3](f: E => Stream[E2, A2])(implicit
  valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
  jtA3: JvmType.Infer[A3]
): Stream[E2, A3]
```

The `@uncheckedVariance` annotations on the left inputs are how variance is kept sound here; the older notes described this as a lower bound on the left element type, which is not what the implementation does.

Let these infer. Casts, invariant wrappers, and input-side evidence added to compensate for the older signatures should come out.

The break that costs real work is the deletion of the **preserving overloads of `catchAll` and `catchDefect`**. One widening form of each now remains. That is deliberate: once a stream has been widened, a recovery branch can legally change its element type from `Int` to `Double`, and preserving the original lane would be unsound. The consequence is that a call which used to resolve to the preserving overload now resolves to the widening one, and its inferred element type can differ from what a surrounding annotation expects:

```
type mismatch: found Stream[Nothing, Any], required Stream[Nothing, Int]
```

Pre-migration, a recovery branch with a different element type under a pinned annotation:

```scala
val parsed: Stream[Nothing, Int] =
  Stream.attempt(Integer.parseInt("not a number")).catchAll(_ => Stream.succeed(0.0))
```

After migration there are two honest resolutions, and which one is right is a domain question rather than a mechanical one. Either make both branches agree on an element type, which keeps the primitive lane:

```scala mdoc:compile-only
import zio.blocks.streams._

val parsed: Stream[Nothing, Int] =
  Stream.attempt(Integer.parseInt("not a number")).catchAll(_ => Stream.succeed(0))
```

or widen the annotation to the type the two branches actually produce, accepting that the result is boxed.

### `Sink#contramap` Evidence Targets `A0`

`contramap` transforms the sink's input, so it has two element types in play: `A2`, the new external input, and `A0`, the value the callback feeds to the original sink. The required evidence describes `A0` — the lane the wrapped sink will actually see — not `A2`:

```scala
def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
```

Evidence supplied explicitly for the external type is rejected:

```
no implicit argument of type zio.blocks.streams.JvmType.Infer[A0] was found
```

Pre-migration, the evidence aimed at the wrong type:

```scala
val totalLength: Sink[Nothing, String, Long] =
  Sink.foldLeft[Int, Long](0L)((acc, n) => acc + n)
    .contramap[Int, String](_.length)(JvmType.Infer.boxed[String])
```

After migration, nothing is passed and the `Int` lane is inferred from the callback's result:

```scala mdoc:compile-only
import zio.blocks.streams._

val totalLength: Sink[Nothing, String, Long] =
  Sink.foldLeft[Int, Long](0L)((acc, n) => acc + n).contramap[Int, String](_.length)
```

### Long/Double Bulk EOF Moved Out of Band

This rule lifts a restriction rather than imposing one, so it produces no compile error. It is on this page because code written under the old restriction is now carrying a bug it did not have before.

A reader used to be able to signal end-of-stream by returning a chosen data value. For `Long` and `Double` that is unsound: every `Long` bit pattern and every raw `Double` bit pattern, NaN payloads included, is legitimate data, so any sentinel collides with a value some stream can legitimately carry. The bulk operations are now the authoritative full-domain protocol, and they report status out of band in the return count:

- a positive count is the number of elements read, `1 <= n <= maxLen`;
- `-1` means end of stream;
- `0` occurs only for a zero-length request.

Reading one element at a time is `readLongs(scratch, 0, 1)` or `readDoubles(scratch, 0, 1)` against a one-element scratch array allocated once by the owner — never inside the pull loop.

Pre-migration, a value reserved as an end marker:

```scala
def sum(reader: Reader[Long]): Long = {
  var total = 0L
  var v     = reader.readLong(Long.MinValue)   // Long.MinValue doubles as EOF
  while (v != Long.MinValue) {                 // ...and as a legitimate element
    total += v
    v = reader.readLong(Long.MinValue)
  }
  total
}
```

After migration, the count carries the status and every bit pattern is data:

```scala mdoc:compile-only
import zio.blocks.streams.io.Reader

def sum(reader: Reader.SyncReader[Long]): Long = {
  val scratch = new Array[Long](1)
  var total   = 0L
  var n       = reader.readLongs(scratch, 0, 1)
  while (n > 0) {
    total += scratch(0)
    n = reader.readLongs(scratch, 0, 1)
  }
  total
}
```

The scalar `readLong` and `readDouble` methods remain, for callers that can prove a sentinel lies outside their data domain. General-purpose code should not use them for EOF detection, and neither does the library: the conformance probes install readers whose scalar `readLong` and `readDouble` entry points throw even on their own matching lane, so a regression that reintroduces sentinel detection fails a test rather than corrupting a value.

### Both Pipeline Routes

Every rule in this chapter applies through both ways of applying a `Pipeline`, because the two routes materialize differently but ask for the same evidence:

```scala mdoc:compile-only
import zio.blocks.streams._

val toLength: Pipeline[String, Int] = Pipeline.map[String, Int](_.length)

// Route 1 — apply the pipeline to the stream
val lengths: Stream[Nothing, Int] = Stream("a", "bb", "ccc").via(toLength)

// Route 2 — apply the pipeline to the sink
val totalLength: Sink[Nothing, String, Long] =
  toLength.andThenSink(Sink.foldLeft[Int, Long](0L)(_ + _))
```

`Pipeline#applyToSink` is the third spelling and behaves the same way. If a migration edit fixes one route, apply it to the other.

## Binary Compatibility

Binary compatibility was broken deliberately across this release, and the build does not pretend otherwise. The streams module is configured with `mimaSettings(failOnProblem = false)` and is not part of the `mimaChecks` alias, so MiMa reports nothing that would stop a release. No exclusions were added, because none were needed.

Recompile every downstream module against the new artifact. Dropping the new jar onto a classpath built against the previous one produces `NoSuchMethodError` at whatever point execution first reaches a relocated member — which, for the sixteen blocking terminals, is the terminal call itself.

One in-repo consumer shows the shape of a downstream fix. The SQL module's `Frag` used to build a query stream by throwing the module-internal `StreamError` from an acquire block:

```scala
import zio.blocks.streams.internal.StreamError

Stream.fromAcquireRelease(
  acquire = {
    try { /* prepare, execute */ }
    catch { case e: Throwable => throw new StreamError(e) }
  },
  release = { /* close */ }
)
```

`StreamError` is internal machinery of the stream runtime rather than an API: it gained a private primary constructor and a `private[streams]` companion, and its parent changed from `ControlThrowable` to `Exception`. Code outside the module should stop constructing it — throw the cause itself and let `Stream.attempt` turn it into a typed `Throwable` channel:

```scala
Stream.attempt {
  try { /* prepare, execute */ }
  catch { case e: Throwable => /* log */ throw e }
}.flatMap { resource => Stream.fromAcquireRelease(/* ... */) }
```

The parent change is the one consequence that reaches code which never names the type. A `ControlThrowable` is excluded by `scala.util.control.NonFatal` and is not a subtype of `Exception`, so a `catch { case NonFatal(e) => ... }` or a `scala.util.Try` around a stream could not swallow a typed stream error; an `Exception` can be caught by both. Handlers of that shape wrapped around stream code should be checked.

## What Did Not Break

Naming the non-breaks is worth a paragraph, because it saves an audit:

- **`Writer[-Elem]` lost nothing.** Sixteen `*Async` names were added as a mechanical mirror of the existing methods; no member was removed, relocated, or narrowed.
- **`Pipeline[-In, +Out]` lost nothing.** It gained three asynchronous factories — `collectAsync`, `filterAsync`, `mapAsync` — and `andThen`, `andThenSink`, `applyToSink`, and `applyToStream` are unchanged.
- **No `@deprecated` annotations were added anywhere.** That is not an oversight. These are relocations and splits, and a deprecation cycle cannot express "this member exists on one platform and not the other" — the compiler either finds the member or does not.
- **There is no Scala 2 versus Scala 3 difference in any of this.** The streams module's two version-specific source trees contain only `private[streams]` traits, and every signature on this page is identical on both. The axis that changes what compiles is JVM versus Scala.js.
- **`Stream[E, A]` itself is unchanged as a type.** There is no second stream type, no mode parameter, and no annotation distinguishing a synchronous description from an asynchronous one. A migrated pipeline keeps the same type in the same signatures.

## See Also

- [Platform Differences: JVM and Scala.js](./platform-differences.md) — the availability matrix, and why the split is enforced at compile time
- [Asynchronous Stream Execution](./async-execution.md) — the full `*Async` constructor, operator, and terminal surface
- [Zero-Boxing Streams](./zero-boxing.md) — how a primitive lane is chosen, and what the specialization evidence is for
- [Reader](./reader.md) — the two reader kinds and the pull protocol
- [Sink](./sink.md) — the sink factories and the dual drain contract
- [Stream](./stream.md) — the operator and terminal reference
