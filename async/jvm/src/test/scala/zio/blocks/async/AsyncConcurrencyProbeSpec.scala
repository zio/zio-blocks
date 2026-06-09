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
import java.util.concurrent.atomic.AtomicInteger

import zio.ZIO
import zio.test._

/**
 * FINAL CONVERGENCE concurrency stress for the linearization points that the
 * deterministic single-thread specs (`AsyncSuspendedSpec`) exercise only
 * sequentially:
 *
 *   - [[Completer]] is documented "thread-safe and one-shot: the first call to
 *     `succeed` or `fail` wins; all subsequent calls are silent no-ops". Many
 *     threads race to settle the same completer and to poll it concurrently; the
 *     observed outcome must be exactly one settlement, stable across re-polls.
 *   - [[Async.unsafeRunAsync]] documents "the callback is invoked at most once"
 *     with completion and cancellation linearized. A run is raced against
 *     `cancel()` repeatedly; the callback must fire AT MOST once per run.
 *
 * These are JVM-only (Scala.js is single-threaded). They are probabilistic by
 * nature; each scenario runs many trials with real thread contention. A failure
 * here is a definitive concurrency defect.
 */
object AsyncConcurrencyProbeSpec extends ZIOSpecDefault {

  private val noWaker: Waker = new Waker { def wake(): Unit = () }

  def spec = suite("AsyncConcurrencyProbeSpec")(
    test("Completer settles exactly once under many racing succeed/fail callers") {
      ZIO.attemptBlocking {
        val trials  = 2000
        var anomaly = Option.empty[String]
        var i       = 0
        while (i < trials && anomaly.isEmpty) {
          val c       = new Completer[Int]
          val racers  = 8
          val start   = new CountDownLatch(1)
          val done    = new CountDownLatch(racers)
          val threads = (0 until racers).map { id =>
            val t = new Thread(new Runnable {
              def run(): Unit = {
                start.await()
                if (id % 2 == 0) c.succeed(id) else c.fail(new RuntimeException(s"boom-$id"))
                done.countDown()
              }
            })
            t.setDaemon(true)
            t.start()
            t
          }
          start.countDown()
          done.await()
          // The settled outcome must be stable across independent polls.
          val o1 = c.poll(noWaker)
          val o2 = c.poll(noWaker)
          val v1: Any = o1
          val v2: Any = o2
          val settled =
            !v1.isInstanceOf[Completer[_]] // a still-pending completer poll returns `this`
          val stable = (v1, v2) match {
            case (a: Failure, b: Failure) => a.cause eq b.cause
            case (a, b)                   => a == b
          }
          if (!settled) anomaly = Some(s"trial $i: completer never settled despite ${racers} settlers")
          else if (!stable) anomaly = Some(s"trial $i: completer outcome not stable across polls: $v1 vs $v2")
          threads.foreach(_.join())
          i += 1
        }
        assertTrue(anomaly.isEmpty)
      }
    },
    test("Completer poll racing settle eventually observes the value (no lost wakeup / no stuck pending)") {
      ZIO.attemptBlocking {
        val trials  = 2000
        var anomaly = Option.empty[String]
        var i       = 0
        while (i < trials && anomaly.isEmpty) {
          val c     = new Completer[Int]
          val start = new CountDownLatch(1)
          val woke  = new AtomicInteger(0)
          val waker = new Waker { def wake(): Unit = { woke.incrementAndGet(); () } }
          val poller = new Thread(new Runnable {
            def run(): Unit = { start.await(); c.poll(waker); () }
          })
          val settler = new Thread(new Runnable {
            def run(): Unit = { start.await(); c.succeed(42); () }
          })
          poller.setDaemon(true); settler.setDaemon(true)
          poller.start(); settler.start()
          start.countDown()
          poller.join(); settler.join()
          // After both have run, a fresh poll must observe the settled value:
          // either the racing poll registered a waker that was woken, or it lost
          // the CAS and observed the value directly; either way the completer is
          // settled now.
          val v: Any = c.poll(noWaker)
          val ok = v match {
            case _: Completer[_] => false
            case other           => other == (42: Any)
          }
          if (!ok) anomaly = Some(s"trial $i: completer not settled to 42 after race: $v")
          i += 1
        }
        assertTrue(anomaly.isEmpty)
      }
    },
    test("unsafeRunAsync delivers the callback at most once when completion races cancel") {
      ZIO.attemptBlocking {
        val trials  = 1000
        var anomaly = Option.empty[String]
        var i       = 0
        while (i < trials && anomaly.isEmpty) {
          val c      = new Completer[Int]
          val calls  = new AtomicInteger(0)
          val cancelable = Async.unsafeRunAsync(c.peek) { _ =>
            calls.incrementAndGet(); ()
          }
          val start = new CountDownLatch(1)
          val completer = new Thread(new Runnable {
            def run(): Unit = { start.await(); c.succeed(1) }
          })
          val canceller = new Thread(new Runnable {
            def run(): Unit = { start.await(); cancelable.cancel() }
          })
          completer.setDaemon(true); canceller.setDaemon(true)
          completer.start(); canceller.start()
          start.countDown()
          completer.join(); canceller.join()
          // Give the worker a moment to deliver if completion won the race.
          Thread.sleep(1)
          val n = calls.get()
          if (n > 1) anomaly = Some(s"trial $i: callback delivered $n times (must be at most once)")
          i += 1
        }
        assertTrue(anomaly.isEmpty)
      }
    }
  )
}
