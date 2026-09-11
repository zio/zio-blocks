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

trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] =>

  /** Returns the number of emitted elements, or the stream's typed error. */
  def count: Either[E, Long] = run(Sink.count)

  /**
   * Tests whether any element satisfies `pred`, or returns the stream's typed
   * error.
   */
  def exists(pred: A => Boolean): Either[E, Boolean] = run(Sink.exists(pred))

  /**
   * Returns the first element satisfying `pred`, or `None` if no element does.
   */
  def find(pred: A => Boolean): Either[E, Option[A]] = run(Sink.find(pred))

  /**
   * Tests whether every element satisfies `pred`, or returns the stream's typed
   * error.
   */
  def forall(pred: A => Boolean): Either[E, Boolean] = run(Sink.forall(pred))

  /** Alias for [[runForeach]]. */
  def foreach(f: A => Unit): Either[E, Unit] = runForeach(f)

  /** Returns the first element, or `None` if this stream is empty. */
  def head: Either[E, Option[A]] = run(Sink.head)

  /** Returns the last element, or `None` if this stream is empty. */
  def last: Either[E, Option[A]] = run(Sink.last)

  /** Runs this stream through `sink`, blocking until completion. */
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
  ): Either[E3, Z] =
    runBlocking(sink)

  /**
   * Collects all emitted elements into a [[Chunk]], blocking until completion.
   */
  def runCollect: Either[E, Chunk[A]] = run(Sink.collectAll)

  /** Consumes and discards all emitted elements, blocking until completion. */
  def runDrain: Either[E, Unit] = run(Sink.drain)

  /** Folds emitted elements from `z` with `f`, blocking until completion. */
  def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double] = runFoldDoubleBlocking(z, f)

  /** Folds emitted elements from `z` with `f`, blocking until completion. */
  def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int] = runFoldIntBlocking(z, f)

  /** Folds emitted elements from `z` with `f`, blocking until completion. */
  def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long] = runFoldLongBlocking(z, f)

  /** Folds emitted elements from `z` with `f`, blocking until completion. */
  def runFold[Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Either[E, Z] =
    runFoldGenericBlocking(z, f)

  /** Applies `f` to every emitted element, blocking until completion. */
  def runForeach(f: A => Unit): Either[E, Unit] = run(Sink.foreach(f))

  /**
   * Materializes this stream as a scoped synchronous reader. Closing the scope
   * closes the reader; asynchronous boundaries are bridged by the JVM runtime.
   */
  def start(implicit scope: Scope): scope.$[Reader.SyncReader[A]] =
    scope.allocate(Resource.acquireRelease(Sink.toSyncReader(Stream.compileForBlocking(this)))(_.close()))
}
