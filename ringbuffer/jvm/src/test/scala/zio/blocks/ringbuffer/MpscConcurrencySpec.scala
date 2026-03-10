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
import java.util.concurrent.atomic.AtomicLong

object MpscConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("MpscConcurrencySpec")(
    test("4 producers, 1 consumer, 100K items: no loss, no duplicates, correct sum") {
      ZIO.attemptBlocking {
        val numProducers     = 4
        val itemsPerProducer = 25_000
        val totalItems       = numProducers * itemsPerProducer
        val rb               = new MpscRingBuffer[java.lang.Long](1024)
        val expectedSum      = {
          // Each producer p sends: p * totalItems + 0, p * totalItems + 1, ..., p * totalItems + (itemsPerProducer - 1)
          // Sum for producer p = p * totalItems * itemsPerProducer + itemsPerProducer * (itemsPerProducer - 1) / 2
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
              if (rb.offer(java.lang.Long.valueOf(base + i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producersDone.countDown()
          })
        }

        val consumer = new Thread(() => {
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
    test("2 producers, 1 consumer, 500K items: no loss, correct sum") {
      ZIO.attemptBlocking {
        val numProducers     = 2
        val itemsPerProducer = 250_000
        val totalItems       = numProducers * itemsPerProducer
        val rb               = new MpscRingBuffer[java.lang.Long](1024)
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
              if (rb.offer(java.lang.Long.valueOf(base + i))) {
                i += 1
              } else {
                Thread.onSpinWait()
              }
            }
            producersDone.countDown()
          })
        }

        val consumer = new Thread(() => {
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
    test("SPSC degenerate case: 1 producer, 1 consumer, 100K items") {
      ZIO.attemptBlocking {
        val count       = 100_000
        val rb          = new MpscRingBuffer[java.lang.Integer](1024)
        val expectedSum = count.toLong * (count - 1) / 2

        val producerDone = new CountDownLatch(1)
        val consumerDone = new CountDownLatch(1)
        val actualSum    = new AtomicLong(0L)

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
          while (received < count) {
            val v = rb.take()
            if (v != null) {
              actualSum.addAndGet(v.longValue())
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
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds)
  )
}
