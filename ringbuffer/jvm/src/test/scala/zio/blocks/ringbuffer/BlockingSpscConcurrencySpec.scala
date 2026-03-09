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

object BlockingSpscConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("BlockingSpscConcurrencySpec")(
    test("put/take: 10K items with capacity=4, verify FIFO order and sum") {
      ZIO.attemptBlocking {
        val count       = 10_000
        val cap         = 4
        val rb          = new BlockingSpscRingBuffer[java.lang.Integer](cap)
        val expectedSum = count.toLong * (count - 1) / 2

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val actualSum    = new AtomicLong(0L)
        val orderError   = new AtomicBoolean(false)

        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            rb.offer(java.lang.Integer.valueOf(i))
            i += 1
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var received = 0
          var expected = 0
          while (received < count) {
            val v = rb.take()
            if (v.intValue() != expected) orderError.set(true)
            actualSum.addAndGet(v.longValue())
            expected += 1
            received += 1
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
    test("put blocks when full, unblocks when consumer takes") {
      ZIO.attemptBlocking {
        val rb      = new BlockingSpscRingBuffer[String](2)
        val putDone = new CountDownLatch(1)

        // Fill the buffer
        rb.offer("a")
        rb.offer("b")

        val producer = new Thread(() => {
          rb.offer("c") // should block until consumer takes
          putDone.countDown()
        })
        producer.start()

        // Give the producer time to block, then unblock it
        Thread.sleep(50)
        val v         = rb.take() // unblock the producer
        val completed = putDone.await(5, java.util.concurrent.TimeUnit.SECONDS)
        producer.join(5000)

        // "c" should now be in the buffer
        // Drain remaining and check
        val remaining = scala.collection.mutable.ArrayBuffer.empty[String]
        var e         = rb.tryTake()
        while (e ne null) { remaining += e; e = rb.tryTake() }

        assertTrue(v == "a", completed, remaining.contains("c"))
      }
    } @@ TestAspect.timeout(10.seconds),
    test("take blocks when empty, unblocks when producer puts") {
      ZIO.attemptBlocking {
        val rb       = new BlockingSpscRingBuffer[String](4)
        val takeDone = new CountDownLatch(1)
        val result   = new java.util.concurrent.atomic.AtomicReference[String](null)

        val consumer = new Thread(() => {
          val v = rb.take() // should block — buffer is empty
          result.set(v)
          takeDone.countDown()
        })
        consumer.start()

        Thread.sleep(50)
        rb.offer("hello") // unblock the consumer
        val completed = takeDone.await(5, java.util.concurrent.TimeUnit.SECONDS)
        consumer.join(5000)

        assertTrue(completed, result.get() == "hello")
      }
    } @@ TestAspect.timeout(10.seconds),
    test("interrupt on put throws InterruptedException") {
      ZIO.attemptBlocking {
        val rb          = new BlockingSpscRingBuffer[String](1)
        val latch       = new CountDownLatch(1)
        val interrupted = new AtomicBoolean(false)

        rb.offer("fill")

        val producer = new Thread(() => {
          latch.countDown()
          try rb.offer("block")
          catch { case _: InterruptedException => interrupted.set(true) }
        })
        producer.start()
        latch.await()
        Thread.sleep(50)

        producer.interrupt()
        producer.join(5000)
        assertTrue(interrupted.get())
      }
    } @@ TestAspect.timeout(10.seconds),
    test("interrupt on take throws InterruptedException") {
      ZIO.attemptBlocking {
        val rb          = new BlockingSpscRingBuffer[String](4)
        val latch       = new CountDownLatch(1)
        val interrupted = new AtomicBoolean(false)

        val consumer = new Thread((() => {
          latch.countDown()
          try { val _ = rb.take() }
          catch { case _: InterruptedException => interrupted.set(true) }
        }): Runnable)
        consumer.start()
        latch.await()
        Thread.sleep(50)

        consumer.interrupt()
        consumer.join(5000)
        assertTrue(interrupted.get())
      }
    } @@ TestAspect.timeout(10.seconds),
    test("hammer: 500K items, capacity=1024, verify no data loss") {
      ZIO.attemptBlocking {
        val count       = 500_000
        val rb          = new BlockingSpscRingBuffer[java.lang.Integer](1024)
        val expectedSum = count.toLong * (count - 1) / 2

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val actualSum    = new AtomicLong(0L)

        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            rb.offer(java.lang.Integer.valueOf(i))
            i += 1
          }
          producerDone.countDown()
        })

        val consumer = new Thread(() => {
          var received = 0
          while (received < count) {
            val v = rb.take()
            actualSum.addAndGet(v.longValue())
            received += 1
          }
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mixed offer/take and tryOffer/tryTake: no data loss") {
      ZIO.attemptBlocking {
        val count       = 50_000
        val rb          = new BlockingSpscRingBuffer[java.lang.Integer](16)
        val expectedSum = count.toLong * (count - 1) / 2

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val actualSum    = new AtomicLong(0L)

        // Producer: alternate offer/tryOffer
        val producer = new Thread(() => {
          var i = 0
          while (i < count) {
            if (i % 2 == 0) {
              rb.offer(java.lang.Integer.valueOf(i))
              i += 1
            } else {
              if (rb.tryOffer(java.lang.Integer.valueOf(i))) i += 1
              else Thread.onSpinWait()
            }
          }
          producerDone.countDown()
        })

        // Consumer: alternate take/tryTake
        val consumer = new Thread(() => {
          var received = 0
          while (received < count) {
            if (received % 2 == 0) {
              val v = rb.take()
              actualSum.addAndGet(v.longValue())
              received += 1
            } else {
              val v = rb.tryTake()
              if (v ne null) {
                actualSum.addAndGet(v.longValue())
                received += 1
              } else {
                Thread.onSpinWait()
              }
            }
          }
          consumerDone.countDown()
        })

        producer.start()
        consumer.start()
        producerDone.await()
        consumerDone.await()

        assertTrue(
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds)
  )
}
