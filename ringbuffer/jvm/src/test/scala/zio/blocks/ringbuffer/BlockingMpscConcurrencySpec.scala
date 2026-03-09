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

object BlockingMpscConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("BlockingMpscConcurrencySpec")(
    test("4 producers put, 1 consumer take: 100K items, correct sum") {
      ZIO.attemptBlocking {
        val numProducers     = 4
        val itemsPerProducer = 25_000
        val totalItems       = numProducers * itemsPerProducer
        val rb               = new BlockingMpscRingBuffer[java.lang.Long](64)
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
          new Thread(() => {
            val base = p.toLong * totalItems
            var i    = 0
            while (i < itemsPerProducer) {
              rb.offer(java.lang.Long.valueOf(base + i))
              i += 1
            }
            producersDone.countDown()
          })
        }

        val consumer = new Thread(() => {
          var received = 0
          while (received < totalItems) {
            val v = rb.take()
            actualSum.addAndGet(v.longValue())
            actualCount.incrementAndGet()
            received += 1
          }
          consumerDone.countDown()
        })

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
    } @@ TestAspect.timeout(60.seconds),
    test("put blocks when full, unblocks when consumer takes") {
      ZIO.attemptBlocking {
        val rb      = new BlockingMpscRingBuffer[String](2)
        val putDone = new CountDownLatch(1)

        rb.offer("a")
        rb.offer("b")

        val producer = new Thread(() => {
          rb.offer("c") // should block until consumer takes
          putDone.countDown()
        })
        producer.start()

        Thread.sleep(50)
        val v         = rb.take() // unblock the producer
        val completed = putDone.await(5, java.util.concurrent.TimeUnit.SECONDS)
        producer.join(5000)

        val remaining = scala.collection.mutable.ArrayBuffer.empty[String]
        var e         = rb.tryTake()
        while (e ne null) { remaining += e; e = rb.tryTake() }

        assertTrue(v == "a", completed, remaining.contains("c"))
      }
    } @@ TestAspect.timeout(10.seconds),
    test("take blocks when empty, unblocks when producer puts") {
      ZIO.attemptBlocking {
        val rb       = new BlockingMpscRingBuffer[String](4)
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
    test("multiple producers block when full, all unblock as consumer drains") {
      ZIO.attemptBlocking {
        val rb    = new BlockingMpscRingBuffer[String](2)
        val ready = new CountDownLatch(3)
        val done  = new CountDownLatch(3)

        rb.offer("a")
        rb.offer("b")

        // 3 producers all try to put into a full buffer
        val producers = (0 until 3).map { i =>
          new Thread(() => {
            ready.countDown()
            rb.offer(s"p$i")
            done.countDown()
          })
        }
        producers.foreach(_.start())
        ready.await()
        Thread.sleep(100) // let all producers park

        // Drain enough to unblock all 3
        rb.take()
        rb.take()
        rb.take()
        rb.take() // may need up to 5 takes (2 original + 3 from producers)
        rb.take()

        val completed = done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(completed)
      }
    } @@ TestAspect.timeout(10.seconds),
    test("interrupt on put throws InterruptedException") {
      ZIO.attemptBlocking {
        val rb          = new BlockingMpscRingBuffer[String](1)
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
        val rb          = new BlockingMpscRingBuffer[String](4)
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
    test("hammer: 2 producers, 1 consumer, 500K items") {
      ZIO.attemptBlocking {
        val numProducers     = 2
        val itemsPerProducer = 250_000
        val totalItems       = numProducers * itemsPerProducer
        val rb               = new BlockingMpscRingBuffer[java.lang.Long](1024)
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

        val producers = (0 until numProducers).map { p =>
          new Thread(() => {
            val base = p.toLong * totalItems
            var i    = 0
            while (i < itemsPerProducer) {
              rb.offer(java.lang.Long.valueOf(base + i))
              i += 1
            }
            producersDone.countDown()
          })
        }

        val consumer = new Thread(() => {
          var received = 0
          while (received < totalItems) {
            val v = rb.take()
            actualSum.addAndGet(v.longValue())
            received += 1
          }
          consumerDone.countDown()
        })

        producers.foreach(_.start())
        consumer.start()
        producersDone.await()
        consumerDone.await()

        assertTrue(
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds)
  )
}
