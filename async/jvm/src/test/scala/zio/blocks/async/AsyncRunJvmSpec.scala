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
    test("cancel interrupts a parked worker, killing the thread and suppressing the callback") {
      ZIO.attemptBlocking {
        val fired = new AtomicBoolean(false)
        // A Completer that never completes: the worker parks indefinitely.
        val c          = new Completer[Int]
        val cancelable = Async.unsafeRunAsync(c.peek)(_ => fired.set(true))

        def runnerThread(): Option[Thread] =
          Thread.getAllStackTraces
            .keySet()
            .toArray
            .collect { case t: Thread => t }
            .find(t => t.getName == "zio-blocks-async-runner" && t.isAlive)

        // Wait for the worker to start and park.
        var waited = 0
        while (runnerThread().isEmpty && waited < 100) { Thread.sleep(10); waited += 1 }
        val parkedBefore = runnerThread().isDefined

        // cancel() must interrupt the parked worker; its `park` throws
        // InterruptedException, drive() unwinds, and the thread dies.
        cancelable.cancel()

        var stillAlive = true
        var checks     = 0
        while (stillAlive && checks < 100) {
          stillAlive = runnerThread().isDefined
          if (stillAlive) Thread.sleep(10)
          checks += 1
        }

        // A second cancel must be a silent no-op (CAS already lost): idempotent
        // on a genuinely suspended Run, not just on the synchronous no-op path.
        cancelable.cancel()

        assertTrue(parkedBefore, !stillAlive, !fired.get())
      }
    }
  )
}
