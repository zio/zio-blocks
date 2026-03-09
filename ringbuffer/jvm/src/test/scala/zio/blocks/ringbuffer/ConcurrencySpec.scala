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

object ConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("ConcurrencySpec")(
    test("SPSC Generic Hammer: 100K Integer objects through SpscRingBuffer[java.lang.Integer]") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val rb          = new SpscRingBuffer[java.lang.Integer](1024)
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
            if (v != null) {
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
    } @@ TestAspect.timeout(60.seconds),
    test("No Deadlock: 4 threads doing offer/take for 5 seconds") {
      ZIO.attemptBlocking {
        val rb      = new MpmcRingBuffer[java.lang.Integer](16)
        val running = new AtomicBoolean(true)
        val allDone = new CountDownLatch(4)

        val threads = (0 until 4).map { id =>
          new Thread(() => {
            var counter = 0
            while (running.get()) {
              if (id % 2 == 0) {
                rb.offer(java.lang.Integer.valueOf(counter))
                counter += 1
              } else {
                rb.take()
              }
            }
            allDone.countDown()
          })
        }

        threads.foreach(_.start())
        Thread.sleep(5000)
        running.set(false)

        // Wait for all threads to complete with a generous timeout
        val completed = allDone.await(10, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(completed)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("concurrent fill and drain stress test") {
      ZIO.attemptBlocking {
        val total    = 100_000
        val batch    = 32
        val rb       = new SpscRingBuffer[String](1024)
        val consumed = new java.util.concurrent.ConcurrentLinkedQueue[String]()

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)

        val producer = new Thread(() => {
          var produced = 0
          while (produced < total) {
            var counter                = produced
            val supplier: () => String = () => {
              val v = s"item-$counter"
              counter += 1
              v
            }
            val n = rb.fill(supplier, Math.min(batch, total - produced))
            produced += n
            if (n == 0) Thread.onSpinWait()
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var received = 0
          while (received < total) {
            val n = rb.drain(e => consumed.add(e), Math.min(batch, total - received))
            received += n
            if (n == 0) Thread.onSpinWait()
          }
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        // Verify all elements consumed in FIFO order
        val items = new Array[String](total)
        var idx   = 0
        val iter  = consumed.iterator()
        while (iter.hasNext) {
          items(idx) = iter.next()
          idx += 1
        }

        var allCorrect = true
        var i          = 0
        while (i < total) {
          if (items(i) != s"item-$i") allCorrect = false
          i += 1
        }

        assertTrue(
          idx == total,
          allCorrect,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds),
    suite("Capacity Sweep: SPSC hammer at various capacities")(
      capacitySweepTest(1),
      capacitySweepTest(2),
      capacitySweepTest(4),
      capacitySweepTest(16),
      capacitySweepTest(1024)
    )
  )

  private def capacitySweepTest(cap: Int) =
    test(s"SPSC hammer with capacity=$cap") {
      ZIO.attemptBlocking {
        val count       = 50_000
        val rb          = new SpscRingBuffer[java.lang.Integer](cap)
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
    } @@ TestAspect.timeout(60.seconds)
}
