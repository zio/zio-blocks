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
import zio.blocks.async.Async
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Concat
import zio.blocks.scope.{Resource, Scope}
import zio.blocks.streams.io.Reader

private[streams] trait LifecycleWaitPlatform {
  protected final def awaitClose(result: Async[Unit]): Unit = result.block
}

/**
 * The platform-specific members of [[Stream]]. On the JVM these are the
 * blocking terminals (`run`, `runCollect`, `runDrain`, `runFold`, `runForeach`,
 * `foreach`, `count`, `exists`, `find`, `forall`, `head`, `last`), which park
 * the calling thread until the stream is done, and `start`, which returns a
 * blocking reader. The Scala.js copy has no members, so shared code that calls
 * one of them does not compile for Scala.js; use the `*Async` terminals there.
 */
trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] =>

  /** Counts the elements, blocking until the stream ends. */
  def count: Either[E, Long] = run(Sink.count)

  /**
   * Returns `true` at the first element that satisfies `pred`, without pulling
   * further, or `false` at the end. An exception thrown by `pred` is a defect.
   */
  def exists(pred: A => Boolean): Either[E, Boolean] = run(Sink.exists(pred))

  /**
   * Returns the first element that satisfies `pred` in `Some`, without pulling
   * further, or `None` at the end. An exception thrown by `pred` is a defect.
   */
  def find(pred: A => Boolean): Either[E, Option[A]] = run(Sink.find(pred))

  /**
   * Returns `false` at the first element that fails `pred`, without pulling
   * further, or `true` at the end (so `true` for an empty stream). An exception
   * thrown by `pred` is a defect.
   */
  def forall(pred: A => Boolean): Either[E, Boolean] = run(Sink.forall(pred))

  /** Alias for [[runForeach]]. */
  def foreach(f: A => Unit): Either[E, Unit] = runForeach(f)

  /**
   * Returns the first element in `Some`, or `None` for an empty stream, and
   * pulls nothing more.
   */
  def head: Either[E, Option[A]] = run(Sink.head)

  /**
   * Returns the last element in `Some`, or `None` for an empty stream, after
   * pulling every element.
   */
  def last: Either[E, Option[A]] = run(Sink.last)

  /**
   * Runs this stream into `sink` and returns the sink's result, blocking the
   * calling thread until the run ends. The stream is closed before `run`
   * returns, also on failure. A typed error of the stream or of the sink is a
   * `Left` (the error type combines both); a defect is thrown.
   *
   * {{{
   * Stream(1, 2, 3).run(Sink.sumInt) // Right(6)
   * }}}
   *
   * Some stages run on another thread while the caller waits: those before a
   * `catchAll`, `catchDefect`, `orElse` or `*Async` operator run on
   * `ForkJoinPool.commonPool`, as can stages fed by an asynchronous source such
   * as `Stream.unwrap`, and `buffer`, `mapPar`, `flatMapPar` and `mergeAll` run
   * stages on threads of their own. A known bug follows: a run started while an
   * object is being initialized (a `val` in an `object`, or on Scala 3 the body
   * of `object Main extends App`) deadlocks when one of those stages reads a
   * field of that object. Call blocking terminals from `def main` instead.
   */
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Either[E3, Z] =
    runBlocking(sink)

  /**
   * Collects every element, in order, into a `Chunk`, blocking until the stream
   * ends; the result is a `Left` if the stream fails with a typed error. It
   * never returns for an infinite stream.
   *
   * {{{
   * Stream(1, 2, 3).map(_ * 2).runCollect // Right(Chunk(2,4,6))
   * Stream.fail("not found").runCollect   // Left(not found)
   * }}}
   */
  def runCollect: Either[E, Chunk[A]] = run(Sink.collectAll)

  /**
   * Runs the stream for its effects and discards every element, blocking until
   * it ends.
   */
  def runDrain: Either[E, Unit] = run(Sink.drain)

  /**
   * Folds the elements from `z` with `f`, blocking until the stream ends; the
   * overload for a `Double` accumulator.
   */
  def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double] = runFoldDoubleBlocking(z, f)

  /**
   * Folds the elements from `z` with `f`, blocking until the stream ends; the
   * overload for an `Int` accumulator.
   */
  def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int] = runFoldIntBlocking(z, f)

  /**
   * Folds the elements from `z` with `f`, blocking until the stream ends; the
   * overload for a `Long` accumulator.
   */
  def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long] = runFoldLongBlocking(z, f)

  /**
   * Folds the elements from `z` with `f`, in order, blocking until the stream
   * ends. An exception thrown by `f` is a defect. The `Double`, `Int` and
   * `Long` overloads are chosen by the static type of `z`.
   *
   * {{{
   * Stream("Zionomicon", "Scala with Cats").runFold("")((acc, title) => acc + title.head)
   * // Right(ZS)
   * }}}
   */
  def runFold[Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Either[E, Z] =
    runFoldGenericBlocking(z, f)

  /**
   * Runs `f` on every element, in order, blocking until the stream ends. An
   * exception thrown by `f` is a defect and stops the run.
   *
   * {{{
   * Stream("Zionomicon", "Scala with Cats").runForeach(title => println(title))
   * // Zionomicon
   * // Scala with Cats
   * // the result is Right(())
   * }}}
   */
  def runForeach(f: A => Unit): Either[E, Unit] = run(Sink.foreach(f))

  /**
   * Opens this stream and returns its blocking reader, owned by `scope`:
   * closing the scope closes the reader and runs the stream's finalizers. Pulls
   * block while an asynchronous stage is pending. The reader must not outlive
   * the scope. To read a `Long` or `Double` stream without choosing a sentinel
   * that could equal real data, use the bulk reads `readLongs` and
   * `readDoubles`, whose returned count marks the end.
   */
  def start(implicit scope: Scope): scope.$[Reader.SyncReader[A]] =
    scope.allocate(Resource.acquireRelease(Sink.toSyncReader(Stream.compileForBlocking(this)))(_.close()))
}
