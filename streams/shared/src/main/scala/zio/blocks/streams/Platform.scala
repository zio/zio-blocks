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
 * Cross-platform abstraction for thread management in the streams module.
 *
 * Platform-specific implementations are provided in:
 *   - jvm/src/main/scala/zio/blocks/streams/PlatformSpecific.scala (JVM)
 *   - js/src/main/scala/zio/blocks/streams/PlatformSpecific.scala (JS)
 *
 * On the JVM, [[startVirtualThread]] launches a virtual thread (JDK 21+,
 * detected via reflection) or a raw daemon thread as fallback. On Scala.js
 * there is no concurrency, so [[supportsConcurrency]] is `false` and
 * [[startVirtualThread]] throws [[UnsupportedOperationException]].
 *
 * [[createBufferedReader]] is a factory that returns a platform-appropriate
 * buffered reader: concurrent on JVM, synchronous prefetch on JS.
 */
trait Platform {

  /**
   * Whether this platform supports true concurrent threads. `true` on JVM,
   * `false` on Scala.js.
   */
  def supportsConcurrency: Boolean

  /**
   * Starts a new thread and runs `task` on it. Returns the started thread
   * (which can be [[java.lang.Thread#join]]ed). On JVM, uses a virtual thread
   * (JDK 21+) if available, otherwise a raw daemon thread. On Scala.js, always
   * throws [[UnsupportedOperationException]].
   */
  def startVirtualThread(name: String, task: Runnable): Thread

  /**
   * Returns a [[Reader]] that buffers elements produced by `upstream` into a
   * bounded buffer of size `bufferSize`. On JVM, the producer runs on a
   * separate virtual thread (concurrent). On JS, the reader prefetches elements
   * synchronously.
   */
  def createBufferedReader[A](upstream: Reader[A], bufferSize: Int): Reader[A]

  /**
   * Returns a [[Reader]] that merges elements from N inner streams produced by
   * `outerReader`, up to `maxOpen` concurrent inner streams at a time. On JVM,
   * producers run on virtual threads. On JS, degrades to sequential flatMap.
   */
  def createMergeReader[A](outerReader: Reader[?], maxOpen: Int, bufferSize: Int, elemType: JvmType): Reader[A]

  /**
   * Returns a [[Reader]] that applies `f` to each element of `upstream` using
   * `n` concurrent workers. On JVM, workers run on virtual threads. On JS,
   * degrades to sequential map.
   */
  def createMapParReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B]
}

object Platform extends PlatformSpecific
