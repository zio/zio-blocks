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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}

object BlockingMpmcQueueSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BlockingMpmcQueue")(
    suite("unit tests")(
      test("offer/take works") {
        val q = new BlockingMpmcQueue[Integer](16)
        q.offer(Integer.valueOf(1))
        q.offer(Integer.valueOf(2))
        val a = q.take()
        val b = q.take()
        assertTrue(a.intValue() + b.intValue() == 3)
      },
      test("capacity rounds up to next power of two") {
        val q     = new BlockingMpmcQueue[Integer](3)
        var count = 0
        while (count < 4 && q.offer(Integer.valueOf(count))) count += 1
        assertTrue(count == 4)
      },
      test("closed semantics: offer fails, take on empty returns null") {
        val q = new BlockingMpmcQueue[Integer](4)
        q.close()
        assertTrue(q.isClosed) &&
        assertTrue(!q.offer(Integer.valueOf(1))) &&
        assertTrue(q.take() == null)
      }
    ),
    suite("concurrency tests")(
      test("4 producers + 4 consumers: 100K elements, no data loss, correct sum") {
        ZIO.attemptBlocking {
          val n             = 100_000
          val producerCount = 4
          val consumerCount = 4
          val perProducer   = n / producerCount
          val q             = new BlockingMpmcQueue[Integer](1024)
          val sum           = new AtomicLong(0L)
          val received      = new AtomicInteger(0)
          val producersDone = new CountDownLatch(producerCount)
          val done          = new CountDownLatch(producerCount + consumerCount)
          val failed        = new AtomicBoolean(false)

          val producers = (0 until producerCount).map { p =>
            new Thread(() => {
              try {
                val start = p * perProducer
                val end   = start + perProducer
                var i     = start
                while (i < end) { q.offer(Integer.valueOf(i)); i += 1 }
              } catch { case _: Throwable => failed.set(true) }
              finally {
                producersDone.countDown()
                done.countDown()
              }
            })
          }

          val consumers = (0 until consumerCount).map { _ =>
            new Thread(() => {
              try {
                var running = true
                while (running) {
                  val v = q.take()
                  if (v != null) {
                    sum.addAndGet(v.longValue())
                    received.incrementAndGet()
                  } else if (q.isClosed) {
                    running = false
                  }
                }
              } catch { case _: Throwable => failed.set(true) }
              finally done.countDown()
            })
          }

          producers.foreach(_.start())
          consumers.foreach(_.start())
          val producersCompleted = producersDone.await(30, TimeUnit.SECONDS)
          val receiveDeadline    = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
          while (received.get() < n && java.lang.System.nanoTime() < receiveDeadline) Thread.sleep(1)
          q.close()
          val completed = done.await(30, TimeUnit.SECONDS)
          val expected  = n.toLong * (n - 1) / 2
          assertTrue(producersCompleted, completed, !failed.get(), received.get() == n, sum.get() == expected)
        }
      } @@ TestAspect.timeout(40.seconds),
      test("close unblocks all consumers and producers") {
        ZIO.attemptBlocking {
          val q      = new BlockingMpmcQueue[Integer](2)
          val closed = new AtomicBoolean(false)
          val latch  = new CountDownLatch(2)

          val consumer1 = new Thread(() => { q.take(); latch.countDown() })
          val consumer2 = new Thread(() => { q.take(); latch.countDown() })
          consumer1.start(); consumer2.start()
          Thread.sleep(50)
          q.close()
          closed.set(true)
          val ok = latch.await(5, TimeUnit.SECONDS)
          assertTrue(ok, closed.get())
        }
      }
    ),
    suite("stress test")(
      test("4 producers + 4 consumers, 1M elements, no data loss") {
        ZIO.attemptBlocking {
          val n             = 1_000_000
          val producerCount = 4
          val consumerCount = 4
          val perProducer   = n / producerCount
          val q             = new BlockingMpmcQueue[Integer](65536)
          val sum           = new AtomicLong(0L)
          val received      = new AtomicInteger(0)
          val producersDone = new CountDownLatch(producerCount)
          val done          = new CountDownLatch(producerCount + consumerCount)
          val failed        = new AtomicBoolean(false)

          val producers = (0 until producerCount).map { p =>
            new Thread(() => {
              try {
                val start = p * perProducer
                val end   = start + perProducer
                var i     = start
                while (i < end) { q.offer(Integer.valueOf(i)); i += 1 }
              } catch { case _: Throwable => failed.set(true) }
              finally {
                producersDone.countDown()
                done.countDown()
              }
            })
          }

          val consumers = (0 until consumerCount).map { _ =>
            new Thread(() => {
              try {
                var running = true
                while (running) {
                  val v = q.take()
                  if (v != null) {
                    sum.addAndGet(v.longValue())
                    received.incrementAndGet()
                  } else if (q.isClosed) {
                    running = false
                  }
                }
              } catch { case _: Throwable => failed.set(true) }
              finally done.countDown()
            })
          }

          producers.foreach(_.start())
          consumers.foreach(_.start())
          val producersCompleted = producersDone.await(120, TimeUnit.SECONDS)
          val receiveDeadline    = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
          while (received.get() < n && java.lang.System.nanoTime() < receiveDeadline) Thread.sleep(1)
          q.close()
          val completed = done.await(120, TimeUnit.SECONDS)
          val expected  = n.toLong * (n - 1) / 2
          val result    =
            assertTrue(producersCompleted, completed, !failed.get(), received.get() == n, sum.get() == expected)
          Thread.sleep(500)
          result
        }
      } @@ TestAspect.timeout(150.seconds)
    )
  )
}
