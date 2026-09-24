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

import zio.blocks.ringbuffer.IntSpscRingBuffer
import zio.blocks.streams.{JvmType, Platform, Stream}
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingMpmcQueue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Int`-specialized concurrent merge reader (used by `mergeAll` /
 * `flatMapPar`).
 *
 * Uses `IntSpscRingBuffer` for the per-drainer data channel, which packs each
 * `Int` payload alongside a non-zero tag in a `Long` slot: `EMPTY_PACKED`
 * (`0L`) signals an empty slot and `DONE_PACKED` (`2L << 32`) is the in-band
 * end-of-stream sentinel. Any other value is real data whose payload is
 * `packed.toInt`.
 *
 * Each drainer writes one in-band DONE to its data queue when it has finished
 * all assigned inner streams. The consumer counts DONE sentinels, then waits
 * for the coordinator to close the outer reader before terminating.
 */
private[streams] final class IntConcurrentMergeReader[A](
  outerReader: Reader.SyncReader[?],
  maxOpen: Int,
  bufferSize: Int,
  laneType: JvmType
) extends Reader.SyncReader[A] {
  import ConcurrentMergeReader._
  import IntConcurrentMergeReader._

  require(maxOpen >= 1, s"IntConcurrentMergeReader requires maxOpen >= 1, got $maxOpen")
  require(
    laneType == JvmType.Boolean || laneType == JvmType.Byte || laneType == JvmType.Char ||
      laneType == JvmType.Short || laneType == JvmType.Int,
    s"IntConcurrentMergeReader requires an int-like JVM type, got $laneType"
  )

  private val outer: Reader.SyncReader[Any] = outerReader.asInstanceOf[Reader.SyncReader[Any]]

  private val dataQueues: Array[IntSpscRingBuffer] =
    Array.tabulate(maxOpen)(_ => new IntSpscRingBuffer(bufferSize))
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

  override def jvmType: JvmType = laneType

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-thread-private state: scanStart, drainersDone, eofReturned are
  // mutated only from the consumer side and therefore need no atomics.
  private var scanStart: Int       = 0
  private var drainersDone: Int    = 0
  private var eofReturned: Boolean = false

  private def readIntLike(sentinel: Long): Long = {
    while (true) {
      var i = 0
      while (i < maxOpen) {
        val qIdx   = (scanStart + i) % maxOpen
        val packed = dataQueues(qIdx).pollPacked()
        if (packed == IntSpscRingBuffer.DONE_PACKED) {
          drainersDone += 1
        } else if (packed != IntSpscRingBuffer.EMPTY_PACKED) {
          scanStart = (qIdx + 1) % maxOpen
          val err = errorRef.get()
          if (err ne null) rethrow(err)
          return packed.toInt.toLong
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
        var datum = 0
        while (k < maxOpen && !found) {
          val qIdx2   = (scanStart + k) % maxOpen
          val packed2 = dataQueues(qIdx2).pollPacked()
          if (packed2 == IntSpscRingBuffer.DONE_PACKED) {
            drainersDone += 1
          } else if (packed2 != IntSpscRingBuffer.EMPTY_PACKED) {
            scanStart = (qIdx2 + 1) % maxOpen
            datum = packed2.toInt
            found = true
          }
          k += 1
        }
        if (found) {
          consumerWaiter = null
          val err2 = errorRef.get()
          if (err2 ne null) rethrow(err2)
          return datum.toLong
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

  override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
    val value = readIntLike(-1L)
    if (value < 0L) sentinel else value.toInt
  }

  override def readByte(): Int = readIntLike(-1L).toInt

  override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int = {
    val value = readIntLike(-1L)
    if (value < 0L) sentinel else value.toInt
  }

  override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = {
    val value = readIntLike(Int.MinValue.toLong)
    if (value == Int.MinValue.toLong) sentinel else value.toInt
  }

  override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
    val value = readIntLike(Long.MinValue)
    if (value == Long.MinValue) sentinel else value
  }

  def read[A1 >: A](sentinel: A1): A1 = {
    val v = readIntLike(Long.MinValue)
    if (v == Long.MinValue) sentinel
    else {
      val value: Any = laneType match {
        case JvmType.Boolean => Boolean.box(v != 0)
        case JvmType.Byte    => Byte.box(v.toByte)
        case JvmType.Char    => Char.box(v.toChar)
        case JvmType.Short   => Short.box(v.toShort)
        case _               => Int.box(v.toInt)
      }
      value.asInstanceOf[A1]
    }
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
        val v = laneType match {
          case JvmType.Boolean => innerReader.readBooleanPhysical(-1).toLong
          case JvmType.Byte    => innerReader.readBytePhysical().toLong
          case JvmType.Char    => innerReader.readCharPhysical(-1).toLong
          case JvmType.Short   => innerReader.readShortPhysical(Int.MinValue).toLong
          case _               => innerReader.readIntPhysical(Long.MinValue)
        }
        val end = laneType match {
          case JvmType.Boolean | JvmType.Byte | JvmType.Char => v < 0L
          case JvmType.Short                                 => v == Int.MinValue.toLong
          case _                                             => v == Long.MinValue
        }
        if (end) {
          running = false
        } else {
          var offered = false
          while (!offered && !consumerClosed && !Thread.currentThread().isInterrupted) {
            if (dataQueues(drainerIdx).offer(v.toInt)) {
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

private[internal] object IntConcurrentMergeReader {
  private val DoneSentinel: AnyRef = new AnyRef
}
