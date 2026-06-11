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

import zio.blocks.streams.Platform
import zio.blocks.streams.io.Reader
import zio.blocks.ringbuffer.SpscRingBuffer

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

private[streams] final class ConcurrentMapParReader[A, B](
  upstream: Reader[A],
  n: Int,
  f: A => B,
  bufferSize: Int
) extends Reader[B] {
  import ConcurrentMapParReader._

  require(n >= 1, s"ConcurrentMapParReader requires n >= 1, got $n")

  // `outputQueues` and `inputQueues` are reassigned on `reset()` so `mapPar`
  // can replay a resettable upstream (e.g. under `repeated`). All such
  // reassignment happens on the consumer thread only after the previous run's
  // threads have fully terminated (see `reset()`), so single-threaded mutation
  // is safe there.
  private var outputQueues: Array[SpscRingBuffer[AnyRef]] =
    Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
  @volatile private var consumerWaiter: Thread = null

  private var inputQueues: Array[SpscRingBuffer[AnyRef]] =
    Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
  private val workerWaiters  = new AtomicReferenceArray[Thread](n)
  private val workerThreads  = new AtomicReferenceArray[Thread](n)
  private val workersRunning = new AtomicInteger(n)

  private val errorRef                            = new AtomicReference[Throwable](null)
  @volatile private var errorDelivered: Boolean   = false
  @volatile private var consumerClosed: Boolean   = false
  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]

  private val workerTasks: Array[Runnable] = Array.tabulate(n) { idx =>
    new Runnable {
      def run(): Unit = workerLoop(idx)
    }
  }

  private val coordinatorTask: Runnable = new Runnable {
    def run(): Unit =
      try {
        var scanIdx = 0
        var running = true

        while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
          val batch = upstream.readUpToN[Any](bufferSize)
          if (batch.isEmpty) {
            running = false
          } else {
            var j = 0
            while (j < batch.length && running && !consumerClosed && !Thread.currentThread().isInterrupted) {
              val v          = batch(j)
              val wrapped    = if (v == null) NullWorkSentinel else v.asInstanceOf[AnyRef]
              var dispatched = false
              while (!dispatched && !consumerClosed && !Thread.currentThread().isInterrupted) {
                var i = 0
                while (i < n && !dispatched) {
                  val qIdx = (scanIdx + i) % n
                  if (inputQueues(qIdx).offer(wrapped)) {
                    val ww = workerWaiters.get(qIdx)
                    if (ww ne null) LockSupport.unpark(ww)
                    scanIdx = (qIdx + 1) % n
                    dispatched = true
                  }
                  i += 1
                }
                if (!dispatched) {
                  LockSupport.parkNanos(this, 1000L)
                }
              }
              if (!dispatched) running = false
              j += 1
            }
          }
        }
      } catch {
        case t: Throwable =>
          recordError(t)
      } finally {
        signalWorkersToStop()
        // A close failure must surface (Principle 4): record it so the
        // consumer rethrows it from read() or close().
        try upstream.close()
        catch { case t: Throwable => recordError(t) }
      }
  }

  // Spawns the worker pool and the coordinator. Called from the constructor
  // and from `reset()` after all per-run fields have been reinitialized (the
  // `Runnable`s read instance fields, so they are reusable across runs).
  private def startThreads(): Unit = {
    Array.tabulate(n) { idx =>
      Platform.startVirtualThread(
        s"zio-blocks-mappar-worker-${counter.getAndIncrement()}-$idx",
        workerTasks(idx)
      )
    }
    coordinatorThread = Platform.startVirtualThread(
      s"zio-blocks-mappar-coordinator-${counter.getAndIncrement()}",
      coordinatorTask
    )
  }

  startThreads()

  private def allOutputQueuesEmpty(): Boolean = {
    var i = 0
    while (i < n) {
      if (!outputQueues(i).isEmpty) return false
      i += 1
    }
    true
  }

  def isClosed: Boolean = eofReturned || consumerClosed

  private var scanStart: Int          = 0
  private var doneSignalSeen: Boolean = false
  private var eofReturned: Boolean    = false

  def read[B1 >: B](sentinel: B1): B1 = {
    while (true) {
      var i      = 0
      var sawAny = false
      while (i < n) {
        val qIdx = (scanStart + i) % n
        val item = outputQueues(qIdx).take()
        if (item ne null) {
          sawAny = true
          scanStart = (qIdx + 1) % n
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          if (item eq NullSentinel) return null.asInstanceOf[B1]
          if (item eq AllWorkersDone) doneSignalSeen = true
          else return item.asInstanceOf[B1]
        }
        i += 1
      }

      val err = errorRef.get()
      if (err ne null) rethrow(err)
      if (doneSignalSeen && !sawAny) { eofReturned = true; return sentinel }

      consumerWaiter = Thread.currentThread()
      if (!doneSignalSeen && allOutputQueuesEmpty() && errorRef.get() == null) {
        LockSupport.park(this)
      }
      consumerWaiter = null
    }
    sentinel
  }

  def close(): Unit = {
    consumerClosed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    var i = 0
    while (i < n) {
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }

    val ct = coordinatorThread
    if (ct ne null) {
      ct.interrupt()
      ct.join(5000)
    }

    i = 0
    while (i < n) {
      val t = workerThreads.get(i)
      if (t ne null) {
        t.interrupt()
        t.join(5000)
      }
      i += 1
    }

    // A recorded error the consumer never observed via a read (e.g. an
    // upstream close failure after the last element) must still surface
    // (Principle 4): rethrow it exactly once at teardown.
    val err = errorRef.get()
    if ((err ne null) && !errorDelivered) { errorDelivered = true; rethrow(err) }
  }

  override def reset(): Unit = {
    // `mapPar` is a pure decoupling transform: it must not weaken
    // replayability.
    // 1) Fully terminate the current run, exactly as close() does — but discard
    //    any recorded error instead of rethrowing it (reset starts a fresh
    //    run). `Thread.join` establishes happens-before with the coordinator's
    //    and workers' termination, making the subsequent single-threaded
    //    mutation of the per-run fields safe.
    consumerClosed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    var i = 0
    while (i < n) {
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }
    val ct = coordinatorThread
    if (ct ne null) {
      ct.interrupt()
      ct.join(5000)
    }
    i = 0
    while (i < n) {
      val t = workerThreads.get(i)
      if (t ne null) {
        t.interrupt()
        t.join(5000)
      }
      i += 1
    }
    // 2) Replay the upstream. A genuine one-shot source throws
    //    UnsupportedOperationException here, which correctly propagates: a
    //    mapPar over a one-shot source is itself one-shot. (The coordinator
    //    already closed `upstream` in its finally block; resettable readers
    //    re-enable reads.)
    upstream.reset()
    // 3) Reinstate fresh per-run state and respawn the threads.
    outputQueues = Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
    inputQueues = Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
    i = 0
    while (i < n) {
      workerWaiters.set(i, null)
      workerThreads.set(i, null)
      i += 1
    }
    workersRunning.set(n)
    errorRef.set(null)
    errorDelivered = false
    consumerClosed = false
    consumerWaiter = null
    scanStart = 0
    doneSignalSeen = false
    eofReturned = false
    startThreads()
  }

  private def workerLoop(idx: Int): Unit = {
    val self = Thread.currentThread()
    workerThreads.set(idx, self)

    var keepRunning = true
    while (keepRunning && !consumerClosed && !self.isInterrupted) {
      var input: AnyRef = null
      while ((input eq null) && keepRunning && !consumerClosed && !self.isInterrupted) {
        input = inputQueues(idx).take()
        if (input eq null) {
          workerWaiters.set(idx, self)
          input = inputQueues(idx).take()
          if (input eq null) {
            if (!consumerClosed && !self.isInterrupted) {
              LockSupport.park(this)
            }
          }
          workerWaiters.set(idx, null)
        }
      }

      if ((input eq null) || (input eq DoneSentinel) || consumerClosed || self.isInterrupted) {
        keepRunning = false
      } else {
        try {
          val element     = if (input eq NullWorkSentinel) null.asInstanceOf[A] else input.asInstanceOf[A]
          val result      = f(element)
          val wrapped     = if (result == null) NullSentinel else result.asInstanceOf[AnyRef]
          var spscOffered = false
          while (!spscOffered) {
            if (consumerClosed || self.isInterrupted) {
              keepRunning = false
              spscOffered = true
            } else if (outputQueues(idx).offer(wrapped)) {
              spscOffered = true
              val cw = consumerWaiter
              if (cw ne null) LockSupport.unpark(cw)
            } else {
              LockSupport.parkNanos(this, 1000L)
            }
          }
        } catch {
          case t: Throwable =>
            recordError(t)
            keepRunning = false
        }
      }
    }

    if (workersRunning.decrementAndGet() == 0) {
      var done = false
      while (!done) {
        if (consumerClosed || Thread.currentThread().isInterrupted) {
          done = true
        } else if (outputQueues(idx).offer(AllWorkersDone)) {
          done = true
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
        } else {
          LockSupport.parkNanos(this, 1000L)
        }
      }
    }
  }

  private def signalWorkersToStop(): Unit = {
    var i = 0
    while (i < n) {
      while (!inputQueues(i).offer(DoneSentinel) && !consumerClosed && !Thread.currentThread().isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }
  }

  private def recordError(t: Throwable): Unit =
    if (errorRef.compareAndSet(null, t)) {
      consumerClosed = true
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      var i = 0
      while (i < n) {
        val ww = workerWaiters.get(i)
        if (ww ne null) LockSupport.unpark(ww)
        i += 1
      }

      val ct = coordinatorThread
      if (ct ne null) ct.interrupt()

      i = 0
      while (i < n) {
        val wt = workerThreads.get(i)
        if (wt ne null) wt.interrupt()
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

private[internal] object ConcurrentMapParReader {
  val counter: AtomicLong       = new AtomicLong(0L)
  val inputQueueCapacity: Int   = zio.blocks.streams.Stream.DefaultBufferSize
  val outputQueueCapacity: Int  = zio.blocks.streams.Stream.DefaultBufferSize
  val coordinatorBatchSize: Int = zio.blocks.streams.Stream.DefaultBufferSize
  val NullSentinel: AnyRef      = new AnyRef
  val NullWorkSentinel: AnyRef  = new AnyRef
  val AllWorkersDone: AnyRef    = new AnyRef
  val DoneSentinel: AnyRef      = new AnyRef
}
