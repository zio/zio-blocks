/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams

import scala.annotation.unchecked.uncheckedVariance
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.{Concat, Tuples}
import zio.blocks.scope.{Resource, Scope}
import zio.blocks.streams.internal.{
  addSuppressedSafe,
  cleanupWithPrimary,
  doubleEOF,
  elementWiseReadUpToN,
  isCatchable,
  longEOF,
  runBoth,
  EndOfStream,
  Interpreter,
  SinkError,
  StreamError,
  pullDouble,
  pullFloat,
  pullInt,
  pullLong
}
import zio.blocks.streams.io.Reader

/**
 * A lazy, pull-based, resource-safe sequence of elements of type `A` that may
 * fail with an error of type `E`.
 *
 * A [[Stream]] is a ''description'': nothing executes until a terminal
 * operation is called. Terminal operations like [[run]], [[runCollect]],
 * [[head]], etc. execute synchronously and return `Either[E, Z]`, where `Left`
 * contains typed stream errors and `Right` contains the result. Untyped defects
 * propagate as thrown exceptions.
 *
 * @tparam E
 *   Error type.
 * @tparam A
 *   Element type.
 */
abstract class Stream[+E, +A] {

  /** Renders this stream as a human-readable description of its pipeline. */
  def render: String

  /**
   * Returns the underlying `Chunk` if this stream wraps a known, materialized
   * chunk, `None` otherwise. O(1).
   *
   * Only streams created via [[Stream.fromChunk]], [[Stream.fromArray]], or
   * [[Stream.empty]] return `Some`. All combinators (map, filter, take, drop,
   * concat, etc.) return `None` because the chunk identity is lost after
   * transformation.
   */
  def knownChunk: Option[Chunk[A]] = None

  /**
   * Returns the number of elements if known without consuming the stream,
   * `None` otherwise. O(1).
   *
   * Element-preserving combinators like `map` propagate the count.
   * Element-filtering combinators like `filter` invalidate it.
   */
  def knownLength: Option[Long] = None

  /** Returns [[render]]. */
  final override def toString: String = render

  /** Alias for [[concat]]. */
  final def ++[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] = concat(that)

  /**
   * Alias for [[orElse]]. The fallback stream is evaluated lazily, only on
   * error. The original error type `E` is handled and discarded, so the result
   * error type is the fallback's error type `E2`.
   */
  def ||[E2, A2, A3](that: => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3]
  ): Stream[E2, A3] =
    orElse(that)

  /**
   * Recovers from all errors by switching to the stream returned by `f`. The
   * original error `E` is handled and does not appear in the result; the result
   * error type is the recovery stream's error type `E2`. The element type is
   * the [[Concat]] of the original and recovery element types.
   *
   * The output reader reports `AnyRef` as its JVM lane: because the recovery
   * stream is materialized lazily (only on error) its specialization is unknown
   * up front, so the safe, always-correct generic read path is used.
   */
  def catchAll[E2, A2, A3](f: E => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3]
  ): Stream[E2, A3] =
    // Identity fast path: when no element widening is needed (A2 =:= A =:= A3),
    // use `this`/`f` directly instead of allocating a `recover` wrapper closure.
    if (valueConcat.isIdentityLike)
      new Stream.CatchAll[E, E2, A3](
        this.asInstanceOf[Stream[E, A3]],
        f.asInstanceOf[E => Stream[E2, A3]],
        JvmType.AnyRef
      )
    else {
      val self0   = Stream.widenElemLeft(this.asInstanceOf[Stream[E, A]], valueConcat)
      val recover = (e: E) => Stream.widenElemRight(f(e), valueConcat)
      new Stream.CatchAll[E, E2, A3](self0, recover, JvmType.AnyRef)
    }

  /**
   * Recovers from non-fatal defects (throwables that are not typed stream
   * errors) matching `f`. Typed errors `E` from this stream still propagate, so
   * the result error type is the [[Concat]] of `E` and the recovery error `E2`;
   * likewise the element type is the [[Concat]] of `A` and `A2`. As with
   * [[catchAll]], the output reader reports `AnyRef`.
   */
  def catchDefect[E2, E3, A2, A3](f: PartialFunction[Throwable, Stream[E2, A2]])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3]
  ): Stream[E3, A3] =
    // Identity fast path: when neither the error nor the element channel needs
    // widening, use `this`/`f` directly instead of allocating a wrapping
    // `PartialFunction` (and per-defect widen indirections).
    if (errorConcat.isIdentityLike && valueConcat.isIdentityLike)
      new Stream.CatchDefect[E3, A3](
        this.asInstanceOf[Stream[E3, A3]],
        f.asInstanceOf[PartialFunction[Throwable, Stream[E3, A3]]],
        JvmType.AnyRef
      )
    else {
      val self0 =
        Stream.widenElemLeft(Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat), valueConcat)
      val recover = new PartialFunction[Throwable, Stream[E3, A3]] {
        def isDefinedAt(t: Throwable): Boolean  = f.isDefinedAt(t)
        def apply(t: Throwable): Stream[E3, A3] =
          Stream.widenElemRight(Stream.widenErrorRight(f(t), errorConcat), valueConcat)
      }
      new Stream.CatchDefect[E3, A3](self0, recover, JvmType.AnyRef)
    }

  /** Applies a partial function, emitting only defined results. */
  def collect[B](pf: PartialFunction[A, B])(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Stream[E, B] =
    via(Pipeline.collect(pf))

  /**
   * Emits all elements of `this` followed by all elements of `that`. Both the
   * error and element channels widen to their [[Concat]] (a union `L | R` on
   * Scala 3; the least upper bound or `Either[L, R]` on Scala 2).
   */
  def concat[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] = {
    val l = Stream.widenElemLeft(Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat), valueConcat)
    val r = Stream.widenElemRight(Stream.widenErrorRight(that, errorConcat), valueConcat)
    new Stream.Concatenated[E3, A3](l, r, jtA3.jvmType)
  }

  /**
   * Zips this stream with `that`, pairing elements positionally. Shorter stream
   * determines length. Uses [[zio.blocks.combinators.Tuples]] for flattened
   * composition: `a && b && c` produces a `Stream` of `(A, B, C)`.
   */
  def &&[E2, E3, B, C](
    that: Stream[E2, B]
  )(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    t: Tuples.Tuples[A @uncheckedVariance, B] { type Out = C }
  ): Stream[E3, C] =
    Stream.fromReader {
      val leftStream  = Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat)
      val rightStream = Stream.widenErrorRight(that, errorConcat)
      val left        = Stream.compileToReader(leftStream.asInstanceOf[Stream[Any, Any]])
      val right       =
        try Stream.compileToReader(rightStream.asInstanceOf[Stream[Any, Any]])
        catch {
          case t: Throwable =>
            try left.close()
            catch { case s: Throwable => t.addSuppressed(s) }
            throw t
        }
      new io.Reader[C] {
        // Each side is closed exactly once. `read` eagerly releases the unused
        // side as soon as the other hits EOF (prompt resource release), and
        // `Stream.run` later closes this reader; without these idempotence flags
        // the eagerly-closed side's finalizer would run twice (double
        // finalization). `reset()` clears the flags so `repeated` can restart.
        private var leftClosed              = false
        private var rightClosed             = false
        private def closeLeft(): Unit       = if (!leftClosed) { leftClosed = true; left.close() }
        private def closeRight(): Unit      = if (!rightClosed) { rightClosed = true; right.close() }
        def isClosed                        = left.isClosed || right.isClosed
        def read[O1 >: C](sentinel: O1): O1 = {
          val l = left.read[Any](EndOfStream)
          if (l.asInstanceOf[AnyRef] eq EndOfStream) { closeRight(); return sentinel }
          val r = right.read[Any](EndOfStream)
          if (r.asInstanceOf[AnyRef] eq EndOfStream) { closeLeft(); return sentinel }
          t.combine(l.asInstanceOf[A @uncheckedVariance], r.asInstanceOf[B]).asInstanceOf[O1]
        }
        // Reset both sides so `repeated` can restart an in-memory zip. `runBoth`
        // guarantees the second reset runs even if the first throws, suppressing
        // a secondary failure onto the primary rather than discarding it. A
        // one-shot side legitimately throws `UnsupportedOperationException`,
        // which propagates (it is not swallowed). See AdversarialRepeatStateSpec.
        override def reset(): Unit = { leftClosed = false; rightClosed = false; runBoth(left.reset())(right.reset()) }
        // Idempotent close of both sides with try-with-resources suppression: a
        // failure closing one side never discards the other's failure.
        def close(): Unit = runBoth(closeLeft())(closeRight())
      }
    }

  /** Counts the number of elements. */
  def count: Either[E, Long] = run(Sink.count)

  /**
   * Skips the first `n` elements, then emits the rest.
   *
   * Fusion note: for performance, the skip may be pushed below earlier pure
   * transformations (e.g. `map`), in which case the mapping function is never
   * invoked for skipped elements. Functions passed to `map`/`tapEach` should
   * therefore be pure with respect to skipped elements; use [[Stream#mapAccum]]
   * or an explicit `filter` for stateful/effectful per-element logic that must
   * observe every element.
   */
  def drop(n: Long): Stream[E, A] = new Stream.Dropped(this, n)

  /**
   * Emits only elements not previously seen, using a mutable set internally.
   */
  def distinct(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    // The dedup set must live in the reader, not in a `Stream.suspend` closure.
    // With the old closure-based impl, `Stream.repeated` rewound the source but
    // NOT the `seen` set, so the second cycle saw every element as a duplicate
    // and emitted nothing — making `repeated` (which loops until a non-EOF
    // element appears) LIVELOCK (BUG-005). Resetting `seen` on `reset()` fixes
    // it. Source is reset first; a one-shot source's reset throws and propagates.
    new Stream.FromReader[E, A](
      () => {
        val source = Stream.compileToReader(this)
        new Reader[A] {
          private val seen                    = new scala.collection.mutable.HashSet[A]()
          override def jvmType: JvmType       = jtA.jvmType
          def isClosed: Boolean               = source.isClosed
          def read[A1 >: A](sentinel: A1): A1 = {
            var v = source.read[Any](EndOfStream)
            while ((v.asInstanceOf[AnyRef] ne EndOfStream) && !seen.add(v.asInstanceOf[A]))
              v = source.read[Any](EndOfStream)
            if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[A1]
          }
          override def reset(): Unit = { source.reset(); seen.clear() }
          def close(): Unit          = source.close()
        }
      },
      s"${this.render}.distinct"
    )

  /**
   * Emits only elements whose key (computed by `f`) has not been seen before.
   */
  def distinctBy[K](f: A => K)(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    // See `distinct`: the key set must live in the reader and be cleared on
    // `reset()` so `repeated` does not livelock (BUG-005).
    new Stream.FromReader[E, A](
      () => {
        val source = Stream.compileToReader(this)
        new Reader[A] {
          private val seen                    = new scala.collection.mutable.HashSet[K]()
          override def jvmType: JvmType       = jtA.jvmType
          def isClosed: Boolean               = source.isClosed
          def read[A1 >: A](sentinel: A1): A1 = {
            var v = source.read[Any](EndOfStream)
            while ((v.asInstanceOf[AnyRef] ne EndOfStream) && !seen.add(f(v.asInstanceOf[A])))
              v = source.read[Any](EndOfStream)
            if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[A1]
          }
          override def reset(): Unit = { source.reset(); seen.clear() }
          def close(): Unit          = source.close()
        }
      },
      s"${this.render}.distinctBy(...)"
    )

  /**
   * Runs `finalizer` when the stream closes, whether it completes cleanly or
   * with an error.
   */
  def ensuring(finalizer: => Unit): Stream[E, A] =
    new Stream.Ensuring(this, finalizer)

  /** Returns `true` if any element satisfies `pred`, short-circuiting. */
  def exists(pred: A => Boolean): Either[E, Boolean] = run(Sink.exists(pred))

  /** Emits only elements satisfying `pred`. */
  def filter(pred: A => Boolean)(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    new Stream.Filtered(this, pred, jtA)

  /** Returns the first element satisfying `pred`, or `None`. */
  def find(pred: A => Boolean): Either[E, Option[A]] = run(Sink.find(pred))

  /**
   * Maps each element to a stream and flattens the results sequentially. The
   * error channel widens to the [[Concat]] of this stream's error `E` and the
   * inner streams' error `E2` (a union `E | E2` on Scala 3; the least upper
   * bound or `Either[E, E2]` on Scala 2).
   *
   * ==Performance and safety==
   *
   * `flatMap` preserves primitive-specialized output lanes when it is safe to
   * do so, but each returned inner stream is still treated as an independent
   * reader. In particular, an exhausted inner reader is closed before the next
   * inner reader is pulled, and primitive flatMap readers dynamically fall back
   * to the boxed/reference read path when an inner reader advertises the
   * `AnyRef` lane.
   *
   * These checks are intentional. They ensure inner finalizers and close
   * failures are not lost, and they prevent `ClassCastException` or silent
   * wrong-lane reads for boxed-primitive or interpreter-backed inner streams.
   *
   * In workloads that create very small inner streams at very high frequency —
   * for example, flat-mapping each element to only a handful of primitive
   * values — this resource/type-safety bookkeeping can be a measurable part of
   * total runtime. Prefer coarser inner streams, or use `map`, `filter`,
   * `filterMap`, ranges, and chunks directly when they express the same logic.
   */
  def flatMap[E2, E3, B](
    f: A => Stream[E2, B]
  )(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[E3, B] =
    // Identity fast path: when no error widening is needed (E2 =:= E =:= E3),
    // use `this`/`f` directly. This avoids allocating a wrapper closure per
    // `flatMap` call (and a per-element `widenErrorRight` indirection at run
    // time), matching the un-widened cost. Only the genuine union case pays for
    // the `mapError` projections.
    if (errorConcat.isIdentityLike)
      new Stream.FlatMapped[E3, E3, A, B](
        this.asInstanceOf[Stream[E3, A]],
        f.asInstanceOf[A => Stream[E3, B]],
        jtA,
        jtB
      )
    else {
      val self0 = this.asInstanceOf[Stream[E, A]].mapError(errorConcat.left)
      val inner = (a: A) => f(a).mapError(errorConcat.right)
      new Stream.FlatMapped[E3, E3, A, B](self0, inner, jtA, jtB)
    }

  /**
   * Applies `f` to each element to produce an inner stream, then merges up to
   * `n` inner streams concurrently. Output is unordered (arrival order, not
   * input order). On JVM, each inner stream runs on a virtual thread. On JS,
   * degrades to sequential flatMap.
   */
  def flatMapPar[E1 >: E, B](n: Int)(f: A => Stream[E1, B])(implicit
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[E1, B] = {
    require(n >= 1, s"flatMapPar requires n >= 1, got $n")
    jtB.jvmType
    Stream.mergeAll[E1, B](n)(this.asInstanceOf[Stream[E1, A]].map(f))
  }

  /** Returns `true` if all elements satisfy `pred`, short-circuiting. */
  def forall(pred: A => Boolean): Either[E, Boolean] = run(Sink.forall(pred))

  /**
   * Alias for [[runForeach]]. Applies `f` to each element for its side-effects.
   */
  def foreach(f: A => Unit): Either[E, Unit] = runForeach(f)

  /**
   * Groups elements into `Chunk`s of size `n`. The last group may be smaller.
   */
  def chunked(n: Int): Stream[E, Chunk[A]] = {
    require(n >= 1, s"chunked requires n >= 1, got n=$n")
    new Stream.FromReader[E, Chunk[A]](
      () => {
        val source = Stream.compileToReader(this)
        new Reader[Chunk[A]] {
          def isClosed                               = source.isClosed
          def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
            val c = source.readN[A](n)
            if (c.isEmpty) sentinel else c.asInstanceOf[A1]
          }
          // `chunked` holds no per-read state (the chunk is built fresh each
          // read), so restarting for `repeated` only needs the source rewound.
          // A one-shot source's `reset()` legitimately throws and is not
          // swallowed. See AdversarialRepeatStateSpec.
          override def reset(): Unit = source.reset()
          def close()                = source.close()
        }
      },
      s"${this.render}.chunked($n)"
    )
  }

  /** Alias for [[chunked]]. Matches upstream naming. */
  def grouped(n: Int): Stream[E, Chunk[A]] = chunked(n)

  /** Returns the first element, or `None` if empty. */
  def head: Either[E, Option[A]] = run(Sink.head)

  /**
   * Inserts `sep` between each pair of consecutive elements. The element type
   * widens to the [[Concat]] of the stream's element type `A` and the separator
   * type `A2` (a union `A | A2` on Scala 3; the least upper bound or
   * `Either[A, A2]` on Scala 2).
   */
  def intersperse[A2, A3](sep: A2)(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3]
  ): Stream[E, A3] =
    new Stream.FromReader[E, A3](
      () => {
        val source       = Stream.compileToReader(this)
        val projectedSep = valueConcat.right(sep)
        new Reader[A3] {
          private var first       = true
          private var hasCached   = false
          private var cached: Any = null
          // A cached element is still deliverable after the source closes; a
          // readable()-gated consumer loop must not drop it (BUG-R7-03).
          def isClosed                         = !hasCached && source.isClosed
          override def readable(): Boolean     = hasCached || source.readable()
          def read[O1 >: A3](sentinel: O1): O1 =
            if (hasCached) { hasCached = false; cached.asInstanceOf[O1] }
            else {
              val v = source.read[Any](EndOfStream)
              if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
              else if (first) { first = false; valueConcat.left(v.asInstanceOf[A]).asInstanceOf[O1] }
              else { cached = valueConcat.left(v.asInstanceOf[A]); hasCached = true; projectedSep.asInstanceOf[O1] }
            }
          // Rewind both the source and the local separator-interleaving state so
          // `repeated` restarts cleanly. Source is reset first; if it is one-shot
          // its `reset()` throws and propagates (not swallowed). See
          // AdversarialRepeatStateSpec.
          override def reset(): Unit = {
            source.reset()
            first = true
            hasCached = false
            cached = null
          }
          def close() = source.close()
        }
      },
      s"${this.render}.intersperse(...)"
    )

  /** Returns the last element, or `None` if empty. */
  def last: Either[E, Option[A]] = run(Sink.last)

  /** Lazily transforms each element with `f`. */
  def map[B](f: A => B)(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Stream[E, B] =
    new Stream.Mapped(this, f, jtA, jtB)

  /**
   * Applies `f` to each element using `n` concurrent workers. Output is
   * UNORDERED (arrival order, not input order). On JVM, workers run on virtual
   * threads. On JS, degrades to sequential map.
   *
   * @param n
   *   number of concurrent workers
   * @param f
   *   transformation to apply to each element
   */
  def mapPar[B](n: Int)(f: A => B)(implicit
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[E, B] = {
    require(n >= 1, s"mapPar requires n >= 1, got $n")
    jtA.jvmType
    jtB.jvmType
    new Stream.MapPar[E, A, B](this, n, f, jtA.jvmType, jtB.jvmType)
  }

  /**
   * Transforms elements with a stateful function `f`, threading state `S`
   * through each step and emitting the `B` from each result.
   *
   * @param init
   *   Initial state value.
   * @param f
   *   Function that takes the current state and an element, returning the
   *   updated state and the output element.
   */
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B)): Stream[E, B] =
    // The accumulator must live in the reader (not in a `Stream.suspend`
    // closure created once at compile time). With the old closure-based impl,
    // `Stream.repeated` rewound the source reader but NOT the external `state`
    // var, so the second cycle kept accumulating from where the first ended
    // (BUG-003). Holding state in the reader and resetting it on `reset()` makes
    // `repeated` restart the accumulator. (`mapAccum` already boxes through the
    // generic `(S, A) => (S, B)` and its `Tuple2` result, so the previous
    // `JvmType`-specialized `map` path gave no zero-boxing benefit here.)
    new Stream.FromReader[E, B](
      () => {
        val source = Stream.compileToReader(this)
        new Reader[B] {
          private var state: S                = init
          def isClosed: Boolean               = source.isClosed
          def read[B1 >: B](sentinel: B1): B1 = {
            val v = source.read[Any](EndOfStream)
            if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
            else {
              val (s2, b) = f(state, v.asInstanceOf[A])
              state = s2
              b.asInstanceOf[B1]
            }
          }
          // Source is reset first; a one-shot source's reset throws and
          // propagates (not swallowed).
          override def reset(): Unit = { source.reset(); state = init }
          def close(): Unit          = source.close()
        }
      },
      s"${this.render}.mapAccum(...)"
    )

  /**
   * Transforms the error channel with `f`, leaving elements unchanged.
   *
   * When `E` is `Nothing` (infallible stream), the call is eliminated at
   * compile time and the stream is returned unchanged.
   */
  def mapError[E2](f: E => E2): Stream[E2, A] =
    new Stream.ErrorMapped(this, f)

  /**
   * Falls back to `that` on any error. Alias: `||`. The original error `E` is
   * handled and discarded, so the result error type is the fallback's error
   * type `E2`; the element type is the [[Concat]] of `A` and `A2`.
   */
  def orElse[E2, A2, A3](that: => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3]
  ): Stream[E2, A3] =
    catchAll[E2, A2, A3](_ => that)

  /** Restarts this stream from the beginning each time it completes cleanly. */
  def repeated: Stream[E, A] = new Stream.Repeated(this)

  /**
   * Runs this stream into `sink`, returning either an error `Left(e)` or the
   * sink's result `Right(z)`. Typed stream errors are returned as `Left`;
   * untyped defects propagate as exceptions.
   */
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Either[E3, Z] =
    // Stream-origin typed errors propagate as `StreamError`; sink-origin typed
    // errors (`Sink.fail`) propagate as `SinkError`. Because the two carriers
    // are distinct types, we can tell the origin apart and project each through
    // the correct side of `errorConcat` (left for stream, right for sink) for
    // both the identity-like and disjoint (`Either`) cases. This is also what
    // prevents `Sink.mapError` from ever rewriting a stream-origin error.
    //
    // `close()` runs with try-with-resources suppression: a close failure never
    // discards an in-flight `drain` failure, and is itself surfaced when nothing
    // else is in flight (Principle 4). `cleanupWithPrimary` combines via
    // `combineFailures`, so an UNTYPED close defect WINS over an in-flight
    // typed-error carrier: otherwise the carrier would be projected to `Left`
    // below and the defect (which the contract says must propagate as a thrown
    // exception) would be silently swallowed (AdversarialCleanupErrorIntegritySpec).
    try {
      val reader             = Stream.compileToReader(this)
      var primary: Throwable = null
      val z                  =
        try sink.drain(reader)
        catch {
          case t: Throwable =>
            primary = t
            null.asInstanceOf[Z]
        }
      val toThrow = cleanupWithPrimary(primary)(reader.close())
      if (toThrow ne null) throw toThrow
      Right(z)
    } catch {
      case e: StreamError => Left(errorConcat.left(e.value.asInstanceOf[E]))
      case e: SinkError   => Left(errorConcat.right(e.value.asInstanceOf[ES]))
    }

  /** Runs the stream and collects all elements into a `Chunk`. */
  def runCollect: Either[E, Chunk[A]] = run(Sink.collectAll)

  /** Runs the stream, discarding all elements. */
  def runDrain: Either[E, Unit] = run(Sink.drain)

  /** Specialized `runFold` with a `Double` accumulator to avoid boxing. */
  def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double] =
    run(new Sink.FoldLeftDouble(z, f))

  /** Specialized `runFold` with an `Int` accumulator to avoid boxing. */
  def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int] =
    run(new Sink.FoldLeftInt(z, f))

  /** Specialized `runFold` with a `Long` accumulator to avoid boxing. */
  def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long] =
    run(new Sink.FoldLeftLong(z, f))

  /** Runs the stream, folding elements with `f` starting from `z`. */
  def runFold[Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Either[E, Z] = run(Sink.foldLeft(z)(f))

  /** Runs the stream, applying `f` to each element for its side-effects. */
  def runForeach(f: A => Unit): Either[E, Unit] = run(Sink.foreach(f))

  /**
   * Emits the accumulator at each step, starting with `init`. The output stream
   * has one more element than the input.
   */
  def scan[S](init: S)(f: (S, A) => S)(implicit jtS: JvmType.Infer[S]): Stream[E, S] =
    new Stream.FromReader[E, S](
      () => {
        val source  = Stream.compileToReader(this)
        val srcType = source.jvmType
        val outType = jtS.jvmType
        if ((srcType eq JvmType.Byte) && (outType eq JvmType.Byte)) {
          val fB = f.asInstanceOf[(Byte, Byte) => Byte]
          new Reader[S] {
            private var state: Byte = init.asInstanceOf[Byte]
            private var emittedInit = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit    = { source.reset(); state = init.asInstanceOf[Byte]; emittedInit = false }
            override def jvmType: JvmType = JvmType.Byte
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) sentinel
                else { state = fB(state, v.toByte); state.asInstanceOf[S1] }
              }
            override def readInt(sentinel: Long)(implicit ev: S <:< Int): Long =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Byte].toLong }
              else {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) sentinel
                else { state = fB(state, v.toByte); state.toLong }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Int) && (outType eq JvmType.Int)) {
          val fI = f.asInstanceOf[(Int, Int) => Int]
          new Reader[S] {
            private var state: Int  = init.asInstanceOf[Int]
            private var emittedInit = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit    = { source.reset(); state = init.asInstanceOf[Int]; emittedInit = false }
            override def jvmType: JvmType = JvmType.Int
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) sentinel
                else { state = fI(state, v.toInt); state.asInstanceOf[S1] }
              }
            override def readInt(sentinel: Long)(implicit ev: S <:< Int): Long =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Int].toLong }
              else {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) sentinel
                else { state = fI(state, v.toInt); state.toLong }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Long) && (outType eq JvmType.Long)) {
          val fL = f.asInstanceOf[(Long, Long) => Long]
          new Reader[S] {
            private var state: Long = init.asInstanceOf[Long]
            private var emittedInit = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit    = { source.reset(); state = init.asInstanceOf[Long]; emittedInit = false }
            override def jvmType: JvmType = JvmType.Long
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = pullLong(source, Long.MaxValue)
                if (longEOF(source, v, Long.MaxValue)) sentinel
                else { state = fL(state, v); state.asInstanceOf[S1] }
              }
            // This reader emits accumulated `state`, which may itself equal the
            // sentinel value, so it maintains its own EOF flag rather than
            // delegating to `source` (BUG-004).
            override def readLong(sentinel: Long)(implicit ev: S <:< Long): Long =
              if (!emittedInit) { emittedInit = true; markReadValue(); init.asInstanceOf[Long] }
              else {
                val v = pullLong(source, Long.MaxValue)
                if (longEOF(source, v, Long.MaxValue)) { markReadEOF(); sentinel }
                else { state = fL(state, v); markReadValue(); state }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Float) && (outType eq JvmType.Float)) {
          val fF = f.asInstanceOf[(Float, Float) => Float]
          new Reader[S] {
            private var state: Float = init.asInstanceOf[Float]
            private var emittedInit  = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit    = { source.reset(); state = init.asInstanceOf[Float]; emittedInit = false }
            override def jvmType: JvmType = JvmType.Float
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = pullFloat(source, Double.MaxValue)
                if (v == Double.MaxValue) sentinel
                else { state = fF(state, v.toFloat); state.asInstanceOf[S1] }
              }
            override def readFloat(sentinel: Double)(implicit ev: S <:< Float): Double =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Float].toDouble }
              else {
                val v = pullFloat(source, Double.MaxValue)
                if (v == Double.MaxValue) sentinel
                else { state = fF(state, v.toFloat); state.toDouble }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Double) && (outType eq JvmType.Double)) {
          val fD = f.asInstanceOf[(Double, Double) => Double]
          new Reader[S] {
            private var state: Double = init.asInstanceOf[Double]
            private var emittedInit   = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit    = { source.reset(); state = init.asInstanceOf[Double]; emittedInit = false }
            override def jvmType: JvmType = JvmType.Double
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = pullDouble(source, Double.MaxValue)
                if (doubleEOF(source, v, Double.MaxValue)) sentinel
                else { state = fD(state, v); state.asInstanceOf[S1] }
              }
            // This reader emits accumulated `state`, which may itself equal the
            // sentinel value, so it maintains its own EOF flag rather than
            // delegating to `source` (BUG-004).
            override def readDouble(sentinel: Double)(implicit ev: S <:< Double): Double =
              if (!emittedInit) { emittedInit = true; markReadValue(); init.asInstanceOf[Double] }
              else {
                val v = pullDouble(source, Double.MaxValue)
                if (doubleEOF(source, v, Double.MaxValue)) { markReadEOF(); sentinel }
                else { state = fD(state, v); markReadValue(); state }
              }
            def close(): Unit = source.close()
          }
        } else {
          new Reader[S] {
            private var state: S    = init
            private var emittedInit = false
            // Rewind source and local fold state so `repeated` restarts cleanly
            // (source first; a one-shot source's reset throws and propagates).
            override def reset(): Unit = { source.reset(); state = init; emittedInit = false }
            // The un-emitted `init` is still deliverable even over an empty/closed
            // source; a readable()-gated consumer loop must not drop it (BUG-R7-03).
            def isClosed: Boolean               = emittedInit && source.isClosed
            override def readable(): Boolean    = !emittedInit || source.readable()
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.read[Any](EndOfStream)
                if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
                else { state = f(state, v.asInstanceOf[A]); state.asInstanceOf[S1] }
              }
            def close(): Unit = source.close()
          }
        }
      },
      s"${this.render}.scan(...)"
    )

  /**
   * Emits sliding windows of size `n` as [[Chunk]]s. Each window advances by
   * `step` elements. The last window may be smaller than `n` if there are fewer
   * remaining elements.
   *
   * Unlike `take`/`grouped` (which take ''up to'' `n` and therefore grow their
   * buffer lazily), a sliding window's contract is a fixed-capacity buffer of
   * exactly `n` elements with fixed-modulus indexing, so the backing buffer is
   * allocated up front sized to `n`. A very large `n` therefore allocates a
   * correspondingly large buffer regardless of stream length — this is by
   * design, since `n` is the window capacity rather than an upper bound.
   */
  def sliding(n: Int, step: Int = 1): Stream[E, Chunk[A]] = {
    require(n >= 1 && step >= 1, s"sliding requires n >= 1 and step >= 1, got n=$n, step=$step")
    new Stream.FromReader[E, Chunk[A]](
      () => {
        val source = Stream.compileToReader(this)
        val et     = source.jvmType

        /**
         * Skip `count` elements from source using the appropriate primitive
         * read. Returns true if all skipped, false if source ended.
         */
        def skipElements(count: Int): Boolean = {
          var i = 0
          if (et eq JvmType.Byte) {
            while (i < count) {
              if (pullInt(source, Long.MinValue) == Long.MinValue) return false; i += 1
            }
          } else if (et eq JvmType.Int) {
            while (i < count) {
              if (pullInt(source, Long.MinValue) == Long.MinValue) return false; i += 1
            }
          } else if (et eq JvmType.Long) {
            while (i < count) {
              if (longEOF(source, pullLong(source, Long.MaxValue), Long.MaxValue)) return false; i += 1
            }
          } else if (et eq JvmType.Float) {
            while (i < count) {
              if (pullFloat(source, Double.MaxValue) == Double.MaxValue) return false; i += 1
            }
          } else if (et eq JvmType.Double) {
            while (i < count) {
              if (doubleEOF(source, pullDouble(source, Double.MaxValue), Double.MaxValue)) return false; i += 1
            }
          } else {
            while (i < count) {
              if (source.read[Any](EndOfStream).asInstanceOf[AnyRef] eq EndOfStream) return false; i += 1
            }
          }
          true
        }

        if (et eq JvmType.Byte) {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferByte(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toByte)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        } else if (et eq JvmType.Int) {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferInt(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = pullInt(source, Long.MinValue)
                if (v == Long.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toInt)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        } else if (et eq JvmType.Long) {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferLong(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = pullLong(source, Long.MaxValue)
                if (longEOF(source, v, Long.MaxValue)) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        } else if (et eq JvmType.Float) {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferFloat(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = pullFloat(source, Double.MaxValue)
                if (v == Double.MaxValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toFloat)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        } else if (et eq JvmType.Double) {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferDouble(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = pullDouble(source, Double.MaxValue)
                if (doubleEOF(source, v, Double.MaxValue)) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        } else {
          new Reader[Chunk[A]] {
            private val buf                            = new internal.CircularBufferRef(n)
            private var firstWindow                    = true
            private var done                           = false
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) {
                  buf.shift(step)
                } else {
                  buf.shift(n)
                  if (!skipElements(step - n)) { done = true; return sentinel }
                }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = source.read[Any](EndOfStream)
                if (v.asInstanceOf[AnyRef] eq EndOfStream) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk[A].asInstanceOf[A1] }
                }
                buf.add(v.asInstanceOf[AnyRef])
              }
              buf.toChunk[A].asInstanceOf[A1]
            }
            // Rewind source and local window state so `repeated` restarts
            // cleanly (source first; a one-shot source's reset throws and
            // propagates). `shift(size)` empties the circular buffer.
            override def reset(): Unit = { source.reset(); buf.shift(buf.size); firstWindow = true; done = false }
            def close(): Unit          = { done = true; source.close() }
          }
        }
      },
      s"${this.render}.sliding($n, $step)"
    )
  }

  /**
   * Opens this stream for manual pull-based consumption within a
   * [[zio.blocks.scope.Scope]]. Use this when you need element-by-element
   * control rather than running through a [[Sink]]. The returned `Reader` is
   * closed automatically when the scope closes.
   */
  def start(implicit scope: Scope): scope.$[Reader[A]] =
    scope.allocate(Resource.acquireRelease(compile(0, Stream.DefaultBufferSize))(_.close()))

  /** Emits at most the first `n` elements, then closes. */
  def take(n: Long): Stream[E, A] = new Stream.Taken(this, n)

  /**
   * Decouples upstream production from downstream consumption with a bounded
   * buffer of `n` elements.
   */
  def buffer(n: Int): Stream[E, A] = {
    require(n >= 1, s"buffer requires n >= 1, got n=$n")
    new Stream.Buffered(this, n)
  }

  /**
   * Emits elements while `pred` holds, then closes on the first element where
   * `pred` returns `false`.
   */
  def takeWhile(pred: A => Boolean): Stream[E, A] =
    new Stream.TakenWhile(this, pred)

  /**
   * Applies `f` to each element for side-effects, passing the element through.
   *
   * Fusion note: a later `drop`/`skip` may be pushed below this operator for
   * performance, in which case `f` is never invoked for skipped elements (see
   * [[Stream#drop]]). `f` must not rely on being called once per upstream
   * element.
   */
  def tapEach(f: A => Unit)(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    map { a => f(a); a }

  /** Transforms this stream by applying a [[Pipeline]]. */
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B] =
    pipe.applyToStream(this)

  /** Compiles this stream into a [[Reader]] for pull-based evaluation. */
  private[streams] def compile(depth: Int, bufferSize: Int): Reader[A]

  private[streams] def compile(depth: Int): Reader[A] = compile(depth, Stream.DefaultBufferSize)

  /** Compiles this stream description into the given [[Interpreter]]. */
  private[streams] def compileInterpreter(pipeline: Interpreter): Unit
}

/**
 * Companion object for [[Stream]]. Provides factory constructors:
 *
 *   - '''Values''': [[Stream.succeed succeed]], [[Stream.fail fail]],
 *     [[Stream.empty empty]], [[Stream.die die]]
 *   - '''Collections''': [[Stream.fromChunk fromChunk]],
 *     [[Stream.fromIterable fromIterable]], [[Stream.apply apply(as: A*)]]
 *   - '''Ranges''': [[Stream.range range]], [[Stream.fromRange fromRange]]
 *   - '''Generators''': [[Stream.repeat repeat]], [[Stream.unfold unfold]]
 *   - '''Side-effects''': [[Stream.eval eval]],
 *     [[Stream.attemptEval attemptEval]], [[Stream.attempt attempt]],
 *     [[Stream.defer defer]]
 *   - '''I/O''': [[Stream.fromInputStream fromInputStream]],
 *     [[Stream.fromJavaReader fromJavaReader]]
 *   - '''Resources''': [[Stream.fromAcquireRelease fromAcquireRelease]],
 *     [[Stream.fromResource fromResource]]
 *   - '''Advanced''': [[Stream.fromReader fromReader]],
 *     [[Stream.suspend suspend]], [[Stream.flattenAll flattenAll]]
 */
object Stream {

  /** Creates a stream from the given elements. */
  def apply[A](as: A*)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] = {
    val label =
      if (as.isEmpty) "Stream()"
      else if (as.length <= 5) s"Stream(${as.mkString(", ")})"
      else s"Stream(${as.take(5).mkString(", ")}, ...)"
    val chunk = Chunk.fromIterable(as)
    // Expose the O(1) metadata API: `Stream(...)` is backed by a known chunk,
    // so both `knownChunk` and `knownLength` are known without consuming the
    // stream (AdversarialMetadataSpec).
    new FromReader(() => Reader.fromChunk[A](chunk)(jt), label) {
      override def knownChunk: Option[Chunk[A]] = Some(chunk)
      override def knownLength: Option[Long]    = Some(chunk.length.toLong)
    }
  }

  /**
   * Evaluates `f` and emits its result; captures non-fatal exceptions as
   * errors.
   */
  def attempt[A](f: => A): Stream[Throwable, A] =
    suspend {
      try succeed(f)
      catch { case t if isCatchable(t) => fail(t) }
    }

  /**
   * An empty stream that executes `f` for its side-effect, capturing any
   * non-fatal throwable as a typed stream error rather than a defect.
   */
  def attemptEval(f: => Any): Stream[Throwable, Nothing] =
    suspend {
      try { f; empty }
      catch { case t if isCatchable(t) => fail(t) }
    }

  /**
   * An empty stream that registers `f` as a release action to be executed when
   * the stream is closed.
   */
  def defer(f: => Unit): Stream[Nothing, Nothing] =
    new FromReader[Nothing, Nothing](() => Reader.closed.withRelease(() => f), "Stream.defer(...)")

  /** Creates a stream that immediately dies with the given throwable. */
  def die(t: Throwable): Stream[Nothing, Nothing] =
    new FromReader(() => new DyingReader(t), "Stream.die(...)")

  /** The empty stream that emits no elements. */
  val empty: Stream[Nothing, Nothing] = new FromReader(() => Reader.closed, "Stream.empty") {
    override def knownChunk: Option[Chunk[Nothing]] = Some(Chunk.empty)
    override def knownLength: Option[Long]          = Some(0L)
  }

  /** An empty stream that executes `f` for its side-effect when pulled. */
  def eval(f: => Any): Stream[Nothing, Nothing] =
    suspend { f; empty }

  /** Creates a stream that immediately fails with `error`. */
  def fail[E](error: E): Stream[E, Nothing] =
    new FromReader(() => new FailedReader(new StreamError(error)), "Stream.fail(...)")

  /**
   * Flattens a stream of streams into a single stream by sequencing. The outer
   * stream's error `EOuter` and the inner streams' error `EInner` widen to
   * their [[Concat]] (a union on Scala 3; the least upper bound or `Either` on
   * Scala 2).
   */
  def flattenAll[EOuter, EInner, EOut, A](streams: Stream[EOuter, Stream[EInner, A]])(implicit
    errorConcat: Concat.WithOut[EOuter, EInner, EOut]
  ): Stream[EOut, A] =
    streams.flatMap(identity)

  /**
   * Merges up to `maxOpen` inner streams concurrently into a single output
   * stream. Elements arrive in completion order (unordered with respect to
   * input position). On JVM, each inner stream runs on a virtual thread. On JS,
   * degrades to sequential flatMap.
   *
   * @param maxOpen
   *   maximum number of concurrently active inner streams
   * @param streams
   *   a stream of inner streams to merge
   */
  def mergeAll[E, A](maxOpen: Int)(streams: Stream[E, Stream[E, A]])(implicit
    jtA: JvmType.Infer[A]
  ): Stream[E, A] = {
    require(maxOpen >= 1, s"mergeAll requires maxOpen >= 1, got $maxOpen")
    new Stream.MergedAll[E, A](streams, maxOpen, jtA.jvmType)
  }

  def bufferSize[E, A](n: Int)(body: => Stream[E, A]): Stream[E, A] = {
    require(n >= 1 && (n & (n - 1)) == 0, s"bufferSize must be a positive power of 2, got $n")
    new Stream.WithBufferSize(body, n)
  }

  /** Creates a resource-safe stream: acquires `R`, uses it, then releases. */
  def fromAcquireRelease[R, E, A](
    acquire: => R,
    release: R => Unit = (r: R) => r match { case ac: AutoCloseable => ac.close(); case _ => () }
  )(use: R => Stream[E, A]): Stream[E, A] =
    new FromAcquireRelease(acquire, release, use)

  /**
   * Creates a stream from an array. The array is wrapped via `Chunk.fromArray`
   * without copying.
   */
  def fromArray[A](array: Array[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    fromChunk(Chunk.fromArray(array))

  /** Creates a stream backed by a `Chunk`. */
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromChunkStream(chunk, jt)

  /**
   * Wraps a [[java.io.InputStream]] as a stream of bytes. Closes the stream
   * when done.
   */
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Byte] =
    // Let a close `IOException` propagate (with suppression) via
    // `fromAcquireRelease` rather than swallowing it (Principle 4).
    fromAcquireRelease(is, (s: java.io.InputStream) => s.close())(s => fromInputStreamUnmanaged(s))

  /**
   * Wraps a [[java.io.InputStream]] as a stream of bytes without managing its
   * lifecycle. The caller is responsible for closing the stream.
   */
  def fromInputStreamUnmanaged(is: java.io.InputStream): Stream[java.io.IOException, Byte] =
    new FromReader(() => Reader.fromInputStream(is), "Stream.fromInputStreamUnmanaged(...)")

  /**
   * Creates a stream backed by an `Iterable`. If the iterable has a known size,
   * `knownLength` is set.
   */
  def fromIterable[A](it: Iterable[A]): Stream[Nothing, A] = {
    val size = it.knownSize
    if (size >= 0)
      new FromReader(() => Reader.fromIterable[A](it), "Stream.fromIterable(...)") {
        override def knownLength: Option[Long] = Some(size.toLong)
      }
    else
      new FromReader(() => Reader.fromIterable[A](it), "Stream.fromIterable(...)")
  }

  /**
   * Creates a stream from a lazily-evaluated `Iterator`.
   *
   * The argument is by-name so the stream can be replayed (e.g. under
   * [[Stream.repeated]]): each restart re-evaluates the expression to obtain a
   * fresh iterator. This only replays correctly when the expression *produces*
   * a fresh iterator each time (e.g. `Stream.fromIterator(xs.iterator)`). If
   * you pass an already-materialized iterator value (`val it = xs.iterator;
   * Stream.fromIterator(it)`), re-evaluation yields the same, now-exhausted
   * iterator and replays as empty.
   */
  def fromIterator[A](it: => Iterator[A]): Stream[Nothing, A] =
    new FromReader(
      () =>
        new Reader[A] {
          private var iter                    = it
          private var exhausted               = false
          def isClosed: Boolean               = exhausted || !iter.hasNext
          def read[A1 >: A](sentinel: A1): A1 =
            if (!exhausted && iter.hasNext) iter.next().asInstanceOf[A1]
            else { exhausted = true; sentinel }
          def close(): Unit          = exhausted = true
          override def reset(): Unit = {
            iter = it
            exhausted = false
          }
        },
      "Stream.fromIterator(...)"
    )

  /**
   * Wraps a [[java.io.Reader]] as a stream of Chars. Closes the reader when
   * done.
   */
  def fromJavaReader(r: java.io.Reader): Stream[java.io.IOException, Char] =
    // Let a close `IOException` propagate (with suppression) via
    // `fromAcquireRelease` rather than swallowing it (Principle 4).
    fromAcquireRelease(r, (w: java.io.Reader) => w.close())(w => fromJavaReaderUnmanaged(w))

  /**
   * Wraps a [[java.io.Reader]] as a stream of Chars without managing its
   * lifecycle. The caller is responsible for closing.
   */
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[java.io.IOException, Char] =
    new FromReader(() => Reader.fromReader(r), "Stream.fromJavaReaderUnmanaged(...)")

  /**
   * Creates a stream of integers from a Scala `Range`. The `knownLength` is set
   * from the range size.
   */
  def fromRange(range: Range): Stream[Nothing, Int] =
    new FromReader(() => Reader.fromRange(range), "Stream.fromRange(...)") {
      override def knownLength: Option[Long] = Some(range.size.toLong)
    }

  /** Creates a stream from a lazily-evaluated `Reader` (advanced API). */
  def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A] =
    new FromReader(() => mkReader)

  /**
   * Creates a resource-safe stream from a `Resource` managed by a [[Scope]].
   */
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A] =
    new FromResource(resource, use)

  /**
   * Creates a stream of integers from `from` (inclusive) to `until`
   * (exclusive).
   */
  def range(from: Int, until: Int): Stream[Nothing, Int] =
    new RangedStream(from, until)

  /** Creates an infinite stream that always emits `a`. */
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromReader(() => Reader.repeat[A](a), "Stream.repeat(...)")

  // The primitive `succeed` overloads mirror the `Reader.single*` raw-bits
  // encodings exactly (value widened/raw-bit-cast to Long + JvmType ordinal);
  // `SucceedPrim.compile` reconstructs the identical `SingletonPrim`.

  /** Creates a stream that emits a single Boolean. */
  def succeed(a: Boolean): Stream[Nothing, Boolean] =
    new SucceedPrim[Boolean](if (a) 1L else 0L, JvmType.Boolean.ordinal)

  /**
   * Creates a stream that emits a single Byte. Returns `Stream[Nothing, Int]`
   * because Byte is represented as Int internally to avoid boxing, matching the
   * convention of [[fromInputStream]].
   */
  def succeed(a: Byte): Stream[Nothing, Int] =
    new SucceedPrim[Int]((a & 0xff).toLong, JvmType.Int.ordinal)

  /** Creates a stream that emits a single Char. */
  def succeed(a: Char): Stream[Nothing, Char] =
    new SucceedPrim[Char](a.toLong, JvmType.Char.ordinal)

  /** Creates a stream that emits a single Double. */
  def succeed(a: Double): Stream[Nothing, Double] =
    new SucceedPrim[Double](java.lang.Double.doubleToRawLongBits(a), JvmType.Double.ordinal)

  /** Creates a stream that emits a single Float. */
  def succeed(a: Float): Stream[Nothing, Float] =
    new SucceedPrim[Float](java.lang.Float.floatToRawIntBits(a).toLong, JvmType.Float.ordinal)

  /** Creates a stream that emits a single Int. */
  def succeed(a: Int): Stream[Nothing, Int] =
    new SucceedPrim[Int](a.toLong, JvmType.Int.ordinal)

  /** Creates a stream that emits a single Long. */
  def succeed(a: Long): Stream[Nothing, Long] =
    new SucceedPrim[Long](a, JvmType.Long.ordinal)

  /** Creates a stream that emits a single Short. */
  def succeed(a: Short): Stream[Nothing, Short] =
    new SucceedPrim[Short](a.toLong, JvmType.Short.ordinal)

  /** Creates a stream that emits a single element `a`, then closes. */
  def succeed[A](a: A): Stream[Nothing, A] =
    new FromReader(() => Reader.single[A](a)(JvmType.Infer.anyRef), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /**
   * Defers stream construction until run-time. Useful for recursive streams.
   */
  def suspend[E, A](stream: => Stream[E, A]): Stream[E, A] =
    new Deferred(() => stream)

  /** Creates a stream by unfolding state `s` with `f` until `None`. */
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Stream[Nothing, A] =
    new FromReader(() => Reader.unfold[S, A](s)(f), "Stream.unfold(...)")

  /** Compiles a stream for pull-based evaluation. */
  private[streams] def compileToReader[E, A](stream: Stream[E, A]): Reader[A] =
    stream.compile(0, Stream.DefaultBufferSize)

  /**
   * Mixed into unary stream nodes whose interpreter compilation has the shape
   * `upstream.compileInterpreter(pipeline); <append one op>`. Instead of
   * recursing (which overflows the JVM stack for long linear chains — BUG-C /
   * AdversarialCompileStackSafetySpec), each such node delegates its
   * `compileInterpreter` to [[compileInterpreterStackSafe]], which walks the
   * `upstream` spine on an explicit heap stack and applies the deferred
   * `compileOp`s in post-order (innermost first), exactly matching the original
   * recursive order.
   */
  private[streams] trait LinearStream { self: Stream[_, _] =>
    private[streams] def upstream: Stream[_, _]
    private[streams] def compileOp(pipeline: Interpreter): Unit

    /**
     * True for nodes whose `compileOp` either rewrites the segment's last read
     * (`wrapLastRead`: take/drop/takeWhile/concat/ensuring/mapError/repeated)
     * or starts a `flatMap` push. In the segmented compiler these must operate
     * on the fully-fused upstream prefix, so a fresh segment is sealed before
     * them (matching the shallow `compile` path, where the wrapper sees the
     * whole upstream reader rather than just the source read op).
     */
    private[streams] def isSealBefore: Boolean = false

    final private[streams] def compileInterpreter(pipeline: Interpreter): Unit =
      compileInterpreterStackSafe(this, pipeline)
  }

  /**
   * Walks a [[LinearStream]] spine iteratively, returning the deferred
   * post-order `compileOp` nodes (innermost first) in `posts(0 until postLen)`
   * and the non-linear boundary at the bottom of the spine. Shared by the
   * inline ([[compileInterpreterStackSafe]]) and segmented
   * ([[compileInterpreterSegmented]]) drivers.
   */
  private def collectLinearSpine(start: Stream[_, _]): (Array[LinearStream], Int, Stream[_, _]) = {
    // Pass 1: count the spine (allocation-free) so the node array is allocated
    // exactly once — a deep spine (10k+) otherwise pays O(log n) doubling
    // copies.
    var postLen           = 0
    var cur: Stream[_, _] = start
    while (cur.isInstanceOf[LinearStream]) {
      postLen += 1
      cur = cur.asInstanceOf[LinearStream].upstream
    }
    val boundary = cur
    // Pass 2: fill the exact-size array.
    val posts = new Array[LinearStream](postLen)
    cur = start
    var i = 0
    while (i < postLen) {
      val ls = cur.asInstanceOf[LinearStream]
      posts(i) = ls
      i += 1
      cur = ls.upstream
    }
    (posts, postLen, boundary)
  }

  /**
   * Stack-safe INLINE driver for [[LinearStream]] chains: compiles the whole
   * spine into the single given `pipeline` (used when filling an existing
   * interpreter, e.g. a `flatMap` inner stream). Descends the spine on a heap
   * stack, compiles the boundary, then applies the deferred ops post-order.
   * Bounded by the interpreter's `MaxIndex` (segmentation is the unbounded
   * path).
   *
   * A seal-before node (take/drop/takeWhile/concat/…/flatMap-push) sitting
   * ABOVE already-fused ops cannot be compiled inline: its `compileOp` wraps
   * only the segment's last READ, so it would be re-ordered below the ops fused
   * after that read, producing silently wrong elements (BUG-A01). The segmented
   * driver seals a fresh segment before such nodes, but here the pipeline is
   * shared with the outer stream and cannot be sealed — so the whole spine is
   * compiled through the shallow reference path and appended as a single read
   * instead. Compile-time decision only; safe spines (the common case) are
   * unaffected.
   */
  private[streams] def compileInterpreterStackSafe(start: Stream[_, _], pipeline: Interpreter): Unit = {
    val (posts, postLen, boundary) = collectLinearSpine(start)
    // Unsafe shape: a seal-before node with at least one fusing
    // (non-seal-before) node between it and the boundary. Walk innermost
    // (postLen - 1) outward.
    var unsafe     = false
    var seenFusing = false
    var k          = postLen - 1
    while (k >= 0 && !unsafe) {
      if (posts(k).isSealBefore) unsafe = seenFusing
      else seenFusing = true
      k -= 1
    }
    if (unsafe) {
      pipeline.appendRead(start.compile(0, Stream.DefaultBufferSize))
      var j = 0
      while (j < postLen) { posts(j) = null; j += 1 }
      return
    }
    boundary.compileInterpreter(pipeline)
    var i = postLen - 1
    while (i >= 0) {
      posts(i).compileOp(pipeline)
      posts(i) = null
      i -= 1
    }
  }

  /**
   * Stack-safe, capacity-safe SEGMENTED compiler for deep linear pipelines
   * (BUG-C). A single [[Interpreter]] can address at most `MaxIndex` ops, so a
   * very deep spine is split into a CHAIN of bounded interpreter segments, each
   * reading from the previous (sealed) one. The chain is short (≈ depth /
   * `SegmentBudget`) and every segment is a flat loop, so this is stack-safe at
   * both compile and run time. A new segment is sealed:
   *   - before a `seal-before` node (so take/drop/concat/etc. wrap the fully
   *     fused upstream prefix, and a `flatMap` push starts cleanly), and
   *   - after any node once the segment reaches `SegmentBudget`.
   * Returns the final segment (itself a `Reader`).
   */
  private[streams] def compileInterpreterSegmented(start: Stream[_, _]): Interpreter = {
    val (posts, postLen, boundary) = collectLinearSpine(start)
    var current                    = Interpreter.unsealed()
    // Presize for the ops this segment will receive: the remaining spine ops
    // (capped by the interpreter's index budget internally) plus slack for
    // runtime-pushed transient reads (deep flatMap chains stack one per active
    // inner). Skips O(log n) doubling copies per segment on deep pipelines.
    current.presize(postLen + 9)
    boundary.compileInterpreter(current)

    def sealAndChain(remaining: Int): Unit = {
      current.seal()
      val prev = current
      current = Interpreter.unsealed()
      current.presize(remaining + 9)
      current.appendRead(prev)
    }

    var i = postLen - 1
    while (i >= 0) {
      val node = posts(i)
      // Seal-before barriers/pushes so they see the fused upstream as one reader
      // (skip when the segment is already just a single read — nothing to fuse).
      if (node.isSealBefore && current.incomingCount > 1) sealAndChain(i + 1)
      node.compileOp(current)
      // Seal-after on budget to keep each segment within the interpreter's
      // 13-bit op-index cap. This applies even right after a push: `addPush`
      // sets the segment's output lane to the flatMap's declared B lane, so a
      // freshly-sealed post-push segment reads the correct lane. Without this a
      // deep flatMap chain (> ~8000) overflows `outgoingLen` (AdversarialDeepFlatMapSpec).
      if (current.incomingCount >= Interpreter.SegmentBudget || current.outgoingCount >= Interpreter.SegmentBudget)
        sealAndChain(i)
      posts(i) = null
      i -= 1
    }
    current.seal()
    current
  }

  /**
   * Stack-safe READER compiler for a left-nested concat spine (the depth-cutoff
   * fallback for [[Concatenated.compile]]). Walks the `self` spine iteratively
   * (no recursion → no stack overflow at depth), compiles the leftmost
   * non-concat source once, then appends each `that` side innermost-first via
   * `concatRaw`. Because `concatRaw` appends in place when the output lane
   * matches, a homogeneous spine collapses to a single flat `ConcatReader` with
   * NO surrounding `Interpreter` — avoiding the per-element interpreter
   * dispatch that `Interpreter.fromStream` would impose on a pure concat chain.
   * The `that` sides stay lazy (compiled inside thunks), preserving concat
   * laziness and matching the shallow recursive path's semantics exactly.
   */
  private[streams] def compileConcatSpine(
    start: Concatenated[_, _],
    depth: Int,
    bufferSize: Int
  ): Reader[_] = {
    // Pass 1: count the spine (no allocation) so pass 2 can use an exact-size
    // array — a deep spine (10k+) otherwise pays O(log n) doubling copies.
    var len               = 0
    var cur: Stream[_, _] = start
    while (cur.isInstanceOf[Concatenated[_, _]]) {
      len += 1
      cur = cur.asInstanceOf[Concatenated[_, _]].self
    }
    // Pass 2: collect the nodes innermost-last (walk is outer->inner, append
    // order below is inner->outer).
    val nodes = new Array[Concatenated[_, _]](len)
    cur = start
    var k = 0
    while (k < len) {
      val c = cur.asInstanceOf[Concatenated[_, _]]
      nodes(k) = c
      k += 1
      cur = c.self
    }
    var r        = cur.asInstanceOf[Stream[Any, Any]].compile(depth, bufferSize).asInstanceOf[Reader[Any]]
    var i        = len - 1
    var presized = false
    while (i >= 0) {
      val c = nodes(i).asInstanceOf[Concatenated[Any, Any]]
      // Append the NOT-YET-COMPILED tail stream as the lazy entry (compiled at
      // segment-advance time — identical laziness, no per-node thunk).
      r = r.concatRawEntry(c.that, c.outJvmType)
      // Presize the tail array once, right after the flat ConcatReader first
      // appears (a homogeneous spine appends the remaining `i` entries to it
      // in place — one allocation instead of O(log n) doubling copies).
      if (!presized && r.isInstanceOf[Reader.ConcatReader[_]]) {
        presized = true
        r.asInstanceOf[Reader.ConcatReader[Any]].ensureTailCapacity(i)
      }
      nodes(i) = null
      i -= 1
    }
    r
  }

  // --------------------------------------------------------------------------
  //  Concat-based channel projection helpers
  //
  //  Composition combinators widen one or both of their channels to the output
  //  type chosen by `Concat` (`L | R` on Scala 3; LUB or `Either[L, R]` on
  //  Scala 2). When the chosen `Concat` is identity-like (Scala 3 unions, or a
  //  Scala 2 LUB) the runtime values already are valid output values, so we can
  //  reuse the stream unchanged. Otherwise (Scala 2 disjoint `Either`) the
  //  values must be projected into `Left` / `Right` via `mapError` / `map`.
  // --------------------------------------------------------------------------

  /** Projects the error channel of `s` onto the left of `errorConcat`. */
  private[streams] def widenErrorLeft[E, E2, E3, A](
    s: Stream[E, A],
    errorConcat: Concat.WithOut[E, E2, E3]
  ): Stream[E3, A] =
    if (errorConcat.isIdentityLike) s.asInstanceOf[Stream[E3, A]]
    else s.mapError(errorConcat.left)

  /** Projects the error channel of `s` onto the right of `errorConcat`. */
  private[streams] def widenErrorRight[E, E2, E3, A](
    s: Stream[E2, A],
    errorConcat: Concat.WithOut[E, E2, E3]
  ): Stream[E3, A] =
    if (errorConcat.isIdentityLike) s.asInstanceOf[Stream[E3, A]]
    else s.mapError(errorConcat.right)

  /** Projects the element channel of `s` onto the left of `valueConcat`. */
  private[streams] def widenElemLeft[E, A, A2, A3](
    s: Stream[E, A],
    valueConcat: Concat.WithOut[A, A2, A3]
  )(implicit jtA3: JvmType.Infer[A3]): Stream[E, A3] =
    if (valueConcat.isIdentityLike) s.asInstanceOf[Stream[E, A3]]
    else s.map(valueConcat.left)

  /** Projects the element channel of `s` onto the right of `valueConcat`. */
  private[streams] def widenElemRight[E, A, A2, A3](
    s: Stream[E, A2],
    valueConcat: Concat.WithOut[A, A2, A3]
  )(implicit jtA3: JvmType.Infer[A3]): Stream[E, A3] =
    if (valueConcat.isIdentityLike) s.asInstanceOf[Stream[E, A3]]
    else s.map(valueConcat.right)

  /**
   * Depth threshold; beyond this, compilation falls back to the flat-array
   * [[Interpreter]] to prevent stack overflow during recursive stream
   * compilation.
   */
  private[streams] val DepthCutoff       = 100
  private[streams] val DefaultBufferSize = 64

  /** Recovers from all errors by switching to the stream returned by `f`. */
  private[streams] final class CatchAll[E, E2, A](
    self: Stream[E, A],
    f: E => Stream[E2, A],
    outJvmType: JvmType
  ) extends Stream[E2, A] {
    def render: String                                                   = s"${self.render}.catchAll(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val innerReader = compileToReader(self)
      pipeline.appendRead(new CatchAllReader[E, A](innerReader.asInstanceOf[Reader[A]], f, outJvmType))
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      new CatchAllReader[E, A](self.compile(depth, bufferSize), f, outJvmType)
  }

  /** Catches StreamErrors and switches to a recovery stream. */
  private[streams] final class CatchAllReader[E, A](upstream: Reader[A], f: E => Stream[Any, A], outJvmType: JvmType)
      extends Reader[A] {
    private var current: Reader[_] = upstream
    private var switched           = false
    // True once the old `current` has been closed during a switch but not yet
    // replaced by a successfully-built recovery reader. Keeps `close()`/`reset()`
    // from finalizing the already-closed upstream a second time if the recovery
    // builder throws (double finalization).
    private var currentClosed     = false
    private val _jvmType: JvmType = outJvmType

    override def jvmType: JvmType = _jvmType
    def isClosed: Boolean         = current.isClosed

    private def doSwitch(e: StreamError): Unit = {
      switched = true
      // Mark `current` finalized BEFORE closing it. The `StreamError` is being
      // handled (recovered) here, so it will not propagate. If closing the old
      // reader fails, that close failure must abort recovery and become the
      // propagated throwable, with the handled error attached as suppressed
      // context (Principle 4). But even when `close()` throws, the already-closed
      // upstream must not be finalized a second time by the terminal cleanup
      // (double finalization), so the guard must be set first.
      currentClosed = true
      try current.close()
      catch {
        case closeFailure: Throwable =>
          addSuppressedSafe(closeFailure, e)
          throw closeFailure
      }
      try current = compileToReader(f(e.value.asInstanceOf[E]))
      catch {
        // Recovery construction failed. The already-closed upstream must not be
        // finalized again (handled by `currentClosed`), and the handled error
        // must not be lost: attach it to the build failure as suppressed context
        // (Principle: lossless errors).
        case buildFailure: Throwable =>
          addSuppressedSafe(buildFailure, e)
          throw buildFailure
      }
      currentClosed = false
    }

    private def cur: Reader[A] = current.asInstanceOf[Reader[A]]

    def read[A1 >: A](sentinel: A1): A1 =
      if (!switched) {
        try cur.read(sentinel)
        catch { case e: StreamError => doSwitch(e); cur.read(sentinel) }
      } else cur.read(sentinel)

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (!switched) {
        try pullInt(current, sentinel)
        catch { case e: StreamError => doSwitch(e); pullInt(current, sentinel) }
      } else pullInt(current, sentinel)

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (!switched) {
        try pullLong(current, sentinel)
        catch { case e: StreamError => doSwitch(e); pullLong(current, sentinel) }
      } else pullLong(current, sentinel)

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (!switched) {
        try pullFloat(current, sentinel)
        catch { case e: StreamError => doSwitch(e); pullFloat(current, sentinel) }
      } else pullFloat(current, sentinel)

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (!switched) {
        try pullDouble(current, sentinel)
        catch { case e: StreamError => doSwitch(e); pullDouble(current, sentinel) }
      } else pullDouble(current, sentinel)

    // Bulk-delegating to `cur.readUpToN` would silently lose the prefix the
    // upstream buffered before a mid-chunk failure (BUG-A02). Pull
    // element-wise through this reader's recovery-aware pulls instead (lane
    // dispatch keeps primitive lanes unboxed); an error mid-loop switches to
    // the recovery stream and keeps filling the same chunk, matching
    // `runCollect` semantics. After the switch, plain delegation is safe.
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] =
      if (!switched) elementWiseReadUpToN(this, n).asInstanceOf[Chunk[A1]]
      else cur.readUpToN(n)

    // Route skips through this reader's own `read*` path so a `StreamError`
    // encountered while dropping still triggers recovery via `doSwitch`.
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    // No throwable is in flight here, so a close failure is the primary and
    // must propagate (Principle 4).
    // This reader passes `current`'s elements through unchanged, so EOF
    // disambiguation delegates to whichever reader it last pulled from (BUG-004).
    override def lastReadWasEOF: Boolean = current.lastReadWasEOF
    // Whole-stream restart (so `xs.catchAll(...).repeated` and the `&&` zip
    // restart work, like every other stateless transform): close the recovery
    // reader once if we had switched, then rewind `upstream` and return to the
    // un-switched state. `currentClosed` is reset so the next cycle finalizes
    // cleanly exactly once (ITER-4).
    override def reset(): Unit = {
      if (switched && !currentClosed) { current.close(); currentClosed = true }
      upstream.reset()
      current = upstream
      switched = false
      currentClosed = false
    }
    // Idempotent: skips a `current` already closed by a failed switch or by an
    // enclosing combinator (concat advance / zip eager-close) before `reset()`,
    // and SETS `currentClosed` so a subsequent reset()/close() does not
    // double-finalize the recovery reader (ITER-5a).
    def close(): Unit = if (!currentClosed) { current.close(); currentClosed = true }
  }

  /** Recovers from non-fatal defects matching `f`. */
  private[streams] final class CatchDefect[E, A](
    self: Stream[E, A],
    f: PartialFunction[Throwable, Stream[E, A]],
    outJvmType: JvmType
  ) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.catchDefect(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val innerReader = compileToReader(self)
      pipeline.appendRead(new CatchDefectReader[E, A](innerReader.asInstanceOf[Reader[A]], f, outJvmType))
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      new CatchDefectReader[E, A](self.compile(depth, bufferSize), f, outJvmType)
  }

  /** Catches non-fatal defects and switches to a recovery stream. */
  private[streams] final class CatchDefectReader[E, A](
    upstream: Reader[A],
    f: PartialFunction[Throwable, Stream[E, A]],
    outJvmType: JvmType
  ) extends Reader[A] {
    private var current: Reader[_] = upstream
    private var switched           = false
    // See `CatchAllReader`: prevents double finalization of the already-closed
    // upstream when the recovery builder throws.
    private var currentClosed     = false
    private val _jvmType: JvmType = outJvmType

    override def jvmType: JvmType = _jvmType
    def isClosed: Boolean         = current.isClosed

    private def cur: Reader[A] = current.asInstanceOf[Reader[A]]

    private def trySwitch(t: Throwable): Boolean =
      // `isCatchable` already excludes control signals (`StreamError`,
      // `SinkError`) and fatal errors, so only genuine recoverable defects
      // reach `f` (Principle 2).
      if (isCatchable(t) && f.isDefinedAt(t)) {
        switched = true
        // Mark `current` finalized BEFORE closing it. `t` is being handled
        // (recovered) here, so it will not propagate. If closing the old reader
        // fails, that close failure must abort recovery and become the
        // propagated throwable, with `t` attached as suppressed context
        // (Principle 4). But even when `close()` throws, the already-closed
        // upstream must not be finalized a second time by the terminal cleanup
        // (double finalization), so the guard must be set first.
        currentClosed = true
        try current.close()
        catch {
          case closeFailure: Throwable =>
            addSuppressedSafe(closeFailure, t)
            throw closeFailure
        }
        try current = compileToReader(f(t))
        catch {
          // Recovery construction failed: do not finalize the already-closed
          // upstream again, and preserve the handled defect by attaching it to
          // the build failure (Principle: lossless errors).
          case buildFailure: Throwable =>
            addSuppressedSafe(buildFailure, t)
            throw buildFailure
        }
        currentClosed = false
        true
      } else false

    def read[A1 >: A](sentinel: A1): A1 =
      if (!switched) {
        try cur.read(sentinel)
        catch { case t: Throwable => if (trySwitch(t)) cur.read(sentinel) else throw t }
      } else cur.read(sentinel)

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (!switched) {
        try pullInt(current, sentinel)
        catch { case t: Throwable => if (trySwitch(t)) pullInt(current, sentinel) else throw t }
      } else pullInt(current, sentinel)

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (!switched) {
        try pullLong(current, sentinel)
        catch { case t: Throwable => if (trySwitch(t)) pullLong(current, sentinel) else throw t }
      } else pullLong(current, sentinel)

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (!switched) {
        try pullFloat(current, sentinel)
        catch { case t: Throwable => if (trySwitch(t)) pullFloat(current, sentinel) else throw t }
      } else pullFloat(current, sentinel)

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (!switched) {
        try pullDouble(current, sentinel)
        catch { case t: Throwable => if (trySwitch(t)) pullDouble(current, sentinel) else throw t }
      } else pullDouble(current, sentinel)

    // See `CatchAllReader.readUpToN`: element-wise pulls preserve the prefix
    // a bulk upstream read would lose on a mid-chunk failure (BUG-A02).
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] =
      if (!switched) elementWiseReadUpToN(this, n).asInstanceOf[Chunk[A1]]
      else cur.readUpToN(n)

    // Route skips through this reader's own `read*` path so a recoverable defect
    // encountered while dropping still triggers recovery via `trySwitch`.
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    // No throwable is in flight here, so a close failure is the primary and
    // must propagate (Principle 4).
    // This reader passes `current`'s elements through unchanged, so EOF
    // disambiguation delegates to whichever reader it last pulled from (BUG-004).
    override def lastReadWasEOF: Boolean = current.lastReadWasEOF
    // Whole-stream restart (ITER-4): see `CatchAllReader.reset`.
    override def reset(): Unit = {
      if (switched && !currentClosed) { current.close(); currentClosed = true }
      upstream.reset()
      current = upstream
      switched = false
      currentClosed = false
    }
    // Idempotent and SETS `currentClosed` to avoid double-finalizing the
    // recovery reader when an enclosing combinator closed it before reset()
    // (ITER-5a).
    def close(): Unit = if (!currentClosed) { current.close(); currentClosed = true }
  }

  /** Emits all elements of `self` followed by all elements of `that`. */
  private[streams] final class Concatenated[E, A](
    private[streams] val self: Stream[E, A],
    private[streams] val that: Stream[E, A],
    private[streams] val outJvmType: JvmType
  ) extends Stream[E, A]
      with LinearStream {
    def render: String                     = s"${self.render} ++ ${that.render}"
    override def knownLength: Option[Long] =
      for { a <- self.knownLength; b <- that.knownLength } yield a + b
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead { src =>
        src.asInstanceOf[Reader[A]].concatRaw(() => compileToReader(that), outJvmType)
      }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff)
        // A left-nested concat spine compiles to a flat ConcatReader (concatRaw
        // appends in place when the output lane matches). Walking the `self`
        // spine iteratively here keeps that flattening stack-safe at the depth
        // cutoff WITHOUT wrapping the whole spine in an Interpreter — the
        // interpreter wrapper added measurable per-element overhead on long
        // concat chains (nested_concat). Mixed spines (e.g. `s.map(f) ++ t`)
        // are still correct: the loop stops at the first non-Concatenated
        // `self`, which is compiled independently before the `that` sides are
        // appended.
        Stream.compileConcatSpine(this, depth, bufferSize).asInstanceOf[Reader[A]]
      else {
        val r1 = self.compile(depth + 1, bufferSize)
        r1.concatRaw(() => that.compile(depth + 1, bufferSize), outJvmType)
      }
  }

  /** Defers stream construction until run-time. */
  private[streams] final class Deferred[E, A](mkStream: () => Stream[E, A]) extends Stream[E, A] {
    def render: String                                                            = "Stream.suspend(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit          = mkStream().compileInterpreter(pipeline)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      mkStream().compile(depth, bufferSize)
  }

  /** Skips the first `n` elements. */
  private[streams] final class Dropped[E, A](self: Stream[E, A], n: Long) extends Stream[E, A] with LinearStream {
    def render: String                                  = s"${self.render}.drop($n)"
    override def knownLength: Option[Long]              = self.knownLength.map(l => math.max(0L, l - math.max(0L, n)))
    private[streams] def upstream: Stream[_, _]         = self
    override private[streams] def isSealBefore: Boolean = true
    // Fallback wraps in a persistent skip (rather than an eager one-shot
    // `r.skip(n)`) so the drop is re-applied after `reset()` (repeat-correct)
    // and so it composes correctly with a previously fused `take` (BUG-B).
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(r => if (r.setSkip(n)) r else Reader.withSkipLimit(r, n, Long.MaxValue))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      val r = self.compile(depth + 1, bufferSize)
      (if (r.setSkip(n)) r else Reader.withSkipLimit(r, n, Long.MaxValue)).asInstanceOf[Reader[A]]
    }
  }

  /** Buffers up to `n` elements between upstream and downstream. */
  private[streams] final class Buffered[E, A](self: Stream[E, A], n: Int) extends Stream[E, A] {
    def render: String = s"${self.render}.buffer($n)"

    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val upstreamReader = compileToReader(self)
      val bufferedReader = Platform.createBufferedReader(upstreamReader, n)
      pipeline.appendRead(bufferedReader)
    }

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      Platform.createBufferedReader(self.compile(depth, bufferSize), n)
  }

  private[streams] final class WithBufferSize[E, A](inner: Stream[E, A], n: Int) extends Stream[E, A] {
    def render: String                                                            = s"Stream.bufferSize($n)(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit          = inner.compileInterpreter(pipeline)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = inner.compile(depth, n)
  }

  private[streams] final class MapPar[E, A, B](
    self: Stream[E, A],
    n: Int,
    f: A => B,
    inType: JvmType,
    outType: JvmType
  ) extends Stream[E, B] {

    def render: String = s"${self.render}.mapPar($n)(...)"

    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val upstream = compileToReader(self)
      val par      = Platform.createMapParReader[A, B](upstream, n, f, Stream.DefaultBufferSize, inType, outType)
      pipeline.appendRead(par)
    }

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      val upstream = self.compile(depth, bufferSize)
      Platform.createMapParReader[A, B](upstream, n, f, bufferSize, inType, outType)
    }
  }

  private[streams] final class MergedAll[E, A](
    outerStream: Stream[E, Stream[E, A]],
    maxOpen: Int,
    elemType: JvmType
  ) extends Stream[E, A] {

    def render: String = s"Stream.mergeAll($maxOpen)(...)"

    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val outerReader = compileToReader(outerStream)
      val merged      = Platform.createMergeReader[A](outerReader, maxOpen, Stream.DefaultBufferSize, elemType)
      pipeline.appendRead(merged)
    }

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      val outerReader = outerStream.compile(depth, bufferSize)
      Platform.createMergeReader[A](outerReader, maxOpen, bufferSize, elemType)
    }
  }

  /** A dying stream source that always throws the given Throwable. */
  private[streams] final class DyingReader(t: Throwable) extends Reader[Nothing] {
    def isClosed: Boolean                     = false
    def read[A1 >: Nothing](sentinel: A1): A1 = throw t
    override def readByte(): Int              = throw t
    override def skip(n: Long): Unit          = ()
    def close(): Unit                         = ()
    // Stateless: re-throws the same defect on every pull, so reset is a no-op.
    override def reset(): Unit = ()
  }

  /** Wraps a stream with a finalizer that runs on close. */
  private[streams] final class Ensuring[E, A](self: Stream[E, A], finalizer: => Unit)
      extends Stream[E, A]
      with LinearStream {
    def render: String                                          = s"${self.render}.ensuring(...)"
    override def knownLength: Option[Long]                      = self.knownLength
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(src =>
        new Reader.DelegatingReader[Any](src) {
          // `ensuring` is a per-CLOSE hook (unlike fromAcquireRelease's
          // acquisition-bound release): under `repeated` each cycle's close
          // re-fires it ("close on transition fires during repeated cycles").
          override def close(): Unit = runBoth(src.close())(finalizer)
        }
      )
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      val src = self.compile(depth + 1, bufferSize)
      new Reader.DelegatingReader[A](src) {
        // Per-CLOSE hook: see the interpreter branch above.
        override def close(): Unit = runBoth(src.close())(finalizer)
      }
    }
  }

  /** Transforms the error channel with `f`. */
  private[streams] final class ErrorMapped[E, E2, A](self: Stream[E, A], f: E => E2)
      extends Stream[E2, A]
      with LinearStream {
    def render: String                                          = s"${self.render}.mapError(...)"
    override def knownLength: Option[Long]                      = self.knownLength
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(r => new ErrorMappedReader[E, A](r.asInstanceOf[Reader[A]], f))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      new ErrorMappedReader[E, A](self.compile(depth + 1, bufferSize), f)
    }
  }

  /** Transforms StreamError values with `f` during pull-based evaluation. */
  private[streams] final class ErrorMappedReader[E, A](upstream: Reader[A], f: E => Any) extends Reader[A] {
    override def jvmType: JvmType       = upstream.jvmType
    def isClosed: Boolean               = upstream.isClosed
    def read[A1 >: A](sentinel: A1): A1 =
      try upstream.read(sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      try pullInt(upstream, sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      try pullLong(upstream, sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      try pullFloat(upstream, sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      try pullDouble(upstream, sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readByte(): Int =
      try upstream.readByte()
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] =
      try upstream.readUpToN[A1](n)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def skip(n: Long): Unit =
      try upstream.skip(n)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    // Passes `upstream` elements through unchanged; EOF disambiguation delegates
    // to `upstream` (BUG-004).
    override def lastReadWasEOF: Boolean = upstream.lastReadWasEOF
    def close(): Unit                    = upstream.close()
    // Stateless pass-through: a reset just rewinds the underlying source so
    // `xs.mapError(...).repeated` restarts like every other stateless transform
    // (ITER-4).
    override def reset(): Unit = upstream.reset()
  }

  /** A failed stream source that always throws the given StreamError. */
  private[streams] final class FailedReader(se: StreamError) extends Reader[Nothing] {
    def isClosed: Boolean                     = false
    def read[A1 >: Nothing](sentinel: A1): A1 = throw se
    override def readByte(): Int              = throw se
    override def skip(n: Long): Unit          = ()
    def close(): Unit                         = ()
    // Stateless: re-throws the same error on every pull, so reset is a no-op
    // (lets `Stream.fail(...).repeated` and `&&` restart instead of throwing UOE).
    override def reset(): Unit = ()
  }

  /** Emits only elements satisfying `pred`. */
  private[streams] final class Filtered[E, A](
    self: Stream[E, A],
    pred: A => Boolean,
    jtA: JvmType.Infer[A]
  ) extends Stream[E, A]
      with LinearStream {
    def render: String                                          = s"${self.render}.filter(...)"
    private[streams] def upstream: Stream[_, _]                 = self
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.addFilter(Interpreter.laneOf(jtA.jvmType))(pred)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      val sourceReader = self.compile(depth + 1, bufferSize)
      val inLane       = Interpreter.laneOf(jtA.jvmType)
      sourceReader match {
        case p: Interpreter =>
          p.addFilter(inLane)(pred)
          p.seal()
          p.asInstanceOf[Reader[A]]
        case r =>
          val reader = (inLane: @scala.annotation.switch) match {
            case 0 => new Reader.FilteredInt(r, pred.asInstanceOf[AnyRef])
            case 1 => new Reader.FilteredLong(r, pred.asInstanceOf[AnyRef])
            case 2 => new Reader.FilteredFloat(r, pred.asInstanceOf[AnyRef])
            case 3 => new Reader.FilteredDouble(r, pred.asInstanceOf[AnyRef])
            case _ => new Reader.FilteredRef(r, pred.asInstanceOf[AnyRef])
          }
          reader.asInstanceOf[Reader[A]]
      }
    }
  }

  /**
   * Collect: applies a partial function, emitting only defined results without
   * double evaluation.
   */
  private[streams] final class Collected[E, A, B](
    self: Stream[E, A],
    pf: PartialFunction[A, B],
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B]
      with LinearStream {
    def render: String                                          = s"${self.render}.collect(...)"
    private[streams] def upstream: Stream[_, _]                 = self
    private[streams] def compileOp(pipeline: Interpreter): Unit = {
      val inLane    = Interpreter.laneOf(jtA.jvmType)
      val sentinel  = Reader.CollectedRef.sentinel
      val fallback  = Reader.CollectedRef.fallback
      val fallbackA = fallback.asInstanceOf[A => B]
      pipeline.addMap(inLane, Interpreter.OUT_R)((a: Any) =>
        pf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
      )
      pipeline.addFilter(Interpreter.OUT_R)((b: Any) => (b.asInstanceOf[AnyRef] ne sentinel))
      if (Interpreter.outLaneOf(jtB.jvmType) != Interpreter.OUT_R) {
        pipeline.addMap(Interpreter.OUT_R, Interpreter.outLaneOf(jtB.jvmType))((b: Any) => b)
      }
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      val sourceReader = self.compile(depth + 1, bufferSize)
      sourceReader match {
        case p: Interpreter =>
          val inLane    = Interpreter.laneOf(jtA.jvmType)
          val sentinel  = Reader.CollectedRef.sentinel
          val fallback  = Reader.CollectedRef.fallback
          val fallbackA = fallback.asInstanceOf[A => B]
          p.addMap(inLane, Interpreter.OUT_R)((a: Any) =>
            pf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
          )
          p.addFilter(Interpreter.OUT_R)((b: Any) => (b.asInstanceOf[AnyRef] ne sentinel))
          if (Interpreter.outLaneOf(jtB.jvmType) != Interpreter.OUT_R) {
            p.addMap(Interpreter.OUT_R, Interpreter.outLaneOf(jtB.jvmType))((b: Any) => b)
          }
          p.seal()
          p.asInstanceOf[Reader[B]]
        case r =>
          new Reader.CollectedRef(r, pf.asInstanceOf[AnyRef], jtB.jvmType).asInstanceOf[Reader[B]]
      }
    }
  }

  /** FlatMap: maps each element to a stream, then flattens sequentially. */
  private[streams] final class FlatMapped[E, E2 >: E, A, B](
    self: Stream[E, A],
    f: A => Stream[E2, B],
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ) extends Stream[E2, B]
      with LinearStream {
    def render: String                                          = s"${self.render}.flatMap(...)"
    private[streams] def upstream: Stream[_, _]                 = self
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.addPush(Interpreter.laneOf(jtA.jvmType), Interpreter.laneOf(jtB.jvmType))(f)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      val sourceReader = self.compile(depth + 1, bufferSize)
      val inLane       = Interpreter.laneOf(jtA.jvmType)
      sourceReader match {
        case p: Interpreter =>
          p.addPush(inLane, Interpreter.laneOf(jtB.jvmType))(f)
          p.seal()
          p.asInstanceOf[Reader[B]]
        case r =>
          // Per-element inner compilation with leaf reuse: the common
          // `flatMap(_ => Stream.succeed(..))` / `flatMap(_ => Stream.range(..))`
          // shapes re-arm a private mutable reader (one per flatMap reader,
          // created lazily) instead of allocating a fresh reader per OUTER
          // element. Safe because `pullOuter` only runs after the previous
          // inner has been closed, and the cached readers are never
          // user-visible. Range reuse applies to ALL spans: under the bulk
          // `foldInt` drain, `ReusableIntRange` and a fresh `FromIntRange`
          // run the identical tight local loop, so re-arming is a pure
          // allocation saving (the earlier span<=32 gate predated `foldInt`,
          // when reuse measurably lost the per-element inlining lottery at
          // span 100). Everything else takes the generic compile path.
          val compileInner: AnyRef => Reader[Any] = new (AnyRef => Reader[Any]) {
            private[this] var cachedSingleton: Reader.ReusableSingletonPrim = null
            private[this] var cachedRange: Reader.ReusableIntRange          = null
            def apply(stream: AnyRef): Reader[Any]                          = stream match {
              case sp: SucceedPrim[_] =>
                var c = cachedSingleton
                if (c eq null) { c = new Reader.ReusableSingletonPrim; cachedSingleton = c }
                c.arm(sp.prim, sp.ord)
                c
              case rs: RangedStream =>
                var c = cachedRange
                if (c eq null) { c = new Reader.ReusableIntRange; cachedRange = c }
                c.arm(rs.from, rs.until)
                c.asInstanceOf[Reader[Any]]
              case s =>
                s.asInstanceOf[Stream[Any, Any]].compile(0, Stream.DefaultBufferSize).asInstanceOf[Reader[Any]]
            }
          }
          val outType = jtB.jvmType
          val reader  = (inLane: @scala.annotation.switch) match {
            case 0 => new Reader.FlatMappedInt(r, f, compileInner, outType)
            case 1 => new Reader.FlatMappedLong(r, f, compileInner, outType)
            case 2 => new Reader.FlatMappedFloat(r, f, compileInner, outType)
            case 3 => new Reader.FlatMappedDouble(r, f, compileInner, outType)
            case _ => new Reader.FlatMappedRef(r, f, compileInner, outType)
          }
          reader.asInstanceOf[Reader[B]]
      }
    }
  }

  /** Resource-safe stream: acquires R, uses it, then releases on close. */
  private[streams] final class FromAcquireRelease[R, E, A](
    acquire: => R,
    release: R => Unit,
    use: R => Stream[E, A]
  ) extends Stream[E, A] {
    def render: String                                                   = "Stream.fromAcquireRelease(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val r = acquire
      try {
        use(r).compileInterpreter(pipeline)
        pipeline.wrapLastRead(src =>
          new Reader.DelegatingReader[Any](src) {
            // Release at most once per acquire (BUG-R7-01): reset()-driven
            // replay never re-acquires `r`.
            private var released       = false
            override def close(): Unit = runBoth(src.close())(if (!released) { released = true; release(r) })
          }
        )
      } catch {
        case t: Throwable =>
          throw cleanupWithPrimary(t)(release(r))
      }
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      val r = acquire
      try {
        val src = use(r).compile(depth, bufferSize)
        new Reader.DelegatingReader[A](src) {
          // Release at most once per acquire (BUG-R7-01).
          private var released       = false
          override def close(): Unit = runBoth(src.close())(if (!released) { released = true; release(r) })
        }
      } catch {
        case t: Throwable =>
          throw cleanupWithPrimary(t)(release(r))
      }
    }
  }

  /** Leaf stream backed by a known, materialized [[Chunk]]. */
  private[streams] final class FromChunkStream[A](
    chunk: Chunk[A],
    jt: JvmType.Infer[A]
  ) extends Stream[Nothing, A] {
    def render: String                                                   = "Stream.fromChunk(...)"
    override def knownChunk: Option[Chunk[A]]                            = Some(chunk)
    override def knownLength: Option[Long]                               = Some(chunk.length.toLong)
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit =
      pipeline.appendRead(Reader.fromChunk(chunk)(jt))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      Reader.fromChunk(chunk)(jt)
  }

  /** Leaf stream backed by a lazily-created [[Reader]]. */
  private[streams] class FromReader[E, A](
    mkReader: () => Reader[A],
    val renderLabel: String = "Stream.fromReader(...)"
  ) extends Stream[E, A] {
    def render: String                                                   = renderLabel
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val reader = mkReader()
      pipeline.appendRead(reader)
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = mkReader()
  }

  /**
   * Leaf stream of exactly one primitive element, stored unboxed as raw bits
   * plus its [[JvmType]] ordinal (the same encoding as
   * [[io.Reader.SingletonPrim]]). A dedicated leaf class (rather than a generic
   * `FromReader` with a capturing thunk) keeps `Stream.succeed(prim)` down to a
   * single small allocation and — critically — lets [[FlatMapped]]'s
   * per-element inner compilation recognize the one-element shape and re-arm a
   * private reusable reader instead of allocating a fresh reader chain per
   * outer element.
   */
  private[streams] final class SucceedPrim[A](
    private[streams] val prim: Long,
    private[streams] val ord: Int
  ) extends Stream[Nothing, A] {
    def render: String                                                   = "Stream.succeed(...)"
    override def knownLength: Option[Long]                               = Some(1L)
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit =
      pipeline.appendRead(new Reader.SingletonPrim[A](prim, ord << 8))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      new Reader.SingletonPrim[A](prim, ord << 8)
  }

  /**
   * Leaf stream over the half-open integer interval `[from, until)` (see
   * [[io.Reader.range]]). A dedicated leaf class (rather than a generic
   * `FromReader` with a capturing thunk) keeps `Stream.range` to a single small
   * allocation and lets [[FlatMapped]]'s per-element inner compilation re-arm a
   * private reusable range reader instead of allocating a fresh one per outer
   * element.
   */
  private[streams] final class RangedStream(
    private[streams] val from: Int,
    private[streams] val until: Int
  ) extends Stream[Nothing, Int] {
    def render: String = s"Stream.range($from, $until)"
    // O(1) metadata computed directly from the bounds — no `scala.Range`, which
    // throws when `[from, until)` spans more than `Int.MaxValue` integers
    // (BUG-N3). `knownLength` is a `Long` so it can hold the true count.
    override def knownLength: Option[Long]                               = Some(if (until > from) until.toLong - from.toLong else 0L)
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit =
      pipeline.appendRead(Reader.range(from, until))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int] =
      Reader.range(from, until)
  }

  /** Resource-safe stream backed by a Resource managed in a Scope. */
  private[streams] final class FromResource[R, E, A](
    resource: Resource[R],
    use: R => Stream[E, A]
  ) extends Stream[E, A] {
    def render: String                                                   = "Stream.fromResource(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val os    = Scope.global.open()
      val scope = os.scope
      val r     = scope.leak(scope.allocate(resource))
      try {
        use(r).compileInterpreter(pipeline)
        pipeline.wrapLastRead(src =>
          new Reader.DelegatingReader[Any](src) {
            // Scope closes at most once per open (BUG-R7-01).
            private var released       = false
            override def close(): Unit = runBoth(src.close())(if (!released) { released = true; os.close() })
          }
        )
      } catch {
        case t: Throwable =>
          throw cleanupWithPrimary(t)(os.close())
      }
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      val os    = Scope.global.open()
      val scope = os.scope
      val r     = scope.leak(scope.allocate(resource))
      try {
        val src = use(r).compile(depth, bufferSize)
        new Reader.DelegatingReader[A](src) {
          // Scope closes at most once per open (BUG-R7-01).
          private var released       = false
          override def close(): Unit = runBoth(src.close())(if (!released) { released = true; os.close() })
        }
      } catch {
        case t: Throwable =>
          throw cleanupWithPrimary(t)(os.close())
      }
    }
  }

  /** Lazily maps elements with `f`. */
  private[streams] final class Mapped[E, A, B](
    self: Stream[E, A],
    f: A => B,
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B]
      with LinearStream {
    def render: String                                          = s"${self.render}.map(...)"
    override def knownLength: Option[Long]                      = self.knownLength
    private[streams] def upstream: Stream[_, _]                 = self
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.addMap(Interpreter.laneOf(jtA.jvmType), Interpreter.outLaneOf(jtB.jvmType))(f)
    // NOTE on method size: `compile` runs on every `run()` (streams re-compile
    // per run) and its caller's compilation unit also contains the drain loop.
    // Keeping `compile` and each helper below under HotSpot's hot-method
    // inline threshold (`FreqInlineSize`, 325 bytecode bytes) lets the whole
    // compile step inline there, so escape analysis can scalar-replace the
    // freshly allocated wrapper reader. A single monolithic method here
    // crossed that threshold and measurably regressed flatMap-heavy pipelines
    // (~14% on eval mixed_1) purely through lost inlining — keep these small.
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      wrapReader(self.compile(depth + 1, bufferSize))
    }

    private[this] def wrapReader(sourceReader: Reader[A]): Reader[B] =
      sourceReader match {
        case p: Interpreter =>
          p.addMap(Interpreter.laneOf(jtA.jvmType), Interpreter.outLaneOf(jtB.jvmType))(f)
          p.seal()
          p.asInstanceOf[Reader[B]]
        case r =>
          val fused = fuseReader(r, Interpreter.outLaneOf(jtB.jvmType))
          if (fused ne null) fused
          else {
            val outType = jtB.jvmType
            val reader  = (Interpreter.laneOf(jtA.jvmType): @scala.annotation.switch) match {
              case 0 => new Reader.MappedInt(r, f.asInstanceOf[AnyRef], outType)
              case 1 => new Reader.MappedLong(r, f.asInstanceOf[AnyRef], outType)
              case 2 => new Reader.MappedFloat(r, f.asInstanceOf[AnyRef], outType)
              case 3 => new Reader.MappedDouble(r, f.asInstanceOf[AnyRef], outType)
              case _ => new Reader.MappedRef(r, f.asInstanceOf[AnyRef], outType)
            }
            reader.asInstanceOf[Reader[B]]
          }
      }

    /** `Int`-lane fusion cases; returns `null` when none apply. */
    private[this] def fuseReader(r: Reader[_], outLane: Int): Reader[B] =
      if (outLane != Interpreter.OUT_I) null
      else
        r match {
          // Fuse `filter(Int).map(Int => Int)` into one reader: the nested
          // `MappedInt(FilteredInt(..))` chain is a 3-deep monomorphic `readInt`
          // call HotSpot inlines only intermittently (filterMap perf lottery).
          case fr: Reader.FilteredInt =>
            new Reader.FilteredMappedInt(fr.source, fr.pred, f.asInstanceOf[AnyRef]).asInstanceOf[Reader[B]]
          // Fuse consecutive `map(Int => Int)` stages into ONE reader with a
          // composed specialized function: N nested MappedInt readers form an
          // N-deep virtual `readInt` chain HotSpot stops inlining after a few
          // levels (chainedMaps: ~3 ns/elem per un-inlined level). Composition
          // preserves application order (`m.f` first, then `f`) and per-element
          // exception semantics exactly.
          case m: Reader.MappedInt if (m.outType eq JvmType.Int) =>
            new Reader.MappedInt(m.source, Reader.composeIntInt(m.f, f.asInstanceOf[AnyRef]), JvmType.Int)
              .asInstanceOf[Reader[B]]
          // Same fusion when the upstream is an already-fused filter+map reader.
          case fm: Reader.FilteredMappedInt =>
            new Reader.FilteredMappedInt(fm.source, fm.pred, Reader.composeIntInt(fm.f, f.asInstanceOf[AnyRef]))
              .asInstanceOf[Reader[B]]
          case _ => null
        }
  }

  /** Restarts the stream on clean close. */
  private[streams] final class Repeated[E, A](self: Stream[E, A]) extends Stream[E, A] with LinearStream {
    def render: String                                          = s"${self.render}.repeated"
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(r => Reader.repeated[A](r.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      Reader.repeated[A](self.compile(depth + 1, bufferSize))
    }
  }

  /** Emits at most the first `n` elements. */
  private[streams] final class Taken[E, A](self: Stream[E, A], n: Long) extends Stream[E, A] with LinearStream {
    def render: String                                          = s"${self.render}.take($n)"
    override def knownLength: Option[Long]                      = self.knownLength.map(l => math.max(0L, math.min(math.max(0L, n), l)))
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(r => if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      val r = self.compile(depth + 1, bufferSize)
      (if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r).asInstanceOf[Reader[A]]
    }
  }

  /** Emits elements while `pred` holds. */
  private[streams] final class TakenWhile[E, A](self: Stream[E, A], pred: A => Boolean)
      extends Stream[E, A]
      with LinearStream {
    def render: String                                          = s"${self.render}.takeWhile(...)"
    private[streams] def upstream: Stream[_, _]                 = self
    override private[streams] def isSealBefore: Boolean         = true
    private[streams] def compileOp(pipeline: Interpreter): Unit =
      pipeline.wrapLastRead(r => new Reader.TakenWhile[A](r.asInstanceOf[Reader[A]], pred))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      new Reader.TakenWhile[A](self.compile(depth + 1, bufferSize), pred)
    }
  }

}
