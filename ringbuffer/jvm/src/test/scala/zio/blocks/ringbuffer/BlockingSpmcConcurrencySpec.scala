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

object BlockingSpmcConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("BlockingSpmcConcurrencySpec")(
    suite("put/take hammer")(
      blockingHammerTest(numConsumers = 4, numItems = 100_000, bufferCapacity = 1024),
      blockingHammerTest(numConsumers = 2, numItems = 100_000, bufferCapacity = 1024),
      blockingHammerTest(numConsumers = 1, numItems = 100_000, bufferCapacity = 1024)
    ),
    suite("capacity sweep")(
      blockingHammerTest(numConsumers = 2, numItems = 50_000, bufferCapacity = 1),
      blockingHammerTest(numConsumers = 2, numItems = 50_000, bufferCapacity = 2),
      blockingHammerTest(numConsumers = 4, numItems = 50_000, bufferCapacity = 4),
      blockingHammerTest(numConsumers = 4, numItems = 50_000, bufferCapacity = 16)
    ),
    suite("offer blocks when full, unblocks on tryTake")(
      test("producer blocks on full buffer and resumes when consumer drains") {
        ZIO.attemptBlocking {
          val rb       = new BlockingSpmcRingBuffer[java.lang.Integer](2)
          val finished = new CountDownLatch(1)

          // Fill the buffer
          rb.tryOffer(java.lang.Integer.valueOf(1))
          rb.tryOffer(java.lang.Integer.valueOf(2))

          // Producer will block on offer because buffer is full
          val producer = new Thread(() => {
            rb.offer(java.lang.Integer.valueOf(3))
            finished.countDown()
          })
          producer.start()

          // Give the producer time to park, then drain one element
          Thread.sleep(50)
          rb.tryTake()

          // Producer should unblock and complete
          val completed = finished.await(5, java.util.concurrent.TimeUnit.SECONDS)
          assertTrue(completed)
        }
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("take blocks when empty, unblocks on offer")(
      test("consumer blocks on empty buffer and resumes when producer offers") {
        ZIO.attemptBlocking {
          val rb       = new BlockingSpmcRingBuffer[String](4)
          val finished = new CountDownLatch(1)
          val result   = new java.util.concurrent.atomic.AtomicReference[String](null)

          val consumer = new Thread(() => {
            val v = rb.take()
            result.set(v)
            finished.countDown()
          })
          consumer.start()

          // Give the consumer time to block, then offer an element
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
          val rb    = new BlockingSpmcRingBuffer[java.lang.Integer](1)
          val ready = new CountDownLatch(1)
          val error = new AtomicBoolean(false)
          val gotIE = new AtomicBoolean(false)

          rb.tryOffer(java.lang.Integer.valueOf(1)) // fill the buffer

          val producer = new Thread(() => {
            ready.countDown()
            try
              rb.offer(java.lang.Integer.valueOf(2)) // should block
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
          val rb    = new BlockingSpmcRingBuffer[String](4)
          val ready = new CountDownLatch(1)
          val error = new AtomicBoolean(false)
          val gotIE = new AtomicBoolean(false)

          val consumer = new Thread((() => {
            ready.countDown()
            try { val _ = rb.take() } // should block — buffer is empty
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
          val rb = new BlockingSpmcRingBuffer[String](4)
          assertTrue(throwsNPE(rb.offer(null)))
        }
      }
    ),
    suite("multiple consumers take concurrently")(
      test("multiple blocked consumers all get woken and receive elements") {
        ZIO.attemptBlocking {
          val rb         = new BlockingSpmcRingBuffer[java.lang.Integer](16)
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
          Thread.sleep(50) // Let all consumers block

          // Now offer elements — each consumer should get one
          (1 to numThreads).foreach { i =>
            rb.offer(java.lang.Integer.valueOf(i))
          }

          finished.await()

          val expected = (1 to numThreads).map(_.toLong).sum
          assertTrue(sum.get() == expected, rb.isEmpty)
        }
      } @@ TestAspect.timeout(10.seconds)
    )
  )

  private def blockingHammerTest(numConsumers: Int, numItems: Int, bufferCapacity: Int) =
    test(
      s"SPMC blocking: 1 producer, $numConsumers consumers, $numItems items, capacity=$bufferCapacity"
    ) {
      ZIO.attemptBlocking {
        val rb          = new BlockingSpmcRingBuffer[java.lang.Long](bufferCapacity)
        val expectedSum = numItems.toLong * (numItems - 1) / 2

        val allDone   = new CountDownLatch(1 + numConsumers)
        val actualSum = new AtomicLong(0L)
        val count     = new AtomicLong(0L)
        val dupError  = new AtomicBoolean(false)

        val seen = new java.util.concurrent.ConcurrentHashMap[Long, java.lang.Boolean](numItems * 2)

        val producer = new Thread(
          () => {
            var i = 0L
            while (i < numItems) {
              rb.offer(java.lang.Long.valueOf(i))
              i += 1
            }
            allDone.countDown()
          },
          "bspmc-producer"
        )

        val consumers = (0 until numConsumers).map { cId =>
          new Thread(
            () => {
              while (count.get() < numItems) {
                val v = rb.tryTake()
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
            },
            s"bspmc-consumer-$cId"
          )
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

  private def throwsNPE(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: NullPointerException => true
      case _: Throwable            => false
    }
}
