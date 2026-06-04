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
import zio.blocks.ringbuffer.DoubleSpscRingBuffer
import zio.blocks.streams.{JvmType, Platform, Stream}
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingMpmcQueue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Double`-specialized concurrent merge reader (used by `mergeAll` /
 * `flatMapPar`).
 *
 * Uses `DoubleSpscRingBuffer` for the per-drainer data channel, which reserves
 * two NaN bit patterns: `EMPTY_BITS` (`0xFFF8_0000_0000_0001L`) is the
 * empty-slot marker and `DONE_BITS` (`0xFFF8_0000_0000_0002L`) is the in-band
 * end-of-stream sentinel. Any other 64-bit pattern decodes to a real `Double`
 * via `java.lang.Double.longBitsToDouble`.
 *
 * Each drainer writes one in-band DONE to its data queue when it has finished
 * all assigned inner streams. The consumer counts DONE sentinels and terminates
 * once it has seen `maxOpen` of them — no separate `coordinatorDone` volatile
 * flag is needed on the hot path.
 */
private[streams] final class DoubleConcurrentMergeReader(outerReader: Reader[?], maxOpen: Int, bufferSize: Int)
    extends Reader[Double] {
  import ConcurrentMergeReader._
  import DoubleConcurrentMergeReader._

  require(maxOpen >= 1, s"DoubleConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader[Any] = outerReader.asInstanceOf[Reader[Any]]

  private val dataQueues: Array[DoubleSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new DoubleSpscRingBuffer(bufferSize))
  @volatile private var consumerWaiter: Thread = null

  private val errorRef                          = new AtomicReference[Throwable](null)
  @volatile private var consumerClosed: Boolean = false

  private val workQueue      = new BlockingMpmcQueue[AnyRef](Math.max(maxOpen, 16))
  private val drainerLatch   = new CountDownLatch(maxOpen)
  private val drainerThreads = new AtomicReferenceArray[Thread](maxOpen)

  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]

  Array.tabulate(maxOpen) { idx =>
    Platform.startVirtualThread(
      s"zio-blocks-merge-drainer-${counter.getAndIncrement()}-$idx",
      new Runnable { def run(): Unit = drainerLoop(idx) }
    )
  }

  private val coordinatorTask: Runnable = new Runnable {
    def run(): Unit =
      try {
        var running = true
        while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
          val v = outer.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) {
            running = false
          } else {
            if (!workQueue.offer(v.asInstanceOf[AnyRef])) running = false
          }
        }
      } catch {
        case t: Throwable =>
          recordError(t)
      } finally {
        try {
          var i = 0
          while (i < maxOpen) {
            workQueue.offer(DoneSentinel)
            i += 1
          }
          try drainerLatch.await()
          catch { case _: InterruptedException => () }
        } catch {
          case t: Throwable => recordError(t)
        } finally {
          try outer.close()
          catch { case _: Throwable => () }
        }
      }
  }

  coordinatorThread = Platform.startVirtualThread(
    s"zio-blocks-merge-coordinator-${counter.getAndIncrement()}",
    coordinatorTask
  )

  override def jvmType: JvmType = JvmType.Double

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-thread-private state: scanStart, drainersDone, eofReturned are
  // mutated only from the consumer side and therefore need no atomics.
  private var scanStart: Int       = 0
  private var drainersDone: Int    = 0
  private var eofReturned: Boolean = false

  override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx   = (scanStart + i) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == DoubleSpscRingBuffer.DONE_BITS) {
          drainersDone += 1
        } else if (packed != DoubleSpscRingBuffer.EMPTY_BITS) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          return java.lang.Double.longBitsToDouble(packed)
        }
        i += 1
      }

      val err = errorRef.get()
      if (err ne null) rethrow(err)

      if (drainersDone >= maxOpen) {
        eofReturned = true
        return sentinel
      }

      consumerWaiter = Thread.currentThread()
      try {
        // Re-poll after registering as waiter (avoid lost wakeup).
        var k     = 0
        var found = false
        var datum = 0.0d
        while (k < maxOpen && !found) {
          val qIdx2   = (scanStart + k) % maxOpen
          val packed2 = dataQueues(qIdx2).pollPacked()
          if (packed2 == DoubleSpscRingBuffer.DONE_BITS) {
            drainersDone += 1
          } else if (packed2 != DoubleSpscRingBuffer.EMPTY_BITS) {
            scanStart = (qIdx2 + 1) % maxOpen
            datum = java.lang.Double.longBitsToDouble(packed2)
            found = true
          }
          k += 1
        }
        if (found) {
          consumerWaiter = null
          val err2 = errorRef.get()
          if (err2 ne null) rethrow(err2)
          return datum
        }
        if (drainersDone < maxOpen && errorRef.get() == null && !consumerClosed) {
          LockSupport.park(this)
        }
      } finally {
        consumerWaiter = null
      }
    }
    sentinel
  }

  def read[A1 >: Double](sentinel: A1): A1 = {
    val v = readDouble(Double.MaxValue)(unsafeEvidence)
    if (v == Double.MaxValue) sentinel
    else Double.box(v).asInstanceOf[A1]
  }

  override def readUpToN[A1 >: Double](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = readDouble(Double.MaxValue)(unsafeEvidence)
    if (first == Double.MaxValue) return Chunk.empty
    if (n == 1) return Chunk.single(first.asInstanceOf[A1])

    val b = new ChunkBuilder.Double()
    b.addOne(first)
    var i = 1
    while (i < n) {
      var found = false
      var q     = 0
      while (q < maxOpen && !found) {
        val qIdx   = (scanStart + q) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == DoubleSpscRingBuffer.DONE_BITS) {
          drainersDone += 1
        } else if (packed != DoubleSpscRingBuffer.EMPTY_BITS) {
          found = true
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          b.addOne(java.lang.Double.longBitsToDouble(packed))
          i += 1
        }
        q += 1
      }
      if (!found) return b.result().asInstanceOf[Chunk[A1]]
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  def close(): Unit = {
    consumerClosed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    workQueue.close()

    coordinatorThread.interrupt()
    coordinatorThread.join(5000)

    var i = 0
    while (i < maxOpen) {
      val t = drainerThreads.get(i)
      if (t ne null) {
        t.interrupt()
        t.join(5000)
      }
      i += 1
    }
  }

  private def drainerLoop(idx: Int): Unit = {
    val self = Thread.currentThread()
    drainerThreads.set(idx, self)
    try {
      var keepRunning = true
      while (keepRunning && !consumerClosed && !self.isInterrupted) {
        val work = workQueue.take()
        if ((work eq null) || (work eq DoneSentinel) || consumerClosed || self.isInterrupted) {
          keepRunning = false
        } else {
          drainInner(work.asInstanceOf[Stream[Any, Double]], idx)
        }
      }
    } finally {
      // In-band: write DONE into this drainer's data queue. This is ordered
      // after every prior `offer` and tells the consumer that no more data
      // will ever come from this drainer. Once the consumer has seen `maxOpen`
      // DONE sentinels, the stream is terminated.
      while (!dataQueues(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      drainerLatch.countDown()
    }
  }

  private def drainInner(innerStream: Stream[Any, Double], drainerIdx: Int): Unit = {
    var innerReader: Reader[Double] = null
    try {
      innerReader = innerStream.compile(0, bufferSize)
      var running = true
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val v = innerReader.readDouble(Double.MaxValue)(unsafeEvidence)
        if (v == Double.MaxValue) {
          running = false
        } else {
          var offered = false
          while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
            if (dataQueues(drainerIdx).offer(v)) {
              offered = true
              val cw = consumerWaiter
              if (cw ne null) LockSupport.unpark(cw)
            } else {
              LockSupport.parkNanos(this, 1000L)
            }
          }
          if (!offered) {
            running = false
          }
        }
      }

    } catch {
      case t: Throwable =>
        recordError(t)
    } finally {
      if (innerReader ne null) {
        try innerReader.close()
        catch { case _: Throwable => () }
      }
    }
  }

  private def recordError(t: Throwable): Unit =
    if (errorRef.compareAndSet(null, t)) {
      consumerClosed = true
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      workQueue.close()
      val ct = coordinatorThread
      if (ct ne null) ct.interrupt()
      var i = 0
      while (i < maxOpen) {
        val dt = drainerThreads.get(i)
        if (dt ne null) dt.interrupt()
        i += 1
      }
    }

  private def rethrow(t: Throwable): Nothing = t match {
    case se: StreamError => throw se
    case _               => throw t
  }
}

private[internal] object DoubleConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
