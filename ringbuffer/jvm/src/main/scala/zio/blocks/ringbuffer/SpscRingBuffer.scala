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

package zio.blocks.ringbuffer

import java.lang.invoke.{MethodHandles, VarHandle}

// Cache-line padding hierarchy (16 longs = 128 bytes per region, full cache line on Apple Silicon).
// Prevents false sharing on 64- and 128-byte cache lines (x86, ARM, Apple Silicon).

private[ringbuffer] class SpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class SpscProducerFields extends SpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L // look-ahead boundary; initially 0 forces first offer to slow path
}

private[ringbuffer] class SpscPad1 extends SpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class SpscConsumerFields extends SpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class SpscPad2 extends SpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A bounded first-in, first-out queue of non-`null` references for one producer
 * thread and one consumer thread. Neither side blocks: [[offer]] returns
 * `false` when the buffer is full and [[take]] returns `null` when it is empty,
 * so the caller decides whether to retry, back off or drop the element.
 * [[fill]] and [[drain]] move several elements per call.
 *
 * On the JVM the two threads share no lock: a slot's `null` or non-`null`
 * content tells each side whether the slot is free (the FastFlow design), so on
 * the fast path neither side reads the other's index. Call `offer` and `fill`
 * from one thread only, and `take` and `drain` from one thread only; nothing
 * checks this. On Scala.js, which runs one thread, the class is a plain
 * array-backed queue with the same methods and results.
 *
 * `null` marks an empty slot, so `offer(null)` throws `NullPointerException`.
 *
 * {{{
 * val jobs = SpscRingBuffer[String](2)
 *
 * val consumer = new Thread(() => {
 *   var handled = 0
 *   while (handled < 3) {
 *     val job = jobs.take()
 *     if (job != null) {
 *       println(job)
 *       handled += 1
 *     }
 *   }
 * })
 * consumer.start()
 *
 * for (job <- List("resize-image", "send-email", "rebuild-index"))
 *   while (!jobs.offer(job)) Thread.onSpinWait()
 * consumer.join()
 * // resize-image
 * // send-email
 * // rebuild-index
 * }}}
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spsc/ SPSC RingBuffer]]
 */
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) extends SpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import SpscRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private val lookAheadStep: Long   = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false`, without
   * waiting, when the buffer is full. Call it from the producer thread only.
   *
   * @throws java.lang.NullPointerException
   *   if `a` is `null`
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val buf  = buffer
    val m    = mask
    val pIdx = producerIndex

    if (pIdx >= producerLimit) {
      if (!offerSlowPath(buf, m, pIdx)) return false
    }

    val offset = (pIdx & m).toInt
    ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Removes and returns the oldest element, or returns `null`, without waiting,
   * when the buffer is empty. Call it from the consumer thread only.
   */
  def take(): A = {
    val buf    = buffer
    val m      = mask
    val cIdx   = consumerIndex
    val offset = (cIdx & m).toInt

    val e = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
    if (e eq null) return null.asInstanceOf[A]

    ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    e
  }

  /**
   * Returns the number of elements in the buffer. While other threads offer or
   * take (JVM only), the result is an estimate: the two indices are read one
   * after the other, so it can be out of date, negative, or larger than
   * `capacity`.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  /**
   * Returns `true` when the buffer holds no elements. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` when every slot holds an element. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }

  /**
   * Removes up to `limit` elements, oldest first, and passes each one to
   * `consumer`; returns how many it removed, which is 0 when the buffer is
   * empty. Call it from the consumer thread only.
   *
   * Each element leaves the buffer before `consumer` sees it, so when
   * `consumer` throws, that element and the ones before it stay removed, the
   * rest stay in the buffer, and the exception propagates.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   */
  def drain(consumer: A => Unit, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    val buf   = buffer
    val m     = mask
    var cIdx  = consumerIndex
    var count = 0

    while (count < limit) {
      val offset = (cIdx & m).toInt
      val e      = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
      if (e eq null) return count
      ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
      cIdx += 1L
      CONSUMER_INDEX.setOpaque(this, cIdx)
      count += 1
      consumer(e)
    }
    count
  }

  /**
   * Calls `supplier` once per free slot, up to `limit` times, and appends each
   * result; returns how many elements it appended, which is 0 when the buffer
   * is full. It checks for a free slot before each call, so `supplier` never
   * runs for an element that does not fit. Call it from the producer thread
   * only.
   *
   * When `supplier` throws, the elements appended before stay in the buffer and
   * the exception propagates.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   * @throws java.lang.NullPointerException
   *   if `supplier` returns `null`; the elements appended before stay in the
   *   buffer
   */
  def fill(supplier: () => A, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    val buf   = buffer
    val m     = mask
    var pIdx  = producerIndex
    var pLim  = producerLimit
    var count = 0

    while (count < limit) {
      if (pIdx >= pLim) {
        val lookAheadOffset = ((pIdx + lookAheadStep) & m).toInt
        if ((ARRAY_HANDLE.getAcquire(buf, lookAheadOffset): AnyRef) eq null) {
          pLim = pIdx + lookAheadStep
        } else {
          val currentOffset = (pIdx & m).toInt
          if ((ARRAY_HANDLE.getAcquire(buf, currentOffset): AnyRef) ne null) {
            producerLimit = pLim
            return count
          }
          pLim = pIdx + 1
        }
      }
      val a = supplier()
      if (a == null) throw new NullPointerException("fill: supplier returned null")
      val offset = (pIdx & m).toInt
      ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
      pIdx += 1L
      PRODUCER_INDEX.setOpaque(this, pIdx)
      count += 1
    }
    producerLimit = pLim
    count
  }

  private def offerSlowPath(buf: Array[AnyRef], m: Long, pIdx: Long): Boolean = {
    val lookAheadOffset = ((pIdx + lookAheadStep) & m).toInt
    if ((ARRAY_HANDLE.getAcquire(buf, lookAheadOffset): AnyRef) eq null) {
      producerLimit = pIdx + lookAheadStep
      true
    } else {
      val currentOffset = (pIdx & m).toInt
      if ((ARRAY_HANDLE.getAcquire(buf, currentOffset): AnyRef) ne null) {
        false
      } else {
        producerLimit = pIdx + 1
        true
      }
    }
  }
}

/**
 * Creates [[SpscRingBuffer]] instances.
 */
object SpscRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpscConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new SpscRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): SpscRingBuffer[A] = new SpscRingBuffer[A](capacity)
}
