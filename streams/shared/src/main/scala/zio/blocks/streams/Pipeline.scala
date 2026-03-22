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

  /** Composes this pipeline with `that`, applying `this` first, then `that`. */
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C] =
    new Pipeline.Composed(this, that)

  /** Alias for `applyToSink`. Composes this pipeline with a downstream sink. */
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z] =
    applyToSink(sink)

  /**
   * Applies this pipeline to a sink, producing a sink that pre-processes
   * elements through this pipeline.
   */
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]

  /** Applies this pipeline to a stream, producing a transformed stream. */
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
}

/**
 * Companion object for [[Pipeline]]. Provides factory constructors for common
 * transformations: `map`, `filter`, `take`, `drop`, `collect`, and `identity`.
 */
object Pipeline {

  /**
   * A pipeline that applies a partial function, emitting only defined results.
   */
  def collect[A, B](
    pf: PartialFunction[A, B]
  )(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B] = new CollectPipeline(pf, jtA, jtB)

  /**
   * A pipeline that skips the first `n` elements, then passes through the rest.
   */
  def drop[A](n: Long): Pipeline[A, A] = new DropPipeline(n)

  /** A pipeline that emits only elements satisfying `pred`. */
  def filter[A](pred: A => Boolean)(implicit jtA: JvmType.Infer[A]): Pipeline[A, A] =
    new FilterPipeline(pred, jtA)

  /** The identity pipeline that passes all elements through unchanged. */
  def identity[A](implicit jtA: JvmType.Infer[A]): Pipeline[A, A] =
    new MapPipeline[A, A](x => x, jtA, jtA)

  /** A pipeline that transforms each element with `f`. */
  def map[A, B](f: A => B)(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B] =
    new MapPipeline(f, jtA, jtB)

  /** A pipeline that passes through at most the first `n` elements. */
  def take[A](n: Long): Pipeline[A, A] = new TakePipeline(n)

  private[streams] def runViaSink[A, B, E, Z](
    pipe: Pipeline[A, B],
    sink: Sink[E, B, Z]
  ): Sink[E, A, Z] =
    new RunViaSink(pipe, sink)

  /**
   * Pipeline that applies a partial function, emitting only defined results.
   */
  private[streams] final class CollectPipeline[A, B](
    pf: PartialFunction[A, B],
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ) extends Pipeline[A, B] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B] =
      new Stream.Collected(stream, pf, jtA, jtB)
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, B, E, Z](this, sink)
  }

  /** Composed pipeline: applies `self` then `that`. */
  private[streams] final class Composed[A, B, C](
    self: Pipeline[A, B],
    that: Pipeline[B, C]
  ) extends Pipeline[A, C] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, C] =
      that.applyToStream[E](self.applyToStream[E](stream))
    def applyToSink[E, Z](sink: Sink[E, C, Z]): Sink[E, A, Z] = {
      val midSink: Sink[E, B, Z] = that.applyToSink[E, Z](sink)
      self.applyToSink[E, Z](midSink)
    }
  }

  /** Pipeline that skips the first `n` elements. */
  private[streams] final class DropPipeline[A](n: Long) extends Pipeline[A, A] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Dropped(stream, n)
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
  }

  /** Pipeline that emits only elements satisfying `pred`. */
  private[streams] final class FilterPipeline[A](pred: A => Boolean, jtA: JvmType.Infer[A]) extends Pipeline[A, A] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Filtered(stream, pred, jtA)
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
  }

  /** Pipeline that transforms each element with `f`. */
  private[streams] final class MapPipeline[A, B](f: A => B, jtA: JvmType.Infer[A], jtB: JvmType.Infer[B])
      extends Pipeline[A, B] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, B] =
      new Stream.Mapped(stream, f, jtA, jtB)
    def applyToSink[E, Z](sink: Sink[E, B, Z]): Sink[E, A, Z] =
      sink.contramap[A](f)
  }

  /**
   * Sink adapter: applies a pipeline to the reader then drains with the
   * downstream sink.
   */
  private[streams] final class RunViaSink[A, B, E, Z](
    pipe: Pipeline[A, B],
    sink: Sink[E, B, Z]
  ) extends Sink[E, A, Z] {
    private[streams] def drain(reader: Reader[?]): Z = {
      val synthStream         = Stream.fromReader[E, A](reader.asInstanceOf[Reader[A]])
      val piped: Stream[E, B] = pipe.applyToStream[E](synthStream)
      val pipedReader         = Stream.compileToReader(piped)
      try sink.drain(pipedReader)
      finally pipedReader.close()
    }
  }

  /** Pipeline that passes through at most `n` elements. */
  private[streams] final class TakePipeline[A](n: Long) extends Pipeline[A, A] {
    def applyToStream[E](stream: Stream[E, A]): Stream[E, A] =
      new Stream.Taken(stream, n)
    def applyToSink[E, Z](sink: Sink[E, A, Z]): Sink[E, A, Z] =
      Pipeline.runViaSink[A, A, E, Z](this, sink)
  }
}
