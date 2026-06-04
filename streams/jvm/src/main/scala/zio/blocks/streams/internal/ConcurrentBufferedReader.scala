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
import zio.blocks.streams.Platform
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingSpscQueue

import java.util.concurrent.atomic.AtomicLong

/**
 * A concurrent buffered reader. The producer runs on a separate virtual thread
 * (or daemon thread) and feeds elements into a [[BlockingSpscQueue]]; the
 * consumer pulls from the queue.
 *
 * Null elements from upstream are transparently encoded as [[NullSentinel]] to
 * distinguish them from the queue's null-means-closed signal and the
 * [[EndOfStream]] completion sentinel.
 *
 * Thread ownership: the producer thread exclusively owns `upstream`. The
 * consumer (this reader) never calls `upstream.read()` or `upstream.close()`.
 */
private[streams] final class ConcurrentBufferedReader[A](upstream: Reader[A], bufferSize: Int) extends Reader[A] {
  import ConcurrentBufferedReader._

  private val queue: BlockingSpscQueue[AnyRef]   = new BlockingSpscQueue[AnyRef](bufferSize)
  @volatile private var producerError: Throwable = null
  @volatile private var consumerClosed: Boolean  = false
  @volatile private var producerDone: Boolean    = false
  private var upstreamClosedByProducer: Boolean  = false

  private val producerTask: Runnable = new Runnable {
    def run(): Unit =
      try {
        var running = true
        while (running && !consumerClosed && !queue.isClosed && !Thread.currentThread().isInterrupted) {
          val v = upstream.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) {
            queue.offer(EndOfStream)
            running = false
          } else {
            val wrapped = if (v == null) NullSentinel else v.asInstanceOf[AnyRef]
            if (!queue.offer(wrapped)) {
              running = false
            }
          }
        }
      } catch {
        case t: Throwable =>
          producerError = t
          queue.offer(EndOfStream)
      } finally {
        queue.close()
        if (!upstreamClosedByProducer) {
          upstreamClosedByProducer = true
          try upstream.close()
          catch { case _: Throwable => () }
        }
        producerDone = true
      }
  }

  private val producerThread: Thread = Platform.startVirtualThread(
    s"zio-blocks-buffer-${ConcurrentBufferedReader.counter.getAndIncrement()}",
    producerTask
  )

  def isClosed: Boolean = producerDone && queue.isEmpty

  def read[A1 >: A](sentinel: A1): A1 = {
    val result = queue.take()
    result match {
      case null =>
        val err = producerError
        if (err ne null) rethrow(err)
        sentinel
      case r if r eq EndOfStream =>
        val err = producerError
        if (err ne null) rethrow(err)
        sentinel
      case r if r eq NullSentinel =>
        null.asInstanceOf[A1]
      case r =>
        r.asInstanceOf[A1]
    }
  }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = queue.take()
    first match {
      case null =>
        val err = producerError
        if (err ne null) rethrow(err)
        Chunk.empty
      case r if r eq EndOfStream =>
        val err = producerError
        if (err ne null) rethrow(err)
        Chunk.empty
      case r if r eq NullSentinel =>
        if (n == 1) Chunk.single(null.asInstanceOf[A1])
        else {
          val b = ChunkBuilder.make[A1](math.min(n, 16))
          b += null.asInstanceOf[A1]
          drainQueue(b, n - 1)
          b.result()
        }
      case r =>
        if (n == 1) Chunk.single(r.asInstanceOf[A1])
        else {
          val b = ChunkBuilder.make[A1](math.min(n, 16))
          b += r.asInstanceOf[A1]
          drainQueue(b, n - 1)
          b.result()
        }
    }
  }

  private def drainQueue[A1 >: A](b: ChunkBuilder[A1], limit: Int): Unit = {
    var i = 0
    while (i < limit) {
      val v = queue.poll()
      v match {
        case null                   => return
        case r if r eq EndOfStream  => return
        case r if r eq NullSentinel => b += null.asInstanceOf[A1]; i += 1
        case r                      => b += r.asInstanceOf[A1]; i += 1
      }
    }
  }

  def close(): Unit = {
    consumerClosed = true
    queue.close()
    producerThread.interrupt()
    producerThread.join(5000)
  }

  private def rethrow(t: Throwable): Nothing = t match {
    case se: StreamError => throw se
    case _               => throw t
  }
}

private object ConcurrentBufferedReader {
  val counter: AtomicLong  = new AtomicLong(0L)
  val NullSentinel: AnyRef = new AnyRef
}
