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

package zio.blocks.streams.internal

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.io.Reader

/**
 * A buffered reader that prefetches up to `bufferSize` elements from `upstream`
 * into an internal buffer and serves reads from it.
 *
 * Used on Scala.js where true concurrency is not available. Refills the
 * internal buffer lazily (only when exhausted). Elements are read using the
 * generic AnyRef lane (boxing is acceptable since no concurrency benefit exists
 * on JS).
 *
 * @param upstream
 *   the source reader
 * @param bufferSize
 *   maximum elements to prefetch per fill
 */
private[streams] final class SyncBufferedReader[A](upstream: Reader[A], bufferSize: Int) extends Reader[A] {
  private val buf: Array[AnyRef] = new Array[AnyRef](bufferSize)
  private var pos: Int           = 0
  private var limit: Int         = 0
  private var upstreamDone       = false
  // An upstream failure raised mid-refill, AFTER elements were already
  // prefetched. `buffer` is a pure decoupling transform: the prefetched
  // prefix must be served before the error surfaces — rethrowing from inside
  // the refill loop would silently abandon those elements, diverging from the
  // JVM `ConcurrentBufferedReader`, which drains its queue before surfacing
  // the producer error (BUG-R5-05).
  private var pendingError: Throwable = null

  def isClosed: Boolean = pos >= limit && (upstreamDone || upstream.isClosed)

  def read[A1 >: A](sentinel: A1): A1 =
    if (pos < limit) {
      val v = buf(pos)
      pos += 1
      v.asInstanceOf[A1]
    } else if (upstreamDone) {
      if (pendingError ne null) throw pendingError
      sentinel
    } else {
      pos = 0
      limit = 0
      var i = 0
      while (i < bufferSize) {
        val v =
          try upstream.read[Any](EndOfStream)
          catch {
            case t: Throwable =>
              pendingError = t
              EndOfStream
          }
        if (v.asInstanceOf[AnyRef] eq EndOfStream) {
          upstreamDone = true
          i = bufferSize
        } else {
          buf(i) = v.asInstanceOf[AnyRef]
          limit += 1
          i += 1
        }
      }
      if (limit > 0) {
        val v = buf(pos)
        pos += 1
        v.asInstanceOf[A1]
      } else {
        if (pendingError ne null) throw pendingError
        sentinel
      }
    }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    if (pos >= limit && !upstreamDone) {
      val first = read[Any](EndOfStream)
      if (first.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
      if (n == 1 || pos >= limit) return Chunk.single(first.asInstanceOf[A1])
      val count = math.min(n - 1, limit - pos)
      val b     = ChunkBuilder.make[A1](count + 1)
      b += first.asInstanceOf[A1]
      var i = 0
      while (i < count) {
        b += buf(pos).asInstanceOf[A1]
        pos += 1
        i += 1
      }
      b.result()
    } else if (pos >= limit) {
      if (pendingError ne null) throw pendingError
      Chunk.empty
    } else {
      val count = math.min(n, limit - pos)
      val b     = ChunkBuilder.make[A1](count)
      var i     = 0
      while (i < count) {
        b += buf(pos).asInstanceOf[A1]
        pos += 1
        i += 1
      }
      b.result()
    }
  }

  def close(): Unit = upstream.close()

  override def reset(): Unit = {
    // `buffer` is a pure decoupling transform: it must not weaken replayability.
    // Reset the upstream (propagating UnsupportedOperationException for a genuine
    // one-shot source) and discard any prefetched elements.
    upstream.reset()
    java.util.Arrays.fill(buf, null)
    pos = 0
    limit = 0
    upstreamDone = false
    pendingError = null
  }
}
