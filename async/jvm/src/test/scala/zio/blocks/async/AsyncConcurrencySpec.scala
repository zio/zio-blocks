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

import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM concurrency probes.
 */
object AsyncConcurrencySpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  // A genuinely-asynchronous leaf: a daemon thread sleeps `ms` then completes a
  // Completer off the driving thread. Unlike the synchronous `succeedAfter`
  // helpers (which complete inside `poll`), this exercises the waker/park path
  // with a real off-thread wakeup landing between polls.
  private def realAsync[A](value: A, ms: Long): Async[A] = {
    val c = new Completer[A]
    val t = new Thread(new Runnable { def run(): Unit = { Thread.sleep(ms); c.succeed(value) } })
    t.setDaemon(true); t.start()
    c.peek
  }

  private def realAsyncFail(cause: Throwable, ms: Long): Async[Nothing] = {
    val c = new Completer[Nothing]
    val t = new Thread(new Runnable { def run(): Unit = { Thread.sleep(ms); c.fail(cause) } })
    t.setDaemon(true); t.start()
    c.peek
  }

  def spec = suite("AsyncConcurrencySpec")(
    // Round-32 convergence: combinators driven over GENUINELY off-thread leaves
    // (a worker that sleeps then completes a Completer), where the wakeup lands
    // between polls via the park/onComplete path — not synchronously inside
    // `poll` as the `succeedAfter` helpers do. Covers the timing/scheduling
    // surface (real async wakeups through map/flatMap/zip/collectAll/ensuring).
    suite("real off-thread leaves through combinators")(
      test("zipWith of two off-thread leaves lands both wakeups and combines") {
        ZIO.attemptBlocking {
          val r = (realAsync(3, 30): Async[Int]).zipWith(realAsync(4, 5): Async[Int])(_ + _)
          assertTrue(r.block == 7)
        }
      },
      test("collectAll over off-thread leaves completing out of order preserves input order") {
        ZIO.attemptBlocking {
          val r = Async.collectAll(List[Async[Int]](realAsync(1, 40), realAsync(2, 5), realAsync(3, 20)))
          assertTrue(r.block == List(1, 2, 3))
        }
      },
      test("zipWith slow-success left and fast-fail right waits for left then surfaces right's failure") {
        ZIO.attemptBlocking {
          val rs  = new RuntimeException("zip-right")
          val r   = (realAsync(1, 40): Async[Int]).zipWith(realAsyncFail(rs, 5): Async[Int])(_ + _)
          val got =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(got.contains(rs))
        }
      },
      test("collectAll with an off-thread middle failure does not drive a still-pending later element") {
        ZIO.attemptBlocking {
          val mid               = new RuntimeException("collect-mid")
          val driven            = new AtomicInteger(0)
          val later: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { driven.incrementAndGet(); onComplete.run(); this }
          }
          val r   = Async.collectAll(List[Async[Int]](realAsync(1, 10), realAsyncFail(mid, 20), later))
          val got =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(got.contains(mid), driven.get() == 0)
        }
      },
      test("ensuring runs an off-thread finalizer after an off-thread primary succeeds") {
        ZIO.attemptBlocking {
          val fin = new AtomicInteger(0)
          val r   =
            (realAsync(5, 20): Async[Int]).ensuring((realAsync((), 20): Async[Unit]).map(_ => fin.incrementAndGet()))
          assertTrue(r.block == 5, fin.get() == 1)
        }
      },
      test(
        "ensuring over off-thread primary-fail and off-thread finalizer-fail keeps primary with finalizer suppressed"
      ) {
        ZIO.attemptBlocking {
          val prim = new RuntimeException("ens-primary")
          val fin  = new RuntimeException("ens-finalizer")
          val r    = (realAsyncFail(prim, 20): Async[Int]).ensuring(realAsyncFail(fin, 20))
          val got  =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          val suppressed = got.toList.flatMap(_.getSuppressed.toList)
          assertTrue(got.contains(prim), suppressed.contains(fin))
        }
      },
      test("deep flatMap chain over off-thread leaves resumes across every wakeup") {
        ZIO.attemptBlocking {
          val r = (realAsync(1, 10): Async[Int])
            .flatMap(a => (realAsync(a + 1, 10): Async[Int]))
            .flatMap(b => (realAsync(b + 1, 10): Async[Int]))
            .flatMap(c => (realAsync(c + 1, 10): Async[Int]))
          assertTrue(r.block == 4)
        }
      },
      test("zipWith of off-thread leaves driven through start observes the combined value") {
        ZIO.attemptBlocking {
          val r = (realAsync(3, 20): Async[Int]).zipWith(realAsync(4, 5): Async[Int])(_ + _).start
          assertTrue(r.block == 7)
        }
      }
    ),
    suite("concurrency")(
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
            // The settled AsyncTestSupport.outcome must be stable across independent polls.
            val o1      = c.poll(AsyncTestSupport.noopRunnable)
            val o2      = c.poll(AsyncTestSupport.noopRunnable)
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
            val c      = new Completer[Int]
            val start  = new CountDownLatch(1)
            val woke   = new AtomicInteger(0)
            val waker  = new Runnable { def run(): Unit = { woke.incrementAndGet(); () } }
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
            val v: Any = c.poll(AsyncTestSupport.noopRunnable)
            val ok     = v match {
              case _: Completer[_] => false
              case other           => other == (42: Any)
            }
            if (!ok) anomaly = Some(s"trial $i: completer not settled to 42 after race: $v")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("every distinct waker registered while pending is resumed under settle contention (waiter chain)") {
        ZIO.attemptBlocking {
          // Round-2 surface: Completer keeps a CAS-linked chain of distinct
          // waiters. Under contention each poller must either be woken by the
          // settle chain-walk or lose the CAS and observe the settled value
          // directly — never silently dropped from the chain.
          val trials  = 500
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val pollers = 8
            val start   = new CountDownLatch(1)
            val done    = new CountDownLatch(pollers)
            val resumed = new AtomicInteger(0)
            (0 until pollers).foreach { _ =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  start.await()
                  val waker  = new Runnable { def run(): Unit = { resumed.incrementAndGet(); () } }
                  val r: Any = c.poll(waker)
                  if (!r.isInstanceOf[Completer[_]]) resumed.incrementAndGet() // observed the value directly
                  done.countDown()
                }
              })
              t.setDaemon(true)
              t.start()
            }
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(7) }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            done.await()
            settler.join()
            val n = resumed.get()
            if (n != pollers) anomaly = Some(s"trial $i: $n of $pollers pollers resumed (lost wakeup or double count)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("Completer peek racing settle observes only pending-or-the-value (null settle included)") {
        ZIO.attemptBlocking {
          // `peek` is the registration-free snapshot: while a settle is in
          // flight on another thread it must observe either the still-pending
          // completer itself or the exact settled value — never a foreign
          // state (in particular the internal null-value sentinel must not
          // escape, and a registered waiter chain must read as pending).
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val useNull       = i % 2 == 1
            val expected: Any = if (useNull) null else "v"
            val c             = new Completer[String]
            if (i % 4 < 2) { c.poll(AsyncTestSupport.noopRunnable); () } // chain a waiter first on half the trials
            val start   = new CountDownLatch(1)
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(if (useNull) null else "v") }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            var settledSeen = false
            var spins       = 0
            while (!settledSeen && anomaly.isEmpty && spins < 10000000) {
              val p: Any = c.peek
              if (p.asInstanceOf[AnyRef] eq c) ()        // still pending: peek returns the completer
              else if (p == expected) settledSeen = true // settled: the exact value (or raw null)
              else anomaly = Some(s"trial $i: peek observed $p (expected pending or $expected)")
              spins += 1
            }
            settler.join()
            val fin: Any = c.peek
            if (anomaly.isEmpty && fin != expected)
              anomaly = Some(s"trial $i: post-settle peek observed $fin instead of $expected")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("Running pollers racing a null-terminal publish are all resumed and observe null") {
        ZIO.attemptBlocking {
          // Round-3 surface: the JVM runner publishes a raw-null success through
          // the `NullTerminal` sentinel. Pollers racing that publish must either
          // be woken by `wakeAll` or observe the terminal directly on `poll` —
          // never stay pending, and never read the sentinel as a value.
          val trials  = 500
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[String]
            val running = c.peek.start
            val pollers = 4
            val start   = new CountDownLatch(1)
            val done    = new CountDownLatch(pollers)
            val resumed = new AtomicInteger(0)
            (0 until pollers).foreach { _ =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  start.await()
                  val waker  = new Runnable { def run(): Unit = { resumed.incrementAndGet(); () } }
                  val r: Any = running.poll(waker)
                  if (!r.isInstanceOf[Async.Running[_]]) resumed.incrementAndGet() // observed terminal directly
                  done.countDown()
                }
              })
              t.setDaemon(true)
              t.start()
            }
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(null) }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            done.await()
            settler.join()
            // Wait for the worker to publish, then check every poller resumed and
            // a fresh poll exposes the raw null value (not the sentinel, not `this`).
            var spins = 0
            while (AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable)) && spins < 5000) {
              Thread.sleep(1); spins += 1
            }
            val terminal: Any = running.poll(AsyncTestSupport.noopRunnable)
            var waitWake      = 0
            while (resumed.get() < pollers && waitWake < 5000) { Thread.sleep(1); waitWake += 1 }
            if (terminal != null) anomaly = Some(s"trial $i: terminal was $terminal, expected raw null")
            else if (resumed.get() < pollers)
              anomaly = Some(s"trial $i: only ${resumed.get()} of $pollers pollers resumed (lost wakeup)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("concurrent fan-out via the shared Running handle runs the user function exactly once") {
        // Concurrent fan-out by re-driving the RAW `Async` from two threads is
        // UNDEFINED (the `done` memo is a plain `var`, not a synchronizer — see
        // the slowPath memo note; it cannot happen on single-threaded JS). The
        // SUPPORTED way to fan one `Async` out to concurrent consumers is to
        // share the `Running` handle from `fa.start`: it drives the underlying
        // `Async` exactly once on its worker and publishes the result through an
        // atomic, so any number of threads blocking the SAME handle observe that
        // one result. This pins that supported guarantee.
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val calls   = new AtomicInteger(0)
            val c       = new Completer[Int]
            val running = c.peek.map { x => calls.incrementAndGet(); x + 1 }.start
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val results = new Array[Any](2)
            val threads = (0 until 2).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            val n = calls.get()
            if (n != 1)
              anomaly = Some(s"trial $i: user function ran $n times via shared Running handle (must be 1)")
            else if (!results.forall(_ == 2))
              anomaly = Some(s"trial $i: shared handle delivered ${results.toList} (must be [2, 2])")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("concurrent fan-out of collectAll via the shared Running handle yields one correct list") {
        // As above, for `collectAll`: concurrent raw re-drive is undefined (it
        // may corrupt the shared drain buffer), so fan out by sharing one
        // `Running` handle, driven once on its worker. Concurrent consumers all
        // observe the same published list.
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val running =
              Async.collectAll(List[Async[Int]](Async.succeed(0), c.peek, Async.succeed(2))).start
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val results = new Array[Any](2)
            val threads = (0 until 2).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            if (!results.forall(_ == List(0, 1, 2)))
              anomaly =
                Some(s"trial $i: shared collectAll handle delivered ${results.toList} (must be two List(0, 1, 2))")
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
            val c         = new Completer[Int]
            val calls     = new AtomicInteger(0)
            val running   = AsyncTestSupport.startTap(c.peek)(_ => calls.incrementAndGet())
            val start     = new CountDownLatch(1)
            val completer = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(1) }
            })
            val canceller = new Thread(new Runnable {
              def run(): Unit = { start.await(); running.cancel() }
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
      },
      test("many threads blocking ONE shared Running handle all wake and agree when completion lands after they park") {
        // Directly stresses the SuspendedRunning lost-wakeup window: the
        // off-thread leaf completes AFTER every blocker has already entered
        // `.block` (and is parking inside `registerOnComplete`/park), so the
        // terminal publish + lock-guarded `wakeAll` must drain EVERY waiter
        // added before it ran — no blocker may stay parked, and all must agree
        // on the single published value exactly once. (Existing fan-out probes
        // complete the leaf BEFORE blocking; this one inverts the order so the
        // waiter chain is non-empty at publish time.)
        ZIO.attemptBlocking {
          val trials   = 400
          val blockers = 8
          var anomaly  = Option.empty[String]
          var i        = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val running = c.peek.map(_ + 1).start
            val parked  = new CountDownLatch(blockers)
            val release = new CountDownLatch(1)
            val results = new Array[Any](blockers)
            val threads = (0 until blockers).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  parked.countDown() // announce intent to block
                  release.await()    // all blockers released together
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true); t.start(); t
            }
            parked.await()
            release.countDown()
            // Complete off-thread only AFTER releasing the blockers, so the
            // publish races them parking — the waiter chain is populated when
            // wakeAll runs (or a late blocker observes the terminal directly).
            val settler = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(2); c.succeed(41) }
            })
            settler.setDaemon(true); settler.start()
            threads.foreach(_.join(5000))
            settler.join(5000)
            val stuck = threads.zipWithIndex.collect { case (t, idx) if t.isAlive => idx }
            if (stuck.nonEmpty) anomaly = Some(s"trial $i: blockers $stuck never woke (lost wakeup)")
            else if (!results.forall(_ == 42))
              anomaly = Some(s"trial $i: blockers disagreed/wrong: ${results.toList} (all must be 42)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      }
    )
  )
}
