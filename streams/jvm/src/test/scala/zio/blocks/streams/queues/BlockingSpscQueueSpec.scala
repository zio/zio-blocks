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

package zio.blocks.streams.queues

import zio._
import zio.blocks.streams.StreamsBaseSpec
import zio.test._

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

object BlockingSpscQueueSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BlockingSpscQueue")(
    suite("unit tests")(
      test("offer then take returns same element") {
        val q = new BlockingSpscQueue[Integer](4)
        assertTrue(q.offer(Integer.valueOf(42))) &&
        assertTrue(q.take() == Integer.valueOf(42))
      },
      test("take from closed empty queue returns null") {
        val q = new BlockingSpscQueue[Integer](4)
        q.close()
        assertTrue(q.isClosed) &&
        assertTrue(q.take() == null)
      },
      test("offer to closed queue returns false") {
        val q = new BlockingSpscQueue[Integer](4)
        q.close()
        assertTrue(!q.offer(Integer.valueOf(1)))
      },
      test("isEmpty is true initially, false after offer") {
        val q = new BlockingSpscQueue[Integer](4)
        assertTrue(q.isEmpty) &&
        assertTrue(q.offer(Integer.valueOf(1))) &&
        assertTrue(!q.isEmpty) &&
        assertTrue(q.take() == Integer.valueOf(1)) &&
        assertTrue(q.isEmpty)
      },
      test("ordering: offer N elements, take N in same order") {
        val q = new BlockingSpscQueue[Integer](16)
        (0 until 10).foreach(i => q.offer(Integer.valueOf(i)))
        val results = (0 until 10).map(_ => q.take().intValue())
        assertTrue(results.toList == (0 until 10).toList)
      },
      test("double close is safe (no exception)") {
        val q = new BlockingSpscQueue[Integer](4)
        q.close()
        q.close()
        assertTrue(q.isClosed)
      },
      test("capacity is rounded up to next power of two") {
        val q     = new BlockingSpscQueue[Integer](3)
        var count = 0
        while (count < 4 && q.offer(Integer.valueOf(count))) {
          count += 1
        }
        assertTrue(count == 4)
      }
    ),
    suite("concurrency tests")(
      test("producer-consumer: 100K elements, correct order and sum") {
        ZIO.attemptBlocking {
          val n      = 100_000
          val q      = new BlockingSpscQueue[Integer](16384)
          val sum    = new AtomicLong(0L)
          val order  = new AtomicBoolean(true)
          val done   = new CountDownLatch(2)
          val failed = new AtomicBoolean(false)

          val producer = new Thread(() => {
            try {
              var i = 0
              while (i < n) {
                q.offer(Integer.valueOf(i))
                i += 1
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          val consumer = new Thread(() => {
            try {
              var expected = 0
              var received = 0
              while (received < n) {
                val v = q.take()
                if (v != null) {
                  if (v.intValue() != expected) order.set(false)
                  sum.addAndGet(v.longValue())
                  expected += 1
                  received += 1
                }
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          producer.start()
          consumer.start()
          val completed = done.await(20, TimeUnit.SECONDS)

          assertTrue(
            completed,
            !failed.get(),
            order.get(),
            sum.get() == n.toLong * (n - 1) / 2
          )
        }
      } @@ TestAspect.timeout(30.seconds),
      test("close unblocks consumer blocked on empty queue") {
        ZIO.attemptBlocking {
          val q            = new BlockingSpscQueue[Integer](4)
          val started      = new CountDownLatch(1)
          val returnedTake = new AtomicBoolean(false)
          val result       = new AtomicReference[Integer](Integer.valueOf(-1))

          val consumer = new Thread(() => {
            started.countDown()
            val v = q.take()
            result.set(v)
            returnedTake.set(true)
          })

          consumer.start()
          started.await()

          var blockedObserved = false
          var spins           = 0
          while (!blockedObserved && spins < 1_000_000) {
            val state = consumer.getState
            blockedObserved = !returnedTake.get() && (state == Thread.State.WAITING || state == Thread.State.BLOCKED)
            if (!blockedObserved) Thread.onSpinWait()
            spins += 1
          }

          q.close()
          consumer.join(5000)

          assertTrue(blockedObserved, !consumer.isAlive, returnedTake.get(), result.get() == null)
        }
      },
      test("close unblocks producer blocked on full queue") {
        ZIO.attemptBlocking {
          val q = new BlockingSpscQueue[Integer](4)

          var i = 0
          while (i < 4) {
            q.offer(Integer.valueOf(i))
            i += 1
          }

          val enteredOffer  = new CountDownLatch(1)
          val returnedOffer = new AtomicBoolean(false)
          val result        = new AtomicBoolean(true)

          val producer = new Thread(() => {
            enteredOffer.countDown()
            result.set(q.offer(Integer.valueOf(99)))
            returnedOffer.set(true)
          })

          producer.start()
          enteredOffer.await()

          var blockedObserved = false
          var spins           = 0
          while (!blockedObserved && spins < 1_000_000) {
            val state = producer.getState
            blockedObserved = !returnedOffer.get() && (state == Thread.State.WAITING || state == Thread.State.BLOCKED)
            if (!blockedObserved) Thread.onSpinWait()
            spins += 1
          }

          q.close()
          producer.join(5000)

          assertTrue(blockedObserved, !producer.isAlive, returnedOffer.get(), !result.get())
        }
      }
    ),
    suite("stress test")(
      test("1M elements: no data loss, correct order, correct sum") {
        ZIO.attemptBlocking {
          val n        = 1_000_000
          val q        = new BlockingSpscQueue[Integer](262144)
          val sum      = new AtomicLong(0L)
          val order    = new AtomicBoolean(true)
          val done     = new CountDownLatch(2)
          val failed   = new AtomicBoolean(false)
          val expected = n.toLong * (n - 1) / 2

          val producer = new Thread(() => {
            try {
              var i = 0
              while (i < n) {
                q.offer(Integer.valueOf(i))
                i += 1
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          val consumer = new Thread(() => {
            try {
              var exp = 0
              var got = 0
              while (got < n) {
                val v = q.take()
                if (v != null) {
                  if (v.intValue() != exp) order.set(false)
                  sum.addAndGet(v.longValue())
                  exp += 1
                  got += 1
                }
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          producer.start()
          consumer.start()
          val completed = done.await(25, TimeUnit.SECONDS)

          assertTrue(completed, !failed.get(), order.get(), sum.get() == expected)
        }
      } @@ TestAspect.timeout(90.seconds)
    )
  )
}
