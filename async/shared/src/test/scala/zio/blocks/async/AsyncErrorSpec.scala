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

import scala.util.Try

/**
 * Error channel semantics.
 */
object AsyncErrorSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncErrorSpec")(
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
    suite("catchAll")(
      test("recovers from a synchronous failure") {
        val r = Async.fail(AsyncTestSupport.boom).catchAll(_ => Async.succeed(42)).block
        assertTrue(r == 42)
      },
      test("does not invoke the handler on success") {
        val r = Async.succeed(1).catchAll(_ => Async.succeed(99)).block
        assertTrue(r == 1)
      },
      test("the handler can itself fail") {
        val thrown = scala.util
          .Try(Async.fail(AsyncTestSupport.boom).catchAll(_ => Async.fail(AsyncTestSupport.boom2)).block)
          .failed
          .toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom2))
      },
      test("recovers from a suspended failure") {
        val pa: Async[Int] = AsyncTestSupport.failAfter(AsyncTestSupport.boom, 1)
        val r              = pa.catchAll(_ => Async.succeed(7)).block
        assertTrue(r == 7)
      },
      test("passes through a suspended success") {
        val pa: Async[Int] = AsyncTestSupport.succeedAfter(5, 1)
        val r              = pa.catchAll(_ => Async.succeed(99)).block
        assertTrue(r == 5)
      },
      test("catchAll_pendingFail_handlerPollThrow_propagatesHandlerThrow") {
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val a = pending.catchAll(_ => AsyncTestSupport.throwingRecovery)
        c.fail(AsyncTestSupport.primary)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.handlerPoll))
      },
      test("catchAll_readyFail_handlerPollThrow_propagatesHandlerThrow") {
        val thrown =
          Try(
            Async.fail(AsyncTestSupport.primary).catchAll(_ => AsyncTestSupport.throwingRecovery).block
          ).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.handlerPoll))
      }
    ),
    suite("mapError")(
      test("transforms the cause") {
        val r      = Async.fail(AsyncTestSupport.boom).mapError(_ => AsyncTestSupport.boom2)
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom2))
      },
      test("does not run on success") {
        val r = Async.succeed(10).mapError(_ => AsyncTestSupport.boom2).block
        assertTrue(r == 10)
      },
      test("mapError_nullCause_eitherReifiesLeftNull") {
        assertTrue(Async.fail(AsyncTestSupport.original).mapError(_ => null).either.block == Left(null))
      },
      test("mapError_nullCause_blockMatchesEither") {
        val fa        = Async.fail(AsyncTestSupport.original).mapError(_ => null)
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
      test("mapError_pendingNullCause_eitherAndBlockAgree") {
        val c = new Completer[Unit]
        val a = c.peek.flatMap(_ => Async.fail(null).mapError(identity))
        c.succeed(())
        val viaEither = a.either.block
        val viaBlock  =
          try {
            a.block
            Right(None)
          } catch {
            case Failure.NullCauseMarker => Left(null)
            case t: Throwable            => Left(t)
          }
        assertTrue(viaEither == Left(null), viaBlock == Left(null))
      }
    ),
    suite("orElse")(
      test("falls back to `that` on failure") {
        val r = Async.fail(AsyncTestSupport.boom).orElse(Async.succeed(99)).block
        assertTrue(r == 99)
      },
      test("ignores `that` on success") {
        val r = Async.succeed(1).orElse(Async.succeed(99)).block
        assertTrue(r == 1)
      }
    ),
    suite("foldCause")(
      test("foldCause applies onFailure on failure") {
        val r = Async.fail(AsyncTestSupport.boom).foldCause(t => s"err:${t.getMessage}")(_ => "ok").block
        assertTrue(r == "err:boom")
      },
      test("foldCause applies onSuccess on success") {
        val r = Async.succeed(7).foldCause(_ => -1)(x => x * 2).block
        assertTrue(r == 14)
      },
      test("either returns Left on failure") {
        val r = Async.fail(AsyncTestSupport.boom).either.block
        assertTrue(r == Left(AsyncTestSupport.boom))
      },
      test("either returns Right on success") {
        val r: Either[Throwable, Int] = Async.succeed(3).either.block
        assertTrue(r == Right(3))
      },
      test("foldCause_readyNullFail_onFailureReceivesNull") {
        var observed: Option[Throwable] = None
        val r                           =
          Async
            .fail(null)
            .foldCause { t => observed = Some(t); "fail" }(_ => "ok")
            .block
        assertTrue(r == "fail", observed.contains(null))
      },
      test("foldCause_pendingNullFail_onFailureReceivesNull") {
        var observed: Option[Throwable] = None
        val (c, pending)                = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val a =
          pending
            .flatMap(_ => Async.fail(null))
            .foldCause { t => observed = Some(t); "fail" }(_ => "ok")
        c.succeed(0)
        assertTrue(a.block == "fail", observed.contains(null))
      },
      test("either_pendingNullFail_reifiesLeftNull") {
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val ei = pending.flatMap(_ => Async.fail(null)).either
        c.succeed(0)
        assertTrue(ei.block == Left(null))
      }
    ),
    suite("either")(
      test("foldCause applies onFailure on failure") {
        val r = Async.fail(AsyncTestSupport.boom).foldCause(t => s"err:${t.getMessage}")(_ => "ok").block
        assertTrue(r == "err:boom")
      },
      test("foldCause applies onSuccess on success") {
        val r = Async.succeed(7).foldCause(_ => -1)(x => x * 2).block
        assertTrue(r == 14)
      },
      test("either returns Left on failure") {
        val r = Async.fail(AsyncTestSupport.boom).either.block
        assertTrue(r == Left(AsyncTestSupport.boom))
      },
      test("either returns Right on success") {
        val r: Either[Throwable, Int] = Async.succeed(3).either.block
        assertTrue(r == Right(3))
      }
    ),
    suite("map / flatMap propagate failure")(
      test("map past a fail does not invoke f") {
        var called = false
        val r      = Async.fail(AsyncTestSupport.boom).map { (_: Any) => called = true; 0 }
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom), !called)
      },
      test("flatMap past a fail does not invoke f") {
        var called = false
        val r      = Async.fail(AsyncTestSupport.boom).flatMap { (_: Any) => called = true; Async.succeed(0) }
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom), !called)
      },
      test("flatMap that itself returns a failure propagates the new cause") {
        val r      = Async.succeed(1).flatMap(_ => Async.fail(AsyncTestSupport.boom))
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.boom))
      }
    ),
    suite("null cause")(
      test("fail_nullCause_eitherReifiesLeftNull") {
        assertTrue(Async.fail(null).either.block == Left(null))
      },
      test("fail_nullCause_blockMatchesEitherChannel") {
        val viaEither = Async.fail(null).either.block
        val viaBlock  =
          try {
            Async.fail(null).block
            Right(None)
          } catch {
            case Failure.NullCauseMarker => Left(null)
            case t: Throwable            => Left(t)
          }
        assertTrue(viaEither == Left(null), viaBlock == Left(null))
      },
      test("mapError_identityOnNullCause_blockMatchesEither") {
        val fa        = Async.fail(null).mapError(identity)
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
      test("ensuring with a null finalizer cause surfaces the primary, not an addSuppressed NPE") {
        val boom   = AsyncTestSupport.boom
        val a      = Async.fail(boom).ensuring(Async.fail(null))
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(boom))
      }
    ),
    suite("poll throw")(
      test("flatMap_readyOuter_contPollThrow_propagatesThrow") {
        val thrown =
          Try(
            Async
              .succeed(1)
              .flatMap(_ => AsyncTestSupport.throwingContinuation)
              .block
          ).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.contPoll))
      },
      test("flatMap_pendingOuter_contPollThrow_propagatesThrow") {
        val (c, pending) = {
          val c = new Completer[Int]
          (c, c.peek)
        }
        val a = pending.flatMap(_ => AsyncTestSupport.throwingContinuation)
        c.succeed(1)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.contPoll))
      },
      test("flatMap_outerPollThrow_doesNotInvokeContinuation") {
        var invoked = false
        val a       =
          AsyncTestSupport.throwingOuter.flatMap(_ => Async.attempt { invoked = true; 0 })
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.outerPoll), !invoked)
      },
      test("ensuring_primaryFail_finalizerPollThrow_surfacesPrimary") {
        val a      = Async.fail(AsyncTestSupport.primary).ensuring(AsyncTestSupport.throwingFinalizer)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.primary))
      },
      test("ensuring_primarySuccess_finalizerPollThrow_surfacesPrimaryValue") {
        val a = Async.succeed(42).ensuring(AsyncTestSupport.throwingFinalizer)
        assertTrue(a.block == 42)
      },
      test("ensuring_primarySucceedPollable_finalizerPollThrow_settlesToPollableValueCarrier") {
        // Same shape as above but the primary value is itself a pollable-as-value.
        // Suppressing the finalizer defect must settle to the wrapped success
        // carrier (as the non-throwing finalizer sibling does); surfacing the bare
        // pollable instead makes the driver treat the already-settled result as a
        // still-suspended computation (block parks forever on an unarmed waker).
        val inner                    = AsyncTestSupport.pollableSuccessValue
        val fa: Async[Pollable[Int]] =
          Async.succeed(inner).ensuring(AsyncTestSupport.throwingFinalizer)
        val res = AsyncTestSupport.pollOnce(fa)
        assertTrue(
          !AsyncTestSupport.isPending(res),
          AsyncEncoding.deliverSuccess[AnyRef](res) eq inner
        )
      },
      test("ensuring_pendingPrimaryFail_finalizerPollThrow_surfacesPrimary") {
        val c = new Completer[Int]
        val a = c.peek.ensuring(AsyncTestSupport.throwingFinalizer)
        c.fail(AsyncTestSupport.primary)
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.primary))
      },
      test("ensuring_primaryFail_finalizerPollThrow_attachesFinalizerAsSuppressed") {
        val a      = Async.fail(AsyncTestSupport.primary).ensuring(AsyncTestSupport.throwingFinalizer)
        val thrown = Try(a.block).failed.toOption
        assertTrue(
          thrown.contains(AsyncTestSupport.primary),
          thrown.exists(_.getSuppressed.toList.contains(AsyncTestSupport.finPoll))
        )
      },
      test("ensuring_primaryPollThrow_stillRunsFinalizer") {
        var finRan = false
        val a      =
          AsyncTestSupport.throwingPrimary.ensuring(Async.attempt { finRan = true; () })
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.primaryPoll), finRan)
      },
      test("ensuring_primaryPollThrow_observableFinalizerEffect") {
        var finValue = -1
        val a        =
          AsyncTestSupport.throwingPrimary.ensuring(Async.succeed { finValue = 99; () })
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(AsyncTestSupport.primaryPoll), finValue == 99)
      }
    ),
    suite("ensuring suppressed errors")(
      test("a failing finalizer is attached to the primary failure as suppressed") {
        val primaryCause = new RuntimeException("primary")
        val fin          = new RuntimeException("finalizer")
        val a            = Async.fail(primaryCause).ensuring(Async.fail(fin))
        val thrown       = Try(a.block).failed.toOption
        assertTrue(
          thrown.contains(primaryCause),
          thrown.exists(_.getSuppressed.toList.contains(fin))
        )
      },
      test(
        "ensuring with the same exception as primary and finalizer surfaces the primary, not a self-suppression crash"
      ) {
        val e      = new RuntimeException("shared")
        val a      = Async.fail(e).ensuring(Async.fail(e))
        val thrown = Try(a.block).failed.toOption
        assertTrue(thrown.contains(e))
      },
      test(
        "finalizer cause that is also the primary's cause still surfaces the primary"
      ) {
        val root          = new RuntimeException("root")
        val primaryCause  = new RuntimeException("primary", root) // primaryCause.getCause eq root
        val a: Async[Int] = Async.fail(primaryCause).ensuring(Async.fail(root))
        val thrown        = Try(a.block).failed.toOption
        assertTrue(
          thrown.contains(primaryCause),
          thrown.exists(_.getSuppressed.toList.contains(root))
        )
      },
      test(
        "a suppressed-graph cycle (finalizer caused-by primary) surfaces the primary without looping"
      ) {
        val primaryCause  = new RuntimeException("primary")
        val fin           = new RuntimeException("fin", primaryCause) // fin.getCause eq primaryCause -> cycle once suppressed
        val a: Async[Int] = Async.fail(primaryCause).ensuring(Async.fail(fin))
        val thrown        = Try(a.block).failed.toOption
        // Force the cyclic graph to be walked (printStackTrace uses a dejaVu set).
        thrown.foreach { t =>
          val sw = new java.io.StringWriter
          t.printStackTrace(new java.io.PrintWriter(sw))
        }
        assertTrue(
          thrown.contains(primaryCause),
          thrown.exists(_.getSuppressed.toList.contains(fin))
        )
      },
      test("deeply nested ensuring with mixed null / same / distinct finalizer causes keeps the primary") {
        val primaryCause  = new RuntimeException("primary")
        val f1            = new RuntimeException("f1")
        val f2            = new RuntimeException("f2")
        val a: Async[Int] =
          Async
            .fail(primaryCause)
            .ensuring(Async.fail(f1))           // distinct -> attached
            .ensuring(Async.fail(primaryCause)) // same instance as primary -> skipped (guarded)
            .ensuring(Async.fail(null))         // null finalizer cause -> skipped (guarded)
            .ensuring(Async.fail(f2))           // distinct -> attached
        val thrown = Try(a.block).failed.toOption
        val supp   = thrown.toList.flatMap(_.getSuppressed.toList)
        assertTrue(
          thrown.contains(primaryCause),
          supp.contains(f1),
          supp.contains(f2)
        )
      },
      test("ensuring with a succeeding primary drops a failing finalizer without surfacing it") {
        val fin           = new RuntimeException("fin")
        val a: Async[Int] = Async.succeed(1).ensuring(Async.fail(fin))
        assertTrue(a.block == 1)
      }
    )
  )
}
