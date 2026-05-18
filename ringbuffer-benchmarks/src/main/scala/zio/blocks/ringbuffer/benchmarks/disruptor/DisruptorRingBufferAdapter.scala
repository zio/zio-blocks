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

package zio.blocks.ringbuffer.benchmarks.disruptor

import com.lmax.disruptor.{RingBuffer, EventFactory, BlockingWaitStrategy, InsufficientCapacityException}
import java.util.concurrent.atomic.AtomicLong
import scala.compiletime.uninitialized

// Disruptor requires pre-allocated event objects. Box wrapper holds our element.
private[benchmarks] final class Box[A] {
  var value: A = uninitialized
}

private[benchmarks] object DisruptorRingBufferAdapter {
  private[benchmarks] def boxFactory[A]: EventFactory[Box[A]] = () => new Box[A]()
}

// SPSC: Single Producer, Single Consumer
final class DisruptorSpscRingBuffer[A <: AnyRef](val capacity: Int) {
  import DisruptorRingBufferAdapter._

  private val ring: RingBuffer[Box[A]] =
    RingBuffer.createSingleProducer(boxFactory[A], capacity, new BlockingWaitStrategy())
  private var consumerIdx: Long = 0L

  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    try {
      val seq = ring.tryNext()
      if (seq >= 0) {
        ring.get(seq).value = a
        ring.publish(seq)
        true
      } else false
    } catch {
      case _: InsufficientCapacityException => false
    }
  }

  def take(): A = {
    val c = consumerIdx
    if (c > ring.getCursor) return null.asInstanceOf[A]
    val e   = ring.get(c)
    val res = e.value
    e.value = null.asInstanceOf[A] // clear for GC
    consumerIdx = c + 1
    res
  }
}

// MPSC: Multi Producer, Single Consumer
final class DisruptorMpscRingBuffer[A <: AnyRef](val capacity: Int) {
  import DisruptorRingBufferAdapter._

  private val ring: RingBuffer[Box[A]] =
    RingBuffer.createMultiProducer(boxFactory[A], capacity, new BlockingWaitStrategy())
  private var consumerIdx: Long = 0L

  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    try {
      val seq = ring.tryNext()
      if (seq >= 0) {
        ring.get(seq).value = a
        ring.publish(seq)
        true
      } else false
    } catch {
      case _: InsufficientCapacityException => false
    }
  }

  def take(): A = {
    val c = consumerIdx
    if (c > ring.getCursor) return null.asInstanceOf[A]
    val e   = ring.get(c)
    val res = e.value
    e.value = null.asInstanceOf[A] // clear for GC
    consumerIdx = c + 1
    res
  }
}

// SPMC: Single Producer, Multiple Consumers
// Note: Disruptor not designed for competing consumers; we implement custom consumer CAS loop.
final class DisruptorSpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  import DisruptorRingBufferAdapter._

  private val ring: RingBuffer[Box[A]] =
    RingBuffer.createSingleProducer(boxFactory[A], capacity, new BlockingWaitStrategy())
  private val consumerIdx = new AtomicLong(0L)

  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    try {
      val seq = ring.tryNext()
      if (seq >= 0) {
        ring.get(seq).value = a
        ring.publish(seq)
        true
      } else false
    } catch {
      case _: InsufficientCapacityException => false
    }
  }

  def take(): A = {
    var c = consumerIdx.get()
    while (true) {
      val published = ring.getCursor
      if (c > published) return null.asInstanceOf[A] // empty
      val e   = ring.get(c)
      val res = e.value
      if (consumerIdx.compareAndSet(c, c + 1)) {
        // Do NOT clear: producer will overwrite on next lap (matches ZIO SpmcRingBuffer)
        return res
      } else {
        c = consumerIdx.get() // CAS failed, retry with latest value
      }
    }
    null.asInstanceOf[A] // unreachable
  }
}

// MPMC: Multi Producer, Multi Consumer
final class DisruptorMpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  import DisruptorRingBufferAdapter._

  private val ring: RingBuffer[Box[A]] =
    RingBuffer.createMultiProducer(boxFactory[A], capacity, new BlockingWaitStrategy())
  private val consumerIdx = new AtomicLong(0L)

  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    try {
      val seq = ring.tryNext()
      if (seq >= 0) {
        ring.get(seq).value = a
        ring.publish(seq)
        true
      } else false
    } catch {
      case _: InsufficientCapacityException => false
    }
  }

  def take(): A = {
    var c = consumerIdx.get()
    while (true) {
      val published = ring.getCursor
      if (c > published) return null.asInstanceOf[A]
      val e   = ring.get(c)
      val res = e.value
      if (consumerIdx.compareAndSet(c, c + 1)) {
        e.value = null.asInstanceOf[A] // clear for GC
        return res
      } else {
        c = consumerIdx.get()
      }
    }
    null.asInstanceOf[A] // unreachable
  }
}
