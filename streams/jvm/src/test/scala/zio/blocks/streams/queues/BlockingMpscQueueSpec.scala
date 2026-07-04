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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}

object BlockingMpscQueueSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BlockingMpscQueue")(
    suite("unit tests")(
      test("offer/take preserves FIFO ordering") {
        val q = new BlockingMpscQueue[Integer](16)
        var i = 0
        while (i < 10) {
          q.offer(Integer.valueOf(i))
          i += 1
        }
        val results = (0 until 10).map(_ => q.take().intValue())
        assertTrue(results.toList == (0 until 10).toList)
      },
      test("capacity is rounded up to next power of two") {
        val q     = new BlockingMpscQueue[Integer](3)
        var count = 0
        while (count < 4 && q.offer(Integer.valueOf(count))) {
          count += 1
        }
        assertTrue(count == 4)
      },
      test("closed semantics: offer fails, take on closed-empty returns null") {
        val q = new BlockingMpscQueue[Integer](4)
        q.close()
        assertTrue(q.isClosed) &&
        assertTrue(!q.offer(Integer.valueOf(1))) &&
        assertTrue(q.take() == null)
      }
    ),
    suite("concurrency tests")(
      test("4 producers + 1 consumer: 100K elements, no data loss, correct sum") {
        ZIO.attemptBlocking {
          val n             = 100_000
          val producerCount = 4
          val perProducer   = n / producerCount
          val q             = new BlockingMpscQueue[Integer](16384)
          val sum           = new AtomicLong(0L)
          val received      = new AtomicInteger(0)
          val done          = new CountDownLatch(producerCount + 1)
          val failed        = new AtomicBoolean(false)

          val producers = (0 until producerCount).map { p =>
            new Thread(() => {
              try {
                val start = p * perProducer
                val end   = start + perProducer
                var i     = start
                while (i < end) {
                  q.offer(Integer.valueOf(i))
                  i += 1
                }
              } catch {
                case _: Throwable => failed.set(true)
              } finally done.countDown()
            })
          }

          val consumer = new Thread(() => {
            try {
              while (received.get() < n) {
                val v = q.take()
                if (v != null) {
                  sum.addAndGet(v.longValue())
                  received.incrementAndGet()
                }
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          producers.foreach(_.start())
          consumer.start()
          val completed = done.await(25, TimeUnit.SECONDS)
          val expected  = n.toLong * (n - 1) / 2

          assertTrue(completed, !failed.get(), received.get() == n, sum.get() == expected)
        }
      } @@ TestAspect.timeout(35.seconds),
      test("close unblocks consumer blocked on empty queue") {
        ZIO.attemptBlocking {
          val q            = new BlockingMpscQueue[Integer](4)
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
      test("close unblocks producers blocked on full queue") {
        ZIO.attemptBlocking {
          val q = new BlockingMpscQueue[Integer](1)
          q.offer(Integer.valueOf(0))

          val producerCount = 4
          val started       = new CountDownLatch(producerCount)
          val done          = new CountDownLatch(producerCount)
          val returned      = new AtomicInteger(0)
          val success       = new AtomicInteger(0)

          val producers = (0 until producerCount).map { i =>
            new Thread(() => {
              started.countDown()
              val ok = q.offer(Integer.valueOf(i + 1))
              if (ok) success.incrementAndGet()
              returned.incrementAndGet()
              done.countDown()
            })
          }

          producers.foreach(_.start())
          started.await()

          var blockedObserved = false
          var spins           = 0
          while (!blockedObserved && spins < 1_000_000) {
            val states = producers.map(_.getState)
            blockedObserved = returned.get() < producerCount &&
              states.exists(s =>
                s == Thread.State.TIMED_WAITING || s == Thread.State.WAITING || s == Thread.State.BLOCKED
              )
            if (!blockedObserved) Thread.onSpinWait()
            spins += 1
          }

          q.close()
          val completed = done.await(5, TimeUnit.SECONDS)

          assertTrue(blockedObserved, completed, returned.get() == producerCount, success.get() == 0)
        }
      }
    ),
    suite("stress test")(
      test("8 producers, 1M elements: no data loss, correct sum") {
        ZIO.attemptBlocking {
          val n             = 1_000_000
          val producerCount = 8
          val perProducer   = n / producerCount
          val q             = new BlockingMpscQueue[Integer](262144)
          val sum           = new AtomicLong(0L)
          val received      = new AtomicInteger(0)
          val done          = new CountDownLatch(producerCount + 1)
          val failed        = new AtomicBoolean(false)

          val producers = (0 until producerCount).map { p =>
            new Thread(() => {
              try {
                val start = p * perProducer
                val end   = start + perProducer
                var i     = start
                while (i < end) {
                  q.offer(Integer.valueOf(i))
                  i += 1
                }
              } catch {
                case _: Throwable => failed.set(true)
              } finally done.countDown()
            })
          }

          val consumer = new Thread(() => {
            try {
              while (received.get() < n) {
                val v = q.take()
                if (v != null) {
                  sum.addAndGet(v.longValue())
                  received.incrementAndGet()
                }
              }
            } catch {
              case _: Throwable => failed.set(true)
            } finally done.countDown()
          })

          producers.foreach(_.start())
          consumer.start()

          val completed = done.await(60, TimeUnit.SECONDS)
          val expected  = n.toLong * (n - 1) / 2

          val result = assertTrue(completed, !failed.get(), received.get() == n, sum.get() == expected)
          // Allow threads to fully terminate before next test
          Thread.sleep(500)
          result
        }
      } @@ TestAspect.timeout(120.seconds)
    )
  )
}
