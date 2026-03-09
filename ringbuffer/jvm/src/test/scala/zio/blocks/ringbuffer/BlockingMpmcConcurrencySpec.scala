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

object BlockingMpmcConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("BlockingMpmcConcurrencySpec")(
    suite("put/take hammer")(
      blockingHammerTest(
        label = "4 producers, 4 consumers, 100K items",
        numProducers = 4,
        numConsumers = 4,
        totalItems = 100_000,
        bufferCapacity = 1024
      ),
      blockingHammerTest(
        label = "2 producers, 2 consumers, 100K items",
        numProducers = 2,
        numConsumers = 2,
        totalItems = 100_000,
        bufferCapacity = 1024
      ),
      blockingHammerTest(
        label = "1 producer, 1 consumer (degenerate SPSC), 100K items",
        numProducers = 1,
        numConsumers = 1,
        totalItems = 100_000,
        bufferCapacity = 1024
      )
    ),
    suite("capacity sweep")(
      blockingHammerTest(
        label = "capacity=2, 2P/2C",
        numProducers = 2,
        numConsumers = 2,
        totalItems = 50_000,
        bufferCapacity = 2
      ),
      blockingHammerTest(
        label = "capacity=4, 4P/4C",
        numProducers = 4,
        numConsumers = 4,
        totalItems = 50_000,
        bufferCapacity = 4
      ),
      blockingHammerTest(
        label = "capacity=16, 4P/4C",
        numProducers = 4,
        numConsumers = 4,
        totalItems = 50_000,
        bufferCapacity = 16
      )
    ),
    suite("offer blocks when full, unblocks on tryTake")(
      test("producer blocks on full buffer and resumes when consumer drains") {
        ZIO.attemptBlocking {
          val rb       = new BlockingMpmcRingBuffer[java.lang.Integer](2)
          val finished = new CountDownLatch(1)

          // Fill the buffer
          rb.tryOffer(java.lang.Integer.valueOf(1))
          rb.tryOffer(java.lang.Integer.valueOf(2))

          val producer = new Thread(() => {
            rb.offer(java.lang.Integer.valueOf(3))
            finished.countDown()
          })
          producer.start()

          // Give the producer time to park, then drain one element
          Thread.sleep(50)
          rb.tryTake()

          val completed = finished.await(5, java.util.concurrent.TimeUnit.SECONDS)
          assertTrue(completed)
        }
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("take blocks when empty, unblocks on tryOffer")(
      test("consumer blocks on empty buffer and resumes when producer tryOffers") {
        ZIO.attemptBlocking {
          val rb       = new BlockingMpmcRingBuffer[String](4)
          val finished = new CountDownLatch(1)
          val result   = new java.util.concurrent.atomic.AtomicReference[String](null)

          val consumer = new Thread(() => {
            val v = rb.take()
            result.set(v)
            finished.countDown()
          })
          consumer.start()

          // Give the consumer time to block, then tryOffer an element
          Thread.sleep(50)
          rb.tryOffer("hello")

          val completed = finished.await(5, java.util.concurrent.TimeUnit.SECONDS)
          assertTrue(completed, result.get() == "hello")
        }
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("interrupt handling")(
      test("offer responds to interrupt") {
        ZIO.attemptBlocking {
          val rb    = new BlockingMpmcRingBuffer[java.lang.Integer](2)
          val ready = new CountDownLatch(1)
          val error = new AtomicBoolean(false)
          val gotIE = new AtomicBoolean(false)

          rb.tryOffer(java.lang.Integer.valueOf(1))
          rb.tryOffer(java.lang.Integer.valueOf(2))

          val producer = new Thread(() => {
            ready.countDown()
            try
              rb.offer(java.lang.Integer.valueOf(3))
            catch {
              case _: InterruptedException => gotIE.set(true)
              case _: Throwable            => error.set(true)
            }
          })
          producer.start()

          ready.await()
          Thread.sleep(50)
          producer.interrupt()
          producer.join(5000)

          assertTrue(gotIE.get(), !error.get(), !producer.isAlive)
        }
      } @@ TestAspect.timeout(10.seconds),
      test("take responds to interrupt") {
        ZIO.attemptBlocking {
          val rb    = new BlockingMpmcRingBuffer[String](4)
          val ready = new CountDownLatch(1)
          val error = new AtomicBoolean(false)
          val gotIE = new AtomicBoolean(false)

          val consumer = new Thread((() => {
            ready.countDown()
            try { val _ = rb.take() }
            catch {
              case _: InterruptedException => gotIE.set(true)
              case _: Throwable            => error.set(true)
            }
          }): Runnable)
          consumer.start()

          ready.await()
          Thread.sleep(50)
          consumer.interrupt()
          consumer.join(5000)

          assertTrue(gotIE.get(), !error.get(), !consumer.isAlive)
        }
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("null rejection")(
      test("offer(null) throws NullPointerException") {
        ZIO.attemptBlocking {
          val rb = new BlockingMpmcRingBuffer[String](4)
          assertTrue(throwsNPE(rb.offer(null)))
        }
      }
    ),
    suite("multiple producers and consumers")(
      test("multiple blocked producers all unblock when consumers drain") {
        ZIO.attemptBlocking {
          val rb         = new BlockingMpmcRingBuffer[java.lang.Integer](2)
          val numThreads = 4
          val ready      = new CountDownLatch(numThreads)
          val finished   = new CountDownLatch(numThreads)

          // Fill the buffer
          rb.tryOffer(java.lang.Integer.valueOf(0))
          rb.tryOffer(java.lang.Integer.valueOf(0))

          // Start producers that will block on full buffer
          (1 to numThreads).foreach { i =>
            new Thread(() => {
              ready.countDown()
              rb.offer(java.lang.Integer.valueOf(i))
              finished.countDown()
            }).start()
          }

          ready.await()
          Thread.sleep(100) // Let all producers block

          // Drain elements to unblock producers
          val drained = (0 until (numThreads + 2)).map { _ =>
            val v = rb.take()
            v.intValue()
          }

          finished.await()
          assertTrue(drained.sum == (1 to numThreads).sum, rb.isEmpty)
        }
      } @@ TestAspect.timeout(10.seconds),
      test("multiple blocked consumers all get woken and receive elements") {
        ZIO.attemptBlocking {
          val rb         = new BlockingMpmcRingBuffer[java.lang.Integer](16)
          val numThreads = 4
          val ready      = new CountDownLatch(numThreads)
          val finished   = new CountDownLatch(numThreads)
          val sum        = new AtomicLong(0L)

          // Start consumers that will block on empty buffer
          (0 until numThreads).foreach { _ =>
            new Thread(() => {
              ready.countDown()
              val v = rb.take()
              sum.addAndGet(v.longValue())
              finished.countDown()
            }).start()
          }

          ready.await()
          Thread.sleep(50)

          // Offer elements
          (1 to numThreads).foreach { i =>
            rb.offer(java.lang.Integer.valueOf(i))
          }

          finished.await()

          val expected = (1 to numThreads).map(_.toLong).sum
          assertTrue(sum.get() == expected, rb.isEmpty)
        }
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("all-blocking hammer (put/take only)")(
      allBlockingHammerTest(
        label = "4P/4C, 50K items, capacity=64",
        numProducers = 4,
        numConsumers = 4,
        totalItems = 50_000,
        bufferCapacity = 64
      ),
      allBlockingHammerTest(
        label = "2P/2C, 50K items, capacity=2",
        numProducers = 2,
        numConsumers = 2,
        totalItems = 50_000,
        bufferCapacity = 2
      )
    )
  )

  /**
   * Hammer test using mixed offer/poll (non-blocking) + put for the producer.
   * Consumers use poll with spin-wait.
   */
  private def blockingHammerTest(
    label: String,
    numProducers: Int,
    numConsumers: Int,
    totalItems: Int,
    bufferCapacity: Int
  ): Spec[Any, Throwable] =
    test(label) {
      ZIO.attemptBlocking {
        val rb = new BlockingMpmcRingBuffer[java.lang.Long](bufferCapacity)

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
                rb.offer(java.lang.Long.valueOf(i))
                i += 1
              }
              producersDone.countDown()
            },
            s"bmpmc-producer-$pId"
          )
        }

        val consumers = (0 until numConsumers).map { cId =>
          new Thread(
            () => {
              var localSum   = 0L
              var localCount = 0L
              var running    = true
              while (running) {
                val v = rb.tryTake()
                if (v != null) {
                  localSum += v.longValue()
                  localCount += 1
                } else if (allProducersFinished.get()) {
                  var v2 = rb.tryTake()
                  while (v2 != null) {
                    localSum += v2.longValue()
                    localCount += 1
                    v2 = rb.tryTake()
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
            s"bmpmc-consumer-$cId"
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

  /**
   * Hammer test using exclusively put/take (blocking on both sides). All
   * producers use put(), all consumers use take().
   */
  private def allBlockingHammerTest(
    label: String,
    numProducers: Int,
    numConsumers: Int,
    totalItems: Int,
    bufferCapacity: Int
  ): Spec[Any, Throwable] =
    test(label) {
      ZIO.attemptBlocking {
        val rb = new BlockingMpmcRingBuffer[java.lang.Long](bufferCapacity)

        val itemsPerProducer  = totalItems / numProducers
        val expectedSum: Long = totalItems.toLong * (totalItems - 1) / 2

        val actualSum   = new AtomicLong(0L)
        val actualCount = new AtomicLong(0L)
        val allDone     = new CountDownLatch(numProducers + numConsumers)

        val producers = (0 until numProducers).map { pId =>
          val start = pId.toLong * itemsPerProducer
          val end   = if (pId == numProducers - 1) totalItems.toLong else start + itemsPerProducer
          new Thread(
            () => {
              var i = start
              while (i < end) {
                rb.offer(java.lang.Long.valueOf(i))
                i += 1
              }
              allDone.countDown()
            },
            s"bmpmc-bp-$pId"
          )
        }

        val consumers = (0 until numConsumers).map { cId =>
          new Thread(
            () => {
              var localSum   = 0L
              var localCount = 0L
              while (localCount < totalItems.toLong / numConsumers) {
                val v = rb.take()
                localSum += v.longValue()
                localCount += 1
              }
              actualSum.addAndGet(localSum)
              actualCount.addAndGet(localCount)
              allDone.countDown()
            },
            s"bmpmc-bc-$cId"
          )
        }

        producers.foreach(_.start())
        consumers.foreach(_.start())
        allDone.await()

        assertTrue(
          actualSum.get() == expectedSum,
          actualCount.get() == totalItems.toLong,
          rb.isEmpty
        )
      }
    } @@ TestAspect.timeout(120.seconds)

  private def throwsNPE(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: NullPointerException => true
      case _: Throwable            => false
    }
}
