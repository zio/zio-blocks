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

object SpmcConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("SpmcConcurrencySpec")(
    spmcHammerTest(numConsumers = 4, numItems = 100_000),
    spmcHammerTest(numConsumers = 2, numItems = 100_000),
    spmcHammerTest(numConsumers = 1, numItems = 100_000),
    suite("Capacity Sweep: SPMC hammer at various capacities")(
      spmcCapacitySweepTest(cap = 1, numConsumers = 2, numItems = 50_000),
      spmcCapacitySweepTest(cap = 2, numConsumers = 2, numItems = 50_000),
      spmcCapacitySweepTest(cap = 4, numConsumers = 4, numItems = 50_000),
      spmcCapacitySweepTest(cap = 16, numConsumers = 4, numItems = 50_000),
      spmcCapacitySweepTest(cap = 1024, numConsumers = 4, numItems = 50_000)
    )
  )

  private def spmcHammerTest(numConsumers: Int, numItems: Int) =
    test(s"SPMC Hammer: 1 producer, $numConsumers consumers, ${numItems} items — no loss, no duplicates, correct sum") {
      ZIO.attemptBlocking {
        val rb          = new SpmcRingBuffer[java.lang.Long](1024)
        val expectedSum = numItems.toLong * (numItems - 1) / 2

        val allDone   = new CountDownLatch(1 + numConsumers)
        val actualSum = new AtomicLong(0L)
        val count     = new AtomicLong(0L)
        val dupError  = new AtomicBoolean(false)

        // Track which items were consumed (for duplicate/loss detection)
        val seen = new java.util.concurrent.ConcurrentHashMap[Long, java.lang.Boolean](numItems * 2)

        val producer = new Thread(() => {
          var i = 0L
          while (i < numItems) {
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
            while (count.get() < numItems) {
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
          count.get() == numItems.toLong,
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds)

  private def spmcCapacitySweepTest(cap: Int, numConsumers: Int, numItems: Int) =
    test(s"SPMC hammer with capacity=$cap, $numConsumers consumers") {
      ZIO.attemptBlocking {
        val rb          = new SpmcRingBuffer[java.lang.Long](cap)
        val expectedSum = numItems.toLong * (numItems - 1) / 2

        val allDone   = new CountDownLatch(1 + numConsumers)
        val actualSum = new AtomicLong(0L)
        val count     = new AtomicLong(0L)
        val dupError  = new AtomicBoolean(false)

        val seen = new java.util.concurrent.ConcurrentHashMap[Long, java.lang.Boolean](numItems * 2)

        val producer = new Thread(() => {
          var i = 0L
          while (i < numItems) {
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
            while (count.get() < numItems) {
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
          count.get() == numItems.toLong,
          actualSum.get() == expectedSum,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(60.seconds)
}
