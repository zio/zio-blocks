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

import zio.blocks.async.{Async, _}
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader

/**
 * A transformation from a `Stream[_, In]` to a `Stream[_, Out]`. Pipelines
 * compose sequentially via [[andThen]] (with [[Pipeline.identity]] as the
 * identity) and can be applied to [[Stream]]s via [[applyToStream]] or to
 * [[Sink]]s via [[applyToSink]].
 *
 * @tparam In
 *   Input element type.
 * @tparam Out
 *   Output element type.
 */
abstract class Pipeline[-In, +Out] {

  /**
   * Composes this pipeline with `that`, applying `this` first and `that` to its
   * output. Composition builds a lazy pipeline value; it does not run either
   * pipeline or share per-run state. [[Pipeline.identity]] is the left and
   * right identity.
   */
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C] =
    new Pipeline.Composed(this, that)

  /**
   * Pre-composes this pipeline with `sink`. Running the returned sink is
   * equivalent to transforming the input with this pipeline and then running
   * `sink`; the sink's typed error and result types are unchanged. Derived
   * readers are closed on completion, failure, or cancellation.
   *
   * This is an alias for [[applyToSink]].
   */
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z] =
    applyToSink(sink)

  /**
   * Returns a sink that transforms its input with this pipeline before passing
   * it to `sink`. Construction is lazy and preserves `sink`'s typed error and
   * result types. Implementations must release the transformed reader, and
   * therefore its upstream resources, on every exit.
   */
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]

  /**
   * Returns the lazy stream obtained by applying this transformation to
   * `stream`. The stream's typed error type is preserved; transformation
   * callbacks cannot add typed errors. Resources and cancellation remain
   * governed by the returned stream and its source.
   */
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]

  private[streams] def fuseAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[Out] = null
}

/**
 * Companion object for [[Pipeline]]. Provides factory constructors for common
 * transformations: `map`, `filter`, `take`, `drop`, `collect`, and `identity`.
 */
object Pipeline {

  /**
   * Inserts a bounded buffer of `n` elements between upstream production and
   * downstream consumption. This is a materialization/asynchronous boundary: it
   * preserves elements and order while decoupling producer and consumer.
   * Closing or cancelling downstream closes the buffer and upstream.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n < 1`
   */
  def buffer[A](n: Int): Pipeline[A, A] = {
    require(n >= 1, s"buffer requires n >= 1, got n=$n")
    new BufferPipeline(n)
  }

  /**
   * Groups consecutive elements into non-empty `Chunk`s of at most `n`
   * elements. Full chunks have size `n`; on normal exhaustion the final chunk
   * may be smaller. Grouping spans upstream read boundaries, preserves order,
   * and uses fresh accumulation state for each materialization.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n < 1`
   */
  def chunked[A](n: Int): Pipeline[A, Chunk[A]] = {
    require(n >= 1, s"chunked requires n >= 1, got n=$n")
    new ChunkedPipeline(n)
  }

  /**
   * Applies `pf` once per input via `applyOrElse`, emitting only defined
   * results while preserving order. Exceptions thrown while testing or applying
   * `pf` are defects, not typed stream errors. Runtime type evidence selects
   * specialized primitive reader paths.
   */
  def collect[A, B](
    pf: PartialFunction[A, B]
  )(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] = new CollectPipeline(pf, jtB)

  /**
   * Asynchronously evaluates each element, sequentially and in input order,
   * emitting the value of `Some` and dropping `None`. A failed or defective
   * `Async` becomes a stream defect, and cancellation of a suspended callback
   * is propagated. Runtime type evidence selects specialized reader paths.
   */
  def collectAsync[A, B](f: A => Async[Option[B]])(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] = new Pipeline[A, B] {
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] = runViaSink(this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B]  = stream.collectAsync(f)
  }

  /**
   * Skips the first `n` elements of each run, then emits the rest unchanged.
   * Non-positive values skip nothing. Counting state is fresh for every stream
   * or sink materialization.
   */
  def drop[A](n: Long): Pipeline[A, A] = new DropPipeline(n)

  /**
   * Emits elements satisfying `pred`, in input order. The predicate is
   * evaluated once per element; thrown exceptions are defects rather than typed
   * stream errors. Runtime type evidence preserves primitive specialization.
   */
  def filter[A](pred: A => Boolean): Pipeline[A, A] =
    new FilterPipeline(pred)

  /**
   * Asynchronously tests each element, sequentially and in input order,
   * emitting it only when `f` yields `true`. A failed or defective `Async`
   * becomes a stream defect, and cancellation of a suspended predicate is
   * propagated. Runtime type evidence preserves primitive specialization.
   */
  def filterAsync[A](f: A => Async[Boolean]): Pipeline[A, A] =
    new Pipeline[A, A] {
      def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] = runViaSink(this, sink)
      def applyToStream[E](stream: Stream[E, A]): Stream[E, A]  = stream.filterAsync(f)
    }

  /**
   * Passes every element through unchanged, preserving order, errors, and
   * resource behavior. It is the identity for [[Pipeline.andThen]] and returns
   * its input stream or sink unchanged, preserving any specialized reader path.
   */
  def identity[A]: Pipeline[A, A] = new Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] = sink
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A]  = stream
  }

  /**
   * Maps each element lazily with a function whose result is `Nothing`. This
   * overload preserves bottom-type inference and uses a boxed output lane.
   * Thrown exceptions are defects rather than typed stream errors.
   */
  def map[A](f: A => Nothing)(implicit
    dummy: DummyImplicit
  ): Pipeline[A, Nothing] = {
    val _ = dummy
    new MapPipeline[A, Nothing](f, JvmType.Infer.boxed[Nothing])
  }

  /**
   * Lazily transforms each element with `f`, preserving input order and
   * cardinality. Thrown exceptions are defects rather than typed stream errors.
   * Runtime type evidence selects specialized primitive input and output reader
   * paths.
   */
  def map[A, B](f: A => B)(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] =
    new MapPipeline(f, jtB)

  /**
   * Asynchronously transforms each element, sequentially and in input order.
   * Each callback is awaited before the next element is processed. A failed or
   * defective `Async` fails the stream as a defect rather than through its
   * typed error channel; cancellation of a suspended callback is propagated.
   * Runtime type evidence selects specialized primitive reader paths.
   */
  def mapAsync[A, B](f: A => Async[B])(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] =
    new Pipeline[A, B] {
      def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] = sink.contramapAsync[B, A](f)(jtB)
      def applyToStream[E](stream: Stream[E, A]): Stream[E, B]  = stream.mapAsync(f)
    }

  /**
   * Emits at most the first `n` elements of each run and then closes upstream
   * early. Non-positive values produce an empty stream. Counting state is fresh
   * for every stream or sink materialization.
   */
  def take[A](n: Long): Pipeline[A, A] = new TakePipeline(n)

  /**
   * Pipeline that applies a partial function, emitting only defined results.
   */
  private[streams] final class CollectPipeline[A, B](
    pf: PartialFunction[A, B],
    jtB: JvmType.Infer[B]
  ) extends Pipeline[A, B] {
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, B, E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B] =
      new Stream.Collected(stream, pf, jtB)
  }

  /** Composed pipeline: applies `self` then `that`. */
  private[streams] final class Composed[A, B, C](
    self: Pipeline[A, B],
    that: Pipeline[B, C]
  ) extends Pipeline[A, C] {
    def applyToSink[E, Z](sink: Sink[E, C, Z]): Sink[E, A, Z] = {
      val midSink: Sink[E, B, Z] = that.applyToSink[E, Z](sink)
      self.applyToSink[E, Z](midSink)
    }
    def applyToStream[E](stream: Stream[E, A]): Stream[E, C] =
      that.applyToStream[E](self.applyToStream[E](stream))
    override private[streams] def fuseAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[C] = {
      val first = self.fuseAsyncReader(reader)
      if (first eq null) null else that.fuseAsyncReader(first)
    }
  }

  /** Pipeline that skips the first `n` elements. */
  private[streams] final class DropPipeline[A](n: Long) extends Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Dropped(stream, n)
  }

  /** Pipeline that buffers up to `n` elements. */
  private[streams] final class BufferPipeline[A](n: Int) extends Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Buffered(stream, n)
  }

  /** Pipeline that groups elements into fixed-size chunks. */
  private[streams] final class ChunkedPipeline[A](n: Int) extends Pipeline[A, Chunk[A]] {
    def applyToSink[E, Z](sink: Sink[E, Chunk[A], Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, Chunk[A], E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, Chunk[A]] =
      stream.chunked(n)
  }

  /** Pipeline that emits only elements satisfying `pred`. */
  private[streams] final class FilterPipeline[A](pred: A => Boolean) extends Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Filtered(stream, pred)
    override private[streams] def fuseAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[A] =
      AsyncInterpreter.fuseFilter[A](reader, reader.jvmType, pred)
  }

  /** Pipeline that transforms each element with `f`. */
  private[streams] final class MapPipeline[A, B](f: A => B, jtB: JvmType.Infer[B]) extends Pipeline[A, B] {
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] =
      sink.contramap[B, A](f)(jtB)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B] =
      Stream.mapped(stream, f, jtB)
    override private[streams] def fuseAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[B] =
      AsyncInterpreter.fuseMap[A, B](reader, reader.jvmType, jtB.jvmType, f)
  }

  /**
   * Sink adapter: applies a pipeline to the reader then drains with the
   * downstream sink.
   */
  private[streams] final class RunViaSink[A, B, E, Z](
    pipe: Pipeline[A, B],
    sink: Sink[E, B, Z]
  ) extends Sink[E, A, Z] {
    private[streams] def drain(reader: Reader.SyncReader[_]): Z = {
      val input               = Reader.borrowed(reader.asInstanceOf[Reader.SyncReader[A]])
      val synthStream         = Stream.fromReader[E, A](input)
      val piped: Stream[E, B] = pipe.applyToStream[E](synthStream)
      val pipedReader         = Sink.toSyncReader(Stream.compileToReader(piped))
      var result: Z           = null.asInstanceOf[Z]
      var failure: Throwable  = null
      try result = sink.drain(pipedReader)
      catch { case cause: Throwable => failure = cause }
      try pipedReader.close()
      catch {
        case closeFailure: Throwable =>
          failure = StreamError.attachCleanupReplay(failure, closeFailure)
      }
      if (failure ne null) throw failure
      result
    }

    private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] = {
      var useFailure: Throwable                                     = null
      def drainDerived(acquire: => Reader.AsyncReader[B]): Async[Z] =
        Async.bracketSync[Reader.AsyncReader[B], Z](
          () => acquire,
          async => {
            val effect =
              try sink.drain(async)
              catch { case cause: Throwable => Async.fail(cause) }
            effect.catchAll { cause =>
              useFailure = cause
              cause match {
                case error: StreamError if error.isTrusted => Async.failTrusted(error)
                case _                                     => Async.fail(cause)
              }
            }
          },
          async =>
            async.close().catchAll { closeFailure =>
              if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
              else {
                StreamError.attachCleanupReplay(useFailure, closeFailure)
                Async.succeed(())
              }
            }
        )

      val syncInput = Reader.borrowedSync(reader.asInstanceOf[Reader.AsyncReader[A]])
      if (syncInput ne null) {
        val synthStream         = Stream.fromReader[E, A](syncInput)
        val piped: Stream[E, B] = pipe.applyToStream[E](synthStream)
        return drainDerived(
          Stream.compileToReader(piped) match {
            case sync: Reader.SyncReader[B @unchecked]   => sync.toAsync
            case async: Reader.AsyncReader[B @unchecked] => async
          }
        )
      }
      val fused = pipe.fuseAsyncReader(reader)
      if (fused ne null) return drainDerived(fused)
      val input               = Reader.borrowed(reader.asInstanceOf[Reader.AsyncReader[A]])
      val synthStream         = Stream.fromReader[E, A](input)
      val piped: Stream[E, B] = pipe.applyToStream[E](synthStream)
      drainDerived(
        Stream.compileToReader(piped) match {
          case sync: Reader.SyncReader[B @unchecked]   => sync.toAsync
          case async: Reader.AsyncReader[B @unchecked] => async
        }
      )
    }

    private[streams] override def drainAsync(reader: Reader.SyncReader[_]): Async[Z] = {
      val input               = Reader.borrowed(reader.asInstanceOf[Reader.SyncReader[A]])
      val synthStream         = Stream.fromReader[E, A](input)
      val piped: Stream[E, B] = pipe.applyToStream[E](synthStream)
      Stream.compileToReader(piped) match {
        case pipedReader: Reader.SyncReader[B @unchecked] =>
          Async.bracketSync[Reader.SyncReader[B], Z](
            () => pipedReader,
            derived => sink.drainAsync(derived),
            derived =>
              try { derived.close(); Async.succeed(()) }
              catch { case cause: Throwable => Async.fail(cause) }
          )
        case pipedReader: Reader.AsyncReader[B @unchecked] =>
          Async.bracketSync[Reader.AsyncReader[B], Z](
            () => pipedReader,
            derived => sink.drain(derived),
            derived => derived.close()
          )
      }
    }
  }

  /** Pipeline that passes through at most `n` elements. */
  private[streams] final class TakePipeline[A](n: Long) extends Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Taken(stream, n)
  }

  private[streams] def runViaSink[A, B, E, Z](
    pipe: Pipeline[A, B],
    sink: Sink[E, B, Z]
  ): Sink[E, A, Z] =
    new RunViaSink(pipe, sink)
}
