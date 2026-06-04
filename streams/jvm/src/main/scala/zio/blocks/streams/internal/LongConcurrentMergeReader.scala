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
import zio.blocks.ringbuffer.LongSpscRingBuffer
import zio.blocks.streams.{JvmType, Platform, Stream}
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingMpmcQueue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Long`-specialized concurrent merge reader (used by `mergeAll` /
 * `flatMapPar`).
 *
 * Uses `LongSpscRingBuffer` for the per-drainer data channel, which reserves
 * `Long.MinValue` as an empty-slot sentinel and `Long.MinValue + 1L` as the
 * in-band end-of-stream sentinel. Neither value can flow through this
 * specialized path; if an inner stream emits one of them, the data queue's
 * `offer` will throw `IllegalArgumentException` and the drainer surfaces that
 * as a stream error rather than letting it leak.
 *
 * Each drainer writes one in-band DONE to its data queue when it has finished
 * all assigned inner streams. The consumer counts DONE sentinels and terminates
 * once it has seen `maxOpen` of them — no separate `coordinatorDone` volatile
 * flag is needed on the hot path.
 *
 * Streams that may legitimately contain `Long.MinValue` or `Long.MinValue + 1L`
 * should avoid the specialized factory in
 * `PlatformSpecific.specializeMergeReader`.
 */
private[streams] final class LongConcurrentMergeReader(outerReader: Reader[?], maxOpen: Int, bufferSize: Int)
    extends Reader[Long] {
  import ConcurrentMergeReader._
  import LongConcurrentMergeReader._

  require(maxOpen >= 1, s"LongConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader[Any] = outerReader.asInstanceOf[Reader[Any]]

  private val dataQueues: Array[LongSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new LongSpscRingBuffer(bufferSize))
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

  override def jvmType: JvmType = JvmType.Long

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-thread-private state: scanStart, drainersDone, eofReturned are
  // mutated only from the consumer side and therefore need no atomics.
  private var scanStart: Int       = 0
  private var drainersDone: Int    = 0
  private var eofReturned: Boolean = false

  override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Long = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx   = (scanStart + i) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == LongSpscRingBuffer.DONE) {
          drainersDone += 1
        } else if (packed != LongSpscRingBuffer.EMPTY) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          return packed
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
        var datum = 0L
        while (k < maxOpen && !found) {
          val qIdx2   = (scanStart + k) % maxOpen
          val packed2 = dataQueues(qIdx2).pollPacked()
          if (packed2 == LongSpscRingBuffer.DONE) {
            drainersDone += 1
          } else if (packed2 != LongSpscRingBuffer.EMPTY) {
            scanStart = (qIdx2 + 1) % maxOpen
            datum = packed2
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

  def read[A1 >: Long](sentinel: A1): A1 = {
    val v = readLong(Long.MaxValue)(unsafeEvidence)
    if (v == Long.MaxValue) sentinel
    else Long.box(v).asInstanceOf[A1]
  }

  override def readUpToN[A1 >: Long](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = readLong(Long.MaxValue)(unsafeEvidence)
    if (first == Long.MaxValue) return Chunk.empty
    if (n == 1) return Chunk.single(first.asInstanceOf[A1])

    val b = new ChunkBuilder.Long()
    b.addOne(first)
    var i = 1
    while (i < n) {
      var found = false
      var q     = 0
      while (q < maxOpen && !found) {
        val qIdx   = (scanStart + q) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == LongSpscRingBuffer.DONE) {
          drainersDone += 1
        } else if (packed != LongSpscRingBuffer.EMPTY) {
          found = true
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          b.addOne(packed)
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
          drainInner(work.asInstanceOf[Stream[Any, Long]], idx)
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

  private def drainInner(innerStream: Stream[Any, Long], drainerIdx: Int): Unit = {
    var innerReader: Reader[Long] = null
    try {
      innerReader = innerStream.compile(0, bufferSize)
      var running = true
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val v = innerReader.readLong(Long.MaxValue)(unsafeEvidence)
        if (v == Long.MaxValue) {
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

private[internal] object LongConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
