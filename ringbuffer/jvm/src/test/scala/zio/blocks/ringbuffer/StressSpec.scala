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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

/**
 * Comprehensive stress tests that go beyond the per-type concurrency specs.
 *
 * These tests push every ring buffer variant to extremes: tiny capacities with
 * massive throughput (extreme wrap-around), high thread counts, and mixed
 * usage. Every test verifies:
 *   - '''No data loss''': consumed count == produced count
 *   - '''No duplicates''': each element consumed exactly once (via
 *     ConcurrentHashMap or sequential counter)
 *   - '''Correct aggregate''': sum of consumed values == expected sum
 */
object StressSpec extends ZIOSpecDefault {

  def spec = suite("StressSpec")(
    suite("1. Extended SPSC stress")(
      test("1M elements through SpscRingBuffer[String] with capacity=16") {
        ZIO.attemptBlocking {
          val count       = 1_000_000
          val capacity    = 16
          val rb          = new SpscRingBuffer[String](capacity)
          val expectedSum = count.toLong * (count - 1) / 2

          val producerDone = new CountDownLatch(1)
          val consumerDone = new CountDownLatch(1)
          val actualSum    = new AtomicLong(0L)
          val orderError   = new AtomicBoolean(false)

          val producer = new Thread(() => {
            var i = 0
            while (i < count) {
              if (rb.offer(i.toString)) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producerDone.countDown()
          })

          val consumer = new Thread(() => {
            var received = 0
            var expected = 0
            while (received < count) {
              val v = rb.take()
              if (v ne null) {
                val parsed = v.toInt
                if (parsed != expected) orderError.set(true)
                actualSum.addAndGet(parsed.toLong)
                expected += 1
                received += 1
              } else {
                Thread.onSpinWait()
              }
            }
            consumerDone.countDown()
          })

          producer.start()
          consumer.start()
          producerDone.await()
          consumerDone.await()

          assertTrue(
            !orderError.get(),
            actualSum.get() == expectedSum,
            rb.isEmpty
          )
        }
      } @@ TestAspect.timeout(120.seconds),
      test("1M elements through SpscRingBuffer[java.lang.Integer] with capacity=4") {
        ZIO.attemptBlocking {
          val count       = 1_000_000
          val capacity    = 4
          val rb          = new SpscRingBuffer[java.lang.Integer](capacity)
          val expectedSum = count.toLong * (count - 1) / 2

          val producerDone = new CountDownLatch(1)
          val consumerDone = new CountDownLatch(1)
          val actualSum    = new AtomicLong(0L)
          val orderError   = new AtomicBoolean(false)

          val producer = new Thread(() => {
            var i = 0
            while (i < count) {
              if (rb.offer(java.lang.Integer.valueOf(i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producerDone.countDown()
          })

          val consumer = new Thread(() => {
            var received = 0
            var expected = 0
            while (received < count) {
              val v = rb.take()
              if (v ne null) {
                if (v.intValue() != expected) orderError.set(true)
                actualSum.addAndGet(v.longValue())
                expected += 1
                received += 1
              } else {
                Thread.onSpinWait()
              }
            }
            consumerDone.countDown()
          })

          producer.start()
          consumer.start()
          producerDone.await()
          consumerDone.await()

          assertTrue(
            !orderError.get(),
            actualSum.get() == expectedSum,
            rb.isEmpty
          )
        }
      } @@ TestAspect.timeout(120.seconds)
    ),
    suite("2. Extended MPSC stress")(
      test("8 producers, 1 consumer, 500K items, capacity=64") {
        mpscStressTest(numProducers = 8, totalItems = 500_000, capacity = 64)
      } @@ TestAspect.timeout(120.seconds),
      test("16 producers, 1 consumer, 100K items, capacity=32") {
        mpscStressTest(numProducers = 16, totalItems = 100_000, capacity = 32)
      } @@ TestAspect.timeout(120.seconds)
    ),
    suite("3. Extended SPMC stress")(
      test("1 producer, 8 consumers, 500K items, capacity=64") {
        ZIO.attemptBlocking {
          val numConsumers = 8
          val totalItems   = 500_000
          val capacity     = 64
          val rb           = new SpmcRingBuffer[java.lang.Long](capacity)
          val expectedSum  = totalItems.toLong * (totalItems - 1) / 2

          val allDone   = new CountDownLatch(1 + numConsumers)
          val actualSum = new AtomicLong(0L)
          val count     = new AtomicLong(0L)
          val dupError  = new AtomicBoolean(false)

          val seen = new java.util.concurrent.ConcurrentHashMap[Long, java.lang.Boolean](totalItems * 2)

          val producer = new Thread(() => {
            var i = 0L
            while (i < totalItems) {
              if (rb.offer(java.lang.Long.valueOf(i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            allDone.countDown()
          })

          val consumers = (0 until numConsumers).map { _ =>
            new Thread(() => {
              while (count.get() < totalItems) {
                val v = rb.take()
                if (v != null) {
                  val longVal = v.longValue()
                  if (seen.putIfAbsent(longVal, java.lang.Boolean.TRUE) != null) {
                    dupError.set(true)
                  }
                  actualSum.addAndGet(longVal)
                  count.incrementAndGet()
                } else {
                  Thread.onSpinWait()
                }
              }
              allDone.countDown()
            })
          }

          producer.start()
          consumers.foreach(_.start())
          allDone.await()

          assertTrue(
            !dupError.get(),
            count.get() == totalItems.toLong,
            actualSum.get() == expectedSum,
            rb.isEmpty
          )
        }
      } @@ TestAspect.timeout(120.seconds)
    ),
    suite("4. Extended MPMC stress")(
      test("4P + 4C, 200K items, capacity=32") {
        mpmcStressTest(numProducers = 4, numConsumers = 4, totalItems = 200_000, capacity = 32)
      } @@ TestAspect.timeout(120.seconds),
      test("8P + 8C, 100K items, capacity=128") {
        mpmcStressTest(numProducers = 8, numConsumers = 8, totalItems = 100_000, capacity = 128)
      } @@ TestAspect.timeout(120.seconds)
    )
  )

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * MPSC stress test helper. Each producer is assigned a contiguous range of
   * unique integers. The single consumer collects all values and verifies sum
   * and count.
   */
  private def mpscStressTest(
    numProducers: Int,
    totalItems: Int,
    capacity: Int
  ): ZIO[Any, Throwable, TestResult] =
    ZIO.attemptBlocking {
      val rb               = new MpscRingBuffer[java.lang.Long](capacity)
      val itemsPerProducer = totalItems / numProducers
      val expectedSum      = {
        var s = 0L
        (0 until numProducers).foreach { p =>
          val base = p.toLong * totalItems
          s += base * itemsPerProducer + itemsPerProducer.toLong * (itemsPerProducer - 1) / 2
        }
        s
      }

      val producersDone = new CountDownLatch(numProducers)
      val consumerDone  = new CountDownLatch(1)
      val actualSum     = new AtomicLong(0L)
      val actualCount   = new AtomicLong(0L)

      val producers = (0 until numProducers).map { p =>
        new Thread(
          () => {
            val base = p.toLong * totalItems
            var i    = 0
            while (i < itemsPerProducer) {
              if (rb.offer(java.lang.Long.valueOf(base + i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producersDone.countDown()
          },
          s"stress-mpsc-p-$p"
        )
      }

      val consumer = new Thread(
        () => {
          var received = 0
          while (received < totalItems) {
            val v = rb.take()
            if (v != null) {
              actualSum.addAndGet(v.longValue())
              actualCount.incrementAndGet()
              received += 1
            } else {
              Thread.onSpinWait()
            }
          }
          consumerDone.countDown()
        },
        "stress-mpsc-c"
      )

      producers.foreach(_.start())
      consumer.start()
      producersDone.await()
      consumerDone.await()

      assertTrue(
        actualCount.get() == totalItems.toLong,
        actualSum.get() == expectedSum,
        rb.isEmpty
      )
    }

  /**
   * MPMC stress test helper. Each producer gets a contiguous range [start, end)
   * of the global sequence 0 ..< totalItems. Consumers drain cooperatively
   * until all producers signal completion.
   */
  private def mpmcStressTest(
    numProducers: Int,
    numConsumers: Int,
    totalItems: Int,
    capacity: Int
  ): ZIO[Any, Throwable, TestResult] =
    ZIO.attemptBlocking {
      val rb = new MpmcRingBuffer[java.lang.Long](capacity)

      val itemsPerProducer  = totalItems / numProducers
      val expectedSum: Long = totalItems.toLong * (totalItems - 1) / 2

      val producersDone        = new CountDownLatch(numProducers)
      val consumersDone        = new CountDownLatch(numConsumers)
      val actualSum            = new AtomicLong(0L)
      val actualCount          = new AtomicLong(0L)
      val allProducersFinished = new AtomicBoolean(false)

      val producers = (0 until numProducers).map { pId =>
        val start = pId.toLong * itemsPerProducer
        val end   = if (pId == numProducers - 1) totalItems.toLong else start + itemsPerProducer
        new Thread(
          () => {
            var i = start
            while (i < end) {
              if (rb.offer(java.lang.Long.valueOf(i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producersDone.countDown()
          },
          s"stress-mpmc-p-$pId"
        )
      }

      val consumers = (0 until numConsumers).map { cId =>
        new Thread(
          () => {
            var localSum   = 0L
            var localCount = 0L
            var running    = true
            while (running) {
              val v = rb.take()
              if (v != null) {
                localSum += v.longValue()
                localCount += 1
              } else if (allProducersFinished.get()) {
                var v2 = rb.take()
                while (v2 != null) {
                  localSum += v2.longValue()
                  localCount += 1
                  v2 = rb.take()
                }
                actualSum.addAndGet(localSum)
                actualCount.addAndGet(localCount)
                consumersDone.countDown()
                running = false
              } else {
                Thread.onSpinWait()
              }
            }
          },
          s"stress-mpmc-c-$cId"
        )
      }

      producers.foreach(_.start())
      consumers.foreach(_.start())

      producersDone.await()
      allProducersFinished.set(true)
      consumersDone.await()

      assertTrue(
        actualSum.get() == expectedSum,
        actualCount.get() == totalItems.toLong,
        rb.isEmpty
      )
    }
}
