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
import zio.blocks.streams.internal.{runBoth, EndOfStream}

/**
 * JavaScript-specific platform implementation.
 *
 * Scala.js is single-threaded; true concurrency is not supported. Virtual
 * threads are not available. [[createBufferedReader]] uses
 * [[internal.SyncBufferedReader]] to synchronously prefetch elements.
 *
 * [[createMergeReader]] and [[createMapParReader]] degrade to sequential
 * implementations (flatMap and map, respectively).
 */
trait PlatformSpecific extends Platform {
  override val supportsConcurrency: Boolean = false

  override def startVirtualThread(name: String, task: Runnable): Thread =
    throw new UnsupportedOperationException(
      "Virtual threads are not supported on Scala.js. Use supportsConcurrency to guard calls."
    )

  override def createBufferedReader[A](upstream: Reader[A], bufferSize: Int): Reader[A] =
    new internal.SyncBufferedReader(upstream, bufferSize)

  override def createMergeReader[A](
    outerReader: Reader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader[A] =
    new Reader[A] {
      private val outer            = outerReader.asInstanceOf[Reader[Stream[Any, A]]]
      private var inner: Reader[A] = null
      private var outerDone        = false

      def isClosed: Boolean = outerDone && (inner == null || inner.isClosed)

      def read[A1 >: A](sentinel: A1): A1 = {
        while (true) {
          if (inner != null) {
            val v = inner.read[Any](EndOfStream)
            if (v.asInstanceOf[AnyRef] ne EndOfStream) {
              return v.asInstanceOf[A1]
            }
            // Inner stream exhausted: close it and get next. A close failure
            // must surface (Principle 4), never be swallowed; clear `inner`
            // first so a throwing close is not finalized twice by close().
            val toClose = inner
            inner = null
            toClose.close()
          }
          // Try to get next inner stream from outer
          val nextInner = outer.read[Any](EndOfStream)
          if (nextInner.asInstanceOf[AnyRef] eq EndOfStream) {
            outerDone = true
            return sentinel
          }
          inner = nextInner.asInstanceOf[Stream[Any, A]].compile(0, bufferSize)
        }
        sentinel
      }

      def close(): Unit = {
        outerDone = true
        if (inner != null) {
          val toClose = inner
          inner = null
          // Both finalizers must run; a failure propagates with suppression
          // (Principle 4).
          runBoth(toClose.close())(outer.close())
        } else outer.close()
      }

      override def reset(): Unit = {
        // Sequential merge must not weaken replayability. Close any open inner
        // first — a close failure must surface (Principle 4); clear `inner`
        // before closing so a throwing close is not finalized twice. Then
        // replay the outer reader: a genuine one-shot outer throws
        // UnsupportedOperationException here, which correctly propagates (a
        // merge over a one-shot source is itself one-shot).
        if (inner != null) {
          val toClose = inner
          inner = null
          toClose.close()
        }
        outer.reset()
        outerDone = false
      }
    }

  override def createMapParReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B] =
    new Reader[B] {
      override def jvmType: JvmType = outType
      def isClosed: Boolean         = upstream.isClosed

      def read[B1 >: B](sentinel: B1): B1 = {
        val v = upstream.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) {
          sentinel
        } else {
          val a = v.asInstanceOf[A]
          f(a).asInstanceOf[B1]
        }
      }

      def close(): Unit = upstream.close()

      // Sequential mapPar is stateless: replaying is just replaying the
      // upstream. A genuine one-shot upstream throws
      // UnsupportedOperationException here, which correctly propagates.
      override def reset(): Unit = upstream.reset()
    }
}
