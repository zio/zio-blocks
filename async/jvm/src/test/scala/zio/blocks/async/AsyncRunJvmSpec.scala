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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import zio.ZIO
import zio.test._

/**
 * JVM-only semantics of [[Async.unsafeRunAsync]]: a suspended [[Async]] is
 * driven on a daemon worker thread (not the caller thread), and
 * [[Cancelable.cancel]] interrupts a parked worker so its callback is never
 * delivered. Scala.js has no worker thread, so these scenarios live here.
 */
object AsyncRunJvmSpec extends ZIOSpecDefault {

  def spec = suite("AsyncRunJvmSpec")(
    test("cb for a suspended Async runs on a worker thread, not the caller") {
      ZIO.attemptBlocking {
        val callerThread = Thread.currentThread()
        val cbThread     = new AtomicReference[Thread](null)
        val latch        = new CountDownLatch(1)

        // Completed off-thread so the run genuinely suspends and resumes on the
        // worker rather than collapsing synchronously.
        val a = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); c.succeed(5) }
          })
          t.setDaemon(true)
          t.start()
        }

        Async.unsafeRunAsync(a) { _ =>
          cbThread.set(Thread.currentThread())
          latch.countDown()
        }
        latch.await()
        assertTrue(cbThread.get() ne callerThread)
      }
    },
    test("cancel interrupts a parked worker and suppresses the callback") {
      ZIO.attemptBlocking {
        val fired = new AtomicBoolean(false)
        // A Completer that never completes: the worker parks indefinitely.
        val c          = new Completer[Int]
        val cancelable = Async.unsafeRunAsync(c.peek)(_ => fired.set(true))
        // Give the worker time to reach its parked state, then cancel.
        Thread.sleep(50)
        cancelable.cancel()
        Thread.sleep(50)
        assertTrue(!fired.get())
      }
    }
  )
}
