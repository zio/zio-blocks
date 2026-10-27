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
 * The platform capabilities that the stream engine builds its concurrent
 * operators on: a flag that says whether threads exist, a thread starter, and
 * the factories for the readers behind `buffer`, `mapPar`, `flatMapPar` and
 * `mergeAll`. The companion object [[Platform]] implements it for the platform
 * the code runs on.
 *
 * On the JVM, [[supportsConcurrency]] is `true` and [[startVirtualThread]]
 * starts a virtual thread, or a daemon platform thread when the JDK has none.
 * On Scala.js, [[supportsConcurrency]] is `false` and [[startVirtualThread]]
 * throws `UnsupportedOperationException`.
 */
trait Platform {

  /**
   * Whether this platform can run code on more than one thread: `true` on the
   * JVM, `false` on Scala.js.
   */
  def supportsConcurrency: Boolean

  /**
   * Starts a thread that runs `task` and returns it, already started.
   *
   * On the JVM it starts a virtual thread when the JDK has them (JDK 21 and
   * later), and a daemon platform thread named `name` otherwise or when
   * starting the virtual thread fails. A known bug: a virtual thread is named
   * `name` followed by `0` (`"worker"` becomes `"worker0"`). On Scala.js it
   * throws `UnsupportedOperationException`.
   */
  def startVirtualThread(name: String, task: Runnable): Thread

  /**
   * Returns a reader that reads `upstream` ahead into a buffer of `bufferSize`
   * elements, as `Stream#buffer` does.
   *
   * For a synchronous `upstream`, the JVM fills the buffer on a thread of its
   * own (started with [[startVirtualThread]]), and Scala.js fills it on the
   * reading thread whenever it runs empty. For an asynchronous `upstream`, both
   * platforms return the same asynchronous buffered reader.
   */
  def createBufferedReaderFromReader[A](upstream: Reader[A], bufferSize: Int): Reader[A]

  /**
   * [[createBufferedReaderFromReader]] for a synchronous `upstream`, with the
   * result typed as a `Reader.SyncReader`.
   */
  final def createBufferedReader[A](upstream: Reader.SyncReader[A], bufferSize: Int): Reader.SyncReader[A] =
    createBufferedReaderFromReader(upstream, bufferSize).asInstanceOf[Reader.SyncReader[A]]

  /**
   * Returns an asynchronous reader that merges the inner streams that
   * `outerReader` yields (each of its elements is a `Stream`), with at most
   * `maxOpen` of them open at a time, as `Stream#flatMapPar` and
   * `Stream.mergeAll` do. `bufferSize` is the buffer size each inner stream is
   * compiled with, and `elemType` is the lane of the result.
   *
   * Both platforms use the same asynchronous merge, which interleaves the open
   * inner streams on one engine and starts no threads. The threaded merge of a
   * JVM blocking terminal is built elsewhere.
   */
  def createMergeReaderFromReader[A](
    outerReader: Reader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader[A]

  /**
   * The synchronous merge that a JVM blocking terminal runs: on the JVM it
   * drains each open inner stream on a thread of its own; on Scala.js it reads
   * the inner streams one after another.
   */
  private[streams] def createMergeReader[A](
    outerReader: Reader.SyncReader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader.SyncReader[A]

  /**
   * Returns a reader that applies `f` to the elements of `upstream` with up to
   * `n` calls at a time, as `Stream#mapPar` does. `inType` and `outType` are
   * the lanes of `A` and `B`.
   *
   * For a synchronous `upstream`, the JVM runs `f` on `n` worker threads
   * (started with [[startVirtualThread]]) with queues of `bufferSize`, and
   * Scala.js maps the elements one after another on the reading thread. For an
   * asynchronous `upstream`, both platforms use the same asynchronous engine
   * and wrap each call in a completed `Async`, so the calls do not overlap.
   */
  def createMapParReaderFromReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B]

  /**
   * [[createMapParReaderFromReader]] for a synchronous `upstream`, with the
   * result typed as a `Reader.SyncReader`.
   */
  final def createMapParReader[A, B](
    upstream: Reader.SyncReader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader.SyncReader[B] =
    createMapParReaderFromReader(upstream, n, f, bufferSize, inType, outType).asInstanceOf[Reader.SyncReader[B]]
}

/** The [[Platform]] capabilities of the platform the code runs on. */
object Platform extends PlatformSpecific
