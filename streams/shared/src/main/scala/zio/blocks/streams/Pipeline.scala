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
 * A reusable stream transformation that turns elements of type `In` into
 * elements of type `Out`, as a value: define it once, compose it with
 * [[andThen]], and apply it to any stream or any sink. Use it when the same
 * steps (filtering, mapping, limiting) apply in several places.
 *
 * Applied to a stream with `Stream#via` (or [[applyToStream]]), it transforms
 * the stream's output. Applied to a sink with [[andThenSink]] (or
 * [[applyToSink]]), it transforms the sink's input. The two give the same
 * result: `stream.via(p).run(sink)` and `stream.run(p.andThenSink(sink))`.
 * Building a pipeline runs nothing, and each run gets fresh state, such as the
 * counter of `Pipeline.take`.
 *
 * {{{
 * val evensDoubled: Pipeline[Int, Int] =
 *   Pipeline.filter[Int](_ % 2 == 0).andThen(Pipeline.map[Int, Int](_ * 2))
 *
 * Stream(1, 2, 3, 4).via(evensDoubled).runCollect            // Right(Chunk(4,8))
 * Stream(1, 2, 3, 4).run(evensDoubled.andThenSink(Sink.sumInt)) // Right(12)
 * }}}
 *
 * @tparam In
 *   Input element type.
 * @tparam Out
 *   Output element type.
 *
 * @see
 *   [[https://zioblocks.com/reference/streams/core/pipeline/ Pipeline]]
 */
abstract class Pipeline[-In, +Out] {

  /**
   * Composes this pipeline with `that` into one pipeline that applies this one
   * first and `that` to its output. Composition is associative, and
   * [[Pipeline.identity]] is neutral on either side.
   *
   * {{{
   * val trimmed = Pipeline.map[String, String](_.trim)
   * val lengths = Pipeline.map[String, Int](_.length)
   *
   * Stream(" Zionomicon ", "Scala with Cats").via(trimmed.andThen(lengths)).runCollect
   * // Right(Chunk(10,15))
   * }}}
   */
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C] =
    new Pipeline.Composed(this, that)

  /**
   * Returns a sink that passes its input through this pipeline and then into
   * `sink`; an alias for [[applyToSink]]. Running it gives the same result as
   * applying this pipeline to the stream and running `sink`, and `sink`'s error
   * and result types do not change.
   *
   * {{{
   * val lengths = Pipeline.map[String, Int](_.length)
   *
   * Stream("Zionomicon", "Scala with Cats").run(lengths.andThenSink(Sink.collectAll[Int]))
   * // Right(Chunk(10,15))
   * }}}
   */
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z] =
    applyToSink(sink)

  /**
   * Returns a sink that passes its input through this pipeline and then into
   * `sink`, keeping `sink`'s error and result types. [[andThenSink]] is the
   * alias that reads better in a chain. The transformed input is closed on
   * every exit.
   */
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]

  /**
   * Applies this pipeline to `stream`; `stream.via(this)` is the same. The
   * error type does not change, since a pipeline's callbacks cannot add typed
   * errors (their exceptions are defects).
   *
   * {{{
   * val firstTwo = Pipeline.take[String](2)
   *
   * firstTwo.applyToStream(Stream("Zionomicon", "Scala with Cats", "Programming in Scala")).runCollect
   * // Right(Chunk(Zionomicon,Scala with Cats))
   * }}}
   */
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]

  private[streams] def fuseAsyncReader(reader: Reader.AsyncReader[_]): Reader.AsyncReader[Out] = null
}

/**
 * Constructors for [[Pipeline]]: `map`, `filter`, `collect` and their `*Async`
 * forms, `take`, `drop`, `chunked`, `buffer` and `identity`.
 */
object Pipeline {

  /**
   * Inserts a bounded buffer of `n` elements between producer and consumer, as
   * [[Stream#buffer]] does: elements and order are kept, and closing or
   * cancelling downstream closes the buffer and upstream.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n < 1`
   */
  def buffer[A](n: Int): Pipeline[A, A] = {
    require(n >= 1, s"buffer requires n >= 1, got n=$n")
    new BufferPipeline(n)
  }

  /**
   * Groups consecutive elements into `Chunk`s of `n`, in order; the last chunk
   * holds what is left and may be smaller, and no chunk is empty. Each run
   * starts a fresh group.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n < 1`
   */
  def chunked[A](n: Int): Pipeline[A, Chunk[A]] = {
    require(n >= 1, s"chunked requires n >= 1, got n=$n")
    new ChunkedPipeline(n)
  }

  /**
   * Emits `pf(a)` for each element `a` where `pf` is defined and drops the
   * rest, in order. `pf` is called once per element (through `applyOrElse`),
   * and an exception it throws is a defect.
   */
  def collect[A, B](
    pf: PartialFunction[A, B]
  )(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] = new CollectPipeline(pf, jtB)

  /**
   * The asynchronous form of [[collect]], taking a total function: runs `f` on
   * each element, one at a time and in order, emitting the value of each `Some`
   * and dropping each `None`. A failed `Async` is a defect, and cancellation
   * reaches a pending call.
   */
  def collectAsync[A, B](f: A => Async[Option[B]])(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] = new Pipeline[A, B] {
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] = runViaSink(this, sink)
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B]  = stream.collectAsync(f)
  }

  /**
   * Skips the first `n` elements of each run and emits the rest; `n <= 0` skips
   * nothing.
   */
  def drop[A](n: Long): Pipeline[A, A] = new DropPipeline(n)

  /**
   * Emits the elements for which `pred` returns `true`, in order. `pred` runs
   * once per element, and an exception it throws is a defect.
   */
  def filter[A](pred: A => Boolean): Pipeline[A, A] =
    new FilterPipeline(pred)

  /**
   * The asynchronous form of [[filter]]: tests elements with `f` one at a time
   * and in order, emitting those for which it yields `true`. A failed `Async`
   * is a defect, and cancellation reaches a pending call.
   */
  def filterAsync[A](f: A => Async[Boolean]): Pipeline[A, A] =
    new Pipeline[A, A] {
      def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] = runViaSink(this, sink)
      def applyToStream[E](stream: Stream[E, A]): Stream[E, A]  = stream.filterAsync(f)
    }

  /**
   * The pipeline that changes nothing: applying it returns the same stream or
   * sink instance, and it is neutral for [[Pipeline#andThen]]. Useful as the
   * starting point when a pipeline is built step by step.
   */
  def identity[A]: Pipeline[A, A] = new Pipeline[A, A] {
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] = sink
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A]  = stream
  }

  /**
   * Overload of `map` for a function that never returns, such as one that
   * always throws; it keeps type inference working for `Nothing` results. An
   * exception thrown by `f` is a defect.
   */
  def map[A](f: A => Nothing)(implicit
    dummy: DummyImplicit
  ): Pipeline[A, Nothing] = {
    val _ = dummy
    new MapPipeline[A, Nothing](f, JvmType.Infer.boxed[Nothing])
  }

  /**
   * Transforms each element with `f`, in order, one output per input. An
   * exception thrown by `f` is a defect. The `JvmType.Infer` evidence, which
   * the compiler supplies, lets a primitive result travel unboxed.
   */
  def map[A, B](f: A => B)(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] =
    new MapPipeline(f, jtB)

  /**
   * The asynchronous form of `map`: transforms each element with `f`, one at a
   * time and in order, awaiting each call before pulling the next element. A
   * failed `Async` is a defect, and cancellation reaches a pending call.
   */
  def mapAsync[A, B](f: A => Async[B])(implicit
    jtB: JvmType.Infer[B]
  ): Pipeline[A, B] =
    new Pipeline[A, B] {
      def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] = sink.contramapAsync[B, A](f)(jtB)
      def applyToStream[E](stream: Stream[E, A]): Stream[E, B]  = stream.mapAsync(f)
    }

  /**
   * Emits at most the first `n` elements of each run, then closes upstream;
   * `n <= 0` emits nothing.
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
