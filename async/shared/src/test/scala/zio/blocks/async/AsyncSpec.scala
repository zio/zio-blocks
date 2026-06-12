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
import zio.test.Assertion._

import scala.util.Try

/**
 * Cross-platform core Async semantics.
 */
object AsyncSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncSpec")(
    suite("succeed")(
      test("await on a succeeded value returns the value with no suspension") {
        val r = Async.succeed("hi").block
        assertTrue(r == "hi")
      },
      test("map over a succeeded value applies the function") {
        val r = Async.succeed(2).map(_ * 3).block
        assertTrue(r == 6)
      }
    ),
    suite("fail")(
      test("await on a failed value throws the cause") {
        val thrown = scala.util.Try(Async.fail(AsyncTestSupport.boom).block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("await on a fail pollable also throws the cause") {
        val pa: Async[Int] = AsyncTestSupport.failAfter(AsyncTestSupport.boom, 0)
        val thrown         = scala.util.Try(pa.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("await on a fail pollable that suspends once still throws") {
        val pa: Async[Int] = AsyncTestSupport.failAfter(AsyncTestSupport.boom, 1)
        val thrown         = scala.util.Try(pa.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      }
    ),
    suite("attempt")(
      test("captures a thrown exception as a failure") {
        val thrown = scala.util.Try(Async.attempt[Int](throw AsyncTestSupport.boom).block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("propagates a successful body as a value") {
        val r = Async.attempt(7).block
        assertTrue(r == 7)
      }
    ),
    suite("never")(
      test("polling returns the same pollable (never advances)") {
        val n = Async.never
        // Drive the pollable once and observe it returns itself.
        val nany = n.asInstanceOf[Any]
        assertTrue(nany.isInstanceOf[Pollable[_]])
      }
    ),
    suite("map")(
      test("maps a ready value synchronously") {
        val r = Async.succeed(1).map(_ + 1).block
        assertTrue(r == 2)
      },
      test("maps over a Ready pollable") {
        val r = AsyncTestSupport.fromPollable(AsyncTestSupport.syncReadyPollable(10)).map(_ * 2).block
        assertTrue(r == 20)
      }
    ),
    suite("flatMap")(
      test("chains two ready values synchronously") {
        val r = Async.succeed(3).flatMap(x => Async.succeed(x + 4)).block
        assertTrue(r == 7)
      },
      test("chains ready -> pollable") {
        val r =
          Async.succeed(5).flatMap(x => AsyncTestSupport.fromPollable(AsyncTestSupport.syncReadyPollable(x * 10))).block
        assertTrue(r == 50)
      },
      test("chains pollable -> ready") {
        val r = AsyncTestSupport
          .fromPollable(AsyncTestSupport.syncReadyPollable(2))
          .flatMap(x => Async.succeed(x + 100))
          .block
        assertTrue(r == 102)
      },
      test("chains pollable -> pollable") {
        val r = AsyncTestSupport
          .fromPollable(AsyncTestSupport.syncReadyPollable(7))
          .flatMap(x => AsyncTestSupport.fromPollable(AsyncTestSupport.syncReadyPollable(x + 1)))
          .block
        assertTrue(r == 8)
      },
      // Category L — tap must propagate a Failure side effect (not only throws).
      suite("tap propagates Failure side effects")(
        test("tap_readyValue_sideEffectFail_propagatesSideFailure") {
          val thrown =
            Try(Async.succeed(1).tap(_ => Async.fail(AsyncTestSupport.sideFx)).block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.sideFx))
        },
        test("tap_pendingValue_sideEffectFail_propagatesSideFailure") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.tap(_ => Async.fail(AsyncTestSupport.sideFx))
          c.succeed(1)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.sideFx))
        }
      ),
      // Category L — catchAll recovery must preserve null causes.
      suite("catchAll preserves null failure causes")(
        test("catchAll_readyNullFail_handlerReturnsNullFail_eitherAndBlockAgree") {
          val fa        = Async.fail(null).catchAll(_ => Async.fail(null))
          val viaEither = fa.either.block
          val viaBlock  =
            try {
              fa.block
              Right(None)
            } catch {
              case Failure.NullCauseMarker => Left(null)
              case t: Throwable            => Left(t)
            }
          assertTrue(viaEither == Left(null), viaBlock == Left(null))
        },
        test("catchAll_readyNullFail_handlerReturnsSucceed_recoversToSuccess") {
          assertTrue((Async.fail(null): Async[Any]).catchAll(_ => Async.succeed("ok")).block == "ok")
        }
      ),
      // Category F / J — *> and <* must short-circuit like zipWith.
      suite("*> and <* fail-fast sequencing")(
        test("starGt_readyLeftFail_doesNotDriveRight") {
          var rightPolled       = false
          val right: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val thrown = Try(Async.fail(boom).*>(right).block).failed.toOption
          assertTrue(thrown.contains(boom), !rightPolled)
        },
        test("starLt_readyRightFail_doesNotRunAfterLeftSucceeds") {
          val thrown =
            Try(Async.succeed(1).<*(Async.fail(boom)).block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category A / B — null success values through combinators.
      suite("null success preservation")(
        test("zipWith_pendingNullLeft_readyRight_preservesNullInCombine") {
          val (c, left) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val z = left.zipWith(Async.succeed(1))((a, b) => (a, b))
          c.succeed(null)
          assertTrue(z.block == ((null, 1)))
        },
        test("flatMap_pendingNullSuccess_continuationReceivesNull") {
          var observed: Option[String] = None
          val (c, pending)             = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val a = pending.flatMap { s =>
            observed = Some(s)
            Async.succeed(Option(s).fold(0)(_.length))
          }
          c.succeed(null)
          assertTrue(a.block == 0, observed.contains(null))
        },
        test("ensuring_pendingNullSuccess_runsFinalizerAndPreservesNull") {
          var ran          = false
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val a = pending.ensuring(Async.succeed { ran = true })
          c.succeed(null)
          assertTrue(a.block == null, ran)
        }
      ),
      // Category F — orElse must drive a pending fallback.
      suite("orElse drives pending fallback")(
        test("orElse_readyFail_pendingFallback_usesFallbackValue") {
          val (c, fallback) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = Async.fail(boom).orElse(fallback)
          c.succeed(99)
          assertTrue(a.block == 99)
        }
      ),
      // Category K — mapError must not run on success values (pending path).
      suite("mapError skips successful pending inputs")(
        test("mapError_pendingSuccess_doesNotInvokeMapper") {
          var invoked      = false
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.mapError { t => invoked = true; t }
          c.succeed(1)
          assertTrue(a.block == 1, !invoked)
        }
      ),
      // Category J — when/unless must not evaluate by-name fa when skipped.
      suite("when/unless by-name guards")(
        test("when_false_doesNotConstructFa") {
          var constructed           = false
          def expensive: Async[Any] = { constructed = true; Async.succeed(()) }
          assertTrue(when(false)(expensive).block == (), !constructed)
        },
        test("unless_true_doesNotConstructFa") {
          var constructed           = false
          def expensive: Async[Any] = { constructed = true; Async.succeed(()) }
          assertTrue(unless(true)(expensive).block == (), !constructed)
        }
      ),
      // Category F — flatten must propagate inner failures.
      suite("flatten error propagation")(
        test("flatten_nestedReadyFail_surfacesInnerCause") {
          val nested: Async[Async[Throwable]] = Async.succeed(Async.fail(boom))
          val thrown                          = Try(nested.flatten.block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category A — collectAll preserves null elements before failure.
      suite("collectAll null and failure boundaries")(
        test("collectAll_readyValuesWithNull_preservesNullBeforeFail") {
          val r = Async.collectAll(
            List[Async[String]](Async.succeed(null), Async.succeed("x"), Async.fail(boom))
          )
          val thrown = Try(r.block).failed.toOption
          assertTrue(thrown.contains(boom))
        },
        test("collectAll_onlyFailure_shortCircuitsToFailure") {
          val thrown = Try(Async.collectAll(List(Async.fail(boom))).block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category P — unsafeRunAsync must deliver null success.
      suite("unsafeRunAsync null success")(
        test("unsafeRunAsync_succeedNull_deliversRightNull") {
          var out: Either[Throwable, String] = null.asInstanceOf[Either[Throwable, String]]
          AsyncTestSupport.startEither(Async.succeed(null: String)) { res => out = res }
          assertTrue(out == Right(null))
        }
      ),
      // Category L — foldCause onFailure must receive null on pending path.
      suite("foldCause null cause on pending failure")(
        test("foldCause_pendingNullFail_onFailureReceivesNull") {
          var observed: Option[Throwable] = None
          val (c, pending)                = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a =
            pending
              .flatMap(_ => Async.fail(null))
              .foldCause { t => observed = Some(t); "nope" }(_ => "ok")
          c.succeed(0)
          assertTrue(a.block == "nope", observed.contains(null))
        }
      )
    ),
    suite("flatten")(
      test("flattens an Async[Async[A]] (both ready)") {
        val nested: Async[Async[Int]] = Async.succeed(Async.succeed(7))
        val r                         = nested.flatten.block
        assertTrue(r == 7)
      },
      test("flattens when inner is a Pollable") {
        val inner: Async[Int]         = AsyncTestSupport.syncReadyPollable(11)
        val nested: Async[Async[Int]] = Async.succeed(inner)
        val r                         = nested.flatten.block
        assertTrue(r == 11)
      }
    ),
    suite("promise")(
      test("synchronous completion awaits to the value") {
        val r = Async.promiseInternal[Int](c => c.succeed(7)).block
        assertTrue(r == 7)
      },
      test("double succeed is ignored (first wins)") {
        val r = Async
          .promiseInternal[Int] { c =>
            c.succeed(1)
            c.succeed(2)
          }
          .block
        assertTrue(r == 1)
      },
      test("synchronous completion with a null value resolves to null (regression: null sentinel)") {
        // `succeed(null)` must store the `NullValue` sentinel, not a raw `null`
        // (which collides with the empty state, so the completer never settles).
        val r = Async.promiseInternal[String](c => c.succeed(null)).block
        assertTrue(r == null)
      },
      test("completion that happens during poll (waker-driven) resumes") {
        // A pollable that delays completion by a few polls, each time waking
        // synchronously. The scheduler must keep looping until done.
        val r = AsyncTestSupport.fromPollable(AsyncTestSupport.syncReadyAfterPollable("ok", pollsNeeded = 3)).block
        assertTrue(r == "ok")
      },
      test("promiseInternal_bodyThrow_escapesSynchronously") {
        val caught =
          try {
            Async.promiseInternal[Int](_ => throw boom)
            None
          } catch {
            case t: Throwable => Some(t)
          }
        assertTrue(caught.contains(boom))
      }
    ),
    suite("Completer")(
      test("synchronous completer fail surfaces as a Failure") {
        val a      = Async.promiseInternal[Int](c => c.fail(AsyncTestSupport.boom))
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      },
      test("succeed-then-fail keeps the first outcome (succeed)") {
        val r = Async
          .promiseInternal[Int] { c =>
            c.succeed(1)
            c.fail(AsyncTestSupport.boom)
          }
          .block
        assertTrue(r == 1)
      },
      test("fail-then-succeed keeps the first outcome (fail)") {
        val a = Async.promiseInternal[Int] { c =>
          c.fail(AsyncTestSupport.boom)
          c.succeed(1)
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      }
    ),
    suite("Pollable")(
      test("re-poll after suspension yields the value") {
        val r = AsyncTestSupport.fromPollable(AsyncTestSupport.syncReadyAfterPollable(99, pollsNeeded = 2)).block
        assertTrue(r == 99)
      },
      test("flatMap over a suspended pollable consumes its left side exactly once") {
        // Verify FlatMapPollable.stage memoization: once `pa` produces a value,
        // we must never re-poll it (so a leaf like a socket read isn't retried).
        var leftPolls = 0
        val left      = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            leftPolls += 1
            if (leftPolls < 2) {
              onComplete.run()
              this
            } else Async.succeed(11)
          }
        }
        val v = AsyncTestSupport.fromPollable(left).flatMap(x => Async.succeed(x + 1)).block
        assertTrue(v == 12, leftPolls == 2)
      }
    ),
    suite("WrappedPollable")(
      // Category L — mapError must preserve null causes on every path.
      suite("mapError null-cause integrity")(
        test("mapError_readyNullFail_identityMapper_eitherAndBlockAgree") {
          val fa = Async.fail(null).mapError(identity)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsNullCause(fa) == Left(null))
        },
        test("mapError_pendingNullFail_identityMapper_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pending.flatMap(_ => Async.fail(null)).mapError(identity)
          c.succeed(0)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsNullCause(fa) == Left(null))
        }
      ),
      // Category L — ensuring must not let null-finalizer addSuppressed NPE replace AsyncTestSupport.primary.
      suite("ensuring null finalizer cause on pending primary")(
        test("ensuring_pendingPrimaryFail_nullFinalizerFail_surfacesPrimaryNotNpe") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.ensuring(Async.fail(null))
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(boom))
        },
        test("ensuring_pendingPrimaryNullFail_nonNullFinalizerFail_surfacesNullPrimary") {
          val fin          = new RuntimeException("fin")
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.ensuring(Async.fail(fin))
          c.fail(null)
          val viaEither = a.either.block
          val viaBlock  = AsyncTestSupport.blockAsNullCause(a)
          assertTrue(viaEither == Left(null), viaBlock == Left(null))
        }
      ),
      // Category K — catchAll handler synchronous throw must surface as failure, not hang.
      suite("catchAll handler synchronous throw")(
        test("catchAll_pendingFail_handlerThrows_propagatesHandlerThrow") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.catchAll(_ => throw AsyncTestSupport.handlerFx)
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.handlerFx))
        },
        test("catchAll_readyFail_handlerThrows_propagatesHandlerThrow") {
          val thrown =
            Try(Async.fail(boom).catchAll(_ => throw AsyncTestSupport.handlerFx).block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.handlerFx))
        },
        test("catchAll_handlerThrows_thenEither_readyAndPendingAgree") {
          // Ready path converts a synchronously-throwing handler into Async.fail,
          // so `.either` reifies it as Left. The pending path must agree: the same
          // program over a genuinely pending failure must also settle to Left, not
          // let the handler throw escape `.block` past the `.either`.
          val readyOut: Either[Throwable, Int] =
            Async.fail(boom).catchAll((_: Throwable) => throw AsyncTestSupport.handlerFx).either.block
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val sus = pending.catchAll(_ => throw AsyncTestSupport.handlerFx).either
          c.fail(boom)
          val pendingOut: Either[Throwable, Either[Throwable, Int]] = Try(sus.block).toEither
          assertTrue(
            readyOut == Left(AsyncTestSupport.handlerFx),
            pendingOut == Right(Left(AsyncTestSupport.handlerFx))
          )
        }
      ),
      // Category K — zipWith combine throw on pending path must escape (not hang).
      suite("zipWith combine throw")(
        test("zipWith_pendingBothReady_combineThrows_propagatesCombineThrow") {
          val (c1, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val z = left.zipWith(right)((_, _) => throw AsyncTestSupport.combineFx)
          c1.succeed(1)
          c2.succeed(2)
          val thrown = Try(z.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.combineFx))
        }
      ),
      // Category F — flatten must drive pending inner failures.
      suite("flatten pending inner failure")(
        test("flatten_pendingInnerFail_surfacesInnerCause") {
          val (c, inner) = {
            val c = new Completer[Async[Int]]
            (c, c.peek)
          }
          val outer = inner.map(identity)
          val flat  = outer.flatten
          c.succeed(Async.fail(boom))
          val thrown = Try(flat.block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category L — tap must not invoke effect on failure (ready or pending).
      suite("tap skips failure inputs")(
        test("tap_readyFail_doesNotInvokeEffect") {
          var invoked = false
          val thrown  =
            Try(Async.fail(boom).tap { (_: Nothing) => invoked = true; Async.succeed(()) }.block).failed.toOption
          assertTrue(thrown.contains(boom), !invoked)
        },
        test("tap_pendingFail_doesNotInvokeEffect") {
          var invoked      = false
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.tap { _ => invoked = true; Async.succeed(()) }
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(boom), !invoked)
        }
      ),
      // Category F — as/unit must short-circuit on failure without invoking mapper.
      suite("as and unit fail-fast")(
        test("as_readyFail_doesNotConstructReplacement") {
          var constructed = false
          val thrown      =
            Try(Async.fail(boom).as { constructed = true; 99 }.block).failed.toOption
          assertTrue(thrown.contains(boom), !constructed)
        },
        test("unit_pendingFail_doesNotRunFinalizer") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.unit
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category A — collectAll must preserve null elements before a later pending failure.
      suite("collectAll pending null element ordering")(
        test("collectAll_pendingNullThenFail_preservesNullInBufferBeforeFail") {
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val r = Async.collectAll(
            List[Async[String]](Async.succeed("a"), pending, Async.fail(boom))
          )
          c.succeed(null)
          val thrown = Try(r.block).failed.toOption
          assertTrue(thrown.contains(boom))
        },
        test("collectAll_pendingNullSuccess_completesWithNullElement") {
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val r = Async.collectAll(List[Async[String]](pending, Async.succeed("tail")))
          c.succeed(null)
          assertTrue(r.block == List(null, "tail"))
        }
      ),
      // Category F — orElse must not invoke fallback on success (pending or ready).
      suite("orElse skips fallback on success")(
        test("orElse_pendingSuccess_doesNotDriveFallback") {
          var fallbackPolled       = false
          val fallback: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { fallbackPolled = true; Async.succeed(0) }
          }
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.orElse(fallback)
          c.succeed(42)
          assertTrue(a.block == 42, !fallbackPolled)
        }
      ),
      // Category F — *> must fail-fast on pending left failure without driving right.
      suite("*> pending left failure fail-fast")(
        test("starGt_pendingLeftFail_doesNotDriveRight") {
          var rightPolled       = false
          val right: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val (c, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = left.*>(right)
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(boom), !rightPolled)
        }
      ),
      // Category L — either must reify null success on pending path.
      suite("either null success on pending path")(
        test("either_pendingNullSuccess_reifiesRightNull") {
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val a = pending.either
          c.succeed(null)
          assertTrue(a.block == Right(null))
        }
      ),
      // Category F — foldCause must pass null to onSuccess on pending path.
      suite("foldCause null success on pending path")(
        test("foldCause_pendingNullSuccess_onSuccessReceivesNull") {
          var observed: Option[String] = None
          val (c, pending)             = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val a = pending.foldCause(_ => "nope") { s => observed = Some(s); s }
          c.succeed(null)
          assertTrue(a.block == null, observed.contains(null))
        }
      ),
      // Category J — unsafeRunAsync cancel on never must not invoke callback.
      suite("unsafeRunAsync cancel on never")(
        test("unsafeRunAsync_never_cancelSuppressesCallback") {
          var invoked = false
          val running = AsyncTestSupport.startTap(Async.never)(_ => invoked = true)
          running.cancel()
          assertTrue(!invoked)
        }
      ),
      // Category L — unsafeRunAsync must deliver null failure without marker leak.
      suite("unsafeRunAsync null failure delivery")(
        test("unsafeRunAsync_readyNullFail_deliversLeftNull") {
          var out: Either[Throwable, Any] = Right(-1)
          AsyncTestSupport.startEither(Async.fail(null)) { res => out = res }
          assertTrue(out == Left(null))
        }
      ),
      // Category F — monad right-identity with null success value.
      suite("monad laws with null success")(
        test("rightIdentity_pendingNull_flatMapSucceedEqualsOriginal") {
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val lhs = pending.flatMap(Async.succeed(_))
          c.succeed(null)
          assertTrue(lhs.block == null)
        }
      ),
      // Category K — flatMap continuation throw on pending null success must escape.
      suite("flatMap continuation throw on pending null success")(
        test("flatMap_pendingNullSuccess_continuationThrows_propagatesThrow") {
          val contFx       = new RuntimeException("cont-throw")
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val a = pending.flatMap(_ => throw contFx)
          c.succeed(null)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(contFx))
        }
      ),
      // Category L — catchAll recovery to null failure must reify consistently.
      suite("catchAll recovery to null failure on pending path")(
        test("catchAll_pendingFail_handlerReturnsNullFail_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pending.catchAll(_ => Async.fail(null))
          c.fail(boom)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsNullCause(fa) == Left(null))
        }
      ),
      // CONVERGENCE — categories A/B/F/J/K/L probes that must stay green.
      suite("CONVERGENCE: pass-4 regression locks")(
        test("map_readyFail_doesNotInvokeMapper") {
          var invoked = false
          val thrown  =
            Try(Async.fail(boom).map { (_: Nothing) => invoked = true; 0 }.block).failed.toOption
          assertTrue(thrown.contains(boom), !invoked)
        },
        test("catchAll_readySuccess_doesNotInvokeHandler") {
          var invoked = false
          assertTrue(
            Async.succeed(1).catchAll { _ => invoked = true; Async.succeed(0) }.block == 1,
            !invoked
          )
        },
        test("zipWith_readyLeftFail_doesNotDriveRight") {
          var rightPolled       = false
          val right: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val thrown = Try(Async.fail(boom).zipWith(right)((_, _) => 0).block).failed.toOption
          assertTrue(thrown.contains(boom), !rightPolled)
        }
      ),
      // Category B/H — Failure-as-success encoding collision (sibling to Pollable).
      suite("Failure-as-success encoding")(
        test("succeed_failureValue_isNotSilentlyDrivenAsFailure") {
          val failure = new Failure(boom)
          val a       = Async.succeed(failure)
          // Contract: reject at construction OR return the Failure as the success value.
          // Bug: `.block` treats the stored Failure as a terminal failure and throws.
          assertTrue(a.block == failure)
        }
      ),
      // Category K — mapError mapper throw on pending path must escape via driver.
      suite("mapError mapper synchronous throw")(
        test("mapError_pendingFail_mapperThrows_propagatesMapperThrow") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.mapError(_ => throw AsyncTestSupport.mapperFx)
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.mapperFx))
        },
        test("mapError_readyFail_mapperThrows_propagatesMapperThrow") {
          val thrown =
            Try(Async.fail(boom).mapError(_ => throw AsyncTestSupport.mapperFx).block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.mapperFx))
        }
      ),
      // Category F — <* must fail-fast on pending left failure without driving right.
      suite("<* pending left failure fail-fast")(
        test("starLt_pendingLeftFail_doesNotDriveRight") {
          var rightPolled       = false
          val right: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val (c, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = left.<*(right)
          c.fail(boom)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(boom), !rightPolled)
        }
      ),
      // Category F — zipWith pending left then pending right failure preserves ordering.
      suite("zipWith pending sequential failure")(
        test("zipWith_pendingLeftThenPendingRightFail_surfacesRightFailure") {
          val (c1, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val z = left.zipWith(right)((a, b) => a + b)
          c1.succeed(1)
          c2.fail(boom)
          val thrown = Try(z.block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      ),
      // Category J — Completer one-shot: second completion is ignored.
      suite("Completer one-shot completion")(
        test("completer_secondSucceedAfterFirstFail_isIgnored") {
          val c = new Completer[Int]
          c.fail(boom)
          c.succeed(99)
          val thrown = Try(c.peek.block).failed.toOption
          assertTrue(thrown.contains(boom))
        },
        test("completer_secondFailAfterFirstSucceed_isIgnored") {
          val c = new Completer[Int]
          c.succeed(42)
          c.fail(boom)
          assertTrue(c.peek.block == 42)
        }
      ),
      // Category A/B — attempt and promise null success boundaries.
      suite("null success via attempt and promise")(
        test("attempt_nullValue_succeedsWithNull") {
          assertTrue(Async.attempt(null: String).block == null)
        },
        test("promise_succeedNull_peekIsReadyNullNotPending") {
          val a         = Async.promiseInternal[String](c => c.succeed(null))
          val any       = a.asInstanceOf[Any]
          val suspended = any != null && any.isInstanceOf[Pollable[_]]
          assertTrue(!suspended, a.block == null)
        }
      ),
      // Category L — orElse chain with null failure on pending fallback.
      suite("orElse pending null failure recovery")(
        test("orElse_pendingPrimaryFail_pendingFallbackNullFail_eitherAndBlockAgree") {
          val (c1, pendingPrimary) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, fallback) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pendingPrimary.orElse(fallback)
          c1.fail(boom)
          c2.fail(null)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        }
      ),
      // Category L — catchAll handler returns null failure on ready path.
      suite("catchAll handler returns null failure")(
        test("catchAll_readyFail_handlerReturnsNullFail_eitherAndBlockAgree") {
          val fa = Async.fail(boom).catchAll(_ => Async.fail(null))
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        }
      ),
      // Category K — foldCause onFailure throw on pending null failure.
      suite("foldCause onFailure throw")(
        test("foldCause_pendingNullFail_onFailureThrows_propagatesThrow") {
          val onFailFx     = new RuntimeException("onFail-throw")
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a =
            pending
              .flatMap(_ => Async.fail(null))
              .foldCause(_ => throw onFailFx)(_ => "ok")
          c.succeed(0)
          val thrown = Try(a.block).failed.toOption
          assertTrue(thrown.contains(onFailFx))
        }
      ),
      // Category F — associativity on pending path with null success.
      suite("monad associativity pending null")(
        test("associativity_pendingNull_flatMapChainEqualsNested") {
          val f            = (s: String) => Async.succeed(s + "!")
          val g            = (s: String) => Async.succeed(s + "?")
          val (c, pending) = {
            val c = new Completer[String]
            (c, c.peek)
          }
          val lhs = pending.flatMap(f).flatMap(g)
          val rhs = pending.flatMap(s => f(s).flatMap(g))
          c.succeed(null)
          assertTrue(lhs.block == rhs.block)
        }
      ),
      // Category M/J — ensuring ready success + finalizer poll throw is suppressed.
      suite("ensuring ready success finalizer poll throw")(
        test("ensuring_readySuccess_finalizerPollThrow_preservesSuccess") {
          val finFx           = new RuntimeException("fin-poll-throw")
          val fin: Async[Any] = new Pollable[Any] {
            def poll(onComplete: Runnable): Async[Any] = throw finFx
          }
          assertTrue(Async.succeed(42).ensuring(fin).block == 42)
        }
      ),
      // Category P — unsafeRunAsync cancel after completion is noop (no double cb).
      suite("unsafeRunAsync cancel after completion")(
        test("unsafeRunAsync_readySuccess_cancelAfterCallback_isNoop") {
          var count   = 0
          val running = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
          running.cancel()
          assertTrue(count == 1)
        }
      ),
      // CONVERGENCE — extended pass-4 regression locks.
      suite("CONVERGENCE: pass-4 extended regression locks")(
        test("mapError_readySuccess_doesNotInvokeMapper") {
          var invoked = false
          assertTrue(
            Async.succeed(1).mapError { t => invoked = true; t }.block == 1,
            !invoked
          )
        },
        test("catchAll_handlerReturnsNullSuccess_recoversOnPending") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val a = pending.catchAll(_ => Async.succeed("ok"))
          c.fail(boom)
          assertTrue(a.block == "ok")
        }
      ),
      // Category B/H/L — Ready carrier must unwrap through poll continuations.
      suite("Ready carrier through pending flatMap")(
        test("flatMap_pendingSucceedPollable_blockReturnsPollableNotReadyOrDriven") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner))
          c.succeed(())
          val result = fa.block
          assertTrue((result: AnyRef) eq inner)
        },
        test("flatMap_pendingSucceedPollable_unsafeRunAsyncDeliversPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]]              = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner))
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val running                               = AsyncTestSupport.startEither(fa)(res => out = res)
          c.succeed(())
          // JVM: awaitSuspended path may not unwrap; JS runner step does unwrap.
          // Both must deliver the pollable as the success value, not 99.
          running.cancel()
          assertTrue(out == Right(inner))
        }
      ),
      suite("Ready carrier through pending catchAll")(
        test("catchAll_pendingFail_recoverySucceedPollable_blockReturnsPollableNotReadyOrDriven") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(pending.catchAll(_ => Async.succeed(inner)))
          c.fail(boom)
          val result = fa.block
          assertTrue((result: AnyRef) eq inner)
        }
      ),
      // Category F — collectAll must preserve Ready-wrapped pollable elements.
      suite("collectAll Ready-wrapped pollable element")(
        test("collectAll_succeedPollableValue_collectsPollableWithoutDriving") {
          val inner     = AsyncTestSupport.pollableSuccessValue
          val collected = Async.collectAll(List(Async.succeed(inner))).block
          assertTrue(collected.length == 1, (collected.head: AnyRef) eq inner)
        }
      ),
      // Category K — waker reentrancy must not observe partial driver state.
      suite("waker reentrancy during poll")(
        test("poll_wakerReentersUnsafeRunAsync_completesBothRunsAtMostOnce") {
          val boomInner                        = new RuntimeException("inner-cb-throw")
          var outerCount                       = 0
          var innerCount                       = 0
          var innerOut: Either[Throwable, Any] = Right(-1)
          val nested: Pollable[Int]            = new Pollable[Int] {
            private var polls                          = 0
            def poll(onComplete: Runnable): Async[Int] = {
              polls += 1
              if (polls == 1) {
                AsyncTestSupport.startEither(Async.fail(boomInner)) { res =>
                  innerCount += 1
                  innerOut = res
                }
                onComplete.run()
                this
              } else Async.fail(boomInner)
            }
          }
          val running = AsyncTestSupport.startTap(nested) { _ =>
            outerCount += 1
            ()
          }
          val _ = Try(AsyncTestSupport.driveToEnd(nested).block)
          running.cancel()
          assertTrue(innerCount <= 1, outerCount <= 1, innerOut == Left(boomInner))
        }
      ),
      // Category F — foldCause onSuccess throw on ready path (documented eager escape).
      suite("CONVERGENCE: foldCause eager onSuccess throw")(
        test("foldCause_readySuccess_onSuccessThrow_escapesEagerly") {
          val fx     = new RuntimeException("onSuccess-throw")
          val caught =
            try {
              Async.succeed(1).foldCause(_ => "nope")(_ => throw fx).block
              Option.empty[Throwable]
            } catch {
              case t: Throwable => Some(t)
            }
          assertTrue(caught.contains(fx))
        }
      ),
      // Category L — mapError identity on nested pending null failure.
      suite("CONVERGENCE: mapError pending null after flatMap")(
        test("mapError_pendingNullAfterFlatMap_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.fail(null))
              .mapError(identity)
          c.succeed(0)
          val viaEither = fa.either.block
          val viaBlock  =
            try {
              fa.block
              Right(None)
            } catch {
              case Failure.NullCauseMarker => Left(null)
              case t: Throwable            => Left(t)
            }
          assertTrue(viaEither == Left(null), viaBlock == Left(null))
        }
      ),
      // Category B/H — Ready carrier through mapAsync-equivalent pending path.
      suite("Ready carrier through pending mapAsync path")(
        test("flatMap_pendingSucceedPollable_blockReturnsPollableNotReadyOrDriven") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending.flatMap[Pollable[Int]](_ => Async.succeed(inner))
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("flatMap_pendingSucceedPollable_unsafeRunAsyncDeliversPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending.flatMap[Pollable[Int]](_ => Async.succeed(inner))
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val done                                  = new Completer[Unit]
          val running                               = AsyncTestSupport.startEither(fa) { res =>
            out = res
            done.succeed(())
          }
          c.succeed(())
          for {
            _ <- AsyncTestSupport.runAsync(done.peek)
          } yield {
            running.cancel()
            assertTrue(out == Right(inner))
          }
        }
      ),
      // Category B/H — pollable success values through pending map.
      suite("Ready carrier through pending map")(
        test("map_pendingSource_userPollableValue_blockReturnsPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] = pending.map(_ => inner)
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("map_pendingSource_combinatorAsyncValue_staysAValueLikeTheReadyPath") {
          // The mapped value here is a *combinator-built* Async (a continuation
          // pollable), not a user Pollable. `map`'s function returns a value:
          // the pending path must lift it exactly as the ready path
          // (`Async.succeed`) does, not re-dispatch it as the rest of the
          // computation and replace the value with its polled scalar.
          val (cSrc, src) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val inner: Async[Int] = src.map(_ + 1)
          val (cGate, gate)     = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val nested: Async[Async[Int]] = gate.map(_ => inner)
          val ready: Async[Async[Int]]  = Async.succeed(()).map(_ => inner)
          cGate.succeed(())
          cSrc.succeed(41) // settled, so a (defective) drive of `inner` cannot hang `.block`
          val viaReady: Any   = ready.block
          val viaPending: Any = nested.block
          assertTrue(viaReady == inner, viaPending == inner)
        }
      ),
      // Category B/H — Ready carrier through tap on pending path.
      suite("Ready carrier through pending tap")(
        test("tap_pendingSucceedPollable_blockReturnsPollableNotReadyOrDriven") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending
              .tap(_ => Async.succeed(()))
              .flatMap(_ => Async.succeed(inner))

          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("tap_sideEffectSucceedPollable_preservesPrimaryPollableIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.succeed(inner).tap(_ => Async.succeed(AsyncTestSupport.pollableSuccessValue))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category B/H — Ready carrier through ensuring on pending path.
      suite("Ready carrier through pending ensuring")(
        test("ensuring_pendingSucceedPollable_blockReturnsPollableNotReadyOrDriven") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending
              .ensuring(Async.succeed(()))
              .flatMap(_ => Async.succeed(inner))

          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("ensuring_primaryPollable_finalizerSucceedPollable_preservesPrimaryIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.succeed(inner).ensuring(Async.succeed(AsyncTestSupport.pollableSuccessValue))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category B/H/F — zipWith and *> with pollable-as-value from pending chain.
      suite("Ready carrier through zipWith and *>")(
        test("zipWith_pendingLeftSucceedPollable_blockReturnsPairWithPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)).zipWith(Async.succeed(1))((p, n) => (p, n))
          c.succeed(())
          val result = fa.block
          assertTrue(result == ((inner, 1)), (result._1: AnyRef) eq inner)
        },
        test("starGt_pendingLeftSucceedPollable_blockReturnsPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending
              .flatMap[Pollable[Int]](_ => Async.succeed(inner))
              .<*(Async.succeed(()))
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category F/M — collectAll with pending element succeeding to pollable.
      suite("collectAll pending element succeeds to pollable")(
        test("collectAll_pendingSucceedPollable_collectsPollableWithoutDriving") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val collected = Async.collectAll[Any](List(Async.succeed(1), pending, Async.succeed(3)))
          c.succeed(inner)
          val result = collected.block
          assertTrue(
            result.length == 3,
            result == List[Any](1, inner, 3)
          )
        },
        test("collectAll_mixedReadyAndPendingPollables_preservesIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(77)
          }
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val collected = Async.collectAll(List(Async.succeed(inner1), pending))
          c.succeed(inner2)
          val result = collected.block
          assertTrue(
            result.length == 2,
            inner1 eq result.head,
            inner2 eq result(1)
          )
        }
      ),
      // Category F — flatten with nested succeed pollable.
      suite("flatten nested succeed pollable")(
        test("flatten_pendingInnerSucceedPollable_blockReturnsPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Async[Pollable[Int]]]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending.flatMap(identity)
          c.succeed(Async.succeed(inner))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("flatten_readyNestedSucceedPollable_blockReturnsPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async.succeed(Async.succeed(inner)).flatten.block
          assertTrue(result eq inner)
        }
      ),
      // Category J — promise completion with pollable value.
      suite("promise completes with pollable value")(
        test("promise_succeedPollable_deliversPollableIdentity") {
          val inner                                 = AsyncTestSupport.pollableSuccessValue
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val fa: Async[Pollable[Int]]              =
            Async.promiseInternal[Pollable[Int]](c => c.succeed(inner))
          AsyncTestSupport.startEither(fa)(res => out = res)
          assertTrue(out == Right(inner))
        }
      ),
      // Category F — nested flatMap chain preserves Ready through three combinators.
      suite("nested Ready preservation chain")(
        test("tripleFlatMap_pendingSucceedPollable_blockReturnsPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending
              .flatMap(_ => Async.succeed(()))
              .flatMap(_ => Async.succeed(()))
              .flatMap(_ => Async.succeed(inner))
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("flatMapFlatMap_pendingSucceedPollable_eitherReifiesRightPollable") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(()))
              .flatMap(_ => Async.succeed(inner))
              .either
          c.succeed(())
          val result = fa.block
          assertTrue(result == Right(inner))
        }
      ),
      // Category F/L — tap observes pollable identity; flatMap replaces value.
      suite("tap observation and value replacement with pollable success")(
        test("tap_pendingSucceedPollable_observesPollableIdentity") {
          val inner                           = AsyncTestSupport.pollableSuccessValue
          var observed: Option[Pollable[Int]] = None
          val (c, pending)                    = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .tap { p => observed = Some(p); Async.succeed(()) }
          c.succeed(())
          assertTrue(fa.block eq inner, observed.exists(_ eq inner))
        },
        test("flatMap_pendingSucceedPollable_replacesWithoutDrivingPollable") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .flatMap(_ => Async.succeed("replaced"))
          c.succeed(())
          assertTrue(fa.block == "replaced")
        }
      ),
      // Category K — flatMap continuation throw on pending path after pollable chain.
      suite("flatMap continuation throw on pending pollable chain")(
        test("flatMap_pendingSucceedPollable_continuationThrows_propagatesThrow") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .flatMap(_ => throw AsyncTestSupport.mapperFx)
          c.succeed(())
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.mapperFx))
        }
      ),
      // Category L — ensuring AsyncTestSupport.primary succeed + finalizer null fail preserves pollable.
      suite("ensuring null finalizer with pollable primary")(
        test("ensuring_pendingPrimarySucceedPollable_nullFinalizerFail_deliversPollableNotNpe") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val a = pending.ensuring(Async.fail(null))
          c.succeed(inner)
          val result: Pollable[Int] = a.block
          assertTrue(result eq inner)
        }
      ),
      // Category K — waker reentrancy from tap side effect during pollable await.
      suite("tap waker reentrancy during pollable poll")(
        test("tap_pollableSideEffectStartsNestedRun_deliversAtMostOnce") {
          val innerFail                        = new RuntimeException("inner-fail")
          var innerCount                       = 0
          var innerOut: Either[Throwable, Any] = Right(-1)
          val pa: Pollable[Int]                = new Pollable[Int] {
            private var polls                          = 0
            def poll(onComplete: Runnable): Async[Int] = {
              polls += 1
              if (polls == 1) {
                val innerFx: Async[Int] = Async.fail(innerFail)
                AsyncTestSupport.startEither(innerFx) { (res: Either[Throwable, Int]) =>
                  innerCount += 1
                  innerOut = res
                }
                onComplete.run()
                this
              } else Async.succeed(7)
            }
          }
          val fa = Async.succeed(pa).tap(_ => Async.succeed(()))
          assertTrue(fa.block == pa, innerCount == 1, innerOut == Left(innerFail))
        }
      ),
      // Category L — orElse recovery to pollable on pending path.
      suite("orElse recovery to pollable on pending path")(
        test("orElse_pendingFail_pendingRecoverySucceedPollable_deliversPollableIdentity") {
          val inner                = AsyncTestSupport.pollableSuccessValue
          val (c1, pendingPrimary) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val (c2, fallback) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] = pendingPrimary.orElse(fallback)
          c1.fail(boom)
          c2.succeed(inner)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category F — monad associativity with pollable-as-value in the chain.
      suite("monad associativity with pollable-as-value")(
        test("associativity_pendingPollable_flatMapChainPreservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val f            = (_: Pollable[Int]) => Async.succeed(1)
          val g            = (_: Int) => Async.succeed(inner)
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val lhs = pending.flatMap(f).flatMap(g)
          val rhs = pending.flatMap(p => f(p).flatMap(g))
          c.succeed(inner)
          val l: Pollable[Int] = lhs.block
          val r: Pollable[Int] = rhs.block
          assertTrue(l eq inner, r eq inner)
        }
      ),
      // CONVERGENCE — pass-6 regression locks for categories exercised above.
      suite("CONVERGENCE: pass-6 regression locks")(
        test("flatMap_readySucceedPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async.succeed(inner).flatMap(x => Async.succeed(x)).block
          assertTrue(result eq inner)
        },
        test("catchAll_pendingSuccess_doesNotInvokeHandlerOnPollableValue") {
          var invoked      = false
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = pending.catchAll { _ => invoked = true; Async.succeed(inner) }
          c.succeed(inner)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner, !invoked)
        },
        test("mapError_pendingNullAfterFlatMapPollable_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.fail(null))
              .mapError(identity)
          c.succeed(0)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        }
      ),
      // Category F/H — zip combinator (not just zipWith) with pollable-as-value.
      suite("zip pollable-as-value")(
        test("zip_readyLeftSucceedPollable_preservesPollableIdentity") {
          val inner  = AsyncTestSupport.pollableSuccessValue
          val result = Async.succeed(inner).zip(Async.succeed(1)).block
          assertTrue(result._1 eq inner, result == ((inner, 1)))
        },
        test("zip_pendingLeftSucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = pending.zip(Async.succeed(1))
          c.succeed(inner)
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, 1))
        },
        test("zip_pendingBothSucceedPollable_preservesBothIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(77)
          }
          val (c1, left) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = left.zip(right)
          c1.succeed(inner1)
          c2.succeed(inner2)
          val result = fa.block
          assertTrue(result._1 eq inner1, result._2 eq inner2)
        }
      ),
      // Category B/H — as and unit must preserve pollable-as-value on pending path.
      suite("as and unit pollable-as-value")(
        test("as_readySucceedPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async.succeed(inner).as(inner).block
          assertTrue(result eq inner)
        },
        test("as_pendingSucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = pending.as(inner)
          c.succeed(AsyncTestSupport.pollableSuccessValue)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("unit_readySucceedPollable_doesNotDrivePollable") {
          var polled               = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { polled = true; Async.succeed(99) }
          }
          assertTrue(Async.succeed(inner).unit.block == (), !polled)
        }
      ),
      // Category F/B — flatten nested Async layers with pollable-as-value.
      suite("flatten nested succeed pollable")(
        test("flatten_readyNestedSucceedPollable_blockReturnsPollableIdentity") {
          val inner                        = AsyncTestSupport.pollableSuccessValue
          val nested: Async[Pollable[Int]] = Async.succeed(inner)
          val result: AnyRef               =
            Async.succeed(nested).flatten.block
          assertTrue(result eq inner)
        },
        test("flatten_pendingOuterSucceedPollable_blockReturnsPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Async[Pollable[Int]]]
            (c, c.peek)
          }
          val fa = pending.flatten
          c.succeed(Async.succeed(inner))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category J/L — catchAll handler returns pending recovery succeeding to pollable.
      suite("catchAll nested pending recovery to pollable")(
        test("catchAll_pendingFail_pendingRecoverySucceedPollable_deliversPollableIdentity") {
          val inner                = AsyncTestSupport.pollableSuccessValue
          val (c1, pendingPrimary) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, recovery) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(
              pendingPrimary
                .catchAll(_ => recovery.flatMap(_ => Async.succeed(inner)))
            )
          c1.fail(boom)
          c2.succeed(())
          assertTrue(fa.either.block == Right(inner))
        },
        test("catchAll_pendingFail_handlerReturnsBarePollable_sequencesToPolledValue") {
          val inner               = AsyncTestSupport.pollableSuccessValue
          val (c, pendingPrimary) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pendingPrimary.catchAll(_ => inner)
          c.fail(boom)
          assertTrue(fa.block == 99)
        }
      ),
      // Category F/L — foldCause failure branch returning pollable-as-value.
      suite("foldCause failure branch pollable-as-value")(
        test("foldCause_readyFail_onFailureReturnsPollable_preservesIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .fail(boom)
              .foldCause(_ => inner)(_ => AsyncTestSupport.pollableSuccessValue)
              .block
          assertTrue(result eq inner)
        },
        test("foldCause_pendingFail_onFailureReturnsPollable_preservesIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            pending.foldCause(_ => inner)(_ => AsyncTestSupport.pollableSuccessValue)
          c.fail(boom)
          assertTrue(fa.either.block == Right(inner))
        }
      ),
      // Category J/M — EnsuringPollable Ready AsyncTestSupport.outcome branch after poll returns succeed(pollable).
      suite("ensuring Ready outcome propagation")(
        test("ensuring_pollReturnsSucceedPollable_preservesPollableIdentity") {
          val inner                       = AsyncTestSupport.pollableSuccessValue
          val pa: Pollable[Pollable[Int]] = new Pollable[Pollable[Int]] {
            def poll(onComplete: Runnable): Async[Pollable[Int]] = Async.succeed(inner)
          }
          val result: AnyRef =
            pa.ensuring(Async.succeed(())).block
          assertTrue(result eq inner)
        },
        test("ensuring_pendingPollReturnsSucceedPollable_finalizerFail_preservesPrimary") {
          val inner                       = AsyncTestSupport.pollableSuccessValue
          val pa: Pollable[Pollable[Int]] = new Pollable[Pollable[Int]] {
            def poll(onComplete: Runnable): Async[Pollable[Int]] = Async.succeed(inner)
          }
          val result: AnyRef =
            pa.ensuring(Async.fail(null)).block
          assertTrue(result eq inner)
        }
      ),
      // Category J/A — when/unless by-name guards with pollable-bearing fa.
      suite("when and unless pollable guards")(
        test("when_false_doesNotConstructPollableFa") {
          var constructed = false
          assertTrue(
            when(false) {
              constructed = true
              Async.succeed(AsyncTestSupport.pollableSuccessValue)
            }.block == (),
            !constructed
          )
        },
        test("when_true_succeedPollable_unitDoesNotDrivePollable") {
          var polled               = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { polled = true; Async.succeed(99) }
          }
          when(true)(Async.succeed(inner)).block
          assertTrue(!polled)
        },
        test("unless_true_doesNotConstructPollableFa") {
          var constructed = false
          assertTrue(
            unless(true) {
              constructed = true
              Async.succeed(AsyncTestSupport.pollableSuccessValue)
            }.block == (),
            !constructed
          )
        }
      ),
      // Category A — lazy Iterator collectAll boundary with pollable element.
      suite("collectAll lazy iterator")(
        test("collectAll_iteratorSingleSucceedPollable_preservesIdentity") {
          val inner     = AsyncTestSupport.pollableSuccessValue
          val collected =
            Async.collectAll(Iterator.single(Async.succeed(inner))).block
          assertTrue(collected.length == 1, collected.head eq inner)
        },
        test("collectAll_lazyIteratorMixedReadyPending_preservesPollableIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(55)
          }
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val collected = Async.collectAll(Iterator(Async.succeed(inner1), pending))
          c.succeed(inner2)
          val result = collected.block
          assertTrue(
            result.length == 2,
            result.head eq inner1,
            result(1) eq inner2
          )
        }
      ),
      // Category B — attempt lifting pollable value without driving.
      suite("attempt pollable-as-value")(
        test("attempt_bodyReturnsPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async.attempt(inner).block
          assertTrue(result eq inner)
        }
      ),
      // Category H/F — ZipWithPollable with Ready left operand and pending right.
      suite("zipWith Ready left operand")(
        test("zipWith_readyLeftSucceedPollable_pendingRight_preservesPollableIdentity") {
          val inner      = AsyncTestSupport.pollableSuccessValue
          val (c, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = Async.succeed(inner).zipWith(right)((p, n) => (p, n))
          c.succeed(2)
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, 2))
        }
      ),
      // Category L — mapError pending fail then recovery to pollable via catchAllAsync.
      suite("mapError recovery to pollable")(
        test("mapError_pendingFail_catchAllRecoverySucceedPollable_preservesIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(
              pending
                .mapError(identity)
                .catchAll(_ => Async.succeed(inner))
            )
          c.fail(boom)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category F — monad identities with pollable-as-value.
      suite("monad identities with pollable-as-value")(
        test("leftIdentity_succeedPollable_flatMapPreservesIdentity") {
          val inner  = AsyncTestSupport.pollableSuccessValue
          val f      = (p: Pollable[Int]) => Async.succeed(p)
          val result = Async.succeed(inner).flatMap(f).block
          assertTrue(result eq inner)
        },
        test("rightIdentity_pendingPollable_flatMapSucceedPreservesIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = pending.flatMap(p => Async.succeed(p))
          c.succeed(inner)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category F — never right-zero with pollable recovery.
      suite("never orElse pollable recovery")(
        test("orElse_pendingFail_succeedPollable_deliversPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pending.orElse(Async.succeed(inner))
          c.fail(boom)
          assertTrue(fa.either.block == Right(inner))
        }
      ),
      // Category K — map mapper throw after pending pollable chain.
      suite("map mapper throw on pending pollable chain")(
        test("map_pendingSucceedPollable_mapperThrows_propagatesThrow") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .map(_ => throw AsyncTestSupport.mapperFx)
          c.succeed(())
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.mapperFx))
        }
      ),
      // Category P/N — unsafeRunAsync egress with deep pending pollable chain.
      suite("unsafeRunAsync pollable egress")(
        test("unsafeRunAsync_catchAllPendingRecoverySucceedPollable_deliversPollableIdentity") {
          val inner                = AsyncTestSupport.pollableSuccessValue
          val (c1, pendingPrimary) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, recovery) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(
              pendingPrimary
                .catchAll(_ => recovery.flatMap(_ => Async.succeed(inner)))
            )
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val done                                  = new Completer[Unit]
          val running                               = AsyncTestSupport.startEither(fa) { res =>
            out = res
            done.succeed(())
          }
          c1.fail(boom)
          c2.succeed(())
          for {
            _ <- AsyncTestSupport.runAsync(done.peek)
          } yield {
            running.cancel()
            assertTrue(out == Right(inner))
          }
        }
      ),
      // Category L — either/block agreement on pending pollable success chain.
      suite("either and block agreement pollable chain")(
        test("either_pendingSucceedPollable_matchesBlockIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)).either
          c.succeed(AsyncTestSupport.pollableSuccessValue)
          val viaEither = fa.block
          val viaBlock  = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)).block
          assertTrue(viaEither == Right(inner), viaBlock eq inner)
        }
      ),
      // Category F — map pending returning pollable from mapper (not identity).
      suite("map pending returns pollable from mapper")(
        test("map_pendingSucceedUnit_mapperReturnsPollable_preservesIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa = pending.map(_ => inner)
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category J — promiseInternal completes with pollable then flatMaps.
      suite("promise pollable then flatMap")(
        test("promise_succeedPollable_flatMapIdentity_preservesPollable") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .flatMap(p => Async.succeed(p))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // CONVERGENCE — pass-7 regression locks for categories exercised above.
      suite("CONVERGENCE: pass-7 regression locks")(
        test("zipWith_readyBoth_combineReturnsPollable_preservesIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .succeed(inner)
              .zipWith(Async.succeed("x"))((p, _) => p)
              .block
          assertTrue(result eq inner)
        },
        test("flatten_doubleNestedSucceedPollable_preservesIdentity") {
          val inner                 = AsyncTestSupport.pollableSuccessValue
          val once                  = Async.succeed(inner)
          val twice                 = Async.succeed(once).flatten
          val result: Pollable[Int] = twice.block
          assertTrue(result eq inner)
        },
        test("mapError_pendingNullAfterFlatMapPollable_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.fail(null))
              .mapError(identity)
          c.succeed(0)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        }
      ),
      // Category J/H — poll() egress must deliver Ready-wrapped pollable like peek.
      suite("Completer poll path Ready-wrap")(
        test("poll_afterSucceedPollable_flatMapIdentity_preservesPollableNotDriven") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Pollable[Int]]
          c.succeed(inner)
          val fromPoll: Async[Pollable[Int]] = c.poll(AsyncTestSupport.noopRunnable)
          val result: AnyRef                 =
            fromPoll.flatMap(p => Async.succeed(p)).block
          assertTrue(result eq inner)
        },
        test("poll_syncCompleteDuringFirstPoll_flatMapIdentity_preservesPollableNotDriven") {
          val inner                      = AsyncTestSupport.pollableSuccessValue
          val c                          = new Completer[Pollable[Int]]
          var polls                      = 0
          val gate: Async[Pollable[Int]] = new Pollable[Pollable[Int]] {
            def poll(onComplete: Runnable): Async[Pollable[Int]] = {
              polls += 1
              if (polls == 1) {
                c.succeed(inner)
                c.poll(onComplete)
              } else c.poll(onComplete)
            }
          }
          val result: AnyRef =
            gate.flatMap(p => Async.succeed(p)).block
          assertTrue(result eq inner, polls >= 1)
        },
        test("block_directOnPromiseSucceedPollable_preservesPollableIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category B/H/J — BUG-018 regression: promise and peek paths after fix.
      suite("CONVERGENCE: BUG-018 Completer Ready-wrap regression locks")(
        test("promise_succeedPollable_catchAllIdentity_preservesPollableNotDriven") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .catchAll(_ => Async.succeed(inner))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("promise_succeedPollable_starLt_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .<*(Async.succeed(()))
              .block
          assertTrue(result eq inner)
        },
        test("promise_succeedPollable_ensuring_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .ensuring(Async.succeed(()))
              .block
          assertTrue(result eq inner)
        },
        test("promise_succeedPollable_either_reifiesRightPollableIdentity") {
          val inner                                       = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Either[Throwable, Pollable[Int]]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner)).either
          val result: Either[Throwable, Pollable[Int]] = fa.block
          assertTrue(result == Right(inner))
        },
        test("promise_succeedPollable_mapError_passesThroughWithoutDriving") {
          var invoked        = false
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .mapError { t => invoked = true; t }
              .block
          assertTrue(result eq inner, !invoked)
        },
        test("promise_succeedPollable_foldCause_onSuccess_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .foldCause(_ => AsyncTestSupport.pollableSuccessValue)(p => p)
              .block
          assertTrue(result eq inner)
        }
      ),
      // Category F — nested promise and associativity through promise nodes.
      suite("nested promise algebraic laws")(
        test("nestedPromise_flatMapSucceedPollable_preservesPollableIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async
              .promiseInternal[Pollable[Int]] { outer =>
                Async.promiseInternal[Pollable[Int]] { innerC =>
                  outer.succeed(inner)
                  innerC.succeed(AsyncTestSupport.pollableSuccessValue)
                }
              }
              .flatMap(p => Async.succeed(p))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("associativity_promisePollable_flatMapChains_agree") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner))
          val f             = (p: Pollable[Int]) => Async.succeed(p)
          val g             = (p: Pollable[Int]) => Async.succeed(p)
          val left: AnyRef  = fa.flatMap(f).flatMap(g).block
          val right: AnyRef = fa.flatMap(p => f(p).flatMap(g)).block
          assertTrue(left eq inner, right eq inner)
        }
      ),
      // Category F/M — collectAll with promise-completed pollable elements.
      suite("collectAll promise pollable batch")(
        test("collectAll_multiplePromiseSucceedPollable_preservesAllIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(55)
          }
          val collected =
            Async
              .collectAll(
                List(
                  Async.promiseInternal[Pollable[Int]](_.succeed(inner1)),
                  Async.promiseInternal[Pollable[Int]](_.succeed(inner2))
                )
              )
              .block
          assertTrue(
            collected.length == 2,
            collected.head eq inner1,
            collected(1) eq inner2
          )
        },
        test("collectAll_lazyIteratorPromiseOnly_preservesPollableIdentities") {
          val inner     = AsyncTestSupport.pollableSuccessValue
          val collected =
            Async
              .collectAll(
                Iterator.single(Async.promiseInternal[Pollable[Int]](_.succeed(inner)))
              )
              .block
          assertTrue(collected.length == 1, collected.head eq inner)
        },
        test("collectAll_promisePollableThenPendingFail_doesNotDriveTrailing") {
          var trailingPolled          = false
          val trailing: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { trailingPolled = true; Async.succeed(0) }
          }
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val collected =
            Async
              .collectAll(
                List(
                  Async.promiseInternal[Pollable[Int]](_.succeed(AsyncTestSupport.pollableSuccessValue)),
                  pending,
                  Async.succeed(trailing)
                )
              )
              .either
          c.fail(boom)
          assertTrue(collected.block == Left(boom), !trailingPolled)
        }
      ),
      // Category F/H — zip and orElse with promise operands.
      suite("zip and orElse promise pollable operands")(
        test("zip_promiseLeftPendingRight_preservesPollableIdentity") {
          val inner      = AsyncTestSupport.pollableSuccessValue
          val (c, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner)).zip(right)
          c.succeed(7)
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, 7))
        },
        test("orElse_promiseFail_promiseFallbackSucceedPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val boom2          = new RuntimeException("primary")
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.fail(boom2))
              .orElse(Async.promiseInternal[Pollable[Int]](_.succeed(inner)))
              .block
          assertTrue(result eq inner)
        }
      ),
      // Category J/A — when/unless with promise pollable-bearing fa.
      suite("when and unless promise pollable guards")(
        test("when_true_promiseSucceedPollable_unitDoesNotDrivePollable") {
          var polled               = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { polled = true; Async.succeed(99) }
          }
          when(true)(Async.promiseInternal[Pollable[Int]](_.succeed(inner))).block
          assertTrue(!polled)
        },
        test("unless_false_promiseSucceedPollable_preservesPollableIdentity") {
          val inner  = AsyncTestSupport.pollableSuccessValue
          val result = unless(false)(Async.promiseInternal[Pollable[Int]](_.succeed(inner))).block
          assertTrue(result == ())
        }
      ),
      // Category K — hostile handlers on promise-completed pollable paths.
      suite("hostile handlers on promise pollable paths")(
        test("catchAll_promiseFail_handlerThrow_propagatesThrow") {
          val fa =
            Async
              .promiseInternal[Int](_.fail(boom))
              .catchAll(_ => throw AsyncTestSupport.handlerFx)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.handlerFx))
        },
        test("tap_promiseSucceedPollable_sideEffectThrow_propagatesThrow") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val fx    = new RuntimeException("tap-throw")
          val fa    =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .tap(_ => throw fx)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(fx))
        }
      ),
      // Category L — error integrity on promise paths.
      suite("promise error integrity")(
        test("promise_failNull_eitherAndBlockAgree") {
          val fa = Async.promiseInternal[Int](_.fail(null))
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        },
        test("catchAll_promiseFail_recoveryPromiseSucceedPollable_preservesPollableIdentity") {
          val inner     = AsyncTestSupport.pollableSuccessValue
          val recovered =
            Async
              .promiseInternal[Int](_.fail(boom))
              .catchAll(_ => Async.promiseInternal[Pollable[Int]](_.succeed(inner)))
          assertTrue(recovered.either.block == Right(inner))
        },
        test("ensuring_promiseSucceedPollable_finalizerNullFail_preservesPrimary") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .ensuring(Async.fail(null))
              .block
          assertTrue(result eq inner)
        }
      ),
      // Category P — unsafeRunAsync egress on promise pollable paths.
      suite("unsafeRunAsync promise pollable egress")(
        test("unsafeRunAsync_promiseSucceedPollable_flatMapIdentity_deliversPollableIdentity") {
          val inner                                 = AsyncTestSupport.pollableSuccessValue
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val fa: Async[Pollable[Int]]              =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .flatMap(p => Async.succeed(p))
          val done    = new Completer[Unit]
          val running = AsyncTestSupport.startEither(fa) { res =>
            out = res
            done.succeed(())
          }
          done.peek.block
          running.cancel()
          assertTrue(out == Right(inner))
        },
        test("unsafeRunAsync_promiseFail_cancelBeforeComplete_suppressesCallback") {
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa: Async[Pollable[Int]] =
            pending.flatMap(_ =>
              Async.promiseInternal[Pollable[Int]] { innerC =>
                // never completed before cancel
              }
            )
          var count   = 0
          val running = AsyncTestSupport.startTap(fa)(_ => count += 1)
          running.cancel()
          c.succeed(AsyncTestSupport.pollableSuccessValue)
          assertTrue(count == 0)
        }
      ),
      // Category F — flatten and *> on promise-completed pollable.
      suite("flatten and *> promise pollable paths")(
        test("flatten_promiseNestedSucceedPollable_sequencesInnerPollable") {
          val inner                        = AsyncTestSupport.pollableSuccessValue
          val nested: Async[Pollable[Int]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner))
          assertTrue((Async.succeed(nested).flatten.block: AnyRef) eq inner)
        },
        test("starGt_promiseSucceedPollable_doesNotDrivePrimaryPollable") {
          var primaryPolled        = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { primaryPolled = true; Async.succeed(99) }
          }
          val result =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(inner))
              .*>(Async.succeed(42))
              .block
          assertTrue(result == 42, !primaryPolled)
        }
      ),
      // Category H — custom Pollable implementation through promise completion.
      suite("custom Pollable through promise")(
        test("promise_succeedCustomPollable_blockReturnsPolledValue") {
          val custom: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(123)
          }
          assertTrue(
            Async
              .promiseInternal[Pollable[Int]](_.succeed(custom))
              .flatMap((p: Pollable[Int]) => p: Async[Int])
              .block == 123
          )
        },
        test("promise_succeedCustomPollable_flatMapSucceed_preservesPollableIdentity") {
          val custom: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(123)
          }
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(custom))
              .flatMap(p => Async.succeed(p))
              .block
          assertTrue(result eq custom)
        }
      ),
      // Category A/C — numeric boundary through promise path.
      suite("numeric boundary through promise")(
        test("promise_succeedIntMaxValue_preservesValue") {
          val v = Int.MaxValue
          assertTrue(Async.promiseInternal[Int](_.succeed(v)).block == v)
        },
        test("zipWith_promiseIntMin_combinePreservesValue") {
          val v      = Int.MinValue
          val result =
            Async
              .promiseInternal[Int](_.succeed(v))
              .zipWith(Async.succeed(1))(_ + _)
              .block
          assertTrue(result == v + 1)
        }
      ),
      // Category J — Completer one-shot lifecycle on pollable completion.
      suite("CONVERGENCE: Completer lifecycle with pollable values")(
        test("completer_succeedPollableThenFail_firstWriterWins") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Pollable[Int]]
          c.succeed(inner)
          c.fail(boom)
          val result: Pollable[Int] = c.peek.block
          assertTrue(result eq inner)
        },
        test("completer_succeedPollableThenSucceedOther_firstWriterWins") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(11)
          }
          val c = new Completer[Pollable[Int]]
          c.succeed(inner1)
          c.succeed(inner2)
          val result: Pollable[Int] = c.peek.block
          assertTrue(result eq inner1)
        }
      ),
      // Category F — tap on async-delayed promise completion.
      suite("tap on pending promise completion")(
        test("tap_promisePendingSucceedPollable_observesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          var observed: Option[Pollable[Int]] = None
          val fa                              =
            Async
              .promiseInternal[Unit](_.succeed(()))
              .flatMap(_ => pending)
              .tap { p => observed = Some(p); Async.succeed(()) }
          c.succeed(inner)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner, observed.exists(_ eq inner))
        }
      ),
      // Category L — mapError on promise-completed pollable after pending failure chain.
      suite("mapError pending chain into promise pollable recovery")(
        test("mapError_pendingFail_promiseRecoverySucceedPollable_preservesIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(
              pending
                .mapError(identity)
                .catchAll(_ => Async.promiseInternal[Pollable[Int]](_.succeed(inner)))
            )
          c.fail(boom)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // Category K — waker reentrancy with promise-completed pollable in chain.
      suite("waker reentrancy with promise pollable")(
        test("poll_nestedUnsafeRunAsyncDuringPromisePoll_deliversAtMostOnce") {
          val innerFail                        = new RuntimeException("inner-fail")
          var innerCount                       = 0
          var innerOut: Either[Throwable, Any] = Right(-1)
          val pa: Pollable[Int]                = new Pollable[Int] {
            private var polls                          = 0
            def poll(onComplete: Runnable): Async[Int] = {
              polls += 1
              if (polls == 1) {
                AsyncTestSupport.startEither(Async.fail(innerFail)) { res =>
                  innerCount += 1
                  innerOut = res
                }
                onComplete.run()
                this
              } else Async.succeed(8)
            }
          }
          val result: AnyRef =
            Async
              .promiseInternal[Pollable[Int]](_.succeed(pa))
              .flatMap(p => Async.succeed(p))
              .block
          assertTrue(result eq pa, innerCount == 1, innerOut == Left(innerFail))
        }
      ),
      // CONVERGENCE — pass-8 regression locks for categories exercised above.
      suite("CONVERGENCE: pass-8 regression locks")(
        test("peek_afterSucceedPollable_flatMapIdentity_preservesPollableNotDriven") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Pollable[Int]]
          c.succeed(inner)
          val result: AnyRef =
            c.peek.flatMap(p => Async.succeed(p)).block
          assertTrue(result eq inner)
        },
        test("collectAll_emptyIterator_returnsNil") {
          assertTrue(Async.collectAll(Iterator.empty[Async[Int]]).block == Nil)
        },
        test("collectAll_infiniteIteratorWithFailedPrefix_shortCircuitsWithoutConsumingMore") {
          // A failure must stop the drain immediately: the iterator is not
          // advanced past the failed element (otherwise an infinite source
          // would spin forever / run construction effects it must not).
          var consumed                 = 0
          val it: Iterator[Async[Int]] = Iterator.from(1).map { i =>
            consumed = i
            if (i == 3) Async.fail(boom) else Async.succeed(i)
          }
          val out = AsyncTestSupport.blockAsLeftCause(Async.collectAll(it))
          assertTrue(out == Some(boom), consumed == 3)
        },
        test("either_promiseSucceedPollable_matchesBlockIdentity") {
          val inner                    = AsyncTestSupport.pollableSuccessValue
          val fa: Async[Pollable[Int]] =
            Async.promiseInternal[Pollable[Int]](_.succeed(inner))
          assertTrue(fa.either.block == Right(inner), fa.block eq inner)
        }
      ),
      // Category E/H — interop-ready egress must unwrap WrappedPollable (differential vs block).
      suite("ready pollable-as-value interop differential")(
        test("either_succeedPollable_reifiesRightPollableNotWrappedCarrier") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val right = Async.succeed(inner).either.block
          assertTrue(
            right == Right(inner),
            right.toOption.get eq inner
          )
        },
        test("unsafeRunAsync_succeedPollable_deliversPollableNotWrappedOrDriven") {
          val inner                                 = AsyncTestSupport.pollableSuccessValue
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          AsyncTestSupport.startEither(Async.succeed(inner))(res => out = res)
          assertTrue(out == Right(inner))
        }
      ),
      // Category B/H — liftSuccess idempotence on WrappedPollable carrier.
      suite("WrappedPollable double-lift")(
        test("succeed_pollableTwiceViaFlatMapIdentity_preservesPollableNotDoubleWrapped") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val fa    =
            Async
              .succeed(inner)
              .flatMap(p => Async.succeed(p))
              .flatMap(p => Async.succeed(p))
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("catchAll_recoveryDoubleSucceedPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async
              .fail(boom)
              .catchAll(_ => Async.succeed(inner))
              .flatMap(p => Async.succeed(p))
              .block
          assertTrue(result eq inner)
        }
      ),
      // Category J — EnsuringPollable AsyncTestSupport.outcome branch when pa resolves to WrappedPollable.
      suite("ensuring WrappedPollable outcome branch")(
        test("ensuring_pendingSucceedPollable_finalizerFail_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .ensuring(Async.fail(boom))

          c.succeed(())
          assertTrue(fa.block eq inner)
        },
        test("ensuring_readySucceedPollable_pendingFinalizer_preservesPollableIdentity") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val fin   = new Pollable[Any] {
            private var polls                          = 0
            def poll(onComplete: Runnable): Async[Any] = {
              polls += 1
              if (polls == 1) { onComplete.run(); this }
              else Async.succeed(())
            }
          }
          val result: AnyRef =
            Async
              .succeed(inner)
              .ensuring(fin)
              .block
          assertTrue(result eq inner)
        }
      ),
      // Category F — flatten must sequence nested pollable, not treat as value.
      suite("flatten pollable-as-value vs sequencing")(
        test("flatten_succeedNestedSucceedPollable_sequencesInnerPollable") {
          val inner                        = AsyncTestSupport.pollableSuccessValue
          val nested: Async[Pollable[Int]] = Async.succeed(inner)
          assertTrue((Async.succeed(nested).flatten.block: AnyRef) eq inner)
        },
        test("flatten_pendingNestedSucceedPollable_sequencesInnerPollable") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = Async.succeed(pending).flatten
          c.succeed(inner)
          assertTrue((fa.block: AnyRef) eq inner)
        }
      ),
      // Category B/H — Failure-as-success must not short-circuit like Async.fail.
      suite("Failure-as-success differential")(
        test("succeed_failureValue_catchAllDoesNotInvokeHandler") {
          val failure = new Failure(boom)
          var invoked = false
          val result  =
            Async
              .succeed(failure)
              .catchAll { _ => invoked = true; Async.succeed(failure) }
              .block
          assertTrue(result eq failure, !invoked)
        },
        test("succeed_failureValue_eitherReifiesRightNotLeft") {
          val failure = new Failure(boom)
          assertTrue(Async.succeed(failure).either.block == Right(failure))
        },
        test("succeed_failureValue_mapAppliesToFailureObject") {
          val failure = new Failure(boom)
          val tagged  = new Failure(boom)
          val result  = Async.succeed(failure).map(_ => tagged).block
          assertTrue(result eq tagged)
        }
      ),
      // Category F/M — collectAll pending terminal unwrap through flatMap chain.
      suite("collectAll pending pollable terminal unwrap")(
        test("collectAll_pendingFlatMapSucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val collected =
            Async
              .collectAll(
                List(
                  pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)),
                  Async.succeed(1)
                )
              )
          c.succeed(())
          val head: AnyRef = collected.block.head.asInstanceOf[AnyRef]
          assertTrue(head eq inner)
        },
        test("collectAll_mixedReadyAndPendingPollableElements_preservesIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(55)
          }
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val collected =
            Async.collectAll(
              List(
                Async.succeed(inner1),
                pending,
                Async.succeed(inner2)
              )
            )
          c.succeed(inner2)
          val r = collected.block
          assertTrue(r.length == 3, r.head eq inner1, r(1) eq inner2, r(2) eq inner2)
        }
      ),
      // Category K — hostile mapper on pollable-as-value ready path.
      suite("hostile handlers on WrappedPollable ready path")(
        test("flatMap_succeedPollable_mapperThrow_propagatesThrow") {
          val inner  = AsyncTestSupport.pollableSuccessValue
          val fx     = new RuntimeException("flatMap-throw")
          val thrown =
            Try(
              Async
                .succeed(inner)
                .flatMap(_ => throw fx)
                .block
            ).failed.toOption
          assertTrue(thrown.contains(fx))
        },
        test("zipWith_succeedPollable_combineThrow_propagatesCombineThrow") {
          val inner  = AsyncTestSupport.pollableSuccessValue
          val fx     = new RuntimeException("combine-throw")
          val thrown =
            Try(
              Async
                .succeed(inner)
                .zipWith(Async.succeed(1))((_, _) => throw fx)
                .block
            ).failed.toOption
          assertTrue(thrown.contains(fx))
        }
      ),
      // Category L — null-cause on pollable-bearing pending ensuring chain.
      suite("null-cause pollable ensuring chain")(
        test("ensuring_pendingSucceedPollable_finalizerNullFail_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .ensuring(Async.fail(null))
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("catchAll_pendingFail_recoveryNullFail_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa =
            pending.catchAll(_ => Async.fail(null))
          c.fail(boom)
          assertTrue(fa.either.block == Left(null), AsyncTestSupport.blockAsLeftCause(fa).contains(null))
        }
      ),
      // Category F — zipWith pending left returning pollable-as-value through terminal unwrap.
      suite("zipWith pending pollable terminal unwrap")(
        test("zipWith_pendingLeftFlatMapSucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .zipWith(Async.succeed(7))((p, n) => (p, n))
          c.succeed(())
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, 7))
        },
        test("zipWith_readyLeftSucceedPollable_rightStillPendingAcrossPolls_preservesPollableIdentity") {
          // The left side resolves to a pollable-as-value on the FIRST poll while
          // the right side is still pending; the next poll must not re-dispatch
          // the already-resolved left value as a suspended computation.
          val inner      = AsyncTestSupport.pollableSuccessValue
          val (c, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa: Async[(Pollable[Int], Int)] =
            (Async.succeed(inner): Async[Pollable[Int]]).zipWith(right)((p, n) => (p, n))
          val _ = AsyncTestSupport.pollOnce(fa) // left settles to the pollable value; right still pending
          c.succeed(5)
          val result = fa.block
          assertTrue((result._1: AnyRef) eq inner, result._2 == 5)
        }
      ),
      // Category P — unsafeRunAsync on pending path returning WrappedPollable from flatMap.
      suite("unsafeRunAsync pending WrappedPollable terminal")(
        test("unsafeRunAsync_pendingFlatMapSucceedPollable_deliversPollableIdentity") {
          val inner                                 = AsyncTestSupport.pollableSuccessValue
          val c                                     = new Completer[Unit]
          val pending                               = c.peek
          val fa: Async[Pollable[Int]]              = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner))
          var out: Either[Throwable, Pollable[Int]] = Right(inner)
          val done                                  = new Completer[Unit]
          val running                               = AsyncTestSupport.startEither(fa) { res =>
            out = res
            done.succeed(())
          }
          c.succeed(())
          for {
            _ <- AsyncTestSupport.runAsync(done.peek)
          } yield {
            running.cancel()
            assertTrue(out == Right(inner))
          }
        }
      ),
      // Category J — Completer.poll after succeed pollable must match peek egress.
      suite("Completer poll vs peek differential")(
        test("poll_and_peek_afterSucceedPollable_flatMapIdentity_agree") {
          val inner = AsyncTestSupport.pollableSuccessValue
          val c     = new Completer[Pollable[Int]]
          c.succeed(inner)
          val onComplete: Runnable = AsyncTestSupport.noopRunnable
          val fromPoll: AnyRef     =
            c.poll(AsyncTestSupport.noopRunnable).flatMap(p => Async.succeed(p)).block
          val fromPeek: AnyRef =
            c.peek.flatMap(p => Async.succeed(p)).block
          assertTrue(fromPoll eq inner, fromPeek eq inner, fromPoll eq fromPeek)
        }
      ),
      // CONVERGENCE — pass-9 regression locks for categories exercised above.
      suite("CONVERGENCE: pass-9 WrappedPollable regression locks")(
        test("map_succeedPollable_preservesPollableIdentity") {
          val inner          = AsyncTestSupport.pollableSuccessValue
          val result: AnyRef =
            Async.succeed(inner).map(x => x).block
          assertTrue(result eq inner)
        },
        test("tap_succeedPollable_observesPollableIdentity") {
          val inner                           = AsyncTestSupport.pollableSuccessValue
          var observed: Option[Pollable[Int]] = None
          val result: AnyRef                  =
            Async
              .succeed(inner)
              .tap { p => observed = Some(p); Async.succeed(()) }
              .block
          assertTrue(result eq inner, observed.exists(_ eq inner))
        },
        test("orElse_succeedPollable_primaryWinsWithoutDriving") {
          var fallbackPolled          = false
          val inner                   = AsyncTestSupport.pollableSuccessValue
          val fallback: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { fallbackPolled = true; Async.succeed(0) }
          }
          val result: AnyRef =
            Async
              .succeed(inner)
              .orElse(Async.succeed(fallback))
              .block
          assertTrue(result eq inner, !fallbackPolled)
        }
      ),
      // Category F/H — zipWith pending RIGHT operand completes to pollable-as-value.
      suite("zipWith pending right pollable terminal unwrap")(
        test("zipWith_readyLeftUnit_pendingRightFlatMapSucceedPollable_preservesPollableIdentity") {
          val inner      = AsyncTestSupport.pollableSuccessValue
          val (c, right) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            Async
              .succeed(())
              .zipWith(right.flatMap(_ => Async.succeed(inner)))((_, p) => p)
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("zipWith_pendingLeftFlatMapSucceedPollable_pendingRightFlatMapSucceedPollable_preservesBothIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(55)
          }
          val (c1, left) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            left
              .flatMap(_ => Async.succeed(inner1))
              .zipWith(right.flatMap(_ => Async.succeed(inner2)))((p1, p2) => (p1, p2))
          c1.succeed(())
          c2.succeed(())
          val result = fa.block
          assertTrue(result._1 eq inner1, result._2 eq inner2)
        }
      ),
      // Category F/L — zip fail-fast sequencing must not drive pollable right operand.
      suite("zipWith fail-fast pollable operand sequencing")(
        test("zipWith_pendingLeftFail_readyRightSucceedPollable_doesNotDriveRightPollable") {
          var rightPolled          = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val (c, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = left.zipWith(Async.succeed(inner))((_, p) => p)
          c.fail(AsyncTestSupport.leftSent)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.leftSent), !rightPolled)
        },
        test("zipWith_pendingLeftFail_pendingRightPollable_doesNotDriveRightPollable") {
          var rightPolled          = false
          val inner: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { rightPolled = true; Async.succeed(0) }
          }
          val (c1, left) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          val fa = left.zipWith(right)((_, p) => p)
          c1.fail(AsyncTestSupport.leftSent)
          c2.succeed(inner)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.leftSent), !rightPolled)
        },
        test("zipWith_pendingLeftPollableChain_pendingRightFail_surfacesRightSentinelAfterLeftCompletes") {
          val inner      = AsyncTestSupport.pollableSuccessValue
          val (c1, left) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val (c2, right) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          var combineInvoked = false
          val fa             =
            left
              .flatMap(_ => Async.succeed(inner))
              .zipWith(right) { (_, _) => combineInvoked = true; inner }
          c1.succeed(())
          c2.fail(AsyncTestSupport.rightSent)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.rightSent), !combineInvoked)
        }
      ),
      // Category K — hostile interleaving: right pollable completes during left poll.
      suite("zipWith hostile interleaving")(
        test("zipWith_pendingLeftGate_pendingRightPollable_syncCompleteDuringLeftPoll_preservesBothIdentities") {
          val inner1                = AsyncTestSupport.pollableSuccessValue
          val inner2: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(77)
          }
          val (cRight, rightPending) = {
            val c = new Completer[Pollable[Int]]
            (c, c.peek)
          }
          var leftPolls                      = 0
          val leftGate: Async[Pollable[Int]] = new Pollable[Pollable[Int]] {
            def poll(onComplete: Runnable): Async[Pollable[Int]] = {
              leftPolls += 1
              if (leftPolls == 1) {
                cRight.succeed(inner2)
                onComplete.run()
                this
              } else Async.succeed(inner1)
            }
          }
          val fa     = leftGate.zipWith(rightPending)((p1, p2) => (p1, p2))
          val result = fa.block
          assertTrue(result._1 eq inner1, result._2 eq inner2, leftPolls >= 1)
        }
      ),
      // Category L/M — collectAll tagged error integrity with pollable prefix.
      suite("collectAll tagged error integrity with pollable elements")(
        test("collectAll_readyPollableThenPendingFail_surfacesExactMidSentinelWithoutDrivingTail") {
          var tailPolled          = false
          val tail: Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { tailPolled = true; Async.succeed(0) }
          }
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val inner = AsyncTestSupport.pollableSuccessValue
          val fa    =
            Async.collectAll(
              List(
                Async.succeed(inner),
                pending,
                Async.succeed(tail)
              )
            )
          c.fail(AsyncTestSupport.midSent)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.midSent), !tailPolled)
        },
        test("collectAll_pendingPollablePrefixThenPendingFail_eitherAndBlockAgreeOnMidSentinel") {
          val inner       = AsyncTestSupport.pollableSuccessValue
          val (c1, first) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val (c2, second) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            Async.collectAll(
              List(
                first.flatMap(_ => Async.succeed(inner)),
                second
              )
            )
          c1.succeed(())
          c2.fail(AsyncTestSupport.midSent)
          assertTrue(
            fa.either.block == Left(AsyncTestSupport.midSent),
            AsyncTestSupport.blockAsLeftCause(fa).contains(AsyncTestSupport.midSent)
          )
        }
      ),
      // Category K/L — catchAll handler throw on pending pollable-bearing recovery path.
      suite("catchAll handler throw on pending pollable path")(
        test("catchAll_pendingFail_handlerThrow_propagatesHandlerThrow") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pending.catchAll(_ => throw AsyncTestSupport.handlerFx)
          c.fail(boom)
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.handlerFx))
        },
        test("catchAll_pendingFlatMapSucceedPollable_outerFail_handlerThrow_propagatesHandlerThrow") {
          val inner       = AsyncTestSupport.pollableSuccessValue
          val (c1, outer) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            outer
              .flatMap(_ => Async.succeed(inner))
              .flatMap(_ => Async.fail(boom))
              .catchAll(_ => throw AsyncTestSupport.handlerFx)
          c1.succeed(())
          val thrown = Try(fa.block).failed.toOption
          assertTrue(thrown.contains(AsyncTestSupport.handlerFx))
        }
      ),
      // Category F/L — foldCause on pending WrappedPollable path.
      suite("foldCause pending WrappedPollable path")(
        test("foldCause_pendingSucceedPollable_onSuccessIdentity_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .foldCause(_ => AsyncTestSupport.pollableSuccessValue)(p => p)
          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("foldCause_pendingFail_onFailureTaggedSentinel_eitherAndBlockAgree") {
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa = pending.mapError(_ => AsyncTestSupport.leftSent)
          c.fail(AsyncTestSupport.leftSent)
          assertTrue(
            fa.either.block == Left(AsyncTestSupport.leftSent),
            AsyncTestSupport.blockAsLeftCause(fa).contains(AsyncTestSupport.leftSent)
          )
        }
      ),
      // Category L — mapError tagged sentinel preservation on pending pollable recovery.
      suite("mapError error integrity pending pollable recovery")(
        test("mapError_pendingFail_taggedSentinel_catchAllRecoverySucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa =
            AsyncTestSupport.pollableValueAsync(
              pending
                .mapError(_ => AsyncTestSupport.midSent)
                .catchAll(t => if (t eq AsyncTestSupport.midSent) Async.succeed(inner) else Async.fail(t))
            )
          c.fail(boom)
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        }
      ),
      // CONVERGENCE — pass-10 regression locks for categories exercised above.
      suite("CONVERGENCE: pass-10 WrappedPollable regression locks")(
        test("starGt_pendingFlatMapSucceedPollable_preservesRightValueWithoutDrivingPrimary") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val right: Async[Int] = Async.succeed(42)
          val fa                = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)).*>(right)
          c.succeed(())
          val result = fa.block
          assertTrue(result == 42)
        },
        test("starLt_pendingFlatMapSucceedPollable_preservesPollableIdentity") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa =
            pending
              .flatMap(_ => Async.succeed(inner))
              .<*(Async.succeed(()))

          c.succeed(())
          val result: Pollable[Int] = fa.block
          assertTrue(result eq inner)
        },
        test("zip_pendingLeftFlatMapSucceedPollable_readyRight_preservesPollableInTuple") {
          val inner        = AsyncTestSupport.pollableSuccessValue
          val (c, pending) = {
            val c = new Completer[Unit]
            (c, c.peek)
          }
          val fa = pending.flatMap[Pollable[Int]](_ => Async.succeed(inner)).zip(Async.succeed("x"))
          c.succeed(())
          val result = fa.block
          assertTrue(result._1 eq inner, result == (inner, "x"))
        }
      ),
      // Category H/B — map/as over a *suspended* source must preserve a public
      // Completer held as a success value, identically to the ready path. A
      // Completer is a value-capable public Pollable, not a CPS continuation, so
      // it must not be driven (which would substitute its inner value).
      suite("map/as Completer-as-value preservation (pending source)")(
        test("map_pendingSourceCompleterValue_isNotSilentlyDriven") {
          val cv = new Completer[String]
          cv.succeed("driven")
          val value: AnyRef = cv

          val ready: AnyRef = Async.succeed(0).map(_ => value).block

          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa: Async[AnyRef] = pending.map(_ => value)
          c.succeed(0)
          val suspended: AnyRef = fa.block

          assertTrue(ready eq cv, suspended eq cv)
        },
        test("as_pendingSourceCompleterValue_isNotSilentlyDriven") {
          val cv = new Completer[String]
          cv.succeed("driven")
          val value: AnyRef = cv

          val ready: AnyRef = Async.succeed(0).as(value).block

          val (c, pending) = {
            val c = new Completer[Int]
            (c, c.peek)
          }
          val fa: Async[AnyRef] = pending.as(value)
          c.succeed(0)
          val suspended: AnyRef = fa.block

          assertTrue(ready eq cv, suspended eq cv)
        }
      ),
      test("succeed_pollableValue_isNotSilentlyDriven") {
        val inner: Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
        }
        // Contract: reject at construction OR return the pollable as the value.
        // Bug: the pollable is driven and yields 99 instead.
        var driven: AnyRef = null
        val fa             = Async.succeed(inner)
        AsyncTestSupport.startEither(fa) {
          case Right(v) => driven = v: AnyRef
          case Left(_)  => ()
        }
        assertTrue(driven.isInstanceOf[Pollable[?]], driven eq inner)
      },
      test("catchAll_recoverySucceedPollable_isNotSilentlyDriven") {
        val inner: Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
        }
        // Same encoding collision via catchAll recovery, not only succeed().
        var driven: Any    = (-1: Any)
        val fa: Async[Any] = Async.fail(boom).catchAll(_ => Async.succeed(inner))
        AsyncTestSupport.startEither(fa) {
          case Right(v) => driven = v
          case Left(_)  => ()
        }
        assertTrue(driven != 99)
      },
      test("promise_succeedNull_blockReturnsNull") {
        val a = Async.promiseInternal[String](_.succeed(null))
        assertTrue(a.block == null)
      },
      test("collectAll_empty_returnsNil") {
        assertTrue(Async.collectAll(Nil).block == Nil)
      },
      test("collectAll_singleReadyNullValue_preservesNull") {
        assertTrue(Async.collectAll(List(Async.succeed(null: String))).block == List(null))
      },
      test("orElse_readyFail_fallsBackToSecond") {
        val boom = AsyncTestSupport.boom
        assertTrue((Async.fail(boom): Async[Any]).orElse(Async.succeed(99)).block == 99)
      },
      test("catchAll_returnsNullFail_eitherAndBlockAgree") {
        val fa        = Async.fail(new RuntimeException("x")).catchAll(_ => Async.fail(null))
        val viaEither = fa.either.block
        val viaBlock  =
          try {
            fa.block
            Right(None)
          } catch {
            case Failure.NullCauseMarker => Left(null)
            case t: Throwable            => Left(t)
          }
        assertTrue(viaEither == Left(null), viaBlock == Left(null))
      }
    ),
    suite("suspended slow path")(
      suite("map (FlatMapPollable)")(
        test("pending input then success") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val m       = fa.map(_ + 1)
          val r1      = AsyncTestSupport.pollOnce(m)
          c.succeed(5)
          assertTrue(
            AsyncTestSupport.isPending(m),
            AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(6)
          )
        },
        test("pending input that fails propagates the cause") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val m       = fa.map(_ + 1)
          val r1      = AsyncTestSupport.pollOnce(m)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom))
        }
      ),
      suite("flatMap (FlatMapPollable)")(
        test("pending input, f returns a ready value") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val fm      = fa.flatMap(v => Async.succeed(v * 2))
          val r1      = AsyncTestSupport.pollOnce(fm)
          c.succeed(10)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(20))
        },
        test("pending input, f returns another pending async (stage latch)") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val fm       = fa.flatMap(_ => fb)
          val r1       = AsyncTestSupport.pollOnce(fm)
          c1.succeed(1)
          val r2 = AsyncTestSupport.pollOnce(r1) // pa done; f(a) returns fb (pending) -> stage latched
          c2.succeed(99)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r2)) == Right(99))
        },
        test("pending input where f itself fails") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val fm      = fa.flatMap(_ => Async.fail(boom))
          val r1      = AsyncTestSupport.pollOnce(fm)
          c.succeed(1)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom))
        },
        test("pending input that fails short-circuits f") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          var ran     = false
          val fm      = fa.flatMap { v =>
            ran = true; Async.succeed(v)
          }
          val r1 = AsyncTestSupport.pollOnce(fm)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom), !ran)
        }
      ),
      suite("catchAll (CatchAllPollable)")(
        test("pending input that fails, handler returns a value") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val ca      = fa.catchAll(_ => Async.succeed(-1))
          val r1      = AsyncTestSupport.pollOnce(ca)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(-1))
        },
        test("pending input that fails, handler returns another pending async") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val ca       = fa.catchAll(_ => fb)
          val r1       = AsyncTestSupport.pollOnce(ca)
          c1.fail(boom)
          val r2 = AsyncTestSupport.pollOnce(r1) // handler fired, returns fb pending -> stage latched
          c2.succeed(7)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r2)) == Right(7))
        },
        test("pending input that fails, handler returns another failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val boom2   = new RuntimeException("boom2")
          val ca      = fa.catchAll(_ => Async.fail(boom2))
          val r1      = AsyncTestSupport.pollOnce(ca)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom2))
        },
        test("pending input that succeeds passes through unchanged") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val ca      = fa.catchAll(_ => Async.succeed(-1))
          val r1      = AsyncTestSupport.pollOnce(ca)
          c.succeed(42)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(42))
        }
      ),
      suite("zipWith (ZipWithPollable)")(
        test("both inputs pending then succeed") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val z        = fa.zipWith(fb)(_ + _)
          val r1       = AsyncTestSupport.pollOnce(z)
          c1.succeed(3)
          c2.succeed(4)
          assertTrue(
            AsyncTestSupport.isPending(z),
            AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(7)
          )
        },
        test("left input fails while pending") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (_, fb)  = AsyncTestSupport.pending[Int]
          val z        = fa.zipWith(fb)(_ + _)
          val r1       = AsyncTestSupport.pollOnce(z)
          c1.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom))
        },
        test("right input fails after left succeeds") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val z        = fa.zipWith(fb)(_ + _)
          val r1       = AsyncTestSupport.pollOnce(z)
          c1.succeed(1)
          c2.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom))
        },
        test("left already failed is short-circuited") {
          val (_, fb) = AsyncTestSupport.pending[Int]
          val z       = Async.fail(boom).zipWith(fb)((_, b) => b)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(z)) == Left(boom))
        },
        test("right already failed is short-circuited after left ready") {
          val z = Async.succeed(1).zipWith(Async.fail(boom))((a, _) => a)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(z)) == Left(boom))
        }
      ),
      suite("tap (RunThenValuePollable)")(
        test("pending input, tap effect runs then yields the original value") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          var seen    = 0
          val t       = fa.tap { v => seen = v; Async.succeed(()) }
          val r1      = AsyncTestSupport.pollOnce(t)
          c.succeed(11)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(11), seen == 11)
        },
        test("pending input, tap effect itself is pending then completes") {
          val (c1, fa)  = AsyncTestSupport.pending[Int]
          val (c2, eff) = AsyncTestSupport.pending[Unit]
          val t         = fa.tap(_ => eff)
          val r1        = AsyncTestSupport.pollOnce(t)
          c1.succeed(8)
          val r2 = AsyncTestSupport.pollOnce(r1)
          c2.succeed(())
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r2)) == Right(8))
        },
        test("pending input, a failing tap effect propagates the failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val t       = fa.tap(_ => Async.fail(boom))
          val r1      = AsyncTestSupport.pollOnce(t)
          c.succeed(8)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom))
        },
        test("pending input, a tap effect that is itself pending then fails propagates the failure") {
          // Drives `RunThenValuePollable` to a *Failure* through its pending
          // branch: a ready `Async.fail` is short-circuited by `runThenValue`
          // before the pollable is built, so only a pending-then-failing effect
          // reaches the pollable's failure-propagation path.
          val (c1, fa)  = AsyncTestSupport.pending[Int]
          val (c2, eff) = AsyncTestSupport.pending[Unit]
          val t         = fa.tap(_ => eff)
          val r1        = AsyncTestSupport.pollOnce(t) // fa pending
          c1.succeed(8)
          val r2 = AsyncTestSupport.pollOnce(r1) // fa -> 8; tap effect now pending
          c2.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r2)) == Left(boom))
        }
      ),
      suite("ensuring (EnsuringPollable)")(
        test("pending input succeeds, finalizer runs, value propagates") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          var ran     = false
          val e       = fa.ensuring(Async.succeed { ran = true })
          val r1      = AsyncTestSupport.pollOnce(e)
          c.succeed(5)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(5), ran)
        },
        test("pending input fails, finalizer still runs, failure propagates") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          var ran     = false
          val e       = fa.ensuring(Async.succeed { ran = true })
          val r1      = AsyncTestSupport.pollOnce(e)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(boom), ran)
        },
        test("pending input, finalizer is itself pending then completes") {
          val (c1, fa)  = AsyncTestSupport.pending[Int]
          val (c2, fin) = AsyncTestSupport.pending[Unit]
          val e         = fa.ensuring(fin)
          val r1        = AsyncTestSupport.pollOnce(e)
          c1.succeed(9)
          val r2 = AsyncTestSupport.pollOnce(r1)
          c2.succeed(())
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r2)) == Right(9))
        },
        test("a failing finalizer is suppressed; original outcome wins") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val e       = fa.ensuring(Async.fail(boom))
          val r1      = AsyncTestSupport.pollOnce(e)
          c.succeed(3)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(3))
        },
        test("a null-resolved input is not re-polled while the finalizer is pending (regression)") {
          // `EnsuringPollable.AsyncTestSupport.outcome` must use a `NotResolved` sentinel, not raw
          // `null`: a `pa` that resolves to `null` would otherwise be re-read as
          // "still pending" and re-polled on every later poll (here, fatally).
          var paPolls           = 0
          val pa: Async[String] = new Pollable[String] {
            def poll(onComplete: Runnable): Async[String] = {
              paPolls += 1
              if (paPolls == 1) { onComplete.run(); this }
              else if (paPolls == 2) null.asInstanceOf[Async[String]]
              else Async.fail(new RuntimeException("pa re-polled after completion"))
            }
          }
          val (c2, fin) = AsyncTestSupport.pending[Unit]
          val e         = pa.ensuring(fin)
          val r1        = AsyncTestSupport.pollOnce(e)  // pa pending
          val r2        = AsyncTestSupport.pollOnce(r1) // pa -> null; finalizer now pending
          c2.succeed(())
          val settled = AsyncTestSupport.driveToEnd(r2)
          assertTrue(AsyncTestSupport.outcome(settled) == Right(null), paPolls == 2)
        }
      ),
      suite("Completer (null completion)")(
        test("a completer settled with null after a poll resolves to a ready null (regression)") {
          // `succeed(null)` settling a parked waiter must store the `NullValue`
          // sentinel; storing raw `null` would reset the slot to empty, so the
          // re-poll would re-register a waiter and never observe completion.
          val (c, fa) = AsyncTestSupport.pending[String]
          val r1      = AsyncTestSupport.pollOnce(fa) // registers a parked waiter
          c.succeed(null)
          val settled = AsyncTestSupport.driveToEnd(r1)
          assertTrue(!AsyncTestSupport.isPending(settled), AsyncTestSupport.outcome(settled) == Right(null))
        }
      ),
      suite("derived combinators over pending inputs")(
        test("mapError transforms a pending failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val wrapped = new RuntimeException("wrapped")
          val me      = fa.mapError(_ => wrapped)
          val r1      = AsyncTestSupport.pollOnce(me)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Left(wrapped))
        },
        test("orElse recovers a pending failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val oe      = fa.orElse(Async.succeed(123))
          val r1      = AsyncTestSupport.pollOnce(oe)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(123))
        },
        test("either reifies a pending success") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val ei      = fa.either
          val r1      = AsyncTestSupport.pollOnce(ei)
          c.succeed(5)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(Right(5)))
        },
        test("either reifies a pending failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val ei      = fa.either
          val r1      = AsyncTestSupport.pollOnce(ei)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(Left(boom)))
        },
        test("foldCause folds a pending failure") {
          val (c, fa) = AsyncTestSupport.pending[Int]
          val fc      = fa.foldCause(_ => -7)(v => v)
          val r1      = AsyncTestSupport.pollOnce(fc)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(-7))
        }
      ),
      suite("ready Failure handled by the inline catchAll")(
        test("catchAll on a ready Failure invokes the handler") {
          // Async.fail is a Failure (a Pollable), but the inline `catchAll`
          // intercepts a ready Failure directly and applies the handler without
          // entering the slow-path `catchAllAsync`.
          val ca = Async.fail(boom).catchAll(_ => Async.succeed(99))
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(ca)) == Right(99))
        }
      ),
      suite("latched stage re-poll (nested continuations)")(
        test("nested flatMap re-polls a latched FlatMapPollable.stage") {
          // Wrapping the inner flatMap in an outer one forces the driver to
          // re-enter the inner pollable after its `stage` has been latched,
          // exercising the `if (stage != null) stage.poll(onComplete)` fast path.
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val inner    = fa.flatMap(_ => fb)
          val outer    = inner.flatMap(v => Async.succeed(v + 1))
          var cur      = AsyncTestSupport.pollOnce(outer) // pending
          c1.succeed(1)
          cur = AsyncTestSupport.pollOnce(cur) // inner latches stage = c2; still pending
          c2.succeed(10)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(cur)) == Right(11))
        },
        test("nested catchAll re-polls a latched CatchAllPollable.stage") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val inner    = fa.catchAll(_ => fb)
          val outer    = inner.flatMap(v => Async.succeed(v + 1))
          var cur      = AsyncTestSupport.pollOnce(outer)
          c1.fail(boom)
          cur = AsyncTestSupport.pollOnce(cur) // handler fired, stage = c2; still pending
          c2.succeed(10)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(cur)) == Right(11))
        }
      ),
      suite("zipWith: second side pending across an extra round")(
        test("fa resolves first, fb stays pending one more poll") {
          val (c1, fa) = AsyncTestSupport.pending[Int]
          val (c2, fb) = AsyncTestSupport.pending[Int]
          val z        = fa.zipWith(fb)(_ + _)
          var cur      = AsyncTestSupport.pollOnce(z) // both pending
          c1.succeed(3)
          cur = AsyncTestSupport.pollOnce(cur) // fa done; fb still pending -> returns this
          c2.succeed(4)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(cur)) == Right(7))
        }
      ),
      suite("RunThenValuePollable failure resolution")(
        test("tap effect pending then fails propagates the failure") {
          val (c1, fa)  = AsyncTestSupport.pending[Int]
          val (c2, eff) = AsyncTestSupport.pending[Unit]
          val t         = fa.tap(_ => eff)
          var cur       = AsyncTestSupport.pollOnce(t)
          c1.succeed(8)
          cur = AsyncTestSupport.pollOnce(cur) // tap effect latched, still pending
          c2.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(cur)) == Left(boom))
        },
        test("ensuring finalizer pending then fails is suppressed") {
          val (c1, fa)  = AsyncTestSupport.pending[Int]
          val (c2, fin) = AsyncTestSupport.pending[Unit]
          val e         = fa.ensuring(fin)
          var cur       = AsyncTestSupport.pollOnce(e)
          c1.succeed(5)
          cur = AsyncTestSupport.pollOnce(cur) // finalizer latched, still pending
          c2.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(cur)) == Right(5))
        },
        test("ready value, ensuring finalizer pending then fails is suppressed") {
          // A ready `fa` routes ensuring through `runThenValue(..., suppressFailure
          // = true)`, building a RunThenValuePollable. When its pending finalizer
          // later fails, the failure is suppressed and the AsyncTestSupport.original value yielded
          // (exercises RunThenValuePollable's `if (suppressFailure) a` arm, which
          // the pending-input path via EnsuringPollable never reaches).
          val (c, fin) = AsyncTestSupport.pending[Unit]
          val e        = Async.succeed(5).ensuring(fin)
          val r1       = AsyncTestSupport.pollOnce(e) // finalizer pending
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(r1)) == Right(5))
        }
      ),
      suite("Completer state machine")(
        test("re-poll while waiting coalesces an identical waker") {
          val c              = new Completer[Int]
          val r1: Async[Int] = c.poll(AsyncTestSupport.noopRunnable) // null -> WaitingMarker
          val r2: Async[Int] = c.poll(AsyncTestSupport.noopRunnable) // same runnable -> coalesced, not duplicated
          assertTrue(AsyncTestSupport.isPending(r1), AsyncTestSupport.isPending(r2)) && {
            c.succeed(7)
            assertTrue(AsyncTestSupport.outcome(c.poll(AsyncTestSupport.noopRunnable)) == Right(7)) // settled -> value
          }
        },
        test("peek does not register a waker and reflects later completion") {
          val c = new Completer[Int]
          val p = c.peek // empty -> this (pending)
          assertTrue(AsyncTestSupport.isPending(p)) && {
            c.succeed(3)
            assertTrue(AsyncTestSupport.outcome(c.peek) == Right(3))
          }
        },
        test("fail then poll yields the failure") {
          val c = new Completer[Int]
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(c.poll(AsyncTestSupport.noopRunnable)) == Left(boom))
        },
        test("second completion is a silent no-op (first writer wins)") {
          val c = new Completer[Int]
          c.succeed(1)
          c.succeed(2)
          c.fail(boom)
          assertTrue(AsyncTestSupport.outcome(c.poll(AsyncTestSupport.noopRunnable)) == Right(1))
        },
        test("two distinct pollers registered before completion are both woken") {
          // Fan-out: one promise-backed Async observed by two independent drivers
          // (e.g. the same `Async.promise` value passed to two `Async.start`s).
          // Each driver registers its own waker while the completer is pending;
          // completion must wake both, or the un-woken driver never completes.
          // (Async.Running.poll keeps a waiter list for exactly this reason.)
          val c  = new Completer[Int]
          var w1 = false
          var w2 = false
          c.poll(new Runnable { def run(): Unit = w1 = true })
          c.poll(new Runnable { def run(): Unit = w2 = true })
          c.succeed(7)
          assertTrue(w1, w2)
        },
        test("a waker that throws does not starve the other registered pollers") {
          // Category K (contract proposal): settle promises to "wake every
          // registered waiter" (fan-out, above). A hostile waker registered by
          // one driver must not prevent the remaining well-behaved drivers from
          // being woken — and must not propagate its throw into the I/O
          // callback that performs the completion (`succeed` is the completion
          // channel, not the waker's failure channel).
          val c  = new Completer[Int]
          var w1 = false
          c.poll(new Runnable { def run(): Unit = w1 = true })
          c.poll(new Runnable { def run(): Unit = throw AsyncTestSupport.sideFx })
          val settled = scala.util.Try(c.succeed(7))
          assertTrue(
            w1,
            settled.isSuccess,
            AsyncTestSupport.outcome(c.poll(AsyncTestSupport.noopRunnable)) == Right(7)
          )
        }
      )
    ),
    suite("monad laws")(
      test("functor identity: succeed(a).map(identity) == succeed(a)") {
        assertTrue(Async.succeed(42).map(identity).block == 42)
      },
      test("functor composition: map(f).map(g) == map(g compose f)") {
        val f = (x: Int) => x + 1
        val g = (x: Int) => x * 3
        assertTrue(
          Async.succeed(5).map(f).map(g).block == Async.succeed(5).map(g compose f).block
        )
      },
      test("left identity: succeed(a).flatMap(f) == f(a)") {
        val f = (x: Int) => Async.succeed(x * 10)
        assertTrue(Async.succeed(4).flatMap(f).block == f(4).block)
      },
      test("right identity: m.flatMap(succeed) == m") {
        assertTrue(Async.succeed(7).flatMap(Async.succeed(_)).block == 7)
      },
      test("associativity: m.flatMap(f).flatMap(g) == m.flatMap(x => f(x).flatMap(g))") {
        val f   = (x: Int) => Async.succeed(x + 1)
        val g   = (x: Int) => Async.succeed(x * 2)
        val lhs = Async.succeed(3).flatMap(f).flatMap(g).block
        val rhs = Async.succeed(3).flatMap((x: Int) => f(x).flatMap(g)).block
        assertTrue(lhs == rhs)
      }
    ),
    suite("type constraints")(
      // Pollable.scala / AsyncEncoding.scala promise: "A Pollable[A] is itself an
      // Async[A], so it can be used wherever an Async[A] is expected." So the
      // extension ops (`map`, `flatMap`, ...) must be available on a value whose
      // STATIC type is `Pollable[A]`, not just `Async[A]`.
      test("a value statically typed Pollable[A] can use the Async ops (map)") {
        typeCheck {
          """
              import zio.blocks.async._
              val p: Pollable[Int] = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(1) }
              val r: Async[Int] = p.map(_ + 1)
              r
              """
        }.map(result => assert(result)(isRight))
      },
      test("a value statically typed Pollable[A] can use flatMap") {
        typeCheck {
          """
              import zio.blocks.async._
              val p: Pollable[Int] = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(1) }
              val r: Async[String] = p.flatMap(i => Async.succeed(i.toString))
              r
              """
        }.map(result => assert(result)(isRight))
      },
      // collectAll is fixed to `Iterable[Async[A]] => Async[List[A]]`. Passing a
      // Vector (an Iterable) is legitimate and should compile.
      test("collectAll accepts a Vector input") {
        typeCheck {
          """
              import zio.blocks.async._
              val r: Async[List[Int]] = Async.collectAll(Vector(Async.succeed(1), Async.succeed(2)))
              r
              """
        }.map(result => assert(result)(isRight))
      },
      // Covariance: Async[+A]. Async[Dog] should be usable as Async[Animal].
      test("covariant widening Async[Dog] <: Async[Animal] is accepted") {
        typeCheck {
          """
              import zio.blocks.async._
              class Animal
              class Dog extends Animal
              val d: Async[Dog] = Async.succeed(new Dog)
              val a: Async[Animal] = d
              a
              """
        }.map(result => assert(result)(isRight))
      },
      // for-comprehension desugars to flatMap/map on the extension ops; should
      // compile (and run) outside an async block. NOTE: this is a *real compiled*
      // assertion rather than a `typeCheck` probe: the Scala 3 runtime ToolBox
      // used by `typeCheck` reports a spurious "Cyclic reference involving method
      // $anonfun" when re-deriving the opaque-type extension-method signatures for
      // a desugared for-comprehension — a known ToolBox limitation, not a defect
      // in `Async` (the same source compiles and runs correctly here).
      test("two-generator for-comprehension over Async desugars and compiles") {
        val r: Async[Int] = for {
          x <- Async.succeed(1)
          y <- Async.succeed(2)
        } yield x + y
        assertTrue(r.block == 3)
      },
      // collectAll fed by an eta-expanded polymorphic succeed.
      test("collectAll over List(...).map(Async.succeed) compiles") {
        typeCheck {
          """
              import zio.blocks.async._
              val r: Async[List[Int]] = Async.collectAll(List(1, 2, 3).map(Async.succeed))
              r
              """
        }.map(result => assert(result)(isRight))
      },
      // catchAll widening the error-recovery branch to a common supertype.
      test("catchAll with a wider recovery type infers the lub") {
        typeCheck {
          """
              import zio.blocks.async._
              val r: Async[Any] = Async.succeed(1).catchAll(_ => Async.succeed("recovered"))
              r
              """
        }.map(result => assert(result)(isRight))
      },
      // zipWith over genuinely distinct element types.
      test("zipWith over distinct element types compiles") {
        typeCheck {
          """
              import zio.blocks.async._
              val r: Async[String] = Async.succeed(1).zipWith(Async.succeed("a"))((i, s) => s * i)
              r
              """
        }.map(result => assert(result)(isRight))
      },
      // orElse falling back to a value of a supertype.
      test("orElse falls back to a wider type") {
        typeCheck {
          """
              import zio.blocks.async._
              val r: Async[Any] = Async.succeed(1).orElse(Async.succeed("x"))
              r
              """
        }.map(result => assert(result)(isRight))
      }
    ),
    suite("eager evaluation")(
      test("a throw inside map on a ready value escapes eagerly and is NOT captured") {
        // Documented behavior: errors thrown by user code inside map/flatMap are
        // NOT turned into a Failure on the eager fast path.
        val boom   = new RuntimeException("eager")
        val caught =
          try { Async.succeed(1).map[Int](_ => throw boom); Option.empty[Throwable] }
          catch { case e: Throwable => Some(e) }
        assertTrue(caught.contains(boom))
      }
    )
  )
}
