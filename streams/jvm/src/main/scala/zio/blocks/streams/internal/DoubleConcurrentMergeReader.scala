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
 * Its per-drainer SPSC channels represent empty and done out of band, so every
 * raw `Double` bit pattern is transported without boxing or canonicalization.
 */
private[streams] final class DoubleConcurrentMergeReader(
  outerReader: Reader.SyncReader[?],
  maxOpen: Int,
  bufferSize: Int
) extends Reader.SyncReader[Double] {
  import ConcurrentMergeReader._
  import DoubleConcurrentMergeReader._

  require(maxOpen >= 1, s"DoubleConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")

  private val outer: Reader.SyncReader[Any] = outerReader.asInstanceOf[Reader.SyncReader[Any]]

  private val dataQueues: Array[FullDomainSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new FullDomainSpscRingBuffer(bufferSize))
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

  override def jvmType: JvmType = JvmType.Double

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-thread-private state: scanStart, drainersDone, eofReturned are
  // mutated only from the consumer side and therefore need no atomics.
  private var scanStart: Int       = 0
  private var drainersDone: Int    = 0
  private var eofReturned: Boolean = false
  private val rawScratch           = new Array[Long](1)

  override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double =
    if (readRaw(rawScratch, 0, wait = true) < 0) sentinel else java.lang.Double.longBitsToDouble(rawScratch(0))

  def read[A1 >: Double](sentinel: A1): A1 =
    if (readRaw(rawScratch, 0, wait = true) < 0) sentinel
    else Double.box(java.lang.Double.longBitsToDouble(rawScratch(0))).asInstanceOf[A1]

  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Double <:< Double): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      val first = readRaw(rawScratch, 0, wait = true)
      if (first < 0) -1
      else {
        dest(offset) = java.lang.Double.longBitsToDouble(rawScratch(0))
        var count = 1
        var next  = 1
        while (count < length && next > 0) {
          next = readRaw(rawScratch, 0, wait = false)
          if (next > 0) {
            dest(offset + count) = java.lang.Double.longBitsToDouble(rawScratch(0))
            count += 1
          }
        }
        count
      }
    }
  }

  override def readUpToN[A1 >: Double](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val b     = new ChunkBuilder.Double()
    val first = readRaw(rawScratch, 0, wait = true)
    if (first < 0) return Chunk.empty
    b.addOne(java.lang.Double.longBitsToDouble(rawScratch(0)))
    var i    = 1
    var read = 1
    while (i < n && read > 0) {
      read = readRaw(rawScratch, 0, wait = false)
      if (read > 0) { b.addOne(java.lang.Double.longBitsToDouble(rawScratch(0))); i += 1 }
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
          drainInner(work.asInstanceOf[Stream[Any, Double]], idx)
        }
      }
    } finally {
      dataQueues(idx).offerDone()
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      drainerLatch.countDown()
    }
  }

  private def drainInner(innerStream: Stream[Any, Double], drainerIdx: Int): Unit = {
    var innerReader: Reader.SyncReader[Double] = null
    try {
      innerReader = innerStream.compile(0, bufferSize) match {
        case sync: Reader.SyncReader[Double @unchecked]   => sync
        case async: Reader.AsyncReader[Double @unchecked] => async.toSync
      }
      activeReaders.set(drainerIdx, new ConcurrentShutdown.CloseOnce(innerReader))
      var running = true
      val input   = new Array[Double](1)
      while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
        val count = innerReader.readDoublesPhysical(input, 0, 1)
        if (count < 0) {
          running = false
        } else {
          val rawBits = java.lang.Double.doubleToRawLongBits(input(0))
          var offered = false
          while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
            if (dataQueues(drainerIdx).offer(rawBits)) {
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

  private def readRaw(dest: Array[Long], offset: Int, wait: Boolean): Int = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx   = (scanStart + i) % maxOpen
        val status = dataQueues(qIdx).poll(dest, offset)
        if (status == FullDomainSpscRingBuffer.Done) drainersDone += 1
        else if (status == FullDomainSpscRingBuffer.Data) {
          scanStart = (qIdx + 1) % maxOpen
          val error = errorRef.get()
          if (error ne null) rethrow(error)
          return 1
        }
        i += 1
      }

      val error = errorRef.get()
      if (error ne null) rethrow(error)
      if (consumerClosed || (coordinatorDone && drainersDone >= maxOpen)) {
        eofReturned = true
        return -1
      }
      if (!wait) return 0

      consumerWaiter = Thread.currentThread()
      try {
        if (errorRef.get() == null && !consumerClosed && (!coordinatorDone || drainersDone < maxOpen))
          LockSupport.parkNanos(this, 1000L)
      } finally consumerWaiter = null
    }
    -1
  }

  private def rethrow(t: Throwable): Nothing = t match {
    case _ if !closeDone => close(); throw t
    case se: StreamError => throw se
    case _               => throw t
  }
}

private[internal] object DoubleConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
