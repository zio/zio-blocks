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
 * all assigned inner streams. The consumer counts DONE sentinels and terminates
 * once it has seen `maxOpen` of them — no separate `coordinatorDone` volatile
 * flag is needed on the hot path.
 */
private[streams] final class FloatConcurrentMergeReader(outerReader: Reader[?], maxOpen: Int, bufferSize: Int)
    extends Reader[Float] {
  import ConcurrentMergeReader._
  import FloatConcurrentMergeReader._

  require(maxOpen >= 1, s"FloatConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader[Any] = outerReader.asInstanceOf[Reader[Any]]

  // `dataQueues`, `workQueue` and `drainerLatch` are reassigned on `reset()`
  // so the merge can replay a resettable outer reader (e.g. under `repeated`).
  // All such reassignment happens on the consumer thread only after the
  // previous run's threads have fully terminated (see `reset()`), so
  // single-threaded mutation is safe there.
  private var dataQueues: Array[FloatSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new FloatSpscRingBuffer(bufferSize))
  @volatile private var consumerWaiter: Thread = null

  private val errorRef                          = new AtomicReference[Throwable](null)
  @volatile private var errorDelivered: Boolean = false
  @volatile private var consumerClosed: Boolean = false

  private var workQueue      = new BlockingMpmcQueue[AnyRef](Math.max(maxOpen, 16))
  private var drainerLatch   = new CountDownLatch(maxOpen)
  private val drainerThreads = new AtomicReferenceArray[Thread](maxOpen)

  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]

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
          // A close failure must surface (Principle 4): record it so the
          // consumer rethrows it from read() or close().
          try outer.close()
          catch { case t: Throwable => recordError(t) }
        }
      }
  }

  // Spawns the drainer pool and the coordinator. Called from the constructor
  // and from `reset()` after all per-run fields have been reinitialized (the
  // `Runnable`s read instance fields, so they are reusable across runs).
  private def startThreads(): Unit = {
    Array.tabulate(maxOpen) { idx =>
      Platform.startVirtualThread(
        s"zio-blocks-merge-drainer-${counter.getAndIncrement()}-$idx",
        new Runnable { def run(): Unit = drainerLoop(idx) }
      )
    }
    coordinatorThread = Platform.startVirtualThread(
      s"zio-blocks-merge-coordinator-${counter.getAndIncrement()}",
      coordinatorTask
    )
  }

  startThreads()

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

      if (drainersDone >= maxOpen) {
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
        if (drainersDone < maxOpen && errorRef.get() == null && !consumerClosed) {
          LockSupport.park(this)
        }
      } finally {
        consumerWaiter = null
      }
    }
    sentinel
  }

  def read[A1 >: Float](sentinel: A1): A1 = {
    val v = readFloat(Double.MaxValue)(unsafeEvidence)
    if (v == Double.MaxValue) sentinel
    else Float.box(v.toFloat).asInstanceOf[A1]
  }

  override def readUpToN[A1 >: Float](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = readFloat(Double.MaxValue)(unsafeEvidence)
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

    // A recorded error the consumer never observed via a read (e.g. an inner/
    // outer close failure after the last element) must still surface
    // (Principle 4): rethrow it exactly once at teardown.
    val err = errorRef.get()
    if ((err ne null) && !errorDelivered) { errorDelivered = true; rethrow(err) }
  }

  override def reset(): Unit = {
    // `mergeAll` is a pure fan-in transform: it must not weaken replayability.
    // 1) Fully terminate the current run, exactly as close() does — but discard
    //    any recorded error instead of rethrowing it (reset starts a fresh
    //    run). `Thread.join` establishes happens-before with the coordinator's
    //    and drainers' termination, making the subsequent single-threaded
    //    mutation of the per-run fields safe.
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
    // 2) Replay the outer reader. A genuine one-shot source throws
    //    UnsupportedOperationException here, which correctly propagates: a
    //    merge over a one-shot source is itself one-shot. (The coordinator
    //    already closed `outer` in its finally block; resettable readers
    //    re-enable reads.)
    outer.reset()
    // 3) Reinstate fresh per-run state and respawn the threads.
    dataQueues = Array.tabulate(maxOpen)(_ => new FloatSpscRingBuffer(bufferSize))
    workQueue = new BlockingMpmcQueue[AnyRef](Math.max(maxOpen, 16))
    drainerLatch = new CountDownLatch(maxOpen)
    i = 0
    while (i < maxOpen) {
      drainerThreads.set(i, null)
      i += 1
    }
    errorRef.set(null)
    errorDelivered = false
    consumerClosed = false
    consumerWaiter = null
    scanStart = 0
    drainersDone = 0
    eofReturned = false
    startThreads()
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
    var innerReader: Reader[Float] = null
    try {
      innerReader = innerStream.compile(0, bufferSize)
      var running = true
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val v = innerReader.readFloat(Double.MaxValue)(unsafeEvidence)
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
        // A close failure must surface (Principle 4): record it so the
        // consumer rethrows it from read() or close().
        try innerReader.close()
        catch { case t: Throwable => recordError(t) }
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

  private def rethrow(t: Throwable): Nothing = {
    errorDelivered = true
    t match {
      case se: StreamError => throw se
      case _               => throw t
    }
  }
}

private[internal] object FloatConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
