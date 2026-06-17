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

  def spec = suite("AsyncConcurrencySpec")(
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
      test("two concurrent drivers polling one settled map-continuation run the user function exactly once") {
        // Fan-out is a documented consumer scenario: a single `Async` may be
        // driven by more than one consumer (two `fa.start`s, or `fa.start` plus
        // `fa.block`), each polling the SAME combinator pollable. The settling
        // poll of every driver must be a pure observation — the user function
        // runs exactly once. The single-threaded fan-out suite already asserts
        // this; here the two drivers poll CONCURRENTLY. The `done` memo guards
        // the function with a plain `var` read-then-write, so if both drivers
        // enter `poll` before either has written `done`, both re-run the
        // function. We settle the leaf first, then release both pollers through
        // a barrier so they poll the already-settled pollable simultaneously.
        ZIO.attemptBlocking {
          val trials  = 5000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val calls = new AtomicInteger(0)
            val c     = new Completer[Int]
            val fa    = c.peek.map { x => calls.incrementAndGet(); x + 1 }
            val pa    = fa.asInstanceOf[Pollable[Int]]
            // Documented fan-out interleaving: each of two distinct drivers
            // first polls while PENDING (registering its own waker), so neither
            // has set `done`. The leaf then settles, and both drivers are woken
            // and poll once more to OBSERVE the value — concurrently, gated by a
            // barrier. The settling polls must collectively run `f` once.
            pa.poll(AsyncTestSupport.noopRunnable) // driver A registers (pending)
            pa.poll(() => ())                      // driver B registers (distinct waker)
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val threads = (0 until 2).map { _ =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  pa.poll(AsyncTestSupport.noopRunnable)
                  ()
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            val n = calls.get()
            if (n != 1) anomaly = Some(s"trial $i: map function ran $n times under concurrent fan-out (must be 1)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("two concurrent drivers over one collectAll batch agree on the list (no corruption / crash)") {
        // Same root as the map fan-out race, but the blast radius is worse:
        // CollectAllPollable mutates a shared ListBuffer and nulls `cur` when
        // the batch settles. Two drivers racing the settling poll can both enter
        // the drain, double-append into the buffer or NPE on the nulled `cur`.
        // A correct (idempotent) implementation hands both drivers the same
        // List(0,1,2).
        ZIO.attemptBlocking {
          val trials  = 5000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c   = new Completer[Int]
            val all = Async.collectAll(List[Async[Int]](Async.succeed(0), c.peek, Async.succeed(2)))
            val pa  = all.asInstanceOf[Pollable[List[Int]]]
            pa.poll(AsyncTestSupport.noopRunnable) // driver A registers (pending on the middle element)
            pa.poll(() => ())                      // driver B registers
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val results = new Array[Any](2)
            val threads = (0 until 2).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  results(id) =
                    try pa.poll(AsyncTestSupport.noopRunnable)
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            val outcomes = results.map {
              case t: Throwable          => Left(t.getClass.getSimpleName)
              case a if AsyncTestSupport.isPending(a.asInstanceOf[Async[List[Int]]]) =>
                Left("pending")
              case a                     => Right(a.asInstanceOf[Async[List[Int]]].block)
            }.toList
            val bad = outcomes.exists {
              case Right(List(0, 1, 2)) => false
              case _                    => true
            }
            if (bad) anomaly = Some(s"trial $i: concurrent collectAll drivers disagreed/crashed: $outcomes")
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
      }
    )
  )
}
