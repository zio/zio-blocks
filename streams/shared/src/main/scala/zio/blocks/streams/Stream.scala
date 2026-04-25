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

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.combinators.Tuples
import zio.blocks.scope.{Resource, Scope}
import zio.blocks.streams.internal.{EndOfStream, Interpreter, StreamError, unsafeEvidence}
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
   * Returns the underlying [[Chunk]] if this stream wraps a known, materialized
   * chunk, `None` otherwise. O(1).
   *
   * Only streams created via [[Stream.fromChunk]] or [[Stream.empty]] return
   * `Some`. All combinators (map, filter, take, drop, concat, etc.) return
   * `None` because the chunk identity is lost after transformation.
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
  final def ++[E2, A2](that: Stream[E2, A2]): Stream[E | E2, A | A2] = concat(that)

  /**
   * Alias for [[orElse]]. The fallback stream is evaluated lazily, only on
   * error.
   */
  def ||[E2, A1](that: => Stream[E2, A1]): Stream[E2, A | A1] =
    orElse(that)

  /** Recovers from all errors by switching to the stream returned by `f`. */
  def catchAll[E2, A1](f: E => Stream[E2, A1]): Stream[E2, A | A1] =
    new Stream.CatchAll[E, E2, A | A1](this, f)

  /**
   * Recovers from non-fatal defects (throwables that are not typed stream
   * errors) matching `f`.
   */
  def catchDefect[E1, A1](f: PartialFunction[Throwable, Stream[E1, A1]]): Stream[E | E1, A | A1] =
    new Stream.CatchDefect[E | E1, A | A1](this, f)

  /** Applies a partial function, emitting only defined results. */
  def collect[B](pf: PartialFunction[A, B])(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Stream[E, B] =
    via(Pipeline.collect(pf))

  /** Emits all elements of `this` followed by all elements of `that`. */
  def concat[E2, A2](that: Stream[E2, A2]): Stream[E | E2, A | A2] =
    new Stream.Concatenated[E | E2, A | A2](this, that)

  /** Counts the number of elements. */
  def count: Either[E, Long] = run(Sink.count)

  /** Skips the first `n` elements, then emits the rest. */
  def drop(n: Long): Stream[E, A] = new Stream.Dropped(this, n)

  /**
   * Emits only elements not previously seen, using a mutable set internally.
   */
  def distinct(implicit jtA: JvmType.Infer[A]): Stream[E, A] = Stream.suspend {
    val seen = new scala.collection.mutable.HashSet[A]()
    this.filter(a => seen.add(a))
  }

  /**
   * Emits only elements whose key (computed by `f`) has not been seen before.
   */
  def distinctBy[K](f: A => K)(implicit jtA: JvmType.Infer[A]): Stream[E, A] = Stream.suspend {
    val seen = new scala.collection.mutable.HashSet[K]()
    this.filter(a => seen.add(f(a)))
  }

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

  /** Maps each element to a stream and flattens the results sequentially. */
  def flatMap[E2, B](
    f: A => Stream[E2, B]
  )(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Stream[E | E2, B] =
    new Stream.FlatMapped[E, E | E2, A, B](this, f, jtA, jtB)

  /** Returns `true` if all elements satisfy `pred`, short-circuiting. */
  def forall(pred: A => Boolean): Either[E, Boolean] = run(Sink.forall(pred))

  /**
   * Alias for [[runForeach]]. Applies `f` to each element for its side-effects.
   */
  def foreach(f: A => Unit): Either[E, Unit] = runForeach(f)

  /**
   * Groups elements into [[Chunk]]s of size `n`. The last group may be smaller.
   */
  def grouped(n: Int): Stream[E, Chunk[A]] = {
    require(n >= 1, s"grouped requires n >= 1, got n=$n")
    new Stream.FromReader[E, Chunk[A]](
      () => {
        val source = Stream.compileToReader(this)
        val et     = source.jvmType
        if (et eq JvmType.Byte) {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = new ChunkBuilder.Byte()
              var i       = 0
              var v       = source.readInt(Long.MinValue)(using unsafeEvidence)
              while (v != Long.MinValue && i < n) {
                builder.addOne(v.toByte)
                i += 1
                if (i < n) v = source.readInt(Long.MinValue)(using unsafeEvidence)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        } else if (et eq JvmType.Int) {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = new ChunkBuilder.Int()
              var i       = 0
              var v       = source.readInt(Long.MinValue)(using unsafeEvidence)
              while (v != Long.MinValue && i < n) {
                builder.addOne(v.toInt)
                i += 1
                if (i < n) v = source.readInt(Long.MinValue)(using unsafeEvidence)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        } else if (et eq JvmType.Long) {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = new ChunkBuilder.Long()
              var i       = 0
              var v       = source.readLong(Long.MaxValue)(using unsafeEvidence)
              while (v != Long.MaxValue && i < n) {
                builder.addOne(v)
                i += 1
                if (i < n) v = source.readLong(Long.MaxValue)(using unsafeEvidence)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        } else if (et eq JvmType.Float) {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = new ChunkBuilder.Float()
              var i       = 0
              var v       = source.readFloat(Double.MaxValue)(using unsafeEvidence)
              while (v != Double.MaxValue && i < n) {
                builder.addOne(v.toFloat)
                i += 1
                if (i < n) v = source.readFloat(Double.MaxValue)(using unsafeEvidence)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        } else if (et eq JvmType.Double) {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = new ChunkBuilder.Double()
              var i       = 0
              var v       = source.readDouble(Double.MaxValue)(using unsafeEvidence)
              while (v != Double.MaxValue && i < n) {
                builder.addOne(v)
                i += 1
                if (i < n) v = source.readDouble(Double.MaxValue)(using unsafeEvidence)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        } else {
          new Reader[Chunk[A]] {
            def isClosed                               = source.isClosed
            def read[A1 >: Chunk[A]](sentinel: A1): A1 = {
              val builder = ChunkBuilder.make[A](n)
              var i       = 0
              var v       = source.read[Any](EndOfStream)
              while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
                builder += v.asInstanceOf[A]
                i += 1
                if (i < n) v = source.read[Any](EndOfStream)
              }
              if (i == 0) sentinel else builder.result().asInstanceOf[A1]
            }
            def close() = source.close()
          }
        }
      },
      s"${this.render}.grouped($n)"
    )
  }

  /** Returns the first element, or `None` if empty. */
  def head: Either[E, Option[A]] = run(Sink.head)

  /** Inserts `sep` between each pair of consecutive elements. */
  def intersperse[A1 >: A](sep: A1): Stream[E, A1] =
    new Stream.FromReader[E, A1](
      () => {
        val source = Stream.compileToReader(this)
        new Reader[A1] {
          private var first                    = true
          private var hasCached                = false
          private var cached: Any              = null
          def isClosed                         = source.isClosed
          def read[A2 >: A1](sentinel: A2): A2 =
            if (hasCached) { hasCached = false; cached.asInstanceOf[A2] }
            else {
              val v = source.read[Any](EndOfStream)
              if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
              else if (first) { first = false; v.asInstanceOf[A2] }
              else { cached = v; hasCached = true; sep.asInstanceOf[A2] }
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
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[E, B] =
    Stream.suspend {
      var state = init
      this.map { a =>
        val (newState, b) = f(state, a)
        state = newState
        b
      }
    }

  /**
   * Transforms the error channel with `f`, leaving elements unchanged.
   *
   * When `E` is `Nothing` (infallible stream), the call is eliminated at
   * compile time and the stream is returned unchanged.
   */
  inline def mapError[E2](f: E => E2): Stream[E2, A] =
    scala.compiletime.summonFrom {
      case _: (E =:= Nothing) => this.asInstanceOf[Stream[E2, A]]
      case _                  => new Stream.ErrorMapped(this, f)
    }

  /** Falls back to `that` on any error. Alias: `||`. */
  def orElse[E2, A1](that: => Stream[E2, A1]): Stream[E2, A | A1] =
    catchAll[E2, A1](_ => that)

  /** Restarts this stream from the beginning each time it completes cleanly. */
  def repeated: Stream[E, A] = new Stream.Repeated(this)

  /**
   * Runs this stream into `sink`, returning either an error `Left(e)` or the
   * sink's result `Right(z)`. Typed stream errors are returned as `Left`;
   * untyped defects propagate as exceptions.
   */
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z] =
    try {
      val reader = Stream.compileToReader(this)
      try Right(sink.drain(reader))
      finally reader.close()
    } catch {
      case e: StreamError => Left(e.value.asInstanceOf[E2])
    }

  /** Runs the stream and collects all elements into a [[Chunk]]. */
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
  def runFold[Z](z: Z)(f: (Z, A) => Z): Either[E, Z] = run(Sink.foldLeft(z)(f))

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
            private var state: Byte             = init.asInstanceOf[Byte]
            private var emittedInit             = false
            override def jvmType: JvmType       = JvmType.Byte
            def isClosed: Boolean               = source.isClosed
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) sentinel
                else { state = fB(state, v.toByte); state.asInstanceOf[S1] }
              }
            override def readInt(sentinel: Long)(using S <:< Int): Long =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Byte].toLong }
              else {
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) sentinel
                else { state = fB(state, v.toByte); state.toLong }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Int) && (outType eq JvmType.Int)) {
          val fI = f.asInstanceOf[(Int, Int) => Int]
          new Reader[S] {
            private var state: Int              = init.asInstanceOf[Int]
            private var emittedInit             = false
            override def jvmType: JvmType       = JvmType.Int
            def isClosed: Boolean               = source.isClosed
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) sentinel
                else { state = fI(state, v.toInt); state.asInstanceOf[S1] }
              }
            override def readInt(sentinel: Long)(using S <:< Int): Long =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Int].toLong }
              else {
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) sentinel
                else { state = fI(state, v.toInt); state.toLong }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Long) && (outType eq JvmType.Long)) {
          val fL = f.asInstanceOf[(Long, Long) => Long]
          new Reader[S] {
            private var state: Long             = init.asInstanceOf[Long]
            private var emittedInit             = false
            override def jvmType: JvmType       = JvmType.Long
            def isClosed: Boolean               = source.isClosed
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.readLong(Long.MaxValue)(using unsafeEvidence)
                if (v == Long.MaxValue) sentinel
                else { state = fL(state, v); state.asInstanceOf[S1] }
              }
            override def readLong(sentinel: Long)(using S <:< Long): Long =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Long] }
              else {
                val v = source.readLong(Long.MaxValue)(using unsafeEvidence)
                if (v == Long.MaxValue) sentinel
                else { state = fL(state, v); state }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Float) && (outType eq JvmType.Float)) {
          val fF = f.asInstanceOf[(Float, Float) => Float]
          new Reader[S] {
            private var state: Float            = init.asInstanceOf[Float]
            private var emittedInit             = false
            override def jvmType: JvmType       = JvmType.Float
            def isClosed: Boolean               = source.isClosed
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.readFloat(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) sentinel
                else { state = fF(state, v.toFloat); state.asInstanceOf[S1] }
              }
            override def readFloat(sentinel: Double)(using S <:< Float): Double =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Float].toDouble }
              else {
                val v = source.readFloat(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) sentinel
                else { state = fF(state, v.toFloat); state.toDouble }
              }
            def close(): Unit = source.close()
          }
        } else if ((srcType eq JvmType.Double) && (outType eq JvmType.Double)) {
          val fD = f.asInstanceOf[(Double, Double) => Double]
          new Reader[S] {
            private var state: Double           = init.asInstanceOf[Double]
            private var emittedInit             = false
            override def jvmType: JvmType       = JvmType.Double
            def isClosed: Boolean               = source.isClosed
            def read[S1 >: S](sentinel: S1): S1 =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[S1] }
              else {
                val v = source.readDouble(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) sentinel
                else { state = fD(state, v); state.asInstanceOf[S1] }
              }
            override def readDouble(sentinel: Double)(using S <:< Double): Double =
              if (!emittedInit) { emittedInit = true; init.asInstanceOf[Double] }
              else {
                val v = source.readDouble(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) sentinel
                else { state = fD(state, v); state }
              }
            def close(): Unit = source.close()
          }
        } else {
          new Reader[S] {
            private var state: S                = init
            private var emittedInit             = false
            def isClosed: Boolean               = source.isClosed
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
              if (source.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue) return false; i += 1
            }
          } else if (et eq JvmType.Int) {
            while (i < count) {
              if (source.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue) return false; i += 1
            }
          } else if (et eq JvmType.Long) {
            while (i < count) {
              if (source.readLong(Long.MaxValue)(using unsafeEvidence) == Long.MaxValue) return false; i += 1
            }
          } else if (et eq JvmType.Float) {
            while (i < count) {
              if (source.readFloat(Double.MaxValue)(using unsafeEvidence) == Double.MaxValue) return false; i += 1
            }
          } else if (et eq JvmType.Double) {
            while (i < count) {
              if (source.readDouble(Double.MaxValue)(using unsafeEvidence) == Double.MaxValue) return false; i += 1
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
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toByte)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            def close(): Unit = { done = true; source.close() }
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
                val v = source.readInt(Long.MinValue)(using unsafeEvidence)
                if (v == Long.MinValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toInt)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            def close(): Unit = { done = true; source.close() }
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
                val v = source.readLong(Long.MaxValue)(using unsafeEvidence)
                if (v == Long.MaxValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            def close(): Unit = { done = true; source.close() }
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
                val v = source.readFloat(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v.toFloat)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            def close(): Unit = { done = true; source.close() }
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
                val v = source.readDouble(Double.MaxValue)(using unsafeEvidence)
                if (v == Double.MaxValue) {
                  if (buf.size == 0 || buf.size == sizeBeforeFill) { done = true; return sentinel }
                  else { done = true; return buf.toChunk.asInstanceOf[A1] }
                }
                buf.add(v)
              }
              buf.toChunk.asInstanceOf[A1]
            }
            def close(): Unit = { done = true; source.close() }
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
            def close(): Unit = { done = true; source.close() }
          }
        }
      },
      s"${this.render}.sliding($n, $step)"
    )
  }

  /**
   * Opens this stream for manual pull-based consumption within a
   * [[zio.blocks.scope.Scope]]. Use this when you need element-by-element
   * control rather than running through a [[Sink]]. The returned [[Reader]] is
   * closed automatically when the scope closes.
   */
  def start(using scope: Scope): scope.$[Reader[A]] =
    scope.allocate(Resource.acquireRelease(compile(0))(_.close()))

  /** Emits at most the first `n` elements, then closes. */
  def take(n: Long): Stream[E, A] = new Stream.Taken(this, n)

  /**
   * Emits elements while `pred` holds, then closes on the first element where
   * `pred` returns `false`.
   */
  def takeWhile(pred: A => Boolean): Stream[E, A] =
    new Stream.TakenWhile(this, pred)

  /**
   * Applies `f` to each element for side-effects, passing the element through.
   */
  def tapEach(f: A => Unit)(implicit jtA: JvmType.Infer[A]): Stream[E, A] =
    map { a => f(a); a }

  /** Transforms this stream by applying a [[Pipeline]]. */
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B] =
    pipe.applyToStream(this)

  /** Compiles this stream into a [[Reader]] for pull-based evaluation. */
  private[streams] def compile(depth: Int): Reader[A]

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
    new FromReader(() => Reader.fromChunk[A](Chunk.fromIterable(as))(jt), label)
  }

  /**
   * Evaluates `f` and emits its result; captures non-fatal exceptions as
   * errors.
   */
  def attempt[A](f: => A): Stream[Throwable, A] =
    suspend {
      try succeed(f)
      catch { case scala.util.control.NonFatal(e) => fail(e) }
    }

  /**
   * An empty stream that executes `f` for its side-effect, capturing any
   * non-fatal throwable as a typed stream error rather than a defect.
   */
  def attemptEval(f: => Any): Stream[Throwable, Nothing] =
    suspend {
      try { f; empty }
      catch { case scala.util.control.NonFatal(e) => fail(e) }
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

  /** Flattens a stream of streams into a single stream by sequencing. */
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]]): Stream[E, A] =
    streams.flatMap(identity)

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

  /** Creates a stream backed by a [[Chunk]]. */
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromChunkStream(chunk, jt)

  /**
   * Wraps a [[java.io.InputStream]] as a stream of bytes widened to Int
   * (0–255). Closes the stream when done.
   */
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Int] =
    fromAcquireRelease(
      is,
      (s: java.io.InputStream) =>
        try s.close()
        catch { case _: java.io.IOException => () }
    )(s => fromInputStreamUnmanaged(s))

  /**
   * Wraps a [[java.io.InputStream]] as a stream of bytes widened to Int (0–255)
   * without managing its lifecycle. The caller is responsible for closing the
   * stream.
   */
  def fromInputStreamUnmanaged(is: java.io.InputStream): Stream[java.io.IOException, Int] =
    new FromReader(() => Reader.fromInputStream(is), "Stream.fromInputStreamUnmanaged(...)")

  /**
   * Creates a stream backed by an [[Iterable]]. If the iterable has a known
   * size, `knownLength` is set.
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

  /** Creates a stream from a lazily-evaluated [[Iterator]]. */
  def fromIterator[A](it: => Iterator[A]): Stream[Nothing, A] =
    new FromReader(
      () => {
        val iter = it
        new Reader[A] {
          private var exhausted               = false
          def isClosed: Boolean               = exhausted || !iter.hasNext
          def read[A1 >: A](sentinel: A1): A1 =
            if (!exhausted && iter.hasNext) iter.next().asInstanceOf[A1]
            else { exhausted = true; sentinel }
          def close(): Unit = exhausted = true
        }
      },
      "Stream.fromIterator(...)"
    )

  /**
   * Wraps a [[java.io.Reader]] as a stream of Chars. Closes the reader when
   * done.
   */
  def fromJavaReader(r: java.io.Reader): Stream[java.io.IOException, Char] =
    fromAcquireRelease(
      r,
      (w: java.io.Reader) =>
        try w.close()
        catch { case _: java.io.IOException => () }
    )(w => fromJavaReaderUnmanaged(w))

  /**
   * Wraps a [[java.io.Reader]] as a stream of Chars without managing its
   * lifecycle. The caller is responsible for closing.
   */
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[java.io.IOException, Char] =
    new FromReader(() => Reader.fromReader(r), "Stream.fromJavaReaderUnmanaged(...)")

  /**
   * Creates a stream of integers from a Scala [[Range]]. The `knownLength` is
   * set from the range size.
   */
  def fromRange(range: Range): Stream[Nothing, Int] =
    new FromReader(() => Reader.fromRange(range), "Stream.fromRange(...)") {
      override def knownLength: Option[Long] = Some(range.size.toLong)
    }

  /** Creates a stream from a lazily-evaluated [[Reader]] (advanced API). */
  def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A] =
    new FromReader(() => mkReader)

  /**
   * Creates a resource-safe stream from a [[Resource]] managed by a [[Scope]].
   */
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A] =
    new FromResource(resource, use)

  /**
   * Creates a stream of integers from `from` (inclusive) to `until`
   * (exclusive).
   */
  def range(from: Int, until: Int): Stream[Nothing, Int] =
    new FromReader(() => Reader.fromRange(Range(from, until)), s"Stream.range($from, $until)")

  /** Creates an infinite stream that always emits `a`. */
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    new FromReader(() => Reader.repeat[A](a), "Stream.repeat(...)")

  /** Creates a stream that emits a single Boolean. */
  def succeed(a: Boolean): Stream[Nothing, Boolean] =
    new FromReader(() => Reader.singleBoolean(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /**
   * Creates a stream that emits a single Byte. Returns `Stream[Nothing, Int]`
   * because Byte is represented as Int internally to avoid boxing, matching the
   * convention of [[fromInputStream]].
   */
  def succeed(a: Byte): Stream[Nothing, Int] =
    new FromReader(() => Reader.singleByte(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Char. */
  def succeed(a: Char): Stream[Nothing, Char] =
    new FromReader(() => Reader.singleChar(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Double. */
  def succeed(a: Double): Stream[Nothing, Double] =
    new FromReader(() => Reader.singleDouble(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Float. */
  def succeed(a: Float): Stream[Nothing, Float] =
    new FromReader(() => Reader.singleFloat(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Int. */
  def succeed(a: Int): Stream[Nothing, Int] =
    new FromReader(() => Reader.singleInt(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Long. */
  def succeed(a: Long): Stream[Nothing, Long] =
    new FromReader(() => Reader.singleLong(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

  /** Creates a stream that emits a single Short. */
  def succeed(a: Short): Stream[Nothing, Short] =
    new FromReader(() => Reader.singleShort(a), "Stream.succeed(...)") {
      override def knownLength: Option[Long] = Some(1L)
    }

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
    stream.compile(0)

  /**
   * Depth threshold; beyond this, compilation falls back to the flat-array
   * [[Interpreter]] to prevent stack overflow during recursive stream
   * compilation.
   */
  private[streams] inline val DepthCutoff = 100

  /** Recovers from all errors by switching to the stream returned by `f`. */
  private[streams] final class CatchAll[E, E2, A](
    self: Stream[E, A],
    f: E => Stream[E2, A]
  ) extends Stream[E2, A] {
    def render: String                                                   = s"${self.render}.catchAll(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val innerReader = compileToReader(self)
      pipeline.appendRead(new CatchAllReader[E, A](innerReader.asInstanceOf[Reader[A]], f))
    }
    override private[streams] def compile(depth: Int): Reader[A] =
      new CatchAllReader[E, A](self.compile(depth), f)
  }

  /** Catches StreamErrors and switches to a recovery stream. */
  private[streams] final class CatchAllReader[E, A](upstream: Reader[A], f: E => Stream[Any, A]) extends Reader[A] {
    private var current: Reader[?] = upstream
    private var switched           = false
    private val _jvmType: JvmType  = upstream.jvmType

    override def jvmType: JvmType = _jvmType
    def isClosed: Boolean         = current.isClosed

    private def doSwitch(e: StreamError): Unit = {
      switched = true
      try current.close()
      catch { case _: Throwable => () }
      current = compileToReader(f(e.value.asInstanceOf[E]))
    }

    private def cur: Reader[A] = current.asInstanceOf[Reader[A]]

    def read[A1 >: A](sentinel: A1): A1 =
      if (!switched) {
        try cur.read(sentinel)
        catch { case e: StreamError => doSwitch(e); cur.read(sentinel) }
      } else cur.read(sentinel)

    override def readInt(sentinel: Long)(using A <:< Int): Long =
      if (!switched) {
        try current.readInt(sentinel)(using unsafeEvidence)
        catch { case e: StreamError => doSwitch(e); current.readInt(sentinel)(using unsafeEvidence) }
      } else current.readInt(sentinel)(using unsafeEvidence)

    override def readLong(sentinel: Long)(using A <:< Long): Long =
      if (!switched) {
        try current.readLong(sentinel)(using unsafeEvidence)
        catch { case e: StreamError => doSwitch(e); current.readLong(sentinel)(using unsafeEvidence) }
      } else current.readLong(sentinel)(using unsafeEvidence)

    override def readFloat(sentinel: Double)(using A <:< Float): Double =
      if (!switched) {
        try current.readFloat(sentinel)(using unsafeEvidence)
        catch { case e: StreamError => doSwitch(e); current.readFloat(sentinel)(using unsafeEvidence) }
      } else current.readFloat(sentinel)(using unsafeEvidence)

    override def readDouble(sentinel: Double)(using A <:< Double): Double =
      if (!switched) {
        try current.readDouble(sentinel)(using unsafeEvidence)
        catch { case e: StreamError => doSwitch(e); current.readDouble(sentinel)(using unsafeEvidence) }
      } else current.readDouble(sentinel)(using unsafeEvidence)

    override def skip(n: Long): Unit = current.skip(n)
    def close(): Unit                = try current.close()
    catch { case _: Throwable => () }
  }

  /** Recovers from non-fatal defects matching `f`. */
  private[streams] final class CatchDefect[E, A](
    self: Stream[E, A],
    f: PartialFunction[Throwable, Stream[E, A]]
  ) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.catchDefect(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      val innerReader = compileToReader(self)
      pipeline.appendRead(new CatchDefectReader[E, A](innerReader.asInstanceOf[Reader[A]], f))
    }
    override private[streams] def compile(depth: Int): Reader[A] =
      new CatchDefectReader[E, A](self.compile(depth), f)
  }

  /** Catches non-fatal defects and switches to a recovery stream. */
  private[streams] final class CatchDefectReader[E, A](
    upstream: Reader[A],
    f: PartialFunction[Throwable, Stream[E, A]]
  ) extends Reader[A] {
    private var current: Reader[?] = upstream
    private var switched           = false
    private val _jvmType: JvmType  = upstream.jvmType

    override def jvmType: JvmType = _jvmType
    def isClosed: Boolean         = current.isClosed

    private def cur: Reader[A] = current.asInstanceOf[Reader[A]]

    private def trySwitch(t: Throwable): Boolean =
      if (scala.util.control.NonFatal(t) && !t.isInstanceOf[StreamError] && f.isDefinedAt(t)) {
        switched = true
        try current.close()
        catch { case _: Throwable => () }
        current = compileToReader(f(t))
        true
      } else false

    def read[A1 >: A](sentinel: A1): A1 =
      if (!switched) {
        try cur.read(sentinel)
        catch { case t: Throwable => if (trySwitch(t)) cur.read(sentinel) else throw t }
      } else cur.read(sentinel)

    override def readInt(sentinel: Long)(using A <:< Int): Long =
      if (!switched) {
        try current.readInt(sentinel)(using unsafeEvidence)
        catch { case t: Throwable => if (trySwitch(t)) current.readInt(sentinel)(using unsafeEvidence) else throw t }
      } else current.readInt(sentinel)(using unsafeEvidence)

    override def readLong(sentinel: Long)(using A <:< Long): Long =
      if (!switched) {
        try current.readLong(sentinel)(using unsafeEvidence)
        catch { case t: Throwable => if (trySwitch(t)) current.readLong(sentinel)(using unsafeEvidence) else throw t }
      } else current.readLong(sentinel)(using unsafeEvidence)

    override def readFloat(sentinel: Double)(using A <:< Float): Double =
      if (!switched) {
        try current.readFloat(sentinel)(using unsafeEvidence)
        catch { case t: Throwable => if (trySwitch(t)) current.readFloat(sentinel)(using unsafeEvidence) else throw t }
      } else current.readFloat(sentinel)(using unsafeEvidence)

    override def readDouble(sentinel: Double)(using A <:< Double): Double =
      if (!switched) {
        try current.readDouble(sentinel)(using unsafeEvidence)
        catch { case t: Throwable => if (trySwitch(t)) current.readDouble(sentinel)(using unsafeEvidence) else throw t }
      } else current.readDouble(sentinel)(using unsafeEvidence)

    override def skip(n: Long): Unit = current.skip(n)
    def close(): Unit                = try current.close()
    catch { case _: Throwable => () }
  }

  /** Emits all elements of `self` followed by all elements of `that`. */
  private[streams] final class Concatenated[E, A](self: Stream[E, A], that: Stream[E, A]) extends Stream[E, A] {
    def render: String                     = s"${self.render} ++ ${that.render}"
    override def knownLength: Option[Long] =
      for { a <- self.knownLength; b <- that.knownLength } yield a + b
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead { src =>
        src.asInstanceOf[Reader[A]].concat(() => compileToReader(that))
      }
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val r1 = self.compile(depth)
      r1.concat(() => compileToReader(that))
    }
  }

  /** Defers stream construction until run-time. */
  private[streams] final class Deferred[E, A](mkStream: () => Stream[E, A]) extends Stream[E, A] {
    def render: String                                                   = "Stream.suspend(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = mkStream().compileInterpreter(pipeline)
    override private[streams] def compile(depth: Int): Reader[A]         = mkStream().compile(depth)
  }

  /** Skips the first `n` elements. */
  private[streams] final class Dropped[E, A](self: Stream[E, A], n: Long) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.drop($n)"
    override def knownLength: Option[Long]                               = self.knownLength.map(l => math.max(0L, l - math.max(0L, n)))
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead { r => if (!r.setSkip(n)) r.skip(n); r }
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val r = self.compile(depth)
      if (!r.setSkip(n)) r.skip(n)
      r
    }
  }

  /** A dying stream source that always throws the given Throwable. */
  private[streams] final class DyingReader(t: Throwable) extends Reader[Nothing] {
    def isClosed: Boolean                     = false
    def read[A1 >: Nothing](sentinel: A1): A1 = throw t
    override def readByte(): Int              = throw t
    override def skip(n: Long): Unit          = ()
    def close(): Unit                         = ()
  }

  /** Wraps a stream with a finalizer that runs on close. */
  private[streams] final class Ensuring[E, A](self: Stream[E, A], finalizer: => Unit) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.ensuring(...)"
    override def knownLength: Option[Long]                               = self.knownLength
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(src =>
        new Reader.DelegatingReader[Any](src) {
          override def close(): Unit = try src.close()
          finally finalizer
        }
      )
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val src = self.compile(depth)
      new Reader.DelegatingReader[A](src) {
        override def close(): Unit = try src.close()
        finally finalizer
      }
    }
  }

  /** Transforms the error channel with `f`. */
  private[streams] final class ErrorMapped[E, E2, A](self: Stream[E, A], f: E => E2) extends Stream[E2, A] {
    def render: String                                                   = s"${self.render}.mapError(...)"
    override def knownLength: Option[Long]                               = self.knownLength
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(r => new ErrorMappedReader[E, A](r.asInstanceOf[Reader[A]], f))
    }
    override private[streams] def compile(depth: Int): Reader[A] =
      new ErrorMappedReader[E, A](self.compile(depth), f)
  }

  /** Transforms StreamError values with `f` during pull-based evaluation. */
  private[streams] final class ErrorMappedReader[E, A](upstream: Reader[A], f: E => Any) extends Reader[A] {
    override def jvmType: JvmType       = upstream.jvmType
    def isClosed: Boolean               = upstream.isClosed
    def read[A1 >: A](sentinel: A1): A1 =
      try upstream.read(sentinel)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readInt(sentinel: Long)(using A <:< Int): Long =
      try upstream.readInt(sentinel)(using unsafeEvidence)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readLong(sentinel: Long)(using A <:< Long): Long =
      try upstream.readLong(sentinel)(using unsafeEvidence)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readFloat(sentinel: Double)(using A <:< Float): Double =
      try upstream.readFloat(sentinel)(using unsafeEvidence)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readDouble(sentinel: Double)(using A <:< Double): Double =
      try upstream.readDouble(sentinel)(using unsafeEvidence)
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def readByte(): Int =
      try upstream.readByte()
      catch { case e: StreamError => throw new StreamError(f(e.value.asInstanceOf[E])) }
    override def skip(n: Long): Unit = upstream.skip(n)
    def close(): Unit                = upstream.close()
    override def reset(): Unit       = throw new UnsupportedOperationException("ErrorMapped does not support reset")
  }

  /** A failed stream source that always throws the given StreamError. */
  private[streams] final class FailedReader(se: StreamError) extends Reader[Nothing] {
    def isClosed: Boolean                     = false
    def read[A1 >: Nothing](sentinel: A1): A1 = throw se
    override def readByte(): Int              = throw se
    override def skip(n: Long): Unit          = ()
    def close(): Unit                         = ()
  }

  /** Emits only elements satisfying `pred`. */
  private[streams] final class Filtered[E, A](
    self: Stream[E, A],
    pred: A => Boolean,
    jtA: JvmType.Infer[A]
  ) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.filter(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addFilter(Interpreter.laneOf(jtA.jvmType))(pred)
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[A]]
      val sourceReader = self.compile(depth + 1)
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
  ) extends Stream[E, B] {
    def render: String                                                   = s"${self.render}.collect(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
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
    override private[streams] def compile(depth: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      val sourceReader = self.compile(depth + 1)
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
  ) extends Stream[E2, B] {
    def render: String                                                   = s"${self.render}.flatMap(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addPush(Interpreter.laneOf(jtA.jvmType))(f)
    }
    override private[streams] def compile(depth: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      val sourceReader = self.compile(depth + 1)
      val inLane       = Interpreter.laneOf(jtA.jvmType)
      sourceReader match {
        case p: Interpreter =>
          p.addPush(inLane)(f)
          p.seal()
          p.asInstanceOf[Reader[B]]
        case r =>
          val compileInner: AnyRef => Reader[Any] = (stream: AnyRef) =>
            stream.asInstanceOf[Stream[Any, Any]].compile(0).asInstanceOf[Reader[Any]]
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
            override def close(): Unit = try src.close()
            finally release(r)
          }
        )
      } catch {
        case t: Throwable =>
          try release(r)
          catch { case _: Throwable => () }
          throw t
      }
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val r = acquire
      try {
        val src = use(r).compile(depth)
        new Reader.DelegatingReader[A](src) {
          override def close(): Unit = try src.close()
          finally release(r)
        }
      } catch {
        case t: Throwable =>
          try release(r)
          catch { case _: Throwable => () }
          throw t
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
    override private[streams] def compile(depth: Int): Reader[A] =
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
    override private[streams] def compile(depth: Int): Reader[A] = mkReader()
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
            override def close(): Unit = try src.close()
            finally os.close()
          }
        )
      } catch {
        case t: Throwable =>
          try os.close()
          catch { case _: Throwable => () }
          throw t
      }
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val os    = Scope.global.open()
      val scope = os.scope
      val r     = scope.leak(scope.allocate(resource))
      try {
        val src = use(r).compile(depth)
        new Reader.DelegatingReader[A](src) {
          override def close(): Unit = try src.close()
          finally os.close()
        }
      } catch {
        case t: Throwable =>
          try os.close()
          catch { case _: Throwable => () }
          throw t
      }
    }
  }

  /** Lazily maps elements with `f`. */
  private[streams] final class Mapped[E, A, B](
    self: Stream[E, A],
    f: A => B,
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ) extends Stream[E, B] {
    def render: String                                                   = s"${self.render}.map(...)"
    override def knownLength: Option[Long]                               = self.knownLength
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.addMap(Interpreter.laneOf(jtA.jvmType), Interpreter.outLaneOf(jtB.jvmType))(f)
    }
    override private[streams] def compile(depth: Int): Reader[B] = {
      if (depth >= Stream.DepthCutoff)
        return Interpreter.fromStream(this).asInstanceOf[Reader[B]]
      val sourceReader = self.compile(depth + 1)
      val inLane       = Interpreter.laneOf(jtA.jvmType)
      val outLane      = Interpreter.outLaneOf(jtB.jvmType)
      sourceReader match {
        case p: Interpreter =>
          p.addMap(inLane, outLane)(f)
          p.seal()
          p.asInstanceOf[Reader[B]]
        case r =>
          val outType = jtB.jvmType
          val reader  = (inLane: @scala.annotation.switch) match {
            case 0 => new Reader.MappedInt(r, f.asInstanceOf[AnyRef], outType)
            case 1 => new Reader.MappedLong(r, f.asInstanceOf[AnyRef], outType)
            case 2 => new Reader.MappedFloat(r, f.asInstanceOf[AnyRef], outType)
            case 3 => new Reader.MappedDouble(r, f.asInstanceOf[AnyRef], outType)
            case _ => new Reader.MappedRef(r, f.asInstanceOf[AnyRef], outType)
          }
          reader.asInstanceOf[Reader[B]]
      }
    }
  }

  /** Restarts the stream on clean close. */
  private[streams] final class Repeated[E, A](self: Stream[E, A]) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.repeated"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(r => Reader.repeated[A](r.asInstanceOf[Reader[A]]))
    }
    override private[streams] def compile(depth: Int): Reader[A] =
      Reader.repeated[A](self.compile(depth))
  }

  /** Emits at most the first `n` elements. */
  private[streams] final class Taken[E, A](self: Stream[E, A], n: Long) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.take($n)"
    override def knownLength: Option[Long]                               = self.knownLength.map(l => math.max(0L, math.min(n, l)))
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(r => if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r)
    }
    override private[streams] def compile(depth: Int): Reader[A] = {
      val r = self.compile(depth)
      (if (!r.setLimit(n)) Reader.withSkipLimit(r, 0, n) else r).asInstanceOf[Reader[A]]
    }
  }

  /** Emits elements while `pred` holds. */
  private[streams] final class TakenWhile[E, A](self: Stream[E, A], pred: A => Boolean) extends Stream[E, A] {
    def render: String                                                   = s"${self.render}.takeWhile(...)"
    private[streams] def compileInterpreter(pipeline: Interpreter): Unit = {
      self.compileInterpreter(pipeline)
      pipeline.wrapLastRead(r => new Reader.TakenWhile[A](r.asInstanceOf[Reader[A]], pred))
    }
    override private[streams] def compile(depth: Int): Reader[A] =
      new Reader.TakenWhile[A](self.compile(depth), pred)
  }

  /**
   * Zips this stream with `that`, pairing elements positionally. Shorter stream
   * determines length. Uses [[zio.blocks.combinators.Tuples]] for flattened
   * composition: `a && b && c` produces a `Stream` of `(A, B, C)`.
   */
  extension [E, A](self: Stream[E, A]) {
    def &&[E2, B, C](that: Stream[E2, B])(using t: Tuples.Tuples[A, B] { type Out = C }): Stream[E | E2, C] =
      Stream.fromReader {
        val left  = compileToReader(self.asInstanceOf[Stream[Any, Any]])
        val right =
          try compileToReader(that.asInstanceOf[Stream[Any, Any]])
          catch {
            case t: Throwable =>
              try left.close()
              catch { case s: Throwable => t.addSuppressed(s) };
              throw t
          }
        new io.Reader[C] {
          def isClosed                        = left.isClosed || right.isClosed
          def read[O1 >: C](sentinel: O1): O1 = {
            val l = left.read[Any](EndOfStream)
            if (l.asInstanceOf[AnyRef] eq EndOfStream) { right.close(); return sentinel }
            val r = right.read[Any](EndOfStream)
            if (r.asInstanceOf[AnyRef] eq EndOfStream) { left.close(); return sentinel }
            t.combine(l.asInstanceOf[A], r.asInstanceOf[B]).asInstanceOf[O1]
          }
          def close(): Unit = {
            var firstError: Throwable = null
            try left.close()
            catch { case t: Throwable => firstError = t }
            try right.close()
            catch {
              case t: Throwable =>
                if (firstError == null) firstError = t
                else firstError.addSuppressed(t)
            }
            if (firstError != null) throw firstError
          }
        }
      }
  }
}
