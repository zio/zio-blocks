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

import zio._
import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

object PrimitiveSpscRingBufferSpec extends ZIOSpecDefault {
  def spec = suite("PrimitiveSpscRingBufferSpec")(
    intSuite,
    longSuite,
    doubleSuite,
    floatSuite,
    inBandDoneSuite
  )

  private val inBandDoneSuite = suite("in-band DONE sentinel")(
    test("IntSpscRingBuffer: offerDone + pollPacked produces DONE_PACKED") {
      val rb = IntSpscRingBuffer(4)
      rb.offer(42)
      rb.offerDone()
      val p1 = rb.pollPacked()
      val p2 = rb.pollPacked()
      val p3 = rb.pollPacked()
      assertTrue(
        p1 != IntSpscRingBuffer.EMPTY_PACKED,
        p1 != IntSpscRingBuffer.DONE_PACKED,
        p1.toInt == 42,
        p2 == IntSpscRingBuffer.DONE_PACKED,
        p3 == IntSpscRingBuffer.EMPTY_PACKED
      )
    },
    test("LongSpscRingBuffer: offerDone + pollPacked produces DONE") {
      val rb = LongSpscRingBuffer(4)
      rb.offer(123L)
      rb.offerDone()
      val p1 = rb.pollPacked()
      val p2 = rb.pollPacked()
      val p3 = rb.pollPacked()
      assertTrue(
        p1 == 123L,
        p2 == LongSpscRingBuffer.DONE,
        p3 == LongSpscRingBuffer.EMPTY
      )
    },
    test("FloatSpscRingBuffer: offerDone + pollPacked produces DONE_PACKED") {
      val rb = FloatSpscRingBuffer(4)
      rb.offer(3.14f)
      rb.offerDone()
      val p1: Long  = rb.pollPacked()
      val p2: Long  = rb.pollPacked()
      val p3: Long  = rb.pollPacked()
      val f1: Float = java.lang.Float.intBitsToFloat(p1.toInt)
      assertTrue(
        p1 != FloatSpscRingBuffer.EMPTY_PACKED,
        p1 != FloatSpscRingBuffer.DONE_PACKED,
        f1 == 3.14f,
        p2 == FloatSpscRingBuffer.DONE_PACKED,
        p3 == FloatSpscRingBuffer.EMPTY_PACKED
      )
    },
    test("DoubleSpscRingBuffer: offerDone + pollPacked produces DONE_BITS") {
      val rb = DoubleSpscRingBuffer(4)
      rb.offer(2.71)
      rb.offerDone()
      val p1: Long   = rb.pollPacked()
      val p2: Long   = rb.pollPacked()
      val p3: Long   = rb.pollPacked()
      val d1: Double = java.lang.Double.longBitsToDouble(p1)
      assertTrue(
        d1 == 2.71,
        p2 == DoubleSpscRingBuffer.DONE_BITS,
        p3 == DoubleSpscRingBuffer.EMPTY_BITS
      )
    },
    test("DONE is ordered after data in same Long queue") {
      val rb = LongSpscRingBuffer(16)
      var i  = 0L
      while (i < 8L) {
        rb.offer(i + 1)
        i += 1L
      }
      rb.offerDone()
      var sum = 0L
      var p   = rb.pollPacked()
      while (p != LongSpscRingBuffer.DONE && p != LongSpscRingBuffer.EMPTY) {
        sum += p
        p = rb.pollPacked()
      }
      assertTrue(p == LongSpscRingBuffer.DONE, sum == (1L + 2 + 3 + 4 + 5 + 6 + 7 + 8))
    }
  )

  private val intSuite = suite("IntSpscRingBuffer")(
    test("offer then peek then take") {
      val rb = IntSpscRingBuffer(4)
      assertTrue(rb.offer(42), rb.peek(), rb.take() == 42)
    },
    test("FIFO ordering") {
      val rb = IntSpscRingBuffer(8)
      rb.offer(1)
      rb.offer(2)
      rb.offer(3)
      assertTrue(rb.peek(), rb.take() == 1, rb.peek(), rb.take() == 2, rb.peek(), rb.take() == 3)
    },
    test("full buffer rejects offer") {
      val rb = IntSpscRingBuffer(2)
      assertTrue(rb.offer(1), rb.offer(2), !rb.offer(3))
    },
    test("empty buffer reports no data") {
      val rb = IntSpscRingBuffer(2)
      assertTrue(!rb.peek())
    },
    test("isEmpty / isFull / size track occupancy") {
      val rb = IntSpscRingBuffer(2)
      val e0 = rb.isEmpty
      val f0 = rb.isFull
      val s0 = rb.size
      rb.offer(1)
      val e1 = rb.isEmpty
      val f1 = rb.isFull
      val s1 = rb.size
      rb.offer(2)
      val e2 = rb.isEmpty
      val f2 = rb.isFull
      val s2 = rb.size
      rb.take()
      val e3 = rb.isEmpty
      val f3 = rb.isFull
      val s3 = rb.size
      assertTrue(
        e0,
        !f0,
        s0 == 0,
        !e1,
        !f1,
        s1 == 1,
        !e2,
        f2,
        s2 == 2,
        !e3,
        !f3,
        s3 == 1
      )
    },
    test("capacity must be a positive power of two") {
      val cases   = List(0, -1, 3, 5, 6, 7)
      val results = cases.map { c =>
        try { IntSpscRingBuffer(c); false }
        catch { case _: IllegalArgumentException => true }
      }
      assertTrue(results.forall(identity))
    },
    test("capacity=1 boundary") {
      val rb = IntSpscRingBuffer(1)
      assertTrue(rb.offer(7), rb.peek(), rb.take() == 7, !rb.peek())
    },
    test("boundary values") {
      val rb     = IntSpscRingBuffer(8)
      val values = List(Int.MinValue, Int.MaxValue, 0, -1)
      values.foreach(rb.offer)
      val read = values.forall { expected =>
        rb.peek() && rb.take() == expected
      }
      assertTrue(read, !rb.peek())
    },
    test("wrap-around fill drain refill") {
      val rb = IntSpscRingBuffer(4)
      assertTrue(rb.offer(1), rb.offer(2), rb.offer(3), rb.offer(4)) &&
      assertTrue(
        rb.peek(),
        rb.take() == 1,
        rb.peek(),
        rb.take() == 2,
        rb.peek(),
        rb.take() == 3,
        rb.peek(),
        rb.take() == 4
      ) &&
      assertTrue(
        rb.offer(5),
        rb.offer(6),
        rb.offer(7),
        rb.offer(8),
        rb.peek(),
        rb.take() == 5,
        rb.peek(),
        rb.take() == 6,
        rb.peek(),
        rb.take() == 7,
        rb.peek(),
        rb.take() == 8
      )
    },
    test("simple 1p1c stress 100k") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val expectedSum = count.toLong * (count - 1) / 2
        val rb          = IntSpscRingBuffer(1024)

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val orderError   = new AtomicBoolean(false)
        val actualSum    = new AtomicLong(0L)

        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            if (rb.offer(i)) i += 1
            else Thread.onSpinWait()
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var expected = 0
          var received = 0
          while (received < count) {
            if (rb.peek()) {
              val v = rb.take()
              if (v != expected) orderError.set(true)
              actualSum.addAndGet(v.toLong)
              expected += 1
              received += 1
            } else Thread.onSpinWait()
          }
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(!orderError.get(), actualSum.get() == expectedSum, rb.isEmpty)
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private val longSuite = suite("LongSpscRingBuffer")(
    test("offer then peek then take") {
      val rb = LongSpscRingBuffer(4)
      assertTrue(rb.offer(42L), rb.peek(), rb.take() == 42L)
    },
    test("FIFO ordering") {
      val rb = LongSpscRingBuffer(8)
      rb.offer(1L)
      rb.offer(2L)
      rb.offer(3L)
      assertTrue(rb.peek(), rb.take() == 1L, rb.peek(), rb.take() == 2L, rb.peek(), rb.take() == 3L)
    },
    test("full buffer rejects offer") {
      val rb = LongSpscRingBuffer(2)
      assertTrue(rb.offer(1L), rb.offer(2L), !rb.offer(3L))
    },
    test("empty buffer reports no data") {
      val rb = LongSpscRingBuffer(2)
      assertTrue(!rb.peek())
    },
    test("isEmpty / isFull / size track occupancy") {
      val rb = LongSpscRingBuffer(2)
      val s0 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(1L)
      val s1 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(2L)
      val s2 = (rb.isEmpty, rb.isFull, rb.size)
      rb.take()
      val s3 = (rb.isEmpty, rb.isFull, rb.size)
      assertTrue(
        s0 == ((true, false, 0)),
        s1 == ((false, false, 1)),
        s2 == ((false, true, 2)),
        s3 == ((false, false, 1))
      )
    },
    test("capacity must be a positive power of two") {
      val cases   = List(0, -1, 3, 5, 6, 7)
      val results = cases.map { c =>
        try { LongSpscRingBuffer(c); false }
        catch { case _: IllegalArgumentException => true }
      }
      assertTrue(results.forall(identity))
    },
    test("capacity=1 boundary") {
      val rb = LongSpscRingBuffer(1)
      assertTrue(rb.offer(7L), rb.peek(), rb.take() == 7L, !rb.peek())
    },
    test("boundary values") {
      val rb = LongSpscRingBuffer(8)
      // Long.MinValue is the empty-slot sentinel and Long.MinValue + 1L is the
      // in-band DONE sentinel; both are rejected on offer.
      val values = List(Long.MinValue + 2L, Long.MaxValue, 0L, -1L)
      values.foreach(rb.offer)
      val read = values.forall { expected =>
        rb.peek() && rb.take() == expected
      }
      assertTrue(read, !rb.peek())
    },
    test("offer(Long.MinValue) is rejected") {
      val rb     = LongSpscRingBuffer(2)
      val thrown =
        try { rb.offer(Long.MinValue); false }
        catch { case _: IllegalArgumentException => true }
      assertTrue(thrown, !rb.peek())
    },
    test("offer(Long.MinValue + 1L) is rejected (in-band DONE sentinel)") {
      val rb     = LongSpscRingBuffer(2)
      val thrown =
        try { rb.offer(Long.MinValue + 1L); false }
        catch { case _: IllegalArgumentException => true }
      assertTrue(thrown, !rb.peek())
    },
    test("wrap-around fill drain refill") {
      val rb = LongSpscRingBuffer(4)
      assertTrue(rb.offer(1L), rb.offer(2L), rb.offer(3L), rb.offer(4L)) &&
      assertTrue(
        rb.peek(),
        rb.take() == 1L,
        rb.peek(),
        rb.take() == 2L,
        rb.peek(),
        rb.take() == 3L,
        rb.peek(),
        rb.take() == 4L
      ) &&
      assertTrue(
        rb.offer(5L),
        rb.offer(6L),
        rb.offer(7L),
        rb.offer(8L),
        rb.peek(),
        rb.take() == 5L,
        rb.peek(),
        rb.take() == 6L,
        rb.peek(),
        rb.take() == 7L,
        rb.peek(),
        rb.take() == 8L
      )
    },
    test("simple 1p1c stress 100k") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val expectedSum = count.toLong * (count - 1) / 2
        val rb          = LongSpscRingBuffer(1024)

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val orderError   = new AtomicBoolean(false)
        val actualSum    = new AtomicLong(0L)

        val producer = new Thread(() => {
          var i = 0L
          while (i < count.toLong) {
            if (rb.offer(i)) i += 1L
            else Thread.onSpinWait()
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var expected = 0L
          var received = 0
          while (received < count) {
            if (rb.peek()) {
              val v = rb.take()
              if (v != expected) orderError.set(true)
              actualSum.addAndGet(v)
              expected += 1L
              received += 1
            } else Thread.onSpinWait()
          }
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(!orderError.get(), actualSum.get() == expectedSum, rb.isEmpty)
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private val doubleSuite = suite("DoubleSpscRingBuffer")(
    test("offer then peek then take") {
      val rb = DoubleSpscRingBuffer(4)
      assertTrue(rb.offer(42.5), rb.peek(), rb.take() == 42.5)
    },
    test("FIFO ordering") {
      val rb = DoubleSpscRingBuffer(8)
      rb.offer(1.0)
      rb.offer(2.0)
      rb.offer(3.0)
      assertTrue(rb.peek(), rb.take() == 1.0, rb.peek(), rb.take() == 2.0, rb.peek(), rb.take() == 3.0)
    },
    test("full buffer rejects offer") {
      val rb = DoubleSpscRingBuffer(2)
      assertTrue(rb.offer(1.0), rb.offer(2.0), !rb.offer(3.0))
    },
    test("empty buffer reports no data") {
      val rb = DoubleSpscRingBuffer(2)
      assertTrue(!rb.peek())
    },
    test("isEmpty / isFull / size track occupancy") {
      val rb = DoubleSpscRingBuffer(2)
      val s0 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(1.0)
      val s1 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(2.0)
      val s2 = (rb.isEmpty, rb.isFull, rb.size)
      rb.take()
      val s3 = (rb.isEmpty, rb.isFull, rb.size)
      assertTrue(
        s0 == ((true, false, 0)),
        s1 == ((false, false, 1)),
        s2 == ((false, true, 2)),
        s3 == ((false, false, 1))
      )
    },
    test("capacity must be a positive power of two") {
      val cases   = List(0, -1, 3, 5, 6, 7)
      val results = cases.map { c =>
        try { DoubleSpscRingBuffer(c); false }
        catch { case _: IllegalArgumentException => true }
      }
      assertTrue(results.forall(identity))
    },
    test("capacity=1 boundary") {
      val rb = DoubleSpscRingBuffer(1)
      assertTrue(rb.offer(7.25), rb.peek(), rb.take() == 7.25, !rb.peek())
    },
    test("boundary values") {
      val rb     = DoubleSpscRingBuffer(8)
      val values = List(Double.MinValue, Double.MaxValue, 0.0, -1.0)
      values.foreach(rb.offer)
      val read = values.forall { expected =>
        rb.peek() && rb.take() == expected
      }
      assertTrue(read, !rb.peek())
    },
    test("NaN round-trips as NaN") {
      val rb = DoubleSpscRingBuffer(2)
      rb.offer(Double.NaN)
      val nonEmpty = rb.peek()
      val taken    = rb.take()
      assertTrue(nonEmpty, taken.isNaN)
    },
    test("sentinel-bit NaN is canonicalized but still observed as NaN") {
      // The reserved empty marker bit pattern: if the user manages to
      // construct a Double with this exact raw layout, offer must canonicalize
      // it to Double.NaN's bit pattern so the slot is still observed as full.
      val rb            = DoubleSpscRingBuffer(2)
      val sentinelAsDbl = java.lang.Double.longBitsToDouble(0xfff8000000000001L)
      val offered       = rb.offer(sentinelAsDbl)
      val nonEmpty      = rb.peek()
      val taken         = rb.take()
      assertTrue(offered, nonEmpty, taken.isNaN)
    },
    test("+/- Inf round-trip") {
      val rb = DoubleSpscRingBuffer(4)
      rb.offer(Double.PositiveInfinity)
      rb.offer(Double.NegativeInfinity)
      assertTrue(
        rb.peek(),
        rb.take() == Double.PositiveInfinity,
        rb.peek(),
        rb.take() == Double.NegativeInfinity
      )
    },
    test("wrap-around fill drain refill") {
      val rb = DoubleSpscRingBuffer(4)
      assertTrue(rb.offer(1.0), rb.offer(2.0), rb.offer(3.0), rb.offer(4.0)) &&
      assertTrue(
        rb.peek(),
        rb.take() == 1.0,
        rb.peek(),
        rb.take() == 2.0,
        rb.peek(),
        rb.take() == 3.0,
        rb.peek(),
        rb.take() == 4.0
      ) &&
      assertTrue(
        rb.offer(5.0),
        rb.offer(6.0),
        rb.offer(7.0),
        rb.offer(8.0),
        rb.peek(),
        rb.take() == 5.0,
        rb.peek(),
        rb.take() == 6.0,
        rb.peek(),
        rb.take() == 7.0,
        rb.peek(),
        rb.take() == 8.0
      )
    },
    test("simple 1p1c stress 100k") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val expectedSum = count.toLong * (count - 1) / 2
        val rb          = DoubleSpscRingBuffer(1024)

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val orderError   = new AtomicBoolean(false)
        val actualSum    = new AtomicReference[Double](0.0)

        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            if (rb.offer(i.toDouble)) i += 1
            else Thread.onSpinWait()
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var expected = 0
          var received = 0
          var sum      = 0.0
          while (received < count) {
            if (rb.peek()) {
              val v = rb.take()
              if (v != expected.toDouble) orderError.set(true)
              sum += v
              expected += 1
              received += 1
            } else Thread.onSpinWait()
          }
          actualSum.set(sum)
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(!orderError.get(), actualSum.get() == expectedSum.toDouble, rb.isEmpty)
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private val floatSuite = suite("FloatSpscRingBuffer")(
    test("offer then peek then take") {
      val rb = FloatSpscRingBuffer(4)
      assertTrue(rb.offer(42.5f), rb.peek(), rb.take() == 42.5f)
    },
    test("FIFO ordering") {
      val rb = FloatSpscRingBuffer(8)
      rb.offer(1.0f)
      rb.offer(2.0f)
      rb.offer(3.0f)
      assertTrue(rb.peek(), rb.take() == 1.0f, rb.peek(), rb.take() == 2.0f, rb.peek(), rb.take() == 3.0f)
    },
    test("full buffer rejects offer") {
      val rb = FloatSpscRingBuffer(2)
      assertTrue(rb.offer(1.0f), rb.offer(2.0f), !rb.offer(3.0f))
    },
    test("empty buffer reports no data") {
      val rb = FloatSpscRingBuffer(2)
      assertTrue(!rb.peek())
    },
    test("isEmpty / isFull / size track occupancy") {
      val rb = FloatSpscRingBuffer(2)
      val s0 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(1.0f)
      val s1 = (rb.isEmpty, rb.isFull, rb.size)
      rb.offer(2.0f)
      val s2 = (rb.isEmpty, rb.isFull, rb.size)
      rb.take()
      val s3 = (rb.isEmpty, rb.isFull, rb.size)
      assertTrue(
        s0 == ((true, false, 0)),
        s1 == ((false, false, 1)),
        s2 == ((false, true, 2)),
        s3 == ((false, false, 1))
      )
    },
    test("capacity must be a positive power of two") {
      val cases   = List(0, -1, 3, 5, 6, 7)
      val results = cases.map { c =>
        try { FloatSpscRingBuffer(c); false }
        catch { case _: IllegalArgumentException => true }
      }
      assertTrue(results.forall(identity))
    },
    test("capacity=1 boundary") {
      val rb = FloatSpscRingBuffer(1)
      assertTrue(rb.offer(7.25f), rb.peek(), rb.take() == 7.25f, !rb.peek())
    },
    test("boundary values") {
      val rb     = FloatSpscRingBuffer(8)
      val values = List(Float.MinValue, Float.MaxValue, 0.0f, -1.0f)
      values.foreach(rb.offer)
      val read = values.forall { expected =>
        rb.peek() && rb.take() == expected
      }
      assertTrue(read, !rb.peek())
    },
    test("wrap-around fill drain refill") {
      val rb = FloatSpscRingBuffer(4)
      assertTrue(rb.offer(1.0f), rb.offer(2.0f), rb.offer(3.0f), rb.offer(4.0f)) &&
      assertTrue(
        rb.peek(),
        rb.take() == 1.0f,
        rb.peek(),
        rb.take() == 2.0f,
        rb.peek(),
        rb.take() == 3.0f,
        rb.peek(),
        rb.take() == 4.0f
      ) &&
      assertTrue(
        rb.offer(5.0f),
        rb.offer(6.0f),
        rb.offer(7.0f),
        rb.offer(8.0f),
        rb.peek(),
        rb.take() == 5.0f,
        rb.peek(),
        rb.take() == 6.0f,
        rb.peek(),
        rb.take() == 7.0f,
        rb.peek(),
        rb.take() == 8.0f
      )
    },
    test("simple 1p1c stress 100k") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val expectedSum = count.toLong * (count - 1) / 2
        val rb          = FloatSpscRingBuffer(1024)

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val orderError   = new AtomicBoolean(false)
        val actualSum    = new AtomicReference[Double](0.0)

        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            if (rb.offer(i.toFloat)) i += 1
            else Thread.onSpinWait()
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var expected = 0
          var received = 0
          var sum      = 0.0
          while (received < count) {
            if (rb.peek()) {
              val v = rb.take()
              if (v != expected.toFloat) orderError.set(true)
              sum += v.toDouble
              expected += 1
              received += 1
            } else Thread.onSpinWait()
          }
          actualSum.set(sum)
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(!orderError.get(), actualSum.get() == expectedSum.toDouble, rb.isEmpty)
      }
    } @@ TestAspect.timeout(60.seconds)
  )
}
