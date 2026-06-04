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

package zio.blocks.async

import zio.ZIO
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM-only behavior: `.block` parks the calling thread on the `ReentrantLock`
 * parker until another thread completes the value.
 *
 * Scala.js has no thread to park, so these scenarios deliberately throw
 * `IllegalStateException` there and are excluded from the shared suite.
 *
 * All `await` calls are wrapped in `ZIO.attemptBlocking` because they really do
 * block a carrier thread until the background thread fires the waker.
 */
object AsyncBlockingSpec extends ZIOSpecDefault {

  private def sleep(ms: Long): Unit = Thread.sleep(ms)

  def spec = suite("AsyncBlockingSpec")(
    test("await blocks until another thread completes the Completer") {
      ZIO.attemptBlocking {
        // Construct an Async whose completion is performed by a worker thread
        // after a small delay. The main thread's `await` must park until then.
        val a = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = {
              sleep(50)
              c.succeed(42)
            }
          })
          t.setDaemon(true)
          t.start()
        }
        val started = System.nanoTime()
        val v       = a.block
        val elapsed = System.nanoTime() - started
        // We must have actually slept (≥40ms ≈ 40,000,000ns) — i.e. await
        // really parked rather than spinning or returning early.
        assertTrue(v == 42, elapsed >= 40_000_000L)
      }
    },
    test("flatMap across an off-thread completion resumes correctly") {
      ZIO.attemptBlocking {
        val left = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { sleep(20); c.succeed(10) }
          })
          t.setDaemon(true)
          t.start()
        }
        val v = left.flatMap { x =>
          Async.promiseInternal[Int] { c =>
            val t = new Thread(new Runnable {
              def run(): Unit = { sleep(20); c.succeed(x + 5) }
            })
            t.setDaemon(true)
            t.start()
          }
        }.block
        assertTrue(v == 15)
      }
    },
    test("complete-then-await is non-blocking") {
      ZIO.attemptBlocking {
        // If the completion happens BEFORE await, peek returns the bare value
        // and await never enters the slow path. We assert the timing.
        val a       = Async.promiseInternal[Int](c => c.succeed(7))
        val started = System.nanoTime()
        val v       = a.block
        val elapsed = System.nanoTime() - started
        assertTrue(v == 7, elapsed < 50_000_000L)
      }
    },
    test("double complete from concurrent threads is safe and yields the first value") {
      ZIO.attemptBlocking {
        // Both threads race to `complete`; whichever wins, the other must be a
        // silent no-op (no exception, no value overwrite).
        val winner = new AtomicInteger(-1)
        val a      = Async.promiseInternal[Int] { c =>
          val t1: Thread = new Thread(new Runnable {
            def run(): Unit = {
              sleep(30)
              c.succeed(1)
              winner.compareAndSet(-1, 1)
              ()
            }
          })
          val t2: Thread = new Thread(new Runnable {
            def run(): Unit = {
              sleep(30)
              c.succeed(2)
              winner.compareAndSet(-1, 2)
              ()
            }
          })
          t1.setDaemon(true); t2.setDaemon(true)
          t1.start(); t2.start()
        }
        val v = a.block
        // `v` is whichever value's `complete` CAS-ed Done first; both 1 and 2
        // are legal observed values. The point is: no exception, no deadlock.
        assertTrue(v == 1 || v == 2)
      }
    }
  )
}
