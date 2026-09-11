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
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader
import zio.blocks.ringbuffer.SpscRingBuffer
import zio.blocks.streams.queues.BlockingMpmcQueue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicLong, AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * Concurrent fan-in reader for `Stream[Any, Stream[Any, A]]`.
 *
 * A single coordinator thread reads inner stream descriptions from
 * `outerReader` and hands them to an eagerly-started pool of drainer threads
 * (up to `maxOpen`). Drainers compile and drain inner streams into per-drainer
 * SPSC queues consumed by this reader.
 */
private[streams] final class ConcurrentMergeReader[A](outerReader: Reader.SyncReader[?], maxOpen: Int, bufferSize: Int)
    extends Reader.SyncReader[A] {
  import ConcurrentMergeReader._

  require(maxOpen >= 1, s"ConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader.SyncReader[Any]               = outerReader.asInstanceOf[Reader.SyncReader[Any]]
  private val outputQueues: Array[SpscRingBuffer[AnyRef]] =
    Array.tabulate(maxOpen)(_ => new SpscRingBuffer[AnyRef](bufferSize))
  @volatile private var consumerWaiter: Thread = null

  private val errorRef                          = new AtomicReference[Throwable](null)
  @volatile private var consumerClosed: Boolean = false
  private val outerClose                        = new ConcurrentShutdown.CloseOnce(outer)
  private val activeReaders                     = new AtomicReferenceArray[ConcurrentShutdown.CloseOnce](maxOpen)
  private val closeLock                         = new AnyRef
  @volatile private var closeDone: Boolean      = false
  private var closeStarted: Boolean             = false
  private var closeLeader: Thread               = null

  private var completedCount: Int = 0

  @volatile private var totalStarted: Int        = 0
  @volatile private var coordinatorDone: Boolean = false

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
          } else if (workQueue.offer(v.asInstanceOf[AnyRef])) {
            totalStarted += 1
          } else {
            running = false
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
          // Publish completion via a volatile flag rather than an AllDone enqueue.
          // The SPSC queues are owned by a single drainer at a time; sending
          // AllDone from a different thread would violate that contract.
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

  private def allOutputQueuesEmpty(): Boolean = {
    var i = 0
    while (i < maxOpen) {
      if (!outputQueues(i).isEmpty) return false
      i += 1
    }
    true
  }

  def isClosed: Boolean =
    consumerClosed || (coordinatorDone && completedCount >= totalStarted && allOutputQueuesEmpty())

  private var scanStart: Int = 0

  private def terminal: Boolean = coordinatorDone && completedCount >= totalStarted

  def read[A1 >: A](sentinel: A1): A1 = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx = (scanStart + i) % maxOpen
        val item = outputQueues(qIdx).take()
        if (item ne null) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          if (item eq NullSentinel) return null.asInstanceOf[A1]
          else if (item eq InnerDone) completedCount += 1
          else return item.asInstanceOf[A1]
        }
        i += 1
      }

      val err = errorRef.get()
      if (err ne null) rethrow(err)
      if (consumerClosed) {
        val closeError = errorRef.get()
        if (closeError ne null) rethrow(closeError)
        return sentinel
      }

      if (terminal) {
        // Re-scan after observing terminal state: items may have been written
        // between the scan above and the visibility of coordinatorDone.
        if (allOutputQueuesEmpty()) {
          val terminalError = errorRef.get()
          if (terminalError ne null) rethrow(terminalError)
          return sentinel
        }
        // else loop again to drain whatever became visible
      } else {
        consumerWaiter = Thread.currentThread()
        try {
          if (!consumerClosed && !terminal && allOutputQueuesEmpty() && errorRef.get() == null) {
            LockSupport.park(this)
          }
        } finally {
          consumerWaiter = null
        }
      }
    }
    sentinel
  }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = read[Any](EndOfStream)
    if (first.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
    if (n == 1) return Chunk.single(first.asInstanceOf[A1])

    val b = ChunkBuilder.make[A1](math.min(n, 16))
    b += first.asInstanceOf[A1]
    var i = 1
    while (i < n) {
      var foundData = false
      var q         = 0
      while (q < maxOpen) {
        val qIdx = (scanStart + q) % maxOpen
        val item = outputQueues(qIdx).take()
        if (item ne null) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          if (item eq NullSentinel) {
            b += null.asInstanceOf[A1]
            i += 1; foundData = true
          } else if (item eq InnerDone) {
            completedCount += 1
          } else {
            b += item.asInstanceOf[A1]
            i += 1; foundData = true
          }
          if (i >= n) return b.result()
        }
        q += 1
      }
      if (!foundData) {
        return b.result()
      }
    }
    b.result()
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
          drainInner(work.asInstanceOf[Stream[Any, A]], idx)
        }
      }
    } finally {
      drainerLatch.countDown()
    }
  }

  private def drainInner(innerStream: Stream[Any, A], drainerIdx: Int): Unit = {
    var innerReader: Reader.SyncReader[A] = null
    try {
      innerReader = innerStream.compile(0, bufferSize) match {
        case sync: Reader.SyncReader[A @unchecked]   => sync
        case async: Reader.AsyncReader[A @unchecked] => async.toSync
      }
      activeReaders.set(drainerIdx, new ConcurrentShutdown.CloseOnce(innerReader))
      var running = true
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val v = innerReader.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) {
          running = false
        } else {
          val wrapped = if (v == null) NullSentinel else v.asInstanceOf[AnyRef]
          var offered = false
          while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
            if (outputQueues(drainerIdx).offer(wrapped)) {
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
      var doneOffered = false
      while (!doneOffered && !consumerClosed && !Thread.currentThread().isInterrupted) {
        if (outputQueues(drainerIdx).offer(InnerDone)) {
          doneOffered = true
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
        } else {
          LockSupport.parkNanos(this, 1000L)
        }
      }
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

private[internal] object ConcurrentMergeReader {
  val counter: AtomicLong      = new AtomicLong(0L)
  val outputQueueCapacity: Int = zio.blocks.streams.Stream.DefaultBufferSize

  val NullSentinel: AnyRef         = new AnyRef
  val InnerDone: AnyRef            = new AnyRef
  val AllDone: AnyRef              = new AnyRef
  private val DoneSentinel: AnyRef = new AnyRef
}
