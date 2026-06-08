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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.queues.BlockingSpscQueue
import zio.blocks.streams.{ProducerSink, ProducerStreams}
import zio.blocks.streams.io.Reader

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reader used by [[zio.blocks.streams.ProducerStreams.fromProducer]]. The
 * producer side pushes elements (or whole chunks) into a [[BlockingSpscQueue]],
 * while the consumer drains them via [[read]].
 */
private[streams] final class ProducerReader[A](bufferSize: Int) extends Reader[A] {

  private val queue: BlockingSpscQueue[AnyRef]     = new BlockingSpscQueue[AnyRef](bufferSize)
  private val terminated: AtomicBoolean            = new AtomicBoolean(false)
  private val cancelled: AtomicBoolean             = new AtomicBoolean(false)
  @volatile private var cancelCallback: () => Unit = null
  @volatile private var closed: Boolean            = false

  private var currentChunk: Chunk[_ <: A] = null
  private var chunkIndex: Int             = 0

  def setCancelCallback(cb: () => Unit): Unit = cancelCallback = cb

  private[streams] def offerDefect(cause: Throwable): Unit =
    if (terminated.compareAndSet(false, true)) {
      queue.offer(new ProducerStreams.DefectMarker(cause))
    }

  val sink: ProducerSink[A, Any] = new ProducerSink[A, Any] {
    def emit(a: A): Boolean = {
      if (a == null) throw new NullPointerException("ProducerSink.emit does not accept null values")
      if (terminated.get() || cancelled.get()) return false
      queue.offer(a.asInstanceOf[AnyRef]) && !cancelled.get()
    }

    override def emit(chunk: Chunk[A @unchecked]): Boolean = {
      if (chunk == null) throw new NullPointerException("ProducerSink.emit does not accept null values")
      if (chunk.isEmpty) return true
      if (terminated.get() || cancelled.get()) return false
      queue.offer(new ProducerStreams.ChunkEnvelope(chunk)) && !cancelled.get()
    }

    def end(): Unit =
      if (terminated.compareAndSet(false, true)) {
        queue.offer(ProducerStreams.EndMarker)
      }

    def fail(error: Any): Unit =
      if (terminated.compareAndSet(false, true)) {
        queue.offer(new ProducerStreams.FailMarker(error))
      }
  }

  def isClosed: Boolean = closed

  def read[A1 >: A](sentinel: A1): A1 = {
    if (currentChunk != null) {
      val a = currentChunk(chunkIndex).asInstanceOf[A1]
      chunkIndex += 1
      if (chunkIndex >= currentChunk.length) currentChunk = null
      return a
    }
    if (closed) return sentinel
    val item = queue.take()
    item match {
      case null =>
        closed = true
        sentinel
      case r if r eq ProducerStreams.EndMarker =>
        closed = true
        sentinel
      case fm: ProducerStreams.FailMarker =>
        closed = true
        throw new StreamError(fm.error)
      case dm: ProducerStreams.DefectMarker =>
        closed = true
        throw dm.cause
      case env: ProducerStreams.ChunkEnvelope =>
        val c = env.chunk.asInstanceOf[Chunk[A]]
        if (c.isEmpty) {
          read(sentinel)
        } else {
          if (c.length > 1) {
            currentChunk = c
            chunkIndex = 1
          }
          c(0).asInstanceOf[A1]
        }
      case _ =>
        item.asInstanceOf[A1]
    }
  }

  def close(): Unit =
    if (!closed) {
      closed = true
      if (cancelled.compareAndSet(false, true)) {
        terminated.set(true)
        queue.close()
        val cb = cancelCallback
        if (cb != null) {
          try cb()
          catch { case _: Throwable => () }
        }
      }
    }
}
