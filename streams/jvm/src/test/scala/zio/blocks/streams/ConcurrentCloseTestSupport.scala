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

package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

private[streams] object ConcurrentCloseTestSupport {
  final class CloseHookReader[A](override val jvmType: JvmType, closeFailure: Throwable) extends Reader.SyncReader[A] {
    val readEntered  = new CountDownLatch(1)
    val closeEntered = new CountDownLatch(1)
    val closeExited  = new CountDownLatch(1)
    val releaseClose = new CountDownLatch(1)
    val releaseRead  = new CountDownLatch(1)
    val closes       = new AtomicInteger(0)
    private val wake = new CountDownLatch(1)

    def isClosed: Boolean                                                                         = false
    def read[A1 >: A](sentinel: A1): A1                                                           = { blockUntilClose(); sentinel }
    override def readUpToN[A1 >: A](n: Int): Chunk[A1]                                            = { blockUntilClose(); Chunk.empty }
    override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: A <:< Int): Int = {
      blockUntilClose(); -1
    }
    override def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: A <:< Long): Int = {
      blockUntilClose(); -1
    }
    override def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: A <:< Float): Int = {
      blockUntilClose(); -1
    }
    override def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: A <:< Double): Int = {
      blockUntilClose(); -1
    }

    def close(): Unit = {
      closes.incrementAndGet()
      wake.countDown()
      closeEntered.countDown()
      awaitIgnoringInterrupt(releaseClose)
      closeExited.countDown()
      if (closeFailure ne null) throw closeFailure
    }

    private def blockUntilClose(): Unit = {
      readEntered.countDown()
      awaitIgnoringInterrupt(wake)
      awaitIgnoringInterrupt(releaseRead)
    }
  }

  final case class CloseRaceResult(
    entered: Boolean,
    secondWaited: Boolean,
    completed: Boolean,
    threadsStopped: Boolean,
    firstFailure: Throwable,
    secondFailure: Throwable,
    firstInterrupted: Boolean,
    secondInterrupted: Boolean
  )

  def raceClose(reader: Reader.SyncReader[?], hook: CloseHookReader[?]): CloseRaceResult = {
    val firstDone         = new CountDownLatch(1)
    val secondDone        = new CountDownLatch(1)
    val firstFailure      = new AtomicReference[Throwable](null)
    val secondFailure     = new AtomicReference[Throwable](null)
    val firstInterrupted  = new AtomicBoolean(false)
    val secondInterrupted = new AtomicBoolean(false)
    val first             = new Thread(() => {
      try reader.close()
      catch { case cause: Throwable => firstFailure.set(cause) }
      finally {
        firstInterrupted.set(Thread.currentThread().isInterrupted)
        firstDone.countDown()
      }
    })
    val second = new Thread(() => {
      try reader.close()
      catch { case cause: Throwable => secondFailure.set(cause) }
      finally {
        secondInterrupted.set(Thread.currentThread().isInterrupted)
        secondDone.countDown()
      }
    })
    first.setDaemon(true)
    second.setDaemon(true)

    first.start()
    val entered = hook.closeEntered.await(2, TimeUnit.SECONDS)
    first.interrupt()
    second.start()
    val secondWaited = !secondDone.await(100, TimeUnit.MILLISECONDS)
    hook.releaseClose.countDown()
    hook.closeExited.await(1, TimeUnit.SECONDS)
    val joinDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
    while (
      first.getState != Thread.State.WAITING && first.getState != Thread.State.TIMED_WAITING &&
      System.nanoTime() < joinDeadline
    ) Thread.onSpinWait()
    hook.releaseRead.countDown()
    val completed = firstDone.await(2, TimeUnit.SECONDS) && secondDone.await(2, TimeUnit.SECONDS)
    first.join(1000)
    second.join(1000)
    CloseRaceResult(
      entered,
      secondWaited,
      completed,
      !first.isAlive && !second.isAlive,
      firstFailure.get(),
      secondFailure.get(),
      firstInterrupted.get(),
      secondInterrupted.get()
    )
  }

  def correctFailure(result: CloseRaceResult, closeFailure: Throwable): Boolean = {
    val correct = result.entered && result.secondWaited && result.completed && result.threadsStopped &&
      (result.firstFailure eq closeFailure) && (result.secondFailure eq closeFailure) &&
      result.firstInterrupted && !result.secondInterrupted &&
      closeFailure.getSuppressed.toList.size == 1 && closeFailure.getSuppressed.head.isInstanceOf[InterruptedException]
    correct
  }

  private def awaitIgnoringInterrupt(latch: CountDownLatch): Unit = {
    var waiting     = true
    var interrupted = false
    while (waiting)
      try { latch.await(); waiting = false }
      catch { case _: InterruptedException => interrupted = true }
    if (interrupted) Thread.currentThread().interrupt()
  }
}
