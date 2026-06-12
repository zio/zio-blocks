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

import java.util.concurrent.{
  CancellationException,
  CompletableFuture,
  CompletionException,
  CountDownLatch,
  ExecutionException,
  Executors,
  TimeUnit
}

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._

/**
 * JVM interop with Future and CompletionStage.
 */
object AsyncInteropSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncInteropSpec")(
    suite("Future")(
      test("fromFuture: already-succeeded collapses to a value") {
        val r = AsyncInterop.fromFuture(Future.successful(7)).block
        assertTrue(r == 7)
      },
      test("fromFuture: already-failed collapses to a fail") {
        val boom   = AsyncTestSupport.boom
        val r      = AsyncInterop.fromFuture(Future.failed(boom))
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("fromFuture: pending future completes asynchronously") {
        ZIO.attemptBlocking {
          val p = Promise[Int]()
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); p.success(11); () }
          })
          t.setDaemon(true)
          t.start()
          val r = AsyncInterop.fromFuture(p.future).block
          assertTrue(r == 11)
        }
      },
      test("fromFuture: pending future that fails surfaces the cause") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late")
          val p    = Promise[Int]()
          val t    = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); p.failure(boom); () }
          })
          t.setDaemon(true)
          t.start()
          val thrown = scala.util.Try(AsyncInterop.fromFuture(p.future).block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      },
      test("toFuture: ready value collapses to Future.successful") {
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(Async.succeed(3)))
          .map(v => assertTrue(v == 3))
      },
      test("toFuture: failure collapses to Future.failed") {
        val boom = new RuntimeException("nope")
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(Async.fail(boom)))
          .either
          .map {
            case Left(t)  => assertTrue(t eq boom)
            case Right(_) => assertTrue(false)
          }
      },
      test("toFuture: suspended pollable resolves via the EC") {
        val a = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); c.succeed(99) }
          })
          t.setDaemon(true)
          t.start()
        }
        ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).map(v => assertTrue(v == 99))
      },
      test("toFuture: suspended pollable that fails surfaces the cause via the EC") {
        val boom = new RuntimeException("late-future-fail")
        val a    = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); c.fail(boom) }
          })
          t.setDaemon(true)
          t.start()
        }
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(a))
          .either
          .map {
            case Left(t)  => assertTrue(t eq boom)
            case Right(_) => assertTrue(false)
          }
      }
    ),
    suite("CompletionStage")(
      test("fromCompletionStage: already-completed collapses to a value") {
        val cf = CompletableFuture.completedFuture(5)
        val r  = AsyncInterop.fromCompletionStage(cf).block
        assertTrue(r == 5)
      },
      test("fromCompletionStage: already-failed unwraps the cause") {
        val boom = new RuntimeException("ce")
        val cf   = new CompletableFuture[Int]()
        cf.completeExceptionally(boom)
        val thrown = scala.util.Try(AsyncInterop.fromCompletionStage(cf).block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("fromCompletionStage: cancelled stage surfaces the CancellationException") {
        // A cancelled future is done; `get()` throws a CancellationException,
        // which is neither a CompletionException nor an ExecutionException, so
        // the unwrap leaves it untouched (the `other` branch).
        val cf = new CompletableFuture[Int]()
        cf.cancel(true)
        val thrown = scala.util.Try(AsyncInterop.fromCompletionStage(cf).block).failed.toOption
        assertTrue(thrown.exists(_.isInstanceOf[CancellationException]))
      },
      test("fromCompletionStage: pending stage completes asynchronously") {
        ZIO.attemptBlocking {
          val ex = Executors.newSingleThreadExecutor()
          try {
            val cf = CompletableFuture.supplyAsync[Int](
              () => { Thread.sleep(30); 21 },
              ex
            )
            val r = AsyncInterop.fromCompletionStage(cf).block
            assertTrue(r == 21)
          } finally {
            ex.shutdown(); ex.awaitTermination(2, TimeUnit.SECONDS); ()
          }
        }
      },
      test("fromCompletionStage: pending failure is unwrapped from CompletionException") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late-ce")
          val ex   = Executors.newSingleThreadExecutor()
          try {
            val cf = CompletableFuture.supplyAsync[Int](
              () => { Thread.sleep(30); throw boom },
              ex
            )
            val thrown = scala.util.Try(AsyncInterop.fromCompletionStage(cf).block).failed.toOption
            // CompletableFuture wraps in CompletionException; interop unwraps it.
            assertTrue(thrown.contains(boom))
          } finally {
            ex.shutdown(); ex.awaitTermination(2, TimeUnit.SECONDS); ()
          }
        }
      },
      test("toCompletableFuture: ready value completes immediately") {
        ZIO.attemptBlocking {
          val cf = AsyncInterop.toCompletableFuture(Async.succeed(8))
          assertTrue(cf.isDone, cf.get() == 8)
        }
      },
      test("toCompletableFuture: failure completes exceptionally") {
        ZIO.attemptBlocking {
          val boom   = new RuntimeException("nope")
          val cf     = AsyncInterop.toCompletableFuture(Async.fail(boom))
          val thrown = scala.util.Try(cf.get()).failed.toOption
          assertTrue(thrown.exists {
            case ce: CompletionException => ce.getCause eq boom
            case ee: ExecutionException  => ee.getCause eq boom
            case other                   => other eq boom
          })
        }
      },
      test("toCompletableFuture: suspended pollable completes via the EC") {
        ZIO.attemptBlocking {
          val a = Async.promiseInternal[Int] { c =>
            val t = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(30); c.succeed(77) }
            })
            t.setDaemon(true)
            t.start()
          }
          val cf = AsyncInterop.toCompletableFuture(a)
          assertTrue(cf.get(2, TimeUnit.SECONDS) == 77)
        }
      },
      test("toCompletableFuture: suspended pollable that fails completes exceptionally via the EC") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late-cf-fail")
          val a    = Async.promiseInternal[Int] { c =>
            val t = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(30); c.fail(boom) }
            })
            t.setDaemon(true)
            t.start()
          }
          val cf     = AsyncInterop.toCompletableFuture(a)
          val thrown = scala.util.Try(cf.get(2, TimeUnit.SECONDS)).failed.toOption
          assertTrue(thrown.exists {
            case ce: CompletionException => ce.getCause eq boom
            case ee: ExecutionException  => ee.getCause eq boom
            case other                   => other eq boom
          })
        }
      }
    ),
    suite("null cause")(
      test("fromFuture_completedNullFail_eitherReifiesLeftNull") {
        val fa = AsyncInterop.fromFuture(Future.failed(null))
        assertTrue(fa.either.block == Left(null))
      },
      test("fromFuture_completedNullFail_toFuture_roundTripsNull") {
        val fa = AsyncInterop.fromFuture(Future.failed(null))
        for {
          result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa)).either
        } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
      },
      test("fromFuture_completedNullFail_toCompletableFuture_roundTripsNull") {
        val fa       = AsyncInterop.fromFuture(Future.failed(null))
        val cf       = AsyncInterop.toCompletableFuture(fa)
        val observed =
          try {
            cf.join()
            Right(None)
          } catch {
            case t: Throwable =>
              val raw = t match {
                case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
                case other                                                               => other
              }
              Left((raw match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
          }
        assertTrue(observed == Left(null))
      },
      test("fromFuture_valueAlreadyFailedWithNull_isNotConfusedWithPending") {
        val p = scala.concurrent.Promise[Int]()
        p.failure(null)
        val fa  = AsyncInterop.fromFuture(p.future)
        val any = fa.asInstanceOf[Any]
        assertTrue(
          any.isInstanceOf[Failure] || !any.isInstanceOf[Pollable[_]],
          fa.either.block == Left(null)
        )
      },
      test("either_pendingNullFail_reifiesLeftNull") {
        val (c, a) = AsyncTestSupport.pendingNullFail
        val ei     = a.either
        c.succeed(())
        assertTrue(ei.block == Left(null))
      },
      test("unsafeRunAsync_pendingNullFail_deliversLeftNull") {
        val (c, a)                      = AsyncTestSupport.pendingNullFail
        var out: Either[Throwable, Any] = null.asInstanceOf[Either[Throwable, Any]]
        AsyncTestSupport.startEither(a) { res => out = res }
        c.succeed(())
        // Spin until the background worker delivers (deterministic after complete).
        var spins = 0
        while (out == null && spins < 1000) {
          Thread.sleep(1)
          spins += 1
        }
        assertTrue(out == Left(null))
      },
      test("toFuture_pendingNullFail_failsWithNullCause") {
        val (c, a) = AsyncTestSupport.pendingNullFail
        c.succeed(())
        for {
          result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).either
        } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
      },
      test("toFuture_genuinelyPendingNullFail_awaitObservesNullCause") {
        // Twin of `toFuture_readyNullFail_awaitObservesNullCause` below, with
        // the Async genuinely pending when `toFuture` is called, so the failure
        // travels through the driven (executor) arm. That arm must use the same
        // marker transport as the ready arm (and as `toCompletableFuture` and
        // the JS driver): failing the promise with a raw null makes
        // `Await.result` throw a fabricated NullPointerException instead.
        val (c, fa) = AsyncTestSupport.pending[Int]
        val fut     = AsyncInterop.toFuture(fa)
        c.fail(null)
        val raw =
          try {
            Right(Await.result(fut, 5.seconds))
          } catch {
            case t: Throwable => Left((t match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
          }
        assertTrue(raw == Left(null))
      },
      test("toFuture_readyNullFail_awaitObservesNullCause") {
        val f   = AsyncInterop.toFuture(Async.fail(null))
        val raw =
          try {
            Right(Await.result(f, 1.second))
          } catch {
            case t: Throwable => Left((t match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
          }
        assertTrue(raw == Left(null))
      },
      test("toCompletableFuture_genuinelyPendingNullFail_joinObservesNullCause") {
        // Twin of `toFuture_genuinelyPendingNullFail_awaitObservesNullCause`:
        // the Async is genuinely pending when `toCompletableFuture` is called,
        // so the null cause travels through the driven (executor) arm, which
        // must use the same marker transport as the ready arm.
        val (c, fa) = AsyncTestSupport.pending[Int]
        val cf      = AsyncInterop.toCompletableFuture(fa)
        c.fail(null)
        val observed =
          try {
            cf.get(5, java.util.concurrent.TimeUnit.SECONDS)
            Right(None)
          } catch {
            case t: Throwable =>
              val raw = t match {
                case ee: java.util.concurrent.ExecutionException if ee.getCause ne null => ee.getCause
                case other                                                              => other
              }
              Left((raw match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
          }
        assertTrue(observed == Left(null))
      },
      test("toCompletableFuture_readyNullFail_joinObservesNullCause") {
        val cf       = AsyncInterop.toCompletableFuture(Async.fail(null))
        val observed =
          try {
            cf.join()
            Right(None)
          } catch {
            case t: Throwable =>
              val raw = t match {
                case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
                case other                                                               => other
              }
              Left((raw match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
          }
        assertTrue(observed == Left(null))
      },
      test("unsafeRunAsync_failNullCause_deliversLeftNull") {
        for {
          out <- ZIO.async[Any, Nothing, Either[Throwable, Any]] { cb =>
                   val _ = AsyncTestSupport.startEither(Async.fail(null)) { res =>
                     cb(ZIO.succeed(res))
                   }
                   ()
                 }
        } yield assertTrue(out == Left(null))
      }
    ),
    suite("WrappedPollable interop")(
      test("collectAll_succeedPollableElement_isNotSilentlyDriven") {
        val inner: Pollable[String] = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = Async.succeed("driven")
        }
        val r = Async.collectAll(List(Async.succeed(inner)))
        // collectAll fast-path misclassifies a ready Pollable as pending input.
        val observed = r.block.head.asInstanceOf[AnyRef]
        assertTrue(observed eq inner)
      },
      test("unsafeRunAsync_succeedPollable_deliversPollableNotDrivenValue") {
        val inner: Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
        }
        var out: Either[Throwable, Pollable[Int]] = Right(inner)
        val fa: Async[Pollable[Int]]              = Async.succeed(inner)
        AsyncTestSupport.startEither(fa) { res => out = res }
        assertTrue(out.toOption.exists((v: Pollable[Int]) => (v: AnyRef) eq inner))
      },
      test("either_succeedPollable_reifiesRightPollableNotDrivenValue") {
        val inner: Pollable[String] = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = Async.succeed("driven")
        }
        val observed = Async.succeed(inner).either.block.toOption.get.asInstanceOf[AnyRef]
        assertTrue(observed eq inner)
      },
      // Category E/L — fromCompletionStage must preserve null failure causes.
      suite("fromCompletionStage null-cause round-trip")(
        test("fromCompletionStage_completedNullFail_eitherReifiesLeftNull") {
          val cf = new CompletableFuture[Int]()
          cf.completeExceptionally(Failure.NullCauseMarker)
          val fa = AsyncInterop.fromCompletionStage(cf)
          assertTrue(fa.either.block == Left(null))
        },
        test("fromCompletionStage_completedNullFail_toCompletableFuture_roundTripsNull") {
          val cf = new CompletableFuture[Int]()
          cf.completeExceptionally(Failure.NullCauseMarker)
          val fa       = AsyncInterop.fromCompletionStage(cf)
          val out      = AsyncInterop.toCompletableFuture(fa)
          val observed =
            try {
              out.join()
              Right(None)
            } catch {
              case t: Throwable =>
                val raw = t match {
                  case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
                  case other                                                               => other
                }
                Left((raw match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
            }
          assertTrue(observed == Left(null))
        }
      ),
      // Category P — unsafeRunAsync cancel before pending completes suppresses callback.
      suite("unsafeRunAsync cancel races completion")(
        test("unsafeRunAsync_pending_cancelBeforeComplete_suppressesCallback") {
          ZIO.attemptBlocking {
            val c       = new Completer[Int]
            val invoked = new AtomicInteger(0)
            val start   = new CountDownLatch(1)
            val running = AsyncTestSupport.startTap(c.peek)(_ => invoked.incrementAndGet())
            start.countDown()
            running.cancel()
            // Give the worker a moment to observe cancellation.
            Thread.sleep(50)
            c.succeed(1)
            Thread.sleep(50)
            assertTrue(invoked.get() == 0)
          }
        }
      ),
      // Category P — unsafeRunAsync ready path callback is at-most-once.
      suite("unsafeRunAsync at-most-once callback")(
        test("unsafeRunAsync_readySuccess_callbackInvokedExactlyOnce") {
          val count = new AtomicInteger(0)
          AsyncTestSupport.startTap(Async.succeed(42))(_ => count.incrementAndGet())
          assertTrue(count.get() == 1)
        },
        test("unsafeRunAsync_readyNullFail_callbackInvokedExactlyOnceWithNull") {
          var out: Either[Throwable, Any] = Right(-1)
          val count                       = new AtomicInteger(0)
          AsyncTestSupport.startEither(Async.fail(null)) { res =>
            count.incrementAndGet()
            out = res
          }
          assertTrue(count.get() == 1, out == Left(null))
        }
      ),
      // Category L — toFuture pending null failure must not leak NullCauseMarker.
      suite("toFuture pending null-cause integrity")(
        test("toFuture_pendingNullFail_roundTripsNull") {
          val fa = Async.promiseInternal[Int](_.fail(null))
          for {
            result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa)).either
          } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
        }
      ),
      // CONVERGENCE — JVM interop locks.
      suite("CONVERGENCE: JVM pass-4 regression locks")(
        test("fromFuture_pendingNullFail_toFuture_roundTripsNull") {
          val p  = scala.concurrent.Promise[Int]()
          val fa = AsyncInterop.fromFuture(p.future)
          p.failure(null)
          for {
            result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa)).either
          } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
        }
      ),
      // Category E — fromCompletionStage ready null success round-trip.
      suite("fromCompletionStage null success")(
        test("fromCompletionStage_readyNullSuccess_preservesNull") {
          val cf = CompletableFuture.completedFuture(null.asInstanceOf[String])
          val fa = AsyncInterop.fromCompletionStage(cf)
          assertTrue(fa.block == null)
        },
        test("fromCompletionStage_readyNullSuccess_toCompletableFuture_roundTripsNull") {
          val cf  = CompletableFuture.completedFuture(null.asInstanceOf[String])
          val fa  = AsyncInterop.fromCompletionStage(cf)
          val out = AsyncInterop.toCompletableFuture(fa).join()
          assertTrue(out == null)
        }
      ),
      // Category E/L — toCompletableFuture ready null failure uses marker transport.
      suite("toCompletableFuture ready null-cause integrity")(
        test("toCompletableFuture_readyNullFail_observesNullCause") {
          val cf       = AsyncInterop.toCompletableFuture(Async.fail(null))
          val observed =
            try {
              cf.join()
              Right(None)
            } catch {
              case t: Throwable =>
                val raw = t match {
                  case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
                  case other                                                               => other
                }
                Left((raw match { case Failure.NullCauseMarker => null; case t: Throwable => t }))
            }
          assertTrue(observed == Left(null))
        }
      ),
      // Category P — unsafeRunAsync completion wins race over late cancel.
      suite("unsafeRunAsync completion wins cancel race")(
        test("unsafeRunAsync_readySuccess_cancelConcurrently_stillExactlyOnce") {
          val count   = new AtomicInteger(0)
          val running = AsyncTestSupport.startTap(Async.succeed(7))(_ => count.incrementAndGet())
          running.cancel()
          assertTrue(count.get() == 1)
        },
        test("unsafeRunAsync_pending_completeBeforeCancel_invokesCallback") {
          ZIO.attemptBlocking {
            val c       = new Completer[Int]
            val invoked = new AtomicInteger(0)
            val running = AsyncTestSupport.startTap(c.peek)(_ => invoked.incrementAndGet())
            c.succeed(99)
            Thread.sleep(100)
            running.cancel()
            assertTrue(invoked.get() == 1)
          }
        }
      ),
      // Category E — fromFuture already-completed null success.
      suite("fromFuture ready null success")(
        test("fromFuture_completedNullSuccess_preservesNull") {
          val p = Promise[String]()
          p.success(null)
          assertTrue(AsyncInterop.fromFuture(p.future).block == null)
        }
      ),
      // CONVERGENCE — JVM extended locks.
      suite("CONVERGENCE: JVM pass-4 extended regression locks")(
        test("toFuture_readyNullSuccess_deliversNull") {
          for {
            v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(Async.succeed(null: String)))
          } yield assertTrue(v == null)
        }
      ),
      // Category E/H — ready pollable-as-value must round-trip through JVM interop.
      suite("JVM interop pollable-as-value egress")(
        test("toFuture_succeedPollable_deliversPollableNotDrivenValue") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val f     = AsyncInterop.toFuture(Async.succeed(inner))
          val raw   = Await.result(f, 1.second).asInstanceOf[AnyRef]
          assertTrue(raw eq inner)
        },
        test("toCompletableFuture_succeedPollable_deliversPollableNotDrivenValue") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val cf    = AsyncInterop.toCompletableFuture(Async.succeed(inner))
          val raw   = cf.join().asInstanceOf[AnyRef]
          assertTrue(raw eq inner)
        },
        test("fromFuture_successPollable_toFuture_preservesPollableIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val fa    = AsyncInterop.fromFuture(Future.successful(inner))
          for {
            raw <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
          } yield assertTrue((raw: AnyRef) eq inner)
        },
        test("fromCompletionStage_successPollable_blockPreservesPollableIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val cf    = CompletableFuture.completedFuture(inner)
          val raw   = AsyncInterop.fromCompletionStage(cf).block.asInstanceOf[AnyRef]
          assertTrue(raw eq inner)
        }
      ),
      // Category E/H — pending interop path completing to pollable-as-value.
      suite("JVM interop pending pollable-as-value egress")(
        test("toFuture_pendingFlatMapSucceedPollable_deliversPollableIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val c                        = new Completer[Unit]
          val fa: Async[Pollable[Int]] =
            c.peek.flatMap(_ => Async.succeed(inner))
          val f = AsyncInterop.toFuture(fa)
          c.succeed(())
          for {
            raw <- ZIO.fromFuture(_ => f)
          } yield assertTrue((raw: AnyRef) eq inner)
        },
        test("toCompletableFuture_promiseSucceedPollable_deliversPollableIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner))
          val cf  = AsyncInterop.toCompletableFuture(fa)
          val raw = cf.join().asInstanceOf[AnyRef]
          assertTrue(raw eq inner)
        },
        test("toFuture_succeedStillPendingPollable_returnsWithoutBlockingCaller") {
          ZIO.attemptBlocking {
            // `toFuture` promises that a not-yet-complete input "is driven on
            // `ec`, so this call returns immediately". A success value that is
            // itself a still-pending pollable must be driven like any other
            // suspension (exactly as `Async.start` drives it on the background
            // worker) — not parked on the calling thread until the leaf settles.
            val c        = new Completer[Int]
            val returned = new java.util.concurrent.atomic.AtomicBoolean(false)
            val caller   = new Thread(new Runnable {
              def run(): Unit = {
                val _ = AsyncInterop.toFuture(Async.succeed(c: Pollable[Int]))
                returned.set(true)
              }
            })
            caller.setDaemon(true)
            caller.start()
            caller.join(2000) // bound the regression: a blocking toFuture parks here until the completer settles
            val returnedWhileInnerStillPending = returned.get()
            c.succeed(1) // un-wedge a parked caller so the suite does not leak the thread
            assertTrue(returnedWhileInnerStillPending)
          }
        },
        test("toCompletableFuture_succeedStillPendingPollable_returnsWithoutBlockingCaller") {
          ZIO.attemptBlocking {
            // Same non-blocking contract as toFuture (`toCompletableFuture`
            // "behaves like toFuture"): a pending pollable-as-value carrier must
            // not park the calling thread.
            val c        = new Completer[Int]
            val returned = new java.util.concurrent.atomic.AtomicBoolean(false)
            val caller   = new Thread(new Runnable {
              def run(): Unit = {
                val _ = AsyncInterop.toCompletableFuture(Async.succeed(c: Pollable[Int]))
                returned.set(true)
              }
            })
            caller.setDaemon(true)
            caller.start()
            caller.join(2000)
            val returnedWhileInnerStillPending = returned.get()
            c.succeed(1)
            assertTrue(returnedWhileInnerStillPending)
          }
        }
      ),
      // CONVERGENCE — JVM pass-9 interop regression locks.
      suite("CONVERGENCE: JVM pass-9 interop regression locks")(
        test("toFuture_succeedPollable_eitherChannelAgreesWithBlock") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] = Async.succeed(inner)
          for {
            fromFuture <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
          } yield assertTrue((fromFuture: AnyRef) eq inner, fa.block eq inner)
        }
      ),
      // Category P — nested macro await with zipWith pollable terminal unwrap.
      suite("Async.async nested await combinator chains")(
        test("asyncAwait_zipWithPendingRightPollable_preservesPollableIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Unit]
          val fa    =
            Async.async {
              val right = c.peek.flatMap(_ => Async.succeed(inner))
              Async.succeed(()).await
              right.await
            }

          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("asyncAwait_pendingLeftPollableChain_zipWithReadyRight_preservesTupleIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Unit]
          val fa    =
            Async.async {
              val left = c.peek.flatMap(_ => Async.succeed(inner))
              val pair = left.zipWith(Async.succeed(7))((p, n) => (p, n))
              pair.await
            }
          c.succeed(())
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, 7))
        }
      ),
      // CONVERGENCE — JVM pass-10 macro regression locks.
      suite("CONVERGENCE: JVM pass-10 macro regression locks")(
        test("asyncAwait_foldCausePendingPollable_onSuccess_preservesPollableIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Unit]
          val fa    =
            Async.async {
              val left =
                c.peek
                  .flatMap(_ => Async.succeed(inner))
                  .foldCause(_ => AsyncTestSupport.pollableSuccessValue)(p => p)
              left.await
            }

          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      )
    )
  )
}
