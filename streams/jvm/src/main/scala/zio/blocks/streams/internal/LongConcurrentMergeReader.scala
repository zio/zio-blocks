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

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Long`-specialized concurrent merge reader (used by `mergeAll` /
 * `flatMapPar`).
 *
 * Uses `LongSpscRingBuffer` for the per-drainer data channel, which reserves
 * `Long.MinValue` as an empty-slot sentinel and `Long.MinValue + 1L` as the
 * in-band end-of-stream sentinel. Those two values cannot be stored directly in
 * the ring, but they ARE valid `Long` stream elements (unlike `Double`'s
 * reserved NaN bit patterns, which are unobservable). To keep `mergeAll` /
 * `flatMapPar` lossless for every `Long`, reserved element values are routed
 * out-of-band through a per-drainer escape queue and signalled in the ring with
 * the existing `DONE` token:
 *
 *   - Producer: a non-reserved value `v` (i.e. `v > DONE`) is written with the
 *     unchecked `offerNonReserved` (one comparison on the hot path). A reserved
 *     value is enqueued into that drainer's `dataEscapes` queue and then a
 *     `DONE` token is written into the ring at the correct FIFO position.
 *   - Consumer: on polling `DONE`, it first polls `dataEscapes(qIdx)`; a
 *     non-null payload is the escaped element value (emit it, even if it equals
 *     a reserved sentinel), while `null` is the real end-of-drainer marker.
 *
 * The `setRelease`/`getAcquire` pair on the ring slot plus the concurrent
 * escape queue provide the happens-before edge so the consumer never sees a
 * `DONE` token before the corresponding escape payload is visible.
 *
 * Each drainer writes one real (unescaped) `DONE` to its data queue when it has
 * finished all assigned inner streams. The consumer counts those real `DONE`
 * markers and terminates once it has seen `maxOpen` of them — no separate
 * `coordinatorDone` volatile flag is needed on the hot path.
 */
private[streams] final class LongConcurrentMergeReader(outerReader: Reader[?], maxOpen: Int, bufferSize: Int)
    extends Reader[Long] {
  import ConcurrentMergeReader._
  import LongConcurrentMergeReader._

  require(maxOpen >= 1, s"LongConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader[Any] = outerReader.asInstanceOf[Reader[Any]]

  // `dataQueues`, `dataEscapes`, `workQueue` and `drainerLatch` are reassigned
  // on `reset()` so the merge can replay a resettable outer reader (e.g. under
  // `repeated`). All such reassignment happens on the consumer thread only
  // after the previous run's threads have fully terminated (see `reset()`), so
  // single-threaded mutation is safe there.
  private var dataQueues: Array[LongSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new LongSpscRingBuffer(bufferSize))
  // Per-drainer out-of-band channel for the (rare) reserved element values
  // Long.MinValue / Long.MinValue+1 that cannot be stored in the ring directly.
  private var dataEscapes: Array[ConcurrentLinkedQueue[java.lang.Long]] =
    Array.tabulate(maxOpen)(_ => new ConcurrentLinkedQueue[java.lang.Long]())
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
          // Rare path: real end-of-drainer or an escaped reserved element value.
          val esc = dataEscapes(qIdx).poll()
          if (esc ne null) {
            scanStart = (qIdx + 1) % maxOpen
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            markReadValue()
            return esc.longValue()
          } else drainersDone += 1
        } else if (packed != LongSpscRingBuffer.EMPTY) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          markReadValue()
          return packed
        }
        i += 1
      }

      val err = errorRef.get()
      if (err ne null) rethrow(err)

      if (drainersDone >= maxOpen) {
        eofReturned = true
        markReadEOF()
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
            val esc = dataEscapes(qIdx2).poll()
            if (esc ne null) {
              scanStart = (qIdx2 + 1) % maxOpen
              datum = esc.longValue()
              found = true
            } else drainersDone += 1
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
          markReadValue()
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
    if (longEOF(this, v, Long.MaxValue)) sentinel
    else Long.box(v).asInstanceOf[A1]
  }

  override def readUpToN[A1 >: Long](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = readLong(Long.MaxValue)(unsafeEvidence)
    if (longEOF(this, first, Long.MaxValue)) return Chunk.empty
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
          // Rare path: real end-of-drainer or an escaped reserved element value.
          val esc = dataEscapes(qIdx).poll()
          if (esc ne null) {
            found = true
            scanStart = (qIdx + 1) % maxOpen
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            b.addOne(esc.longValue())
            i += 1
          } else drainersDone += 1
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
    dataQueues = Array.tabulate(maxOpen)(_ => new LongSpscRingBuffer(bufferSize))
    dataEscapes = Array.tabulate(maxOpen)(_ => new ConcurrentLinkedQueue[java.lang.Long]())
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
        if (longEOF(innerReader, v, Long.MaxValue)) {
          running = false
        } else {
          if (!offerValue(drainerIdx, v)) running = false
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

  /**
   * Hand off one inner element to drainer `drainerIdx`'s channel, park-spinning
   * until accepted or the reader is shutting down. Non-reserved values take the
   * hot path (`offerNonReserved`, one comparison). Reserved values
   * (`Long.MinValue` / `Long.MinValue+1`) are enqueued once into the escape
   * queue and then signalled with a `DONE` token at the matching FIFO position.
   * Returns `true` iff the value was handed off.
   *
   * The reserved path uses `offerDoneAfter`, which enqueues the escape value
   * only when a slot is available and immediately before publishing the `DONE`
   * token, so the escape is never enqueued without a matching token (no orphan
   * even on shutdown).
   */
  private def offerValue(drainerIdx: Int, v: Long): Boolean = {
    val q = dataQueues(drainerIdx)
    if (v > LongSpscRingBuffer.DONE) {
      var offered = false
      while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
        if (q.offerNonReserved(v)) {
          offered = true
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
        } else {
          LockSupport.parkNanos(this, 1000L)
        }
      }
      offered
    } else {
      val boxed   = java.lang.Long.valueOf(v)
      var offered = false
      while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
        if (q.offerDoneAfter(dataEscapes(drainerIdx).offer(boxed): Unit)) {
          offered = true
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
        } else {
          LockSupport.parkNanos(this, 1000L)
        }
      }
      offered
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

private[internal] object LongConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
