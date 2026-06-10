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

import zio.{Chunk, Task, ZIO}
import zio.test._
import zio.test.Assertion._

import scala.util.Try

/**
 * Scala.js interop.
 */
object AsyncInteropSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncInteropSpec")(
    suite("Promise")(
      suite("Future ↔ Async")(
            test("fromFuture: already-succeeded collapses to a value") {
              val r = AsyncInterop.fromFuture(Future.successful(7)).block
              assertTrue(r == 7)
            },
            test("fromFuture: already-failed collapses to a fail") {
              val boom = AsyncTestSupport.boom
              val r      = AsyncInterop.fromFuture(Future.failed(boom))
              val thrown = scala.util.Try(r.block).failed.toOption
              assertTrue(thrown.contains(boom))
            },
            test("toFuture: ready value collapses to Future.successful") {
              ZIO.fromFuture(_ => AsyncInterop.toFuture(Async.succeed(3))).map(v => assertTrue(v == 3))
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
            test("toFuture: pending pollable resolves via microtask polling") {
              // Create a Completer that fires from a queued microtask so it does
              // NOT complete inside the promise body — the pollable path is taken.
              val a = Async.promiseInternal[Int] { c =>
                js.timers.setTimeout(0.0)(c.succeed(99))
                ()
              }
              ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).map(v => assertTrue(v == 99))
            },
            test("toFuture: driver advances to the pollable returned by poll (not re-polling the AsyncTestSupport.original)") {
              ZIO.fromFuture(_ => AsyncInterop.toFuture(new StepChain(5, 0))).map(v => assertTrue(v == 5))
            },
            test("toFuture: a multi-wake pollable resolves once and is not re-polled after completion") {
              val p = new DoubleWake
              for {
                v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(p))
                // Let any stray resumption microtasks drain before reading the count.
                _ <- Live.live(zio.ZIO.sleep(zio.Duration.fromMillis(50)))
              } yield assertTrue(v == 42, p.polls == 2)
            }
          ),
          suite("js.Promise ↔ Async")(
            test("fromJsPromise: resolved promise yields the value") {
              val pr: js.Promise[Int] = js.Promise.resolve[Int](42)
              ZIO
                .fromFuture(_ => AsyncInterop.toFuture(AsyncInterop.fromJsPromise(pr)))
                .map(v => assertTrue(v == 42))
            },
            test("toJsPromise: ready value resolves the JS promise") {
              val jp = AsyncInterop.toJsPromise(Async.succeed("ok"))
              ZIO.fromFuture(_ => jp.toFuture).map(v => assertTrue(v == "ok"))
            },
            test("toJsPromise: failure rejects the JS promise") {
              val boom = new RuntimeException("rej")
              val jp   = AsyncInterop.toJsPromise(Async.fail(boom))
              ZIO
                .fromFuture(_ => jp.toFuture)
                .either
                .map {
                  case Left(t)  => assertTrue(t eq boom)
                  case Right(_) => assertTrue(false)
                }
            }
          )
    ),
    suite("null cause")(
      test("fromJsPromise_rejectedNull_eitherChannelReifiesLeftNull") {
            val p  = js.Promise.reject(null).asInstanceOf[js.Promise[Int]]
            val fa = AsyncInterop.fromJsPromise(p)
            var out: Either[Throwable, Either[Throwable, Int]] = Right(Right(-1))
            AsyncTestSupport.startEither(fa.either)(res => out = res)
            for {
              _ <- drain
            } yield assertTrue(out == Right(Left(null)))
          },
          test("fromJsPromise_rejectedNull_matchesFailNullEither") {
            val p     = js.Promise.reject(null).asInstanceOf[js.Promise[Int]]
            val fromP = AsyncInterop.fromJsPromise(p).either
            val ref   = Async.fail(null).either
            for {
              a <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fromP))
              b <- ZIO.fromFuture(_ => AsyncInterop.toFuture(ref))
              _ <- drain
            } yield assertTrue(a == b)
          }
      ,
      test("unsafeRunAsync_pendingNullFail_deliversLeftNull") {
            val (c, a) = AsyncTestSupport.pendingNullFail
            for {
              out <- ZIO.async[Any, Nothing, Either[Throwable, Unit]] { k =>
                       AsyncTestSupport.startEither(a)(res => k(ZIO.succeed(res)))
                       c.succeed(())
                     }
            } yield assertTrue(out == Left(null))
          },
          test("toFuture_pendingNullFail_failsWithNullCause") {
            val (c, a) = AsyncTestSupport.pendingNullFail
            c.succeed(())
            for {
              result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(a)(queue)).either
            } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
          }
      ,
      test("unsafeRunAsync_pollThrowsNullCauseMarker_deliversLeftNull") {
            for {
              out <- ZIO.async[Any, Nothing, Either[Throwable, Unit]] { k =>
                       AsyncTestSupport.startEither(markerThrowingPollable)(res => k(ZIO.succeed(res)))
                     }
              _ <- Live.live(ZIO.sleep(50.millis))
            } yield assertTrue(out == Left(null))
          },
          test("toFuture_pollThrowsNullCauseMarker_failsWithNullCause") {
            for {
              result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(markerThrowingPollable)(queue)).either
              _      <- Live.live(ZIO.sleep(50.millis))
            } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
          }
      ,
      test("toFuture_readyNullFail_observesNullCause") {
            for {
              result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(Async.fail(null))(queue)).either
              _      <- Live.live(ZIO.sleep(10.millis))
            } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
          }
    ),
    suite("WrappedPollable interop")(
      test("unsafeRunAsync_pollThrowsNullCauseMarker_deliversLeftNull") {
            for {
              out <- ZIO.async[Any, Nothing, Either[Throwable, Unit]] { k =>
                       AsyncTestSupport.startEither(markerThrowingPollable)(res => k(ZIO.succeed(res)))
                     }
              _ <- Live.live(ZIO.sleep(50.millis))
            } yield assertTrue(out == Left(null))
          },
          test("unsafeRunAsync_failNullCause_deliversLeftNull") {
            for {
              out <- ZIO.async[Any, Nothing, Either[Throwable, Any]] { k =>
                       val _ = AsyncTestSupport.startEither(Async.fail(null)) { res => k(ZIO.succeed(res)) }
                     }
              _ <- Live.live(ZIO.sleep(10.millis))
            } yield assertTrue(out == Left(null))
          }
      ,
      // Category E/L — fromJsPromise must preserve null failure causes.
          suite("fromJsPromise null-cause round-trip")(
            test("fromJsPromise_rejectedNull_eitherReifiesLeftNull") {
              val p = js.Promise.reject[Throwable, Int](null)
              val fa = AsyncInterop.fromJsPromise(p)
              assertTrue(fa.either.block == Left(null))
            },
            test("fromJsPromise_rejectedNull_toFuture_roundTripsNull") {
              val p = js.Promise.reject[Throwable, Int](null)
              val fa = AsyncInterop.fromJsPromise(p)
              for {
                result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa)).either
                _      <- drain
              } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
            }
          ),
          // Category P — unsafeRunAsync cancel on pending suppresses callback.
          suite("unsafeRunAsync cancel on pending")(
            test("unsafeRunAsync_pending_cancelBeforeComplete_suppressesCallback") {
              val c       = new Completer[Int]
              var invoked = false
              val running  = AsyncTestSupport.startTap(c.peek)(_ => invoked = true)
              running.cancel()
              c.succeed(1)
              for {
                _ <- drain
              } yield assertTrue(!invoked)
            }
          ),
          // Category L — toJsPromise pending null failure must not leak marker.
          suite("toJsPromise null-cause integrity")(
            test("toJsPromise_pendingNullFail_rejectsWithNull") {
              val fa = Async.promiseInternal[Int](_.fail(null))
              val p  = AsyncInterop.toJsPromise(fa)
              for {
                result <- ZIO.async[Any, Throwable, Either[Throwable, Int]] { cb =>
                  p.`then`[Unit](_ => cb(ZIO.succeed(Right(-1))))(
                    e => cb(ZIO.succeed(Left(e.asInstanceOf[Throwable])))
                  )
                }
                _ <- drain
              } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
            }
          ),
          // Category K — resumption poll throw on JS driver (second microtask).
          suite("JS driver resumption poll throw")(
            test("unsafeRunAsync_resumePollThrow_deliversFailure") {
              val boom = new RuntimeException("resume-boom")
              val pa = new Pollable[Int] {
                private var polls = 0
                def poll(onComplete: Runnable): Async[Int] = {
                  polls += 1
                  if (polls == 1) { onComplete.run(); this }
                  else throw boom
                }
              }
              var out: Either[Throwable, Int] = Right(-1)
              AsyncTestSupport.startEither(pa){ res => out = res }
              for {
                _ <- drain
              } yield assertTrue(out == Left(boom))
            }
          ),
          // CONVERGENCE — JS interop locks.
          suite("CONVERGENCE: JS pass-4 regression locks")(
            test("fromFuture_completedNullFail_toFuture_roundTripsNull") {
              val p = Promise[Int]()
              p.failure(null)
              val fa = AsyncInterop.fromFuture(p.future)
              for {
                result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa)).either
                _      <- drain
              } yield assertTrue(AsyncTestSupport.unwindFutureEither(result) == Left(null))
            },
            test("unsafeRunAsync_readySuccess_callbackInvokedExactlyOnce") {
              var count = 0
              AsyncTestSupport.startTap(Async.succeed(42))(_ => count += 1)
              assertTrue(count == 1)
            }
          )
      ,
      // Category E — fromJsPromise ready null success.
          suite("fromJsPromise null success")(
            test("fromJsPromise_resolvedNull_preservesNull") {
              val p = js.Promise.resolve[String](null)
              val fa = AsyncInterop.fromJsPromise(p)
              for {
                v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
                _ <- drain
              } yield assertTrue(v == null)
            }
          ),
          // Category P — unsafeRunAsync completion before cancel still delivers once.
          suite("unsafeRunAsync completion wins cancel race")(
            test("unsafeRunAsync_pending_completeBeforeCancel_invokesCallback") {
              val c       = new Completer[Int]
              var invoked = false
              val running  = AsyncTestSupport.startTap(c.peek)(_ => invoked = true)
              c.succeed(7)
              for {
                _ <- drain
                _ <- ZIO.succeed(running.cancel())
                _ <- drain
              } yield assertTrue(invoked)
            }
          ),
          // Category L — drive() initial poll throw on pending (first microtask).
          suite("JS drive initial poll throw")(
            test("toFuture_pendingInitialPollThrow_deliversFailure") {
              val boom = new RuntimeException("initial-poll-boom")
              val pa = new Pollable[Int] {
                def poll(onComplete: Runnable): Async[Int] = throw boom
              }
              for {
                result <- ZIO.fromFuture(_ => AsyncInterop.toFuture(pa)).either
                _      <- drain
              } yield assertTrue(result == Left(boom))
            }
          ),
          // Category E — fromFuture completed null success.
          suite("fromFuture ready null success")(
            test("fromFuture_completedNullSuccess_preservesNull") {
              val p = Promise[String]()
              p.success(null)
              for {
                v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(AsyncInterop.fromFuture(p.future)))
                _ <- drain
              } yield assertTrue(v == null)
            }
          ),
          // CONVERGENCE — JS extended locks.
          suite("CONVERGENCE: JS pass-4 extended regression locks")(
            test("toFuture_readyNullSuccess_deliversNull") {
              for {
                v <- ZIO.fromFuture(_ => AsyncInterop.toFuture(Async.succeed(null: String)))
                _ <- drain
              } yield assertTrue(v == null)
            }
          )
      ,
      // Category E/H — ready pollable-as-value must round-trip through JS interop.
          suite("JS interop pollable-as-value egress")(
            test("toFuture_succeedPollable_deliversPollableNotDrivenValue") {
              val inner = AsyncTestSupport.pollableSuccessValue
              val fa: Async[Pollable[Int]] = Async.succeed(inner)
              for {
                raw <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
                _   <- drain
              } yield assertTrue((raw: AnyRef) eq inner)
            },
            test("fromJsPromise_resolvedPollable_preservesPollableIdentity") {
              val inner = AsyncTestSupport.pollableSuccessValue
              val p     = js.Promise.resolve(inner).asInstanceOf[js.Promise[Pollable[Int]]]
              val fa    = AsyncInterop.fromJsPromise(p)
              for {
                raw <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
                _   <- drain
              } yield assertTrue((raw: AnyRef) eq inner)
            },
            test("fromFuture_successPollable_toFuture_preservesPollableIdentity") {
              val inner = AsyncTestSupport.pollableSuccessValue
              val p     = Promise[Pollable[Int]]()
              p.success(inner)
              val fa = AsyncInterop.fromFuture(p.future)
              for {
                raw <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
                _   <- drain
              } yield assertTrue((raw: AnyRef) eq inner)
            }
          ),
          // Category E/H — pending interop path completing to pollable-as-value.
          suite("JS interop pending pollable-as-value egress")(
            test("toFuture_pendingFlatMapSucceedPollable_deliversPollableIdentity") {
              val inner = AsyncTestSupport.pollableSuccessValue
              val c     = new Completer[Unit]
              val fa: Async[Pollable[Int]] =
                c.peek.flatMap(_ => Async.succeed(inner))
              val f = AsyncInterop.toFuture(fa)
              c.succeed(())
              for {
                raw <- ZIO.fromFuture(_ => f)
                _   <- drain
              } yield assertTrue((raw: AnyRef) eq inner)
            },
            test("unsafeRunAsync_promiseSucceedPollable_deliversPollableIdentity") {
              val inner = AsyncTestSupport.pollableSuccessValue
              var out: Either[Throwable, Pollable[Int]] = Right(inner)
              val fa: Async[Pollable[Int]] =
                Async.promiseInternal[Pollable[Int]](_.succeed(inner))
              AsyncTestSupport.startEither(fa)(res => out = res)
              for {
                _ <- drain
              } yield assertTrue(out.isRight, out.toOption.exists((v: Pollable[Int]) => (v: AnyRef) eq inner))
            }
          ),
          // CONVERGENCE — JS pass-9 interop regression locks.
          suite("CONVERGENCE: JS pass-9 interop regression locks")(
            test("toFuture_succeedPollable_eitherChannelAgreesWithBlock") {
              val inner = AsyncTestSupport.pollableSuccessValue
              val fa: Async[Pollable[Int]] = Async.succeed(inner)
              for {
                fromFuture <- ZIO.fromFuture(_ => AsyncInterop.toFuture(fa))
                _          <- drain
              } yield assertTrue((fromFuture: AnyRef) eq inner, fa.block eq inner)
            }
          )
    )
  )
}
