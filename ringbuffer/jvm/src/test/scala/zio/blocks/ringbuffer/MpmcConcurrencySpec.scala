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

object MpmcConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("MpmcConcurrencySpec")(
    mpmcHammerTest(
      label = "4 producers, 4 consumers, 100K items",
      numProducers = 4,
      numConsumers = 4,
      totalItems = 100_000,
      bufferCapacity = 1024
    ),
    mpmcHammerTest(
      label = "2 producers, 2 consumers, 100K items",
      numProducers = 2,
      numConsumers = 2,
      totalItems = 100_000,
      bufferCapacity = 1024
    ),
    mpmcHammerTest(
      label = "1 producer, 1 consumer (degenerate SPSC), 100K items",
      numProducers = 1,
      numConsumers = 1,
      totalItems = 100_000,
      bufferCapacity = 1024
    )
  )

  /**
   * Generic MPMC hammer test.
   *
   * Each producer is assigned a contiguous range of integers. All consumers
   * collect consumed values into a shared AtomicLong sum. We verify: total sum
   * matches expected, total count matches expected, meaning each element was
   * consumed exactly once.
   */
  private def mpmcHammerTest(
    label: String,
    numProducers: Int,
    numConsumers: Int,
    totalItems: Int,
    bufferCapacity: Int
  ): Spec[Any, Throwable] =
    test(label) {
      ZIO.attemptBlocking {
        val rb = new MpmcRingBuffer[java.lang.Long](bufferCapacity)

        // Each producer gets a contiguous range
        val itemsPerProducer  = totalItems / numProducers
        val expectedSum: Long = totalItems.toLong * (totalItems - 1) / 2

        val producersDone        = new CountDownLatch(numProducers)
        val consumersDone        = new CountDownLatch(numConsumers)
        val actualSum            = new AtomicLong(0L)
        val actualCount          = new AtomicLong(0L)
        val allProducersFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

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
            s"mpmc-producer-$pId"
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
                  // Drain remaining
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
            s"mpmc-consumer-$cId"
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
    } @@ TestAspect.timeout(60.seconds)
}
