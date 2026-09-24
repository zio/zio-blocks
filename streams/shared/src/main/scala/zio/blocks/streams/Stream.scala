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
import zio.blocks.async._
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.combinators.{Concat, Tuples}
import zio.blocks.scope.{Finalization, Resource, Scope}
import zio.blocks.streams.internal.{
  AsyncConcurrentReaders,
  AsyncInterpreter,
  AsyncStatefulReader,
  EndOfStream,
  SyncInterpreter,
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
 * operation is driven. Cross-platform terminals such as [[runAsync]],
 * [[runCollectAsync]], and [[headAsync]] return `Async[Either[E, Z]]`. The JVM
 * additionally provides blocking terminals such as `run`, `runCollect`, and
 * `head`, which return `Either[E, Z]`. `Left` contains typed stream errors;
 * defects fail the outer `Async` or propagate from a JVM blocking terminal.
 *
 * @tparam E
 *   Error type.
 * @tparam A
 *   Element type.
 */
abstract class Stream[+E, +A] extends StreamPlatformSpecific[E, A] {

  /**
   * Zips this stream with `that`, pairing elements positionally. Shorter stream
   * determines length. Uses [[zio.blocks.combinators.Tuples]] for flattened
   * composition: `a && b && c` produces a `Stream` of `(A, B, C)`.
   */
  def &&[E2, E3, B, C](that: Stream[E2, B])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    zip: Stream.Zip[A, B, C],
    jtC: JvmType.Infer[C]
  ): Stream[E3, C] =
    new Stream.Zipped[E3, A, B, C](
      Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat),
      Stream.widenErrorRight(that, errorConcat),
      zip,
      jtC.jvmType
    )

  /** Alias for [[concat]]. */
  final def ++[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] = concat(that)

  /**
   * Alias for [[orElse]]. The fallback stream is evaluated lazily, only on
   * error.
   */
  def ||[E2, A2, A3](that: => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E2, A3] =
    orElse(that)

  /**
   * Decouples upstream production from downstream consumption with a bounded
   * buffer of `n` elements while preserving order. `n` must be at least one.
   * Failure, downstream close, or cancellation closes the producer.
   */
  def buffer(n: Int): Stream[E, A] = {
    require(n >= 1, s"buffer requires n >= 1, got n=$n")
    new Stream.Buffered(this, n)
  }

  /**
   * On the first typed error, closes the failing stream and lazily switches to
   * the stream returned by `f`. Defects are not handled.
   */
  def catchAll[E2, A2, A3](f: E => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E2, A3] =
    if (valueConcat.isIdentityLike)
      new Stream.CatchAll[E, E2, A3](
        this.asInstanceOf[Stream[E, A3]],
        f.asInstanceOf[E => Stream[E2, A3]],
        jtA3.jvmType
      )
    else
      new Stream.CatchAll[E, E2, A3](
        Stream.widenElemLeft(this.asInstanceOf[Stream[E, A]], valueConcat, jtA3),
        e => Stream.widenElemRight(f(e), valueConcat, jtA3),
        JvmType.AnyRef
      )

  /**
   * On the first non-fatal defect matched by `f`, closes the failing stream and
   * lazily switches to the selected stream. Typed errors and unmatched defects
   * are not handled.
   */
  def catchDefect[E2, E3, A2, A3](f: PartialFunction[Throwable, Stream[E2, A2]])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] = {
    val self0 =
      Stream.widenElemLeft(Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat), valueConcat, jtA3)
    val recover = new PartialFunction[Throwable, Stream[E3, A3]] {
      def isDefinedAt(t: Throwable): Boolean  = f.isDefinedAt(t)
      def apply(t: Throwable): Stream[E3, A3] =
        Stream.widenElemRight(Stream.widenErrorRight(f(t), errorConcat), valueConcat, jtA3)
    }
    val outputJvmType = if (valueConcat.isIdentityLike) jtA3.jvmType else JvmType.AnyRef
    new Stream.CatchDefect[E3, A3](self0, recover, outputJvmType)
  }

  /**
   * Groups elements into `Chunk`s of size `n`, preserving order. The final
   * group may be smaller; `n` must be at least one.
   */
  def chunked(n: Int): Stream[E, Chunk[A]] = {
    require(n >= 1, s"chunked requires n >= 1, got n=$n")
    val self                                                 = this
    def materializeRoot(reader: Reader[A]): Reader[Chunk[A]] = reader match {
      case source: Reader.AsyncReader[A @unchecked] => AsyncStatefulReader.chunked(source, n)
      case source: Reader.SyncReader[A @unchecked]  =>
        // specialization-id: stream-chunked-sync-reader
        new Reader.SyncReader[Chunk[A]] {
          def close()                                = source.close()
          def isClosed                               = source.isClosed
          def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
            val c = source.readN[A](n)
            if (c.isEmpty) sentinel else c.asInstanceOf[A1]
          }
        }
    }
    new Stream.StatefulReaderStream[E, A, Chunk[A]](
      self,
      materializeRoot,
      s"${this.render}.chunked($n)",
      ElementRepresentation.Boxed
    )
  }

  /** Applies a partial function, emitting only defined results. */
  def collect[B](pf: PartialFunction[A, B])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] =
    new Stream.Collected(this, pf, jtB)

  /** Asynchronously transforms defined elements, dropping `None` results. */
  def collectAsync[B](f: A => Async[Option[B]])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] = new Stream.AsyncCollected(this, f, jtB)

  /** Emits all elements of `this` followed by all elements of `that`. */
  def concat[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] =
    new Stream.Concatenated[E3, A3](
      Stream.widenElemLeft(Stream.widenErrorLeft(this.asInstanceOf[Stream[E, A]], errorConcat), valueConcat, jtA3),
      Stream.widenElemRight(Stream.widenErrorRight(that, errorConcat), valueConcat, jtA3),
      jtA3.jvmType
    )

  /** Asynchronously counts the number of elements. */
  def countAsync: Async[Either[E, Long]] = runAsync(Sink.count)

  /**
   * Emits the first occurrence of each element, preserving order. Distinctness
   * state is allocated per materialization and may grow without bound.
   */
  def distinct: Stream[E, A] =
    new Stream.Deferred(
      () => {
        val seen = new scala.collection.mutable.HashSet[A]()
        this.filter(a => seen.add(a))
      },
      elementRepresentation
    )

  /**
   * Emits the first element for each key computed by `f`, preserving order. Key
   * state is allocated per materialization and may grow without bound.
   */
  def distinctBy[K](f: A => K): Stream[E, A] =
    new Stream.Deferred(
      () => {
        val seen = new scala.collection.mutable.HashSet[K]()
        this.filter(a => seen.add(f(a)))
      },
      elementRepresentation
    )

  /**
   * Sequentially computes keys and emits the first element for each key,
   * preserving order. Key state is per materialization and may grow without
   * bound; asynchronous failures are defects.
   */
  def distinctByAsync[K](f: A => Async[K]): Stream[E, A] =
    new Stream.AsyncDistinctBy(this, f)

  /** Skips the first `n` elements, then emits the rest. `n <= 0` is a no-op. */
  def drop(n: Long): Stream[E, A] = new Stream.Dropped(this, n)

  /**
   * Registers `finalizer` lazily and runs it exactly once when the materialized
   * stream closes, including normal completion, failure, early termination, and
   * cancellation. A finalizer exception is a defect.
   */
  def ensuring(finalizer: => Unit): Stream[E, A] =
    new Stream.Ensuring(this, finalizer)

  /**
   * Registers an asynchronous finalizer lazily and awaits it exactly once when
   * the materialized stream closes, including normal completion, failure, early
   * termination, and cancellation. Finalizer failure is a defect.
   */
  def ensuringAsync(finalizer: => Async[Unit]): Stream[E, A] =
    new Stream.AsyncEnsuring(this, () => finalizer)

  /** Asynchronously tests whether any element satisfies `pred`. */
  def existsAsync(pred: A => Async[Boolean]): Async[Either[E, Boolean]] = runAsync(Sink.existsAsync(pred))

  /** Emits only elements satisfying `pred`. */
  def filter(pred: A => Boolean): Stream[E, A] =
    new Stream.Filtered(this, pred)

  /**
   * Tests elements sequentially and emits those satisfying the asynchronous
   * predicate, preserving order. Predicate failure is a defect.
   */
  def filterAsync(pred: A => Async[Boolean]): Stream[E, A] =
    new Stream.AsyncFiltered(this, pred)

  /** Asynchronously returns the first element satisfying `pred`, or `None`. */
  def findAsync(pred: A => Async[Boolean]): Async[Either[E, Option[A]]] = runAsync(Sink.findAsync(pred))

  /**
   * Maps each element to an inner stream and consumes those streams
   * sequentially in input order, closing each before advancing to the next.
   */
  def flatMap[E2, E3, B](
    f: A => Stream[E2, B]
  )(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, E2, E3],
    jtB: JvmType.Infer[B]
  ): Stream[E3, B] =
    if (errorConcat.isIdentityLike)
      new Stream.FlatMapped[E3, E3, A, B](
        this.asInstanceOf[Stream[E3, A]],
        f.asInstanceOf[A => Stream[E3, B]],
        elementRepresentation,
        jtB
      )
    else
      new Stream.FlatMapped[E3, E3, A, B](
        this.mapError(errorConcat.left),
        a => f(a).mapError(errorConcat.right),
        elementRepresentation,
        jtB
      )

  /**
   * Applies `f` to each element to produce an inner stream, then merges up to
   * `n` inner streams concurrently. Output is unordered (arrival order, not
   * input order). On JVM, each inner stream runs on a virtual thread. On JS,
   * degrades to sequential `flatMap`. `n` must be at least one. Failure or
   * cancellation closes the outer stream and all active inner streams.
   */
  def flatMapPar[E1 >: E, B](n: Int)(f: A => Stream[E1, B])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E1, B] = {
    require(n >= 1, s"flatMapPar requires n >= 1, got $n")
    if (n == 1) flatMap(f)
    else {
      jtB.jvmType
      Stream.mergeAll[E1, B](n)(
        this.asInstanceOf[Stream[E1, A]].map(f)(JvmType.Infer.boxed[Stream[E1, B]])
      )(jtB)
    }
  }

  /** Asynchronously tests whether all elements satisfy `pred`. */
  def forallAsync(pred: A => Async[Boolean]): Async[Either[E, Boolean]] = runAsync(Sink.forallAsync(pred))

  /** Alias for [[runForeachAsync]]. */
  def foreachAsync(f: A => Async[Unit]): Async[Either[E, Unit]] = runForeachAsync(f)

  /** Alias for [[chunked]]. Matches upstream naming. */
  def grouped(n: Int): Stream[E, Chunk[A]] = chunked(n)

  /** Asynchronously returns the first element, or `None` if empty. */
  def headAsync: Async[Either[E, Option[A]]] = runAsync(Sink.head)

  /** Inserts `sep` between each pair of consecutive elements. */
  def intersperse[A2, A3](sep: A2)(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E, A3] = {
    val self                                            = Stream.widenElemLeft(this.asInstanceOf[Stream[E, A]], valueConcat, jtA3)
    val projectedSep                                    = valueConcat.right(sep)
    def materializeRoot(reader: Reader[A3]): Reader[A3] = reader match {
      case source: Reader.AsyncReader[A3 @unchecked] =>
        AsyncStatefulReader.intersperse(Reader.normalizeAsyncChild(source, jtA3.jvmType), projectedSep, jtA3.jvmType)
      case source: Reader.SyncReader[A3 @unchecked] =>
        val normalized = Reader.normalizeSyncChild(source, jtA3.jvmType)
        jtA3.jvmType match {
          case JvmType.Boolean =>
            new Stream.SyncBooleanIntersperseReader(normalized, projectedSep.asInstanceOf[Boolean])
              .asInstanceOf[Reader[A3]]
          case JvmType.Byte =>
            new Stream.SyncByteIntersperseReader(normalized, projectedSep.asInstanceOf[Byte]).asInstanceOf[Reader[A3]]
          case JvmType.Char =>
            new Stream.SyncCharIntersperseReader(normalized, projectedSep.asInstanceOf[Char]).asInstanceOf[Reader[A3]]
          case JvmType.Short =>
            new Stream.SyncShortIntersperseReader(normalized, projectedSep.asInstanceOf[Short]).asInstanceOf[Reader[A3]]
          case JvmType.Int =>
            new Stream.SyncIntIntersperseReader(normalized, projectedSep.asInstanceOf[Int]).asInstanceOf[Reader[A3]]
          case JvmType.Long =>
            new Stream.SyncLongIntersperseReader(normalized, projectedSep.asInstanceOf[Long]).asInstanceOf[Reader[A3]]
          case JvmType.Float =>
            new Stream.SyncFloatIntersperseReader(normalized, projectedSep.asInstanceOf[Float]).asInstanceOf[Reader[A3]]
          case JvmType.Double =>
            new Stream.SyncDoubleIntersperseReader(normalized, projectedSep.asInstanceOf[Double])
              .asInstanceOf[Reader[A3]]
          case _ =>
            new Stream.SyncBoxedIntersperseReader(normalized, projectedSep.asInstanceOf[AnyRef])
              .asInstanceOf[Reader[A3]]
        }
    }
    new Stream.StatefulReaderStream[E, A3, A3](
      self,
      materializeRoot,
      s"${this.render}.intersperse(...)",
      ElementRepresentation.fromJvmType(jtA3.jvmType)
    )
  }

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

  /** Asynchronously returns the last element, or `None` if empty. */
  def lastAsync: Async[Either[E, Option[A]]] = runAsync(Sink.last)

  /** Lazily transforms each element with `f`, preserving order. */
  def map(f: A => Nothing)(implicit dummy: DummyImplicit): Stream[E, Nothing] = {
    val _ = dummy
    this match {
      case source: Stream.ProtectedAsyncSource[E @unchecked, A @unchecked] =>
        new Stream.ProtectedAsyncMapped[E, A, Nothing](
          source.rawAcquisition,
          source.elementRepresentation,
          f,
          JvmType.Infer.boxed[Nothing]
        )
      case _ => new Stream.GenericMapped[E, A, Nothing](this, f, JvmType.Infer.boxed[Nothing])
    }
  }

  /** Lazily transforms each element with `f`, preserving order. */
  def map[B](f: A => B)(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] = this match {
    case source: Stream.ProtectedAsyncSource[E @unchecked, A @unchecked] =>
      if ((source.elementRepresentation.stableJvmType.contains(JvmType.Int)) && (jtB.jvmType eq JvmType.Int))
        new Stream.ProtectedIntAsyncMappedInt[E](
          source.rawAcquisition.asInstanceOf[() => Async[Reader[Int]]],
          f.asInstanceOf[Int => Int]
        ).asInstanceOf[Stream[E, B]]
      else new Stream.ProtectedAsyncMapped(source.rawAcquisition, source.elementRepresentation, f, jtB)
    case _ => Stream.mapped(this, f, jtB)
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
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B))(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] =
    new Stream.Deferred(
      () => {
        var state = init
        this.map { a =>
          val (newState, b) = f(state, a)
          state = newState
          b
        }
      },
      ElementRepresentation.fromJvmType(jtB.jvmType)
    )

  /**
   * Asynchronously transforms elements while threading state sequentially. At
   * most one invocation of `f` is active at a time.
   */
  def mapAccumAsync[S, B](init: S)(f: (S, A) => Async[(S, B)])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] =
    new Stream.AsyncMapAccum(this, init, f, jtB)

  /** Asynchronously transforms each element. */
  def mapAsync[B](f: A => Async[B])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] =
    new Stream.AsyncMapped(this, f, jtB)

  /**
   * Transforms the error channel with `f`, leaving elements unchanged.
   */
  def mapError[E2](f: E => E2): Stream[E2, A] =
    new Stream.ErrorMapped(this, f)

  /**
   * Asynchronously transforms the typed error channel. It runs only when the
   * source fails with a typed error; callback failure is a defect.
   */
  def mapErrorAsync[E2](f: E => Async[E2]): Stream[E2, A] =
    new Stream.AsyncErrorMapped(this, f)

  /**
   * Applies `f` to each element using `n` concurrent workers. Output is
   * UNORDERED (arrival order, not input order). On JVM, workers run on virtual
   * threads. On JS, degrades to sequential `map`. `n` must be at least one;
   * failure or cancellation stops the workers and closes upstream.
   *
   * @param n
   *   number of concurrent workers
   * @param f
   *   transformation to apply to each element
   */
  def mapPar[B](n: Int)(f: A => B)(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] = {
    require(n >= 1, s"mapPar requires n >= 1, got $n")
    if (n == 1) map(f)
    else {
      jtB.jvmType
      new Stream.MapPar[E, A, B](this, n, f, elementRepresentation, jtB.jvmType)
    }
  }

  /**
   * Applies an asynchronous callback with at most `n` callbacks in flight.
   * Results are emitted in completion order. `n` must be at least one; callback
   * failure is a defect, and failure or cancellation stops all active callbacks
   * and closes upstream.
   */
  def mapParAsync[B](n: Int)(f: A => Async[B])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B] = {
    require(n >= 1, s"mapParAsync requires n >= 1, got $n")
    if (n == 1) mapAsync(f)
    else new Stream.AsyncMapPar(this, n, f, jtB)
  }

  /**
   * Falls back to `that` on the first typed error; defects are not handled.
   * Alias: `||`.
   */
  def orElse[E2, A2, A3](that: => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A @uncheckedVariance, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E2, A3] =
    catchAll(_ => that)

  /** Renders this stream as a human-readable description of its pipeline. */
  def render: String

  /**
   * Rematerializes this stream after each clean completion, producing an
   * infinite repetition when a cycle emits elements. Typed errors and defects
   * terminate repetition; closing the result closes the active cycle.
   */
  def repeated: Stream[E, A] = new Stream.Repeated(this)

  /**
   * Runs this stream through the asynchronous drain of `sink`. Materialization
   * and cleanup are lazy, cancellation-safe, and performed exactly once.
   */
  def runAsync[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Async[Either[E3, Z]] =
    (this, sink) match {
      case (_, fold: Sink.FoldLeftLong[A @unchecked]) =>
        val direct = dispatchFoldLongDirect(
          fold.z,
          (acc, value) => Async.succeed(fold.f0(acc, value))
        )
        if (direct.asInstanceOf[AnyRef] ne null) direct.asInstanceOf[Async[Either[E3, Z]]]
        else runSinkManagedAsync(sink)
      case (source: Stream.AsyncSource[_, _], mapped: Sink.MappedAsync[_, _, _, _])
          if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        mapped.self match {
          case fold: Sink.FoldLeftLong[_] =>
            source
              .asInstanceOf[Stream.AsyncSource[E3, Int]]
              .runFoldLeftLongMapAsyncDirect(
                fold.z,
                fold.f0.asInstanceOf[(Long, Int) => Long],
                mapped.f.asInstanceOf[Long => Async[Z]]
              )
          case _ => runSinkManagedAsync(sink)
        }
      case _ => runSinkManagedAsync(sink)
    }

  /**
   * Runs the stream asynchronously and collects all elements in order. This
   * requires memory proportional to the entire output and does not terminate
   * for an infinite stream.
   */
  def runCollectAsync: Async[Either[E, Chunk[A]]] = {
    val direct = this match {
      case source: Stream.AsyncSource[E @unchecked, A @unchecked] => source.runCollectDirect()
      case taken: Stream.Taken[E @unchecked, A @unchecked]        => taken.runCollectDirect()
      case _                                                      => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.collectAll)
  }

  /** Runs the stream asynchronously, discarding all elements. */
  def runDrainAsync: Async[Either[E, Unit]] = {
    val direct = this match {
      case source: Stream.AsyncSource[E @unchecked, A @unchecked]    => source.runDrainDirect()
      case mapped: Stream.AsyncMapped[E @unchecked, _, A @unchecked] => mapped.runDrainDirect()
      case _                                                         => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.drain)
  }

  /** Specialized asynchronous fold with a `Double` accumulator. */
  def runFoldAsync(z: Double)(f: (Double, A) => Async[Double]): Async[Either[E, Double]] = {
    val direct = this match {
      case mapped: Stream.Mapped[E @unchecked, _, A @unchecked]               => mapped.runFoldDoubleDirect(z, f)
      case mapped: Stream.ProtectedAsyncMapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldDoubleDirect(z, f)
      case mapped: Stream.ProtectedIntAsyncMappedInt[E @unchecked] =>
        mapped.runFoldDoubleDirect(z, f.asInstanceOf[(Double, Int) => Async[Double]])
      case _ => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foldLeftAsync[A, Double](z)(f)(JvmType.Infer.double))
  }

  /** Specialized asynchronous fold with a `Float` accumulator. */
  def runFoldAsync(z: Float)(f: (Float, A) => Async[Float]): Async[Either[E, Float]] = {
    val direct = this match {
      case mapped: Stream.Mapped[E @unchecked, _, A @unchecked]               => mapped.runFoldFloatDirect(z, f)
      case mapped: Stream.ProtectedAsyncMapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldFloatDirect(z, f)
      case mapped: Stream.ProtectedIntAsyncMappedInt[E @unchecked] =>
        mapped.runFoldFloatDirect(z, f.asInstanceOf[(Float, Int) => Async[Float]])
      case _ => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foldLeftAsync[A, Float](z)(f)(JvmType.Infer.float))
  }

  /** Specialized asynchronous fold with an `Int` accumulator. */
  def runFoldAsync(z: Int)(f: (Int, A) => Async[Int]): Async[Either[E, Int]] = {
    val direct = this match {
      case mapped: Stream.Mapped[E @unchecked, _, A @unchecked]               => mapped.runFoldIntDirect(z, f)
      case mapped: Stream.ProtectedAsyncMapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldIntDirect(z, f)
      case mapped: Stream.ProtectedIntAsyncMappedInt[E @unchecked] =>
        mapped.runFoldIntDirect(z, f.asInstanceOf[(Int, Int) => Async[Int]])
      case _ => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foldLeftAsync[A, Int](z)(f)(JvmType.Infer.int))
  }

  /** Specialized asynchronous fold with a `Long` accumulator. */
  def runFoldAsync(z: Long)(f: (Long, A) => Async[Long]): Async[Either[E, Long]] = {
    val direct = dispatchFoldLongDirect(z, f)
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foldLeftAsync[A, Long](z)(f)(JvmType.Infer.long))
  }

  private def dispatchFoldLongDirect(z: Long, f: (Long, A) => Async[Long]): Async[Either[E, Long]] =
    this match {
      case concatenated: Stream.Concatenated[E @unchecked, A @unchecked] =>
        concatenated.runFoldLongDirect(z, f)
      case flatMapped: Stream.FlatMapped[_, E @unchecked, _, A @unchecked] =>
        flatMapped.runFoldLongDirect(z, f)
      case filtered: Stream.AsyncFiltered[E @unchecked, A @unchecked] =>
        filtered.runFoldLongDirect(z, f)
      case filtered: Stream.Filtered[E @unchecked, A @unchecked] =>
        filtered.runFoldLongDirect(z, f)
      case managed: Stream.FromAcquireReleaseAsync[_, E @unchecked, A @unchecked] =>
        managed.runFoldLongDirect(z, f)
      case mapped: Stream.AsyncMapPar[E @unchecked, _, A @unchecked] =>
        mapped.runFoldLongDirect(z, f)
      case mapped: Stream.AsyncMapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldLongDirect(z, f)
      case mapped: Stream.Mapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldLongDirect(z, f)
      case mapped: Stream.ProtectedAsyncMapped[E @unchecked, _, A @unchecked] =>
        mapped.runFoldLongDirect(z, f)
      case mapped: Stream.ProtectedIntAsyncMappedInt[E @unchecked] =>
        mapped.runFoldLongDirect(z, f.asInstanceOf[(Long, Int) => Async[Long]])
      case singleton: Stream.SingletonInt =>
        singleton.runFoldLongDirect(z, f.asInstanceOf[(Long, Int) => Async[Long]]).asInstanceOf[Async[Either[E, Long]]]
      case source: Stream.AsyncSource[E @unchecked, A @unchecked] =>
        source.runFoldLongDirect(z, f)
      case taken: Stream.ProtectedAsyncTakeDrop[E @unchecked, A @unchecked] =>
        taken.runFoldLongDirect(z, f)
      case taken: Stream.TakeDrop[E @unchecked, A @unchecked] =>
        taken.runFoldLongDirect(z, f)
      case taken: Stream.Taken[E @unchecked, A @unchecked] =>
        taken.runFoldLongDirect(z, f)
      case taken: Stream.TakenWhile[E @unchecked, A @unchecked] =>
        taken.runFoldLongDirect(z, f)
      case unwrapped: Stream.Unwrapped[E @unchecked, A @unchecked] =>
        unwrapped.runFoldLongDirect(z, f)
      case _ => null
    }

  /** Runs the stream, folding sequentially with an asynchronous callback. */
  def runFoldAsync[Z](z: Z)(f: (Z, A) => Async[Z])(implicit
    jtZ: JvmType.Infer[Z]
  ): Async[Either[E, Z]] = {
    val direct =
      if (jtZ.jvmType eq JvmType.Float)
        this match {
          case mapped: Stream.Mapped[E @unchecked, _, A @unchecked] =>
            mapped
              .runFoldFloatDirect(z.asInstanceOf[Float], f.asInstanceOf[(Float, A) => Async[Float]])
              .asInstanceOf[Async[Either[E, Z]]]
          case mapped: Stream.ProtectedAsyncMapped[E @unchecked, _, A @unchecked] =>
            mapped
              .runFoldFloatDirect(z.asInstanceOf[Float], f.asInstanceOf[(Float, A) => Async[Float]])
              .asInstanceOf[Async[Either[E, Z]]]
          case mapped: Stream.ProtectedIntAsyncMappedInt[E @unchecked] =>
            mapped
              .runFoldFloatDirect(z.asInstanceOf[Float], f.asInstanceOf[(Float, Int) => Async[Float]])
              .asInstanceOf[Async[Either[E, Z]]]
          case _ => null
        }
      else
        this match {
          case taken: Stream.Taken[E @unchecked, A @unchecked] => taken.runFoldDirect(z, f)
          case _                                               => null
        }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foldLeftAsync[A, Z](z)(f))
  }

  /** Runs the stream, applying the asynchronous callback sequentially. */
  def runForeachAsync(f: A => Async[Unit]): Async[Either[E, Unit]] = {
    val direct = this match {
      case source: Stream.AsyncSource[E @unchecked, A @unchecked] => source.runForeachDirect(f)
      case _                                                      => null
    }
    if (direct.asInstanceOf[AnyRef] ne null) direct
    else runAsync(Sink.foreachAsync(f))
  }

  /**
   * Emits the accumulator at each step, starting with `init`. The output stream
   * has one more element than the input.
   */
  def scan[S](init: S)(f: (S, A) => S)(implicit jtS: JvmType.Infer[S]): Stream[E, S] = {
    val self               = this
    val safeF: (S, A) => S = (s, a) =>
      try f(s, a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    def materialize(source: Reader.SyncReader[A]): Reader.SyncReader[S] = {
      val outType = jtS.jvmType
      val input   = Stream.SyncScanInput(source, safeF)
      if (outType eq JvmType.Boolean) {
        // specialization-id: stream-scan-boolean-reader
        new Reader.SyncReader[S] {
          private var state: Boolean          = init.asInstanceOf[Boolean]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Boolean
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Boolean]; state.asInstanceOf[S1] }
            }
          override def readBoolean(sentinel: Int)(implicit ev: S <:< Boolean): Int =
            if (!emittedInit) { emittedInit = true; if (state) 1 else 0 }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Boolean]; if (state) 1 else 0 }
            }
        }
      } else if (outType eq JvmType.Byte) {
        // specialization-id: stream-scan-byte-reader
        new Reader.SyncReader[S] {
          private var state: Byte             = init.asInstanceOf[Byte]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Byte
          def read[S1 >: S](sentinel: S1): S1 = {
            val v = readByte(); if (v < 0) sentinel else v.toByte.asInstanceOf[S1]
          }
          override def readByte(): Int =
            if (!emittedInit) { emittedInit = true; state.toInt & 0xff }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) -1 else { state = next.asInstanceOf[Byte]; state.toInt & 0xff }
            }
        }
      } else if (outType eq JvmType.Char) {
        // specialization-id: stream-scan-char-reader
        new Reader.SyncReader[S] {
          private var state: Char             = init.asInstanceOf[Char]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Char
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Char]; state.asInstanceOf[S1] }
            }
          override def readChar(sentinel: Int)(implicit ev: S <:< Char): Int =
            if (!emittedInit) { emittedInit = true; state.toInt }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Char]; state.toInt }
            }
        }
      } else if (outType eq JvmType.Short) {
        // specialization-id: stream-scan-short-reader
        new Reader.SyncReader[S] {
          private var state: Short            = init.asInstanceOf[Short]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Short
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Short]; state.asInstanceOf[S1] }
            }
          override def readShort(sentinel: Int)(implicit ev: S <:< Short): Int =
            if (!emittedInit) { emittedInit = true; state.toInt }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Short]; state.toInt }
            }
        }
      } else if (outType eq JvmType.Int) {
        // specialization-id: stream-scan-int-reader
        new Reader.SyncReader[S] {
          private var state: Int              = init.asInstanceOf[Int]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Int
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Int]; state.asInstanceOf[S1] }
            }
          override def readInt(sentinel: Long)(implicit ev: S <:< Int): Long =
            if (!emittedInit) { emittedInit = true; state.toLong }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Int]; state.toLong }
            }
        }
      } else if (outType eq JvmType.Long) {
        // specialization-id: stream-scan-long-reader
        new Reader.SyncReader[S] {
          private var state: Long             = init.asInstanceOf[Long]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Long
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Long]; state.asInstanceOf[S1] }
            }
          override def readLong(sentinel: Long)(implicit ev: S <:< Long): Long =
            if (!emittedInit) { emittedInit = true; state }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Long]; state }
            }
          override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: S <:< Long): Int = {
            Reader.validateArrayRange(dest, offset, length)
            if (length == 0) 0
            else if (!emittedInit) { emittedInit = true; dest(offset) = state; 1 }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) -1 else { state = next.asInstanceOf[Long]; dest(offset) = state; 1 }
            }
          }
        }
      } else if (outType eq JvmType.Float) {
        // specialization-id: stream-scan-float-reader
        new Reader.SyncReader[S] {
          private var state: Float            = init.asInstanceOf[Float]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Float
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Float]; state.asInstanceOf[S1] }
            }
          override def readFloat(sentinel: Double)(implicit ev: S <:< Float): Double =
            if (!emittedInit) { emittedInit = true; state.toDouble }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Float]; state.toDouble }
            }
        }
      } else if (outType eq JvmType.Double) {
        // specialization-id: stream-scan-double-reader
        new Reader.SyncReader[S] {
          private var state: Double           = init.asInstanceOf[Double]
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.Double
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state.asInstanceOf[S1] }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Double]; state.asInstanceOf[S1] }
            }
          override def readDouble(sentinel: Double)(implicit ev: S <:< Double): Double =
            if (!emittedInit) { emittedInit = true; state }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) sentinel else { state = next.asInstanceOf[Double]; state }
            }
          override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: S <:< Double): Int = {
            Reader.validateArrayRange(dest, offset, length)
            if (length == 0) 0
            else if (!emittedInit) { emittedInit = true; dest(offset) = state; 1 }
            else {
              val next = input.next(state.asInstanceOf[S]);
              if (input.isEOF) -1 else { state = next.asInstanceOf[Double]; dest(offset) = state; 1 }
            }
          }
        }
      } else {
        // specialization-id: stream-scan-reference-reader
        new Reader.SyncReader[S] {
          private var state: S                = init
          private var emittedInit             = false
          def close(): Unit                   = source.close()
          def isClosed: Boolean               = source.isClosed
          override def jvmType: JvmType       = JvmType.AnyRef
          def read[S1 >: S](sentinel: S1): S1 =
            if (!emittedInit) { emittedInit = true; state }
            else {
              val next = input.next(state)
              if (input.isEOF) sentinel else { state = next; state }
            }
        }
      }
    }
    new Stream[E, S] with Stream.StackCompileNode {
      override private[streams] def elementRepresentation: ElementRepresentation =
        ElementRepresentation.fromJvmType(jtS.jvmType)

      def render: String = s"${self.render}.scan(...)"

      def stackFrame(bufferSize: Int): Stream.CompileFrame =
        Stream.unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))

      private[streams] def compile(depth: Int, bufferSize: Int): Reader[S] =
        if (depth >= Stream.DepthCutoff) Stream.compileStackSafe(this, bufferSize)
        else wrapReader(self.compile(depth + 1, bufferSize))

      private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
        val reader = materialize(Stream.compileToReader(self, pipeline))
        try {
          pipeline.ensureMaterializationCurrent()
          pipeline.appendRead(reader)
        } catch {
          case cause: Throwable =>
            pipeline.closeRejectedOwner(reader, cause)
            throw cause
        }
      }

      override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
        pipeline.deferAsyncScan(init, jtS.jvmType)((s: S, a: A) => Async.succeed(safeF(s, a)))
        self
      }

      private def wrapReader(source: Reader[A]): Reader[S] = source match {
        case reader: Reader.SyncReader[A @unchecked]  => materialize(reader)
        case reader: Reader.AsyncReader[A @unchecked] =>
          Stream.transformAsync(reader)(
            _.deferAsyncScan(init, reader.jvmType, jtS.jvmType)((s: S, a: A) => Async.succeed(safeF(s, a)))
          )
      }
    }
  }

  /**
   * Asynchronously emits the accumulator at each step, starting with `init`.
   * The output stream has one more element than the input.
   */
  def scanAsync[S](init: S)(f: (S, A) => Async[S])(implicit jtS: JvmType.Infer[S]): Stream[E, S] =
    new Stream.AsyncScan(this, init, f, jtS)

  /**
   * Emits sliding windows of up to `n` elements as `Chunk`s, preserving order.
   * Each window starts `step` elements after the previous one; gaps are skipped
   * when `step > n`. One final partial window is emitted when it adds elements
   * not present in the preceding window. Both arguments must be at least one.
   */
  def sliding(n: Int, step: Int = 1): Stream[E, Chunk[A]] = {
    require(n >= 1 && step >= 1, s"sliding requires n >= 1 and step >= 1, got n=$n, step=$step")
    val self                                                 = this
    def materializeRoot(reader: Reader[A]): Reader[Chunk[A]] = reader match {
      case source: Reader.AsyncReader[A @unchecked] => return AsyncStatefulReader.sliding(source, n, step)
      case source: Reader.SyncReader[A @unchecked]  =>
        val et          = source.jvmType
        val longValue   = new Array[Long](1)
        val doubleValue = new Array[Double](1)

        /**
         * Skip `count` elements from source using the appropriate primitive
         * read. Returns true if all skipped, false if source ended.
         */
        def skipElements(count: Int): Boolean = {
          var i = 0
          while (i < count) {
            val available =
              if (et eq JvmType.Boolean) source.readBooleanPhysical(-1) >= 0
              else if (et eq JvmType.Byte) source.readBytePhysical() >= 0
              else if (et eq JvmType.Char) source.readCharPhysical(Int.MinValue) != Int.MinValue
              else if (et eq JvmType.Short) source.readShortPhysical(Int.MinValue) != Int.MinValue
              else if (et eq JvmType.Int) source.readIntPhysical(Long.MinValue) != Long.MinValue
              else if (et eq JvmType.Long) source.readLongsPhysical(longValue, 0, 1) > 0
              else if (et eq JvmType.Float) source.readFloatPhysical(Double.MaxValue) != Double.MaxValue
              else if (et eq JvmType.Double) source.readDoublesPhysical(doubleValue, 0, 1) > 0
              else source.read[Any](EndOfStream).asInstanceOf[AnyRef] ne EndOfStream
            if (!available) return false
            i += 1
          }
          true
        }

        if (et eq JvmType.Boolean) {
          // specialization-id: stream-sliding-boolean-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferBoolean(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) buf.shift(step)
                else { buf.shift(n); if (!skipElements(step - n)) { done = true; return sentinel } }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = source.readBooleanPhysical(-1)
                if (v < 0) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v != 0)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Char) {
          // specialization-id: stream-sliding-char-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferChar(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) buf.shift(step)
                else { buf.shift(n); if (!skipElements(step - n)) { done = true; return sentinel } }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = source.readCharPhysical(Int.MinValue)
                if (v == Int.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toChar)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Short) {
          // specialization-id: stream-sliding-short-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferShort(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
            def isClosed: Boolean                      = source.isClosed || done
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              if (done) return sentinel
              if (!firstWindow) {
                if (step <= n) buf.shift(step)
                else { buf.shift(n); if (!skipElements(step - n)) { done = true; return sentinel } }
              }
              firstWindow = false
              val sizeBeforeFill = buf.size
              while (buf.size < n) {
                val v = source.readShortPhysical(Int.MinValue)
                if (v == Int.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toShort)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Byte) {
          // specialization-id: stream-sliding-byte-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferByte(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
                val v = source.readBytePhysical()
                if (v < 0) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toByte)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Int) {
          // specialization-id: stream-sliding-int-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferInt(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
                val v = source.readIntPhysical(Long.MinValue)
                if (v == Long.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toInt)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Long) {
          // specialization-id: stream-sliding-long-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferLong(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
                val status = source.readLongsPhysical(longValue, 0, 1)
                if (status < 0) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(longValue(0))
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Float) {
          // specialization-id: stream-sliding-float-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferFloat(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
                val v = source.readFloatPhysical(Double.MaxValue)
                if (v == Double.MaxValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toFloat)
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else if (et eq JvmType.Double) {
          // specialization-id: stream-sliding-double-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferDouble(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
                val status = source.readDoublesPhysical(doubleValue, 0, 1)
                if (status < 0) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(doubleValue(0))
              }
              buf.toChunk.asInstanceOf[A1]
            }
          }
        } else {
          // specialization-id: stream-sliding-reference-reader
          new Reader.SyncReader[Chunk[A]] {
            private val buf                            = new zio.blocks.streams.internal.CircularBufferRef(n)
            private var firstWindow                    = true
            private var done                           = false
            def close(): Unit                          = { done = true; source.close() }
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
          }
        }
    }
    new Stream.StatefulReaderStream[E, A, Chunk[A]](
      self,
      materializeRoot,
      s"${this.render}.sliding($n, $step)",
      ElementRepresentation.Boxed
    )
  }

  /**
   * Materializes this stream as a caller-owned asynchronous reader. The caller
   * must drive and await `close()` on the published reader.
   */
  def startAsync: Async[Reader.AsyncReader[A]] =
    acquireReaderAsync

  /**
   * Emits at most the first `n` elements, then closes upstream. `n <= 0` yields
   * an empty stream without pulling an element.
   */
  def take(n: Long): Stream[E, A] = this match {
    case dropped: Stream.Dropped[E @unchecked, A @unchecked] =>
      dropped.self match {
        case source: Stream.ProtectedAsyncSource[E @unchecked, A @unchecked] =>
          new Stream.ProtectedAsyncTakeDrop(source.rawAcquisition, source.elementRepresentation, dropped.n, n)
        case _ => new Stream.TakeDrop(dropped.self, dropped.n, n)
      }
    case _ => new Stream.Taken(this, n)
  }

  /**
   * Emits elements while `pred` holds, then closes on the first element where
   * `pred` returns `false`.
   */
  def takeWhile(pred: A => Boolean): Stream[E, A] =
    new Stream.TakenWhile(this, pred)

  /**
   * Tests elements sequentially and emits them while the asynchronous predicate
   * holds, then closes upstream at the first `false`. Predicate failure is a
   * defect.
   */
  def takeWhileAsync(pred: A => Async[Boolean]): Stream[E, A] =
    new Stream.AsyncTakeWhile(this, pred)

  /**
   * Applies `f` to each element for side-effects, passing the element through.
   */
  def tapEach(f: A => Unit): Stream[E, A] =
    new Stream.Filtered[E, A](this, a => { f(a); true })

  /** Runs an asynchronous effect for each element and passes it through. */
  def tapEachAsync(f: A => Async[Unit]): Stream[E, A] =
    new Stream.AsyncTapped(this, f)

  /** Returns [[render]]. */
  override def toString: String = render

  /** Uses an asynchronous reader and awaits its close on every outcome. */
  def useReaderAsync[Z](f: Reader.AsyncReader[A] => Async[Z]): Async[Z] =
    this match {
      case source: Stream.AsyncSource[_, _] =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[A]]]
        if (source.protectAcquisition) new Stream.AsyncReaderUseProtected[A, Z](acquire, f)
        else new Stream.AsyncReaderUse[A, Z](acquire, f)
      case _ => useReaderManagedAsync(f)
    }

  /** Transforms this stream by applying a [[Pipeline]]. */
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B] =
    pipe.applyToStream(this)

  private[streams] def acquireReader: Async[Reader[A]] =
    this match {
      case source: Stream.AsyncSource[_, _]             => source.acquire().asInstanceOf[Async[Reader[A]]]
      case mapped: Stream.Mapped[_, _, _]               => mapped.acquireDirect().asInstanceOf[Async[Reader[A]]]
      case mapped: Stream.ProtectedAsyncMapped[_, _, _] =>
        mapped.acquireDirect().asInstanceOf[Async[Reader[A]]]
      case mapped: Stream.ProtectedIntAsyncMappedInt[_] =>
        mapped.acquireDirect().asInstanceOf[Async[Reader[A]]]
      case mapped: Stream.AsyncMapped[_, _, _] => mapped.acquireDirect().asInstanceOf[Async[Reader[A]]]
      case _                                   =>
        try Async.succeed(Stream.compileToReader(this))
        catch {
          case error: StreamError if error.isTrusted => Async.failTrusted(error)
          case cause: Throwable                      => Async.fail(cause)
        }
    }

  private def acquireReaderAsync: Async[Reader.AsyncReader[A]] =
    acquireReader.map {
      case reader: Reader.SyncReader[A @unchecked]  => reader.toAsync
      case reader: Reader.AsyncReader[A @unchecked] => reader
    }

  /** Compiles this stream into its sealed materialized [[Reader]] kind. */
  private[streams] def compile(depth: Int, bufferSize: Int): Reader[A]

  private[streams] def compile(depth: Int): Reader[A] = compile(depth, Stream.DefaultBufferSize)

  /**
   * Compiles a JVM blocking terminal without merging statically synchronous
   * reader allocations with the asynchronous reader kind.
   */
  private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] =
    Stream.compileBlockingFallback(this, bufferSize)

  /** Compiles this stream description into the given [[SyncInterpreter]]. */
  private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit

  private[streams] def elementRepresentation: ElementRepresentation = ElementRepresentation.LateBound

  /**
   * Eliminates the materialized Reader kind without first merging both kinds
   * into a root-typed value. Statically synchronous nodes override this method
   * to retain a typed allocation path for blocking terminals.
   */
  private[streams] def foldReaderKind[Z](
    onSync: Reader.SyncReader[A] => Z,
    onAsync: Reader.AsyncReader[A] => Z
  ): Z =
    compile(0, Stream.DefaultBufferSize) match {
      case reader: Reader.SyncReader[A @unchecked]  => onSync(reader)
      case reader: Reader.AsyncReader[A @unchecked] => onAsync(reader)
    }

  /**
   * Peels one node for the private async materializer. Implementations only
   * enqueue encoding work; callbacks and readers are not acquired while the
   * graph is being validated.
   */
  private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
    val outType = elementRepresentation.stableJvmType.getOrElse(JvmType.AnyRef)
    pipeline.deferAsyncReader(() =>
      Reader.closed.toAsync.concatAsyncWithJvmType(
        () => Async.reschedule(() => Async.succeed(compile(0, Stream.DefaultBufferSize))),
        outType
      )
    )
    null
  }

  private[streams] final def runBlocking[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Either[E3, Z] =
    try {
      val reader             = compileBlocking(Stream.DefaultBufferSize)
      var result: Z          = null.asInstanceOf[Z]
      var primary: Throwable = null
      try result = sink.drain(reader)
      catch { case cause: Throwable => primary = cause }
      try reader.close()
      catch { case cleanup: Throwable => primary = StreamError.attachCleanupReplay(primary, cleanup) }
      if (primary ne null) throw primary
      Right(result)
    } catch {
      case error: StreamError if error.isTrusted && !error.cleanupFailed =>
        Left(
          if (error.isSinkOrigin) errorConcat.right(error.value.asInstanceOf[ES])
          else errorConcat.left(error.value.asInstanceOf[E])
        )
    }

  private[streams] def runFoldDoubleBlocking(z: Double, f: (Double, A) => Double): Either[E, Double] =
    runBlocking(new Sink.FoldLeftDouble(z, f))

  private[streams] def runFoldIntBlocking(z: Int, f: (Int, A) => Int): Either[E, Int] =
    runBlocking(new Sink.FoldLeftInt(z, f))

  private[streams] def runFoldLongBlocking(z: Long, f: (Long, A) => Long): Either[E, Long] =
    runBlocking(new Sink.FoldLeftLong(z, f))

  private[streams] def runFoldGenericBlocking[Z](z: Z, f: (Z, A) => Z)(implicit
    jtZ: JvmType.Infer[Z]
  ): Either[E, Z] = runBlocking(Sink.foldLeft(z)(f))

  private[streams] def runFoldLongTransformedIntBlocking(
    z: Long,
    map: Int => Int,
    predicate: Int => Boolean,
    fold: (Long, Int) => Long
  ): Either[E, Long] =
    try {
      val reader             = compileBlocking(Stream.DefaultBufferSize)
      var result             = 0L
      var primary: Throwable = null
      try result = Sink.foldTransformedIntLong(reader, z, map, predicate, fold)
      catch { case cause: Throwable => primary = cause }
      try reader.close()
      catch { case cleanup: Throwable => primary = StreamError.attachCleanupReplay(primary, cleanup) }
      if (primary ne null) throw primary
      Right(result)
    } catch {
      case error: StreamError if error.isTrusted && !error.cleanupFailed => Left(error.value.asInstanceOf[E])
    }

  private[streams] def runFoldLongMappedIntChainBlocking(
    z: Long,
    maps: Array[Int => Int],
    fold: (Long, Int) => Long
  ): Either[E, Long] =
    try {
      val reader             = compileBlocking(Stream.DefaultBufferSize)
      var result             = 0L
      var primary: Throwable = null
      try result = Sink.foldMappedChainIntLong(reader, z, maps, fold)
      catch { case cause: Throwable => primary = cause }
      try reader.close()
      catch { case cleanup: Throwable => primary = StreamError.attachCleanupReplay(primary, cleanup) }
      if (primary ne null) throw primary
      Right(result)
    } catch {
      case error: StreamError if error.isTrusted && !error.cleanupFailed => Left(error.value.asInstanceOf[E])
    }

  private def runSinkManagedAsync[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Async[Either[E3, Z]] =
    useSinkManagedAsync(sink).foldCause[Either[E3, Z]] {
      case error: StreamError if error.isTrusted && !error.cleanupFailed =>
        Left(
          if (error.isSinkOrigin) errorConcat.right(error.value.asInstanceOf[ES])
          else errorConcat.left(error.value.asInstanceOf[E])
        )
      case failure => throw failure
    }(value => Right(value))

  private def useReaderManagedAsync[Z](use: Reader.AsyncReader[A] => Async[Z]): Async[Z] = {
    var useFailure: Throwable = null
    Async.bracketAsync[Reader.AsyncReader[A], Z](
      () => acquireReaderAsync,
      reader => {
        val effect =
          try Sink.readerCallbackAsync(reader)(use)
          catch {
            case cause: Throwable =>
              useFailure = cause
              cause match {
                case error: StreamError if error.isTrusted => Async.failTrusted(error)
                case _                                     => Async.fail(cause)
              }
          }
        effect.catchAll { cause =>
          useFailure = cause
          cause match {
            case error: StreamError if error.isTrusted => Async.failTrusted(error)
            case _                                     => Async.fail(cause)
          }
        }
      },
      reader =>
        reader.close().catchAll { closeFailure =>
          if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
          else {
            StreamError.attachCleanupReplay(useFailure, closeFailure)
            Async.succeed(())
          }
        }
    )
  }

  private def useSinkManagedAsync[E2 >: E, Z](sink: Sink[E2, A, Z]): Async[Z] =
    new Stream.AsyncSinkUse[E2, A, Z](this, sink)
}

/**
 * Companion object for [[Stream]]. Provides factory constructors:
 *
 *   - '''Values''': `Stream.succeed`, [[Stream.fail fail]],
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
 *   - '''Advanced''': `Stream.fromReader`, [[Stream.suspend suspend]],
 *     [[Stream.flattenAll flattenAll]]
 */
object Stream {

  private[streams] def widenErrorLeft[E, E2, E3, A](
    stream: Stream[E, A],
    concat: Concat.WithOut[E, E2, E3]
  ): Stream[E3, A] =
    if (concat.isIdentityLike) stream.asInstanceOf[Stream[E3, A]] else stream.mapError(concat.left)

  private[streams] def widenErrorRight[E, E2, E3, A](
    stream: Stream[E2, A],
    concat: Concat.WithOut[E, E2, E3]
  ): Stream[E3, A] =
    if (concat.isIdentityLike) stream.asInstanceOf[Stream[E3, A]] else stream.mapError(concat.right)

  private[streams] def widenElemLeft[E, A, A2, A3](
    stream: Stream[E, A],
    concat: Concat.WithOut[A, A2, A3],
    jt: JvmType.Infer[A3]
  ): Stream[E, A3] =
    if (concat.isIdentityLike) stream.asInstanceOf[Stream[E, A3]] else stream.map(concat.left)(jt)

  private[streams] def widenElemRight[E, A, A2, A3](
    stream: Stream[E, A2],
    concat: Concat.WithOut[A, A2, A3],
    jt: JvmType.Infer[A3]
  ): Stream[E, A3] =
    if (concat.isIdentityLike) stream.asInstanceOf[Stream[E, A3]] else stream.map(concat.right)(jt)

  private sealed abstract class SyncScanInput[A, S](
    protected val reader: Reader.SyncReader[A],
    protected val f: (S, A) => S
  ) {
    protected var eof: Boolean = false
    final def isEOF: Boolean   = eof
    def next(state: S): S
  }

  private object SyncScanInput {
    def apply[A, S](reader: Reader.SyncReader[A], f: (S, A) => S): SyncScanInput[A, S] = reader.jvmType match {
      case JvmType.Boolean => new BooleanSyncScanInput(reader, f)
      case JvmType.Byte    => new ByteSyncScanInput(reader, f)
      case JvmType.Char    => new CharSyncScanInput(reader, f)
      case JvmType.Short   => new ShortSyncScanInput(reader, f)
      case JvmType.Int     => new IntSyncScanInput(reader, f)
      case JvmType.Long    => new LongSyncScanInput(reader, f)
      case JvmType.Float   => new FloatSyncScanInput(reader, f)
      case JvmType.Double  => new DoubleSyncScanInput(reader, f)
      case _               => new RefSyncScanInput(reader, f)
    }
  }

  private final class BooleanSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readBooleanPhysical(-1)
      eof = value < 0
      if (eof) state else f(state, (value != 0).asInstanceOf[A])
    }
  }

  private final class ByteSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readBytePhysical()
      eof = value < 0
      if (eof) state else f(state, value.toByte.asInstanceOf[A])
    }
  }

  private final class CharSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readCharPhysical(-1)
      eof = value < 0
      if (eof) state else f(state, value.toChar.asInstanceOf[A])
    }
  }

  private final class ShortSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readShortPhysical(Int.MinValue)
      eof = value == Int.MinValue
      if (eof) state else f(state, value.toShort.asInstanceOf[A])
    }
  }

  private final class IntSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readIntPhysical(Long.MinValue)
      eof = value == Long.MinValue
      if (eof) state else f(state, value.toInt.asInstanceOf[A])
    }
  }

  private final class LongSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    private val value     = new Array[Long](1)
    def next(state: S): S = {
      eof = reader.readLongsPhysical(value, 0, 1) < 0
      if (eof) state else f(state, value(0).asInstanceOf[A])
    }
  }

  private final class FloatSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    def next(state: S): S = {
      val value = reader.readFloatPhysical(Double.MaxValue)
      eof = value == Double.MaxValue
      if (eof) state else f(state, value.toFloat.asInstanceOf[A])
    }
  }

  private final class DoubleSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    private val value     = new Array[Double](1)
    def next(state: S): S = {
      eof = reader.readDoublesPhysical(value, 0, 1) < 0
      if (eof) state else f(state, value(0).asInstanceOf[A])
    }
  }

  private final class RefSyncScanInput[A, S](reader: Reader.SyncReader[A], f: (S, A) => S)
      extends SyncScanInput[A, S](reader, f) {
    private val end       = new AnyRef
    def next(state: S): S = {
      val value = reader.read[A](end.asInstanceOf[A])
      eof = value.asInstanceOf[AnyRef] eq end
      if (eof) state else f(state, value)
    }
  }

  private sealed abstract class SyncIntersperseReader[A](protected val source: Reader.SyncReader[_])
      extends Reader.SyncReader[A] {
    protected var first     = true
    protected var hasCached = false
    final def close(): Unit = source.close()
    final def isClosed      = source.isClosed
  }

  private final class SyncBooleanIntersperseReader(source0: Reader.SyncReader[_], sep: Boolean)
      extends SyncIntersperseReader[Boolean](source0) {
    private var cached                                                             = false
    override def jvmType                                                           = JvmType.Boolean
    override def readBoolean(sentinel: Int)(implicit ev: Boolean <:< Boolean): Int =
      if (hasCached) { hasCached = false; if (cached) 1 else 0 }
      else {
        val value = source.readBooleanPhysical(-1)
        if (value < 0) sentinel
        else if (first) { first = false; value }
        else { cached = value != 0; hasCached = true; if (sep) 1 else 0 }
      }
    def read[B >: Boolean](sentinel: B): B = {
      val value = readBoolean(-1)
      if (value < 0) sentinel else (value != 0).asInstanceOf[B]
    }
  }

  private final class SyncByteIntersperseReader(source0: Reader.SyncReader[_], sep: Byte)
      extends SyncIntersperseReader[Byte](source0) {
    private var cached: Byte     = 0
    override def jvmType         = JvmType.Byte
    override def readByte(): Int =
      if (hasCached) { hasCached = false; cached & 0xff }
      else {
        val value = source.readBytePhysical()
        if (value < 0) -1
        else if (first) { first = false; value }
        else { cached = value.toByte; hasCached = true; sep & 0xff }
      }
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Byte <:< Byte): Int = {
      Reader.validateArrayRange(dest, offset, length)
      var count = 0
      var value = 0
      while (count < length && { value = readByte(); value >= 0 }) { dest(offset + count) = value.toByte; count += 1 }
      if (count == 0 && length > 0) -1 else count
    }
    def read[B >: Byte](sentinel: B): B = {
      val value = readByte(); if (value < 0) sentinel else value.toByte.asInstanceOf[B]
    }
  }

  private final class SyncCharIntersperseReader(source0: Reader.SyncReader[_], sep: Char)
      extends SyncIntersperseReader[Char](source0) {
    private var cached: Char                                              = 0
    override def jvmType                                                  = JvmType.Char
    override def readChar(sentinel: Int)(implicit ev: Char <:< Char): Int =
      if (hasCached) { hasCached = false; cached.toInt }
      else {
        val value = source.readCharPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel
        else if (first) { first = false; value }
        else { cached = value.toChar; hasCached = true; sep.toInt }
      }
    def read[B >: Char](sentinel: B): B = {
      val value = readChar(Int.MinValue); if (value == Int.MinValue) sentinel else value.toChar.asInstanceOf[B]
    }
  }

  private final class SyncShortIntersperseReader(source0: Reader.SyncReader[_], sep: Short)
      extends SyncIntersperseReader[Short](source0) {
    private var cached: Short                                                = 0
    override def jvmType                                                     = JvmType.Short
    override def readShort(sentinel: Int)(implicit ev: Short <:< Short): Int =
      if (hasCached) { hasCached = false; cached.toInt }
      else {
        val value = source.readShortPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel
        else if (first) { first = false; value }
        else { cached = value.toShort; hasCached = true; sep.toInt }
      }
    def read[B >: Short](sentinel: B): B = {
      val value = readShort(Int.MinValue); if (value == Int.MinValue) sentinel else value.toShort.asInstanceOf[B]
    }
  }

  private final class SyncIntIntersperseReader(source0: Reader.SyncReader[_], sep: Int)
      extends SyncIntersperseReader[Int](source0) {
    private var cached                                                   = 0
    override def jvmType                                                 = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (hasCached) { hasCached = false; cached.toLong }
      else {
        val value = source.readIntPhysical(Long.MinValue)
        if (value == Long.MinValue) sentinel
        else if (first) { first = false; value }
        else { cached = value.toInt; hasCached = true; sep.toLong }
      }
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Int <:< Int): Int = {
      Reader.validateArrayRange(dest, offset, length)
      var count = 0
      var value = 0L
      while (count < length && { value = readInt(Long.MinValue); value != Long.MinValue }) {
        dest(offset + count) = value.toInt; count += 1
      }
      if (count == 0 && length > 0) -1 else count
    }
    def read[B >: Int](sentinel: B): B = {
      val value = readInt(Long.MinValue); if (value == Long.MinValue) sentinel else value.toInt.asInstanceOf[B]
    }
  }

  private final class SyncLongIntersperseReader(source0: Reader.SyncReader[_], sep: Long)
      extends SyncIntersperseReader[Long](source0) {
    private var cached                                                      = 0L
    private val sourceValue                                                 = new Array[Long](1)
    override def jvmType                                                    = JvmType.Long
    override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Long = {
      val count = readLongs(sourceValue, 0, 1)
      if (count < 0) sentinel else sourceValue(0)
    }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Long <:< Long): Int = {
      Reader.validateArrayRange(dest, offset, length)
      var count = 0
      while (count < length && hasCached) { dest(offset + count) = cached; hasCached = false; count += 1 }
      while (count < length && source.readLongsPhysical(sourceValue, 0, 1) >= 0) {
        val value = sourceValue(0)
        if (first) { first = false; dest(offset + count) = value; count += 1 }
        else {
          dest(offset + count) = sep; count += 1
          if (count < length) { dest(offset + count) = value; count += 1 }
          else { cached = value; hasCached = true }
        }
      }
      if (count == 0 && length > 0) -1 else count
    }
    def read[B >: Long](sentinel: B): B = {
      val count = readLongs(sourceValue, 0, 1); if (count < 0) sentinel else sourceValue(0).asInstanceOf[B]
    }
  }

  private final class SyncFloatIntersperseReader(source0: Reader.SyncReader[_], sep: Float)
      extends SyncIntersperseReader[Float](source0) {
    private var cached                                                             = 0.0f
    override def jvmType                                                           = JvmType.Float
    override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Double =
      if (hasCached) { hasCached = false; cached.toDouble }
      else {
        val value = source.readFloatPhysical(Double.MaxValue)
        if (value == Double.MaxValue) sentinel
        else if (first) { first = false; value }
        else { cached = value.toFloat; hasCached = true; sep.toDouble }
      }
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Float <:< Float): Int = {
      Reader.validateArrayRange(dest, offset, length)
      var count = 0
      var value = 0.0
      while (count < length && { value = readFloat(Double.MaxValue); value != Double.MaxValue }) {
        dest(offset + count) = value.toFloat; count += 1
      }
      if (count == 0 && length > 0) -1 else count
    }
    def read[B >: Float](sentinel: B): B = {
      val value = readFloat(Double.MaxValue); if (value == Double.MaxValue) sentinel else value.toFloat.asInstanceOf[B]
    }
  }

  private final class SyncDoubleIntersperseReader(source0: Reader.SyncReader[_], sep: Double)
      extends SyncIntersperseReader[Double](source0) {
    private var cached                                                                = 0.0
    private val sourceValue                                                           = new Array[Double](1)
    override def jvmType                                                              = JvmType.Double
    override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double = {
      val count = readDoubles(sourceValue, 0, 1)
      if (count < 0) sentinel else sourceValue(0)
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Double <:< Double): Int = {
      Reader.validateArrayRange(dest, offset, length)
      var count = 0
      while (count < length && hasCached) { dest(offset + count) = cached; hasCached = false; count += 1 }
      while (count < length && source.readDoublesPhysical(sourceValue, 0, 1) >= 0) {
        val value = sourceValue(0)
        if (first) { first = false; dest(offset + count) = value; count += 1 }
        else {
          dest(offset + count) = sep; count += 1
          if (count < length) { dest(offset + count) = value; count += 1 }
          else { cached = value; hasCached = true }
        }
      }
      if (count == 0 && length > 0) -1 else count
    }
    def read[B >: Double](sentinel: B): B = {
      val count = readDoubles(sourceValue, 0, 1); if (count < 0) sentinel else sourceValue(0).asInstanceOf[B]
    }
  }

  private final class BoxedSourcePull(reader: Reader.SyncReader[_]) {
    private val longs              = new Array[Long](1)
    private val doubles            = new Array[Double](1)
    def apply(end: AnyRef): AnyRef = reader.jvmType match {
      case JvmType.Boolean =>
        val v = reader.readBooleanPhysical(-1); if (v < 0) end else java.lang.Boolean.valueOf(v != 0)
      case JvmType.Byte => val v = reader.readBytePhysical(); if (v < 0) end else java.lang.Byte.valueOf(v.toByte)
      case JvmType.Char =>
        val v = reader.readCharPhysical(Int.MinValue);
        if (v == Int.MinValue) end else java.lang.Character.valueOf(v.toChar)
      case JvmType.Short =>
        val v = reader.readShortPhysical(Int.MinValue);
        if (v == Int.MinValue) end else java.lang.Short.valueOf(v.toShort)
      case JvmType.Int =>
        val v = reader.readIntPhysical(Long.MinValue);
        if (v == Long.MinValue) end else java.lang.Integer.valueOf(v.toInt)
      case JvmType.Long  => if (reader.readLongsPhysical(longs, 0, 1) < 0) end else java.lang.Long.valueOf(longs(0))
      case JvmType.Float =>
        val v = reader.readFloatPhysical(Double.MaxValue);
        if (v == Double.MaxValue) end else java.lang.Float.valueOf(v.toFloat)
      case JvmType.Double =>
        if (reader.readDoublesPhysical(doubles, 0, 1) < 0) end else java.lang.Double.valueOf(doubles(0))
      case _ => reader.asInstanceOf[Reader.SyncReader[AnyRef]].read[AnyRef](end)
    }
  }

  private final class SyncBoxedIntersperseReader(source0: Reader.SyncReader[_], sep: AnyRef)
      extends SyncIntersperseReader[AnyRef](source0) {
    private val end                       = new AnyRef
    private val sourcePull                = new BoxedSourcePull(source)
    private var cached: AnyRef            = null
    override def jvmType                  = JvmType.AnyRef
    def read[B >: AnyRef](sentinel: B): B =
      if (hasCached) { hasCached = false; cached.asInstanceOf[B] }
      else {
        val value = sourcePull(end)
        if (value eq end) sentinel
        else if (first) { first = false; value.asInstanceOf[B] }
        else { cached = value; hasCached = true; sep.asInstanceOf[B] }
      }
  }

  private trait SyncLaneVisitor[R] {
    def eof(): R
    def visit(value: Boolean): R; def visit(value: Byte): R; def visit(value: Char): R
    def visit(value: Short): R; def visit(value: Int): R; def visit(value: Long): R
    def visit(value: Float): R; def visit(value: Double): R; def visit(value: AnyRef): R
  }
  private trait SyncLanePull[R] { def pull(visitor: SyncLaneVisitor[R]): R }
  private object SyncLanePull   {
    def apply[R](reader: Reader.SyncReader[_]): SyncLanePull[R] = reader.jvmType match {
      case JvmType.Boolean =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readBooleanPhysical(-1); if (x < 0) v.eof() else v.visit(x != 0)
          }
        }
      case JvmType.Byte =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readBytePhysical(); if (x < 0) v.eof() else v.visit(x.toByte)
          }
        }
      case JvmType.Char =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readCharPhysical(Int.MinValue); if (x == Int.MinValue) v.eof() else v.visit(x.toChar)
          }
        }
      case JvmType.Short =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readShortPhysical(Int.MinValue); if (x == Int.MinValue) v.eof() else v.visit(x.toShort)
          }
        }
      case JvmType.Int =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readIntPhysical(Long.MinValue); if (x == Long.MinValue) v.eof() else v.visit(x.toInt)
          }
        }
      case JvmType.Long =>
        new SyncLanePull[R] {
          private val a                   = new Array[Long](1);
          def pull(v: SyncLaneVisitor[R]) = if (reader.readLongsPhysical(a, 0, 1) < 0) v.eof() else v.visit(a(0))
        }
      case JvmType.Float =>
        new SyncLanePull[R] {
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.readFloatPhysical(Double.MaxValue); if (x == Double.MaxValue) v.eof() else v.visit(x.toFloat)
          }
        }
      case JvmType.Double =>
        new SyncLanePull[R] {
          private val a                   = new Array[Double](1);
          def pull(v: SyncLaneVisitor[R]) = if (reader.readDoublesPhysical(a, 0, 1) < 0) v.eof() else v.visit(a(0))
        }
      case _ =>
        new SyncLanePull[R] {
          private val end                 = new AnyRef;
          def pull(v: SyncLaneVisitor[R]) = {
            val x = reader.asInstanceOf[Reader.SyncReader[AnyRef]].read[AnyRef](end);
            if (x eq end) v.eof() else v.visit(x)
          }
        }
    }
  }

  private trait AsyncLaneVisitor[R] {
    def eof(): Async[R]
    def visit(value: Boolean): Async[R]; def visit(value: Byte): Async[R]; def visit(value: Char): Async[R]
    def visit(value: Short): Async[R]; def visit(value: Int): Async[R]; def visit(value: Long): Async[R]
    def visit(value: Float): Async[R]; def visit(value: Double): Async[R]; def visit(value: AnyRef): Async[R]
  }
  private trait AsyncLanePull[R] { def pull(visitor: AsyncLaneVisitor[R]): Async[R] }
  private object AsyncLanePull   {
    def apply[R](reader: Reader.AsyncReader[_]): AsyncLanePull[R] = reader.jvmType match {
      case JvmType.Boolean =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readBooleanPhysical(-1).flatMap(x => if (x < 0) v.eof() else v.visit(x != 0))
        }
      case JvmType.Byte =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readBytePhysical().flatMap(x => if (x < 0) v.eof() else v.visit(x.toByte))
        }
      case JvmType.Char =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readCharPhysical(Int.MinValue).flatMap(x => if (x == Int.MinValue) v.eof() else v.visit(x.toChar))
        }
      case JvmType.Short =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readShortPhysical(Int.MinValue).flatMap(x => if (x == Int.MinValue) v.eof() else v.visit(x.toShort))
        }
      case JvmType.Int =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readIntPhysical(Long.MinValue).flatMap(x => if (x == Long.MinValue) v.eof() else v.visit(x.toInt))
        }
      case JvmType.Long =>
        new AsyncLanePull[R] {
          private val a                    = new Array[Long](1);
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readLongsPhysical(a, 0, 1).flatMap(n => if (n < 0) v.eof() else v.visit(a(0)))
        }
      case JvmType.Float =>
        new AsyncLanePull[R] {
          def pull(v: AsyncLaneVisitor[R]) = reader
            .readFloatPhysical(Double.MaxValue)
            .flatMap(x => if (x == Double.MaxValue) v.eof() else v.visit(x.toFloat))
        }
      case JvmType.Double =>
        new AsyncLanePull[R] {
          private val a                    = new Array[Double](1);
          def pull(v: AsyncLaneVisitor[R]) =
            reader.readDoublesPhysical(a, 0, 1).flatMap(n => if (n < 0) v.eof() else v.visit(a(0)))
        }
      case _ =>
        new AsyncLanePull[R] {
          private val end                  = new AnyRef;
          def pull(v: AsyncLaneVisitor[R]) = reader
            .asInstanceOf[Reader.AsyncReader[AnyRef]]
            .read[AnyRef](end)
            .flatMap(x => if (x eq end) v.eof() else v.visit(x))
        }
    }
  }

  private trait SyncZipPair[A, B, C] { def eof: Boolean; def pull(): C }
  private object SyncZipPair         {
    def apply[A, B, C](
      left: Reader.SyncReader[A],
      right: Reader.SyncReader[B],
      zip: Zip[A, B, C]
    ): SyncZipPair[A, B, C] = {
      val leftPull = SyncLanePull[C](left); val rightPull = SyncLanePull[C](right);
      left.jvmType match {
        case JvmType.Boolean =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Boolean = false
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Byte =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Byte = 0.toByte
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Char =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Char = 0.toChar
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Short =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Short = 0.toShort
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Int =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Int = 0
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Long =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Long = 0L
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Float =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Float = 0.0f
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Double =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: Double = 0.0
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: AnyRef): C  = throw new IllegalStateException("zip lane type mismatch")
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.AnyRef =>
          new SyncZipPair[A, B, C] {
            private var ended        = false; private var value: AnyRef = null
            private val rightVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Byte): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Char): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Short): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Int): C     = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Long): C    = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Float): C   = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: Double): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]));
              def visit(v: AnyRef): C  = StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B]))
            }
            private val leftVisitor = new SyncLaneVisitor[C] {
              def eof(): C             = { ended = true; null.asInstanceOf[C] };
              def visit(v: Boolean): C = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Byte): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Char): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Short): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Int): C     = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Long): C    = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Float): C   = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: Double): C  = throw new IllegalStateException("zip lane type mismatch");
              def visit(v: AnyRef): C  = { value = v; rightPull.pull(rightVisitor) }
            }
            def eof: Boolean = ended; def pull(): C = { ended = false; leftPull.pull(leftVisitor) }
          }
      }
    }
  }
  private trait AsyncZipPair[A, B, C] { def eof: Boolean; def pull(): Async[C] }
  private object AsyncZipPair         {
    def apply[A, B, C](
      left: Reader.AsyncReader[A],
      right: Reader.AsyncReader[B],
      zip: Zip[A, B, C]
    ): AsyncZipPair[A, B, C] = {
      val leftPull = AsyncLanePull[C](left); val rightPull = AsyncLanePull[C](right);
      left.jvmType match {
        case JvmType.Boolean =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Boolean = false
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Byte =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Byte = 0.toByte
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Char =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Char = 0.toChar
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Short =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Short = 0.toShort
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Int =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Int = 0
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Long =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Long = 0L
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Float =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Float = 0.0f
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.Double =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: Double = 0.0
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = { value = v; rightPull.pull(rightVisitor) };
              def visit(v: AnyRef): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"))
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
        case JvmType.AnyRef =>
          new AsyncZipPair[A, B, C] {
            private var ended        = false; private var value: AnyRef = null
            private val rightVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Byte): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Char): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Short): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Int): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Long): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Float): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: Double): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) };
              def visit(v: AnyRef): Async[C] = try
                Async.succeed(StreamError.callback(zip.combine(value.asInstanceOf[A], v.asInstanceOf[B])))
              catch { case cause: Throwable => Async.fail(cause) }
            }
            private val leftVisitor = new AsyncLaneVisitor[C] {
              def eof(): Async[C]             = { ended = true; Async.succeed(null.asInstanceOf[C]) };
              def visit(v: Boolean): Async[C] = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Byte): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Char): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Short): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Int): Async[C]     = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Long): Async[C]    = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Float): Async[C]   = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: Double): Async[C]  = Async.fail(new IllegalStateException("zip lane type mismatch"));
              def visit(v: AnyRef): Async[C]  = { value = v; rightPull.pull(rightVisitor) }
            }
            def eof: Boolean = ended; def pull(): Async[C] = { ended = false; leftPull.pull(leftVisitor) }
          }
      }
    }
  }

  /**
   * Variance-safe adapter for tuple-flattening zip composition. Instances are
   * derived from [[zio.blocks.combinators.Tuples.Tuples]].
   */
  sealed trait Zip[-A, -B, +C] {

    /** Combines one element from each input lane into the zipped output. */
    def combine(left: A, right: B): C
  }

  /** Derives [[Zip]] instances from tuple-flattening [[Tuples.Tuples]]. */
  object Zip {

    /**
     * Derives a zip adapter from the corresponding tuple-composition instance.
     */
    implicit def fromTuples[A, B, C](implicit tuples: Tuples.Tuples[A, B] { type Out = C }): Zip[A, B, C] =
      new Zip[A, B, C] {
        def combine(left: A, right: B): C = tuples.combine(left, right)
      }
  }

  /**
   * Internal signal that the synchronous compiler encountered a genuinely
   * asynchronous boundary. JVM plain terminals catch only this signal and retry
   * through sealed-reader materialization; user exceptions, including
   * UnsupportedOperationException, are never evaluated twice.
   */
  private[streams] case object AsyncBoundaryRequired
      extends Throwable("asynchronous stream boundary")
      with scala.util.control.NoStackTrace

  /**
   * Creates a reusable stream from the given elements, preserving argument
   * order.
   */
  def apply[A](as: A*)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] = {
    val label =
      if (as.isEmpty) "Stream()"
      else if (as.length <= 5) s"Stream(${as.mkString(", ")})"
      else s"Stream(${as.take(5).mkString(", ")}, ...)"
    new FromSyncReader(
      () => Reader.fromChunk[A](Chunk.fromIterable(as))(jt),
      label,
      ElementRepresentation.fromJvmType(jt.jvmType)
    )
  }

  /**
   * Lazily evaluates `f` once per materialization and emits its result.
   * Non-fatal exceptions become typed errors; fatal throwables remain defects.
   */
  def attempt[A](f: => A)(implicit jtA: JvmType.Infer[A]): Stream[Throwable, A] =
    new FromSyncReader(
      () =>
        try Reader.single(f)(jtA)
        catch { case scala.util.control.NonFatal(e) => new FailedReader(StreamError.source(e), jtA.jvmType) },
      "Stream.attempt(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

  /**
   * Lazily evaluates an asynchronous thunk once per materialization and emits
   * its result. Non-fatal synchronous throws and asynchronous failures become
   * typed errors; fatal throwables remain defects.
   */
  def attemptAsync[A](f: => Async[A])(implicit jtA: JvmType.Infer[A]): Stream[Throwable, A] =
    new GenericAsyncSource[Throwable, A](
      () =>
        StreamError.callbackAsync(f).map[Reader[A]](value => Reader.single(value)(jtA)).catchAll {
          case scala.util.control.NonFatal(cause) =>
            Async.succeed(new FailedReader(StreamError.source(cause), jtA.jvmType))
          case cause => Async.fail(cause)
        },
      "Stream.attemptAsync(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

  /**
   * Lazily executes `f` once per materialization and emits nothing. Non-fatal
   * exceptions become typed errors; fatal throwables remain defects.
   */
  def attemptEval(f: => Any): Stream[Throwable, Nothing] =
    suspend {
      try { f; empty }
      catch { case scala.util.control.NonFatal(e) => fail(e) }
    }

  /**
   * Lazily executes an asynchronous effect once per materialization and emits
   * nothing. Non-fatal synchronous throws and asynchronous failures become
   * typed errors; fatal throwables remain defects.
   */
  def attemptEvalAsync(f: => Async[Any]): Stream[Throwable, Nothing] =
    new GenericAsyncSource[Throwable, Nothing](
      () =>
        StreamError.callbackAsync(f).map[Reader[Nothing]](_ => Reader.closed).catchAll {
          case scala.util.control.NonFatal(cause) => Async.succeed(new FailedReader(StreamError.source(cause)))
          case cause                              => Async.fail(cause)
        },
      "Stream.attemptEvalAsync(...)"
    )

  /**
   * Lazily constructs `body` with `n` as the materialization buffer size for
   * concurrent operators in that region. Nested regions use the innermost size.
   * `n` must be a positive power of two.
   */
  def bufferSize[E, A](n: Int)(body: => Stream[E, A]): Stream[E, A] = {
    require(n >= 1 && (n & (n - 1)) == 0, s"bufferSize must be a positive power of 2, got $n")
    new Stream.WithBufferSize(body, n)
  }

  /**
   * Creates an empty stream that lazily registers `f` as a release action. `f`
   * runs exactly once when each materialization closes, including after
   * failure, early termination, or cancellation; exceptions are defects.
   */
  def defer(f: => Unit): Stream[Nothing, Nothing] =
    new FromSyncReader[Nothing, Nothing](
      () => Reader.closed.withRelease(() => StreamError.callback(f)).asInstanceOf[Reader.SyncReader[Nothing]],
      "Stream.defer(...)",
      ElementRepresentation.Boxed
    )

  /**
   * Creates an empty stream that lazily registers an asynchronous release
   * action. It is awaited exactly once when each materialization closes,
   * including after failure, early termination, or cancellation; failure is a
   * defect.
   */
  def deferAsync(finalizer: => Async[Unit]): Stream[Nothing, Nothing] =
    new StableAsyncSource(
      () => Reader.closed.toAsync.withReleaseAsync(() => StreamError.callbackAsync(finalizer)),
      "Stream.deferAsync(...)",
      ElementRepresentation.Boxed
    )

  /** Creates a stream that, when pulled, terminates with `t` as a defect. */
  def die(t: Throwable): Stream[Nothing, Nothing] =
    new FromSyncReader(() => new DyingReader(t), "Stream.die(...)", ElementRepresentation.Boxed)

  /** The empty stream that emits no elements. */
  val empty: Stream[Nothing, Nothing] =
    new FromSyncReader(() => Reader.closed, "Stream.empty", ElementRepresentation.Boxed) {
      override def knownChunk: Option[Chunk[Nothing]] = Some(Chunk.empty)
      override def knownLength: Option[Long]          = Some(0L)
    }

  /**
   * Lazily executes `f` once per materialization and emits nothing. Any thrown
   * exception is a defect.
   */
  def eval(f: => Any): Stream[Nothing, Nothing] =
    suspend { f; empty }

  /**
   * Lazily executes an asynchronous effect once per materialization and emits
   * nothing; synchronous throws and asynchronous failures are defects.
   */
  def evalAsync(f: => Async[Any]): Stream[Nothing, Nothing] =
    new GenericAsyncSource(
      () => StreamError.callbackAsync(f).map[Reader[Nothing]](_ => Reader.closed),
      "Stream.evalAsync(...)"
    )

  /**
   * Creates a stream that, when pulled, fails in the typed channel with
   * `error`.
   */
  def fail[E](error: E): Stream[E, Nothing] =
    new FromSyncReader(
      () => new FailedReader(StreamError.source(error)),
      "Stream.fail(...)",
      ElementRepresentation.Boxed
    )

  /**
   * Consumes the inner streams sequentially in outer-stream order, closing each
   * inner stream before advancing to the next.
   */
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    streams.flatMap(identity)(Concat.derive[E, E].asInstanceOf[Concat.WithOut[E, E, E]], jtA)

  /**
   * Lazily acquires one resource per materialization, constructs the stream
   * with `use`, and releases the resource exactly once after completion,
   * failure, early termination, or cancellation. If omitted, `release` closes
   * an `AutoCloseable` and otherwise does nothing. Thrown exceptions are
   * defects; use [[attempt]] to model acquisition failures as typed errors.
   */
  def fromAcquireRelease[R, E, A](
    acquire: => R,
    release: R => Unit = (r: R) => r match { case ac: AutoCloseable => ac.close(); case _ => () }
  )(use: R => Stream[E, A]): Stream[E, A] =
    new FromAcquireRelease(acquire, release, use)

  /**
   * Lazily acquires one resource per materialization, constructs the stream
   * with `use`, and awaits release exactly once after completion, failure,
   * early termination, or cancellation (including cancellation during
   * acquisition after the resource is obtained). Acquisition, `use`, and
   * release failures are defects.
   */
  def fromAcquireReleaseAsync[R, E, A](
    acquire: => Async[R],
    release: R => Async[Unit]
  )(use: R => Stream[E, A])(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    new FromAcquireReleaseAsync(() => acquire, release, use, jtA)

  /**
   * Creates a stream from an array. The array is wrapped via `Chunk.fromArray`
   * without copying, so later array mutation may be observable.
   */
  def fromArray[A](array: Array[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    fromChunk(Chunk.fromArray(array))

  /** Creates a reusable, already-materialized stream backed by `chunk`. */
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromChunkStream(chunk, jt)

  /**
   * Lazily wraps an already-created [[java.io.InputStream]] as bytes. Read
   * failures are typed [[java.io.IOException]] values; close failures are
   * defects. The stream is closed after completion, failure, early termination,
   * or cancellation; each materialization reuses the same supplied instance.
   */
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Byte] =
    new FromAcquireRelease(
      is,
      (s: java.io.InputStream) => s.close(),
      (s: java.io.InputStream) => fromInputStreamUnmanaged(s),
      ElementRepresentation.Known(JvmType.Byte)
    )

  /**
   * Wraps a [[java.io.InputStream]] as a stream of bytes without managing its
   * lifecycle. I/O failures are typed [[java.io.IOException]] values. The
   * caller owns closing, and each materialization reuses the supplied instance.
   */
  def fromInputStreamUnmanaged(is: java.io.InputStream): Stream[java.io.IOException, Byte] =
    new FromSyncReader(
      () => Reader.fromInputStream(is),
      "Stream.fromInputStreamUnmanaged(...)",
      ElementRepresentation.Known(JvmType.Byte)
    )

  /**
   * Lazily obtains and consumes an iterator from `it` for each materialization,
   * preserving iteration order. If the iterable has a known size, `knownLength`
   * is set. Iterator exceptions are defects.
   */
  def fromIterable[A](it: Iterable[A])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A] = {
    val size = it.knownSize
    if (it.isInstanceOf[Vector[_]])
      new FromVectorStream(it.asInstanceOf[Vector[A]], jtA)
    else if (size >= 0)
      new FromSyncReader(
        () => Reader.fromIterable[A](it),
        "Stream.fromIterable(...)",
        ElementRepresentation.fromJvmType(jtA.jvmType)
      ) {
        override def knownLength: Option[Long] = Some(size.toLong)
      }
    else
      new FromSyncReader(
        () => Reader.fromIterable[A](it),
        "Stream.fromIterable(...)",
        ElementRepresentation.fromJvmType(jtA.jvmType)
      )
  }

  /**
   * Evaluates `it` once per materialization and consumes it in order. Supply a
   * fresh iterator for repeatable runs; iterator and thunk exceptions are
   * defects.
   */
  def fromIterator[A](it: => Iterator[A])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromSyncReader(
      () => iteratorReader(StreamError.callback(it)),
      "Stream.fromIterator(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

  /**
   * Asynchronously obtains one iterator per materialization and consumes it in
   * order. Acquisition and iterator failures are defects.
   */
  def fromIteratorAsync[A](it: => Async[Iterator[A]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A] =
    fromReaderAsync(StreamError.callbackAsync(it).map(iter => iteratorReader(iter)))

  /**
   * Lazily wraps an already-created [[java.io.Reader]] as characters. Read
   * failures are typed [[java.io.IOException]] values; close failures are
   * defects. The reader is closed after completion, failure, early termination,
   * or cancellation; each materialization reuses the same supplied instance.
   */
  def fromJavaReader(r: java.io.Reader): Stream[java.io.IOException, Char] =
    new FromAcquireRelease(
      r,
      (w: java.io.Reader) => w.close(),
      (w: java.io.Reader) => fromJavaReaderUnmanaged(w),
      ElementRepresentation.Known(JvmType.Char)
    )

  /**
   * Wraps a [[java.io.Reader]] as a stream of Chars without managing its
   * lifecycle. I/O failures are typed [[java.io.IOException]] values. The
   * caller owns closing, and each materialization reuses the supplied instance.
   */
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[java.io.IOException, Char] =
    new FromSyncReader(
      () => Reader.fromReader(r),
      "Stream.fromJavaReaderUnmanaged(...)",
      ElementRepresentation.Known(JvmType.Char)
    )

  /**
   * Creates a stream of integers from a Scala `Range`. The `knownLength` is set
   * from the range size.
   */
  def fromRange(range: Range): Stream[Nothing, Int] =
    new FromSyncReader(
      () => Reader.fromRange(range),
      "Stream.fromRange(...)",
      ElementRepresentation.Known(JvmType.Int)
    ) {
      override def knownLength: Option[Long] = Some(range.size.toLong)
      override private[streams] def runFoldLongBlocking(
        z: Long,
        f: (Long, Int) => Long
      ): Either[Nothing, Long] = runFoldLongTransformedIntBlocking(z, null, null, f)
    }

  /**
   * Lazily obtains one `Reader` per materialization and closes it when the
   * stream closes. Reader-thunk failures are defects. This advanced overload
   * preserves whether the returned reader is synchronous or asynchronous.
   */
  def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A] =
    new FromReader(() => StreamError.callback(mkReader))

  /**
   * Lazily obtains one synchronous reader per materialization and closes it
   * when the stream closes. Reader-thunk failures are defects.
   */
  def fromReader[E, A](mkReader: => Reader.SyncReader[A])(implicit
    dummy1: DummyImplicit,
    dummy2: DummyImplicit
  ): Stream[E, A] = {
    val _ = (dummy1, dummy2)
    new FromSyncReader(() => StreamError.callback(mkReader), "Stream.fromReader(...)")
  }

  /**
   * Preserves overload resolution for a reader thunk that never returns.
   * Evaluation remains lazy and a thrown exception is a defect.
   */
  def fromReader[E, A](mkReader: => Nothing)(implicit
    dummy1: DummyImplicit,
    dummy2: DummyImplicit,
    dummy3: DummyImplicit
  ): Stream[E, A] = {
    val _ = (dummy1, dummy2, dummy3)
    new FromReader(() => { mkReader; Reader.closed.asInstanceOf[Reader[A]] })
  }

  /**
   * Lazily obtains one asynchronous reader per materialization and awaits its
   * close when the stream closes. Reader-thunk failures are defects.
   */
  def fromReader[E, A](mkReader: => Reader.AsyncReader[A])(implicit dummy: DummyImplicit): Stream[E, A] = {
    val _ = dummy
    new StableAsyncSource(() => StreamError.callback(mkReader), "Stream.fromReader(...)")
  }

  /**
   * Lazily runs `mkReader` once per materialization and closes the resulting
   * reader when the stream closes. Effect and thunk failures are defects.
   */
  def fromReaderAsync[E, A](mkReader: => Async[Reader[A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    new ProtectedAsyncSource(() => mkReader, ElementRepresentation.fromJvmType(jtA.jvmType))

  /**
   * Lazily creates a `Scope` per materialization, acquires `resource` in it,
   * and closes the scope after completion, failure, early termination, or
   * cancellation. Acquisition and scope-finalization failures are defects.
   */
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A] =
    new FromResource(resource, use)

  /**
   * Merges up to `maxOpen` inner streams concurrently into a single output
   * stream. Elements arrive in completion order (unordered with respect to
   * input position). On JVM, each inner stream runs on a virtual thread. On JS,
   * degrades to sequential `flatMap`. `maxOpen` must be at least one. Failure
   * or cancellation closes the outer stream and all active inner streams.
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
    if (maxOpen == 1)
      streams match {
        case mapped: Mapped[_, _, _] =>
          mapped.self
            .asInstanceOf[Stream[E, Any]]
            .flatMap(mapped.f.asInstanceOf[Any => Stream[E, A]])
        case _ => streams.flatMap(identity)
      }
    else new Stream.MergedAll[E, A](streams, maxOpen, jtA.jvmType)
  }

  /**
   * Creates a stream of integers from `from` (inclusive) to `until`
   * (exclusive).
   */
  def range(from: Int, until: Int): Stream[Nothing, Int] = {
    val range = Range(from, until)
    new IntRange(from, until, range.length)
  }

  /** Creates a lazy infinite stream that repeatedly emits `a`. */
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromSyncReader(
      () => Reader.repeat[A](a),
      "Stream.repeat(...)",
      ElementRepresentation.fromJvmType(jt.jvmType)
    )

  /** Creates a stream that emits a single Boolean. */
  def succeed(a: Boolean): Stream[Nothing, Boolean] =
    new FromSyncReader(
      () => Reader.singleBoolean(a),
      "Stream.succeed(...)",
      ElementRepresentation.Known(JvmType.Boolean)
    ) with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Boolean
      override def scalarBoolean: Boolean    = a
    }

  /**
   * Creates a stream that emits a single Byte.
   */
  def succeed(a: Byte): Stream[Nothing, Byte] =
    new FromSyncReader(() => Reader.singleByte(a), "Stream.succeed(...)", ElementRepresentation.Known(JvmType.Byte))
      with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Byte
      override def scalarByte: Byte          = a
    }

  /** Creates a stream that emits a single Char. */
  def succeed(a: Char): Stream[Nothing, Char] =
    new FromSyncReader(() => Reader.singleChar(a), "Stream.succeed(...)", ElementRepresentation.Known(JvmType.Char))
      with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Char
      override def scalarChar: Char          = a
    }

  /** Creates a stream that emits a single Double. */
  def succeed(a: Double): Stream[Nothing, Double] =
    new FromSyncReader(
      () => Reader.singleDouble(a),
      "Stream.succeed(...)",
      ElementRepresentation.Known(JvmType.Double)
    ) with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Double
      override def scalarDouble: Double      = a
    }

  /** Creates a stream that emits a single Float. */
  def succeed(a: Float): Stream[Nothing, Float] =
    new FromSyncReader(() => Reader.singleFloat(a), "Stream.succeed(...)", ElementRepresentation.Known(JvmType.Float))
      with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Float
      override def scalarFloat: Float        = a
    }

  /** Creates a stream that emits a single Int. */
  def succeed(a: Int): Stream[Nothing, Int] =
    new SingletonInt(a)

  /** Creates a stream that emits a single Long. */
  def succeed(a: Long): Stream[Nothing, Long] =
    new FromSyncReader(() => Reader.singleLong(a), "Stream.succeed(...)", ElementRepresentation.Known(JvmType.Long))
      with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Long
      override def scalarLong: Long          = a
    }

  /** Creates a stream that emits a single Short. */
  def succeed(a: Short): Stream[Nothing, Short] =
    new FromSyncReader(() => Reader.singleShort(a), "Stream.succeed(...)", ElementRepresentation.Known(JvmType.Short))
      with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.Short
      override def scalarShort: Short        = a
    }

  /** Creates a stream that emits a single element `a`, then closes. */
  def succeed[A](a: A): Stream[Nothing, A] =
    new FromSyncReader(
      () => Reader.single[A](a)(JvmType.Infer.boxed[A]),
      "Stream.succeed(...)",
      ElementRepresentation.Boxed
    ) with ScalarNode {
      override def knownLength: Option[Long] = Some(1L)
      def scalarType: JvmType                = JvmType.AnyRef
      override def scalarRef: AnyRef         = a.asInstanceOf[AnyRef]
    }

  /**
   * Defers stream construction until run-time. Useful for recursive streams.
   */
  def suspend[E, A](stream: => Stream[E, A]): Stream[E, A] =
    new Deferred(() => stream)

  /**
   * Lazily unfolds state sequentially, emitting each `A` and continuing with
   * the paired state until `f` returns `None`. Callback exceptions are defects.
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromSyncReader(
      () => Reader.unfold[S, A](s)(a => StreamError.callback(f(a))),
      "Stream.unfold(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

  /**
   * Lazily unfolds state sequentially, emitting each `A` and continuing with
   * the paired state until `f` returns `None`. At most one callback is active;
   * callback failure is a defect.
   */
  def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A] =
    new StableAsyncSource(
      () => Reader.unfoldAsync(s)(f),
      "Stream.unfoldAsync(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

  /**
   * Flattens an asynchronously produced stream. The effect is evaluated lazily
   * for each materialization, and failures in the effect are stream defects.
   */
  def unwrap[E](stream: => Async[Stream[E, Nothing]])(implicit dummy: DummyImplicit): Stream[E, Nothing] = {
    val _ = dummy
    new Unwrapped(() => stream, JvmType.AnyRef)
  }

  /**
   * Flattens an asynchronously produced stream. The effect is evaluated lazily
   * once per materialization; effect failures are defects, while the produced
   * stream retains its typed error channel.
   */
  def unwrap[E, A](stream: => Async[Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    new Unwrapped(() => stream, jtA.jvmType)

  private val RightDoubleZero: Right[Nothing, Double]   = Right(0.0d)
  private val RightFloatZero: Right[Nothing, Float]     = Right(0.0f)
  private val RightIntCache: Array[Right[Nothing, Int]] =
    Array.tabulate(256)(index => Right(index - 128))
  private val RightLongCache: Array[Right[Nothing, Long]] =
    Array.tabulate(256)(index => Right(index.toLong - 128L))
  private val RightLongZero: Right[Nothing, Long] = RightLongCache(128)
  private val RightUnit: Right[Nothing, Unit]     = Right(())

  private def asyncFusedStackFrame(start: AsyncFusableNode, bufferSize: Int): CompileFrame = {
    val operators            = new scala.collection.mutable.ArrayBuffer[AsyncFusableNode]
    var current              = start
    var source: Stream[_, _] = null
    var forceAsync           = false
    while (current ne null) {
      operators += current
      forceAsync = forceAsync || current.forcesAsync
      current.fusableSource match {
        case next: MaterializationBoundary => source = next; current = null
        case next: AsyncFusableNode        => current = next
        case next                          => source = next; current = null
      }
    }
    if (!forceAsync) start.stackFrame(bufferSize)
    else
      unaryFrame(
        source,
        bufferSize,
        reader =>
          transformAsyncChunks(reader, operators.length)((interpreter, i) => operators(i).appendFused(interpreter))
      )
  }

  private def closeIntReader(reader: Reader[Int]): Async[Unit] =
    try
      reader match {
        case sync: Reader.SyncReader[Int @unchecked]   => Async.succeed(sync.close())
        case async: Reader.AsyncReader[Int @unchecked] => async.close()
      }
    catch { case cause: Throwable => Async.fail(cause) }

  private def compileBlockingFallback[E, A](stream: Stream[E, A], bufferSize: Int): Reader.SyncReader[A] = {
    val reader =
      try SyncInterpreter.fromStream(stream).asInstanceOf[Reader.SyncReader[A]]
      catch { case AsyncBoundaryRequired => stream.compile(0, bufferSize) }
    Sink.toSyncReader(reader)
  }

  private[streams] def compileChildStackSafe[E, A](stream: Stream[E, A], bufferSize: Int): Reader[A] =
    stream match {
      case node: StackCompileNode =>
        compileStackSafe(node.asInstanceOf[Stream[_, A] with StackCompileNode], bufferSize)
      case _ => stream.compile(0, bufferSize)
    }

  /**
   * JVM plain-terminal compiler. Sources and linear pipelines retain their
   * specialized readers. Structural graphs use the SyncInterpreter, including
   * its blocking concurrent readers, unless an async boundary requires root
   * materialization and the documented final AsyncReader.toSync boundary.
   */
  private[streams] def compileForBlocking[E, A](stream: Stream[E, A]): Reader.SyncReader[A] =
    stream.compileBlocking(DefaultBufferSize)

  private def compileLinear[A](start: LinearOperator, bufferSize: Int): Reader[A] = {
    val operators            = new scala.collection.mutable.ArrayBuffer[LinearOperator]
    var current              = start
    var source: Stream[_, _] = null
    while (current ne null) {
      operators += current
      current.linearSource match {
        case next: LinearOperator => current = next
        case next                 => source = next; current = null
      }
    }
    source.asInstanceOf[Stream[Any, Any]].compile(0, bufferSize) match {
      case reader: Reader.SyncReader[_] =>
        transformSyncChunks(reader, operators.length)((interpreter, i) => operators(i).appendSync(interpreter))
      case reader: Reader.AsyncReader[_] =>
        transformAsyncChunks(reader, operators.length)((interpreter, i) => operators(i).appendAsync(interpreter))
    }
  }

  private def transformSyncChunks[A](
    source: Reader.SyncReader[_],
    operatorCount: Int
  )(append: (SyncInterpreter, Int) => Unit): Reader.SyncReader[A] = {
    var reader = source
    var end    = operatorCount
    while (end > 0) {
      val start                        = math.max(0, end - MaxOperatorsPerInterpreter)
      var interpreter: SyncInterpreter = null
      try {
        interpreter = SyncInterpreter.unsealed(reader)
        var i = end - 1
        while (i >= start) { append(interpreter, i); i -= 1 }
        interpreter.seal()
        reader = interpreter
      } catch {
        case primary: Throwable =>
          try if (interpreter eq null) reader.close() else interpreter.close()
          catch { case cleanup: Throwable => StreamError.attachCleanupReplay(primary, cleanup) }
          throw primary
      }
      end = start
    }
    reader.asInstanceOf[Reader.SyncReader[A]]
  }

  private def transformAsyncChunks[A](
    source: Reader[_],
    operatorCount: Int
  )(append: (AsyncInterpreter, Int) => Unit): Reader.AsyncReader[A] = {
    var reader = source
    var end    = operatorCount
    while (end > 0) {
      val start = math.max(0, end - MaxOperatorsPerInterpreter)
      reader = reader match {
        case sync: Reader.SyncReader[_] =>
          transformSync(sync) { interpreter =>
            var i = start
            while (i < end) { append(interpreter, i); i += 1 }
          }
        case async: Reader.AsyncReader[_] =>
          transformAsync(async) { interpreter =>
            var i = start
            while (i < end) { append(interpreter, i); i += 1 }
          }
      }
      end = start
    }
    reader.asInstanceOf[Reader.AsyncReader[A]]
  }

  /**
   * Depth threshold; beyond this, compilation falls back to the flat-array
   * [[SyncInterpreter]] to prevent stack overflow during recursive stream
   * compilation.
   */
  private[streams] val DepthCutoff = 100

  private val MaxOperatorsPerInterpreter = 1024

  private[streams] val DefaultBufferSize = 64

  private[streams] final class CompileFrame(
    val source: Stream[_, _],
    val sourceBufferSize: Int,
    val wrap: Reader[_] => Reader[_],
    val reject: Throwable => Throwable
  )

  private[streams] trait StackCompileNode { self: Stream[_, _] =>
    def stackFrame(bufferSize: Int): CompileFrame
  }

  private[streams] trait MaterializationBoundary { self: Stream[_, _] => }

  /**
   * Operation-neutral, iterative traversal of concatenated stream descriptions.
   */
  private final class ConcatLeafCursor[E, A](root: Concatenated[E, A]) {
    private val pending = new Array[AnyRef](root.pendingDepth)
    private var size    = 1
    pending(0) = root

    def next(): Stream[E, A] = {
      while (size > 0) {
        size -= 1
        val current = pending(size).asInstanceOf[Stream[E, A]]
        pending(size) = null
        current match {
          case nested: Concatenated[E @unchecked, A @unchecked] =>
            pending(size) = nested.that
            size += 1
            pending(size) = nested.self
            size += 1
          case leaf => return leaf
        }
      }
      null
    }

  }

  private[streams] trait AsyncFusableNode extends StackCompileNode { self: Stream[_, _] =>
    def appendFused(interpreter: AsyncInterpreter): Unit
    def forcesAsync: Boolean
    def fusableSource: Stream[_, _]
  }

  private[streams] def compileStackSafe[A](start: Stream[_, A] with StackCompileNode, bufferSize: Int): Reader[A] = {
    val frames                = new scala.collection.mutable.ArrayBuffer[CompileFrame]
    var current: Stream[_, _] = start
    var currentBufferSize     = bufferSize
    val initialReader         = try {
      var peeling = true
      while (peeling)
        current match {
          case linear: LinearOperator =>
            val frame = linearStackFrame(linear, currentBufferSize)
            frames += frame
            current = frame.source
            currentBufferSize = frame.sourceBufferSize
          case fusable: AsyncFusableNode =>
            val frame = asyncFusedStackFrame(fusable, currentBufferSize)
            frames += frame
            current = frame.source
            currentBufferSize = frame.sourceBufferSize
          case node: StackCompileNode =>
            val frame = node.stackFrame(currentBufferSize)
            frames += frame
            current = frame.source
            currentBufferSize = frame.sourceBufferSize
          case _ => peeling = false
        }
      current.asInstanceOf[Stream[Any, Any]].compile(0, currentBufferSize).asInstanceOf[Reader[_]]
    } catch {
      case initial: Throwable =>
        var failure = initial
        var i       = frames.length - 1
        while (i >= 0) { failure = frames(i).reject(failure); i -= 1 }
        throw failure
    }
    var reader = initialReader
    var i      = frames.length - 1
    while (i >= 0) {
      try reader = frames(i).wrap(reader)
      catch {
        case primary: Throwable =>
          return rejectReader(reader, primary, frames, i).asInstanceOf[Reader[A]]
      }
      i -= 1
    }
    reader.asInstanceOf[Reader[A]]
  }

  /** Compiles a stream for pull-based evaluation. */
  private[streams] def compileToReader[E, A](stream: Stream[E, A]): Reader[A] =
    stream.compile(0, Stream.DefaultBufferSize)

  /**
   * Compiles while retaining an enclosing interpreter's materialization epoch.
   */
  private[streams] def compileToReader[E, A](stream: Stream[E, A], pipeline: SyncInterpreter): Reader.SyncReader[A] =
    if (pipeline eq null) SyncInterpreter.fromStream(stream).asInstanceOf[Reader.SyncReader[A]]
    else SyncInterpreter.fromStreamGuarded(stream, pipeline).asInstanceOf[Reader.SyncReader[A]]

  private def iteratorReader[A](iter: Iterator[A])(implicit jtA: JvmType.Infer[A]): Reader.SyncReader[A] =
    Reader.fromIterable(new Iterable[A] {
      def iterator: Iterator[A] = iter
    })

  private def linearStackFrame(start: LinearOperator, bufferSize: Int): CompileFrame = {
    val operators            = new scala.collection.mutable.ArrayBuffer[LinearOperator]
    var current              = start
    var source: Stream[_, _] = null
    while (current ne null) {
      operators += current
      current.linearSource match {
        case next: LinearOperator => current = next
        case next                 => source = next; current = null
      }
    }
    unaryFrame(
      source,
      bufferSize,
      {
        case reader: Reader.SyncReader[_] =>
          val interpreter = SyncInterpreter.unsealed(reader)
          var i           = operators.length - 1
          while (i >= 0) { operators(i).appendSync(interpreter); i -= 1 }
          interpreter.seal()
          interpreter
        case reader: Reader.AsyncReader[_] =>
          AsyncInterpreter
            .transform(reader) { interpreter =>
              var i = 0
              while (i < operators.length) { operators(i).appendAsync(interpreter); i += 1 }
            }
            .toReader[Any]
      }
    )
  }

  private def normalizeRecoveryReader[A](source: Reader[A], outType: JvmType): Reader.AsyncReader[A] =
    source match {
      case reader: Reader.AsyncReader[A @unchecked] =>
        transformAsync(reader)(_.normalizeRecoveryOutput(outType))
      case reader: Reader.SyncReader[A @unchecked] =>
        transformSync(reader)(_.normalizeRecoveryOutput(outType))
    }

  private def rejectFrames(
    frames: scala.collection.mutable.ArrayBuffer[CompileFrame],
    lastFrame: Int,
    primary: Throwable
  ): Throwable = {
    var failure = primary
    var i       = lastFrame
    while (i >= 0) { failure = frames(i).reject(failure); i -= 1 }
    failure
  }

  private def rejectReader(
    reader: Reader[_],
    primary: Throwable,
    frames: scala.collection.mutable.ArrayBuffer[CompileFrame],
    lastFrame: Int
  ): Reader[_] = reader match {
    case sync: Reader.SyncReader[_] =>
      var failure = primary
      try sync.close()
      catch { case cleanup: Throwable => failure = StreamError.attachCleanupReplay(failure, cleanup) }
      var i = lastFrame
      while (i >= 0) { failure = frames(i).reject(failure); i -= 1 }
      throw failure
    case async: Reader.AsyncReader[_] =>
      var failure = primary
      Reader.closed.toAsync
        .withReleaseAsync(() =>
          async.close().either.flatMap {
            case Left(cleanup) =>
              failure = StreamError.attachCleanupReplay(failure, cleanup)
              Async
                .reschedule(() => Async.succeed(rejectFrames(frames, lastFrame, failure)))
                .map { updated => failure = updated; () }
            case Right(_) =>
              Async
                .reschedule(() => Async.succeed(rejectFrames(frames, lastFrame, failure)))
                .map { updated => failure = updated; () }
          }
        )
        .concatAsyncWithJvmType(() => Async.fail(failure), async.jvmType)
  }

  private def rejectSyncReader(reader: Reader.SyncReader[_], primary: Throwable): Nothing = {
    try reader.close()
    catch { case cleanup: Throwable => StreamError.attachCleanupReplay(primary, cleanup) }
    throw primary
  }

  private[streams] def rightDouble[E](value: Double): Either[E, Double] =
    if (java.lang.Double.doubleToRawLongBits(value) == 0L) RightDoubleZero.asInstanceOf[Either[E, Double]]
    else Right(value)

  private[streams] def rightFloat[E](value: Float): Either[E, Float] =
    if (java.lang.Float.floatToRawIntBits(value) == 0) RightFloatZero.asInstanceOf[Either[E, Float]]
    else Right(value)

  private[streams] def rightInt[E](value: Int): Either[E, Int] =
    if (value >= -128 && value <= 127) RightIntCache(value + 128).asInstanceOf[Either[E, Int]]
    else Right(value)

  private[streams] def rightLong[E](value: Long): Either[E, Long] =
    if (value >= -128L && value <= 127L) RightLongCache((value + 128L).toInt).asInstanceOf[Either[E, Long]]
    else Right(value)

  private def toAsyncIntReader(reader: Reader[Int]): Reader.AsyncReader[Int] = reader match {
    case sync: Reader.SyncReader[Int @unchecked]   => sync.toAsync
    case async: Reader.AsyncReader[Int @unchecked] => async
  }

  private def transformAsync[A, B](source: Reader.AsyncReader[A])(
    configure: AsyncInterpreter => Unit
  ): Reader.AsyncReader[B] = AsyncInterpreter.transform(source)(configure).toReader[B]

  private def transformSync[A, B](source: Reader.SyncReader[A])(
    configure: AsyncInterpreter => Unit
  ): Reader.AsyncReader[B] = AsyncInterpreter.transformSync(source)(configure).toReader[B]

  private[streams] def transformSyncForTest[A, B](source: Reader.SyncReader[A])(
    configure: AsyncInterpreter => Unit
  ): Reader.AsyncReader[B] = transformSync(source)(configure)

  private[streams] trait LinearOperator extends StackCompileNode { self: Stream[_, _] =>
    def appendAsync(interpreter: AsyncInterpreter): Unit
    def appendSync(interpreter: SyncInterpreter): Unit
    def linearSource: Stream[_, _]
    final def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(
        linearSource,
        bufferSize,
        {
          case reader: Reader.SyncReader[_] =>
            val interpreter = SyncInterpreter.unsealed(reader)
            appendSync(interpreter)
            interpreter.seal()
            interpreter
          case reader: Reader.AsyncReader[_] => AsyncInterpreter.transform(reader)(appendAsync).toReader[Any]
        }
      )
  }

  private def unaryFrame(
    source: Stream[_, _],
    bufferSize: Int,
    wrap: Reader[_] => Reader[_]
  ): CompileFrame = new CompileFrame(source, bufferSize, wrap, identity)

  /** Recovers from all errors by switching to the stream returned by `f`. */
  private[streams] final class CatchAll[E, E2, A](
    self: Stream[E, A],
    f: E => Stream[E2, A],
    outType: JvmType
  ) extends Stream[E2, A]
      with StackCompileNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)
    def render: String                            = s"${self.render}.catchAll(...)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired
    private def wrapReader(upstream: Reader[A]): Reader[A] =
      transformAsync(normalizeRecoveryReader(upstream, outType)) { interpreter =>
        interpreter.addAsyncCatchAll[E](
          e => Async.reschedule(() => Async.succeed(StreamError.callback(f(e)).asInstanceOf[Stream[_, Any]])),
          allowReferenceRecovery = true
        )
      }
  }

  /** Recovers from non-fatal defects matching `f`. */
  private[streams] final class CatchDefect[E, A](
    self: Stream[E, A],
    f: PartialFunction[Throwable, Stream[E, A]],
    outType: JvmType
  ) extends Stream[E, A]
      with StackCompileNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)
    def render: String                            = s"${self.render}.catchDefect(...)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired
    private def wrapReader(upstream: Reader[A]): Reader[A] =
      transformAsync(normalizeRecoveryReader(upstream, outType))(
        _.addAsyncCatchDefect(
          {
            case cause if StreamError.callback(f.isDefinedAt(cause)) =>
              Async.reschedule(() => Async.succeed(Some(StreamError.callback(f(cause)).asInstanceOf[Stream[_, Any]])))
          },
          allowReferenceRecovery = true
        )
      )
  }

  /** Emits all elements of `self` followed by all elements of `that`. */
  private[streams] final class Concatenated[E, A](
    private[streams] val self: Stream[E, A],
    private[streams] val that: Stream[E, A],
    outType: JvmType
  ) extends Stream[E, A]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)
    override def knownLength: Option[Long] =
      for { a <- self.knownLength; b <- that.knownLength } yield a + b
    def render: String                            = s"${self.render} ++ ${that.render}"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]], bufferSize))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) {
        compileStackSafe(this, bufferSize)
      } else {
        self.compile(depth + 1, bufferSize) match {
          case r1: Reader.SyncReader[A @unchecked] =>
            r1.concatReaderWithJvmType(() => that.compile(depth + 1, bufferSize), outType)
          case r1: Reader.AsyncReader[A @unchecked] =>
            r1.concatReaderWithJvmType(() => that.compile(depth + 1, bufferSize), outType)
        }
      }
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] = {
      var current: Stream[_, _] = this
      var depth                 = 0
      while (depth < Stream.DepthCutoff)
        current match {
          case nested: Concatenated[_, _] =>
            depth += 1
            current = nested.stackFrame(bufferSize).source
          case _ => return compileBlockingFallback(this, bufferSize)
        }
      Sink.toSyncReader(compileStackSafe(this, bufferSize))
    }
    override private[streams] def runFoldDoubleBlocking(
      z: Double,
      fold: (Double, A) => Double
    ): Either[E, Double] = {
      val cursor = new ConcatLeafCursor(this)
      var acc    = z
      var leaf   = cursor.next()
      while (leaf ne null) {
        leaf.runFoldDoubleBlocking(acc, fold) match {
          case Right(value) => acc = value
          case left         => return left
        }
        leaf = cursor.next()
      }
      Right(acc)
    }

    override private[streams] def runFoldIntBlocking(z: Int, fold: (Int, A) => Int): Either[E, Int] = {
      val cursor = new ConcatLeafCursor(this)
      var acc    = z
      var leaf   = cursor.next()
      while (leaf ne null) {
        leaf.runFoldIntBlocking(acc, fold) match {
          case Right(value) => acc = value
          case left         => return left
        }
        leaf = cursor.next()
      }
      Right(acc)
    }

    override private[streams] def runFoldLongBlocking(z: Long, fold: (Long, A) => Long): Either[E, Long] = {
      val cursor = new ConcatLeafCursor(this)
      var acc    = z
      var leaf   = cursor.next()
      while (leaf ne null) {
        leaf.runFoldLongBlocking(acc, fold) match {
          case Right(value) => acc = value
          case left         => return left
        }
        leaf = cursor.next()
      }
      Right(acc)
    }

    override private[streams] def runFoldGenericBlocking[Z](z: Z, fold: (Z, A) => Z)(implicit
      jtZ: JvmType.Infer[Z]
    ): Either[E, Z] = {
      val cursor = new ConcatLeafCursor(this)
      var acc    = z
      var leaf   = cursor.next()
      while (leaf ne null) {
        leaf.runFoldGenericBlocking(acc, fold) match {
          case Right(value) => acc = value
          case left         => return left
        }
        leaf = cursor.next()
      }
      Right(acc)
    }
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      var owned: Reader.SyncReader[_] = compileToReader(self, pipeline)
      try {
        pipeline.ensureMaterializationCurrent()
        owned = new Reader.ConcatReader[A](
          owned.asInstanceOf[Reader.SyncReader[A]],
          () => compileToReader(that, pipeline),
          outType,
          pipeline.operationGuard
        )
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(owned, outType)
        owned = null
      } catch {
        case cause: Throwable =>
          if (owned ne null) pipeline.closeRejectedOwner(owned, cause)
          throw cause
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() =>
        Reader.closed.toAsync
          .concatAsyncWithJvmType(
            () => Async.reschedule(() => Async.succeed(self.compile(0, Stream.DefaultBufferSize))),
            outType
          )
          .concatAsyncWithJvmType(
            () => Async.reschedule(() => Async.succeed(that.compile(0, Stream.DefaultBufferSize))),
            outType
          )
      )
      null
    }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = {
      if (!elementRepresentation.stableJvmType.contains(JvmType.Int)) return null

      (self, that) match {
        case (first: AsyncSource[_, _], second: AsyncSource[_, _])
            if first.elementRepresentation.stableJvmType.contains(JvmType.Int) &&
              second.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
          val acquire = first.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          val next    = second.asInstanceOf[AsyncSource[_, Int]]
          val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
          return (
            if (first.protectAcquisition) new AsyncSourceSequenceIntLongUseProtected[E](acquire, next, zero, reduce)
            else new AsyncSourceSequenceIntLongUseUnprotected[E](acquire, next, zero, reduce)
          )
        case _ =>
      }

      val nodes        = new Array[AnyRef](leafCount)
      var pendingCount = 1
      var sourceCount  = 0
      nodes(nodes.length - 1) = this
      while (pendingCount > 0) {
        val pendingIndex = nodes.length - pendingCount
        val current      = nodes(pendingIndex).asInstanceOf[Stream[_, _]]
        nodes(pendingIndex) = null
        pendingCount -= 1
        current match {
          case nested: Concatenated[_, _] if nested.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
            pendingCount += 1
            nodes(nodes.length - pendingCount) = nested.that
            pendingCount += 1
            nodes(nodes.length - pendingCount) = nested.self
          case source: AsyncSource[_, _] if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
            nodes(sourceCount) = source
            sourceCount += 1
          case mapped: AsyncMapped[_, _, _] if mapped.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
            nodes(sourceCount) = mapped
            sourceCount += 1
          case _ => return null
        }
      }

      new AsyncSourceConcatIntLongFold[E](
        nodes,
        sourceCount,
        fold.asInstanceOf[(Long, Int) => Async[Long]]
      ).continueAfterNestedChild(zero)
    }
    private[streams] val leafCount: Int = {
      val left = self match {
        case nested: Concatenated[_, _] => nested.leafCount
        case _                          => 1
      }
      val right = that match {
        case nested: Concatenated[_, _] => nested.leafCount
        case _                          => 1
      }
      val count = left.toLong + right.toLong
      if (count > Int.MaxValue) Int.MaxValue else count.toInt
    }
    private[streams] val pendingDepth: Int = {
      val left = self match {
        case nested: Concatenated[_, _] => nested.pendingDepth
        case _                          => 1
      }
      val right = that match {
        case nested: Concatenated[_, _] => nested.pendingDepth
        case _                          => 1
      }
      math.max(left + 1, right)
    }
    private def wrapReader(reader: Reader[A], bufferSize: Int): Reader[A] = reader match {
      case sync: Reader.SyncReader[A @unchecked] =>
        sync.concatReaderWithJvmType(() => compileChildStackSafe(that, bufferSize), outType)
      case async: Reader.AsyncReader[A @unchecked] =>
        async.concatReaderWithJvmType(() => compileChildStackSafe(that, bufferSize), outType)
    }
  }

  /** Defers stream construction until run-time. */
  private[streams] final class Deferred[E, A](
    mkStream: () => Stream[E, A],
    representation: ElementRepresentation = ElementRepresentation.LateBound
  ) extends Stream[E, A]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation = representation
    def render: String                                                         = "Stream.suspend(...)"
    def stackFrame(bufferSize: Int): CompileFrame                              =
      unaryFrame(StreamError.callback(mkStream()), bufferSize, identity)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else StreamError.callback(mkStream()).compile(depth + 1, bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val stream = StreamError.callback(mkStream())
      pipeline.ensureMaterializationCurrent()
      stream.compileInterpreter(pipeline)
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferOwnedReaderRoot(StreamError.callback(mkStream()).compile(0, Stream.DefaultBufferSize))
      null
    }
  }

  /** Skips the first `n` elements. */
  private[streams] final class Dropped[E, A](private[streams] val self: Stream[E, A], private[streams] val n: Long)
      extends Stream[E, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def appendFused(interpreter: AsyncInterpreter): Unit                       = interpreter.deferDrop(n)
    def forcesAsync: Boolean                                                   = false
    def fusableSource: Stream[_, _]                                            = self
    override def knownLength: Option[Long]                                     = self.knownLength.map(l => math.max(0L, l - math.max(0L, n)))
    def render: String                                                         = s"${self.render}.drop($n)"
    def stackFrame(bufferSize: Int): CompileFrame                              =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapOutput { r =>
        val skipped = r.setSkip(n)
        pipeline.ensureMaterializationCurrent()
        if (!skipped) r.skip(n)
        r
      }
    }
    private def wrapReader(source: Reader[A]): Reader[A] = source match {
      case r: Reader.SyncReader[A @unchecked] =>
        try {
          if (!r.setSkip(n)) r.skip(n)
          r
        } catch { case cause: Throwable => rejectSyncReader(r, cause) }
      case r: Reader.AsyncReader[A @unchecked] => transformAsync(r)(_.deferDrop(n))
    }
  }

  /** Buffers up to `n` elements between upstream and downstream. */
  private[streams] final class Buffered[E, A](self: Stream[E, A], n: Int) extends Stream[E, A] with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def render: String                                                         = s"${self.render}.buffer($n)"

    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => Platform.createBufferedReaderFromReader(reader.asInstanceOf[Reader[A]], n))

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else Platform.createBufferedReaderFromReader(self.compile(depth + 1, bufferSize), n)

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      var owned: Reader.SyncReader[_] = compileToReader(self, pipeline)
      try {
        pipeline.ensureMaterializationCurrent()
        owned.asInstanceOf[SyncInterpreter].releaseEnclosingGuard()
        owned = Platform
          .createBufferedReader(owned.asInstanceOf[Reader.SyncReader[A]], n)
          .asInstanceOf[Reader.SyncReader[A]]
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(owned)
        owned = null
      } catch {
        case cause: Throwable =>
          if (owned ne null) pipeline.closeRejectedOwner(owned, cause)
          throw cause
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() =>
        Platform.createBufferedReaderFromReader(self.compile(0, Stream.DefaultBufferSize), n)
      )
      null
    }
  }

  private[streams] final class Zipped[E, A, B, C](
    leftStream: Stream[E, A],
    rightStream: Stream[E, B],
    zip: Zip[A, B, C],
    outType: JvmType
  ) extends Stream[E, C]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)
    def render: String = s"${leftStream.render} && ${rightStream.render}"

    private var closeBeforeAsyncAdmission = false
    private var closeBeforeAsyncFailure   = false

    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(
        leftStream,
        bufferSize,
        left => combine(left.asInstanceOf[Reader[A]], compileChildStackSafe(rightStream, bufferSize))
      )
    private[streams] def closeBeforeAsyncAdmissionForTest(): Unit = closeBeforeAsyncAdmission = true

    private[streams] def closeBeforeAsyncFailureForTest(): Unit = closeBeforeAsyncFailure = true

    private def combine(left: Reader[A], right: Reader[B]): Reader[C] =
      (left, right) match {
        case (l: Reader.SyncReader[A @unchecked], r: Reader.SyncReader[B @unchecked]) => syncReader(l, r, null)
        case (l, r)                                                                   =>
          new AsyncZipReader(
            l match {
              case value: Reader.SyncReader[A @unchecked]  => value.toAsync
              case value: Reader.AsyncReader[A @unchecked] => value
            },
            r match {
              case value: Reader.SyncReader[B @unchecked]  => value.toAsync
              case value: Reader.AsyncReader[B @unchecked] => value
            }
          )
      }

    private[streams] def compile(depth: Int, bufferSize: Int): Reader[C] = {
      if (depth >= Stream.DepthCutoff) return compileStackSafe(this, bufferSize)
      val cutoff = depth >= Stream.DepthCutoff
      val left   =
        if (cutoff) Stream.compileChildStackSafe(leftStream, bufferSize)
        else leftStream.compile(depth + 1, bufferSize)
      val right =
        try {
          if (cutoff) Stream.compileChildStackSafe(rightStream, bufferSize)
          else rightStream.compile(depth + 1, bufferSize)
        } catch {
          case cause: Throwable =>
            left match {
              case sync: Reader.SyncReader[A @unchecked] =>
                try sync.close()
                catch { case cleanup: Throwable => StreamError.attachCleanup(cause, cleanup) }
                throw cause
              case async: Reader.AsyncReader[A @unchecked] =>
                val owner = Reader.closed.toAsync.withReleaseAsync(() =>
                  async.close().either.flatMap {
                    case Right(_)      => Async.succeed(())
                    case Left(cleanup) => fail(StreamError.attachCleanupReplay(cause, cleanup))
                  }
                )
                return owner.concatAsyncWithJvmType(() => fail(cause), outType)
            }
        }
      combine(left, right)
    }

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val owned = compileSync(pipeline)
      try pipeline.appendRead(owned)
      catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(owned, cause)
          throw cause
      }
    }

    private def compileSync(pipeline: SyncInterpreter): Reader.SyncReader[C] = {
      val left                        = compileToReader(leftStream, pipeline)
      var right: Reader.SyncReader[B] = null
      try {
        if (pipeline ne null) pipeline.ensureMaterializationCurrent()
        right = compileToReader(rightStream, pipeline)
        if (pipeline ne null) pipeline.ensureMaterializationCurrent()
        syncReader(left, right, pipeline)
      } catch {
        case cause: Throwable =>
          if (right ne null)
            try right.close()
            catch {
              case cleanup: Throwable =>
                if (pipeline ne null) pipeline.recordCleanupFailure(cause, cleanup)
                else StreamError.attachCleanup(cause, cleanup)
            }
          try left.close()
          catch {
            case cleanup: Throwable =>
              if (pipeline ne null) pipeline.recordCleanupFailure(cause, cleanup)
              else StreamError.attachCleanup(cause, cleanup)
          }
          throw cause
      }
    }

    private def fail[X](cause: Throwable): Async[X] = cause match {
      case error: StreamError if error.isTrusted => Async.failTrusted(error)
      case other                                 => Async.fail(other)
    }

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => compile(0, DefaultBufferSize))
      null
    }

    private def syncReader(
      left: Reader.SyncReader[A],
      right: Reader.SyncReader[B],
      pipeline: SyncInterpreter
    ): Reader.SyncReader[C] =
      // specialization-id: stream-zipped-pipeline-reader
      new Reader.SyncReader[C] {
        private var closeDone               = false
        private var closeFailure: Throwable = null
        private var readFailure: Throwable  = null
        private val pair                    = SyncZipPair(left, right, zip)
        private var eofState                = false

        override def jvmType: JvmType = outType

        override def readBoolean(sentinel: Int)(implicit ev: C <:< Boolean): Int = {
          val value = pullZip(false.asInstanceOf[C])
          if (eofState) sentinel else if (value.asInstanceOf[Boolean]) 1 else 0
        }
        override def readByte(): Int = {
          val value = pullZip(0.toByte.asInstanceOf[C])
          if (eofState) -1 else value.asInstanceOf[Byte].toInt & 0xff
        }
        override def readChar(sentinel: Int)(implicit ev: C <:< Char): Int = {
          val value = pullZip(0.toChar.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Char].toInt
        }
        override def readShort(sentinel: Int)(implicit ev: C <:< Short): Int = {
          val value = pullZip(0.toShort.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Short].toInt
        }
        override def readInt(sentinel: Long)(implicit ev: C <:< Int): Long = {
          val value = pullZip(0.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Int].toLong
        }
        override def readLong(sentinel: Long)(implicit ev: C <:< Long): Long = {
          val value = pullZip(sentinel.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Long]
        }
        override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: C <:< Long): Int = {
          Reader.validateArrayRange(dest, offset, length)
          if (length == 0) 0
          else {
            val value = pullZip(0L.asInstanceOf[C])
            if (eofState) -1 else { dest(offset) = value.asInstanceOf[Long]; 1 }
          }
        }
        override def readFloat(sentinel: Double)(implicit ev: C <:< Float): Double = {
          val value = pullZip(0.0f.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Float].toDouble
        }
        override def readDouble(sentinel: Double)(implicit ev: C <:< Double): Double = {
          val value = pullZip(sentinel.asInstanceOf[C])
          if (eofState) sentinel else value.asInstanceOf[Double]
        }
        override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: C <:< Double): Int = {
          Reader.validateArrayRange(dest, offset, length)
          if (length == 0) 0
          else {
            val value = pullZip(0.0.asInstanceOf[C])
            if (eofState) -1 else { dest(offset) = value.asInstanceOf[Double]; 1 }
          }
        }

        def close(): Unit = {
          if (!closeDone) {
            closeDone = true
            closeFailure = readFailure
            try left.close()
            catch { case cause: Throwable => closeFailure = StreamError.attachCleanupReplay(closeFailure, cause) }
            try right.close()
            catch { case cause: Throwable => closeFailure = StreamError.attachCleanupReplay(closeFailure, cause) }
          }
          if (closeFailure ne null) throw closeFailure
        }
        def isClosed: Boolean               = closeDone || (readFailure ne null) || left.isClosed || right.isClosed
        def read[C1 >: C](sentinel: C1): C1 = {
          val value = pullZip(defaultOutput)
          if (eofState) sentinel else value.asInstanceOf[C1]
        }

        private def defaultOutput: C = outType match {
          case JvmType.Boolean => false.asInstanceOf[C]; case JvmType.Byte     => 0.toByte.asInstanceOf[C]
          case JvmType.Char    => 0.toChar.asInstanceOf[C]; case JvmType.Short => 0.toShort.asInstanceOf[C]
          case JvmType.Int     => 0.asInstanceOf[C]; case JvmType.Long         => 0L.asInstanceOf[C]
          case JvmType.Float   => 0.0f.asInstanceOf[C]; case JvmType.Double    => 0.0.asInstanceOf[C]
          case _               => null.asInstanceOf[C]
        }

        private def pullZip(sentinel: C): C = {
          if (closeFailure ne null) throw closeFailure
          if (readFailure ne null) throw readFailure
          if (closeDone) return sentinel
          try {
            eofState = false
            val result = pair.pull()
            eofState = pair.eof
            if (eofState) close()
            if (eofState) sentinel else result
          } catch {
            case cause: Throwable =>
              if (pipeline ne null) pipeline.operationGuard.check()
              if (readFailure eq null) readFailure = cause
              throw readFailure
          }
        }
      }

    private final class AsyncZipReader(left: Reader.AsyncReader[A], right: Reader.AsyncReader[B])
        extends Reader.AsyncReader[C] {
      def close(): Async[Unit]               = closeOwner.close()
      private var closed                     = false
      private var terminalFailure: Throwable = null
      private var terminalFailed             = false
      private var generation                 = 0L
      private var active: Pollable[_]        = null
      private val pair                       = AsyncZipPair(left, right, zip)
      private var eofState                   = false
      private val closeOwner                 = {
        var operation: Pollable[_] = null
        new Reader.MemoizedClose(
          () => synchronized { closed = true; generation += 1; operation = active },
          () => {
            val cancelled =
              if (operation eq null) Async.succeed(())
              else Async.cancelWithCleanup(operation)
            join(cancelled, join(left.close(), right.close()))
          }
        )
      }

      def isClosed: Async[Boolean]                                                    = Async.succeed(synchronized(closed || terminalFailed))
      override def jvmType: JvmType                                                   = outType
      override def readBoolean(sentinel: Int)(implicit ev: C <:< Boolean): Async[Int] =
        pullZip(false.asInstanceOf[C]).map(value =>
          if (eofState) sentinel else if (value.asInstanceOf[Boolean]) 1 else 0
        )
      override def readByte(): Async[Int] =
        pullZip(0.toByte.asInstanceOf[C]).map(value => if (eofState) -1 else value.asInstanceOf[Byte].toInt & 0xff)
      override def readChar(sentinel: Int)(implicit ev: C <:< Char): Async[Int] =
        pullZip(0.toChar.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Char].toInt)
      override def readShort(sentinel: Int)(implicit ev: C <:< Short): Async[Int] =
        pullZip(0.toShort.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Short].toInt)
      override def readInt(sentinel: Long)(implicit ev: C <:< Int): Async[Long] =
        pullZip(0.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Int].toLong)
      override def readLong(sentinel: Long)(implicit ev: C <:< Long): Async[Long] =
        pullZip(sentinel.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Long])
      override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: C <:< Long): Async[Int] = {
        Reader.validateArrayRange(dest, offset, length)
        if (length == 0) Async.succeed(0)
        else
          pullZip(0L.asInstanceOf[C]).map(value => if (eofState) -1 else { dest(offset) = value.asInstanceOf[Long]; 1 })
      }
      override def readFloat(sentinel: Double)(implicit ev: C <:< Float): Async[Double] =
        pullZip(0.0f.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Float].toDouble)
      override def readDouble(sentinel: Double)(implicit ev: C <:< Double): Async[Double] =
        pullZip(sentinel.asInstanceOf[C]).map(value => if (eofState) sentinel else value.asInstanceOf[Double])
      override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: C <:< Double): Async[Int] = {
        Reader.validateArrayRange(dest, offset, length)
        if (length == 0) Async.succeed(0)
        else
          pullZip(0.0.asInstanceOf[C]).map(value =>
            if (eofState) -1 else { dest(offset) = value.asInstanceOf[Double]; 1 }
          )
      }
      def read[C1 >: C](sentinel: C1): Async[C1] =
        pullZip(defaultOutput).map(value => if (eofState) sentinel else value.asInstanceOf[C1])

      private def defaultOutput: C = outType match {
        case JvmType.Boolean => false.asInstanceOf[C]; case JvmType.Byte     => 0.toByte.asInstanceOf[C]
        case JvmType.Char    => 0.toChar.asInstanceOf[C]; case JvmType.Short => 0.toShort.asInstanceOf[C]
        case JvmType.Int     => 0.asInstanceOf[C]; case JvmType.Long         => 0L.asInstanceOf[C]
        case JvmType.Float   => 0.0f.asInstanceOf[C]; case JvmType.Double    => 0.0.asInstanceOf[C]
        case _               => null.asInstanceOf[C]
      }

      private def pullZip(sentinel: C): Async[C] = {
        val initial = synchronized {
          if (terminalFailed) Left(terminalFailure)
          else if (closed) Right(None)
          else if (active ne null) Left(new IllegalStateException("Only one Reader pull may be in flight"))
          else Right(Some(generation))
        }
        initial match {
          case Left(cause)        => fail(cause)
          case Right(None)        => Async.succeed(sentinel)
          case Right(Some(token)) =>
            val reserved = Async.reschedule { () =>
              eofState = false; pair.pull().map { value => eofState = pair.eof; value }
            }
              .asInstanceOf[Pollable[C]]
            val operation = Async.cancelTo(reserved, () => Async.succeed(sentinel))
            if (closeBeforeAsyncAdmission) synchronized {
              closeBeforeAsyncAdmission = false
              closed = true
              generation += 1
            }
            val admitted = synchronized {
              if (!closed && !terminalFailed && generation == token && (active eq null)) {
                active = operation
                true
              } else false
            }
            val observed: Async[C] =
              if (admitted) operation
              else Async.cancelWithCleanup(operation).map(_ => sentinel)
            observed
              .catchAll(cause => recordFailure(cause, operation, token, sentinel))
              .flatMap { result =>
                val accepted = synchronized {
                  if (active eq operation) active = null
                  !closed && generation == token
                }
                if (!accepted) Async.succeed(sentinel)
                else if (eofState) close().map(_ => sentinel)
                else Async.succeed(result)
              }
        }
      }
      def readable(): Async[Boolean] = Async.succeed(synchronized(!closed && !terminalFailed && (active eq null)))

      private def join(first: Async[Unit], second: => Async[Unit]): Async[Unit] =
        first.either.flatMap {
          case Right(_)      => second
          case Left(primary) =>
            second.either.flatMap {
              case Right(_)      => fail(primary)
              case Left(cleanup) =>
                fail(if (primary eq null) null else StreamError.attachCleanupReplay(primary, cleanup))
            }
        }

      private def recordFailure[X](cause: Throwable, operation: Pollable[_], token: Long, stale: X): Async[X] = {
        if (closeBeforeAsyncFailure) synchronized {
          closeBeforeAsyncFailure = false
          closed = true
          generation += 1
        }
        val accepted = synchronized {
          if (active eq operation) active = null
          if (!closed && generation == token) {
            terminalFailure = cause
            terminalFailed = true
            true
          } else false
        }
        if (!accepted) Async.succeed(stale)
        else
          close().either.flatMap {
            case Right(_)      => fail(cause)
            case Left(cleanup) => fail(if (cause eq null) null else StreamError.attachCleanupReplay(cause, cleanup))
          }
      }
    }
  }

  private[streams] final class WithBufferSize[E, A](inner: Stream[E, A], n: Int)
      extends Stream[E, A]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation    = inner.elementRepresentation
    def render: String                                                            = s"Stream.bufferSize($n)(...)"
    def stackFrame(bufferSize: Int): CompileFrame                                 = unaryFrame(inner, n, identity)
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else inner.compile(depth + 1, n)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit                 = inner.compileInterpreter(pipeline)
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => inner.compile(0, n))
      null
    }
  }

  private[streams] final class MapPar[E, A, B](
    self: Stream[E, A],
    n: Int,
    f: A => B,
    inRepresentation: ElementRepresentation,
    outType: JvmType
  ) extends Stream[E, B]
      with StackCompileNode {

    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)

    private val safeF: A => B = a =>
      try f(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }

    def render: String = s"${self.render}.mapPar($n)(...)"

    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]], bufferSize))

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize), bufferSize)

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      var owned: Reader.SyncReader[_] = compileToReader(self, pipeline)
      try {
        pipeline.ensureMaterializationCurrent()
        owned.asInstanceOf[SyncInterpreter].releaseEnclosingGuard()
        val inType = ElementRepresentation.resolve(inRepresentation, owned.jvmType)
        owned = Platform
          .createMapParReader[A, B](
            owned.asInstanceOf[Reader.SyncReader[A]],
            n,
            safeF,
            Stream.DefaultBufferSize,
            inType,
            outType
          )
          .asInstanceOf[Reader.SyncReader[B]]
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(owned)
        owned = null
      } catch {
        case cause: Throwable =>
          if (owned ne null) pipeline.closeRejectedOwner(owned, cause)
          throw cause
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() =>
        Reader.closed.toAsync.concatAsyncWithJvmType(
          () =>
            Async.reschedule { () =>
              val source = self.compile(0, Stream.DefaultBufferSize)
              val inType = ElementRepresentation.resolve(inRepresentation, source.jvmType)
              Async.succeed(
                Platform.createMapParReaderFromReader[A, B](
                  source,
                  n,
                  safeF,
                  Stream.DefaultBufferSize,
                  inType,
                  outType
                )
              )
            },
          outType
        )
      )
      null
    }
    private def wrapReader(reader: Reader[A], bufferSize: Int): Reader[B] =
      Platform.createMapParReaderFromReader[A, B](
        reader,
        n,
        safeF,
        bufferSize,
        ElementRepresentation.resolve(inRepresentation, reader.jvmType),
        outType
      )
  }

  private[streams] final class MergedAll[E, A](
    outerStream: Stream[E, Stream[E, A]],
    maxOpen: Int,
    elemType: JvmType
  ) extends Stream[E, A]
      with StackCompileNode {

    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(elemType)

    def render: String = s"Stream.mergeAll($maxOpen)(...)"

    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(outerStream, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[Stream[E, A]]], bufferSize))

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(outerStream.compile(depth + 1, bufferSize), bufferSize)

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      var owned: Reader.SyncReader[_] = compileToReader(outerStream, pipeline)
      try {
        pipeline.ensureMaterializationCurrent()
        owned.asInstanceOf[SyncInterpreter].releaseEnclosingGuard()
        owned = Platform
          .createMergeReader[A](
            owned.asInstanceOf[Reader.SyncReader[Stream[E, A]]],
            maxOpen,
            Stream.DefaultBufferSize,
            elemType
          )
          .asInstanceOf[Reader.SyncReader[A]]
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(owned)
        owned = null
      } catch {
        case cause: Throwable =>
          if (owned ne null) pipeline.closeRejectedOwner(owned, cause)
          throw cause
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() =>
        Reader.closed.toAsync.concatAsyncWithJvmType(
          () =>
            Async.reschedule(() =>
              Async.succeed(
                Platform.createMergeReaderFromReader[A](
                  outerStream.compile(0, Stream.DefaultBufferSize),
                  maxOpen,
                  Stream.DefaultBufferSize,
                  elemType
                )
              )
            ),
          elemType
        )
      )
      null
    }
    private def wrapReader(reader: Reader[Stream[E, A]], bufferSize: Int): Reader[A] =
      Platform.createMergeReaderFromReader[A](reader, maxOpen, bufferSize, elemType)
  }

  /** A dying stream source that always throws the given Throwable. */
  private[streams] final class DyingReader(t: Throwable) extends Reader.SyncReader[Nothing] {
    def close(): Unit                         = ()
    def isClosed: Boolean                     = false
    def read[A1 >: Nothing](sentinel: A1): A1 = throw t
    override def readByte(): Int              = throw t
    override def skip(n: Long): Unit          = ()
  }

  private abstract class LifecycleWrappingReader[A](
    src: Reader.SyncReader[A],
    finalizedMessage: String,
    canResetFinalized: Boolean
  ) extends Reader.DelegatingReader[A](src)
      with LifecycleWaitPlatform {
    private var lifecycle                   = 0 // open, resetting, closing, finalized
    private var completion: Completer[Unit] = null
    private var closingThread: Thread       = null
    private var resettingThread: Thread     = null
    private var closeRequestedDuringReset   = false

    override def close(): Unit = {
      val (result, leader, reentrant) = synchronized {
        if (lifecycle == 0) {
          completion = new Completer[Unit]
          lifecycle = 2
          closingThread = Thread.currentThread()
          (completion, true, false)
        } else if (lifecycle == 1) {
          if (completion eq null) completion = new Completer[Unit]
          closeRequestedDuringReset = true
          (completion, false, resettingThread eq Thread.currentThread())
        } else (completion, false, closingThread eq Thread.currentThread())
      }
      if (leader) {
        var failure: Throwable = null
        try src.close()
        catch { case cause: Throwable => failure = cause }
        try failure = finishClose(failure)
        catch { case cause: Throwable => failure = StreamError.attachCleanup(failure, cause) }
        if (failure eq null) result.succeed(()) else result.fail(failure)
        synchronized { closingThread = null; lifecycle = 3 }
      }
      // A close reentered by child cleanup has already joined this close and
      // must not observe its still-partial failure aggregation.
      if (!reentrant) awaitClose(result)
    }

    override protected def delegationOpen: Boolean = synchronized(lifecycle == 0)

    protected def finishClose(primary: Throwable): Throwable

    override def reset(): Unit = {
      synchronized {
        if (lifecycle == 1 || lifecycle == 2)
          throw new IllegalStateException("Cannot reset reader from its active operation")
        if (lifecycle == 3 && !canResetFinalized)
          throw new UnsupportedOperationException(finalizedMessage)
        lifecycle = 1
        completion = null
        resettingThread = Thread.currentThread()
        closeRequestedDuringReset = false
      }
      var failure: Throwable = null
      try src.reset()
      catch { case cause: Throwable => failure = cause }
      val deferredClose = synchronized {
        resettingThread = null
        if (!closeRequestedDuringReset) {
          lifecycle = 0
          completion = null
          false
        } else {
          lifecycle = 2
          closingThread = Thread.currentThread()
          true
        }
      }
      if (deferredClose) {
        var closeFailure: Throwable = null
        try src.close()
        catch { case cause: Throwable => closeFailure = cause }
        try closeFailure = finishClose(closeFailure)
        catch { case cause: Throwable => closeFailure = StreamError.attachCleanup(closeFailure, cause) }
        if (closeFailure eq null) completion.succeed(()) else completion.fail(closeFailure)
        synchronized { closingThread = null; lifecycle = 3 }
        val rejection =
          if (failure ne null) failure
          else new UnsupportedOperationException(finalizedMessage)
        if (closeFailure ne null) StreamError.attachCleanup(rejection, closeFailure)
        throw rejection
      }
      if (failure ne null) throw failure
    }
  }

  /** Wraps a stream with a finalizer that runs on close. */
  private[streams] final class Ensuring[E, A](self: Stream[E, A], finalizer: => Unit)
      extends Stream[E, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def appendFused(interpreter: AsyncInterpreter): Unit                       =
      interpreter.addAsyncFinalizer(() => Async.reschedule(() => Async.succeed(StreamError.callback(finalizer))))
    def forcesAsync: Boolean                      = false
    def fusableSource: Stream[_, _]               = self
    override def knownLength: Option[Long]        = self.knownLength
    def render: String                            = s"${self.render}.ensuring(...)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(wrap[Any])
    }
    private def wrap[B](src: Reader.SyncReader[B]): Reader.SyncReader[B] =
      new LifecycleWrappingReader[B](src, "Cannot reset a finalized ensuring reader", true) {
        protected def finishClose(primary: Throwable): Throwable =
          try { StreamError.callback(finalizer); primary }
          catch { case cause: Throwable => StreamError.attachCleanup(primary, cause) }
      }
    private def wrapReader(upstream: Reader[A]): Reader[A] = upstream match {
      case reader: Reader.SyncReader[A @unchecked]  => wrap(reader)
      case reader: Reader.AsyncReader[A @unchecked] =>
        transformAsync(reader)(
          _.addAsyncFinalizer(() => Async.reschedule(() => Async.succeed(StreamError.callback(finalizer))))
        )
    }
  }

  /** Transforms the error channel with `f`. */
  private[streams] final class ErrorMapped[E, E2, A](self: Stream[E, A], f: E => E2)
      extends Stream[E2, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def appendFused(interpreter: AsyncInterpreter): Unit                       =
      interpreter.addAsyncErrorMap[E, E2](e => Async.reschedule(() => Async.succeed(StreamError.callback(f(e)))))
    def forcesAsync: Boolean                      = false
    def fusableSource: Stream[_, _]               = self
    override def knownLength: Option[Long]        = self.knownLength
    def render: String                            = s"${self.render}.mapError(...)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapOutput(r =>
        new ErrorMappedReader[E, A](r.asInstanceOf[Reader.SyncReader[A]], f, pipeline.operationGuard)
      )
    }
    private def wrapReader(upstream: Reader[A]): Reader[A] = upstream match {
      case reader: Reader.SyncReader[A @unchecked]  => new ErrorMappedReader[E, A](reader, f)
      case reader: Reader.AsyncReader[A @unchecked] =>
        transformAsync(reader)(
          _.addAsyncErrorMap[E, E2](e => Async.reschedule(() => Async.succeed(StreamError.callback(f(e)))))
        )
    }
  }

  /** Transforms StreamError values with `f` during pull-based evaluation. */
  private[streams] final class ErrorMappedReader[E, A](
    upstream: Reader.SyncReader[A],
    f: E => Any,
    guard: Reader.InterpreterGuard = null
  ) extends Reader.SyncReader[A] {
    def close(): Unit                                                        = upstream.close()
    def isClosed: Boolean                                                    = upstream.isClosed
    override def jvmType: JvmType                                            = upstream.jvmType
    def read[A1 >: A](sentinel: A1): A1                                      = checked(upstream.read(sentinel))
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
      checked(upstream.readBooleanPhysical(sentinel))
    override def readByte(): Int =
      checked(upstream.readBytePhysical())
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
      checked(upstream.readCharPhysical(sentinel))
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      checked(pullDouble(upstream, sentinel))
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
      checked(upstream.readDoublesPhysical(dest, offset, length))
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      checked(pullFloat(upstream, sentinel))
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      checked(pullInt(upstream, sentinel))
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      checked(pullLong(upstream, sentinel))
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
      checked(upstream.readLongsPhysical(dest, offset, length))
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
      checked(upstream.readShortPhysical(sentinel))
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] =
      checked(upstream.readUpToN[A1](n))
    override def reset(): Unit            = throw new UnsupportedOperationException("ErrorMapped does not support reset")
    override def skip(n: Long): Unit      = Reader.skipViaSentinel(this, n)
    private def checked[B](pull: => B): B = {
      val value =
        try pull
        catch { case e: StreamError if e.isTrusted && !e.cleanupFailed => return mapped(e) }
      if (guard ne null) guard.check()
      value
    }
    private def mapped[B](e: StreamError): B = {
      if (guard ne null) guard.check()
      val value = StreamError.callback(f(e.value.asInstanceOf[E]))
      if (guard ne null) guard.check()
      throw StreamError.mapped(e, value)
    }
  }

  /** A failed stream source that always throws the given StreamError. */
  private[streams] final class FailedReader(se: StreamError, failedJvmType: JvmType = JvmType.AnyRef)
      extends Reader.SyncReader[Nothing] {
    def close(): Unit                                                                                             = ()
    def isClosed: Boolean                                                                                         = false
    override def jvmType: JvmType                                                                                 = failedJvmType
    def read[A1 >: Nothing](sentinel: A1): A1                                                                     = throw se
    override def readBoolean(sentinel: Int)(implicit ev: Nothing <:< Boolean): Int                                = throw se
    override def readByte(): Int                                                                                  = throw se
    override def readChar(sentinel: Int)(implicit ev: Nothing <:< Char): Int                                      = throw se
    override def readDouble(sentinel: Double)(implicit ev: Nothing <:< Double): Double                            = throw se
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Nothing <:< Double): Int =
      throw se
    override def readFloat(sentinel: Double)(implicit ev: Nothing <:< Float): Double                        = throw se
    override def readInt(sentinel: Long)(implicit ev: Nothing <:< Int): Long                                = throw se
    override def readLong(sentinel: Long)(implicit ev: Nothing <:< Long): Long                              = throw se
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Nothing <:< Long): Int = throw se
    override def readShort(sentinel: Int)(implicit ev: Nothing <:< Short): Int                              = throw se
    override def skip(n: Long): Unit                                                                        = ()
  }

  /** Emits only elements satisfying `pred`. */
  private[streams] final class Filtered[E, A](
    private[streams] val self: Stream[E, A],
    private[streams] val pred: A => Boolean
  ) extends Stream[E, A]
      with LinearOperator {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    private[streams] def inRepresentation: ElementRepresentation               = self.elementRepresentation
    private[streams] def safePred: A => Boolean                                = a =>
      try pred(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    def appendAsync(interpreter: AsyncInterpreter): Unit                          = interpreter.deferFilter(safePred)
    def appendSync(interpreter: SyncInterpreter): Unit                            = interpreter.addFilter(interpreter.outputType)(pred)
    def linearSource: Stream[_, _]                                                = self
    def render: String                                                            = s"${self.render}.filter(...)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return compileLinear[A](this, bufferSize)
      val sourceReader = self.compile(depth + 1, bufferSize)
      sourceReader match {
        case p: SyncInterpreter =>
          p.addFilter(p.outputType)(pred)
          p.seal()
          p.asInstanceOf[Reader.SyncReader[A]]
        case r: Reader.SyncReader[A @unchecked]  => wrapSync(r)
        case r: Reader.AsyncReader[A @unchecked] => transformAsync(r)(_.deferFilter(safePred))
      }
    }
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] =
      wrapSync(self.compileBlocking(bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addFilter(pipeline.outputType)(pred)
    }
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[A] => Z,
      onAsync: Reader.AsyncReader[A] => Z
    ): Z = self match {
      case source: FromSyncReader[_, A @unchecked] => onSync(wrapSync(source.compileSync()))
      case _                                       =>
        self.foldReaderKind(
          reader => onSync(wrapSync(reader)),
          reader => onAsync(transformAsync(reader)(_.deferFilter(safePred)))
        )
    }

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferFilter(safePred)
      self
    }

    override private[streams] def runFoldLongBlocking(z: Long, f: (Long, A) => Long): Either[E, Long] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int))
        self
          .asInstanceOf[Stream[E, Int]]
          .runFoldLongTransformedIntBlocking(
            z,
            null,
            pred.asInstanceOf[Int => Boolean],
            f.asInstanceOf[(Long, Int) => Long]
          )
      else super.runFoldLongBlocking(z, f)

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = {
      val direct = self match {
        case source: AsyncSource[_, _] if inRepresentation.stableJvmType.contains(JvmType.Int) =>
          val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          val predicate = pred.asInstanceOf[Int => Boolean]
          val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
          if (source.protectAcquisition) new SyncFilteredIntLongUseProtected[E](acquire, predicate, zero, reduce)
          else new SyncFilteredIntLongUseUnprotected[E](acquire, predicate, zero, reduce)
        case _ => null
      }
      if (direct.asInstanceOf[AnyRef] ne null) direct
      else if (inRepresentation.stableJvmType.contains(JvmType.Int))
        Stream.useLinearIntFoldLongDirect(
          this.asInstanceOf[Stream[E, Int]],
          zero,
          fold.asInstanceOf[(Long, Int) => Async[Long]]
        )
      else null
    }

    private[streams] def runMappedFoldLongDirect[B](
      zero: Long,
      map: A => B,
      outType: JvmType,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] = self match {
      case source: AsyncSource[_, _]
          if inRepresentation.stableJvmType.contains(JvmType.Int) && (outType eq JvmType.Int) =>
        val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val predicate = pred.asInstanceOf[Int => Boolean]
        val transform = map.asInstanceOf[Int => Int]
        val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (source.protectAcquisition)
          new SyncFilteredMappedIntLongUseProtected[E](acquire, predicate, transform, zero, reduce)
        else new SyncFilteredMappedIntLongUseUnprotected[E](acquire, predicate, transform, zero, reduce)
      case _ => null
    }

    private def syncPred(inType: JvmType): AnyRef =
      if (inType eq JvmType.Int) {
        val original = pred.asInstanceOf[Int => Boolean]
        (
          (value: Int) =>
            try original(value)
            catch { case error: StreamError => throw StreamError.untrusted(error) }
        ).asInstanceOf[AnyRef]
      } else SyncInterpreter.adaptFilter(inType, pred)

    private def wrapSync(reader: Reader.SyncReader[A]): Reader.SyncReader[A] = {
      val inType  = ElementRepresentation.resolve(inRepresentation, reader.jvmType)
      val adapted = syncPred(inType)
      val wrapped = reader match {
        case thin: Reader.WrappedReader =>
          val interpreter = thin.toInterpreter
          interpreter.addAdaptedFilter(inType, adapted)
          interpreter.seal()
          interpreter
        case _ =>
          (SyncInterpreter.laneOf(inType): @scala.annotation.switch) match {
            case 0 =>
              if (inType eq JvmType.Int) new Reader.FilteredIntInt(reader, pred.asInstanceOf[AnyRef])
              else new Reader.FilteredInt(reader, adapted, inType)
            case 1 => new Reader.FilteredLong(reader, adapted)
            case 2 => new Reader.FilteredFloat(reader, adapted)
            case 3 => new Reader.FilteredDouble(reader, adapted)
            case _ => new Reader.FilteredRef(reader, adapted)
          }
      }
      wrapped.asInstanceOf[Reader.SyncReader[A]]
    }
  }

  private[streams] abstract class AsyncUnary[E, A, B](self: Stream[E, A]) extends Stream[E, B] with AsyncFusableNode {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    final def appendFused(interpreter: AsyncInterpreter): Unit                 = configure(interpreter)
    protected def configure(interpreter: AsyncInterpreter): Unit
    final def forcesAsync: Boolean                      = true
    final def fusableSource: Stream[_, _]               = self
    final def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    private[streams] final def compile(depth: Int, bufferSize: Int): Reader[B] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] final def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired
    override private[streams] final def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      configure(pipeline)
      self
    }
    private def wrapReader(upstream: Reader[A]): Reader[B] = upstream match {
      case reader: Reader.AsyncReader[A @unchecked] => transformAsync(reader)(configure)
      case reader: Reader.SyncReader[A @unchecked]  => transformSync(reader)(configure)
    }
  }

  private[streams] final class AsyncErrorMapped[E, E2, A](self: Stream[E, A], f: E => Async[E2])
      extends AsyncUnary[E2, A, A](self.asInstanceOf[Stream[E2, A]])
      with MaterializationBoundary {
    protected def configure(i: AsyncInterpreter): Unit =
      i.addAsyncErrorMap[E, E2](e => StreamError.callbackAsync(f(e)))
    def render = s"${self.render}.mapErrorAsync(...)"
  }

  private[streams] final class AsyncEnsuring[E, A](self: Stream[E, A], finalizer: () => Async[Unit])
      extends AsyncUnary[E, A, A](self)
      with MaterializationBoundary {
    protected def configure(i: AsyncInterpreter): Unit =
      i.addAsyncFinalizer(() => StreamError.callbackAsync(finalizer()))
    def render = s"${self.render}.ensuringAsync(...)"
  }

  private[streams] final class AsyncMapPar[E, A, B](
    self: Stream[E, A],
    n: Int,
    f: A => Async[B],
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B] {
    private val map = (value: A) => f(value)

    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)

    def render: String = s"${self.render}.mapParAsync($n)(...)"

    private def reader(closeUpstream: Boolean = true): Reader.AsyncReader[B] =
      AsyncConcurrentReaders.mapPar(Stream.compileToReader(self), n, map, jtB.jvmType, closeUpstream)

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = reader()

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() => reader())
      null
    }

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] =
      if (
        (jtB.jvmType eq JvmType.Int) &&
        self.elementRepresentation.stableJvmType.contains(JvmType.Int)
      ) {
        val acquire = self match {
          case source: AsyncSource[_, _] => source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          case _                         => () => self.acquireReader.asInstanceOf[Async[Reader[Int]]]
        }
        val transform = map.asInstanceOf[Int => Async[Int]]
        val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
        val use       = (upstream: Reader.AsyncReader[Int]) => {
          val concurrent = AsyncConcurrentReaders.mapPar(upstream, n, transform, JvmType.Int, closeUpstream = false)
          Sink.foldNativeAsyncIntLong(
            concurrent.asInstanceOf[Reader.AsyncReader[Int]],
            zero,
            (acc, value) => StreamError.callbackAsync(reduce(acc, value))
          )
        }
        val effect = self match {
          case source: AsyncSource[_, _] if source.protectAcquisition =>
            new AsyncTrustedReaderUseProtected[Int, Long](acquire, use)
          case _ => new AsyncTrustedReaderUse[Int, Long](acquire, use)
        }
        effect.foldCause[Either[E, Long]] {
          case error: StreamError if error.isTrusted && !error.cleanupFailed => Left(error.value.asInstanceOf[E])
          case failure                                                       => throw failure
        }(value => rightLong[E](value))
      } else null
  }

  private[streams] final class AsyncMapped[E, A, B](
    private[streams] val self: Stream[E, A],
    private[streams] val f: A => Async[B],
    jtB: JvmType.Infer[B]
  ) extends AsyncUnary[E, A, B](self) {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    private[streams] def acquireDirect(): Async[Reader[B]] = self match {
      case source: AsyncSource[_, _] =>
        Async.acquireInstallCancelable[Reader[A], Reader[B]](
          () => source.acquire().asInstanceOf[Async[Reader[A]]],
          {
            case reader: Reader.AsyncReader[A @unchecked]
                if (reader.jvmType eq JvmType.Int) && (jtB.jvmType eq JvmType.Int) =>
              new Reader.MappedAsyncEffectIntInt(
                reader.asInstanceOf[Reader.AsyncReader[Int]],
                f.asInstanceOf[Int => Async[Int]]
              ).asInstanceOf[Reader[B]]
            case reader: Reader.AsyncReader[A @unchecked] => transformAsync(reader)(configure)
            case reader: Reader.SyncReader[A @unchecked]  => transformSync(reader)(configure)
          },
          reader =>
            try
              reader match {
                case sync: Reader.SyncReader[A @unchecked]   => Async.succeed(sync.close())
                case async: Reader.AsyncReader[A @unchecked] => async.close()
              }
            catch { case cause: Throwable => Async.fail(cause) }
        )
      case _ =>
        try Async.succeed(Stream.compileToReader(this))
        catch {
          case error: StreamError if error.isTrusted => Async.failTrusted(error)
          case cause: Throwable                      => Async.fail(cause)
        }
    }
    private[streams] def runDrainDirect(): Async[Either[E, Unit]] = {
      val folded = runFoldLongDirect(0L, (_, _) => Async.succeed(0L))
      if (folded.asInstanceOf[AnyRef] eq null) null
      else
        folded.map {
          case Left(error) => Left(error)
          case Right(_)    => Right(())
        }
    }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] =
      if ((jtB.jvmType eq JvmType.Int) && self.elementRepresentation.stableJvmType.contains(JvmType.Int))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val map     = f.asInstanceOf[Int => Async[Int]]
            val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
            if (source.protectAcquisition) new AsyncMappedIntLongUseProtected[E](acquire, map, zero, reduce)
            else new AsyncMappedIntLongUseUnprotected[E](acquire, map, zero, reduce)
          case _ => runFoldLongIntFallback(zero, fold)
        }
      else null
    private[streams] def runTakeDropFoldLongDirect(
      dropCount: Long,
      takeCount: Long,
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] =
      if ((jtB.jvmType eq JvmType.Int) && self.elementRepresentation.stableJvmType.contains(JvmType.Int)) {
        val drop  = math.max(0L, dropCount)
        val take  = math.max(0L, takeCount)
        val limit =
          if (Long.MaxValue - drop < take) Long.MaxValue
          else drop + take
        val limited = new Taken(self, limit)
        var seen    = 0L
        val reduce  = (acc: Long, value: Int) => {
          if (seen < drop) {
            seen += 1L
            Async.succeed(acc)
          } else fold.asInstanceOf[(Long, Int) => Async[Long]](acc, value)
        }
        new AsyncMappedIntLongUseUnprotected[E](
          () => limited.acquireReader.asInstanceOf[Async[Reader[Int]]],
          f.asInstanceOf[Int => Async[Int]],
          zero,
          reduce
        )
      } else null
    private[streams] def runMappedFoldLongDirect[C](
      zero: Long,
      downstream: B => C,
      outType: JvmType,
      fold: (Long, C) => Async[Long]
    ): Async[Either[E, Long]] =
      if (
        self.elementRepresentation.stableJvmType.contains(JvmType.Int) &&
        (jtB.jvmType eq JvmType.Int) &&
        (outType eq JvmType.Int)
      )
        self match {
          case source: AsyncSource[_, _] =>
            val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val map       = f.asInstanceOf[Int => Async[Int]]
            val transform = downstream.asInstanceOf[Int => Int]
            val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
            if (source.protectAcquisition)
              new AsyncMappedMappedIntLongUseProtected[E](acquire, map, transform, zero, reduce)
            else new AsyncMappedMappedIntLongUseUnprotected[E](acquire, map, transform, zero, reduce)
          case _ =>
            new AsyncMappedMappedIntLongUseUnprotected[E](
              () => self.acquireReader.asInstanceOf[Async[Reader[Int]]],
              f.asInstanceOf[Int => Async[Int]],
              downstream.asInstanceOf[Int => Int],
              zero,
              fold.asInstanceOf[(Long, Int) => Async[Long]]
            )
        }
      else null
    private[streams] def runFoldDoubleDirect(
      zero: Double,
      fold: (Double, B) => Async[Double]
    ): Async[Either[E, Double]] =
      if (jtB.jvmType eq JvmType.Double)
        self match {
          case mapped: Mapped[E @unchecked, Int @unchecked, Double @unchecked]
              if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
                (mapped.jtB.jvmType eq JvmType.Double) =>
            mapped.self match {
              case source: AsyncSource[_, _] =>
                val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
                val transform = mapped.f.asInstanceOf[Int => Double]
                val map       = f.asInstanceOf[Double => Async[Double]]
                val reduce    = fold.asInstanceOf[(Double, Double) => Async[Double]]
                if (source.protectAcquisition)
                  new AsyncSourceAsyncMappedIntDoubleUseProtected[E](acquire, zero, transform, map, reduce)
                else new AsyncSourceAsyncMappedIntDoubleUseUnprotected[E](acquire, zero, transform, map, reduce)
              case _ => null
            }
          case _ => null
        }
      else null
    private[streams] def runFoldFloatDirect(
      zero: Float,
      fold: (Float, B) => Async[Float]
    ): Async[Either[E, Float]] =
      if (jtB.jvmType eq JvmType.Float)
        self match {
          case mapped: Mapped[E @unchecked, Int @unchecked, Float @unchecked]
              if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
                (mapped.jtB.jvmType eq JvmType.Float) =>
            mapped.self match {
              case source: AsyncSource[_, _] =>
                val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
                val transform = mapped.f.asInstanceOf[Int => Float]
                val map       = f.asInstanceOf[Float => Async[Float]]
                val reduce    = fold.asInstanceOf[(Float, Float) => Async[Float]]
                if (source.protectAcquisition)
                  new AsyncSourceAsyncMappedIntFloatUseProtected[E](acquire, zero, transform, map, reduce)
                else new AsyncSourceAsyncMappedIntFloatUseUnprotected[E](acquire, zero, transform, map, reduce)
              case _ => null
            }
          case _ => null
        }
      else null
    protected def configure(i: AsyncInterpreter): Unit = i.deferAsyncMap(jtB.jvmType)(f)
    def render                                         = s"${self.render}.mapAsync(...)"

    private def runFoldLongIntFallback(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] = {
      val acquire = () => self.acquireReader.asInstanceOf[Async[Reader[Int]]]
      new AsyncMappedIntLongUseUnprotected[E](
        acquire,
        f.asInstanceOf[Int => Async[Int]],
        zero,
        fold.asInstanceOf[(Long, Int) => Async[Long]]
      )
    }

  }
  private[streams] final class AsyncFiltered[E, A](
    self: Stream[E, A],
    f: A => Async[Boolean]
  ) extends AsyncUnary[E, A, A](self) {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    protected def configure(i: AsyncInterpreter): Unit                         = i.deferAsyncFilter(f)
    def render                                                                 = s"${self.render}.filterAsync(...)"
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val predicate = f.asInstanceOf[Int => Async[Boolean]]
        val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (source.protectAcquisition) new AsyncFilteredIntLongUseProtected[E](acquire, predicate, zero, reduce)
        else new AsyncFilteredIntLongUseUnprotected[E](acquire, predicate, zero, reduce)
      case _ => null
    }
    private[streams] def runTakeCollectDirect(limit: Long): Async[Either[E, Chunk[A]]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val predicate = f.asInstanceOf[Int => Async[Boolean]]
        val result    =
          if (source.protectAcquisition)
            new AsyncSourceIntFilteredTakeCollectUseProtected[E](acquire, limit, predicate)
          else new AsyncSourceIntFilteredTakeCollectUseUnprotected[E](acquire, limit, predicate)
        result.asInstanceOf[Async[Either[E, Chunk[A]]]]
      case _ => null
    }
  }
  private[streams] final class AsyncCollected[E, A, B](
    self: Stream[E, A],
    f: A => Async[Option[B]],
    jtB: JvmType.Infer[B]
  ) extends AsyncUnary[E, A, B](self) {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    protected def configure(i: AsyncInterpreter): Unit = i.deferAsyncCollect(jtB.jvmType)(f)
    def render                                         = s"${self.render}.collectAsync(...)"
  }
  private[streams] final class AsyncTapped[E, A](
    self: Stream[E, A],
    f: A => Async[Unit]
  ) extends AsyncUnary[E, A, A](self) {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    protected def configure(i: AsyncInterpreter): Unit                         = i.deferAsyncTap(f)
    def render                                                                 = s"${self.render}.tapEachAsync(...)"
  }
  private[streams] final class AsyncDistinctBy[E, A, K](
    self: Stream[E, A],
    f: A => Async[K]
  ) extends AsyncUnary[E, A, A](self) {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    protected def configure(i: AsyncInterpreter): Unit                         = i.deferAsyncDistinctKey(f)
    def render                                                                 = s"${self.render}.distinctByAsync(...)"
  }
  private[streams] final class AsyncMapAccum[E, S, A, B](
    self: Stream[E, A],
    init: S,
    f: (S, A) => Async[(S, B)],
    jtB: JvmType.Infer[B]
  ) extends AsyncUnary[E, A, B](self) {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    protected def configure(i: AsyncInterpreter): Unit = i.deferAsyncMapAccum(init, jtB.jvmType)(f)
    def render                                         = s"${self.render}.mapAccumAsync(...)"
  }
  private[streams] final class AsyncScan[E, S, A](
    self: Stream[E, A],
    init: S,
    f: (S, A) => Async[S],
    jtS: JvmType.Infer[S]
  ) extends AsyncUnary[E, A, S](self) {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtS.jvmType)
    protected def configure(i: AsyncInterpreter): Unit = i.deferAsyncScan(init, jtS.jvmType)(f)
    def render                                         = s"${self.render}.scanAsync(...)"
  }
  private[streams] final class AsyncTakeWhile[E, A](self: Stream[E, A], f: A => Async[Boolean])
      extends AsyncUnary[E, A, A](self)
      with MaterializationBoundary {
    protected def configure(i: AsyncInterpreter): Unit = i.deferAsyncTakeWhile(f)
    def render                                         = s"${self.render}.takeWhileAsync(...)"
  }

  /**
   * Collect: applies a partial function, emitting only defined results without
   * double evaluation.
   */
  private[streams] final class Collected[E, A, B](
    self: Stream[E, A],
    pf: PartialFunction[A, B],
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B]
      with LinearOperator {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    private val safePf = new PartialFunction[A, B] {
      def apply(a: A): B                                                       = StreamError.callback(pf(a))
      override def applyOrElse[A1 <: A, B1 >: B](a: A1, default: A1 => B1): B1 =
        StreamError.callback(pf.applyOrElse(a, default))
      def isDefinedAt(a: A): Boolean = StreamError.callback(pf.isDefinedAt(a))
    }
    def appendAsync(interpreter: AsyncInterpreter): Unit = {
      val fallbackA = Reader.CollectedRef.fallback.asInstanceOf[A => B]
      if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R)
        interpreter.deferMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
      interpreter.deferFilter(JvmType.AnyRef)((b: Any) => b.asInstanceOf[AnyRef] ne Reader.CollectedRef.sentinel)
      interpreter.deferMap(JvmType.AnyRef)((a: Any) =>
        safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
      )
    }
    def appendSync(interpreter: SyncInterpreter): Unit = {
      val sentinel  = Reader.CollectedRef.sentinel
      val fallback  = Reader.CollectedRef.fallback
      val fallbackA = fallback.asInstanceOf[A => B]
      interpreter.addMap(interpreter.outputType, JvmType.AnyRef)((a: Any) =>
        safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
      )
      interpreter.addFilter(JvmType.AnyRef)((b: Any) => b.asInstanceOf[AnyRef] ne sentinel)
      if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R)
        interpreter.addMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
    }
    def linearSource: Stream[_, _]                                                = self
    def render: String                                                            = s"${self.render}.collect(...)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return compileLinear[B](this, bufferSize)
      val sourceReader = self.compile(depth + 1, bufferSize)
      sourceReader match {
        case p: SyncInterpreter =>
          val sentinel  = Reader.CollectedRef.sentinel
          val fallback  = Reader.CollectedRef.fallback
          val fallbackA = fallback.asInstanceOf[A => B]
          p.addMap(p.outputType, JvmType.AnyRef)((a: Any) =>
            safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
          )
          p.addFilter(JvmType.AnyRef)((b: Any) => (b.asInstanceOf[AnyRef] ne sentinel))
          if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R) {
            p.addMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
          }
          p.seal()
          p.asInstanceOf[Reader.SyncReader[B]]
        case r: Reader.SyncReader[A @unchecked] =>
          val interpreter = SyncInterpreter(r)
          appendSync(interpreter)
          interpreter.seal()
          interpreter.asInstanceOf[Reader.SyncReader[B]]
        case r: Reader.AsyncReader[A @unchecked] =>
          transformAsync(r) { pipeline =>
            val fallbackA = Reader.CollectedRef.fallback.asInstanceOf[A => B]
            if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R)
              pipeline.deferMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
            pipeline.deferFilter(JvmType.AnyRef)((b: Any) => b.asInstanceOf[AnyRef] ne Reader.CollectedRef.sentinel)
            pipeline.deferMap(JvmType.AnyRef)((a: Any) =>
              safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
            )
          }
      }
    }
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      val sentinel  = Reader.CollectedRef.sentinel
      val fallback  = Reader.CollectedRef.fallback
      val fallbackA = fallback.asInstanceOf[A => B]
      pipeline.addMap(pipeline.outputType, JvmType.AnyRef)((a: Any) =>
        safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
      )
      pipeline.addFilter(JvmType.AnyRef)((b: Any) => (b.asInstanceOf[AnyRef] ne sentinel))
      if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R) {
        pipeline.addMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      val sentinel  = Reader.CollectedRef.sentinel
      val fallback  = Reader.CollectedRef.fallback
      val fallbackA = fallback.asInstanceOf[A => B]
      if (SyncInterpreter.outLaneOf(jtB.jvmType) != SyncInterpreter.OUT_R)
        pipeline.deferMap(JvmType.AnyRef, jtB.jvmType)((b: Any) => b)
      pipeline.deferFilter(JvmType.AnyRef)((b: Any) => (b.asInstanceOf[AnyRef] ne sentinel))
      pipeline.deferMap(JvmType.AnyRef)((a: Any) =>
        safePf.applyOrElse(a.asInstanceOf[A], fallbackA).asInstanceOf[AnyRef]
      )
      self
    }

  }

  /** FlatMap: maps each element to a stream, then flattens sequentially. */
  private[streams] final class FlatMapped[E, E2 >: E, A, B](
    self: Stream[E, A],
    f: A => Stream[E2, B],
    private[streams] val inRepresentation: ElementRepresentation,
    private[streams] val jtB: JvmType.Infer[B]
  ) extends Stream[E2, B]
      with AsyncFusableNode {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    private val safeF: A => Stream[E2, B] = a =>
      try f(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    def appendFused(interpreter: AsyncInterpreter): Unit =
      interpreter.deferPush(jtB.jvmType)(safeF)
    def forcesAsync: Boolean                      = true
    def fusableSource: Stream[_, _]               = self
    def render: String                            = s"${self.render}.flatMap(...)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[B] = {
      val intCollapse = prepareIntCollapse(bufferSize)
      if (intCollapse ne null) return intCollapse
      if (hasScalarRoot)
        return new LazyBlockingReader[B](
          jtB.jvmType,
          () => collapseScalarSpine().compileBlocking(bufferSize)
        )

      var current: Stream[_, _] = this
      var depth                 = 0
      while (depth < Stream.DepthCutoff)
        current match {
          case nested: FlatMapped[_, _, _, _] =>
            depth += 1
            current = nested.fusableSource
          case _ => return compileBlockingSpecialized(bufferSize)
        }
      Sink.toSyncReader(compileStackSafe(this, bufferSize))
    }

    private def prepareIntCollapse(bufferSize: Int): Reader.SyncReader[B] = {
      var current: Stream[_, _] = this
      var depth                 = 0
      while (current.isInstanceOf[FlatMapped[_, _, _, _]]) {
        val nested = current.asInstanceOf[FlatMapped[_, _, _, _]]
        if ((nested.jtB.jvmType ne JvmType.Int) || !nested.inRepresentation.stableJvmType.contains(JvmType.Int))
          return null
        depth += 1
        current = nested.fusableSource
      }
      if (!current.isInstanceOf[SingletonInt]) return null

      val nodes = new Array[FlatMapped[Nothing, E2, Int, Int]](depth)
      current = this
      var index = 0
      while (index < depth) {
        val nested = current.asInstanceOf[FlatMapped[Nothing, E2, Int, Int]]
        nodes(index) = nested
        current = nested.fusableSource
        index += 1
      }
      val root = current.asInstanceOf[SingletonInt].value
      new LazyBlockingReader[B](
        JvmType.Int,
        () => evaluateIntCollapse(nodes, root, bufferSize).asInstanceOf[Reader.SyncReader[B]]
      )
    }

    private def evaluateIntCollapse(
      nodes: Array[FlatMapped[Nothing, E2, Int, Int]],
      root: Int,
      bufferSize: Int
    ): Reader.SyncReader[Int] = {
      var value = root
      var index = nodes.length - 1
      while (index >= 0) {
        nodes(index).safeF(value) match {
          case next: SingletonInt => value = next.value
          case child              =>
            var remainder = child
            index -= 1
            while (index >= 0) {
              val nested = nodes(index)
              remainder = new FlatMapped[E2, E2, Int, Int](
                remainder,
                nested.safeF.asInstanceOf[Int => Stream[E2, Int]],
                nested.inRepresentation,
                JvmType.Infer.int
              )
              index -= 1
            }
            return remainder.compileBlocking(bufferSize)
        }
        index -= 1
      }
      Reader.singleInt(value)
    }

    private def hasScalarRoot: Boolean = {
      var current: Stream[_, _] = this
      while (current.isInstanceOf[FlatMapped[_, _, _, _]])
        current = current.asInstanceOf[FlatMapped[_, _, _, _]].fusableSource
      current.isInstanceOf[ScalarNode]
    }

    private def collapseScalarSpine(): Stream[E2, B] = {
      var current: Stream[_, _] = this
      var depth                 = 0
      while (current.isInstanceOf[FlatMapped[_, _, _, _]]) {
        depth += 1
        current = current.asInstanceOf[FlatMapped[_, _, _, _]].fusableSource
      }
      val root = current match {
        case scalar: ScalarNode => scalar
        case _                  => return null
      }

      val nodes = new Array[FlatMapped[_, _, _, _]](depth)
      current = this
      var index = 0
      while (index < depth) {
        val nested = current.asInstanceOf[FlatMapped[_, _, _, _]]
        nodes(index) = nested
        current = nested.fusableSource
        index += 1
      }

      var scalarStream: Stream[_, _] = current
      var scalar: ScalarNode         = root
      var scalarType                 = root.scalarType
      index = depth - 1
      while (index >= 0) {
        val nested = nodes(index)
        val child  =
          scalarType match {
            case JvmType.Boolean =>
              nested.safeF.asInstanceOf[Boolean => Stream[Any, Any]](scalar.scalarBoolean)
            case JvmType.Byte   => nested.safeF.asInstanceOf[Byte => Stream[Any, Any]](scalar.scalarByte)
            case JvmType.Char   => nested.safeF.asInstanceOf[Char => Stream[Any, Any]](scalar.scalarChar)
            case JvmType.Double =>
              nested.safeF.asInstanceOf[Double => Stream[Any, Any]](scalar.scalarDouble)
            case JvmType.Float => nested.safeF.asInstanceOf[Float => Stream[Any, Any]](scalar.scalarFloat)
            case JvmType.Int   => nested.safeF.asInstanceOf[Int => Stream[Any, Any]](scalar.scalarInt)
            case JvmType.Long  => nested.safeF.asInstanceOf[Long => Stream[Any, Any]](scalar.scalarLong)
            case JvmType.Short => nested.safeF.asInstanceOf[Short => Stream[Any, Any]](scalar.scalarShort)
            case _             => nested.safeF.asInstanceOf[AnyRef => Stream[Any, Any]](scalar.scalarRef)
          }
        child match {
          case next: SingletonInt =>
            scalarStream = next
            scalar = next
            scalarType = JvmType.Int
          case next: ScalarNode =>
            scalarStream = child
            scalar = next
            scalarType = next.scalarType
          case _ =>
            var remainder = child
            index -= 1
            while (index >= 0) {
              val outer = nodes(index)
              remainder = outer.prependCollapsedRemainder(remainder)
              index -= 1
            }
            return remainder.asInstanceOf[Stream[E2, B]]
        }
        index -= 1
      }
      scalarStream.asInstanceOf[Stream[E2, B]]
    }

    private def prependCollapsedRemainder(remainder: Stream[Any, Any]): Stream[Any, Any] =
      new FlatMapped[Any, Any, Any, B](
        remainder,
        safeF.asInstanceOf[Any => Stream[Any, B]],
        inRepresentation,
        jtB
      )

    private def compileBlockingSpecialized(bufferSize: Int): Reader.SyncReader[B] = {
      val outType                                        = jtB.jvmType
      val sourceReader                                   = self.compileBlocking(bufferSize)
      val inType                                         = ElementRepresentation.resolve(inRepresentation, sourceReader.jvmType)
      val inLane                                         = SyncInterpreter.laneOf(inType)
      val compileInner: AnyRef => Reader.SyncReader[Any] = (stream: AnyRef) =>
        stream.asInstanceOf[Stream[Any, Any]].compileBlocking(Stream.DefaultBufferSize)
      val smallInput  = SyncInterpreter.laneOf(inType) == 0 && (inType ne JvmType.Int)
      val smallOutput = SyncInterpreter.laneOf(outType) == 0 && (outType ne JvmType.Int)
      val reader      =
        try
          if (smallInput || smallOutput) {
            val interpreter = SyncInterpreter(sourceReader)
            interpreter.addPush(inType, outType)(safeF)
            interpreter
          } else
            (inLane: @scala.annotation.switch) match {
              case 0 => new Reader.FlatMappedInt(sourceReader, safeF, compileInner, outType, inType)
              case 1 => new Reader.FlatMappedLong(sourceReader, safeF, compileInner, outType)
              case 2 => new Reader.FlatMappedFloat(sourceReader, safeF, compileInner, outType)
              case 3 => new Reader.FlatMappedDouble(sourceReader, safeF, compileInner, outType)
              case _ => new Reader.FlatMappedRef(sourceReader, safeF, compileInner, outType)
            }
        catch { case primary: Throwable => rejectSyncReader(sourceReader, primary) }
      reader.asInstanceOf[Reader.SyncReader[B]]
    }
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addPush(pipeline.outputType, jtB.jvmType)(safeF)
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferPush(jtB.jvmType)(safeF)
      self
    }
    override private[streams] def runFoldLongBlocking(
      zero: Long,
      fold: (Long, B) => Long
    ): Either[E2, Long] = {
      if ((jtB.jvmType ne JvmType.Int) || !inRepresentation.stableJvmType.contains(JvmType.Int))
        return super.runFoldLongBlocking(zero, fold)

      var depth   = 0
      var current = this.asInstanceOf[Stream[E2, Int]]
      while (current.isInstanceOf[FlatMapped[_, _, _, _]]) {
        val nested = current.asInstanceOf[FlatMapped[Nothing, E2, Int, Int]]
        if ((nested.jtB.jvmType ne JvmType.Int) || !nested.inRepresentation.stableJvmType.contains(JvmType.Int))
          return super.runFoldLongBlocking(zero, fold)
        depth += 1
        current = nested.fusableSource.asInstanceOf[Stream[E2, Int]]
      }
      current match {
        case singleton: SingletonInt =>
          val nodes = new Array[FlatMapped[Nothing, E2, Int, Int]](depth)
          current = this.asInstanceOf[Stream[E2, Int]]
          var index = 0
          while (index < depth) {
            val nested = current.asInstanceOf[FlatMapped[Nothing, E2, Int, Int]]
            nodes(index) = nested
            current = nested.fusableSource.asInstanceOf[Stream[E2, Int]]
            index += 1
          }

          var value = singleton.value
          index = depth - 1
          while (index >= 0) {
            nodes(index).safeF(value) match {
              case next: SingletonInt => value = next.value
              case child              =>
                var remainder = child.asInstanceOf[Stream[E2, Int]]
                index -= 1
                while (index >= 0) {
                  val nested = nodes(index)
                  remainder = new FlatMapped[E2, E2, Int, Int](
                    remainder,
                    nested.safeF.asInstanceOf[Int => Stream[E2, Int]],
                    nested.inRepresentation,
                    JvmType.Infer.int
                  )
                  index -= 1
                }
                return remainder.runFoldLongBlocking(zero, fold.asInstanceOf[(Long, Int) => Long])
            }
            index -= 1
          }
          Right(StreamError.callback(fold.asInstanceOf[(Long, Int) => Long](zero, value)))
        case _ => super.runFoldLongBlocking(zero, fold)
      }
    }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E2, Long]] = {
      if (jtB.jvmType ne JvmType.Int) return null

      self match {
        case mapped: Mapped[_, _, _] if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) =>
          val mappedInt = mapped.asInstanceOf[Mapped[Any, Int, A]]
          val mapOuter  = (value: Int) => mappedInt.safeF(value)
          val expand    = safeF.asInstanceOf[A => Stream[E2, Int]]
          val direct    = Stream.useLinearIntFlatMapLongDirect(
            mappedInt.self.asInstanceOf[Stream[E2, Int]],
            value => expand(mapOuter(value)),
            zero,
            fold.asInstanceOf[(Long, Int) => Async[Long]]
          )
          if (direct.asInstanceOf[AnyRef] ne null) return direct
        case _ => ()
      }

      if (!inRepresentation.stableJvmType.contains(JvmType.Int)) return null

      val expand    = safeF.asInstanceOf[Int => Stream[E2, Int]]
      val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
      val foldChild = new NestedFlatMapIntLongFold[E2](expand, reduce)

      self match {
        case source: AsyncSource[_, _] =>
          val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          if (source.protectAcquisition)
            new AsyncFlatMapIntLongUseProtected[E2](acquire, expand, zero, reduce)
          else new AsyncFlatMapIntLongUseUnprotected[E2](acquire, expand, zero, reduce)
        case _: Filtered[_, _] | _: Mapped[_, _, _] =>
          val direct = Stream.useLinearIntFlatMapLongDirect(
            self.asInstanceOf[Stream[E2, Int]],
            expand,
            zero,
            reduce
          )
          if (direct.asInstanceOf[AnyRef] ne null) direct
          else {
            val nested = Stream.useLinearIntFoldLongDirect(
              self.asInstanceOf[Stream[E2, Int]],
              zero,
              foldChild
            )
            if (nested.asInstanceOf[AnyRef] ne null) nested
            else
              self
                .asInstanceOf[Stream[E2, Int]]
                .runFoldAsync(zero)(foldChild)
          }
        case nested: FlatMapped[_, _, _, _]
            if nested.inRepresentation.stableJvmType.contains(JvmType.Int) &&
              (nested.jtB.jvmType eq JvmType.Int) =>
          Async
            .deferCancelable(
              () =>
                nested
                  .asInstanceOf[FlatMapped[Any, Any, Int, Int]]
                  .runFoldLongDirect(zero, foldChild),
              () => ()
            )
            .flatten
            .asInstanceOf[Async[Either[E2, Long]]]
        case _ =>
          self
            .asInstanceOf[Stream[E2, Int]]
            .runFoldAsync(zero)(foldChild)
      }
    }

    private def wrapReader(upstream: Reader[A]): Reader[B] = upstream match {
      case reader: Reader.SyncReader[A @unchecked] =>
        transformSync(reader)(_.deferPush(jtB.jvmType)(safeF))
      case reader: Reader.AsyncReader[A @unchecked] =>
        transformAsync(reader)(_.deferPush(jtB.jvmType)(safeF))
    }
  }

  /** Resource-safe stream: acquires R, uses it, then releases on close. */
  private[streams] final class FromAcquireRelease[R, E, A](
    acquire: => R,
    release: R => Unit,
    use: R => Stream[E, A],
    representation: ElementRepresentation = ElementRepresentation.LateBound
  ) extends Stream[E, A]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation = representation
    def render: String                                                         = "Stream.fromAcquireRelease(...)"
    def stackFrame(bufferSize: Int): CompileFrame                              = {
      val resource = StreamError.callback(acquire)
      val stream   =
        try StreamError.callback(use(resource))
        catch {
          case primary: Throwable =>
            try StreamError.callback(release(resource))
            catch { case cleanup: Throwable => StreamError.attachCleanupReplay(primary, cleanup) }
            throw primary
        }
      new CompileFrame(
        stream,
        bufferSize,
        reader => wrapReader(reader.asInstanceOf[Reader[A]], resource),
        primary =>
          try { StreamError.callback(release(resource)); primary }
          catch { case cleanup: Throwable => StreamError.attachCleanupReplay(primary, cleanup) }
      )
    }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else makeReader(depth + 1, bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val r          = StreamError.callback(acquire)
      val checkpoint = pipeline.materializationCheckpoint()
      val wrapping   = pipeline.newWrapTransaction()
      try {
        pipeline.ensureMaterializationCurrent()
        val stream = StreamError.callback(use(r))
        pipeline.ensureMaterializationCurrent()
        stream.compileInterpreter(pipeline)
        pipeline.ensureMaterializationCurrent()
        pipeline.wrapLastRead(src => wrap(src, r), wrapping)
      } catch {
        case t: Throwable =>
          pipeline.rollbackTo(checkpoint, t)
          if (!wrapping.cleanupConsumedOwner)
            try StreamError.callback(release(r))
            catch { case releaseFailure: Throwable => pipeline.recordCleanupFailure(t, releaseFailure) }
          throw t
      }
    }
    private def makeReader(depth: Int, bufferSize: Int): Reader[A] = {
      val r = StreamError.callback(acquire)
      try {
        wrapReader(StreamError.callback(use(r)).compile(depth, bufferSize), r)
      } catch {
        case t: Throwable =>
          try StreamError.callback(release(r))
          catch { case releaseFailure: Throwable => StreamError.attachCleanup(t, releaseFailure) }
          throw t
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferOwnedReaderRoot(makeReader(0, Stream.DefaultBufferSize))
      null
    }
    private def wrap[B](src: Reader.SyncReader[B], resource: R): Reader.SyncReader[B] =
      new LifecycleWrappingReader[B](src, "Cannot reset a finalized resource reader", false) {
        protected def finishClose(primary: Throwable): Throwable =
          try { StreamError.callback(release(resource)); primary }
          catch { case cause: Throwable => StreamError.attachCleanup(primary, cause) }
      }
    private def wrapReader(src: Reader[A], resource: R): Reader[A] = src match {
      case reader: Reader.SyncReader[A @unchecked]  => wrap(reader, resource)
      case reader: Reader.AsyncReader[A @unchecked] =>
        reader.withReleaseAsync(() => Async.reschedule(() => Async.succeed(StreamError.callback(release(resource)))))
    }
  }

  /** Resource-safe stream with asynchronous acquisition and release. */
  private[streams] final class FromAcquireReleaseAsync[R, E, A](
    acquire: () => Async[R],
    release: R => Async[Unit],
    use: R => Stream[E, A],
    jtA: JvmType.Infer[A]
  ) extends Stream[E, A] {
    private lazy val source = new GenericAsyncSource[E, A](
      () =>
        Async.acquireInstallCancelable(
          () => acquire(),
          (resource: R) =>
            Reader.closed.toAsync
              .concatAsyncWithJvmType(
                () =>
                  StreamError.callbackAsync(
                    Async.succeed(StreamError.callback(use(resource)).compile(0, DefaultBufferSize))
                  ),
                jtA.jvmType
              )
              .withReleaseAsync(() => StreamError.callbackAsync(release(resource))),
          (resource: R) => StreamError.callbackAsync(release(resource)),
          StreamError.callbackFailure
        ),
      "Stream.fromAcquireReleaseAsync(...)",
      ElementRepresentation.fromJvmType(jtA.jvmType)
    )

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      source.compile(depth, bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit                 = source.compileInterpreter(pipeline)
    override private[streams] def elementRepresentation: ElementRepresentation               = source.elementRepresentation
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] =
      source.materializeAsync(pipeline)
    def render: String = source.render
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] =
      Async.bracketAsync(
        () => acquire(),
        (resource: R) => StreamError.callbackAsync(use(resource).runFoldAsync(zero)(fold)),
        (resource: R) => StreamError.callbackAsync(release(resource)),
        StreamError.callbackFailure
      )
  }

  /** Leaf stream backed by a known, materialized [[Chunk]]. */
  private[streams] final class FromChunkStream[A](
    chunk: Chunk[A],
    jt: JvmType.Infer[A]
  ) extends Stream[Nothing, A] {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jt.jvmType)
    override def knownChunk: Option[Chunk[A]]                                                = Some(chunk)
    override def knownLength: Option[Long]                                                   = Some(chunk.length.toLong)
    def render: String                                                                       = "Stream.fromChunk(...)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader.SyncReader[A] =
      Reader.fromChunk(chunk)(jt)
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] =
      Reader.fromChunk(chunk)(jt)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      pipeline.appendRead(Reader.fromChunk(chunk)(jt), jt.jvmType)
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReader(() => Reader.fromChunk(chunk)(jt), jt.jvmType)
      null
    }
  }

  /**
   * A stateful reader wrapper that preserves reader kind and bounds recursive
   * compilation.
   */
  private[streams] final class StatefulReaderStream[E, A, B](
    source: Stream[E, A],
    materialize: Reader[A] => Reader[B],
    label: String,
    representation: ElementRepresentation
  ) extends Stream[E, B]
      with StackCompileNode {
    override private[streams] def elementRepresentation: ElementRepresentation = representation
    def render: String                                                         = label

    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(source, bufferSize, reader => materialize(reader.asInstanceOf[Reader[A]]))

    private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else materialize(source.compile(depth + 1, bufferSize))

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val reader = materialize(compileToReader(source, pipeline)).asInstanceOf[Reader.SyncReader[B]]
      try {
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(reader)
      } catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(reader, cause)
          throw cause
      }
    }

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => materialize(source.compile(0, Stream.DefaultBufferSize)))
      null
    }
  }

  /** Allocation-minimal source for the common unit-step integer range. */
  private[streams] final class IntRange(start: Int, until: Int, length: Int) extends Stream[Nothing, Int] {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.Known(JvmType.Int)
    override def knownLength: Option[Long] = Some(length.toLong)

    def render: String                                                              = s"Stream.range($start, $until)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int] = newReader()

    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[Int] = newReader()

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val reader = newReader()
      try {
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(reader)
      } catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(reader, cause)
          throw cause
      }
    }

    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[Int] => Z,
      onAsync: Reader.AsyncReader[Int] => Z
    ): Z = onSync(newReader())

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => newReader())
      null
    }

    override private[streams] def runFoldLongBlocking(
      z: Long,
      fold: (Long, Int) => Long
    ): Either[Nothing, Long] = runFoldLongTransformedIntBlocking(z, null, null, fold)

    override private[streams] def runFoldLongTransformedIntBlocking(
      z: Long,
      map: Int => Int,
      predicate: Int => Boolean,
      fold: (Long, Int) => Long
    ): Either[Nothing, Long] =
      try {
        val result =
          if (map eq null) {
            if (predicate eq null) foldRange(z, fold)
            else foldFilteredRange(z, predicate, fold)
          } else if (predicate eq null) foldMappedRange(z, map, fold)
          else foldMappedFilteredRange(z, map, predicate, fold)
        Right(result)
      } catch {
        case error: StreamError => throw StreamError.untrusted(error)
      }

    @noinline
    private def foldFilteredRange(z: Long, predicate: Int => Boolean, fold: (Long, Int) => Long): Long =
      foldRangeProgram(z, null, predicate, null, 0, length, fold)

    @noinline
    private def foldMappedFilteredRange(
      z: Long,
      map: Int => Int,
      predicate: Int => Boolean,
      fold: (Long, Int) => Long
    ): Long = foldRangeProgram(z, map, predicate, null, 0, length, fold)

    @noinline
    private def foldMappedRange(z: Long, map: Int => Int, fold: (Long, Int) => Long): Long =
      foldRangeProgram(z, map, null, null, 0, length, fold)

    @noinline
    private def foldRange(z: Long, fold: (Long, Int) => Long): Long =
      foldRangeProgram(z, null, null, null, 0, length, fold)

    private[streams] def foldTakeRange(z: Long, n: Long, fold: (Long, Int) => Long): Long =
      foldRangeProgram(z, null, null, null, 0, math.min(length.toLong, math.max(0L, n)).toInt, fold)

    private[streams] def foldTakeWhileRange(
      z: Long,
      predicate: Int => Boolean,
      fold: (Long, Int) => Long
    ): Long = foldRangeProgram(z, null, null, predicate, 0, length, fold)

    private[streams] def foldTakeDropRange(
      z: Long,
      dropCount: Long,
      takeCount: Long,
      fold: (Long, Int) => Long
    ): Long = {
      val from = math.min(length.toLong, math.max(0L, dropCount)).toInt
      val size = math.min((length - from).toLong, math.max(0L, takeCount)).toInt
      foldRangeProgram(z, null, null, null, from, size, fold)
    }

    /**
     * Operation-neutral traversal for the immutable range root. The Long
     * accumulator is a JVM adapter; map, filter, slicing, and short-circuit
     * semantics are represented independently and shared by every range fast
     * path.
     */
    private def foldRangeProgram(
      z: Long,
      map: Int => Int,
      filter: Int => Boolean,
      takeWhile: Int => Boolean,
      from: Int,
      size: Int,
      fold: (Long, Int) => Long
    ): Long = {
      var acc     = z
      var current = start + from
      var index   = 0
      var taking  = true
      while (index < size && taking) {
        val mapped = if (map eq null) current else map(current)
        if ((takeWhile ne null) && !takeWhile(mapped)) taking = false
        else if ((filter eq null) || filter(mapped)) acc = fold(acc, mapped)
        current += 1
        index += 1
      }
      acc
    }

    private def newReader(): Reader.SyncReader[Int] = Reader.fromRange(start, 1, length)
  }

  /** Leaf stream backed by a lazily-created [[Reader]]. */
  private[streams] class FromReader[E, A](
    mkReader: () => Reader[A],
    val renderLabel: String = "Stream.fromReader(...)"
  ) extends Stream[E, A] {
    def render: String                                                                   = renderLabel
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A]        = mkReader()
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] =
      Sink.toSyncReader(mkReader())
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => mkReader())
      null
    }
  }

  /** Leaf stream whose reader kind is statically synchronous. */
  private[streams] class FromSyncReader[E, A](
    private val mkSyncReader: () => Reader.SyncReader[A],
    renderLabel: String = "Stream.fromReader(...)",
    representation: ElementRepresentation = ElementRepresentation.LateBound
  ) extends FromReader[E, A](mkSyncReader, renderLabel) {
    override private[streams] def elementRepresentation: ElementRepresentation           = representation
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A] = compileSync()

    override private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val reader = mkSyncReader()
      try {
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(reader)
      } catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(reader, cause)
          throw cause
      }
    }

    private[streams] def compileSync(): Reader.SyncReader[A] = mkSyncReader()

    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[A] => Z,
      onAsync: Reader.AsyncReader[A] => Z
    ): Z                                                                                     = onSync(compileSync())
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => mkSyncReader())
      null
    }
  }

  /** Lane-aware stream backed by an immutable strict `Vector`. */
  private[streams] final class FromVectorStream[A](
    values: Vector[A],
    jtA: JvmType.Infer[A]
  ) extends Stream[Nothing, A] {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtA.jvmType)
    override def knownLength: Option[Long] = Some(values.length.toLong)
    def render: String                     = "Stream.fromIterable(...)"

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader.SyncReader[A] = newReader()
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[A]     = newReader()
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit                 = {
      val reader = newReader()
      try {
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(reader)
      } catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(reader, cause)
          throw cause
      }
    }
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[A] => Z,
      onAsync: Reader.AsyncReader[A] => Z
    ): Z                                                                                     = onSync(newReader())
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => newReader())
      null
    }

    private[streams] def foldSliceLong(
      z: Long,
      dropCount: Long,
      takeCount: Long,
      predicate: A => Boolean,
      fold: (Long, A) => Long
    ): Long = {
      val from = math.min(values.length.toLong, math.max(0L, dropCount)).toInt
      val size = math.min((values.length - from).toLong, math.max(0L, takeCount)).toInt
      val iter = values.slice(from, from + size).iterator
      var acc  = z
      while (iter.hasNext) {
        val value = iter.next()
        if ((predicate eq null) || predicate(value)) acc = fold(acc, value)
      }
      acc
    }

    private[streams] def foldTakeWhileLong(
      z: Long,
      predicate: A => Boolean,
      fold: (Long, A) => Long
    ): Long = {
      val iter   = values.iterator
      var acc    = z
      var taking = true
      while (taking && iter.hasNext) {
        val value = iter.next()
        if (predicate(value)) acc = fold(acc, value)
        else taking = false
      }
      acc
    }

    private def newReader(): Reader.SyncReader[A] = Reader.fromIterable(values)(jtA)
  }

  /**
   * Internal protocol for resource-free singleton streams. Blocking flatMap
   * compilation uses it to collapse scalar spines independently of element and
   * terminal types.
   */
  private[streams] trait ScalarNode { self: Stream[_, _] =>
    def scalarType: JvmType
    def scalarBoolean: Boolean = throw new IllegalStateException("not a Boolean scalar")
    def scalarByte: Byte       = throw new IllegalStateException("not a Byte scalar")
    def scalarChar: Char       = throw new IllegalStateException("not a Char scalar")
    def scalarDouble: Double   = throw new IllegalStateException("not a Double scalar")
    def scalarFloat: Float     = throw new IllegalStateException("not a Float scalar")
    def scalarInt: Int         = throw new IllegalStateException("not an Int scalar")
    def scalarLong: Long       = throw new IllegalStateException("not a Long scalar")
    def scalarShort: Short     = throw new IllegalStateException("not a Short scalar")
    def scalarRef: AnyRef      = throw new IllegalStateException("not a reference scalar")
  }

  private final class LazyBlockingReader[A](outType: JvmType, initialize: () => Reader.SyncReader[A])
      extends Reader.SyncReader[A] {
    private var delegate: Reader.SyncReader[A] = null
    private var closed                         = false
    private var failure: Throwable             = null

    private def reader: Reader.SyncReader[A] = {
      if (failure ne null) throw failure
      if (delegate eq null)
        try delegate = initialize()
        catch {
          case cause: Throwable =>
            failure = cause
            throw cause
        }
      delegate
    }

    override def jvmType: JvmType                                  = outType
    override private[streams] def tryReadable: Reader.Availability =
      if (closed) Reader.Unavailable
      else if (delegate eq null) Reader.Available
      else delegate.tryReadable
    def isClosed: Boolean                                                    = closed || ((delegate ne null) && delegate.isClosed)
    override def readable(): Boolean                                         = !closed && reader.readable()
    def read[A1 >: A](sentinel: A1): A1                                      = if (closed) sentinel else reader.read(sentinel)
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
      if (closed) sentinel else reader.readBooleanPhysical(sentinel)
    override def readByte(): Int                                       = if (closed) -1 else reader.readBytePhysical()
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
      if (closed) sentinel else reader.readCharPhysical(sentinel)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
      if (closed) sentinel else reader.readShortPhysical(sentinel)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (closed) sentinel else reader.readIntPhysical(sentinel)
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (closed) sentinel else reader.readLongPhysical(sentinel)
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (closed) sentinel else reader.readFloatPhysical(sentinel)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (closed) sentinel else reader.readDoublePhysical(sentinel)
    override def readBytes(buffer: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Int =
      if (closed) { Reader.validateArrayRange(buffer, offset, length); if (length == 0) 0 else -1 }
      else reader.readBytesPhysical(buffer, offset, length)
    override def readInts(buffer: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Int =
      if (closed) { Reader.validateArrayRange(buffer, offset, length); if (length == 0) 0 else -1 }
      else reader.readIntsPhysical(buffer, offset, length)
    override def readLongs(buffer: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
      if (closed) { Reader.validateArrayRange(buffer, offset, length); if (length == 0) 0 else -1 }
      else reader.readLongsPhysical(buffer, offset, length)
    override def readFloats(buffer: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Int =
      if (closed) { Reader.validateArrayRange(buffer, offset, length); if (length == 0) 0 else -1 }
      else reader.readFloatsPhysical(buffer, offset, length)
    override def readDoubles(buffer: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
      if (closed) { Reader.validateArrayRange(buffer, offset, length); if (length == 0) 0 else -1 }
      else reader.readDoublesPhysical(buffer, offset, length)
    override def setLimit(n: Long): Boolean = !closed && reader.setLimit(n)
    override def setRepeat(): Boolean       = !closed && reader.setRepeat()
    override def setSkip(n: Long): Boolean  = !closed && reader.setSkip(n)
    override def skip(n: Long): Unit        = if (!closed) reader.skip(n)
    override def reset(): Unit              = {
      closed = false
      failure = null
      if (delegate ne null) delegate.reset()
    }
    def close(): Unit = {
      closed = true
      if (delegate ne null) delegate.close()
    }
  }

  /**
   * Allocation-minimal Int singleton with an interpreter-visible scalar value.
   */
  private[streams] final class SingletonInt(val value: Int) extends Stream[Nothing, Int] with ScalarNode {
    def scalarType: JvmType                = JvmType.Int
    override def scalarInt: Int            = value
    override def knownLength: Option[Long] = Some(1L)
    def render: String                     = "Stream.succeed(...)"

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader.SyncReader[Int] = Reader.singleInt(value)
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[Int]     = Reader.singleInt(value)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit                   = {
      val reader = Reader.singleInt(value)
      try {
        pipeline.ensureMaterializationCurrent()
        pipeline.appendRead(reader)
      } catch {
        case cause: Throwable =>
          pipeline.closeRejectedOwner(reader, cause)
          throw cause
      }
    }
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.Known(JvmType.Int)
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[Int] => Z,
      onAsync: Reader.AsyncReader[Int] => Z
    ): Z = onSync(Reader.singleInt(value))
    override private[streams] def runFoldLongBlocking(
      z: Long,
      fold: (Long, Int) => Long
    ): Either[Nothing, Long]                                                                 = Right(StreamError.callback(fold(z, value)))
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferReaderRoot(() => Reader.singleInt(value))
      null
    }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, Int) => Async[Long]
    ): Async[Either[Nothing, Long]] = {
      val reduced = fold match {
        case nested: NestedFlatMapIntLongFold[_] => nested(zero, value)
        case _                                   => StreamError.callbackAsync(fold(zero, value))
      }
      reduced.map(result => rightLong[Nothing](result))
    }
  }

  /** An async boundary that installs the stream produced by an effect. */
  private[streams] final class Unwrapped[E, A](make: () => Async[Stream[E, A]], outType: JvmType) extends Stream[E, A] {
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(outType)
    def render: String = "Stream.unwrap(...)"

    private def boundary(bufferSize: Int): Reader.AsyncReader[A] =
      Reader.closed.toAsync.concatAsyncWithJvmType(
        () => StreamError.callbackAsync(make()).map(stream => stream.compile(0, bufferSize)),
        outType
      )

    private[streams] def evaluateDirect(): Async[Stream[E, A]] =
      StreamError.callbackAsync(make())

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = boundary(bufferSize)

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() => boundary(DefaultBufferSize))
      null
    }

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] =
      StreamError.callbackAsync(make()).flatMap(_.runFoldAsync(zero)(fold))
  }

  private abstract class AsyncFlatMapIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    expand0: Int => Stream[E, Int],
    private var carry: Long,
    fold0: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      ) {
    private var budget: Int                    = 1024
    private var outer: Reader.AsyncReader[Int] = null

    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def expandValue(value: Int): Stream[E, Int] = expand0(value)

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] = {
      val close =
        try
          reader match {
            case sync: Reader.SyncReader[Int @unchecked]   => Async.succeed(sync.close())
            case async: Reader.AsyncReader[Int @unchecked] => async.close()
          }
        catch { case cause: Throwable => Async.fail(cause) }
      close.catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }
    }

    protected def useResource(reader: Reader[Int]): Async[Either[E, Long]] = {
      outer = reader match {
        case sync: Reader.SyncReader[Int @unchecked]   => sync.toAsync
        case async: Reader.AsyncReader[Int @unchecked] => async
      }
      loop()
    }

    private[Stream] def continueAfterChild(next: Long): Async[Either[E, Long]] = {
      carry = next
      loop()
    }

    private def continueAfterExpanded(child: Stream[E, Int]): Async[Either[E, Long]] = child match {
      case singleton: SingletonInt =>
        StreamError.callbackAsync(fold0(carry, singleton.value)).flatMap(continueAfterChild)
      case unwrapped: Unwrapped[_, _] if unwrapped.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        unwrapped
          .asInstanceOf[Unwrapped[E, Int]]
          .evaluateDirect()
          .flatMap(continueAfterExpanded)
      case _ =>
        child.runFoldAsync(carry)(fold0).flatMap {
          case Left(error) => Async.failTrusted(StreamError.source(error))
          case Right(next) => continueAfterChild(next)
        }
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def loop(): Async[Either[E, Long]] = {
      if (budget <= 0) {
        budget = 1024
        return Async.reschedule(() => loop())
      }
      val fold = fold0
      while (budget > 0) {
        val read =
          try outer.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return read.flatMap { value =>
              if (value == Long.MinValue) Async.succeed(rightLong[E](carry))
              else {
                budget -= 1
                val child = expandValue(value.toInt)
                if (child eq null) loop()
                else continueAfterExpanded(child)
              }
            }
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](carry))

        budget -= 1
        val child = expandValue(value.toInt)
        if (child ne null)
          child match {
            case source: AsyncSource[_, _] if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
              val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
              return (
                if (source.protectAcquisition)
                  new AsyncSourceSequenceIntLongUseProtected[E](acquire, this, carry, fold)
                else new AsyncSourceSequenceIntLongUseUnprotected[E](acquire, this, carry, fold)
              )
            case singleton: SingletonInt =>
              val reduced = StreamError.callbackAsync(fold(carry, singleton.value))
              Async.stepKind(reduced) match {
                case 1 => return failedStep(reduced)
                case 2 => return reduced.flatMap(continueAfterChild)
                case _ => carry = Async.stepLong(reduced)
              }
            case unwrapped: Unwrapped[_, _] if unwrapped.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
              val acquired = unwrapped.asInstanceOf[Unwrapped[E, Int]].evaluateDirect()
              Async.stepKind(acquired) match {
                case 1 => return failedStep(acquired)
                case 2 => return acquired.flatMap(continueAfterExpanded)
                case _ =>
                  Async.stepValue(acquired) match {
                    case singleton: SingletonInt =>
                      val reduced = StreamError.callbackAsync(fold(carry, singleton.value))
                      Async.stepKind(reduced) match {
                        case 1 => return failedStep(reduced)
                        case 2 => return reduced.flatMap(continueAfterChild)
                        case _ => carry = Async.stepLong(reduced)
                      }
                    case nested =>
                      return nested.runFoldAsync(carry)(fold).flatMap {
                        case Left(error) => Async.failTrusted(StreamError.source(error))
                        case Right(next) => continueAfterChild(next)
                      }
                  }
              }
            case _ =>
              return child.runFoldAsync(carry)(fold).flatMap {
                case Left(error) => Async.failTrusted(StreamError.source(error))
                case Right(next) => continueAfterChild(next)
              }
          }
      }
      Async.reschedule(() => loop())
    }
  }

  private final class AsyncFlatMapIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    expand: Int => Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFlatMapIntLongUse[E](acquire, expand, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncFlatMapIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    expand: Int => Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFlatMapIntLongUse[E](acquire, expand, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class NestedFlatMapIntLongFold[E](
    expand: Int => Stream[E, Int],
    reduce: (Long, Int) => Async[Long]
  ) extends ((Long, Int) => Async[Long]) {
    def apply(acc: Long, value: Int): Async[Long] = {
      val child = expand(value)
      child match {
        case singleton: SingletonInt                                                                       => StreamError.callbackAsync(reduce(acc, singleton.value))
        case source: AsyncSource[_, _] if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
          val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          if (source.protectAcquisition)
            new AsyncSourceFoldIntLongUseProtected(acquire, null, acc, reduce)
          else new AsyncSourceFoldIntLongUseUnprotected(acquire, null, acc, reduce)
        case _ =>
          child.runFoldAsync(acc)(reduce).map {
            case Left(error) => throw StreamError.source(error)
            case Right(next) => next
          }
      }
    }

    def sequence(parent: AsyncSourceFoldIntLongUse, acc: Long, value: Int): Async[Long] = {
      val child = expand(value)
      child match {
        case singleton: SingletonInt =>
          reduce match {
            case nested: NestedFlatMapIntLongFold[_] =>
              nested.asInstanceOf[NestedFlatMapIntLongFold[E]].sequence(parent, acc, singleton.value)
            case _ =>
              val reduced = StreamError.callbackAsync(reduce(acc, singleton.value))
              Async.stepKind(reduced) match {
                case 0 => parent.continueAfterNestedChild(Async.stepLong(reduced))
                case _ => reduced.flatMap(parent.continueAfterNestedChild)
              }
          }
        case source: AsyncSource[_, _] if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
          val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
          if (source.protectAcquisition)
            new AsyncSourceFoldIntLongUseProtected(acquire, parent, acc, reduce)
          else new AsyncSourceFoldIntLongUseUnprotected(acquire, parent, acc, reduce)
        case _ =>
          child.runFoldAsync(acc)(reduce).flatMap {
            case Left(error) => Async.failTrusted(StreamError.source(error))
            case Right(next) => parent.continueAfterNestedChild(next)
          }
      }
    }
  }

  private abstract class AsyncSourceFoldIntLongUse(
    acquire0: () => Async[Reader[Int]],
    parent: AsyncSourceFoldIntLongUse,
    private var carry: Long,
    private val fold0: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Long](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = fold0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Long]): Async[Long] =
      if ((parent ne null) && Async.stepKind(result) == 0)
        parent.continueAfterNestedChild(Async.stepLong(result))
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(carry))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Long]
    ): Async[Unit] = {
      val closed =
        try
          reader match {
            case sync: Reader.SyncReader[Int @unchecked]   => Async.succeed(sync.close())
            case async: Reader.AsyncReader[Int @unchecked] => async.close()
          }
        catch { case closeFailure: Throwable => Async.fail(closeFailure) }
      def failed(closeFailure: Throwable): Async[Unit] = {
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }
      Async.stepKind(closed) match {
        case 1 => failed(Async.stepCause(closed))
        case 2 => closed.catchAll(failed)
        case _ => closed
      }
    }

    protected def useResource(resource: Reader[Int]): Async[Long] = {
      val reader = resource match {
        case sync: Reader.SyncReader[Int @unchecked]   => sync.toAsync
        case async: Reader.AsyncReader[Int @unchecked] => async
      }
      val fold   = fold0
      var acc    = carry
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return read.flatMap { value =>
              if (value == Long.MinValue) Async.succeed(acc)
              else
                StreamError
                  .callbackAsync(fold(acc, value.toInt))
                  .flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold))
            }
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(acc)

        val reduced = fold match {
          case nested: NestedFlatMapIntLongFold[_] =>
            return nested.asInstanceOf[NestedFlatMapIntLongFold[Any]].sequence(this, acc, value.toInt)
          case _ => StreamError.callbackAsync(fold(acc, value.toInt))
        }
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return reduced.flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold))
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      Async.rescheduleKnown(
        Sink.foldNativeAsyncIntLong(reader, acc, fold),
        () => ()
      )
    }

    private[Stream] def continueAfterNestedChild(next: Long): Async[Long] = {
      carry = next
      useResource(currentResource)
    }

    private def failedStep[A](effect: Async[A]): Async[Long] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }
  }

  private final class AsyncSourceFoldIntLongUseProtected(
    acquire: () => Async[Reader[Int]],
    parent: AsyncSourceFoldIntLongUse,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceFoldIntLongUse(acquire, parent, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceFoldIntLongUseUnprotected(
    acquire: () => Async[Reader[Int]],
    parent: AsyncSourceFoldIntLongUse,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceFoldIntLongUse(acquire, parent, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntCollectUse[E](
    acquire0: () => Async[Reader[Int]]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Chunk[Int]]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      ) {
    private var collection: AnyRef = Chunk.empty

    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Chunk[Int]]]): Async[Either[E, Chunk[Int]]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(Right(result()).asInstanceOf[Either[E, Chunk[Int]]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Chunk[Int]]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Chunk[Int]]] = {
      val reader = toAsyncIntReader(rawReader)
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readUpToN[Int](budget)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return read.flatMap { values =>
              if (values.isEmpty) Async.succeed(Right(result()))
              else {
                append(values)
                finish(Sink.collectNativeAsyncInt(reader))
              }
            }
          case _ =>
        }
        val values = Async.stepValue(read)
        if (values.isEmpty) return Async.succeed(Right(result()))
        append(values)
        budget -= values.length
      }
      finish(
        Async.rescheduleKnown(
          Sink.collectNativeAsyncInt(reader),
          () => ()
        )
      )
    }

    private def append(values: Chunk[Int]): Unit =
      if (values.nonEmpty)
        collection match {
          case chunks: Chunk[_] if chunks.isEmpty => collection = values
          case chunks: Chunk[_]                   =>
            val builder = new ChunkBuilder.Int()
            builder.sizeHint(chunks.length + values.length)
            builder.addAll(chunks.asInstanceOf[Chunk[Int]])
            builder.addAll(values)
            collection = builder
          case builder: ChunkBuilder.Int => builder.addAll(values)
        }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Chunk[Int]]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Chunk[Int]]): Async[Either[E, Chunk[Int]]] =
      effect.map { values =>
        append(values)
        Right(result())
      }

    private def result(): Chunk[Int] = collection match {
      case chunks: Chunk[_]          => chunks.asInstanceOf[Chunk[Int]]
      case builder: ChunkBuilder.Int => builder.result()
    }
  }

  private final class AsyncSourceIntCollectUseProtected[E](
    acquire: () => Async[Reader[Int]]
  ) extends AsyncSourceIntCollectUse[E](acquire) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntCollectUseUnprotected[E](
    acquire: () => Async[Reader[Int]]
  ) extends AsyncSourceIntCollectUse[E](acquire) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntDrainUse[E](
    acquire0: () => Async[Reader[Int]],
    foreach0: Int => Async[Unit]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Unit]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = foreach0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Unit]]): Async[Either[E, Unit]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightUnit.asInstanceOf[Either[E, Unit]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Unit]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(reader: Reader[Int]): Async[Either[E, Unit]] = {
      val asyncReader = toAsyncIntReader(reader)
      if (initialUseContext.asInstanceOf[AnyRef] eq null) useDrain(asyncReader)
      else useForeach(asyncReader, initialUseContext.asInstanceOf[Int => Async[Unit]])
    }

    private def continue(reader: Reader.AsyncReader[Int], foreach: Int => Async[Unit]): Async[Unit] =
      if (foreach eq null) Sink.drainNativeAsyncInt(reader)
      else Sink.foldNativeAsyncIntUnit(reader, foreach)

    private def failedStep[A](effect: Async[A]): Async[Either[E, Unit]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Unit]): Async[Either[E, Unit]] =
      effect.map(_ => RightUnit.asInstanceOf[Either[E, Unit]])

    private def useDrain(reader: Reader.AsyncReader[Int]): Async[Either[E, Unit]] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return read.flatMap(value =>
              if (value == Long.MinValue) Async.succeed(RightUnit.asInstanceOf[Either[E, Unit]])
              else continue(reader, null).map(_ => RightUnit.asInstanceOf[Either[E, Unit]])
            )
          case _ =>
        }
        if (Async.stepLong(read) == Long.MinValue)
          return Async.succeed(RightUnit.asInstanceOf[Either[E, Unit]])
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, null),
          () => ()
        )
      )
    }

    private def useForeach(
      reader: Reader.AsyncReader[Int],
      foreach: Int => Async[Unit]
    ): Async[Either[E, Unit]] = {
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(())
                else StreamError.callbackAsync(foreach(value.toInt)).flatMap(_ => continue(reader, foreach))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(RightUnit.asInstanceOf[Either[E, Unit]])

        val callback = StreamError.callbackAsync(foreach(value.toInt))
        Async.stepKind(callback) match {
          case 1 => return failedStep(callback)
          case 2 => return finish(callback.flatMap(_ => continue(reader, foreach)))
          case _ =>
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, foreach),
          () => ()
        )
      )
    }
  }

  private final class AsyncSourceIntDrainUseProtected[E](
    acquire: () => Async[Reader[Int]],
    foreach: Int => Async[Unit]
  ) extends AsyncSourceIntDrainUse[E](acquire, foreach) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntDrainUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    foreach: Int => Async[Unit]
  ) extends AsyncSourceIntDrainUse[E](acquire, foreach) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntFilteredTakeCollectUse[E](
    acquire0: () => Async[Reader[Int]],
    private var remaining: Long,
    predicate0: Int => Async[Boolean]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Chunk[Int]]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = predicate0
      ) {
    private var collection: AnyRef = Chunk.empty

    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Chunk[Int]]]): Async[Either[E, Chunk[Int]]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(Right(result()).asInstanceOf[Either[E, Chunk[Int]]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Chunk[Int]]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Chunk[Int]]] = {
      val reader = toAsyncIntReader(rawReader)
      if (remaining <= 0L) return success()
      val predicate = initialUseContext.asInstanceOf[Int => Async[Boolean]]
      var budget    = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(Chunk.empty)
                else
                  StreamError.callbackAsync(predicate(value.toInt)).flatMap { keep =>
                    if (keep) {
                      append(value.toInt)
                      remaining -= 1L
                    }
                    continue(reader, predicate)
                  }
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return success()

        val keep = StreamError.callbackAsync(predicate(value.toInt))
        Async.stepKind(keep) match {
          case 1 => return failedStep(keep)
          case 2 =>
            return finish(keep.flatMap { accepted =>
              if (accepted) {
                append(value.toInt)
                remaining -= 1L
              }
              continue(reader, predicate)
            })
          case _ =>
            if (Async.stepBoolean(keep)) {
              append(value.toInt)
              remaining -= 1L
            }
        }
        if (remaining <= 0L) return success()
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, predicate),
          () => ()
        )
      )
    }

    private def append(value: Int): Unit = collection match {
      case chunks: Chunk[_] if chunks.isEmpty => collection = Chunk.single(value)
      case chunks: Chunk[_]                   =>
        val builder = new ChunkBuilder.Int()
        builder.sizeHint(chunks.length + 1)
        builder.addAll(chunks.asInstanceOf[Chunk[Int]])
        builder.addOne(value)
        collection = builder
      case builder: ChunkBuilder.Int => builder.addOne(value)
    }

    private def append(values: Chunk[Int]): Unit =
      if (values.nonEmpty)
        collection match {
          case chunks: Chunk[_] if chunks.isEmpty => collection = values
          case chunks: Chunk[_]                   =>
            val builder = new ChunkBuilder.Int()
            builder.sizeHint(chunks.length + values.length)
            builder.addAll(chunks.asInstanceOf[Chunk[Int]])
            builder.addAll(values)
            collection = builder
          case builder: ChunkBuilder.Int => builder.addAll(values)
        }

    private def continue(
      reader: Reader.AsyncReader[Int],
      predicate: Int => Async[Boolean]
    ): Async[Chunk[Int]] =
      if (remaining <= 0L) Async.succeed(Chunk.empty)
      else Sink.collectTakenFilteredNativeAsyncInt(reader, remaining, predicate)

    private def failedStep[A](effect: Async[A]): Async[Either[E, Chunk[Int]]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Chunk[Int]]): Async[Either[E, Chunk[Int]]] =
      effect.map { values =>
        append(values)
        Right(result())
      }

    private def result(): Chunk[Int] = collection match {
      case chunks: Chunk[_]          => chunks.asInstanceOf[Chunk[Int]]
      case builder: ChunkBuilder.Int => builder.result()
    }

    private def success(): Async[Either[E, Chunk[Int]]] =
      Async.succeed(Right(result()))
  }

  private final class AsyncSourceIntFilteredTakeCollectUseProtected[E](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    predicate: Int => Async[Boolean]
  ) extends AsyncSourceIntFilteredTakeCollectUse[E](acquire, math.max(0L, limit), predicate) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntFilteredTakeCollectUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    predicate: Int => Async[Boolean]
  ) extends AsyncSourceIntFilteredTakeCollectUse[E](acquire, math.max(0L, limit), predicate) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntFoldUse[E, Z](
    acquire0: () => Async[Reader[Int]],
    private val limit: Long,
    private val zero: Z,
    fold0: (Z, Int) => Async[Z]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Z]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = fold0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Z]]): Async[Either[E, Z]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(Right(zero)))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Z]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Z]] = {
      val reader    = toAsyncIntReader(rawReader)
      val fold      = initialUseContext.asInstanceOf[(Z, Int) => Async[Z]]
      var acc       = zero
      var remaining = limit
      var budget    = 1024
      while (budget > 0) {
        if (remaining <= 0L) return Async.succeed(Right(acc))
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(fold(acc, value.toInt))
                    .flatMap(next => Sink.foldTakenNativeAsyncInt(reader, remaining - 1L, next, fold))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(Right(acc))

        val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 =>
            return finish(
              reduced.flatMap(next => Sink.foldTakenNativeAsyncInt(reader, remaining - 1L, next, fold))
            )
          case _ => acc = Async.stepValue(reduced)
        }
        remaining -= 1L
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldTakenNativeAsyncInt(reader, remaining, acc, fold),
          () => ()
        )
      )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Z]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Z]): Async[Either[E, Z]] =
      effect.map(value => Right(value))
  }

  private final class AsyncSourceIntFoldUseProtected[E, Z](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    zero: Z,
    fold: (Z, Int) => Async[Z]
  ) extends AsyncSourceIntFoldUse[E, Z](acquire, limit, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntFoldUseUnprotected[E, Z](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    zero: Z,
    fold: (Z, Int) => Async[Z]
  ) extends AsyncSourceIntFoldUse[E, Z](acquire, limit, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceMappedIntIntUse[E, B](
    acquire0: () => Async[Reader[Int]],
    private val zero: Int,
    private val transform: Int => B,
    private val fold: (Int, B) => Async[Int]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Int]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      )
      with ((Int, Int) => Async[Int]) {
    def apply(acc: Int, value: Int): Async[Int] = fold(acc, transform(value))

    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Int]]): Async[Either[E, Int]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(rightInt[E](zero)))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Int]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Int]] = {
      val reader = toAsyncIntReader(rawReader)
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(this.apply(acc, value.toInt))
                    .flatMap(next => Sink.foldNativeAsyncIntInt(reader, next, this))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightInt[E](acc))

        val reduced = StreamError.callbackAsync(this.apply(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return finish(reduced.flatMap(next => Sink.foldNativeAsyncIntInt(reader, next, this)))
          case _ => acc = Async.stepValue(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldNativeAsyncIntInt(reader, acc, this),
          () => ()
        )
      )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Int]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Int]): Async[Either[E, Int]] =
      effect.map(rightInt[E])
  }

  private final class AsyncSourceMappedIntIntUseProtected[E, B](
    acquire: () => Async[Reader[Int]],
    zero: Int,
    transform: Int => B,
    fold: (Int, B) => Async[Int]
  ) extends AsyncSourceMappedIntIntUse[E, B](acquire, zero, transform, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceMappedIntIntUseUnprotected[E, B](
    acquire: () => Async[Reader[Int]],
    zero: Int,
    transform: Int => B,
    fold: (Int, B) => Async[Int]
  ) extends AsyncSourceMappedIntIntUse[E, B](acquire, zero, transform, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntDoubleUse[E](
    acquire0: () => Async[Reader[Int]],
    private val zero: Double
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Double]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      )
      with ((Double, Int) => Async[Double]) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Double]]): Async[Either[E, Double]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightDoubleZero.asInstanceOf[Either[E, Double]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Double]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Double]] = {
      val reader = toAsyncIntReader(rawReader)
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(this.apply(acc, value.toInt))
                    .flatMap(next => Sink.foldNativeAsyncIntDouble(reader, next, this))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightDouble[E](acc))

        val reduced = StreamError.callbackAsync(this.apply(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return finish(reduced.flatMap(next => Sink.foldNativeAsyncIntDouble(reader, next, this)))
          case _ => acc = Async.stepValue(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldNativeAsyncIntDouble(reader, acc, this),
          () => ()
        )
      )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Double]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Double]): Async[Either[E, Double]] =
      effect.map(rightDouble[E])
  }

  private final class AsyncSourceIntDoubleUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Double,
    private val transform: Int => Double,
    private val fold: (Double, Double) => Async[Double]
  ) extends AsyncSourceIntDoubleUse[E](acquire, zero) {
    def apply(acc: Double, value: Int): Async[Double] = fold(acc, transform(value))

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntDoubleUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Double,
    private val transform: Int => Double,
    private val fold: (Double, Double) => Async[Double]
  ) extends AsyncSourceIntDoubleUse[E](acquire, zero) {
    def apply(acc: Double, value: Int): Async[Double] = fold(acc, transform(value))

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceAsyncMappedIntDoubleUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Double,
    private val transform: Int => Double,
    private val map: Double => Async[Double],
    private val fold: (Double, Double) => Async[Double]
  ) extends AsyncSourceIntDoubleUse[E](acquire, zero) {
    def apply(acc: Double, value: Int): Async[Double] =
      StreamError
        .callbackAsync(map(transform(value)))
        .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)))

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceAsyncMappedIntDoubleUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Double,
    private val transform: Int => Double,
    private val map: Double => Async[Double],
    private val fold: (Double, Double) => Async[Double]
  ) extends AsyncSourceIntDoubleUse[E](acquire, zero) {
    def apply(acc: Double, value: Int): Async[Double] =
      StreamError
        .callbackAsync(map(transform(value)))
        .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)))

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntFloatUse[E](
    acquire0: () => Async[Reader[Int]],
    private val zero: Float
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Float]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      )
      with ((Float, Int) => Async[Float]) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Float]]): Async[Either[E, Float]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightFloatZero.asInstanceOf[Either[E, Float]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Float]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Float]] = {
      val reader = toAsyncIntReader(rawReader)
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(this.apply(acc, value.toInt))
                    .flatMap(next => Sink.foldNativeAsyncIntFloat(reader, next, this))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightFloat[E](acc))

        val reduced = StreamError.callbackAsync(this.apply(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return finish(reduced.flatMap(next => Sink.foldNativeAsyncIntFloat(reader, next, this)))
          case _ => acc = Async.stepValue(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldNativeAsyncIntFloat(reader, acc, this),
          () => ()
        )
      )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Float]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Float]): Async[Either[E, Float]] =
      effect.map(rightFloat[E])
  }

  private final class AsyncSourceIntFloatUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Float,
    private val transform: Int => Float,
    private val fold: (Float, Float) => Async[Float]
  ) extends AsyncSourceIntFloatUse[E](acquire, zero) {
    def apply(acc: Float, value: Int): Async[Float] = fold(acc, transform(value))

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntFloatUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Float,
    private val transform: Int => Float,
    private val fold: (Float, Float) => Async[Float]
  ) extends AsyncSourceIntFloatUse[E](acquire, zero) {
    def apply(acc: Float, value: Int): Async[Float] = fold(acc, transform(value))

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceAsyncMappedIntFloatUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Float,
    private val transform: Int => Float,
    private val map: Float => Async[Float],
    private val fold: (Float, Float) => Async[Float]
  ) extends AsyncSourceIntFloatUse[E](acquire, zero) {
    def apply(acc: Float, value: Int): Async[Float] =
      StreamError
        .callbackAsync(map(transform(value)))
        .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)))

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceAsyncMappedIntFloatUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Float,
    private val transform: Int => Float,
    private val map: Float => Async[Float],
    private val fold: (Float, Float) => Async[Float]
  ) extends AsyncSourceIntFloatUse[E](acquire, zero) {
    def apply(acc: Float, value: Int): Async[Float] =
      StreamError
        .callbackAsync(map(transform(value)))
        .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)))

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    private val zero: Long,
    foldContext: AnyRef
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = foldContext
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def foldFunction: (Long, Int) => Async[Long] =
      initialUseContext.asInstanceOf[(Long, Int) => Async[Long]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader = toAsyncIntReader(rawReader)
      val fold   = foldFunction
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(fold(acc, value.toInt))
                    .flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return finish(reduced.flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold)))
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldNativeAsyncIntLong(reader, acc, fold),
          () => ()
        )
      )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(rightLong[E])
  }

  private final class AsyncSourceConcatIntLongFold[E](
    sources: Array[AnyRef],
    sourceCount: Int,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceSequenceIntLongParent[E] {
    def continueAfterNestedChild(next: Long): Async[Either[E, Long]] =
      if (index >= sourceCount) Async.succeed(rightLong[E](next))
      else {
        val source = sources(index)
        index += 1
        source match {
          case async: AsyncSource[_, _] =>
            val acquire = async.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            if (async.protectAcquisition)
              new AsyncSourceSequenceIntLongUseProtected[E](acquire, this, next, fold)
            else new AsyncSourceSequenceIntLongUseUnprotected[E](acquire, this, next, fold)
          case mapped: AsyncMapped[_, _, _] =>
            mapped
              .asInstanceOf[AsyncMapped[E, Int, Int]]
              .runFoldLongDirect(next, fold)
              .flatMap {
                case Left(error)  => Async.succeed(Left(error))
                case Right(value) => continueAfterNestedChild(value)
              }
        }
      }

    private var index = 0
  }

  private final class AsyncSourceIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceIntLongUse[E](acquire, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceIntLongUse[E](acquire, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceMappedIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    private val transform: Int => Long,
    private val fold: (Long, Long) => Async[Long]
  ) extends AsyncSourceIntLongUse[E](acquire, zero, fold)
      with ((Long, Int) => Async[Long]) {
    def apply(acc: Long, value: Int): Async[Long] = fold(acc, transform(value))

    override protected def foldFunction: (Long, Int) => Async[Long] = this

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceMappedIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    private val transform: Int => Long,
    private val fold: (Long, Long) => Async[Long]
  ) extends AsyncSourceIntLongUse[E](acquire, zero, fold)
      with ((Long, Int) => Async[Long]) {
    def apply(acc: Long, value: Int): Async[Long] = fold(acc, transform(value))

    override protected def foldFunction: (Long, Int) => Async[Long] = this

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceIntSyncLongMapUse[E, Z](
    acquire0: () => Async[Reader[Int]],
    private val zero: Long,
    private val fold: (Long, Int) => Long,
    private val map: Long => Async[Z]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Z]](
        releaseAfterUse = true,
        acquisitionContext = acquire0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Z]]): Async[Either[E, Z]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      closeIntReader(reader).catchAll(closeFailure => Async.fail(StreamError.attachCleanup(null, closeFailure)))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Z]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(reader: Reader[Int]): Async[Either[E, Z]] =
      consume(toAsyncIntReader(reader), zero)

    private def consume(reader: Reader.AsyncReader[Int], initial: Long): Async[Either[E, Z]] = {
      var acc    = initial
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return read.flatMap { value =>
              if (value == Long.MinValue) finish(acc)
              else consume(reader, StreamError.callback(fold(acc, value.toInt)))
            }
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return finish(acc)
        acc = StreamError.callback(fold(acc, value.toInt))
        budget -= 1
      }
      Async.reschedule(() => consume(reader, acc))
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Z]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(acc: Long): Async[Either[E, Z]] =
      StreamError.callbackAsync(map(acc)).map(value => Right(value))
  }

  private final class AsyncSourceIntSyncLongMapUseProtected[E, Z](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    fold: (Long, Int) => Long,
    map: Long => Async[Z]
  ) extends AsyncSourceIntSyncLongMapUse[E, Z](acquire, zero, fold, map) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceIntSyncLongMapUseUnprotected[E, Z](
    acquire: () => Async[Reader[Int]],
    zero: Long,
    fold: (Long, Int) => Long,
    map: Long => Async[Z]
  ) extends AsyncSourceIntSyncLongMapUse[E, Z](acquire, zero, fold, map) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncSourceSequenceIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    next: AnyRef,
    private var carry: Long,
    fold0: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = fold0
      )
      with AsyncSourceSequenceIntLongParent[E] {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] = {
      val completed =
        if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
          Async.stepCause(result) match {
            case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
            case _                                          => result
          }
        else result
      if ((next ne null) && Async.stepKind(completed) == 0)
        Async.stepValue(completed) match {
          case Right(_) =>
            next match {
              case nextSource: AsyncSource[_, _] =>
                val acquire = nextSource.acquisition.asInstanceOf[() => Async[Reader[Int]]]
                if (nextSource.protectAcquisition)
                  new AsyncSourceSequenceIntLongUseProtected[E](acquire, null, carry, fold0)
                else new AsyncSourceSequenceIntLongUseUnprotected[E](acquire, null, carry, fold0)
              case parent: AsyncFlatMapIntLongUse[_] =>
                parent.asInstanceOf[AsyncFlatMapIntLongUse[E]].continueAfterChild(carry)
              case parent: AsyncSourceSequenceIntLongParent[_] =>
                parent.asInstanceOf[AsyncSourceSequenceIntLongParent[E]].continueAfterNestedChild(carry)
            }
          case Left(_) => completed
        }
      else completed
    }

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader = toAsyncIntReader(rawReader)
      val fold   = fold0
      var acc    = carry
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            fold match {
              case nested: SyncLinearFlatMapIntLongFold[_] =>
                return read.flatMap { value =>
                  if (value == Long.MinValue) Async.succeed(success(acc))
                  else
                    nested
                      .asInstanceOf[SyncLinearFlatMapIntLongFold[E]]
                      .sequence(this, acc, value.toInt)
                }
              case _ =>
                return finish(
                  read.flatMap { value =>
                    if (value == Long.MinValue) Async.succeed(acc)
                    else
                      StreamError
                        .callbackAsync(fold(acc, value.toInt))
                        .flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold))
                  }
                )
            }
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(success(acc))

        fold match {
          case nested: SyncLinearFlatMapIntLongFold[_] =>
            val direct = nested
              .asInstanceOf[SyncLinearFlatMapIntLongFold[E]]
              .sequenceReady(this.asInstanceOf[AsyncSourceSequenceIntLongParent[E]], acc, value.toInt)
            if (direct.asInstanceOf[AnyRef] ne null) return direct
          case _ =>
            val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
            Async.stepKind(reduced) match {
              case 1 => return failedStep(reduced)
              case 2 => return finish(reduced.flatMap(next => Sink.foldNativeAsyncIntLong(reader, next, fold)))
              case _ => acc = Async.stepLong(reduced)
            }
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldNativeAsyncIntLong(reader, acc, fold),
          () => ()
        )
      )
    }

    final def continueAfterNestedChild(next: Long): Async[Either[E, Long]] = {
      carry = next
      useResource(currentResource)
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(success)

    private def success(value: Long): Either[E, Long] =
      if (next eq null) rightLong[E](value)
      else {
        carry = value
        RightLongZero.asInstanceOf[Either[E, Long]]
      }
  }

  private final class AsyncSourceSequenceIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    next: AnyRef,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceSequenceIntLongUse[E](acquire, next, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncSourceSequenceIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    next: AnyRef,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncSourceSequenceIntLongUse[E](acquire, next, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private trait AsyncSourceSequenceIntLongParent[E] {
    def continueAfterNestedChild(next: Long): Async[Either[E, Long]]
  }

  private class AsyncReaderUse[A, Z](
    acquire: () => Async[Reader[A]],
    protected val callback: Reader.AsyncReader[A] => Async[Z]
  ) extends Async.BracketAsyncPollable[Reader.AsyncReader[A], Z](releaseAfterUse = true) {
    protected def acquireResource(): Async[Reader.AsyncReader[A]] =
      evaluateAcquisition().map {
        case reader: Reader.SyncReader[A @unchecked]  => reader.toAsync
        case reader: Reader.AsyncReader[A @unchecked] => reader
      }

    protected def evaluateAcquisition(): Async[Reader[A]] = acquire()

    protected def releaseResource(reader: Reader.AsyncReader[A]): Async[Unit] =
      reader.close().catchAll { closeFailure =>
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(reader: Reader.AsyncReader[A]): Async[Z] = {
      val effect = Sink.readerCallbackAsync(reader)(callback)
      effect.catchAll(failUse)
    }

    override protected def useFailed(cause: Throwable): Unit = useFailure = cause

    private def failUse(cause: Throwable): Async[Z] = {
      useFailure = cause
      cause match {
        case error: StreamError if error.isTrusted => Async.failTrusted(error)
        case _                                     => Async.fail(cause)
      }
    }

    private var useFailure: Throwable = null
  }

  private abstract class AsyncFilteredIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    predicate0: AnyRef,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = predicate0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def evaluateFold(predicate: AnyRef, acc: Long, value: Int): Async[Long] = {
      val _ = predicate
      fold(acc, value)
    }

    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader    = toAsyncIntReader(rawReader)
      val predicate = initialUseContext.asInstanceOf[AnyRef]
      var acc       = zero
      var budget    = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else
                  StreamError.callbackAsync(evaluatePredicate(predicate, value.toInt)).flatMap { accepted =>
                    if (accepted)
                      StreamError
                        .callbackAsync(evaluateFold(predicate, acc, value.toInt))
                        .flatMap(next => continue(reader, predicate, next))
                    else continue(reader, predicate, acc)
                  }
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        val accepted = StreamError.callbackAsync(evaluatePredicate(predicate, value.toInt))
        Async.stepKind(accepted) match {
          case 1 => return failedStep(accepted)
          case 2 =>
            return finish(
              accepted.flatMap { keep =>
                if (keep)
                  StreamError
                    .callbackAsync(evaluateFold(predicate, acc, value.toInt))
                    .flatMap(next => continue(reader, predicate, next))
                else continue(reader, predicate, acc)
              }
            )
          case _ =>
        }

        if (Async.stepBoolean(accepted)) {
          val reduced = StreamError.callbackAsync(evaluateFold(predicate, acc, value.toInt))
          Async.stepKind(reduced) match {
            case 1 => return failedStep(reduced)
            case 2 =>
              return finish(
                reduced.flatMap(next => continue(reader, predicate, next))
              )
            case _ => acc = Async.stepLong(reduced)
          }
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, predicate, acc),
          () => ()
        )
      )
    }

    private def continue(reader: Reader.AsyncReader[Int], predicate: AnyRef, acc: Long): Async[Long] =
      Sink.foldFilteredNativeAsyncIntLong(
        reader,
        value => evaluatePredicate(predicate, value),
        acc,
        (current, value) => evaluateFold(predicate, current, value)
      )

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(value => rightLong[E](value))
  }

  private final class AsyncFilteredIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Async[Boolean],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]           =
      predicate.asInstanceOf[Int => Async[Boolean]](value)
  }

  private final class AsyncFilteredIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Async[Boolean],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]           =
      predicate.asInstanceOf[Int => Async[Boolean]](value)
  }

  private final class SyncFilteredIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Boolean,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]           =
      Async.succeed(predicate.asInstanceOf[Int => Boolean](value))
  }

  private final class SyncFilteredIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Boolean,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]           =
      Async.succeed(predicate.asInstanceOf[Int => Boolean](value))
  }

  private final class SyncFilteredMappedIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Boolean,
    map: Int => Int,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                     = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]   = acquire()
    override protected def evaluateFold(predicate: AnyRef, acc: Long, value: Int): Async[Long] = fold(acc, map(value))
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]             =
      Async.succeed(predicate.asInstanceOf[Int => Boolean](value))
  }

  private final class SyncFilteredMappedIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    predicate: Int => Boolean,
    map: Int => Int,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, predicate, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]   = acquire()
    override protected def evaluateFold(predicate: AnyRef, acc: Long, value: Int): Async[Long] = fold(acc, map(value))
    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean]             =
      Async.succeed(predicate.asInstanceOf[Int => Boolean](value))
  }

  private abstract class AsyncMappedIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    map0: Int => Async[Int],
    zero: Long
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = map0
      ) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]
    protected def evaluateFold(acc: Long, value: Int): Async[Long]
    protected def foldContinuation: (Long, Int) => Async[Long]

    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] = {
      val close =
        try
          reader match {
            case sync: Reader.SyncReader[Int @unchecked]   => Async.succeed(sync.close())
            case async: Reader.AsyncReader[Int @unchecked] => async.close()
          }
        catch { case cause: Throwable => Async.fail(cause) }
      close.catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }
    }

    protected def useResource(reader: Reader[Int]): Async[Either[E, Long]] = reader match {
      case sync: Reader.SyncReader[Int @unchecked]   => useSyncResource(sync)
      case async: Reader.AsyncReader[Int @unchecked] => useAsyncResource(async)
    }

    private def useAsyncResource(reader: Reader.AsyncReader[Int]): Async[Either[E, Long]] = {
      val map    = initialUseContext.asInstanceOf[Int => Async[Int]]
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return new AsyncMappedIntLongResume[E](
              reader,
              map,
              foldContinuation,
              acc,
              read.asInstanceOf[Pollable[Long]],
              -1
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        val mapped =
          try map(value.toInt)
          catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(mapped) match {
          case 1 => return callbackFailedStep(mapped)
          case 2 =>
            return new AsyncMappedIntLongResume[E](
              reader,
              map,
              foldContinuation,
              acc,
              mapped.asInstanceOf[Pollable[Int]],
              0
            )
          case _ =>
        }

        val reduced =
          try evaluateFold(acc, Async.stepInt(mapped))
          catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(reduced) match {
          case 1 => return callbackFailedStep(reduced)
          case 2 =>
            return new AsyncMappedIntLongResume[E](
              reader,
              map,
              foldContinuation,
              acc,
              reduced.asInstanceOf[Pollable[Long]],
              1
            )
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      Async.rescheduleKnown(
        new MappedNativeAsyncIntEitherLongFold[E](reader, map, acc, foldContinuation),
        () => ()
      )
    }

    private def useSyncResource(reader: Reader.SyncReader[Int]): Async[Either[E, Long]] = {
      val map    = initialUseContext.asInstanceOf[Int => Async[Int]]
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val value =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        val mapped =
          try map(value.toInt)
          catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(mapped) match {
          case 1 => return callbackFailedStep(mapped)
          case 2 =>
            return new SyncMappedIntLongResume[E](
              reader,
              map,
              foldContinuation,
              acc,
              mapped.asInstanceOf[Pollable[Int]],
              0
            )
          case _ =>
        }

        val reduced =
          try evaluateFold(acc, Async.stepInt(mapped))
          catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(reduced) match {
          case 1 => return callbackFailedStep(reduced)
          case 2 =>
            return new SyncMappedIntLongResume[E](
              reader,
              map,
              foldContinuation,
              acc,
              reduced.asInstanceOf[Pollable[Long]],
              1
            )
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          Sink.foldMappedSyncAsyncIntLong(reader, map, acc, foldContinuation),
          () => ()
        )
      )
    }

    private def callbackFailedStep[A](effect: Async[A]): Async[Either[E, Long]] =
      Async.fail(StreamError.callbackFailure(Async.stepCause(effect)))

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(value => rightLong[E](value))
  }

  private final class AsyncMappedIntLongResume[E](
    reader: Reader.AsyncReader[Int],
    map: Int => Async[Int],
    fold: (Long, Int) => Async[Long],
    private var acc: Long,
    initial: Pollable[_],
    private var stage: Int
  ) extends Async.CancellationPropagatingPollable[Either[E, Long]](initial) {
    private var child: Pollable[Any] = initial.asInstanceOf[Pollable[Any]]

    def poll(onComplete: Runnable): Async[Either[E, Long]] = {
      while (true) {
        val current = child
        val result  =
          try pollChild(current, onComplete)
          catch { case failure: Throwable => return fail(failure, callback = stage >= 0 && stage != 2) }
        Async.stepKind(result) match {
          case 1 =>
            return fail(Async.stepCause(result), callback = stage >= 0 && stage != 2, Async.stepTrusted(result))
          case 2 =>
            val next = result.asInstanceOf[Pollable[Any]]
            child = next
            if (next.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef]) return this
          case _ =>
            clearActive()
            if (stage == -1) {
              val value = Async.stepLong(result.asInstanceOf[Async[Long]])
              if (value == Long.MinValue) return complete()
              val next = continueMap(value.toInt)
              if (next.asInstanceOf[AnyRef] ne null) return next
            } else if (stage == 0) {
              val next = continueFold(Async.stepInt(result.asInstanceOf[Async[Int]]))
              if (next.asInstanceOf[AnyRef] ne null) return next
            } else if (stage == 1) {
              acc = Async.stepLong(result.asInstanceOf[Async[Long]])
              val next = continueStream()
              if (next.asInstanceOf[AnyRef] ne null) return next
            } else return Async.succeed(rightLong[E](Async.stepLong(result.asInstanceOf[Async[Long]])))
        }
      }
      this
    }

    private def complete(): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      val effect = Async.succeed(rightLong[E](acc))
      if (finishContinuation(ownership, effect)) this else effect
    }

    private def continueFold(value: Int): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      val effect =
        try fold(acc, value)
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      if (finishContinuation(ownership, effect)) return this
      Async.stepKind(effect) match {
        case 1 => fail(Async.stepCause(effect), callback = true, Async.stepTrusted(effect))
        case 2 =>
          child = effect.asInstanceOf[Pollable[Any]]
          stage = 1
          null
        case _ =>
          acc = Async.stepLong(effect)
          continueStream()
      }
    }

    private def continueMap(value: Int): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      val effect =
        try map(value)
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      if (finishContinuation(ownership, effect)) return this
      Async.stepKind(effect) match {
        case 1 => fail(Async.stepCause(effect), callback = true, Async.stepTrusted(effect))
        case 2 =>
          child = effect.asInstanceOf[Pollable[Any]]
          stage = 0
          null
        case _ => continueFold(Async.stepInt(effect))
      }
    }

    private def continueStream(): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted =>
              val effect = Async.failTrusted(error)
              return if (finishContinuation(ownership, effect)) this else effect
            case failure: Throwable =>
              val effect = Async.fail(failure)
              return if (finishContinuation(ownership, effect)) this else effect
          }
        Async.stepKind(read) match {
          case 1 =>
            val effect = fail(Async.stepCause(read), callback = false, Async.stepTrusted(read))
            return if (finishContinuation(ownership, effect)) this else effect
          case 2 =>
            child = read.asInstanceOf[Pollable[Any]]
            stage = -1
            if (finishContinuation(ownership, child)) return this
            return null
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) {
          val effect = Async.succeed(rightLong[E](acc))
          return if (finishContinuation(ownership, effect)) this else effect
        }

        val mapped =
          try map(value.toInt)
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(mapped) match {
          case 1 =>
            val effect = fail(Async.stepCause(mapped), callback = true, Async.stepTrusted(mapped))
            return if (finishContinuation(ownership, effect)) this else effect
          case 2 =>
            child = mapped.asInstanceOf[Pollable[Any]]
            stage = 0
            if (finishContinuation(ownership, child)) return this
            return null
          case _ =>
        }

        val reduced =
          try fold(acc, Async.stepInt(mapped))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(reduced) match {
          case 1 =>
            val effect = fail(Async.stepCause(reduced), callback = true, Async.stepTrusted(reduced))
            return if (finishContinuation(ownership, effect)) this else effect
          case 2 =>
            child = reduced.asInstanceOf[Pollable[Any]]
            stage = 1
            if (finishContinuation(ownership, child)) return this
            return null
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }

      val effect = Async.rescheduleKnown(Sink.foldMappedNativeAsyncIntLong(reader, map, acc, fold), () => ())
      if (finishContinuation(ownership, effect)) this
      else {
        child = effect.asInstanceOf[Pollable[Any]]
        stage = 2
        null
      }
    }

    private def fail(
      failure: Throwable,
      callback: Boolean,
      trusted: Boolean = false
    ): Async[Either[E, Long]] = {
      val cause = if (callback) StreamError.callbackFailure(failure) else failure
      if (!callback && trusted) Async.failTrusted(cause) else Async.fail(cause)
    }
  }

  private final class AsyncMappedIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    map: Int => Async[Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncMappedIntLongUse[E](acquire, map, zero) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluateFold(acc: Long, value: Int): Async[Long]                           = fold(acc, value)
    protected def foldContinuation: (Long, Int) => Async[Long]                               = fold
  }

  private final class AsyncMappedIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    map: Int => Async[Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncMappedIntLongUse[E](acquire, map, zero) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluateFold(acc: Long, value: Int): Async[Long]                           = fold(acc, value)
    protected def foldContinuation: (Long, Int) => Async[Long]                               = fold
  }

  private final class AsyncMappedTakeWhileIntLongUse[E](
    acquire: () => Async[Reader[Int]],
    map: Int => Async[Int],
    predicate: Int => Boolean,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncMappedIntLongUse[E](acquire, map, zero) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluateFold(acc: Long, value: Int): Async[Long]                           =
      if (predicate(value)) fold(acc, value)
      else Async.fail(new TakeWhileDone(acc))
    protected def foldContinuation: (Long, Int) => Async[Long] = evaluateFold

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1)
        Async.stepCause(result) match {
          case done: TakeWhileDone if done.getSuppressed.isEmpty => Async.succeed(rightLong[E](done.result))
          case done: TakeWhileDone                               => Async.fail(done.getSuppressed.head)
          case _                                                 => super.completeResult(result)
        }
      else super.completeResult(result)
  }

  private final class TakeWhileDone(val result: Long) extends Throwable(null, null, true, false)

  private final class AsyncMappedMappedIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    map: Int => Async[Int],
    downstream: Int => Int,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncMappedIntLongUse[E](acquire, map, zero) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluateFold(acc: Long, value: Int): Async[Long]                           = fold(acc, downstream(value))
    protected def foldContinuation: (Long, Int) => Async[Long]                               = evaluateFold
  }

  private final class AsyncMappedMappedIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    map: Int => Async[Int],
    downstream: Int => Int,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncMappedIntLongUse[E](acquire, map, zero) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
    protected def evaluateFold(acc: Long, value: Int): Async[Long]                           = fold(acc, downstream(value))
    protected def foldContinuation: (Long, Int) => Async[Long]                               = evaluateFold
  }

  private final class IntLinearContext(
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long]
  ) {
    var transformed: Int = 0

    def evaluate(input: Int): Boolean =
      if (filterMask == 0L && (filterWords eq null)) {
        var value = input
        var index = 0
        try {
          while (index < operations.length) {
            value = operations(index).asInstanceOf[Int => Int](value)
            index += 1
          }
        } catch { case error: StreamError => throw StreamError.untrusted(error) }
        transformed = value
        true
      } else if (operations.length <= 8) evaluateFilteredBlock(input)
      else evaluateFilteredLong(input)

    private def evaluateFilteredLong(input: Int): Boolean = {
      var value = input
      try {
        if (operations.length > 0) {
          if ((filterMask & 1L) != 0L) {
            if (!operations(0).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(0).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 1) {
          if ((filterMask & 2L) != 0L) {
            if (!operations(1).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(1).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 2) {
          if ((filterMask & 4L) != 0L) {
            if (!operations(2).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(2).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 3) {
          if ((filterMask & 8L) != 0L) {
            if (!operations(3).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(3).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 4) {
          if ((filterMask & 16L) != 0L) {
            if (!operations(4).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(4).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 5) {
          if ((filterMask & 32L) != 0L) {
            if (!operations(5).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(5).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 6) {
          if ((filterMask & 64L) != 0L) {
            if (!operations(6).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(6).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 7) {
          if ((filterMask & 128L) != 0L) {
            if (!operations(7).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(7).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 8) {
          if ((filterMask & 256L) != 0L) {
            if (!operations(8).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(8).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 9) {
          if ((filterMask & 512L) != 0L) {
            if (!operations(9).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(9).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 10) {
          if ((filterMask & 1024L) != 0L) {
            if (!operations(10).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(10).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 11) {
          if ((filterMask & 2048L) != 0L) {
            if (!operations(11).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(11).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 12) {
          if ((filterMask & 4096L) != 0L) {
            if (!operations(12).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(12).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 13) {
          if ((filterMask & 8192L) != 0L) {
            if (!operations(13).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(13).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 14) {
          if ((filterMask & 16384L) != 0L) {
            if (!operations(14).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(14).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 15) {
          if ((filterMask & 32768L) != 0L) {
            if (!operations(15).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(15).asInstanceOf[Int => Int](value)
        }
        var index = 16
        while (index < operations.length) {
          if (isFilter(index)) {
            if (!operations(index).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(index).asInstanceOf[Int => Int](value)
          index += 1
        }
        transformed = value
        true
      } catch { case error: StreamError => throw StreamError.untrusted(error) }
    }

    private def evaluateFilteredBlock(input: Int): Boolean = {
      var value = input
      try {
        if (operations.length > 0) {
          if ((filterMask & 1L) != 0L) {
            if (!operations(0).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(0).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 1) {
          if ((filterMask & 2L) != 0L) {
            if (!operations(1).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(1).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 2) {
          if ((filterMask & 4L) != 0L) {
            if (!operations(2).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(2).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 3) {
          if ((filterMask & 8L) != 0L) {
            if (!operations(3).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(3).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 4) {
          if ((filterMask & 16L) != 0L) {
            if (!operations(4).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(4).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 5) {
          if ((filterMask & 32L) != 0L) {
            if (!operations(5).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(5).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 6) {
          if ((filterMask & 64L) != 0L) {
            if (!operations(6).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(6).asInstanceOf[Int => Int](value)
        }
        if (operations.length > 7) {
          if ((filterMask & 128L) != 0L) {
            if (!operations(7).asInstanceOf[Int => Boolean](value)) return false
          } else value = operations(7).asInstanceOf[Int => Int](value)
        }
        transformed = value
        true
      } catch { case error: StreamError => throw StreamError.untrusted(error) }
    }

    private def isFilter(index: Int): Boolean =
      if (filterWords eq null) (filterMask & (1L << index)) != 0L
      else (filterWords(index >>> 6) & (1L << index)) != 0L
  }

  private abstract class SyncLinearIntLongUse[E](
    acquire: () => Async[Reader[Int]],
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFilteredIntLongUse[E](acquire, operations, zero, fold) {
    override protected def evaluateFold(predicate: AnyRef, acc: Long, value: Int): Async[Long] =
      fold(acc, transformed)

    protected def evaluatePredicate(predicate: AnyRef, value: Int): Async[Boolean] = {
      val operations = predicate.asInstanceOf[Array[AnyRef]]
      var current    = value
      var index      = 0
      while (index < operations.length) {
        try {
          val isFilter =
            if (filterWords eq null) (filterMask & (1L << index)) != 0L
            else (filterWords(index >>> 6) & (1L << index)) != 0L
          if (isFilter) {
            if (!operations(index).asInstanceOf[Int => Boolean](current)) return Async.succeed(false)
          } else current = operations(index).asInstanceOf[Int => Int](current)
        } catch { case error: StreamError => throw StreamError.untrusted(error) }
        index += 1
      }
      transformed = current
      Async.succeed(true)
    }

    private var transformed: Int = 0
  }

  private final class SyncLinearIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends SyncLinearIntLongUse[E](acquire, operations, filterMask, filterWords, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class SyncLinearIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends SyncLinearIntLongUse[E](acquire, operations, filterMask, filterWords, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class SyncLinearFlatMapIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    expand: Int => Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFlatMapIntLongUse[E](acquire, expand, zero, fold) {
    private val context = new IntLinearContext(operations, filterMask, filterWords)

    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()

    override protected def expandValue(value: Int): Stream[E, Int] =
      if (context.evaluate(value)) expand(context.transformed)
      else null
  }

  private final class SyncLinearFlatMapIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    expand: Int => Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncFlatMapIntLongUse[E](acquire, expand, zero, fold) {
    private val context = new IntLinearContext(operations, filterMask, filterWords)

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()

    override protected def expandValue(value: Int): Stream[E, Int] =
      if (context.evaluate(value)) expand(context.transformed)
      else null
  }

  private final class SyncLinearFlatMapIntLongFold[E](
    operations: Array[AnyRef],
    filterMask: Long,
    filterWords: Array[Long],
    expand: Int => Stream[E, Int],
    fold: (Long, Int) => Async[Long]
  ) extends ((Long, Int) => Async[Long]) {
    private val context = new IntLinearContext(operations, filterMask, filterWords)

    def apply(acc: Long, value: Int): Async[Long] = {
      val child = expandValue(value)
      if (child eq null) Async.succeed(acc)
      else
        child.runFoldAsync(acc)(fold).map {
          case Left(error) => throw StreamError.source(error)
          case Right(next) => next
        }
    }

    def sequence(
      parent: AsyncSourceSequenceIntLongParent[E],
      acc: Long,
      value: Int
    ): Async[Either[E, Long]] = {
      val child = expandValue(value)
      if (child eq null) parent.continueAfterNestedChild(acc)
      else sequenceExpanded(parent, acc, child)
    }

    def sequenceReady(
      parent: AsyncSourceSequenceIntLongParent[E],
      acc: Long,
      value: Int
    ): Async[Either[E, Long]] = {
      val child = expandValue(value)
      if (child eq null) null
      else sequenceExpanded(parent, acc, child)
    }

    private def expandValue(value: Int): Stream[E, Int] =
      if (context.evaluate(value)) expand(context.transformed)
      else null

    private def sequenceExpanded(
      parent: AsyncSourceSequenceIntLongParent[E],
      acc: Long,
      child: Stream[E, Int]
    ): Async[Either[E, Long]] = child match {
      case source: AsyncSource[_, _] if source.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        if (source.protectAcquisition)
          new AsyncSourceSequenceIntLongUseProtected[E](acquire, parent, acc, fold)
        else new AsyncSourceSequenceIntLongUseUnprotected[E](acquire, parent, acc, fold)
      case singleton: SingletonInt =>
        StreamError.callbackAsync(fold(acc, singleton.value)).flatMap(parent.continueAfterNestedChild)
      case _ =>
        child.runFoldAsync(acc)(fold).flatMap {
          case Left(error) => Async.failTrusted(StreamError.source(error))
          case Right(next) => parent.continueAfterNestedChild(next)
        }
    }
  }

  private def useLinearIntFlatMapLongDirect[E](
    stream: Stream[E, Int],
    expand: Int => Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Either[E, Long]] = {
    var current: Stream[_, _]                      = stream
    var source: AsyncSource[_, _]                  = null
    var protectedAcquire: () => Async[Reader[Int]] = null
    var flatMapped: FlatMapped[_, _, _, _]         = null
    var count                                      = 0
    var collecting                                 = true
    while (collecting) current match {
      case mapped: Mapped[_, _, _]
          if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
            (mapped.jtB.jvmType eq JvmType.Int) =>
        count += 1
        current = mapped.self
      case filtered: Filtered[_, _] if filtered.inRepresentation.stableJvmType.contains(JvmType.Int) =>
        count += 1
        current = filtered.self
      case mapped: ProtectedIntAsyncMappedInt[_] =>
        count += 1
        protectedAcquire = mapped.acquireRaw
        collecting = false
      case async: AsyncSource[_, _] if async.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        source = async
        collecting = false
      case nested: FlatMapped[_, _, _, _]
          if nested.inRepresentation.stableJvmType.contains(JvmType.Int) &&
            (nested.jtB.jvmType eq JvmType.Int) =>
        flatMapped = nested
        collecting = false
      case _ => return null
    }

    var filterMask  = 0L
    val filterWords =
      if (count <= 64) null
      else new Array[Long]((count + 63) >>> 6)
    val operations = new Array[AnyRef](count)
    current = stream
    var index = count - 1
    while (index >= 0) {
      current match {
        case mapped: Mapped[_, _, _] =>
          operations(index) = mapped.f.asInstanceOf[AnyRef]
          current = mapped.self
        case filtered: Filtered[_, _] =>
          if (index < 64) filterMask |= 1L << index
          if (filterWords ne null) filterWords(index >>> 6) |= 1L << index
          operations(index) = filtered.pred.asInstanceOf[AnyRef]
          current = filtered.self
        case mapped: ProtectedIntAsyncMappedInt[_] =>
          operations(index) = mapped.f.asInstanceOf[AnyRef]
      }
      index -= 1
    }

    if ((source ne null) || (protectedAcquire ne null)) {
      val acquire =
        if (protectedAcquire ne null) protectedAcquire
        else source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
      if ((protectedAcquire ne null) || source.protectAcquisition)
        new SyncLinearFlatMapIntLongUseProtected[E](
          acquire,
          operations,
          filterMask,
          filterWords,
          expand,
          zero,
          fold
        )
      else
        new SyncLinearFlatMapIntLongUseUnprotected[E](
          acquire,
          operations,
          filterMask,
          filterWords,
          expand,
          zero,
          fold
        )
    } else
      flatMapped
        .asInstanceOf[FlatMapped[Any, Any, Int, Int]]
        .runFoldLongDirect(
          zero,
          new SyncLinearFlatMapIntLongFold[E](operations, filterMask, filterWords, expand, fold)
        )
        .asInstanceOf[Async[Either[E, Long]]]
  }

  private def useLinearIntFoldLongDirect[E](
    stream: Stream[E, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ): Async[Either[E, Long]] = {
    var current: Stream[_, _]              = stream
    var source: AsyncSource[_, _]          = null
    var asyncMapped: AsyncMapped[_, _, _]  = null
    var flatMapped: FlatMapped[_, _, _, _] = null
    var count                              = 0
    var collecting                         = true
    while (collecting) current match {
      case mapped: Mapped[_, _, _]
          if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
            (mapped.jtB.jvmType eq JvmType.Int) =>
        count += 1
        current = mapped.self
      case filtered: Filtered[_, _] if filtered.inRepresentation.stableJvmType.contains(JvmType.Int) =>
        count += 1
        current = filtered.self
      case async: AsyncSource[_, _] if async.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        source = async
        collecting = false
      case mapped: AsyncMapped[_, _, _] if mapped.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        asyncMapped = mapped
        collecting = false
      case nested: FlatMapped[_, _, _, _] if nested.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        flatMapped = nested
        collecting = false
      case _ => return null
    }

    var filterMask  = 0L
    val filterWords =
      if (count <= 64) null
      else new Array[Long]((count + 63) >>> 6)
    val operations = new Array[AnyRef](count)
    current = stream
    var index = count - 1
    while (index >= 0) {
      current match {
        case mapped: Mapped[_, _, _] =>
          operations(index) = mapped.f.asInstanceOf[AnyRef]
          current = mapped.self
        case filtered: Filtered[_, _] =>
          if (index < 64) filterMask |= 1L << index
          if (filterWords ne null) filterWords(index >>> 6) |= 1L << index
          operations(index) = filtered.pred.asInstanceOf[AnyRef]
          current = filtered.self
      }
      index -= 1
    }
    if (source ne null) {
      val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
      if (source.protectAcquisition)
        new SyncLinearIntLongUseProtected[E](acquire, operations, filterMask, filterWords, zero, fold)
      else new SyncLinearIntLongUseUnprotected[E](acquire, operations, filterMask, filterWords, zero, fold)
    } else if (asyncMapped ne null) {
      val context = new IntLinearContext(operations, filterMask, filterWords)
      val reduce  = (acc: Long, input: Int) => {
        if (context.evaluate(input)) fold(acc, context.transformed)
        else Async.succeed(acc)
      }
      asyncMapped
        .asInstanceOf[AsyncMapped[E, Int, Int]]
        .runFoldLongDirect(zero, reduce)
    } else
      flatMapped
        .asInstanceOf[FlatMapped[Any, Any, Int, Int]]
        .runFoldLongDirect(
          zero, {
            val context = new IntLinearContext(operations, filterMask, filterWords)
            (acc: Long, input: Int) =>
              if (context.evaluate(input)) fold(acc, context.transformed)
              else Async.succeed(acc)
          }
        )
        .asInstanceOf[Async[Either[E, Long]]]
  }

  private final class AsyncSinkUse[E, A, Z](stream: Stream[E, A], sink: Sink[E, A, Z])
      extends Async.BracketAsyncPollable[Reader[A], Z](releaseAfterUse = true) {
    protected def acquireResource(): Async[Reader[A]] = stream.acquireReader

    protected def releaseResource(reader: Reader[A]): Async[Unit] = {
      val close =
        try
          reader match {
            case sync: Reader.SyncReader[A @unchecked]   => Async.succeed(sync.close())
            case async: Reader.AsyncReader[A @unchecked] => async.close()
          }
        catch { case cause: Throwable => Async.fail(cause) }
      close.catchAll { closeFailure =>
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }
    }

    override protected def useFailed(cause: Throwable): Unit = useFailure = cause

    protected def useResource(reader: Reader[A]): Async[Z] =
      try
        reader match {
          case sync: Reader.SyncReader[A @unchecked]   => sink.drainAsync(sync)
          case async: Reader.AsyncReader[A @unchecked] => sink.drain(async)
        }
      catch {
        case cause: Throwable =>
          useFailure = cause
          cause match {
            case error: StreamError if error.isTrusted => Async.failTrusted(error)
            case _                                     => Async.fail(cause)
          }
      }

    private var useFailure: Throwable = null
  }

  private abstract class AsyncTakeDropIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    dropCount: Long,
    takeCount: Long,
    zero: Long,
    fold0: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = fold0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader        = toAsyncIntReader(rawReader)
      val fold          = initialUseContext.asInstanceOf[(Long, Int) => Async[Long]]
      var acc           = zero
      var skipRemaining = math.max(0L, dropCount)
      var takeRemaining = math.max(0L, takeCount)
      if (takeRemaining <= 0L) return Async.succeed(rightLong[E](acc))

      if (skipRemaining > 0L) {
        val configured =
          try reader.setSkip(skipRemaining)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(configured) match {
          case 1 => return failedStep(configured)
          case 2 =>
            return finish(
              configured.flatMap { accepted =>
                continue(reader, if (accepted) 0L else skipRemaining, takeRemaining, acc, fold)
              }
            )
          case _ => if (Async.stepBoolean(configured)) skipRemaining = 0L
        }
      }

      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else if (skipRemaining > 0L)
                  continue(reader, skipRemaining - 1L, takeRemaining, acc, fold)
                else {
                  val remaining = takeRemaining - 1L
                  StreamError
                    .callbackAsync(fold(acc, value.toInt))
                    .flatMap(next => continue(reader, 0L, remaining, next, fold))
                }
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        if (skipRemaining > 0L) skipRemaining -= 1L
        else {
          takeRemaining -= 1L
          val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
          Async.stepKind(reduced) match {
            case 1 => return failedStep(reduced)
            case 2 =>
              return finish(
                reduced.flatMap(next => continue(reader, 0L, takeRemaining, next, fold))
              )
            case _ => acc = Async.stepLong(reduced)
          }
        }
        budget -= 1
        if (skipRemaining <= 0L && takeRemaining <= 0L) return Async.succeed(rightLong[E](acc))
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, skipRemaining, takeRemaining, acc, fold),
          () => ()
        )
      )
    }

    private def continue(
      reader: Reader.AsyncReader[Int],
      skipRemaining: Long,
      takeRemaining: Long,
      acc: Long,
      fold: (Long, Int) => Async[Long]
    ): Async[Long] =
      Sink.foldTakeDropNativeAsyncIntLong(reader, skipRemaining, takeRemaining, acc, fold)

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(value => rightLong[E](value))
  }

  private final class AsyncTakeDropIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    dropCount: Long,
    takeCount: Long,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakeDropIntLongUse[E](acquire, dropCount, takeCount, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncTakeDropIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    dropCount: Long,
    takeCount: Long,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakeDropIntLongUse[E](acquire, dropCount, takeCount, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncTakenIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    limit: Long,
    zero: Long,
    fold0: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = fold0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader = toAsyncIntReader(rawReader)
      val fold   = initialUseContext.asInstanceOf[(Long, Int) => Async[Long]]
      var acc    = zero
      var left   = math.max(0L, limit)
      var budget = 1024
      while (budget > 0 && left > 0L) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue) Async.succeed(acc)
                else {
                  val remaining = left - 1L
                  StreamError
                    .callbackAsync(fold(acc, value.toInt))
                    .flatMap(next => Sink.foldTakenNativeAsyncIntLong(reader, remaining, next, fold))
                }
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue) return Async.succeed(rightLong[E](acc))

        left -= 1L
        val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 =>
            return finish(
              reduced.flatMap(next => Sink.foldTakenNativeAsyncIntLong(reader, left, next, fold))
            )
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      if (left <= 0L) Async.succeed(rightLong[E](acc))
      else
        finish(
          Async.rescheduleKnown(
            Sink.foldTakenNativeAsyncIntLong(reader, left, acc, fold),
            () => ()
          )
        )
    }

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(value => rightLong[E](value))
  }

  private final class AsyncTakenIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakenIntLongUse[E](acquire, limit, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncTakenIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    limit: Long,
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakenIntLongUse[E](acquire, limit, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private abstract class AsyncTakenWhileIntLongUse[E](
    acquire0: () => Async[Reader[Int]],
    node0: TakenWhile[_, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
        releaseAfterUse = true,
        acquisitionContext = acquire0,
        useContext = node0
      ) {
    protected def acquireResource(): Async[Reader[Int]] = {
      val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
      evaluateAcquisition(acquire)
    }

    override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
      if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
        Async.stepCause(result) match {
          case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
          case _                                          => result
        }
      else result

    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

    protected def releaseResource(reader: Reader[Int]): Async[Unit] =
      releaseResource(reader, Async.succeed(RightLongZero.asInstanceOf[Either[E, Long]]))

    override protected def releaseResource(
      reader: Reader[Int],
      useResult: Async[Either[E, Long]]
    ): Async[Unit] =
      closeIntReader(reader).catchAll { closeFailure =>
        val useFailure =
          if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
          else null
        if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
        else {
          StreamError.attachCleanupReplay(useFailure, closeFailure)
          Async.succeed(())
        }
      }

    protected def useResource(rawReader: Reader[Int]): Async[Either[E, Long]] = {
      val reader = toAsyncIntReader(rawReader)
      val node   = initialUseContext.asInstanceOf[TakenWhile[_, Int]]
      var acc    = zero
      var budget = 1024
      while (budget > 0) {
        val read =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted => return Async.failTrusted(error)
            case failure: Throwable                    => return Async.fail(failure)
          }
        Async.stepKind(read) match {
          case 1 => return failedStep(read)
          case 2 =>
            return finish(
              read.flatMap { value =>
                if (value == Long.MinValue || !node.test(value.toInt)) Async.succeed(acc)
                else
                  StreamError
                    .callbackAsync(fold(acc, value.toInt))
                    .flatMap(next => continue(reader, node, next))
              }
            )
          case _ =>
        }
        val value = Async.stepLong(read)
        if (value == Long.MinValue || !node.test(value.toInt))
          return Async.succeed(rightLong[E](acc))

        val reduced = StreamError.callbackAsync(fold(acc, value.toInt))
        Async.stepKind(reduced) match {
          case 1 => return failedStep(reduced)
          case 2 => return finish(reduced.flatMap(next => continue(reader, node, next)))
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }
      finish(
        Async.rescheduleKnown(
          continue(reader, node, acc),
          () => ()
        )
      )
    }

    private def continue(
      reader: Reader.AsyncReader[Int],
      node: TakenWhile[_, Int],
      acc: Long
    ): Async[Long] =
      Sink.foldTakenWhileNativeAsyncIntLong(
        reader,
        value => Async.succeed(node.test(value)),
        acc,
        fold
      )

    private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
      val cause = Async.stepCause(effect)
      if (Async.stepTrusted(effect)) Async.failTrusted(cause)
      else Async.fail(cause)
    }

    private def finish(effect: Async[Long]): Async[Either[E, Long]] =
      effect.map(value => rightLong[E](value))
  }

  private final class AsyncTakenWhileIntLongUseProtected[E](
    acquire: () => Async[Reader[Int]],
    node: TakenWhile[_, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakenWhileIntLongUse[E](acquire, node, zero, fold) {
    override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncTakenWhileIntLongUseUnprotected[E](
    acquire: () => Async[Reader[Int]],
    node: TakenWhile[_, Int],
    zero: Long,
    fold: (Long, Int) => Async[Long]
  ) extends AsyncTakenWhileIntLongUse[E](acquire, node, zero, fold) {
    protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  }

  private final class AsyncReaderUseProtected[A, Z](
    acquire: () => Async[Reader[A]],
    callback: Reader.AsyncReader[A] => Async[Z]
  ) extends AsyncReaderUse[A, Z](acquire, callback) {
    override protected def acquisitionFailure(cause: Throwable): Throwable = StreamError.callbackFailure(cause)
    override protected def evaluateAcquisition(): Async[Reader[A]]         = super.evaluateAcquisition()
  }

  private class AsyncTrustedReaderUse[A, Z](
    acquire: () => Async[Reader[A]],
    callback: Reader.AsyncReader[A] => Async[Z]
  ) extends AsyncReaderUse[A, Z](acquire, callback) {
    override protected def useResource(reader: Reader.AsyncReader[A]): Async[Z] =
      callback(reader)
  }

  private final class AsyncTrustedReaderUseProtected[A, Z](
    acquire: () => Async[Reader[A]],
    callback: Reader.AsyncReader[A] => Async[Z]
  ) extends AsyncTrustedReaderUse[A, Z](acquire, callback) {
    override protected def acquisitionFailure(cause: Throwable): Throwable = StreamError.callbackFailure(cause)
    override protected def evaluateAcquisition(): Async[Reader[A]]         = super.evaluateAcquisition()
  }

  /**
   * A stable async boundary whose initialization effect starts only when
   * pulled.
   */
  private[streams] abstract class AsyncSource[E, A] extends Stream[E, A] with (() => Async[Reader[A]]) {
    protected def representation: ElementRepresentation
    protected def protectedAcquisition: Boolean

    private[streams] def acquire(): Async[Reader[A]] =
      if (protectedAcquisition) StreamError.callbackAsync(this.apply()) else this.apply()

    private[streams] def acquisition: () => Async[Reader[A]] = this

    private def boundary(): Reader.AsyncReader[A] = {
      val outType = representation.stableJvmType.getOrElse(JvmType.AnyRef)
      Reader.closed.toAsync.concatAsyncWithJvmType(() => acquire(), outType)
    }

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = boundary()

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired

    override private[streams] def elementRepresentation: ElementRepresentation = representation

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() => boundary())
      null
    }

    private[streams] def protectAcquisition: Boolean = protectedAcquisition

    private[streams] def runCollectDirect(): Async[Either[E, Chunk[A]]] =
      if (representation.stableJvmType.contains(JvmType.Int)) {
        val acquire = this.asInstanceOf[() => Async[Reader[Int]]]
        val result  =
          if (protectedAcquisition) new AsyncSourceIntCollectUseProtected[E](acquire)
          else new AsyncSourceIntCollectUseUnprotected[E](acquire)
        result.asInstanceOf[Async[Either[E, Chunk[A]]]]
      } else null

    private[streams] def runDrainDirect(): Async[Either[E, Unit]] =
      if (representation.stableJvmType.contains(JvmType.Int)) {
        val acquire = this.asInstanceOf[() => Async[Reader[Int]]]
        if (protectedAcquisition) new AsyncSourceIntDrainUseProtected[E](acquire, null)
        else new AsyncSourceIntDrainUseUnprotected[E](acquire, null)
      } else null

    private[streams] def runFoldLeftLongMapAsyncDirect[Z](
      zero: Long,
      fold: (Long, Int) => Long,
      map: Long => Async[Z]
    ): Async[Either[E, Z]] = {
      val acquire = this.asInstanceOf[() => Async[Reader[Int]]]
      if (protectedAcquisition) new AsyncSourceIntSyncLongMapUseProtected[E, Z](acquire, zero, fold, map)
      else new AsyncSourceIntSyncLongMapUseUnprotected[E, Z](acquire, zero, fold, map)
    }

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] =
      if (representation.stableJvmType.contains(JvmType.Int)) {
        val acquire = this.asInstanceOf[() => Async[Reader[Int]]]
        val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (protectedAcquisition)
          new AsyncSourceIntLongUseProtected[E](acquire, zero, reduce)
        else new AsyncSourceIntLongUseUnprotected[E](acquire, zero, reduce)
      } else null

    private[streams] def runForeachDirect(foreach: A => Async[Unit]): Async[Either[E, Unit]] =
      if (representation.stableJvmType.contains(JvmType.Int)) {
        val acquire  = this.asInstanceOf[() => Async[Reader[Int]]]
        val callback = foreach.asInstanceOf[Int => Async[Unit]]
        if (protectedAcquisition) new AsyncSourceIntDrainUseProtected[E](acquire, callback)
        else new AsyncSourceIntDrainUseUnprotected[E](acquire, callback)
      } else null
  }

  private[streams] final class GenericAsyncSource[E, A](
    make: () => Async[Reader[A]],
    label: String,
    protected val representation: ElementRepresentation = ElementRepresentation.Boxed,
    protected val protectedAcquisition: Boolean = false
  ) extends AsyncSource[E, A] {
    def apply(): Async[Reader[A]] = make()
    def render: String            = label
  }

  private[streams] final class ProtectedAsyncSource[E, A](
    private val make: () => Async[Reader[A]],
    protected val representation: ElementRepresentation
  ) extends AsyncSource[E, A] {
    protected def protectedAcquisition: Boolean                 = true
    def apply(): Async[Reader[A]]                               = make()
    def render: String                                          = "Stream.fromReaderAsync(...)"
    private[streams] def rawAcquisition: () => Async[Reader[A]] = make
  }

  /**
   * A source-aware map node that stores the protected primitive source's raw
   * acquisition rather than retaining the temporary source wrapper.
   */
  private[streams] final class ProtectedAsyncMapped[E, A, B](
    acquireRaw: () => Async[Reader[A]],
    sourceRepresentation: ElementRepresentation,
    f: A => B,
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B] {
    private def mapped: Mapped[E, A, B] =
      new GenericMapped(new ProtectedAsyncSource[E, A](acquireRaw, sourceRepresentation), f, jtB)

    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    override def knownLength: Option[Long] = None
    def render: String                     = "Stream.fromReaderAsync(...).map(...)"

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] =
      mapped.compile(depth, bufferSize)
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[B] =
      mapped.compileBlocking(bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      mapped.compileInterpreter(pipeline)
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[B] => Z,
      onAsync: Reader.AsyncReader[B] => Z
    ): Z                                                                                     = mapped.foldReaderKind(onSync, onAsync)
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] =
      mapped.materializeAsync(pipeline)

    private[streams] def acquireDirect(): Async[Reader[B]] = mapped.acquireDirect()
    private[streams] def runFoldDoubleDirect(
      zero: Double,
      fold: (Double, B) => Async[Double]
    ): Async[Either[E, Double]] = mapped.runFoldDoubleDirect(zero, fold)
    private[streams] def runFoldFloatDirect(
      zero: Float,
      fold: (Float, B) => Async[Float]
    ): Async[Either[E, Float]] = mapped.runFoldFloatDirect(zero, fold)
    private[streams] def runFoldIntDirect(
      zero: Int,
      fold: (Int, B) => Async[Int]
    ): Async[Either[E, Int]] = mapped.runFoldIntDirect(zero, fold)
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]]                                                                               = mapped.runFoldLongDirect(zero, fold)
    override private[streams] def runFoldLongBlocking(zero: Long, fold: (Long, B) => Long): Either[E, Long] =
      mapped.runFoldLongBlocking(zero, fold)
  }

  /** Allocation-minimal Int-to-Int instance of the source-aware map shape. */
  private[streams] final class ProtectedIntAsyncMappedInt[E](
    private[streams] val acquireRaw: () => Async[Reader[Int]],
    private[streams] val f: Int => Int
  ) extends Stream[E, Int] {
    private def mapped: Mapped[E, Int, Int] =
      new IntMapped(
        new ProtectedAsyncSource[E, Int](acquireRaw, ElementRepresentation.Known(JvmType.Int)),
        f
      )

    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.Known(JvmType.Int)
    override def knownLength: Option[Long]                                                 = None
    def render: String                                                                     = "Stream.fromReaderAsync(...).map(...)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[Int]        = mapped.compile(depth, bufferSize)
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[Int] =
      mapped.compileBlocking(bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = mapped.compileInterpreter(pipeline)
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[Int] => Z,
      onAsync: Reader.AsyncReader[Int] => Z
    ): Z                                                                                     = mapped.foldReaderKind(onSync, onAsync)
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] =
      mapped.materializeAsync(pipeline)
    private[streams] def acquireDirect(): Async[Reader[Int]] = mapped.acquireDirect()
    private[streams] def runFoldDoubleDirect(
      zero: Double,
      fold: (Double, Int) => Async[Double]
    ): Async[Either[E, Double]] = mapped.runFoldDoubleDirect(zero, fold)
    private[streams] def runFoldFloatDirect(
      zero: Float,
      fold: (Float, Int) => Async[Float]
    ): Async[Either[E, Float]] = mapped.runFoldFloatDirect(zero, fold)
    private[streams] def runFoldIntDirect(
      zero: Int,
      fold: (Int, Int) => Async[Int]
    ): Async[Either[E, Int]] = mapped.runFoldIntDirect(zero, fold)
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, Int) => Async[Long]
    ): Async[Either[E, Long]]                                                                                 = mapped.runFoldLongDirect(zero, fold)
    override private[streams] def runFoldLongBlocking(zero: Long, fold: (Long, Int) => Long): Either[E, Long] =
      mapped.runFoldLongBlocking(zero, fold)
  }

  /** A source whose stable async reader can be installed synchronously. */
  private[streams] final class StableAsyncSource[E, A](
    make: () => Reader.AsyncReader[A],
    label: String,
    representation: ElementRepresentation = ElementRepresentation.LateBound
  ) extends Stream[E, A] {
    override private[streams] def elementRepresentation: ElementRepresentation    = representation
    def render: String                                                            = label
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = make()
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit      =
      throw AsyncBoundaryRequired
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() => make())
      null
    }
  }

  private final class SyncMappedIntLongResume[E](
    reader: Reader.SyncReader[Int],
    map: Int => Async[Int],
    fold: (Long, Int) => Async[Long],
    private var acc: Long,
    initial: Pollable[_],
    private var stage: Int
  ) extends Async.CancellationPropagatingPollable[Either[E, Long]](initial) {
    private var child: Pollable[Any] = initial.asInstanceOf[Pollable[Any]]

    def poll(onComplete: Runnable): Async[Either[E, Long]] = {
      while (true) {
        val current = child
        val result  =
          try pollChild(current, onComplete)
          catch { case failure: Throwable => return fail(failure, callback = stage != 2) }
        Async.stepKind(result) match {
          case 1 => return fail(Async.stepCause(result), callback = stage != 2, Async.stepTrusted(result))
          case 2 =>
            val next = result.asInstanceOf[Pollable[Any]]
            child = next
            if (next.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef]) return this
          case _ =>
            clearActive()
            if (stage == 0) {
              val next = continueFold(Async.stepInt(result.asInstanceOf[Async[Int]]))
              if (next.asInstanceOf[AnyRef] ne null) return next
            } else if (stage == 1) {
              acc = Async.stepLong(result.asInstanceOf[Async[Long]])
              val next = continueStream()
              if (next.asInstanceOf[AnyRef] ne null) return next
            } else return Async.succeed(rightLong[E](Async.stepLong(result.asInstanceOf[Async[Long]])))
        }
      }
      this
    }

    private def continueFold(value: Int): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      val effect =
        try fold(acc, value)
        catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
      if (finishContinuation(ownership, effect)) return this
      Async.stepKind(effect) match {
        case 1 => fail(Async.stepCause(effect), callback = true, Async.stepTrusted(effect))
        case 2 =>
          child = effect.asInstanceOf[Pollable[Any]]
          stage = 1
          null
        case _ =>
          acc = Async.stepLong(effect)
          continueStream()
      }
    }

    private def continueStream(): Async[Either[E, Long]] = {
      val ownership = beginContinuation(this)
      if (ownership == 0L) return this
      var budget = 1024
      while (budget > 0) {
        val value =
          try reader.readIntPhysical(Long.MinValue)
          catch {
            case error: StreamError if error.isTrusted =>
              val effect = Async.failTrusted(error)
              return if (finishContinuation(ownership, effect)) this else effect
            case failure: Throwable =>
              val effect = Async.fail(failure)
              return if (finishContinuation(ownership, effect)) this else effect
          }
        if (value == Long.MinValue) {
          val effect = Async.succeed(rightLong[E](acc))
          return if (finishContinuation(ownership, effect)) this else effect
        }

        val mapped =
          try map(value.toInt)
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(mapped) match {
          case 1 =>
            val effect = fail(Async.stepCause(mapped), callback = true, Async.stepTrusted(mapped))
            return if (finishContinuation(ownership, effect)) this else effect
          case 2 =>
            child = mapped.asInstanceOf[Pollable[Any]]
            stage = 0
            if (finishContinuation(ownership, child)) return this
            return null
          case _ =>
        }

        val reduced =
          try fold(acc, Async.stepInt(mapped))
          catch { case failure: Throwable => Async.fail(StreamError.callbackFailure(failure)) }
        Async.stepKind(reduced) match {
          case 1 =>
            val effect = fail(Async.stepCause(reduced), callback = true, Async.stepTrusted(reduced))
            return if (finishContinuation(ownership, effect)) this else effect
          case 2 =>
            child = reduced.asInstanceOf[Pollable[Any]]
            stage = 1
            if (finishContinuation(ownership, child)) return this
            return null
          case _ => acc = Async.stepLong(reduced)
        }
        budget -= 1
      }

      val effect = Async.rescheduleKnown(Sink.foldMappedSyncAsyncIntLong(reader, map, acc, fold), () => ())
      if (finishContinuation(ownership, effect)) this
      else {
        child = effect.asInstanceOf[Pollable[Any]]
        stage = 2
        null
      }
    }

    private def fail(
      failure: Throwable,
      callback: Boolean,
      trusted: Boolean = false
    ): Async[Either[E, Long]] = {
      val cause = if (callback) StreamError.callbackFailure(failure) else failure
      if (!callback && trusted) Async.failTrusted(cause) else Async.fail(cause)
    }
  }

  /** Resource-safe stream backed by a Resource managed in a Scope. */
  private[streams] final class FromResource[R, E, A](
    resource: Resource[R],
    use: R => Stream[E, A]
  ) extends Stream[E, A]
      with StackCompileNode {
    def render: String                            = "Stream.fromResource(...)"
    def stackFrame(bufferSize: Int): CompileFrame = {
      val os     = Scope.global.open()
      val stream =
        try {
          val scope = os.scope
          val r     = StreamError.callback(scope.leak(scope.allocate(resource)))
          StreamError.callback(use(r))
        } catch {
          case primary: Throwable => throw suppressFinalization(primary, StreamError.callback(os.close()))
        }
      new CompileFrame(
        stream,
        bufferSize,
        reader => wrapReader(reader.asInstanceOf[Reader[A]], os.close),
        primary => suppressFinalization(primary, StreamError.callback(os.close()))
      )
    }
    private def closeScopeAsync(closeScope: () => Finalization): Async[Unit] =
      Async.reschedule(() =>
        Async.succeed {
          val failure = suppressFinalization(null, StreamError.callback(closeScope()))
          if (failure ne null) throw failure
          ()
        }
      )
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else makeReader(depth + 1, bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      val os         = Scope.global.open()
      val checkpoint = pipeline.materializationCheckpoint()
      val wrapping   = pipeline.newWrapTransaction()
      try {
        val scope = os.scope
        val r     = StreamError.callback(scope.leak(scope.allocate(resource)))
        pipeline.ensureMaterializationCurrent()
        val stream = StreamError.callback(use(r))
        pipeline.ensureMaterializationCurrent()
        stream.compileInterpreter(pipeline)
        pipeline.ensureMaterializationCurrent()
        pipeline.wrapLastRead(src => wrap(src, os.close), wrapping)
      } catch {
        case t: Throwable =>
          pipeline.rollbackTo(checkpoint, t)
          if (!wrapping.cleanupConsumedOwner)
            StreamError
              .callback(os.close())
              .errors
              .foreach(cause => pipeline.recordCleanupFailure(t, StreamError.callbackFailure(cause)))
          throw t
      }
    }
    private def makeReader(depth: Int, bufferSize: Int): Reader[A] = {
      val os = Scope.global.open()
      try {
        val scope = os.scope
        val r     = StreamError.callback(scope.leak(scope.allocate(resource)))
        wrapReader(StreamError.callback(use(r)).compile(depth, bufferSize), os.close)
      } catch {
        case t: Throwable =>
          throw suppressFinalization(t, StreamError.callback(os.close()))
      }
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferOwnedReaderRoot(makeReader(0, Stream.DefaultBufferSize))
      null
    }
    private def suppressFinalization(primary: Throwable, finalization: Finalization): Throwable = {
      var failure = primary
      finalization.errors.foreach { cause =>
        failure = StreamError.attachCleanup(failure, StreamError.callbackFailure(cause))
      }
      failure
    }
    private def wrap[B](src: Reader.SyncReader[B], closeScope: () => Finalization): Reader.SyncReader[B] =
      new LifecycleWrappingReader[B](src, "Cannot reset a finalized resource reader", false) {
        protected def finishClose(primary: Throwable): Throwable =
          suppressFinalization(primary, StreamError.callback(closeScope()))
      }
    private def wrapReader(src: Reader[A], closeScope: () => Finalization): Reader[A] = src match {
      case reader: Reader.SyncReader[A @unchecked]  => wrap(reader, closeScope)
      case reader: Reader.AsyncReader[A @unchecked] =>
        reader.withReleaseAsync(() => closeScopeAsync(closeScope))
    }
  }

  private[streams] def mapped[E, A, B](self: Stream[E, A], f: A => B, jtB: JvmType.Infer[B]): Mapped[E, A, B] =
    if (jtB eq JvmType.Infer.int)
      new IntMapped(self, f.asInstanceOf[A => Int]).asInstanceOf[Mapped[E, A, B]]
    else new GenericMapped(self, f, jtB)

  /** Lazily maps elements with `f`. */
  private[streams] sealed abstract class Mapped[E, A, B](
    private[streams] val self: Stream[E, A],
    private[streams] val f: A => B
  ) extends Stream[E, B]
      with LinearOperator {
    private[streams] def jtB: JvmType.Infer[B]
    private[streams] def inRepresentation: ElementRepresentation               = self.elementRepresentation
    override private[streams] def elementRepresentation: ElementRepresentation =
      ElementRepresentation.fromJvmType(jtB.jvmType)
    private[streams] def acquireDirect(): Async[Reader[B]] = self match {
      case source: AsyncSource[_, _] =>
        Async.acquireInstallCancelable[Reader[A], Reader[B]](
          () => source.acquire().asInstanceOf[Async[Reader[A]]],
          {
            case reader: Reader.SyncReader[A @unchecked]  => wrapSync(reader)
            case reader: Reader.AsyncReader[A @unchecked] => wrapAsync(reader)
          },
          reader =>
            try
              reader match {
                case sync: Reader.SyncReader[A @unchecked]   => Async.succeed(sync.close())
                case async: Reader.AsyncReader[A @unchecked] => async.close()
              }
            catch { case cause: Throwable => Async.fail(cause) }
        )
      case _ =>
        try Async.succeed(Stream.compileToReader(this))
        catch {
          case error: StreamError if error.isTrusted => Async.failTrusted(error)
          case cause: Throwable                      => Async.fail(cause)
        }
    }
    private[streams] def safeF(a: A): B =
      try f(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    def appendAsync(interpreter: AsyncInterpreter): Unit                          = interpreter.deferMap(jtB.jvmType)(safeF)
    def appendSync(interpreter: SyncInterpreter): Unit                            = interpreter.addMap(interpreter.outputType, jtB.jvmType)(f)
    override def knownLength: Option[Long]                                        = self.knownLength
    def linearSource: Stream[_, _]                                                = self
    def render: String                                                            = s"${self.render}.map(...)"
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return compileLinear[B](this, bufferSize)
      val sourceReader = self.compile(depth + 1, bufferSize)
      sourceReader match {
        case p: SyncInterpreter =>
          p.addMap(p.outputType, jtB.jvmType)(f)
          p.seal()
          p.asInstanceOf[Reader.SyncReader[B]]
        case r: Reader.SyncReader[A @unchecked]       => wrapSync(r)
        case reader: Reader.AsyncReader[A @unchecked] => wrapAsync(reader)
      }
    }
    override private[streams] def compileBlocking(bufferSize: Int): Reader.SyncReader[B] =
      wrapSync(self.compileBlocking(bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addMap(pipeline.outputType, jtB.jvmType)(f)
    }
    override private[streams] def foldReaderKind[Z](
      onSync: Reader.SyncReader[B] => Z,
      onAsync: Reader.AsyncReader[B] => Z
    ): Z = self match {
      case source: FromSyncReader[_, A @unchecked] => onSync(wrapSync(source.compileSync()))
      case _                                       =>
        self.foldReaderKind(
          reader => onSync(wrapSync(reader)),
          reader => onAsync(wrapAsync(reader))
        )
    }

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferMap(jtB.jvmType)(safeF)
      self
    }

    private[streams] def runFoldDoubleDirect(
      zero: Double,
      fold: (Double, B) => Async[Double]
    ): Async[Either[E, Double]] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int) && (jtB.jvmType eq JvmType.Double))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val transform = f.asInstanceOf[Int => Double]
            val reduce    = fold.asInstanceOf[(Double, Double) => Async[Double]]
            if (source.protectAcquisition)
              new AsyncSourceIntDoubleUseProtected[E](acquire, zero, transform, reduce)
            else new AsyncSourceIntDoubleUseUnprotected[E](acquire, zero, transform, reduce)
          case _ => null
        }
      else null

    private[streams] def runFoldFloatDirect(
      zero: Float,
      fold: (Float, B) => Async[Float]
    ): Async[Either[E, Float]] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int) && (jtB.jvmType eq JvmType.Float))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val transform = f.asInstanceOf[Int => Float]
            val reduce    = fold.asInstanceOf[(Float, Float) => Async[Float]]
            if (source.protectAcquisition)
              new AsyncSourceIntFloatUseProtected[E](acquire, zero, transform, reduce)
            else new AsyncSourceIntFloatUseUnprotected[E](acquire, zero, transform, reduce)
          case _ => null
        }
      else null

    private[streams] def runFoldIntDirect(
      zero: Int,
      fold: (Int, B) => Async[Int]
    ): Async[Either[E, Int]] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val transform = f.asInstanceOf[Int => B]
            if (source.protectAcquisition)
              new AsyncSourceMappedIntIntUseProtected[E, B](acquire, zero, transform, fold)
            else new AsyncSourceMappedIntIntUseUnprotected[E, B](acquire, zero, transform, fold)
          case _ => null
        }
      else null

    override private[streams] def runFoldLongBlocking(z: Long, fold: (Long, B) => Long): Either[E, Long] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int) && (jtB.jvmType eq JvmType.Int)) self match {
        case _: Mapped[E @unchecked, Int @unchecked, Int @unchecked] =>
          runMapChainFoldLongBlocking(z, fold.asInstanceOf[(Long, Int) => Long])
        case _ =>
          self
            .asInstanceOf[Stream[E, Int]]
            .runFoldLongTransformedIntBlocking(
              z,
              f.asInstanceOf[Int => Int],
              null,
              fold.asInstanceOf[(Long, Int) => Long]
            )
      }
      else super.runFoldLongBlocking(z, fold)

    private def runMapChainFoldLongBlocking(z: Long, fold: (Long, Int) => Long): Either[E, Long] = {
      var count                 = 0
      var current: Stream[_, _] = this
      var collecting            = true
      while (collecting) current match {
        case mapped: Mapped[_, _, _]
            if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
              (mapped.jtB.jvmType eq JvmType.Int) =>
          count += 1
          current = mapped.self
        case _ => collecting = false
      }
      val maps = new Array[Int => Int](count)
      current = this
      var index = count - 1
      while (index >= 0) {
        val mapped = current.asInstanceOf[Mapped[_, Int, Int]]
        maps(index) = mapped.f.asInstanceOf[Int => Int]
        current = mapped.self
        index -= 1
      }
      current
        .asInstanceOf[Stream[E, Int]]
        .runFoldLongMappedIntChainBlocking(z, maps, fold)
    }

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, B) => Async[Long]
    ): Async[Either[E, Long]] =
      if (inRepresentation.stableJvmType.contains(JvmType.Int) && (jtB.jvmType eq JvmType.Int)) {
        val direct = self match {
          case source: AsyncSource[_, _] =>
            val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val map     = f.asInstanceOf[Int => Int]
            val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
            if (source.protectAcquisition) new SyncMappedIntLongUseProtected[E](acquire, map, zero, reduce)
            else new SyncMappedIntLongUseUnprotected[E](acquire, map, zero, reduce)
          case filtered: Filtered[E @unchecked, Int @unchecked] =>
            filtered.runMappedFoldLongDirect(
              zero,
              f.asInstanceOf[Int => Int],
              jtB.jvmType,
              fold.asInstanceOf[(Long, Int) => Async[Long]]
            )
          case _: Mapped[E @unchecked, Int @unchecked, Int @unchecked] =>
            runMapChainFoldLongDirect(zero, fold.asInstanceOf[(Long, Int) => Async[Long]])
          case mapped: AsyncMapped[E @unchecked, Int @unchecked, Int @unchecked] =>
            mapped.runMappedFoldLongDirect(
              zero,
              f.asInstanceOf[Int => Int],
              jtB.jvmType,
              fold.asInstanceOf[(Long, Int) => Async[Long]]
            )
          case flatMapped: FlatMapped[_, E @unchecked, _, Int @unchecked] =>
            val transform = f.asInstanceOf[Int => Int]
            val reduce    = fold.asInstanceOf[(Long, Int) => Async[Long]]
            flatMapped.runFoldLongDirect(zero, (acc, value) => reduce(acc, transform(value)))
          case _ => null
        }
        if (direct.asInstanceOf[AnyRef] ne null) direct
        else
          Stream.useLinearIntFoldLongDirect(
            this.asInstanceOf[Stream[E, Int]],
            zero,
            fold.asInstanceOf[(Long, Int) => Async[Long]]
          )
      } else if (inRepresentation.stableJvmType.contains(JvmType.Int) && (jtB.jvmType eq JvmType.Long))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire   = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val transform = f.asInstanceOf[Int => Long]
            val reduce    = fold.asInstanceOf[(Long, Long) => Async[Long]]
            if (source.protectAcquisition)
              new AsyncSourceMappedIntLongUseProtected[E](acquire, zero, transform, reduce)
            else new AsyncSourceMappedIntLongUseUnprotected[E](acquire, zero, transform, reduce)
          case _ => null
        }
      else null

    private[streams] def runMappedFoldLongDirect[C](
      zero: Long,
      downstream: B => C,
      outType: JvmType,
      fold: (Long, C) => Async[Long]
    ): Async[Either[E, Long]] =
      runMappedFoldLongDirect(zero, downstream, outType, fold, 1)

    private def runMapChainFoldLongDirect(
      zero: Long,
      fold: (Long, Int) => Async[Long]
    ): Async[Either[E, Long]] = {
      var count                     = 0
      var current: Stream[_, _]     = this
      var source: AsyncSource[_, _] = null
      var collecting                = true
      while (collecting) current match {
        case mapped: Mapped[_, _, _]
            if mapped.inRepresentation.stableJvmType.contains(JvmType.Int) &&
              (mapped.jtB.jvmType eq JvmType.Int) =>
          count += 1
          current = mapped.self
        case async: AsyncSource[_, _] if async.elementRepresentation.stableJvmType.contains(JvmType.Int) =>
          source = async
          collecting = false
        case _ => return null
      }

      val maps = new Array[Int => Int](count)
      current = this
      var index = count - 1
      while (index >= 0) {
        val mapped = current.asInstanceOf[Mapped[_, Int, Int]]
        maps(index) = mapped.f.asInstanceOf[Int => Int]
        current = mapped.self
        index -= 1
      }
      val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
      if (source.protectAcquisition) new SyncMappedIntChainLongUseProtected[E](acquire, maps, zero, fold)
      else new SyncMappedIntChainLongUseUnprotected[E](acquire, maps, zero, fold)
    }

    private def hasSpecializedPrimitiveInput(inType: JvmType): Boolean =
      if (inType eq JvmType.Int) {
        if (jtB.jvmType eq JvmType.Boolean) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcZI$sp`]
        else if (jtB.jvmType eq JvmType.Int) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcII$sp`]
        else if (jtB.jvmType eq JvmType.Long) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcJI$sp`]
        else if (jtB.jvmType eq JvmType.Float) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcFI$sp`]
        else if (jtB.jvmType eq JvmType.Double) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcDI$sp`]
        else false
      } else if (inType eq JvmType.Long) {
        if (jtB.jvmType eq JvmType.Boolean) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcZJ$sp`]
        else if (jtB.jvmType eq JvmType.Int) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcIJ$sp`]
        else if (jtB.jvmType eq JvmType.Long) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcJJ$sp`]
        else if (jtB.jvmType eq JvmType.Float) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcFJ$sp`]
        else if (jtB.jvmType eq JvmType.Double) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcDJ$sp`]
        else false
      } else if (inType eq JvmType.Float) {
        if (jtB.jvmType eq JvmType.Boolean) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcZF$sp`]
        else if (jtB.jvmType eq JvmType.Int) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcIF$sp`]
        else if (jtB.jvmType eq JvmType.Long) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcJF$sp`]
        else if (jtB.jvmType eq JvmType.Float) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcFF$sp`]
        else if (jtB.jvmType eq JvmType.Double) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcDF$sp`]
        else false
      } else if (inType eq JvmType.Double) {
        if (jtB.jvmType eq JvmType.Boolean) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcZD$sp`]
        else if (jtB.jvmType eq JvmType.Int) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcID$sp`]
        else if (jtB.jvmType eq JvmType.Long) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcJD$sp`]
        else if (jtB.jvmType eq JvmType.Float) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcFD$sp`]
        else if (jtB.jvmType eq JvmType.Double) f.isInstanceOf[scala.runtime.java8.`JFunction1$mcDD$sp`]
        else false
      } else false

    private def isPrimitiveFilter(reader: Reader.WrappedReader): Boolean = reader match {
      case _: Reader.FilteredIntInt | _: Reader.FilteredInt | _: Reader.FilteredLong | _: Reader.FilteredFloat |
          _: Reader.FilteredDouble =>
        true
      case _ => false
    }

    private def runMappedFoldLongDirect[C](
      zero: Long,
      downstream: B => C,
      outType: JvmType,
      fold: (Long, C) => Async[Long],
      depth: Int
    ): Async[Either[E, Long]] =
      if (
        depth < Stream.DepthCutoff &&
        inRepresentation.stableJvmType.contains(JvmType.Int) &&
        (jtB.jvmType eq JvmType.Int) &&
        (outType eq JvmType.Int)
      ) {
        val current  = f.asInstanceOf[Int => Int]
        val next     = downstream.asInstanceOf[Int => Int]
        val composed = (value: Int) => next(current(value))
        self match {
          case source: AsyncSource[_, _] =>
            val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
            val map     = (value: Int) => Async.succeed(composed(value))
            val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
            if (source.protectAcquisition) new AsyncMappedIntLongUseProtected[E](acquire, map, zero, reduce)
            else new AsyncMappedIntLongUseUnprotected[E](acquire, map, zero, reduce)
          case filtered: Filtered[E @unchecked, Int @unchecked] =>
            filtered.runMappedFoldLongDirect(
              zero,
              composed,
              outType,
              fold.asInstanceOf[(Long, Int) => Async[Long]]
            )
          case mapped: Mapped[E @unchecked, Int @unchecked, Int @unchecked] =>
            mapped.runMappedFoldLongDirect(
              zero,
              composed,
              outType,
              fold.asInstanceOf[(Long, Int) => Async[Long]],
              depth + 1
            )
          case _ => null
        }
      } else null

    private def syncF(inType: JvmType): AnyRef =
      if ((inType eq JvmType.Int) && (jtB.jvmType eq JvmType.Int)) {
        val original = f.asInstanceOf[Int => Int]
        (
          (value: Int) =>
            try original(value)
            catch { case error: StreamError => throw StreamError.untrusted(error) }
        ).asInstanceOf[AnyRef]
      } else SyncInterpreter.adaptMap(inType, jtB.jvmType, f)

    private def wrapAsync(reader: Reader.AsyncReader[A]): Reader.AsyncReader[B] = {
      val readerType =
        try reader.jvmType
        catch { case _: Throwable => return transformAsync(reader)(_.deferMap(jtB.jvmType)(safeF)) }
      val inType = ElementRepresentation.resolve(inRepresentation, readerType)
      if ((readerType eq JvmType.Int) && (inType eq JvmType.Int) && (jtB.jvmType eq JvmType.Int))
        reader match {
          case mapped: Reader.MappedAsyncIntInt =>
            val next = f.asInstanceOf[Int => Int]
            new Reader.MappedAsyncIntInt(mapped.source, value => next(mapped.f(value)))
              .asInstanceOf[Reader.AsyncReader[B]]
          case _ =>
            new Reader.MappedAsyncIntInt(
              reader.asInstanceOf[Reader.AsyncReader[Int]],
              f.asInstanceOf[Int => Int]
            ).asInstanceOf[Reader.AsyncReader[B]]
        }
      else if ((readerType eq JvmType.Int) && (inType eq JvmType.Int) && (jtB.jvmType eq JvmType.AnyRef))
        new Reader.MappedAsyncIntRef(
          reader.asInstanceOf[Reader.AsyncReader[Int]],
          f.asInstanceOf[Int => B]
        )
      else transformAsync(reader)(_.deferMap(jtB.jvmType)(safeF))
    }

    private def wrapSync(reader: Reader.SyncReader[A]): Reader.SyncReader[B] = {
      val inType  = ElementRepresentation.resolve(inRepresentation, reader.jvmType)
      val outType = jtB.jvmType
      val adapted = syncF(inType)
      val wrapped = reader match {
        case thin: Reader.WrappedReader
            if (inType ne JvmType.Boolean) && (inType ne JvmType.Byte) && (inType ne JvmType.Char) &&
              (inType ne JvmType.Short) && !(isPrimitiveFilter(thin) && hasSpecializedPrimitiveInput(inType)) =>
          val interpreter = thin.toInterpreter
          interpreter.addAdaptedMap(inType, outType, adapted)
          interpreter.seal()
          interpreter
        case _ =>
          (SyncInterpreter.laneOf(inType): @scala.annotation.switch) match {
            case 0 =>
              if ((inType eq JvmType.Int) && (outType eq JvmType.Int))
                new Reader.MappedIntInt(reader.asInstanceOf[Reader.SyncReader[Int]], adapted)
              else new Reader.MappedInt(reader, adapted, outType, inType)
            case 1 => new Reader.MappedLong(reader, adapted, outType)
            case 2 => new Reader.MappedFloat(reader, adapted, outType)
            case 3 => new Reader.MappedDouble(reader, adapted, outType)
            case _ => new Reader.MappedRef(reader, adapted, outType)
          }
      }
      wrapped.asInstanceOf[Reader.SyncReader[B]]
    }
  }

  private final class GenericMapped[E, A, B](
    self: Stream[E, A],
    f: A => B,
    private[streams] val jtB: JvmType.Infer[B]
  ) extends Mapped[E, A, B](self, f)

  private final class IntMapped[E, A](self: Stream[E, A], f: A => Int) extends Mapped[E, A, Int](self, f) {
    private[streams] def jtB: JvmType.Infer[Int] = JvmType.Infer.int
  }

  /** Restarts the stream on clean close. */
  private[streams] final class Repeated[E, A](self: Stream[E, A]) extends Stream[E, A] with StackCompileNode {
    private val normalizeCycles                                                = self.elementRepresentation == ElementRepresentation.LateBound
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation match {
      case ElementRepresentation.LateBound => ElementRepresentation.Boxed
      case stable                          => stable
    }
    def render: String                            = s"${self.render}.repeated"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]], bufferSize))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize), bufferSize)
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      if (normalizeCycles) {
        val sourceType = pipeline.outputType
        if (sourceType != JvmType.AnyRef)
          pipeline.addAdaptedMap(
            sourceType,
            JvmType.AnyRef,
            SyncInterpreter.logicalBridgeFn(sourceType, JvmType.AnyRef)
          )
      }
      pipeline.wrapOutput(r => Reader.repeated[A](r.asInstanceOf[Reader.SyncReader[A]], pipeline.operationGuard))
    }
    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      def cycle(): Reader[A] = {
        val current = normalizeCycle(Stream.compileToReader(self))
        current match {
          case reader: Reader.SyncReader[A @unchecked] =>
            reader.toAsync.concatAsyncWithJvmType(() => Async.succeed(cycle()), reader.jvmType)
          case reader: Reader.AsyncReader[A @unchecked] =>
            reader.concatAsyncWithJvmType(() => Async.succeed(cycle()), reader.jvmType)
        }
      }
      pipeline.deferReaderRoot(() => cycle())
      null
    }
    private def wrapReader(upstream: Reader[A], bufferSize: Int): Reader[A] = upstream match {
      case original: Reader.SyncReader[A @unchecked] =>
        val reader = normalizeSyncCycle(original)
        Reader.repeated(reader)
      case original: Reader.AsyncReader[A @unchecked] =>
        val reader                                                       = normalizeAsyncCycle(original)
        def cycle(current: Reader.AsyncReader[A]): Reader.AsyncReader[A] =
          current.concatAsyncWithJvmType(
            () =>
              Async.reschedule(() =>
                Async.succeed(
                  normalizeCycle(self.compile(0, bufferSize)) match {
                    case next: Reader.SyncReader[A @unchecked]  => cycle(next.toAsync)
                    case next: Reader.AsyncReader[A @unchecked] => cycle(next)
                  }
                )
              ),
            current.jvmType
          )
        cycle(reader)
    }
    private def normalizeCycle(reader: Reader[A]): Reader[A] = reader match {
      case sync: Reader.SyncReader[A @unchecked]   => normalizeSyncCycle(sync)
      case async: Reader.AsyncReader[A @unchecked] => normalizeAsyncCycle(async)
    }
    private def normalizeSyncCycle(reader: Reader.SyncReader[A]): Reader.SyncReader[A] =
      if (!normalizeCycles || reader.jvmType == JvmType.AnyRef) reader
      else {
        val sourceType  = reader.jvmType
        val interpreter = SyncInterpreter.unsealed(reader)
        interpreter.addAdaptedMap(
          sourceType,
          JvmType.AnyRef,
          SyncInterpreter.logicalBridgeFn(sourceType, JvmType.AnyRef)
        )
        interpreter.seal()
        interpreter.asInstanceOf[Reader.SyncReader[A]]
      }
    private def normalizeAsyncCycle(reader: Reader.AsyncReader[A]): Reader.AsyncReader[A] =
      if (!normalizeCycles) reader
      else transformAsync(reader)(_.normalizeRecoveryOutput(JvmType.AnyRef))
  }

  /**
   * Skips the first `dropCount` elements, then emits at most `takeCount`
   * elements.
   */
  private[streams] final class TakeDrop[E, A](
    private[streams] val self: Stream[E, A],
    private[streams] val dropCount: Long,
    private[streams] val takeCount: Long
  ) extends Stream[E, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    def appendFused(interpreter: AsyncInterpreter): Unit = {
      interpreter.deferDrop(dropCount)
      interpreter.deferTake(takeCount)
    }
    def forcesAsync: Boolean               = false
    def fusableSource: Stream[_, _]        = self
    override def knownLength: Option[Long] =
      self.knownLength.map(length => math.max(0L, math.min(takeCount, math.max(0L, length - math.max(0L, dropCount)))))
    def render: String                            = s"${self.render}.drop($dropCount).take($takeCount)"
    def stackFrame(bufferSize: Int): CompileFrame =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapOutput { reader =>
        val skipped = reader.setSkip(dropCount)
        pipeline.ensureMaterializationCurrent()
        if (!skipped) reader.skip(dropCount)
        if (!reader.setLimit(takeCount)) Reader.withSkipLimit(reader, 0L, takeCount) else reader
      }
    }
    override private[streams] def elementRepresentation: ElementRepresentation                           = self.elementRepresentation
    override private[streams] def runFoldLongBlocking(z: Long, fold: (Long, A) => Long): Either[E, Long] =
      self match {
        case range: IntRange =>
          try
            Right(range.foldTakeDropRange(z, dropCount, takeCount, fold.asInstanceOf[(Long, Int) => Long]))
              .asInstanceOf[Either[E, Long]]
          catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case vector: FromVectorStream[A @unchecked] =>
          try
            Right(
              vector.foldSliceLong(z, dropCount, takeCount, null, fold)
            ).asInstanceOf[Either[E, Long]]
          catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case _ => super.runFoldLongBlocking(z, fold)
      }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (source.protectAcquisition)
          new AsyncTakeDropIntLongUseProtected[E](acquire, dropCount, takeCount, zero, reduce)
        else new AsyncTakeDropIntLongUseUnprotected[E](acquire, dropCount, takeCount, zero, reduce)
      case mapped: AsyncMapped[_, _, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        mapped
          .asInstanceOf[AsyncMapped[E, Int, Int]]
          .runTakeDropFoldLongDirect(
            dropCount,
            takeCount,
            zero,
            fold.asInstanceOf[(Long, Int) => Async[Long]]
          )
      case _ => null
    }
    private def wrapReader(source: Reader[A]): Reader[A] = source match {
      case reader: Reader.SyncReader[A @unchecked] =>
        try {
          if (!reader.setSkip(dropCount)) reader.skip(dropCount)
          if (!reader.setLimit(takeCount)) Reader.withSkipLimit(reader, 0L, takeCount) else reader
        } catch { case cause: Throwable => rejectSyncReader(reader, cause) }
      case reader: Reader.AsyncReader[A @unchecked] =>
        transformAsync(reader) { interpreter =>
          interpreter.deferDrop(dropCount)
          interpreter.deferTake(takeCount)
        }
    }
  }

  /**
   * A source-aware take/drop node that does not retain its temporary async
   * source.
   */
  private final class ProtectedAsyncTakeDrop[E, A](
    acquireRaw: () => Async[Reader[A]],
    representation: ElementRepresentation,
    dropCount: Long,
    takeCount: Long
  ) extends Stream[E, A]
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = representation
    def render: String                                                         = s"Stream.fromReaderAsync(...).drop($dropCount).take($takeCount)"

    private def boundary(): Reader.AsyncReader[A] =
      Reader.closed.toAsync.concatAsyncWithJvmType(
        () => StreamError.callbackAsync(acquireRaw()),
        representation.stableJvmType.getOrElse(JvmType.AnyRef)
      )

    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] = wrapReader(boundary())

    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
      throw AsyncBoundaryRequired

    override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
      pipeline.deferAsyncReader(() => boundary())
      pipeline.deferDrop(dropCount)
      pipeline.deferTake(takeCount)
      null
    }

    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] =
      if (representation.stableJvmType.contains(JvmType.Int))
        new AsyncTakeDropIntLongUseProtected[E](
          acquireRaw.asInstanceOf[() => Async[Reader[Int]]],
          dropCount,
          takeCount,
          zero,
          fold.asInstanceOf[(Long, Int) => Async[Long]]
        )
      else null

    private def wrapReader(reader: Reader.AsyncReader[A]): Reader.AsyncReader[A] =
      transformAsync(reader) { interpreter =>
        interpreter.deferDrop(dropCount)
        interpreter.deferTake(takeCount)
      }
  }

  /** Emits at most the first `n` elements. */
  private[streams] final class Taken[E, A](private[streams] val self: Stream[E, A], n: Long)
      extends Stream[E, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def appendFused(interpreter: AsyncInterpreter): Unit                       = interpreter.deferTake(n)
    def forcesAsync: Boolean                                                   = false
    def fusableSource: Stream[_, _]                                            = self
    override def knownLength: Option[Long]                                     = self.knownLength.map(l => math.max(0L, math.min(n, l)))
    def render: String                                                         = s"${self.render}.take($n)"
    def stackFrame(bufferSize: Int): CompileFrame                              =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapOutput(r => if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r)
    }
    override private[streams] def runFoldLongBlocking(z: Long, fold: (Long, A) => Long): Either[E, Long] =
      self match {
        case range: IntRange =>
          try Right(range.foldTakeRange(z, n, fold.asInstanceOf[(Long, Int) => Long])).asInstanceOf[Either[E, Long]]
          catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case vector: FromVectorStream[A @unchecked] =>
          try
            Right(vector.foldSliceLong(z, 0L, n, null, fold))
              .asInstanceOf[Either[E, Long]]
          catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case _ => super.runFoldLongBlocking(z, fold)
      }
    private[streams] def runCollectDirect(): Async[Either[E, Chunk[A]]] = self match {
      case filtered: AsyncFiltered[E @unchecked, A @unchecked] => filtered.runTakeCollectDirect(n)
      case _                                                   => null
    }
    private[streams] def runFoldDirect[Z](zero: Z, fold: (Z, A) => Async[Z]): Async[Either[E, Z]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val reduce  = fold.asInstanceOf[(Z, Int) => Async[Z]]
        if (source.protectAcquisition) new AsyncSourceIntFoldUseProtected[E, Z](acquire, n, zero, reduce)
        else new AsyncSourceIntFoldUseUnprotected[E, Z](acquire, n, zero, reduce)
      case _ => null
    }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (source.protectAcquisition) new AsyncTakenIntLongUseProtected[E](acquire, n, zero, reduce)
        else new AsyncTakenIntLongUseUnprotected[E](acquire, n, zero, reduce)
      case mapped: AsyncMapped[_, _, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        mapped
          .asInstanceOf[AsyncMapped[E, Int, Int]]
          .runTakeDropFoldLongDirect(0L, n, zero, fold.asInstanceOf[(Long, Int) => Async[Long]])
      case _ => null
    }
    private def wrapReader(upstream: Reader[A]): Reader[A] = upstream match {
      case r: Reader.SyncReader[A @unchecked] =>
        try if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r
        catch { case cause: Throwable => rejectSyncReader(r, cause) }
      case r: Reader.AsyncReader[A @unchecked] => transformAsync(r)(_.deferTake(n))
    }
  }

  /** Emits elements while `pred` holds. */
  private[streams] final class TakenWhile[E, A](private[streams] val self: Stream[E, A], pred: A => Boolean)
      extends Stream[E, A]
      with AsyncFusableNode
      with MaterializationBoundary {
    override private[streams] def elementRepresentation: ElementRepresentation = self.elementRepresentation
    def appendFused(interpreter: AsyncInterpreter): Unit                       = interpreter.deferTakeWhile(test)
    def forcesAsync: Boolean                                                   = false
    def fusableSource: Stream[_, _]                                            = self
    def render: String                                                         = s"${self.render}.takeWhile(...)"
    def stackFrame(bufferSize: Int): CompileFrame                              =
      unaryFrame(self, bufferSize, reader => wrapReader(reader.asInstanceOf[Reader[A]]))
    private[streams] def test(value: A): Boolean =
      try pred(value)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    override private[streams] def compile(depth: Int, bufferSize: Int): Reader[A] =
      if (depth >= Stream.DepthCutoff) compileStackSafe(this, bufferSize)
      else wrapReader(self.compile(depth + 1, bufferSize))
    private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapOutput(r =>
        new Reader.TakenWhile[A](r.asInstanceOf[Reader.SyncReader[A]], test, pipeline.operationGuard)
      )
    }
    override private[streams] def runFoldLongBlocking(z: Long, fold: (Long, A) => Long): Either[E, Long] =
      self match {
        case range: IntRange =>
          try {
            Right(
              range.foldTakeWhileRange(
                z,
                (value: Int) => test(value.asInstanceOf[A]),
                fold.asInstanceOf[(Long, Int) => Long]
              )
            ).asInstanceOf[Either[E, Long]]
          } catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case vector: FromVectorStream[A @unchecked] =>
          try
            Right(
              vector.foldTakeWhileLong(
                z,
                test,
                fold
              )
            ).asInstanceOf[Either[E, Long]]
          catch {
            case error: StreamError => throw StreamError.untrusted(error)
          }
        case _ => super.runFoldLongBlocking(z, fold)
      }
    private[streams] def runFoldLongDirect(
      zero: Long,
      fold: (Long, A) => Async[Long]
    ): Async[Either[E, Long]] = self match {
      case source: AsyncSource[_, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val acquire = source.acquisition.asInstanceOf[() => Async[Reader[Int]]]
        val node    = this.asInstanceOf[TakenWhile[_, Int]]
        val reduce  = fold.asInstanceOf[(Long, Int) => Async[Long]]
        if (source.protectAcquisition)
          new AsyncTakenWhileIntLongUseProtected[E](acquire, node, zero, reduce)
        else new AsyncTakenWhileIntLongUseUnprotected[E](acquire, node, zero, reduce)
      case mapped: AsyncMapped[_, _, _] if elementRepresentation.stableJvmType.contains(JvmType.Int) =>
        val intMapped = mapped.asInstanceOf[AsyncMapped[E, Int, Int]]
        val node      = this.asInstanceOf[TakenWhile[_, Int]]
        new AsyncMappedTakeWhileIntLongUse[E](
          () => intMapped.self.acquireReader.asInstanceOf[Async[Reader[Int]]],
          intMapped.f.asInstanceOf[Int => Async[Int]],
          node.test,
          zero,
          fold.asInstanceOf[(Long, Int) => Async[Long]]
        )
      case _ => null
    }
    private def wrapReader(upstream: Reader[A]): Reader[A] = upstream match {
      case reader: Reader.SyncReader[A @unchecked]  => new Reader.TakenWhile[A](reader, test)
      case reader: Reader.AsyncReader[A @unchecked] => transformAsync(reader)(_.deferTakeWhile(test))
    }
  }

}
