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

import zio.test._

import zio.ZIO
import java.util.concurrent.{CompletableFuture, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

/**
 * JVM blocking await behavior.
 */
object AsyncBlockingSpec extends ZIOSpecDefault {

  private def sleep(ms: Long): Unit = Thread.sleep(ms)

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  /** A genuinely-pending Async that completes with `x` ~5ms later. */
  private def pending(x: Int): Async[Int] = {
    val cf = new CompletableFuture[Int]()
    scheduler.schedule(
      new Runnable { def run(): Unit = { cf.complete(x); () } },
      5,
      TimeUnit.MILLISECONDS
    )
    AsyncInterop.fromCompletionStage(cf)
  }

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncBlockingSpec")(
    suite("block")(
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
      },
      test("two threads blocking the same promise-backed Async both observe the settled value") {
        ZIO.attemptBlocking {
          // Completer fan-out: each `.block` is an independent driver with its
          // own parker; both must be registered on the waiter chain and woken
          // by the single settle — neither may stay parked or see a stale state.
          val c       = new Completer[Int]
          val fa      = c.peek
          val results = new java.util.concurrent.atomic.AtomicIntegerArray(2)
          val done    = new CountDownLatch(2)
          (0 until 2).foreach { i =>
            val t = new Thread(new Runnable {
              def run(): Unit = { results.set(i, fa.block); done.countDown() }
            })
            t.setDaemon(true)
            t.start()
          }
          sleep(20) // give both drivers a chance to park before the settle
          c.succeed(42)
          val completed = done.await(5, TimeUnit.SECONDS)
          assertTrue(completed, results.get(0) == 42, results.get(1) == 42)
        }
      },
      suite("plain .await positions")(
        test("`.await` of a succeeded Async returns the value") {
          val r = Async.async {
            val x = Async.succeed(7).await
            x + 1
          }.block
          assertTrue(r == 8)
        },
        test("`.await` of a failed Async rethrows the cause, captured as Async.fail") {
          val a = Async.async {
            Async.fail(AsyncTestSupport.boom).await
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        },
        test("a body that throws becomes Async.fail") {
          val a      = Async.async[Int]((throw AsyncTestSupport.boom): Int)
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        }
      ),
      suite("sequential .awaits in source order")(
        test("two awaits compose in order") {
          val r = Async.async {
            val a = Async.succeed(2).await
            val b = Async.succeed(3).await
            a * b
          }.block
          assertTrue(r == 6)
        },
        test("a failing await aborts the rest of the body") {
          var laterRan = false
          val a        = Async.async[Int] {
            val _ = Async.fail(AsyncTestSupport.boom).await
            laterRan = true
            99
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), !laterRan)
        }
      ),
      suite("if / else with .await in branches")(
        test("then-branch await") {
          val cond = true
          val r    = Async.async {
            if (cond) Async.succeed(1).await
            else 0
          }.block
          assertTrue(r == 1)
        },
        test("else-branch await") {
          val cond = false
          val r    = Async.async {
            if (cond) 0
            else Async.succeed(2).await
          }.block
          assertTrue(r == 2)
        }
      ),
      suite("while loop with .await in body")(
        test("counts up via repeated awaits") {
          var sum = 0
          val r   = Async.async {
            var i = 0
            while (i < 5) {
              sum += Async.succeed(i + 1).await
              i += 1
            }
            sum
          }.block
          assertTrue(r == 15, sum == 15)
        },
        test("a long while loop of ready awaits completes (stack safety probe)") {
          val n = 100000
          val r = Async.async {
            var i = 0
            while (i < n) {
              val _ = Async.succeed(i).await
              i += 1
            }
            i
          }.block
          assertTrue(r == n)
        },
        test("a long while loop of genuinely-suspending awaits completes (suspension stack safety probe)") {
          val n = 50000
          val r = Async.async {
            var i = 0
            while (i < n) {
              val _ = AsyncTestSupport.fromPollable(AsyncTestSupport.succeedAfter(i, 1)).await
              i += 1
            }
            i
          }.block
          assertTrue(r == n)
        },
        test("await in the loop condition only") {
          val r = Async.async {
            var i = 0
            while (Async.succeed(i < 3).await) i += 1
            i
          }.block
          assertTrue(r == 3)
        },
        test("await in the condition and body across genuine suspensions") {
          var sum = 0
          val r   = Async.async {
            var i = 0
            while (pending(i).await < 4) {
              sum += pending(i * 10).await
              i += 1
            }
            sum
          }.block
          assertTrue(r == 60, sum == 60)
        },
        test("nested while loops with awaits in both bodies") {
          var total = 0
          val r     = Async.async {
            var i = 0
            while (i < 3) {
              var j = 0
              while (j < 2) {
                total += Async.succeed(i * 10 + j).await
                j += 1
              }
              i += 1
            }
            total
          }.block
          assertTrue(r == 63, total == 63)
        },
        test("a var mutated by awaited values in the body is visible to the condition") {
          val r = Async.async {
            var acc = 0
            while (acc < 10) acc += Async.succeed(3).await
            acc
          }.block
          assertTrue(r == 12)
        },
        test("do-while idiom (body-in-condition block) with await") {
          var i     = 0
          var loops = 0
          val r     = Async.async {
            while ({ i += Async.succeed(1).await; i < 3 }) loops += 1
            i
          }.block
          assertTrue(r == 3, loops == 2)
        },
        test("do-while idiom with a pure empty body compiles on every Scala version") {
          // The natural spelling of the idiom: all the work happens in the
          // condition block and the body is the pure `()`. The while-rewrite
          // must not splice that body into statement position, where Scala
          // 3.3's fatal "pure expression does nothing" warning would abort
          // compilation of the whole module.
          var i = 0
          val r = Async.async {
            while ({ i += Async.succeed(1).await; i < 3 }) ()
            i
          }.block
          assertTrue(r == 3)
        },
        test("a failing await in the loop condition propagates without running the body") {
          var bodyRan = false
          val a       = Async.async {
            while (Async.fail(AsyncTestSupport.boom).await: Boolean) bodyRan = true
            0
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), !bodyRan)
        },
        test("a failing await in the loop body stops iteration and runs the enclosing finally") {
          var fin        = false
          var iterations = 0
          val a          = Async.async {
            try {
              var i = 0
              while (i < 5) {
                iterations += 1
                if (i == 2) (Async.fail(AsyncTestSupport.boom).await: Int) else Async.succeed(i).await
                i += 1
              }
              i
            } finally fin = true
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), fin, iterations == 3)
        }
      ),
      suite("try / catch / finally with .await")(
        test("catches a Throwable from a failed await") {
          val r = Async.async {
            try Async.fail(AsyncTestSupport.boom).await
            catch { case _: Throwable => 42 }
          }.block
          assertTrue(r == 42)
        },
        test("finally block runs even when an await fails") {
          var ran = false
          val r   = Async.async {
            try Async.fail(AsyncTestSupport.boom).await
            catch { case _: Throwable => 0 }
            finally ran = true
          }.block
          assertTrue(r == 0, ran)
        },
        test("an await inside a catch handler arm recovers with the awaited value") {
          val r = Async.async {
            try Async.fail(AsyncTestSupport.boom).await
            catch { case _: Throwable => Async.succeed(7).await }
          }.block
          assertTrue(r == 7)
        },
        test("an await inside a finally block runs for its effect") {
          var fin = 0
          val r   = Async.async {
            try Async.succeed(5).await
            finally fin = Async.succeed(9).await
          }.block
          assertTrue(r == 5, fin == 9)
        },
        test("a throwing finalizer replaces the in-flight awaited failure (plain try/finally semantics)") {
          // Scala semantics: a throw from `finally` replaces the in-flight
          // exception.
          val finBoom            = new RuntimeException("fin")
          def finThrow(): Unit   = throw finBoom
          def failed: Async[Int] = Async.fail(AsyncTestSupport.boom)
          val a                  = Async.async[Int] {
            try failed.await
            finally finThrow()
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a throwing finalizer replaces an in-flight null-cause awaited failure with the finalizer's throw") {
          // Same replacement law when the in-flight failure carries the logical
          // null cause: the block must fail with the finalizer's throw — never
          // an internal NullPointerException from combining the two.
          val finBoom                = new RuntimeException("fin")
          def finThrow(): Unit       = throw finBoom
          def failedNull: Async[Int] = Async.fail(null)
          val a                      = Async.async[Int] {
            try failedNull.await
            finally finThrow()
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a finalizer that awaits and then throws still replaces an in-flight null-cause failure") {
          // The awaiting finalizer routes through the async-finalizer
          // machinery; the replacement law (and null-cause safety) must hold
          // there too.
          val finBoom                = new RuntimeException("fin")
          def finThrow(): Unit       = throw finBoom
          def failedNull: Async[Int] = Async.fail(null)
          val a                      = Async.async[Int] {
            try failedNull.await
            finally {
              val _ = Async.succeed(1).await
              finThrow()
            }
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a throwing finalizer attaches the in-flight awaited failure as suppressed") {
          // Plain-Scala replacement semantics PLUS error-graph preservation:
          // the finalizer's throw wins and the original failure stays reachable
          // via getSuppressed (legal here: non-null, distinct). The Scala 3
          // cells honor this via AsyncCpsMonad.withAction/withAsyncAction; the
          // Scala 2 macro's try/finally materializer restores the finalizer's
          // failure without attaching the in-flight one, losing it
          // irrecoverably.
          val primary            = new RuntimeException("primary")
          val finBoom            = new RuntimeException("fin")
          def finThrow(): Unit   = throw finBoom
          def failed: Async[Int] = Async.fail(primary)
          val a                  = Async.async[Int] {
            try failed.await
            finally finThrow()
          }
          assertTrue(
            AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom),
            finBoom.getSuppressed.toList.contains(primary)
          )
        },
        test("a throwing finalizer after a successful awaited body fails the block with the finalizer's throw") {
          // The replacement law also covers the success side: the body's value
          // is discarded, the finalizer's throw is the failure — and nothing
          // gets attached as suppressed (there is no in-flight failure).
          val finBoom          = new RuntimeException("fin")
          def finThrow(): Unit = throw finBoom
          val a                = Async.async[Int] {
            try Async.succeed(5).await
            finally finThrow()
          }
          assertTrue(
            AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom),
            finBoom.getSuppressed.isEmpty
          )
        },
        test("a finalizer that rethrows the in-flight cause itself propagates it without a self-suppression crash") {
          // `Throwable.addSuppressed(self)` throws IllegalArgumentException;
          // when the finalizer's throw IS the in-flight cause the combiner must
          // skip suppression and surface the shared instance untouched.
          val shared             = new RuntimeException("shared")
          def finThrow(): Unit   = throw shared
          def failed: Async[Int] = Async.fail(shared)
          val a                  = Async.async[Int] {
            try failed.await
            finally finThrow()
          }
          assertTrue(
            AsyncTestSupport.blockAsLeftCause(a) == Some(shared),
            shared.getSuppressed.isEmpty
          )
        },
        test("try/finally over a ready null-cause failed await preserves the logical null and runs the finalizer") {
          var fin                    = 0
          def failedNull: Async[Int] = Async.fail(null)
          val a                      = Async.async[Int] {
            try failedNull.await
            finally fin += 1
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(null), fin == 1)
        },
        test("a finalizer that awaits (and succeeds) preserves an in-flight null-cause failure") {
          // A succeeding async finalizer must not disturb the in-flight
          // failure, even when its cause is the logical null.
          var fin                    = 0
          def failedNull: Async[Int] = Async.fail(null)
          val a                      = Async.async[Int] {
            try failedNull.await
            finally fin = Async.succeed(9).await
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(null), fin == 9)
        },
        test("a finalizer that fails via an awaited Async replaces the in-flight failure") {
          // The finalizer's failure arrives through the async channel (an
          // awaited Async.fail), not a raw throw; the replacement law is the
          // same.
          val finBoom              = new RuntimeException("fin")
          def failFin: Async[Unit] = Async.fail(finBoom)
          def failed: Async[Int]   = Async.fail(AsyncTestSupport.boom)
          val a                    = Async.async[Int] {
            try failed.await
            finally failFin.await
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a finalizer that fails via an awaited Async replaces an in-flight null-cause failure") {
          val finBoom                = new RuntimeException("fin")
          def failFin: Async[Unit]   = Async.fail(finBoom)
          def failedNull: Async[Int] = Async.fail(null)
          val a                      = Async.async[Int] {
            try failedNull.await
            finally failFin.await
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a finalizer that fails with a null cause replaces the in-flight failure with the logical null") {
          // The finalizer side can carry the logical null too: the block fails
          // with null (decoded), never an internal NullPointerException from
          // trying to combine the two failures.
          def failFinNull: Async[Unit] = Async.fail(null)
          def failed: Async[Int]       = Async.fail(AsyncTestSupport.boom)
          val a                        = Async.async[Int] {
            try failed.await
            finally failFinNull.await
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(null))
        },
        test("a throwing finalizer replaces a genuinely-pending null-cause awaited failure") {
          // The null-cause primary arrives through the SUSPENDED channel (a
          // Completer failed off-thread), so the failure is in flight when the
          // throwing finalizer runs.
          val finBoom          = new RuntimeException("fin")
          def finThrow(): Unit = throw finBoom
          val cRef             = new AtomicReference[Completer[Int]]()
          val pendingNull      = Async.promiseInternal[Int](c => cRef.set(c))
          val a                = Async.async[Int] {
            try pendingNull.await
            finally finThrow()
          }
          val worker = new Thread(() => { Thread.sleep(25); cRef.get().fail(null) })
          worker.setDaemon(true)
          worker.start()
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(finBoom))
        },
        test("a Nothing-typed awaited body under try/finally propagates the cause and runs the finalizer once") {
          // `Async.fail(t).await` types as Nothing; with no widening catch arm
          // the try expression itself is Nothing-typed. The rewrite must accept
          // it on every Scala version and keep plain try/finally semantics.
          var fin                           = 0
          def failedNothing: Async[Nothing] = Async.fail(AsyncTestSupport.boom)
          val a: Async[Int]                 = Async.async[Int] {
            try failedNothing.await
            finally fin += 1
          }
          assertTrue(AsyncTestSupport.blockAsLeftCause(a) == Some(AsyncTestSupport.boom), fin == 1)
        },
        test("a Nothing-typed awaited body under try/catch recovers") {
          def failedNothing: Async[Nothing] = Async.fail(AsyncTestSupport.boom)
          val r                             = Async.async {
            try failedNothing.await
            catch { case t: Throwable if t eq AsyncTestSupport.boom => 42 }
          }.block
          assertTrue(r == 42)
        },
        test("a Nothing-typed awaited body under try/catch/finally recovers and runs the finalizer once") {
          var fin                           = 0
          def failedNothing: Async[Nothing] = Async.fail(AsyncTestSupport.boom)
          val r                             = Async.async {
            try failedNothing.await
            catch { case t: Throwable if t eq AsyncTestSupport.boom => 42 }
            finally fin += 1
          }.block
          assertTrue(r == 42, fin == 1)
        },
        test("nested try/finally over a Nothing-typed awaited body runs both finalizers once, inner first") {
          var trace                         = List.empty[String]
          def failedNothing: Async[Nothing] = Async.fail(AsyncTestSupport.boom)
          val a: Async[Int]                 = Async.async[Int] {
            try {
              try failedNothing.await
              finally trace ::= "inner"
            } finally trace ::= "outer"
          }
          assertTrue(
            AsyncTestSupport.blockAsLeftCause(a) == Some(AsyncTestSupport.boom),
            trace == List("outer", "inner")
          )
        }
      ),
      suite("match with .await")(
        test("match on a value, await inside an arm") {
          val k = 1
          val r = Async.async {
            k match {
              case 0 => 100
              case 1 => Async.succeed(50).await
              case _ => -1
            }
          }.block
          assertTrue(r == 50)
        },
        test("match on the result of an awaited Async") {
          val r = Async.async {
            Async.succeed(2).await match {
              case 1 => "one"
              case 2 => "two"
              case _ => "other"
            }
          }.block
          assertTrue(r == "two")
        }
      ),
      suite("throw inside async block")(
        test("a raw throw becomes Async.fail") {
          val a = Async.async[Int] {
            if (Async.succeed(true).await) throw AsyncTestSupport.boom else 0
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        }
      ),
      // These stress the element-type tracking that ascribes each generated
      // `flatMap` lambda parameter (Scala 2) — interleaving DISTINCT element
      // types so any mis-ordering would surface as a `ClassCastException` or a
      // compile error rather than a silently-correct result.
      suite("interleaved distinct-typed awaits")(
        test("nested await (inner before outer) with distinct types") {
          val r = Async.async {
            val len: Int = Async.succeed(Async.succeed("hello").await.length).await
            len * 2
          }.block
          assertTrue(r == 10)
        },
        test("awaits as function arguments evaluate left-to-right with distinct types") {
          def combine(s: String, n: Int): String = s * n
          val r                                  = Async.async {
            combine(Async.succeed("ab").await, Async.succeed(3).await)
          }.block
          assertTrue(r == "ababab")
        },
        test("await in if-condition and both branches, distinct branch types collapse") {
          val r = Async.async {
            val flag: Boolean = Async.succeed(true).await
            if (flag) Async.succeed("yes").await
            else Async.succeed(0).await.toString
          }.block
          assertTrue(r == "yes")
        },
        test("sequential awaits of different types feeding a later expression") {
          val r = Async.async {
            val s: String = Async.succeed("xyz").await
            val n: Int    = Async.succeed(s.length).await
            val d: Double = Async.succeed(n.toDouble + 0.5).await
            s"$s/$n/$d"
          }.block
          assertTrue(r == "xyz/3/3.5")
        },
        test("await inside a match scrutinee and arm with distinct types") {
          val r = Async.async {
            Async.succeed(2).await match {
              case 1 => Async.succeed("one").await
              case 2 => Async.succeed("two").await
              case _ => Async.succeed("many").await
            }
          }.block
          assertTrue(r == "two")
        }
      ),
      // Semantics that a naive ANF transform would silently break (oracle review).
      suite("short-circuit and binding semantics")(
        test("`&&` does not evaluate its right operand when the awaited left is false") {
          var rhsRan = false
          val r      = Async.async {
            Async.succeed(false).await && { rhsRan = true; true }
          }.block
          assertTrue(!r, !rhsRan)
        },
        test("`||` does not evaluate its right operand when the awaited left is true") {
          var rhsRan = false
          val r      = Async.async {
            Async.succeed(true).await || { rhsRan = true; false }
          }.block
          assertTrue(r, !rhsRan)
        },
        test("`&&` with an awaited right operand short-circuits without awaiting it") {
          var rhsRan = false
          val r      = Async.async {
            Async.succeed(false).await && Async.succeed { rhsRan = true; true }.await
          }.block
          assertTrue(!r, !rhsRan)
        },
        test("tuple destructuring of an awaited value binds both components") {
          val r = Async.async {
            val (a, b) = Async.succeed((3, 4)).await
            a + b
          }.block
          assertTrue(r == 7)
        }
      ),
      // `.await` inside a `List.map` closure has EAGER semantics on every backend
      // (dotty-cps-async + native `js.await` on Scala 3, `internal.AsyncMacros` on
      // Scala 2): strict `List.map` applies the closure to every element first,
      // producing `List[Async[B]]`, and the awaits are then sequenced
      // left-to-right via `Async.collectAll` (fail-fast on the first failure).
      // This matches how `Array.map(async ...)` composes in real JavaScript.
      suite("List.map with .await in the closure")(
        test("maps over ready awaits") {
          val r = Async.async {
            List(1, 2, 3).map(i => Async.succeed(i + 1).await).sum
          }.block
          assertTrue(r == 9)
        },
        test("maps over genuinely-pending awaits") {
          val r = Async.async {
            List(10, 20, 30).map(i => pending(i).await).sum
          }.block
          assertTrue(r == 60)
        },
        test("preserves element order and result type") {
          val r = Async.async {
            List(1, 2, 3).map(i => Async.succeed(i * 10).await)
          }.block
          assertTrue(r == List(10, 20, 30))
        },
        test("the closure applies eagerly to all elements, then awaits sequence fail-fast") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).map { i =>
              seen = i :: seen
              if (i == 2) Async.fail(AsyncTestSupport.boom).await else Async.succeed(i).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(3, 2, 1))
        },
        test("a Failure stored as a success value travels through the closure as data") {
          // `Async.succeed` carries an effect value as DATA (one wrap layer): a
          // Failure stored that way must be delivered by the closure's await as
          // the bare failed Async, by identity — never short-circuiting the
          // surrounding HOF sequencing as if the element itself had failed.
          val failed: Async[Int] = Async.fail(boom)
          val r                  = Async.async {
            List(1, 2).map(_ => Async.succeed(failed).await)
          }.block
          assertTrue(
            r.length == 2,
            r.forall(x => x.asInstanceOf[AnyRef] eq failed.asInstanceOf[AnyRef])
          )
        }
      ),
      // `.await` inside a `List.foreach` closure has LAZY/sequential semantics on
      // every backend (DCA's `IterableAsyncShift.foreach` + native `js.await` loop
      // suspension): the closure for element n+1 runs only after element n's await
      // completes, and a failed await short-circuits the rest. Result is `Unit`.
      suite("List.foreach with .await in the closure")(
        test("runs the closure for every ready element in order") {
          var acc = 0
          val r   = Async.async {
            List(1, 2, 3).foreach(i => acc += Async.succeed(i).await)
            acc
          }.block
          assertTrue(r == 6, acc == 6)
        },
        test("runs over genuinely-pending awaits in order") {
          var acc = 0
          val r   = Async.async {
            List(10, 20, 30).foreach(i => acc += pending(i).await)
            acc
          }.block
          assertTrue(r == 60, acc == 60)
        },
        test("a failing await short-circuits the remaining elements (lazy)") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).foreach { i =>
              seen = i :: seen
              if (i == 2) Async.fail(AsyncTestSupport.boom).await else { val _ = Async.succeed(i).await; () }
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        }
      ),
      // `.await` inside a `List.flatMap` closure has LAZY/sequential semantics (the
      // closure for element n+1 runs only after element n's await completes, with
      // fail-fast short-circuiting), but accumulates each closure's `IterableOnce`
      // into the result `List`. Matches every Scala 3 backend.
      suite("List.flatMap with .await in the closure")(
        test("accumulates ready awaits in order") {
          val r = Async.async {
            List(1, 2, 3).flatMap { i =>
              val x = Async.succeed(i).await
              List(x, x * 10)
            }
          }.block
          assertTrue(r == List(1, 10, 2, 20, 3, 30))
        },
        test("accumulates genuinely-pending awaits in order") {
          val r = Async.async {
            List(1, 2, 3).flatMap(i => List(pending(i * 10).await))
          }.block
          assertTrue(r == List(10, 20, 30))
        },
        test("a failing await short-circuits the remaining elements (lazy)") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).flatMap { i =>
              seen = i :: seen
              if (i == 2) { Async.fail(AsyncTestSupport.boom).await; List(i) }
              else List(Async.succeed(i).await)
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        }
      ),
      // Single-generator for-comprehensions over a `List` desugar to `.map` (with
      // `yield`) or `.foreach` (without), and multi-generator ones to nested
      // `.flatMap`/`.map`, before any backend sees them — so they inherit the same
      // await semantics for free on every cell.
      suite("for-comprehension over a List with .await")(
        test("`for ... yield` desugars to eager List.map") {
          val r = Async.async {
            (for (i <- List(1, 2, 3)) yield Async.succeed(i * 10).await).sum
          }.block
          assertTrue(r == 60)
        },
        test("`for { ... }` (no yield) desugars to lazy List.foreach") {
          var acc = 0
          val r   = Async.async {
            for (i <- List(1, 2, 3)) acc += Async.succeed(i).await
            acc
          }.block
          assertTrue(r == 6, acc == 6)
        },
        test("multi-generator `for ... yield` desugars to nested flatMap/map") {
          val r = Async.async {
            for {
              i <- List(1, 2)
              j <- List(10, 20)
            } yield Async.succeed(i + j).await
          }.block
          assertTrue(r == List(11, 21, 12, 22))
        },
        test("guard (`if`) desugars to withFilter and is honored before the await") {
          val r = Async.async {
            for {
              i <- List(1, 2, 3, 4)
              if i % 2 == 0
            } yield Async.succeed(i * 10).await
          }.block
          assertTrue(r == List(20, 40))
        },
        test("guard plus multi-generator") {
          val r = Async.async {
            for {
              i <- List(1, 2, 3)
              if i != 2
              j <- List(10, 20)
            } yield Async.succeed(i + j).await
          }.block
          assertTrue(r == List(11, 21, 13, 23))
        }
        // NOTE: *multiple* guards (`if ... if ...`, i.e. chained `withFilter`) are
        // supported by the Scala 2 macro but NOT by dotty-cps-async on Scala 3
        // (it has no `AsyncShift[WithFilter]` for a nested `withFilter`), so that
        // case is a Scala-2-only superset covered in `AsyncAwaitValAscriptionSpec`.
      ),
      // `.await` inside `Option` HOF closures. An `Option` holds at most one
      // element, so the eager/lazy distinction (which separates `List.map` from
      // `List.foreach`/`flatMap`) collapses: every HOF reduces to a single
      // `Some`/`None` branch. `None` short-circuits (the closure never runs);
      // `Some(x)` runs the closure. Verified identical on all six cells.
      suite("Option HOFs with .await in the closure")(
        test("Option.map over a Some runs the closure and rewraps") {
          val r = Async.async(Option(5).map(i => Async.succeed(i * 10).await)).block
          assertTrue(r == Some(50))
        },
        test("Option.map over a None short-circuits (closure never runs)") {
          var ran = false
          val r   = Async.async {
            (None: Option[Int]).map { i => ran = true; Async.succeed(i * 10).await }
          }.block
          assertTrue(r == None, !ran)
        },
        test("Option.map over a genuinely-pending await") {
          val r = Async.async(Option(7).map(i => pending(i).await + 1)).block
          assertTrue(r == Some(8))
        },
        test("Option.map propagates a failing await") {
          val a      = Async.async(Option(5).map(_ => Async.fail(AsyncTestSupport.boom).await: Int))
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        },
        test("Option.flatMap over a Some accumulates the inner Option") {
          val r = Async.async(Option(5).flatMap(i => Option(Async.succeed(i * 10).await))).block
          assertTrue(r == Some(50))
        },
        test("Option.flatMap over a Some that yields None") {
          val r = Async.async(Option(5).flatMap(i => if (Async.succeed(i).await > 0) None else Some(1))).block
          assertTrue(r == None)
        },
        test("Option.foreach over a Some runs the await for its effect") {
          var acc = 0
          val r   = Async.async {
            Option(5).foreach(i => acc += Async.succeed(i).await)
          }.block
          assertTrue(r == (()), acc == 5)
        },
        test("Option.foreach over a None runs nothing") {
          var acc = 0
          val r   = Async.async {
            (None: Option[Int]).foreach(i => acc += Async.succeed(i).await)
          }.block
          assertTrue(r == (()), acc == 0)
        },
        test("for-comprehension over Options desugars to flatMap/map") {
          val r = Async.async {
            for {
              i <- Option(2)
              j <- Option(3)
            } yield Async.succeed(i + j).await
          }.block
          assertTrue(r == Some(5))
        },
        test("for-comprehension over Options short-circuits on a None generator") {
          val r = Async.async {
            for {
              i <- Option(2)
              j <- (None: Option[Int])
            } yield Async.succeed(i + j).await
          }.block
          assertTrue(r == None)
        }
        // NOTE: an Option for-comprehension *guard* (`for { i <- opt if p }`,
        // desugaring to `opt.withFilter(p)`) is supported by the Scala 2 macro but
        // NOT by dotty-cps-async on Scala 3 (it has no `AsyncShift[Option#WithFilter]`
        // — unlike `List`, for which a single guard works). It is therefore a
        // Scala-2-only superset, covered in `AsyncAwaitScala2HofSpec`.
      ),
      // `.await` inside `Vector` HOF closures. Unlike `List.map` (eager via DCA's
      // special `ListAsyncShift`), `Vector.map`/`foreach`/`flatMap` are all
      // LAZY / sequential on every backend (verified empirically): the closure for
      // element n+1 runs only after element n's await completes; a failure
      // short-circuits the rest. The result collection type is preserved (Vector).
      suite("Vector HOFs with .await in the closure")(
        test("Vector.map preserves order and result type") {
          val r = Async.async(Vector(1, 2, 3).map(i => Async.succeed(i * 10).await)).block
          assertTrue(r == Vector(10, 20, 30), r.isInstanceOf[Vector[_]])
        },
        test("Vector.map over genuinely-pending awaits") {
          val r = Async.async(Vector(1, 2, 3).map(i => pending(i * 10).await)).block
          assertTrue(r == Vector(10, 20, 30))
        },
        test("Vector.map is lazy: a failing await short-circuits the remaining elements") {
          var seen = List.empty[Int]
          val a    = Async.async {
            Vector(1, 2, 3).map { i =>
              seen = i :: seen
              if (i == 2) Async.fail(AsyncTestSupport.boom).await else Async.succeed(i).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("Vector.foreach runs ready awaits in order") {
          var acc = 0
          val r   = Async.async {
            Vector(1, 2, 3).foreach(i => acc += Async.succeed(i).await)
            acc
          }.block
          assertTrue(r == 6, acc == 6)
        },
        test("Vector.flatMap accumulates ready awaits, preserving Vector") {
          val r = Async.async {
            Vector(1, 2, 3).flatMap(i => Vector(Async.succeed(i).await, i * 10))
          }.block
          assertTrue(r == Vector(1, 10, 2, 20, 3, 30), r.isInstanceOf[Vector[_]])
        },
        test("for-comprehension over Vectors desugars to flatMap/map") {
          val r = Async.async {
            for {
              i <- Vector(1, 2)
              j <- Vector(10, 20)
            } yield Async.succeed(i + j).await
          }.block
          assertTrue(r == Vector(11, 21, 12, 22))
        }
      ),
      // `.await` inside immutable `Set` HOF closures. Result is a `Set` (the
      // awaited values are deduplicated — never an intermediate `Set[Async[B]]`).
      // Set iteration order is unspecified, so assertions compare as sets / sums.
      suite("Set HOFs with .await in the closure")(
        test("Set.map deduplicates awaited results, preserving Set") {
          val r = Async.async(Set(1, 2, 3, 4).map(i => Async.succeed(i % 2).await)).block
          assertTrue(r == Set(0, 1), r.isInstanceOf[Set[_]])
        },
        test("Set.map over genuinely-pending awaits") {
          val r = Async.async(Set(1, 2, 3).map(i => pending(i * 10).await)).block
          assertTrue(r == Set(10, 20, 30))
        },
        test("Set.foreach runs the await for every element") {
          var sum = 0
          val r   = Async.async {
            Set(1, 2, 3).foreach(i => sum += Async.succeed(i).await)
            sum
          }.block
          assertTrue(r == 6, sum == 6)
        },
        test("Set.flatMap accumulates into a Set") {
          val r = Async.async {
            Set(1, 2).flatMap(i => Set(Async.succeed(i).await, i + 10))
          }.block
          assertTrue(r == Set(1, 11, 2, 12), r.isInstanceOf[Set[_]])
        }
      ),
      // `.await` inside immutable `Map` HOF closures. Entries are `(K, V)` tuples;
      // a pair-returning `map`/`flatMap` rebuilds a `Map[K2, V2]`. `Map` iteration
      // order is unspecified, so assertions compare as maps / sums, never by order.
      suite("Map HOFs with .await in the closure")(
        test("Map.map over a pair-returning closure awaits values, preserving Map") {
          val r = Async.async {
            Map(1 -> 1, 2 -> 2, 3 -> 3).map { case (k, v) => (k, Async.succeed(v * 10).await) }
          }.block
          assertTrue(r == Map(1 -> 10, 2 -> 20, 3 -> 30), r.isInstanceOf[Map[_, _]])
        },
        test("Map.map over genuinely-pending awaits") {
          val r = Async.async {
            Map(1 -> 1, 2 -> 2, 3 -> 3).map { case (k, v) => (k, pending(v * 10).await) }
          }.block
          assertTrue(r == Map(1 -> 10, 2 -> 20, 3 -> 30))
        },
        test("Map.map propagates a failing await") {
          val a      = Async.async(Map(1 -> 1).map { case (k, v) => (k, Async.fail(AsyncTestSupport.boom).await) })
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        },
        test("Map.flatMap accumulates into a Map") {
          val r = Async.async {
            Map(1 -> 1, 2 -> 2).flatMap { case (k, v) => Map(k -> Async.succeed(v).await, (k + 10) -> v) }
          }.block
          assertTrue(r == Map(1 -> 1, 11 -> 1, 2 -> 2, 12 -> 2), r.isInstanceOf[Map[_, _]])
        },
        test("Map.foreach runs the await for every entry") {
          var sum = 0
          val r   = Async.async {
            Map(1 -> 10, 2 -> 20, 3 -> 30).foreach { case (_, v) => sum += Async.succeed(v).await }
            sum
          }.block
          assertTrue(r == 60, sum == 60)
        },
        test("Map.map over a non-pair closure widens to an Iterable") {
          val r = Async.async {
            Map(1 -> 10, 2 -> 20, 3 -> 30).map { case (k, v) => Async.succeed(k + v).await }
          }.block
          assertTrue(r.toSet == Set(11, 22, 33))
        }
      ),
      // `.await` inside short-circuiting predicate HOFs (`find`/`exists`/`forall`).
      // These are inherently lazy / sequential on every backend: the predicate for
      // element n+1 runs only after element n's await completes, and the scan stops
      // at the first decisive element. They work over any whitelisted receiver.
      suite("predicate-scanning HOFs with .await in the closure")(
        test("List.exists short-circuits on the first match") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3, 4).exists { i => seen = i :: seen; Async.succeed(i == 2).await }
          }.block
          assertTrue(r, seen == List(2, 1))
        },
        test("List.exists over pending awaits returns false when none match") {
          val r = Async.async(List(1, 2, 3).exists(i => pending(i).await > 5)).block
          assertTrue(!r)
        },
        test("List.forall short-circuits on the first false") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3, 4).forall { i => seen = i :: seen; Async.succeed(i < 3).await }
          }.block
          assertTrue(!r, seen == List(3, 2, 1))
        },
        test("List.forall returns true when all pass (pending)") {
          val r = Async.async(List(1, 2, 3).forall(i => pending(i).await > 0)).block
          assertTrue(r)
        },
        test("List.find returns the first match, short-circuiting") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3, 4).find { i => seen = i :: seen; Async.succeed(i % 2 == 0).await }
          }.block
          assertTrue(r == Some(2), seen == List(2, 1))
        },
        test("List.find returns None when nothing matches (pending)") {
          val r = Async.async(List(1, 2, 3).find(i => pending(i).await > 5)).block
          assertTrue(r == None)
        },
        test("exists propagates a failing await") {
          val a      = Async.async(List(1, 2, 3).exists(i => Async.fail(AsyncTestSupport.boom).await))
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        },
        test("Vector.exists over a generic receiver") {
          val r = Async.async(Vector(1, 2, 3).exists(i => Async.succeed(i == 3).await)).block
          assertTrue(r)
        },
        test("Option.exists over a Some") {
          val r = Async.async(Option(4).exists(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r)
        },
        test("Map.exists over entries") {
          val r = Async.async(Map(1 -> 10, 2 -> 20).exists { case (_, v) => Async.succeed(v == 20).await }).block
          assertTrue(r)
        },
        test("Map.find over entries returns the matching pair") {
          val r = Async.async(Map(1 -> 10, 2 -> 20).find { case (_, v) => Async.succeed(v == 20).await }).block
          assertTrue(r == Some((2, 20)))
        },
        test("Option.find returns the value when the predicate matches") {
          val r = Async.async(Option(4).find(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == Some(4))
        },
        test("Option.find returns None when the predicate fails (Some)") {
          val r = Async.async(Option(3).find(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == None)
        },
        test("Option.find on None never runs the predicate") {
          var ran = false
          val r   = Async.async(Option.empty[Int].find { i => ran = true; Async.succeed(i % 2 == 0).await }).block
          assertTrue(r == None, !ran)
        },
        test("Option.find propagates a failing await") {
          val a      = Async.async(Option(1).find(_ => Async.fail(AsyncTestSupport.boom).await))
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        }
      ),
      // `.await` inside a `foldLeft` op closure. A left fold is inherently
      // sequential — element n+1's op needs n's accumulator — so it is
      // lazy/sequential on every backend: element n+1's op runs only after
      // element n's await completes, and a failing await short-circuits the rest.
      // `foldLeft[B]` returns `B` directly (not a collection wrapper), and works
      // over any whitelisted receiver via `.iterator`.
      suite("foldLeft with .await in the op closure")(
        test("threads the accumulator over ready awaits") {
          val r = Async.async {
            List(1, 2, 3, 4).foldLeft(0)((acc, x) => acc + Async.succeed(x).await)
          }.block
          assertTrue(r == 10)
        },
        test("threads the accumulator over genuinely-pending awaits") {
          val r = Async.async {
            List(10, 20, 30).foldLeft(0)((acc, x) => acc + pending(x).await)
          }.block
          assertTrue(r == 60)
        },
        test("supports a result type that differs from the element type") {
          val r = Async.async {
            List(1, 2, 3).foldLeft(List.empty[Int])((acc, x) => Async.succeed(x * 10).await :: acc)
          }.block
          assertTrue(r == List(30, 20, 10))
        },
        test("awaits the initial accumulator before folding") {
          val r = Async.async {
            List(1, 2, 3).foldLeft(Async.succeed(100).await)((acc, x) => acc + Async.succeed(x).await)
          }.block
          assertTrue(r == 106)
        },
        test("an empty receiver yields the initial accumulator without running the op") {
          var ran = false
          val r   = Async.async {
            List.empty[Int].foldLeft(42) { (acc, x) => ran = true; acc + Async.succeed(x).await }
          }.block
          assertTrue(r == 42, !ran)
        },
        test("a failing await short-circuits the remaining elements (lazy)") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).foldLeft(0) { (acc, x) =>
              seen = x :: seen
              if (x == 2) acc + (Async.fail(AsyncTestSupport.boom).await: Int) else acc + Async.succeed(x).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("folds over a Vector receiver") {
          val r = Async.async {
            Vector(1, 2, 3).foldLeft(0)((acc, x) => acc + Async.succeed(x).await)
          }.block
          assertTrue(r == 6)
        }
      ),
      // `.await` inside a `filter` / `filterNot` predicate. **Lazy / sequential**
      // on every backend (the predicate for element n+1 runs only after element
      // n's await completes; a failed await short-circuits the rest), and the
      // result **collection type is preserved**. `filter` keeps elements whose
      // predicate is `true`, `filterNot` those whose predicate is `false`.
      suite("filter / filterNot with .await in the predicate")(
        test("List.filter keeps matching elements, preserving order") {
          val r = Async.async(List(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == List(2, 4))
        },
        test("List.filter over genuinely-pending awaits") {
          val r = Async.async(List(1, 2, 3, 4).filter(i => pending(i % 2).await == 0)).block
          assertTrue(r == List(2, 4))
        },
        test("List.filterNot keeps non-matching elements") {
          val r = Async.async(List(1, 2, 3, 4).filterNot(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == List(1, 3))
        },
        test("filter is lazy: a failing await short-circuits the remaining elements") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).filter { i =>
              seen = i :: seen
              if (i == 2) (Async.fail(AsyncTestSupport.boom).await: Boolean) else Async.succeed(i % 2 == 1).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("Vector.filter preserves the Vector type") {
          val r = Async.async(Vector(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == Vector(2, 4))
        },
        test("Set.filter preserves the Set type") {
          val r = Async.async(Set(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == Set(2, 4))
        },
        test("Option.filter over a Some that matches keeps it") {
          val r = Async.async(Option(4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == Some(4))
        },
        test("Option.filter over a Some that fails the predicate yields None") {
          val r = Async.async(Option(3).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == None)
        },
        test("Option.filterNot over a Some inverts the predicate") {
          val r = Async.async(Option(3).filterNot(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == Some(3))
        },
        test("Option.filter over a None short-circuits (predicate never runs)") {
          var ran = false
          val r   = Async.async {
            (None: Option[Int]).filter { i => ran = true; Async.succeed(i % 2 == 0).await }
          }.block
          assertTrue(r == None, !ran)
        }
        // NOTE: `Map.filter`/`filterNot` with `.await` is a Scala-2-only superset
        // — dotty-cps-async has no working `MapOpsAsyncShift.filter` and crashes —
        // so it is covered (Scala 2 only) in `AsyncAwaitScala2HofSpec`.
      ),
      // `.await` inside a `takeWhile` / `dropWhile` predicate. These are
      // **prefix-ordered** (only meaningful on ordered `Seq` receivers — `List` /
      // `Vector`): `takeWhile` keeps the longest leading run satisfying the
      // predicate (the first failure stops the scan), `dropWhile` drops it (the
      // first failure and everything after it is kept unconditionally). The
      // collection type is preserved.
      suite("takeWhile / dropWhile with .await in the predicate")(
        test("List.takeWhile keeps the leading run, stopping at the first failure") {
          val r = Async.async(List(1, 2, 3, 4, 1).takeWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r == List(1, 2))
        },
        test("List.takeWhile over genuinely-pending awaits") {
          val r = Async.async(List(2, 4, 5, 6).takeWhile(i => pending(i % 2).await == 0)).block
          assertTrue(r == List(2, 4))
        },
        test("List.dropWhile drops the leading run, keeping the rest unconditionally") {
          val r = Async.async(List(1, 2, 3, 4, 1).dropWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r == List(3, 4, 1))
        },
        test("List.takeWhile with an always-true predicate keeps everything") {
          val r = Async.async(List(1, 2, 3).takeWhile(i => Async.succeed(i > 0).await)).block
          assertTrue(r == List(1, 2, 3))
        },
        test("List.dropWhile with an always-true predicate drops everything") {
          val r = Async.async(List(1, 2, 3).dropWhile(i => Async.succeed(i > 0).await)).block
          assertTrue(r == Nil)
        },
        test("takeWhile is lazy: it stops evaluating the predicate after the first failure") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3, 4).takeWhile { i =>
              seen = i :: seen
              Async.succeed(i < 3).await
            }
          }.block
          assertTrue(r == List(1, 2), seen == List(3, 2, 1))
        },
        test("dropWhile stops evaluating the predicate after the first failure") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3, 4).dropWhile { i =>
              seen = i :: seen
              Async.succeed(i < 3).await
            }
          }.block
          assertTrue(r == List(3, 4), seen == List(3, 2, 1))
        },
        test("takeWhile is lazy: a failing await short-circuits the remaining elements") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).takeWhile { i =>
              seen = i :: seen
              if (i == 2) (Async.fail(AsyncTestSupport.boom).await: Boolean) else Async.succeed(i < 3).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("Vector.takeWhile preserves the Vector type") {
          val r = Async.async(Vector(1, 2, 3, 4).takeWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r == Vector(1, 2))
        },
        test("Vector.dropWhile preserves the Vector type") {
          val r = Async.async(Vector(1, 2, 3, 4).dropWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r == Vector(3, 4))
        }
      ),
      // `.await` inside a `reduce` / `reduceLeft` op closure. Like `foldLeft` but
      // seeded by the first element: **lazy / sequential** left-to-right, and an
      // EMPTY receiver fails with `UnsupportedOperationException`.
      suite("reduce / reduceLeft with .await in the op closure")(
        test("List.reduce folds left-to-right over ready awaits") {
          val r = Async.async(List(1, 2, 3, 4).reduce((acc, x) => acc + Async.succeed(x).await)).block
          assertTrue(r == 10)
        },
        test("List.reduce over genuinely-pending awaits") {
          val r = Async.async(List(1, 2, 3, 4).reduce((acc, x) => acc + pending(x).await)).block
          assertTrue(r == 10)
        },
        test("List.reduceLeft behaves identically to reduce") {
          val r = Async.async(List(2, 3, 4).reduceLeft((acc, x) => acc * Async.succeed(x).await)).block
          assertTrue(r == 24)
        },
        test("a single-element receiver returns that element without running the op") {
          var ran = false
          val r   = Async.async {
            List(42).reduce { (acc, x) => ran = true; acc + Async.succeed(x).await }
          }.block
          assertTrue(r == 42, !ran)
        },
        test("Vector.reduce folds over a Vector receiver") {
          val r = Async.async(Vector(1, 2, 3).reduce((acc, x) => acc + Async.succeed(x).await)).block
          assertTrue(r == 6)
        },
        test("an empty receiver fails with UnsupportedOperationException") {
          val a      = Async.async(List.empty[Int].reduce((acc, x) => acc + Async.succeed(x).await))
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.exists(_.isInstanceOf[UnsupportedOperationException]))
        },
        test("a failing await short-circuits the remaining elements (lazy)") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3, 4).reduce { (acc, x) =>
              seen = x :: seen
              if (x == 3) (Async.fail(AsyncTestSupport.boom).await: Int) else acc + Async.succeed(x).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(3, 2))
        }
      ),
      // `.await` inside a `foldRight` op closure. **Right-associative**: the op
      // runs right-to-left (`op(x1, op(x2, ..., op(xn, z)))`), so the rightmost
      // element's await happens first; **lazy / sequential** with the accumulator
      // threaded; an empty receiver yields the initial accumulator.
      suite("foldRight with .await in the op closure")(
        test("List.foldRight is right-associative over ready awaits") {
          val r = Async.async {
            List(1, 2, 3).foldRight("z")((x, acc) => s"($x+${Async.succeed(acc).await})")
          }.block
          assertTrue(r == "(1+(2+(3+z)))")
        },
        test("List.foldRight over genuinely-pending awaits") {
          val r = Async.async(List(1, 2, 3).foldRight(0)((x, acc) => x + pending(acc).await)).block
          assertTrue(r == 6)
        },
        test("foldRight runs the op right-to-left") {
          var seen = List.empty[Int]
          val r    = Async.async {
            List(1, 2, 3).foldRight(0) { (x, acc) =>
              seen = x :: seen
              x + Async.succeed(acc).await
            }
          }.block
          assertTrue(r == 6, seen == List(1, 2, 3)) // prepended right-to-left => [1,2,3]
        },
        test("supports a result type that differs from the element type") {
          val r = Async.async {
            List(1, 2, 3).foldRight(List.empty[Int])((x, acc) => Async.succeed(x).await :: acc)
          }.block
          assertTrue(r == List(1, 2, 3))
        },
        test("an empty receiver yields the initial accumulator without running the op") {
          var ran = false
          val r   = Async.async {
            List.empty[Int].foldRight(99) { (x, acc) => ran = true; x + Async.succeed(acc).await }
          }.block
          assertTrue(r == 99, !ran)
        },
        test("Vector.foldRight folds over a Vector receiver") {
          val r = Async.async(Vector(1, 2, 3).foldRight(0)((x, acc) => x + Async.succeed(acc).await)).block
          assertTrue(r == 6)
        },
        test("a failing await short-circuits the remaining elements (lazy, right-to-left)") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3, 4).foldRight(0) { (x, acc) =>
              seen = x :: seen
              if (x == 2) (Async.fail(AsyncTestSupport.boom).await: Int) else x + Async.succeed(acc).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 3, 4))
        }
      ),
      // `.await` inside a `collect` case body. Keeps the elements the partial
      // function is defined at, mapping them through the (awaiting) body; **lazy /
      // sequential**, collection type preserved.
      suite("collect with .await in a case body")(
        test("List.collect keeps matching elements, mapping them through an awaited body") {
          val r = Async.async {
            List(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i * 10).await }
          }.block
          assertTrue(r == List(10, 30))
        },
        test("List.collect over genuinely-pending awaits") {
          val r = Async.async {
            List(1, 2, 3, 4).collect { case i if i % 2 == 0 => pending(i * 100).await }
          }.block
          assertTrue(r == List(200, 400))
        },
        test("multiple cases are tried in order") {
          val r = Async.async {
            List(1, 2, 3, 4, 5).collect {
              case i if i % 2 == 0 => Async.succeed(s"even$i").await
              case i if i == 5 => Async.succeed("five").await
            }
          }.block
          assertTrue(r == List("even2", "even4", "five"))
        },
        test("a guarded single case keeps only the matching elements") {
          // NOTE: the guard-evaluation COUNT is an implementation detail that
          // differs across backends (the Scala 2 macro evaluates it exactly once
          // per element; dotty-cps-async may evaluate it more than once), so only
          // the RESULT is asserted here. The "exactly once" guarantee for the
          // Scala 2 macro is covered in `AsyncAwaitScala2HofSpec`.
          val r = Async.async {
            List(1, 2, 3, 4).collect { case i if i % 2 == 0 => Async.succeed(i).await }
          }.block
          assertTrue(r == List(2, 4))
        },
        test("an empty receiver yields an empty result") {
          val r = Async.async(List.empty[Int].collect { case i if i > 0 => Async.succeed(i).await }).block
          assertTrue(r == Nil)
        },
        test("Vector.collect preserves the Vector type") {
          val r = Async.async {
            Vector(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await }
          }.block
          assertTrue(r == Vector(1, 3))
        },
        test("Set.collect preserves the Set type") {
          val r = Async.async {
            Set(1, 2, 3, 4).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await }
          }.block
          assertTrue(r == Set(20, 40))
        },
        test("a failing await in a matched body short-circuits the remaining elements") {
          var seen = List.empty[Int]
          val a    = Async.async {
            List(1, 2, 3).collect { case i =>
              seen = i :: seen
              if (i == 2) (Async.fail(AsyncTestSupport.boom).await: Int) else Async.succeed(i).await
            }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("Option.collect keeps a matching Some, mapping it through an awaited body") {
          val r = Async.async {
            Option(2).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await }
          }.block
          assertTrue(r == Some(20))
        },
        test("Option.collect over a genuinely-pending await") {
          val r = Async.async {
            Option(3).collect { case i if i % 2 == 1 => pending(i * 100).await }
          }.block
          assertTrue(r == Some(300))
        },
        test("Option.collect returns None when the Some does not match") {
          val r = Async.async {
            Option(3).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await }
          }.block
          assertTrue(r == None)
        },
        test("Option.collect over None never evaluates the partial function") {
          var ran = false
          val r   = Async.async {
            Option.empty[Int].collect { case i =>
              ran = true
              Async.succeed(i).await
            }
          }.block
          assertTrue(r == None, !ran)
        },
        test("Option.collect propagates a failing await in a matched body") {
          val a = Async.async {
            Option(1).collect { case i => (Async.fail(AsyncTestSupport.boom).await: Int) }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        },
        // Only the NON-pair `Map.collect` (result `Iterable[B]`) is cross-version:
        // dotty-cps-async has no Map-specific collect shift, so a pair-yielding
        // `Map.collect` (result `Map[K2, V2]`) is a compile error on Scala 3 and is
        // rejected on Scala 2 too (covered in `AsyncAwaitScala2HofSpec`).
        test("Map.collect with a non-pair body widens to an Iterable") {
          val r = Async.async {
            Map(1 -> 10, 2 -> 20).collect { case (k, v) => Async.succeed(k + v).await }
          }.block
          assertTrue(r.toSet == Set(11, 22))
        },
        test("Map.collect over a genuinely-pending non-pair await") {
          val r = Async.async {
            Map(1 -> 10, 2 -> 20).collect { case (k, v) => pending(k + v).await }
          }.block
          assertTrue(r.toSet == Set(11, 22))
        },
        test("Map.collect that matches nothing yields an empty result") {
          val r = Async.async {
            Map(1 -> 10, 2 -> 20).collect { case (k, v) if k > 100 => Async.succeed(k + v).await }
          }.block
          assertTrue(r.isEmpty)
        },
        test("Map.collect propagates a failing await in a matched body") {
          val a = Async.async {
            Map(1 -> 10).collect { case (k, v) => (Async.fail(AsyncTestSupport.boom).await: Int) }
          }
          val thrown = scala.util.Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom))
        }
      ),
      // Additional strict immutable `Seq` receivers (`Queue` / `ArraySeq`) reuse
      // the generic `iterable` HOF rewrites, so `.await` works in their closures
      // with the collection family preserved. Representative HOFs are covered here.
      suite("immutable Queue / ArraySeq HOF closures with .await")(
        test("Queue.map preserves the Queue type") {
          val r = Async.async(scala.collection.immutable.Queue(1, 2, 3).map(i => Async.succeed(i * 10).await)).block
          assertTrue(r == scala.collection.immutable.Queue(10, 20, 30))
        },
        test("Queue.filter preserves the Queue type") {
          val r =
            Async.async(scala.collection.immutable.Queue(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r == scala.collection.immutable.Queue(2, 4))
        },
        test("Queue.takeWhile preserves the Queue type") {
          val r =
            Async.async(scala.collection.immutable.Queue(1, 2, 3, 1).takeWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r == scala.collection.immutable.Queue(1, 2))
        },
        test("Queue.foldLeft folds over a Queue receiver") {
          val r =
            Async
              .async(scala.collection.immutable.Queue(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
              .block
          assertTrue(r == 6)
        },
        test("Queue.collect preserves the Queue type") {
          val r = Async.async {
            scala.collection.immutable.Queue(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await }
          }.block
          assertTrue(r == scala.collection.immutable.Queue(1, 3))
        },
        test("ArraySeq.map preserves the ArraySeq type") {
          val r = Async.async(scala.collection.immutable.ArraySeq(1, 2, 3).map(i => Async.succeed(i * 10).await)).block
          assertTrue(r == scala.collection.immutable.ArraySeq(10, 20, 30))
        },
        test("ArraySeq.filter preserves the ArraySeq type") {
          val r =
            Async
              .async(scala.collection.immutable.ArraySeq(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
              .block
          assertTrue(r == scala.collection.immutable.ArraySeq(2, 4))
        },
        test("ArraySeq.foldLeft folds over an ArraySeq receiver") {
          val r = Async
            .async(scala.collection.immutable.ArraySeq(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
            .block
          assertTrue(r == 6)
        },
        test("ArraySeq.collect preserves the ArraySeq type") {
          val r = Async.async {
            scala.collection.immutable.ArraySeq(1, 2, 3, 4).collect {
              case i if i % 2 == 1 => Async.succeed(i * 10).await
            }
          }.block
          assertTrue(r == scala.collection.immutable.ArraySeq(10, 30))
        }
      ),
      // `Array` is special: it is invariant, is not an `Iterable`, and its HOFs go
      // through the implicit `ArrayOps` wrapper with an implicit `ClassTag[B]`.
      // `map` is EAGER (like `List.map`), `flatMap` is lazy / sequential, and the
      // result is always an `Array[B]` (element type preserved, incl. primitives).
      suite("Array HOF closures with .await")(
        test("Array.map is eager and preserves the (primitive) element type") {
          val r           = Async.async(Array(1, 2, 3).map(i => Async.succeed(i.toLong * 10).await)).block
          val isLongArray = r.getClass.getComponentType == java.lang.Long.TYPE
          assertTrue(r.toList == List(10L, 20L, 30L), isLongArray)
        },
        test("Array.map is eager: a failing await still runs every preceding closure") {
          var seen   = List.empty[Int]
          val thrown = scala.util
            .Try(Async.async {
              Array(1, 2, 3).map { i =>
                seen = i :: seen
                if (i == 2) (Async.fail(AsyncTestSupport.boom).await: Int) else Async.succeed(i).await
              }
            }.block)
            .failed
            .toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(3, 2, 1))
        },
        test("Array.flatMap concatenates and preserves the Array type") {
          val r = Async.async(Array(1, 2, 3).flatMap(i => Array(Async.succeed(i).await, i * 10))).block
          assertTrue(r.toList == List(1, 10, 2, 20, 3, 30))
        },
        test("Array.flatMap is lazy: a failing await short-circuits the remaining elements") {
          var seen   = List.empty[Int]
          val thrown = scala.util
            .Try(Async.async {
              Array(1, 2, 3).flatMap { i =>
                seen = i :: seen
                if (i == 2) Array(Async.fail(AsyncTestSupport.boom).await: Int) else Array(Async.succeed(i).await)
              }
            }.block)
            .failed
            .toOption
          assertTrue(thrown.contains(AsyncTestSupport.boom), seen == List(2, 1))
        },
        test("Array.foreach runs awaits in order") {
          var acc = 0
          Async.async(Array(1, 2, 3).foreach(i => acc += Async.succeed(i).await)).block
          assertTrue(acc == 6)
        },
        test("Array.filter preserves the Array type") {
          val r = Async.async(Array(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
          assertTrue(r.toList == List(2, 4))
        },
        test("Array.takeWhile preserves the Array type") {
          val r = Async.async(Array(1, 2, 3, 1).takeWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r.toList == List(1, 2))
        },
        test("Array.dropWhile preserves the Array type") {
          val r = Async.async(Array(1, 2, 3, 1).dropWhile(i => Async.succeed(i < 3).await)).block
          assertTrue(r.toList == List(3, 1))
        },
        test("Array.foldLeft folds over an Array receiver") {
          val r = Async.async(Array(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await)).block
          assertTrue(r == 6)
        },
        test("Array.collect preserves the Array type") {
          val r = Async.async(Array(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await }).block
          assertTrue(r.toList == List(1, 3))
        },
        test("Array.exists short-circuits at the first matching await") {
          val r = Async.async(Array(1, 2, 3).exists(i => Async.succeed(i == 2).await)).block
          assertTrue(r)
        }
      )
      // NOTE: `val`-type-ascription preservation is a Scala-2-macro-specific
      // behavior and lives in `AsyncAwaitValAscriptionSpec` (scala-2 only). On
      // Scala 3, dotty-cps-async pushes the val's expected type INTO `.await`
      // (`val n: Long = intAsync.await` elaborates as `await[Long](intAsync)` and
      // does NOT compile), so the cases cannot be asserted identically here.
      ,
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

          AsyncTestSupport.startTap(a) { _ =>
            cbThread.set(Thread.currentThread())
            latch.countDown()
          }
          latch.await()
          assertTrue(cbThread.get() ne callerThread)
        }
      },
      test("start of a pollable-as-value settles to the pollable identity and re-blocks without deadlock") {
        ZIO.attemptBlocking {
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
          }

          // Un-started block preserves the pollable-as-value (established contract).
          val direct: AnyRef = (Async.succeed(inner): Async[Pollable[Int]]).block.asInstanceOf[AnyRef]

          // A Running is itself an Async[A]; blocking it must yield the same value.
          // Regression: `start` stored the bare pollable terminal, so the Running's
          // `poll` re-exposed it as a still-suspended computation and the blocking
          // worker parked forever (no wakeup) -> deadlock.
          val running = Async.start(Async.succeed(inner))
          val result  = new AtomicReference[AnyRef](null)
          val done    = new AtomicBoolean(false)
          val worker  = new Thread(new Runnable {
            def run(): Unit =
              try result.set((running: Async[Pollable[Int]]).block.asInstanceOf[AnyRef])
              finally done.set(true)
          })
          worker.setDaemon(true)
          worker.start()
          worker.join(2000) // bound a potential regression so the suite fails instead of hanging

          assertTrue(direct eq inner, done.get(), result.get() eq inner)
        }
      },
      test("start of a still-pending pollable-as-value returns the Running without blocking the caller") {
        ZIO.attemptBlocking {
          // `Async.start` promises to drive eagerly WITHOUT blocking. A success
          // value that is itself a still-pending pollable must be driven on the
          // background worker like any other suspension — not parked on the
          // calling thread until the leaf completes.
          val c        = new Completer[Int]
          val returned = new AtomicBoolean(false)
          val caller   = new Thread(new Runnable {
            def run(): Unit = {
              val _ = Async.start(Async.succeed(c): Async[Completer[Int]])
              returned.set(true)
            }
          })
          caller.setDaemon(true)
          caller.start()
          caller.join(2000) // bound the regression: a blocking `start` parks here until the completer settles
          val returnedWhileInnerStillPending = returned.get()
          c.succeed(1) // un-wedge a parked caller so the suite does not leak the thread
          assertTrue(returnedWhileInnerStillPending)
        }
      },
      test("cancel interrupts a parked worker, killing the thread and suppressing the callback") {
        ZIO.attemptBlocking {
          val fired = new AtomicBoolean(false)
          // A Completer that never completes: the worker parks indefinitely.
          val c       = new Completer[Int]
          val running = AsyncTestSupport.startTap(c.peek)(_ => fired.set(true))

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
          running.cancel()

          var stillAlive = true
          var checks     = 0
          while (stillAlive && checks < 100) {
            stillAlive = runnerThread().isDefined
            if (stillAlive) Thread.sleep(10)
            checks += 1
          }

          // A second cancel must be a silent no-op (CAS already lost): idempotent
          // on a genuinely suspended Run, not just on the synchronous no-op path.
          running.cancel()

          assertTrue(parkedBefore, !stillAlive, !fired.get())
        }
      }
    )
  )
}
