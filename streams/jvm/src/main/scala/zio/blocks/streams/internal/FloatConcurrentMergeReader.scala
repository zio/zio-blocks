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
import zio.blocks.ringbuffer.FloatSpscRingBuffer
import zio.blocks.streams.{JvmType, Platform, Stream}
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingMpmcQueue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Float`-specialized concurrent merge reader (used by `mergeAll` /
 * `flatMapPar`).
 *
 * Uses `FloatSpscRingBuffer` for the per-drainer data channel, which packs each
 * `Float`'s raw bits alongside a non-zero tag in a `Long` slot: `EMPTY_PACKED`
 * (`0L`) signals an empty slot and `DONE_PACKED` (`2L << 32`) is the in-band
 * end-of-stream sentinel. Any other value is real data whose payload is
 * `java.lang.Float.intBitsToFloat(packed.toInt)`.
 *
 * Each drainer writes one in-band DONE to its data queue when it has finished
 * all assigned inner streams. The consumer counts DONE sentinels, then waits
 * for the coordinator to close the outer reader before terminating.
 */
private[streams] final class FloatConcurrentMergeReader(
  outerReader: Reader.SyncReader[?],
  maxOpen: Int,
  bufferSize: Int
) extends Reader.SyncReader[Float] {
  import ConcurrentMergeReader._
  import FloatConcurrentMergeReader._

  require(maxOpen >= 1, s"FloatConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader.SyncReader[Any] = outerReader.asInstanceOf[Reader.SyncReader[Any]]

  private val dataQueues: Array[FloatSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new FloatSpscRingBuffer(bufferSize))
  @volatile private var consumerWaiter: Thread = null

  private val errorRef                          = new AtomicReference[Throwable](null)
  @volatile private var consumerClosed: Boolean = false
  private val outerClose                        = new ConcurrentShutdown.CloseOnce(outer)
  private val activeReaders                     = new AtomicReferenceArray[ConcurrentShutdown.CloseOnce](maxOpen)
  private val closeLock                         = new AnyRef
  @volatile private var closeDone: Boolean      = false
  private var closeStarted: Boolean             = false
  private var closeLeader: Thread               = null

  private val workQueue      = new BlockingMpmcQueue[AnyRef](Math.max(maxOpen, 16))
  private val drainerLatch   = new CountDownLatch(maxOpen)
  private val drainerThreads = new AtomicReferenceArray[Thread](maxOpen)

  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]
  @volatile private var coordinatorDone: Boolean  = false

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
          outerClose.close(recordError)
          coordinatorDone = true
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
        }
      }
  }

  coordinatorThread = Platform.startVirtualThread(
    s"zio-blocks-merge-coordinator-${counter.getAndIncrement()}",
    coordinatorTask
  )

  override def jvmType: JvmType = JvmType.Float

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-thread-private state: scanStart, drainersDone, eofReturned are
  // mutated only from the consumer side and therefore need no atomics.
  private var scanStart: Int       = 0
  private var drainersDone: Int    = 0
  private var eofReturned: Boolean = false

  override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Double = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx   = (scanStart + i) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == FloatSpscRingBuffer.DONE_PACKED) {
          drainersDone += 1
        } else if (packed != FloatSpscRingBuffer.EMPTY_PACKED) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          return java.lang.Float.intBitsToFloat(packed.toInt).toDouble
        }
        i += 1
      }

      val err = errorRef.get()
      if (err ne null) rethrow(err)
      if (consumerClosed) {
        val closeError = errorRef.get()
        if (closeError ne null) rethrow(closeError)
        eofReturned = true
        return sentinel
      }

      if (coordinatorDone && drainersDone >= maxOpen) {
        val terminalError = errorRef.get()
        if (terminalError ne null) rethrow(terminalError)
        eofReturned = true
        return sentinel
      }

      consumerWaiter = Thread.currentThread()
      try {
        // Re-poll after registering as waiter (avoid lost wakeup).
        var k     = 0
        var found = false
        var datum = 0.0f
        while (k < maxOpen && !found) {
          val qIdx2   = (scanStart + k) % maxOpen
          val packed2 = dataQueues(qIdx2).pollPacked()
          if (packed2 == FloatSpscRingBuffer.DONE_PACKED) {
            drainersDone += 1
          } else if (packed2 != FloatSpscRingBuffer.EMPTY_PACKED) {
            scanStart = (qIdx2 + 1) % maxOpen
            datum = java.lang.Float.intBitsToFloat(packed2.toInt)
            found = true
          }
          k += 1
        }
        if (found) {
          consumerWaiter = null
          val err2 = errorRef.get()
          if (err2 ne null) rethrow(err2)
          return datum.toDouble
        }
        if ((!coordinatorDone || drainersDone < maxOpen) && errorRef.get() == null && !consumerClosed) {
          LockSupport.park(this)
        }
      } finally {
        consumerWaiter = null
      }
    }
    sentinel
  }

  def read[A1 >: Float](sentinel: A1): A1 = {
    val v = readFloat(Double.MaxValue)
    if (v == Double.MaxValue) sentinel
    else Float.box(v.toFloat).asInstanceOf[A1]
  }

  override def readUpToN[A1 >: Float](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = readFloat(Double.MaxValue)
    if (first == Double.MaxValue) return Chunk.empty
    if (n == 1) return Chunk.single(first.toFloat.asInstanceOf[A1])

    val b = new ChunkBuilder.Float()
    b.addOne(first.toFloat)
    var i = 1
    while (i < n) {
      var found = false
      var q     = 0
      while (q < maxOpen && !found) {
        val qIdx   = (scanStart + q) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == FloatSpscRingBuffer.DONE_PACKED) {
          drainersDone += 1
        } else if (packed != FloatSpscRingBuffer.EMPTY_PACKED) {
          found = true
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          b.addOne(java.lang.Float.intBitsToFloat(packed.toInt))
          i += 1
        }
        q += 1
      }
      if (!found) return b.result().asInstanceOf[Chunk[A1]]
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  def close(): Unit = {
    val self   = Thread.currentThread()
    val leader = closeLock.synchronized {
      if (!closeStarted) { closeStarted = true; closeLeader = self; consumerClosed = true; true }
      else false
    }
    var interrupted: InterruptedException = null
    if (leader) try {
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      workQueue.close()
      coordinatorThread.interrupt()
      var i = 0
      while (i < maxOpen) {
        val t = drainerThreads.get(i)
        if (t ne null) t.interrupt()
        i += 1
      }
      outerClose.close(recordError)
      i = 0
      while (i < maxOpen) { val active = activeReaders.get(i); if (active ne null) active.close(recordError); i += 1 }
      interrupted = ConcurrentShutdown.join(coordinatorThread, interrupted)

      i = 0
      while (i < maxOpen) {
        interrupted = ConcurrentShutdown.join(drainerThreads.get(i), interrupted)
        i += 1
      }
    } finally closeLock.synchronized { closeDone = true; closeLeader = null; closeLock.notifyAll() }
    else if (closeLeader ne self) closeLock.synchronized {
      while (!closeDone)
        try closeLock.wait()
        catch { case cause: InterruptedException => if (interrupted eq null) interrupted = cause }
    }
    ConcurrentShutdown.replay(errorRef.get(), interrupted)
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
          drainInner(work.asInstanceOf[Stream[Any, Float]], idx)
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

  private def drainInner(innerStream: Stream[Any, Float], drainerIdx: Int): Unit = {
    var innerReader: Reader.SyncReader[Float] = null
    try {
      innerReader = innerStream.compile(0, bufferSize) match {
        case sync: Reader.SyncReader[Float @unchecked]   => sync
        case async: Reader.AsyncReader[Float @unchecked] => async.toSync
      }
      activeReaders.set(drainerIdx, new ConcurrentShutdown.CloseOnce(innerReader))
      var running = true
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val v = innerReader.readFloatPhysical(Double.MaxValue)
        if (v == Double.MaxValue) {
          running = false
        } else {
          var offered = false
          while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
            if (dataQueues(drainerIdx).offer(v.toFloat)) {
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
        val active = activeReaders.getAndSet(drainerIdx, null)
        if (active ne null) active.close(recordError)
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
    } else {
      val primary = errorRef.get()
      if ((primary ne null) && (primary ne t)) primary.addSuppressed(t)
    }

  private def rethrow(t: Throwable): Nothing = t match {
    case _ if !closeDone => close(); throw t
    case se: StreamError => throw se
    case _               => throw t
  }
}

private[internal] object FloatConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
