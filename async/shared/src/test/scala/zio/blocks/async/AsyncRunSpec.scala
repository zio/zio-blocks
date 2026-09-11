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

import zio.durationInt
import zio.test.Live
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

/**
 * start, driver parity, cancellation.
 */
object AsyncRunSpec extends ZIOSpecDefault {

  private sealed trait Folded[+A]
  private final case class FoldedSuccess[A](value: A)                                extends Folded[A]
  private final case class FoldedFailure(cause: Throwable, trusted: Boolean = false) extends Folded[Nothing]
  private final case class FoldedPending[A](value: Pollable[A])                      extends Folded[A]

  private def foldStep[A](async: Async[A]): Folded[A] =
    Async.foldStep(async)(new Async.StepFold[A, Folded[A]] {
      def success(value: A): Folded[A]                         = FoldedSuccess(value)
      def failure(cause: Throwable): Folded[A]                 = FoldedFailure(cause)
      override def trustedFailure(cause: Throwable): Folded[A] = FoldedFailure(cause, trusted = true)
      def pending(value: Pollable[A]): Folded[A]               = FoldedPending(value)
    })

  private def driveWithFold[A](start: Async[A], maxPolls: Int = 32): Folded[A] = {
    var current = foldStep(start)
    var polls   = 0
    while (current.isInstanceOf[FoldedPending[?]] && polls < maxPolls) {
      val pending = current.asInstanceOf[FoldedPending[A]].value
      current = foldStep(pending.poll(AsyncTestSupport.noopRunnable))
      polls += 1
    }
    current
  }

  private final class StepChain(remaining: Int, taken: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (remaining <= 0) Async.succeed(taken)
      else {
        onComplete.run()
        new StepChain(remaining - 1, taken + 1)
      }
  }

  private final class DoubleWake extends Pollable[Int] {
    val polls                                  = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Int] = {
      val n = polls.incrementAndGet()
      if (n == 1) { onComplete.run(); onComplete.run(); this }
      else Async.succeed(42)
    }
  }

  private final class CancelAwareNever extends Pollable[Int] {
    val polls                                  = new AtomicInteger(0)
    val cancels                                = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Int] = {
      polls.incrementAndGet()
      this
    }
    override def cancel(): Unit = cancels.incrementAndGet()
  }

  private final class CancelAwareReplacement(next: CancelAwareNever) extends Pollable[Int] {
    val polls                                  = new AtomicInteger(0)
    val cancels                                = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Int] = {
      polls.incrementAndGet()
      onComplete.run()
      next
    }
    override def cancel(): Unit = cancels.incrementAndGet()
  }

  private final class CleanupOnCancel(cleanup: Async[Unit]) extends Pollable[Int] {
    val polls                                  = new AtomicInteger(0)
    val cancellations                          = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Int] = {
      polls.incrementAndGet()
      this
    }
    override private[async] def cancelWithCleanup(): Async[Unit] = {
      cancellations.incrementAndGet()
      cleanup
    }
  }

  private final class TwoPollCleanup(failure: Throwable) extends Pollable[Unit] {
    val polls                                   = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[Unit] = {
      val current = polls.incrementAndGet()
      if (current == 1) {
        onComplete.run()
        this
      } else if (failure eq null) Async.succeed(())
      else Async.fail(failure)
    }
  }

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  def spec = suite("AsyncRunSpec")(
    suite("foldStep")(
      test("distinguishes trusted failures without classifying their Throwable shape") {
        final case class StreamErrorAnalog(error: Any) extends RuntimeException
        val same   = new RuntimeException("same")
        val shaped = StreamErrorAnalog("typed")
        assertTrue(
          foldStep[Int](Async.fail(same)) == FoldedFailure(same),
          foldStep[Int](Async.failTrusted(same)) == FoldedFailure(same, trusted = true),
          foldStep[Int](new Failure(shaped)) == FoldedFailure(shaped),
          foldStep[Int](Async.failTrusted(shaped)) == FoldedFailure(shaped, trusted = true)
        )
      },
      test("preserves trusted provenance through ready, pending, and replacement combinators") {
        val cause               = new RuntimeException("typed")
        val trusted: Async[Int] = Async.failTrusted(cause)
        val leaf                = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = trusted
        }
        val replacement = new Pollable[Int] {
          private var first                          = true
          def poll(onComplete: Runnable): Async[Int] =
            if (first) { first = false; leaf }
            else trusted
        }
        assertTrue(
          foldStep(trusted.map(_ + 1)) == FoldedFailure(cause, trusted = true),
          foldStep(trusted.flatMap(i => Async.succeed(i + 1))) == FoldedFailure(cause, trusted = true),
          driveWithFold(AsyncTestSupport.fromPollable(leaf).map(_ + 1)) == FoldedFailure(cause, trusted = true),
          driveWithFold(AsyncTestSupport.fromPollable(leaf).flatMap(i => Async.succeed(i + 1))) ==
            FoldedFailure(cause, trusted = true),
          driveWithFold(AsyncTestSupport.fromPollable(replacement).map(_ + 1)) ==
            FoldedFailure(cause, trusted = true)
        )
      },
      test("callback throws and independently returned failures stay untrusted") {
        final case class StreamErrorAnalog(error: Any) extends RuntimeException
        val thrown   = StreamErrorAnalog("thrown")
        val returned = StreamErrorAnalog("returned")
        val source   = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(1)
        }
        val failed = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.fail(new RuntimeException("source"))
        }
        val callbackThrow = AsyncTestSupport
          .fromPollable(failed)
          .catchAll(_ => throw thrown)
        val callbackFailure = AsyncTestSupport
          .fromPollable(source)
          .flatMap(_ => Async.fail(returned))
        assertTrue(
          driveWithFold(callbackThrow) == FoldedFailure(thrown),
          driveWithFold(callbackFailure) == FoldedFailure(returned)
        )
      },
      test("either and foldCause evaluate a suspended receiver expression exactly once") {
        val eitherEvaluations        = new AtomicInteger
        val foldEvaluations          = new AtomicInteger
        val eitherSource             = new Completer[Int]
        val foldSource               = new Completer[Int]
        def eitherEffect: Async[Int] = { eitherEvaluations.incrementAndGet(); eitherSource }
        def foldEffect: Async[Int]   = { foldEvaluations.incrementAndGet(); foldSource }
        val either                   = eitherEffect.either
        val folded                   = foldEffect.foldCause(_ => -1)(_ + 1)
        eitherSource.succeed(41)
        foldSource.succeed(41)
        assertTrue(
          eitherEvaluations.get() == 1,
          foldEvaluations.get() == 1,
          driveWithFold(either) == FoldedSuccess(Right(41)),
          driveWithFold(folded) == FoldedSuccess(42),
          eitherEvaluations.get() == 1,
          foldEvaluations.get() == 1
        )
      },
      test("classifies ready success, null, and failure without driving") {
        val boom = new RuntimeException("fold-step")
        assertTrue(
          foldStep(Async.succeed(42)) == FoldedSuccess(42),
          foldStep(Async.succeed[String](null)) == FoldedSuccess(null),
          foldStep[Int](Async.fail(boom)) == FoldedFailure(boom),
          foldStep[Int](Async.fail(null)) == FoldedFailure(null)
        )
      },
      test("classifies a bare pollable as pending without polling it") {
        val polls   = new AtomicInteger(0)
        val pending = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            polls.incrementAndGet()
            Async.succeed(1)
          }
        }
        assertTrue(foldStep(AsyncTestSupport.fromPollable(pending)) == FoldedPending(pending), polls.get() == 0)
      },
      test("delivers Pollable success values as data one carrier layer at a time") {
        val value  = AsyncTestSupport.pollableSuccessValue
        val once   = Async.succeed(value)
        val twice  = Async.succeed(once)
        val folded = foldStep(twice)
        assertTrue(
          foldStep(once) == FoldedSuccess(value),
          folded.isInstanceOf[FoldedSuccess[?]],
          foldStep(folded.asInstanceOf[FoldedSuccess[Async[Pollable[Int]]]].value) == FoldedSuccess(value)
        )
      },
      test("prototype loop follows replacement pending values and reentrant wakes") {
        val doubleWake = new DoubleWake
        assertTrue(
          driveWithFold(new StepChain(5, 0)) == FoldedSuccess(5),
          driveWithFold(AsyncTestSupport.fromPollable(doubleWake)) == FoldedSuccess(42),
          doubleWake.polls.get() == 2
        )
      }
    ),
    suite("deferCancelable")(
      test("construction is lazy and the first drive evaluates the thunk once") {
        val evaluated = new AtomicInteger(0)
        val deferred  = Async.deferCancelable(() => { evaluated.incrementAndGet(); 42 }, () => ())
        val before    = evaluated.get()
        val result    = driveWithFold(deferred)
        val again     = driveWithFold(deferred)
        assertTrue(before == 0, result == FoldedSuccess(42), again == FoldedSuccess(42), evaluated.get() == 1)
      },
      test("captures throws and preserves null and Pollable success values") {
        val boom     = new RuntimeException("deferred")
        val value    = AsyncTestSupport.pollableSuccessValue
        val failed   = driveWithFold(Async.deferCancelable[Int](() => throw boom, () => ()))
        val nulled   = driveWithFold(Async.deferCancelable[String](() => null, () => ()))
        val pollable = driveWithFold(Async.deferCancelable[Pollable[Int]](() => value, () => ()))
        assertTrue(failed == FoldedFailure(boom), nulled == FoldedSuccess(null), pollable == FoldedSuccess(value))
      },
      test("cancel before the first drive prevents evaluation and invokes the hook once") {
        val evaluated = new AtomicInteger(0)
        val cancelled = new AtomicInteger(0)
        val deferred  =
          Async.deferCancelable(() => { evaluated.incrementAndGet(); 42 }, () => cancelled.incrementAndGet())
        val pending = foldStep(deferred).asInstanceOf[FoldedPending[Int]].value
        pending.cancel()
        pending.cancel()
        assertTrue(
          driveWithFold(deferred, maxPolls = 1).isInstanceOf[FoldedPending[?]],
          evaluated.get() == 0,
          cancelled.get() == 1
        )
      },
      test("cancel after completion is a no-op") {
        val cancelled = new AtomicInteger(0)
        val deferred  = Async.deferCancelable(() => 42, () => cancelled.incrementAndGet())
        val pending   = foldStep(deferred).asInstanceOf[FoldedPending[Int]].value
        val result    = pending.poll(AsyncTestSupport.noopRunnable)
        pending.cancel()
        assertTrue(foldStep(result) == FoldedSuccess(42), cancelled.get() == 0)
      },
      test("caller-owned acquisition cancellation releases a reentrantly unpublished value and joins cleanup") {
        val released               = new AtomicInteger(0)
        val release                = new Completer[Unit]
        var pending: Pollable[Int] = null
        var cleanup: Async[Unit]   = null
        val acquired               = Async.acquireCancelable[Int](
          () => {
            cleanup = Async.cancelWithCleanup(pending)
            42
          },
          _ => { released.incrementAndGet(); release }
        )
        pending = foldStep(acquired).asInstanceOf[FoldedPending[Int]].value
        val result         = pending.poll(AsyncTestSupport.noopRunnable)
        val cleanupPending = foldStep(cleanup).isInstanceOf[FoldedPending[?]]
        release.succeed(())
        assertTrue(
          foldStep(result).isInstanceOf[FoldedPending[?]],
          cleanupPending,
          driveWithFold(cleanup) == FoldedSuccess(()),
          released.get() == 1
        )
      },
      test("caller-owned acquisition reports a null release effect without hanging cleanup") {
        var pending: Pollable[Int] = null
        var cleanup: Async[Unit]   = null
        val acquired               = Async.acquireCancelable[Int](
          () => {
            cleanup = Async.cancelWithCleanup(pending)
            42
          },
          _ => null
        )
        pending = foldStep(acquired).asInstanceOf[FoldedPending[Int]].value
        val result = pending.poll(AsyncTestSupport.noopRunnable)
        assertTrue(
          foldStep(result).isInstanceOf[FoldedPending[?]],
          driveWithFold(cleanup) match {
            case FoldedFailure(cause: NullPointerException, false) =>
              cause.getMessage == "acquireCancelable release returned null Async"
            case _ => false
          }
        )
      },
      test("caller-owned acquisition wakes a reentrant poller when the value is published") {
        val wakes                  = new AtomicInteger(0)
        var pending: Pollable[Int] = null
        var nestedPending          = false
        val acquired               = Async.acquireCancelable[Int](
          () => {
            nestedPending = foldStep(pending.poll(new Runnable {
              def run(): Unit = { wakes.incrementAndGet(); () }
            })).isInstanceOf[FoldedPending[?]]
            42
          },
          _ => Async.succeed(())
        )
        pending = foldStep(acquired).asInstanceOf[FoldedPending[Int]].value
        val result = pending.poll(AsyncTestSupport.noopRunnable)
        assertTrue(foldStep(result) == FoldedSuccess(42), nestedPending, wakes.get() == 1)
      },
      test("publication wins a reentrant cancellation without releasing the published value") {
        val released               = new AtomicInteger(0)
        var pending: Pollable[Int] = null
        var cleanup: Async[Unit]   = null
        val acquired               = Async.acquireCancelable[Int](
          () => {
            pending.poll(new Runnable {
              def run(): Unit = cleanup = Async.cancelWithCleanup(pending)
            })
            42
          },
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        pending = foldStep(acquired).asInstanceOf[FoldedPending[Int]].value
        val result = pending.poll(AsyncTestSupport.noopRunnable)
        assertTrue(
          foldStep(result) == FoldedSuccess(42),
          driveWithFold(cleanup) == FoldedSuccess(()),
          released.get() == 0
        )
      },
      test("caller-owned acquisition covers fresh cancellation, acquisition failure, and published replay") {
        val freshAcquires = new AtomicInteger(0)
        val freshReleases = new AtomicInteger(0)
        val fresh         = Async
          .acquireCancelable[Int](
            () => { freshAcquires.incrementAndGet(); 1 },
            _ => { freshReleases.incrementAndGet(); Async.succeed(()) }
          )
          .asInstanceOf[Pollable[Int]]
        val freshCleanup = driveWithFold(fresh.cancelWithCleanup())

        val failure = new RuntimeException("acquire")
        val failed  = Async
          .acquireCancelable[Int](() => throw failure, _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
        val failedResult  = foldStep(failed.poll(AsyncTestSupport.noopRunnable))
        val failedCleanup = driveWithFold(failed.cancelWithCleanup())

        val published = Async
          .acquireCancelable[Int](() => 42, _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
        val first                   = foldStep(published.poll(AsyncTestSupport.noopRunnable))
        val replay                  = foldStep(published.poll(AsyncTestSupport.noopRunnable))
        val afterPublicationCleanup = driveWithFold(published.cancelWithCleanup())

        assertTrue(
          freshCleanup == FoldedSuccess(()),
          freshAcquires.get() == 0,
          freshReleases.get() == 0,
          failedResult == FoldedFailure(failure),
          failedCleanup == FoldedSuccess(()),
          first == FoldedSuccess(42),
          replay == FoldedSuccess(42),
          afterPublicationCleanup == FoldedSuccess(())
        )
      }
    ),
    suite("reschedule")(
      test("direct cancellation drives cleanup through a combinator wrapper") {
        val child   = new CleanupOnCancel(Async.succeed(()))
        val wrapped = AsyncTestSupport.fromPollable(child).map(_ + 1).asInstanceOf[Pollable[Int]]
        wrapped.cancel()
        assertTrue(child.cancellations.get() == 1)
      },
      test("rescheduleKnown cancellation before scheduling cancels and joins the known child") {
        val child       = new CleanupOnCancel(new TwoPollCleanup(null))
        val rescheduled = Async.rescheduleKnown[Int](child, () => ())
        val pending     = foldStep(rescheduled).asInstanceOf[FoldedPending[Int]].value
        val cleanup     = pending.cancelWithCleanup()

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          child.cancellations.get() == 1,
          child.polls.get() == 0,
          driveWithFold(rescheduled, maxPolls = 1).isInstanceOf[FoldedPending[?]]
        )
      },
      test("rescheduleKnown replays a throwing child cancellation to every observer") {
        val failure       = new RuntimeException("known child cancellation")
        val cancellations = new AtomicInteger(0)
        val child         = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            cancellations.incrementAndGet()
            throw failure
          }
        }
        val rescheduled = Async.rescheduleKnown[Int](child, () => ()).asInstanceOf[Pollable[Int]]
        val first       = rescheduled.cancelWithCleanup()
        val second      = rescheduled.cancelWithCleanup()

        for {
          firstResult  <- AsyncTestSupport.runAsync(first).either
          secondResult <- AsyncTestSupport.runAsync(second).either
        } yield assertTrue(firstResult == Left(failure), secondResult == Left(failure), cancellations.get() == 1)
      },
      test("is lazy and evaluates its child on a fresh scheduler turn") {
        val evaluated = new AtomicInteger(0)
        val effect    = Async.reschedule { () => evaluated.incrementAndGet(); Async.succeed(42) }
        val before    = evaluated.get()
        AsyncTestSupport.runAsync(effect).map(result => assertTrue(before == 0, result == 42, evaluated.get() == 1))
      },
      test("repeated rescheduling replaces the active computation without growing a chain of runners") {
        def loop(left: Int): Async[Int] =
          if (left == 0) Async.succeed(42)
          else Async.reschedule(() => loop(left - 1))

        AsyncTestSupport.runAsync(loop(10000)).map(result => assertTrue(result == 42))
      },
      test("captures throws and preserves null successes without losing failure provenance") {
        val thrown         = new RuntimeException("rescheduled")
        val trusted        = new RuntimeException("trusted")
        val trustedRunning = Async.reschedule[Int](() => Async.failTrusted(trusted)).start
        for {
          thrownResult  <- AsyncTestSupport.runAsync(Async.reschedule[Int](() => throw thrown)).either
          nullResult    <- AsyncTestSupport.runAsync(Async.reschedule[String](() => Async.succeed(null)))
          trustedResult <-
            Live.live(
              (ZIO.sleep(1.millis) *>
                ZIO.succeed(foldStep(trustedRunning.poll(AsyncTestSupport.noopRunnable))))
                .repeatUntil(!_.isInstanceOf[FoldedPending[?]])
                .timeoutFail(new RuntimeException("trusted rescheduled failure did not settle"))(5.seconds)
            )
        } yield assertTrue(
          thrownResult == Left(thrown),
          nullResult == null,
          trustedResult == FoldedFailure(trusted, trusted = true)
        )
      },
      test("reentrant cancellation during construction signals the child once and joins cleanup") {
        val cleanup                = new Completer[Unit]
        val child                  = new CleanupOnCancel(cleanup)
        val joined                 = new AtomicReference[Async[Unit]](null)
        var pending: Pollable[Int] = null
        val effect                 = Async.reschedule { () =>
          joined.set(Async.cancelWithCleanup(pending))
          AsyncTestSupport.fromPollable(child)
        }
        pending = foldStep(effect).asInstanceOf[FoldedPending[Int]].value
        val running = AsyncTestSupport.fromPollable(pending).start
        for {
          cancelled <- Live.live(
                         (ZIO.sleep(1.millis) *> ZIO.succeed(child.cancellations.get() == 1 && joined.get() != null))
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          fiber  <- AsyncTestSupport.runAsync(joined.get()).fork
          before <- fiber.poll
          _       = cleanup.succeed(())
          done   <- Live.live(fiber.join.timeoutTo(false)(_ => true)(5.seconds))
        } yield assertTrue(
          cancelled,
          before.isEmpty,
          done,
          child.cancellations.get() == 1,
          AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
        )
      },
      test("cancellation during construction suppresses a ready child") {
        val started                = new Completer[Unit]
        val joined                 = new AtomicReference[Async[Unit]](null)
        var pending: Pollable[Int] = null
        val effect                 = Async.reschedule(
          () => {
            joined.set(Async.cancelWithCleanup(pending))
            Async.succeed(42)
          },
          () => started.succeed(())
        )
        pending = foldStep(effect).asInstanceOf[FoldedPending[Int]].value
        val first = pending.poll(AsyncTestSupport.noopRunnable)
        for {
          _       <- AsyncTestSupport.runAsync(started.peek)
          cleanup <- AsyncTestSupport.runAsync(joined.get()).either
        } yield assertTrue(
          AsyncTestSupport.isPending(first),
          cleanup == Right(()),
          foldStep(pending.poll(AsyncTestSupport.noopRunnable)) == FoldedPending(pending)
        )
      },
      test("throwing child cancellation is replayed to every cleanup observer") {
        val failure                = new RuntimeException("cancel child")
        val started                = new Completer[Unit]
        val joined                 = new AtomicReference[Async[Unit]](null)
        var pending: Pollable[Int] = null
        val child                  = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = throw failure
        }
        val effect = Async.reschedule(
          () => {
            joined.set(Async.cancelWithCleanup(pending))
            child
          },
          () => started.succeed(())
        )
        pending = foldStep(effect).asInstanceOf[FoldedPending[Int]].value
        pending.poll(AsyncTestSupport.noopRunnable)
        for {
          _      <- AsyncTestSupport.runAsync(started.peek)
          first  <- AsyncTestSupport.runAsync(joined.get()).either
          second <- AsyncTestSupport.runAsync(Async.cancelWithCleanup(pending)).either
        } yield assertTrue(first == Left(failure), second == Left(failure))
      },
      test("published child cancellation transfers atomically with handoff") {
        val child   = new CleanupOnCancel(Async.succeed(()))
        val started = new Completer[Unit]
        val effect  = Async.reschedule(() => child, () => started.succeed(()))
        val pending = foldStep(effect).asInstanceOf[FoldedPending[Int]].value
        val first   = pending.poll(AsyncTestSupport.noopRunnable)
        for {
          _          <- AsyncTestSupport.runAsync(started.peek)
          replacement =
            if (first.asInstanceOf[AnyRef] eq pending)
              pending.poll(AsyncTestSupport.noopRunnable).asInstanceOf[Pollable[Int]]
            else first.asInstanceOf[Pollable[Int]]
          wrapperCleanup <- AsyncTestSupport.runAsync(Async.cancelWithCleanup(pending))
          childCleanup   <- AsyncTestSupport.runAsync(Async.cancelWithCleanup(replacement))
        } yield assertTrue(
          replacement eq child,
          wrapperCleanup == (),
          childCleanup == (),
          child.cancellations.get() == 1
        )
      }
    ),
    suite("bracketSync")(
      test("is inert until polled, acquires/uses/releases once, and preserves null and Pollable data") {
        val events  = new scala.collection.mutable.ListBuffer[String]
        val value   = AsyncTestSupport.pollableSuccessValue
        val bracket = Async.bracketSync[String, Pollable[Int]](
          () => { events += "acquire"; null },
          r => { events += s"use:$r"; Async.succeed(value) },
          r => { events += s"release:$r"; Async.succeed(()) }
        )
        val before = events.toList
        val first  = driveWithFold(bracket)
        assertTrue(
          before.isEmpty,
          first == FoldedSuccess(value),
          events.toList == List("acquire", "use:null", "release:null")
        )
      },
      test("does not publish success until a pending release completes") {
        val release = new TwoPollCleanup(null)
        val bracket = Async.bracketSync(() => "r", (_: String) => Async.succeed(42), (_: String) => release)
        val pending = foldStep(bracket).asInstanceOf[FoldedPending[Int]].value
        val first   = foldStep(pending.poll(AsyncTestSupport.noopRunnable))
        val second  = driveWithFold(bracket)
        assertTrue(first.isInstanceOf[FoldedPending[?]], second == FoldedSuccess(42), release.polls.get() == 2)
      },
      test(
        "keeps acquire/use failure primary, suppresses release failure, and makes release failure primary otherwise"
      ) {
        val acquireFailure = new RuntimeException("acquire")
        val useFailure     = new RuntimeException("use")
        val releaseFailure = new RuntimeException("release")
        val acquireResult  = driveWithFold(
          Async.bracketSync[String, Int](
            () => throw acquireFailure,
            _ => Async.succeed(1),
            _ => Async.fail(releaseFailure)
          )
        )
        val useResult = driveWithFold(
          Async.bracketSync(() => "r", (_: String) => Async.fail(useFailure), (_: String) => Async.fail(releaseFailure))
        )
        val releaseResult = driveWithFold(
          Async.bracketSync(() => "r", (_: String) => Async.succeed(1), (_: String) => Async.fail(releaseFailure))
        )
        assertTrue(
          acquireResult == FoldedFailure(acquireFailure),
          useResult == FoldedFailure(useFailure),
          useFailure.getSuppressed().toList == List(releaseFailure),
          releaseResult == FoldedFailure(releaseFailure)
        )
      },
      test("cancel before polling skips acquire; cancel during use awaits cleanup then releases once") {
        val acquired = new AtomicInteger(0)
        val released = new AtomicInteger(0)
        val unused   = Async.bracketSync(
          () => { acquired.incrementAndGet(); "r" },
          (_: String) => Async.succeed(1),
          (_: String) => { released.incrementAndGet(); Async.succeed(()) }
        )
        val unusedPollable = foldStep(unused).asInstanceOf[FoldedPending[Int]].value
        val unusedCleanup  = unusedPollable.cancelWithCleanup()
        val leaf           = new CleanupOnCancel(new TwoPollCleanup(null))
        val used           = Async.bracketSync(
          () => "r",
          (_: String) => AsyncTestSupport.fromPollable(leaf),
          (_: String) => { released.incrementAndGet(); Async.succeed(()) }
        )
        val usedPollable = foldStep(used).asInstanceOf[FoldedPending[Int]].value
        usedPollable.poll(AsyncTestSupport.noopRunnable)
        val cleanup = usedPollable.cancelWithCleanup()
        val first   = driveWithFold(cleanup, maxPolls = 1)
        val done    = driveWithFold(cleanup)
        assertTrue(
          driveWithFold(unusedCleanup) == FoldedSuccess(()),
          acquired.get() == 0,
          first.isInstanceOf[FoldedPending[?]],
          done == FoldedSuccess(()),
          leaf.cancellations.get() == 1,
          released.get() == 1
        )
      },
      test("cancel synchronously signals the active use and remains idempotent") {
        val leaf     = new CancelAwareNever
        val released = new AtomicInteger(0)
        val bracket  = Async.bracketSync(
          () => "r",
          (_: String) => AsyncTestSupport.fromPollable(leaf),
          (_: String) => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = foldStep(bracket).asInstanceOf[FoldedPending[Int]].value
        pending.poll(AsyncTestSupport.noopRunnable)
        pending.cancel()
        pending.cancel()
        val signalledBeforeCleanup = leaf.cancels.get()
        val cleaned                = driveWithFold(pending.cancelWithCleanup())
        assertTrue(
          signalledBeforeCleanup == 1,
          cleaned == FoldedSuccess(()),
          leaf.cancels.get() == 1,
          released.get() == 1
        )
      },
      test("repeated cancellation callers join and replay pending cleanup failure") {
        val cleanupFailure = new RuntimeException("cleanup")
        val cleanup        = new TwoPollCleanup(cleanupFailure)
        val leaf           = new CleanupOnCancel(cleanup)
        val bracket        = Async.bracketSync(
          () => "r",
          (_: String) => AsyncTestSupport.fromPollable(leaf),
          (_: String) => Async.succeed(())
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val first        = pending.cancelWithCleanup()
        val second       = pending.cancelWithCleanup()
        val firstPoll    = driveWithFold(first, maxPolls = 1)
        val secondResult = driveWithFold(second)
        val firstResult  = driveWithFold(first)
        assertTrue(
          firstPoll.isInstanceOf[FoldedPending[?]],
          secondResult == FoldedFailure(cleanupFailure),
          firstResult == FoldedFailure(cleanupFailure),
          leaf.cancellations.get() == 1,
          cleanup.polls.get() == 2
        )
      },
      test("concurrent cancellation cleanup observers share one driver and replay its result") {
        val cleanup      = new TwoPollCleanup(null)
        val cancellation = Async.cancellation()
        cancellation.primary(cleanup)
        cancellation.noReplacement()
        val first  = cancellation.effect.start
        val second = cancellation.effect.start
        for {
          _ <- AsyncTestSupport.runAsync(first)
          _ <- AsyncTestSupport.runAsync(second)
        } yield assertTrue(cleanup.polls.get() == 2)
      },
      test("cancelling the first cleanup observer does not abandon the shared driver") {
        val entered = new AtomicBoolean(false)
        val gate    = new Completer[Unit]
        val polls   = new AtomicInteger(0)
        val cleanup = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = {
            polls.incrementAndGet()
            entered.set(true)
            gate.peek
          }
        }
        val cancellation = Async.cancellation()
        cancellation.primary(cleanup)
        cancellation.noReplacement()
        val first = cancellation.effect.start
        for {
          armed <- Live.live(
                     ZIO
                       .succeed(entered.get())
                       .repeatUntil(identity)
                       .timeoutTo(false)(identity)(5.seconds)
                   )
          _       = first.cancel()
          second  = cancellation.effect.start
          _       = gate.succeed(())
          joined <- Live.live(AsyncTestSupport.runAsync(second).as(true).timeoutTo(false)(identity)(5.seconds))
        } yield assertTrue(armed, joined, polls.get() == 1)
      },
      test("cancellation during acquire skips use and releases the acquired resource") {
        val used                 = new AtomicInteger(0)
        val released             = new AtomicInteger(0)
        var bracket: Async[Int]  = null
        var cleanup: Async[Unit] = null
        bracket = Async.bracketSync(
          () => {
            cleanup = bracket.asInstanceOf[Pollable[Int]].cancelWithCleanup()
            "r"
          },
          (_: String) => { used.incrementAndGet(); Async.succeed(1) },
          (_: String) => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        val result  = pending.poll(AsyncTestSupport.noopRunnable)
        val cleaned = driveWithFold(cleanup)
        assertTrue(
          AsyncTestSupport.isPending(result),
          cleaned == FoldedSuccess(()),
          used.get() == 0,
          released.get() == 1
        )
      },
      test("cancellation joins an in-flight release without cancelling or restarting it") {
        val polls    = new AtomicInteger(0)
        val cancels  = new AtomicInteger(0)
        val releases = new AtomicInteger(0)
        val release  = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] =
            if (polls.incrementAndGet() == 1) this else Async.succeed(())
          override def cancel(): Unit = cancels.incrementAndGet()
        }
        val bracket = Async.bracketSync(
          () => "r",
          (_: String) => Async.succeed(1),
          (_: String) => { releases.incrementAndGet(); release }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = driveWithFold(pending.cancelWithCleanup())
        assertTrue(
          cleanup == FoldedSuccess(()),
          releases.get() == 1,
          polls.get() == 2,
          cancels.get() == 0
        )
      },
      test("nested cancellation releases resources inner-first") {
        val order = new scala.collection.mutable.ListBuffer[String]
        val leaf  = new CancelAwareNever
        val inner = Async.bracketSync(
          () => "inner",
          (_: String) => AsyncTestSupport.fromPollable(leaf),
          (_: String) => { order += "inner"; Async.succeed(()) }
        )
        val outer = Async.bracketSync(
          () => "outer",
          (_: String) => inner,
          (_: String) => { order += "outer"; Async.succeed(()) }
        )
        val pending = outer.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = driveWithFold(pending.cancelWithCleanup())
        assertTrue(cleanup == FoldedSuccess(()), leaf.cancels.get() == 1, order.toList == List("inner", "outer"))
      },
      test("deep dynamically nested brackets are stack safe and release inner-first") {
        val depth     = 10000
        val acquired  = new AtomicInteger(0)
        val released  = new AtomicInteger(0)
        val nextOrder = new AtomicInteger(0)
        var effect    = Async.succeed(42)
        var index     = 0
        while (index < depth) {
          val inner = effect
          val id    = index
          effect = Async.bracketSync(
            () => { acquired.incrementAndGet(); id },
            (_: Int) => inner,
            (resource: Int) => {
              if (!nextOrder.compareAndSet(resource, resource + 1))
                Async.fail(new IllegalStateException(s"release order at $resource"))
              else { released.incrementAndGet(); Async.succeed(()) }
            }
          )
          index += 1
        }
        for {
          result <- AsyncTestSupport.runAsync(effect)
        } yield assertTrue(
          result == 42,
          acquired.get() == depth,
          released.get() == depth,
          nextOrder.get() == depth
        )
      },
      test("deep brackets resume a pending use and release inner-first") {
        val depth              = 10000
        val acquired           = new AtomicInteger(0)
        val released           = new AtomicInteger(0)
        val nextOrder          = new AtomicInteger(0)
        val gate               = new Completer[Int]
        var effect: Async[Int] = gate
        var index              = 0
        while (index < depth) {
          val inner = effect
          val id    = index
          effect = Async.bracketSync(
            () => { acquired.incrementAndGet(); id },
            (_: Int) => inner,
            (resource: Int) => {
              if (!nextOrder.compareAndSet(resource, resource + 1))
                Async.fail(new IllegalStateException(s"release order at $resource"))
              else { released.incrementAndGet(); Async.succeed(()) }
            }
          )
          index += 1
        }
        val running = effect.start
        for {
          allAcquired <- Live.live(
                           ZIO
                             .succeed(acquired.get() == depth)
                             .repeatUntil(identity)
                             .timeoutTo(false)(identity)(5.seconds)
                         )
          before  = released.get()
          _       = gate.succeed(42)
          result <- AsyncTestSupport.runAsync(running)
        } yield assertTrue(
          allAcquired,
          before == 0,
          result == 42,
          released.get() == depth,
          nextOrder.get() == depth
        )
      },
      test("deep bracket cancellation joins a pending use before releasing inner-first") {
        val depth              = 10000
        val acquired           = new AtomicInteger(0)
        val released           = new AtomicInteger(0)
        val nextOrder          = new AtomicInteger(0)
        val leafCleanup        = new Completer[Unit]
        val leaf               = new CleanupOnCancel(leafCleanup)
        var effect: Async[Int] = AsyncTestSupport.fromPollable(leaf)
        var index              = 0
        while (index < depth) {
          val inner = effect
          val id    = index
          effect = Async.bracketSync(
            () => { acquired.incrementAndGet(); id },
            (_: Int) => inner,
            (resource: Int) => {
              if (!nextOrder.compareAndSet(resource, resource + 1))
                Async.fail(new IllegalStateException(s"release order at $resource"))
              else { released.incrementAndGet(); Async.succeed(()) }
            }
          )
          index += 1
        }
        val running = effect.start
        for {
          usePending <- Live.live(
                          ZIO
                            .succeed(acquired.get() == depth && leaf.polls.get() > 0)
                            .repeatUntil(identity)
                            .timeoutTo(false)(identity)(5.seconds)
                        )
          cleanup       = Async.cancelWithCleanup(running)
          cleanupFiber <- AsyncTestSupport.runAsync(cleanup).fork
          cancelled    <- Live.live(
                         ZIO
                           .succeed(leaf.cancellations.get() == 1)
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          before  = released.get()
          _       = leafCleanup.succeed(())
          joined <- Live.live(cleanupFiber.join.timeoutFail(new RuntimeException("cleanup timed out"))(5.seconds))
        } yield assertTrue(
          usePending,
          cancelled,
          before == 0,
          joined == (),
          released.get() == depth,
          nextOrder.get() == depth
        )
      },
      test("deep bracket cancellation joins a pending acquisition before releasing outer resources") {
        val depth               = 10000
        val acquired            = new AtomicInteger(0)
        val acquisitionReleases = new AtomicInteger(0)
        val released            = new AtomicInteger(0)
        val nextOrder           = new AtomicInteger(0)
        val leafCleanup         = new Completer[Unit]
        val leafCancels         = new AtomicInteger(0)
        val leafPolls           = new AtomicInteger(0)
        final class LateAcquire extends Pollable[Int] {
          private var value: Async[Int]              = this
          private var waiter: Runnable               = null
          def poll(onComplete: Runnable): Async[Int] = synchronized {
            leafPolls.incrementAndGet()
            if (value.asInstanceOf[AnyRef] eq this) waiter = onComplete
            value
          }
          def succeed(result: Int): Unit = {
            val wake = synchronized {
              value = Async.succeed(result)
              val current = waiter
              waiter = null
              current
            }
            if (wake ne null) wake.run()
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            leafCancels.incrementAndGet()
            leafCleanup
          }
        }
        val leaf               = new LateAcquire
        var effect: Async[Int] = Async.bracketAsync(
          () => leaf,
          (resource: Int) => Async.succeed(resource),
          (_: Int) => { acquisitionReleases.incrementAndGet(); Async.succeed(()) }
        )
        var index = 0
        while (index < depth) {
          val inner = effect
          val id    = index
          effect = Async.bracketSync(
            () => { acquired.incrementAndGet(); id },
            (_: Int) => inner,
            (resource: Int) => {
              if (!nextOrder.compareAndSet(resource, resource + 1))
                Async.fail(new IllegalStateException(s"release order at $resource"))
              else { released.incrementAndGet(); Async.succeed(()) }
            }
          )
          index += 1
        }
        val running = effect.start
        for {
          acquisitionPending <- Live.live(
                                  ZIO
                                    .succeed(acquired.get() == depth && leafPolls.get() > 0)
                                    .repeatUntil(identity)
                                    .timeoutTo(false)(identity)(5.seconds)
                                )
          cleanup       = Async.cancelWithCleanup(running)
          cleanupFiber <- AsyncTestSupport.runAsync(cleanup).fork
          cancelled    <- Live.live(
                         ZIO
                           .succeed(leafCancels.get() == 1)
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          before  = released.get()
          _       = leafCleanup.succeed(())
          _       = leaf.succeed(-1)
          joined <- Live.live(cleanupFiber.join.timeoutFail(new RuntimeException("cleanup timed out"))(5.seconds))
        } yield assertTrue(
          acquisitionPending,
          cancelled,
          before == 0,
          joined == (),
          acquisitionReleases.get() == 1,
          released.get() == depth,
          nextOrder.get() == depth
        )
      }
    ),
    suite("cancellation cleanup")(
      test("claimed replacement is joined without cancellation signalling") {
        val polls   = new AtomicInteger(0)
        val cancels = new AtomicInteger(0)
        val claimed = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] =
            if (polls.incrementAndGet() == 1) { onComplete.run(); this }
            else Async.succeed(())
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            cancels.incrementAndGet()
            Async.succeed(())
          }
        }
        val cleanup = Async.cancellation()
        cleanup.primary(Async.succeed(()))
        cleanup.claimedReplacement(claimed)

        assertTrue(
          driveWithFold(cleanup.effect) == FoldedSuccess(()),
          polls.get() == 2,
          cancels.get() == 0
        )
      },
      test("claimed replacement failure is aggregated once after the primary failure") {
        val primaryFailure = new RuntimeException("primary")
        val claimedFailure = new RuntimeException("claimed")
        val polls          = new AtomicInteger(0)
        val cancels        = new AtomicInteger(0)
        val claimed        = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = {
            polls.incrementAndGet()
            Async.fail(claimedFailure)
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            cancels.incrementAndGet()
            Async.succeed(())
          }
        }
        val cleanup = Async.cancellation()
        cleanup.primary(Async.fail(primaryFailure))
        cleanup.claimedReplacement(claimed)
        cleanup.claimedReplacement(Async.fail(new RuntimeException("duplicate")))

        assertTrue(
          driveWithFold(cleanup.effect) == FoldedFailure(primaryFailure),
          primaryFailure.getSuppressed.toList == List(claimedFailure),
          polls.get() == 1,
          cancels.get() == 0
        )
      },
      test("duplicate primary is ignored and a late primary starts an observed cleanup") {
        val duplicate = new RuntimeException("duplicate")
        val cleanup   = Async.cancellation()
        val observed  = cleanup.effect.asInstanceOf[Pollable[Unit]].poll(AsyncTestSupport.noopRunnable)
        cleanup.noReplacement()
        cleanup.primary(Async.succeed(()))
        cleanup.primary(Async.fail(duplicate))

        assertTrue(
          observed.asInstanceOf[AnyRef] eq cleanup.effect.asInstanceOf[AnyRef],
          driveWithFold(cleanup.effect) == FoldedSuccess(())
        )
      },
      test("replacement cancellation defects and asynchronous cleanup poll defects are preserved") {
        val cancellationFailure = new RuntimeException("replacement-cancel")
        val pollFailure         = new RuntimeException("cleanup-poll")
        val throwingReplacement = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit]                  = this
          override private[async] def cancelWithCleanup(): Async[Unit] = throw cancellationFailure
        }
        val replacementCleanup = Async.cancellation()
        replacementCleanup.primary(Async.succeed(()))
        replacementCleanup.replacement(throwingReplacement)

        var wake: Runnable  = null
        val throwingCleanup = new Pollable[Unit] {
          private var first                           = true
          def poll(onComplete: Runnable): Async[Unit] =
            if (first) {
              first = false
              wake = onComplete
              this
            } else throw pollFailure
        }
        val asynchronousCleanup = Async.cancellation()
        asynchronousCleanup.primary(throwingCleanup)
        asynchronousCleanup.noReplacement()
        val pending = asynchronousCleanup.effect.asInstanceOf[Pollable[Unit]].poll(AsyncTestSupport.noopRunnable)
        wake.run()

        assertTrue(
          driveWithFold(replacementCleanup.effect) == FoldedFailure(cancellationFailure),
          pending.asInstanceOf[AnyRef] eq asynchronousCleanup.effect.asInstanceOf[AnyRef],
          driveWithFold(asynchronousCleanup.effect) == FoldedFailure(pollFailure)
        )
      },
      test("same and null cleanup failures preserve the primary without invalid suppression") {
        val same        = new RuntimeException("same")
        val sameCleanup = Async.cancellation()
        sameCleanup.primary(Async.fail(same))
        sameCleanup.claimedReplacement(Async.fail(same))
        val nullCleanup = Async.cancellation()
        nullCleanup.primary(Async.fail(null))
        nullCleanup.claimedReplacement(Async.fail(null))
        val secondary        = new RuntimeException("secondary")
        val secondaryCleanup = Async.cancellation()
        secondaryCleanup.primary(Async.succeed(()))
        secondaryCleanup.claimedReplacement(Async.fail(secondary))

        assertTrue(
          driveWithFold(sameCleanup.effect) == FoldedFailure(same),
          same.getSuppressed.isEmpty,
          driveWithFold(nullCleanup.effect) == FoldedFailure(null),
          driveWithFold(secondaryCleanup.effect) == FoldedFailure(secondary)
        )
      },
      test("shared cancellation covers terminal, throwing, replacement, and empty-child paths") {
        val terminal = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(1)
        }
        val terminalShared = Async.shareCancellation(terminal)
        val terminalResult = foldStep(terminalShared.poll(AsyncTestSupport.noopRunnable))
        val exhausted      = foldStep(terminalShared.poll(AsyncTestSupport.noopRunnable))
        val emptyCleanup   = terminalShared.cancelWithCleanup()
        val nullShared     = Async.shareCancellation(new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = Async.succeed(null)
        })
        val nullResult = foldStep(nullShared.poll(AsyncTestSupport.noopRunnable))

        val pollFailure = new RuntimeException("shared-poll")
        val throwing    = Async.shareCancellation(new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = throw pollFailure
        })
        val thrown =
          try { throwing.poll(AsyncTestSupport.noopRunnable); None }
          catch { case cause: Throwable => Some(cause) }

        val replacementPolls = new AtomicInteger(0)
        val replacement      = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            replacementPolls.incrementAndGet()
            Async.succeed(2)
          }
        }
        val replacing = Async.shareCancellation(new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = replacement
        })
        val replacementResult = foldStep(replacing.poll(new Runnable {
          def run(): Unit = throw new RuntimeException("wake")
        }))

        val cancelFailure  = new RuntimeException("shared-cancel")
        val cancelThrowing = Async.shareCancellation(new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = throw cancelFailure
        })
        for {
          _            <- AsyncTestSupport.runAsync(emptyCleanup)
          cancelResult <- AsyncTestSupport.runAsync(cancelThrowing.cancelWithCleanup().either)
        } yield {
          assertTrue(
            terminalResult == FoldedSuccess(1),
            nullResult == FoldedSuccess(null),
            exhausted.isInstanceOf[FoldedPending[?]],
            thrown.contains(pollFailure),
            replacementResult == FoldedSuccess(2),
            replacementPolls.get() == 1,
            cancelResult == Left(cancelFailure)
          )
        }
      }
    ),
    suite("bracketAsync")(
      test("reentrant acquisition cancellation handles same, distinct, and failed poll results") {
        def run(mode: Int, cancelThrows: Boolean): (Folded[Unit], Int, Int) = {
          val currentCancels          = new AtomicInteger(0)
          val replacementCancels      = new AtomicInteger(0)
          val cancellationFailure     = new RuntimeException("acquisition cancel")
          val acquisitionFailure      = new RuntimeException("acquisition poll")
          var bracket: Pollable[Unit] = null
          var cleanup: Async[Unit]    = null
          val replacement             = new Pollable[String] {
            private var cancelled                         = false
            def poll(onComplete: Runnable): Async[String] =
              if (cancelled) Async.succeed("resource") else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              replacementCancels.incrementAndGet()
              cancelled = true
              Async.succeed(())
            }
          }
          val current = new Pollable[String] {
            def poll(onComplete: Runnable): Async[String] =
              mode match {
                case 0 => this
                case 1 => replacement
                case _ => Async.fail(acquisitionFailure)
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              currentCancels.incrementAndGet()
              if (cancelThrows) throw cancellationFailure
              Async.succeed(())
            }
          }
          bracket = Async
            .bracketAsync[String, Unit](
              () => {
                cleanup = bracket.cancelWithCleanup()
                current
              },
              _ => Async.succeed(()),
              _ => Async.succeed(())
            )
            .asInstanceOf[Pollable[Unit]]
          bracket.poll(new Runnable { def run(): Unit = throw new RuntimeException("ignored acquisition wake") })
          (driveWithFold(cleanup), currentCancels.get(), replacementCancels.get())
        }

        val same                 = run(0, cancelThrows = false)
        val distinct             = run(1, cancelThrows = false)
        val failed               = run(2, cancelThrows = false)
        val throwingCancellation = run(0, cancelThrows = true)
        assertTrue(
          same._1.isInstanceOf[FoldedPending[?]],
          same._2 == 1,
          same._3 == 0,
          distinct == ((FoldedSuccess(()), 1, 1)),
          failed == ((FoldedSuccess(()), 1, 0)),
          throwingCancellation._1.isInstanceOf[FoldedPending[?]],
          throwingCancellation._2 == 1,
          throwingCancellation._3 == 0
        )
      },
      test("reentrant use cancellation drains cleanup and pending release with failure suppression") {
        val useCleanupFailure      = new RuntimeException("use cleanup")
        val releaseFailure         = new RuntimeException("release")
        val releasePolls           = new AtomicInteger(0)
        var bracket: Pollable[Int] = null
        var cleanup: Async[Unit]   = null
        val use                    = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            cleanup = bracket.cancelWithCleanup()
            this
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(useCleanupFailure)
        }
        val release = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] =
            if (releasePolls.incrementAndGet() == 1) this else Async.fail(releaseFailure)
        }
        bracket = Async
          .bracketAsync[String, Int](() => Async.succeed("resource"), _ => use, _ => release)
          .asInstanceOf[Pollable[Int]]
        val pending = bracket.poll(AsyncTestSupport.noopRunnable)
        val result  = driveWithFold(cleanup)
        assertTrue(
          pending.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
          result == FoldedFailure(useCleanupFailure),
          useCleanupFailure.getSuppressed.toList == List(releaseFailure),
          releasePolls.get() == 2
        )
      },
      test("direct cancel starts pending cleanup after a reentrant permit handoff") {
        val cleanupPolls           = new AtomicInteger(0)
        var bracket: Pollable[Int] = null
        val use                    = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            bracket.cancel()
            this
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              cleanupPolls.incrementAndGet()
              Async.succeed(())
            }
          }
        }
        bracket = Async
          .bracketAsync[String, Int](() => Async.succeed("resource"), _ => use, _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        Live.live(ZIO.yieldNow.repeatUntil(_ => cleanupPolls.get() > 0)).as(assertTrue(cleanupPolls.get() > 0))
      },
      test("poll after use cancellation and after terminal cancellation remains pending") {
        val pendingUse = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = Async.succeed(())
        }
        val duringUse = Async
          .bracketAsync[String, Int](() => Async.succeed("resource"), _ => pendingUse, _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
        duringUse.poll(AsyncTestSupport.noopRunnable)
        val useCleanup       = duringUse.cancelWithCleanup()
        val cancelledUsePoll = duringUse.poll(AsyncTestSupport.noopRunnable)
        val completed        = Async
          .bracketAsync[String, Int](() => Async.succeed("resource"), _ => Async.succeed(1), _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
        val completedValue = completed.poll(AsyncTestSupport.noopRunnable)
        val doneCleanup    = completed.cancelWithCleanup()
        val cancelledDone  = completed.poll(AsyncTestSupport.noopRunnable)
        assertTrue(
          cancelledUsePoll.asInstanceOf[AnyRef] eq duringUse.asInstanceOf[AnyRef],
          driveWithFold(useCleanup) == FoldedSuccess(()),
          foldStep(completedValue) == FoldedSuccess(1),
          driveWithFold(doneCleanup) == FoldedSuccess(()),
          cancelledDone.asInstanceOf[AnyRef] eq completed.asInstanceOf[AnyRef]
        )
      },
      test("use replacement invokes and isolates its throwing wake callback") {
        val replacement = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(1)
        }
        val use = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = replacement
        }
        val bracket = Async.bracketAsync[String, Int](
          () => Async.succeed("resource"),
          _ => use,
          _ => Async.succeed(())
        )
        assertTrue(
          driveWithFold(
            bracket
              .asInstanceOf[Pollable[Int]]
              .poll(new Runnable {
                def run(): Unit = throw new RuntimeException("ignored use replacement wake")
              })
          ) == FoldedSuccess(1)
        )
      },
      test("fresh cancellation and throwing acquisition, use, and release polls preserve lifecycle ordering") {
        val freshAcquire = new AtomicInteger(0)
        val freshUse     = new AtomicInteger(0)
        val freshRelease = new AtomicInteger(0)
        val fresh        = Async
          .bracketAsync[String, Int](
            () => { freshAcquire.incrementAndGet(); Async.succeed("resource") },
            _ => { freshUse.incrementAndGet(); Async.succeed(1) },
            _ => { freshRelease.incrementAndGet(); Async.succeed(()) }
          )
          .asInstanceOf[Pollable[Int]]
        val freshCleanup = driveWithFold(fresh.cancelWithCleanup())
        val freshPoll    = foldStep(fresh.poll(AsyncTestSupport.noopRunnable))

        val acquireFailure = new RuntimeException("acquisition-poll")
        val acquireThrow   = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = throw acquireFailure
        }
        val failedAcquire = Async.bracketAsync[String, Int](
          () => acquireThrow,
          _ => Async.succeed(1),
          _ => Async.succeed(())
        )

        val useFailure = new RuntimeException("use-poll")
        val useThrow   = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = throw useFailure
        }
        val useReleases = new AtomicInteger(0)
        val failedUse   = Async.bracketAsync[String, Int](
          () => Async.succeed("resource"),
          _ => useThrow,
          _ => { useReleases.incrementAndGet(); Async.succeed(()) }
        )

        val releaseFailure = new RuntimeException("release-poll")
        val releaseThrow   = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = throw releaseFailure
        }
        val failedRelease = Async.bracketAsync[String, Int](
          () => Async.succeed("resource"),
          _ => Async.succeed(1),
          _ => releaseThrow
        )

        assertTrue(
          freshCleanup == FoldedSuccess(()),
          freshPoll.isInstanceOf[FoldedPending[?]],
          freshAcquire.get() == 0,
          freshUse.get() == 0,
          freshRelease.get() == 0,
          driveWithFold(failedAcquire) == FoldedFailure(acquireFailure),
          driveWithFold(failedUse) == FoldedFailure(useFailure),
          useReleases.get() == 1,
          driveWithFold(failedRelease) == FoldedFailure(releaseFailure)
        )
      },
      test("is lazy and handles ready acquisition, use, and release") {
        val events  = new scala.collection.mutable.ListBuffer[String]
        val bracket = Async.bracketAsync[String, Int](
          () => { events += "acquire"; Async.succeed("r") },
          r => { events += s"use:$r"; Async.succeed(42) },
          r => { events += s"release:$r"; Async.succeed(()) }
        )
        val before = events.toList
        val result = driveWithFold(bracket)
        assertTrue(
          before.isEmpty,
          result == FoldedSuccess(42),
          events.toList == List("acquire", "use:r", "release:r")
        )
      },
      test("reifies a synchronous acquire throw and does not use or release") {
        val failure  = new RuntimeException("acquire")
        val used     = new AtomicInteger(0)
        val released = new AtomicInteger(0)
        val result   = driveWithFold(
          Async.bracketAsync[String, Int](
            () => throw failure,
            _ => { used.incrementAndGet(); Async.succeed(1) },
            _ => { released.incrementAndGet(); Async.succeed(()) }
          )
        )
        assertTrue(result == FoldedFailure(failure), used.get() == 0, released.get() == 0)
      },
      test("reifies synchronous use and release throws with the use failure primary") {
        val useFailure     = new RuntimeException("use construction")
        val releaseFailure = new RuntimeException("release construction")
        val result         = driveWithFold(
          Async.bracketAsync[String, Int](
            () => Async.succeed("resource"),
            _ => throw useFailure,
            _ => throw releaseFailure
          )
        )

        assertTrue(result == FoldedFailure(useFailure), useFailure.getSuppressed.toList == List(releaseFailure))
      },
      test("cancellation during pending acquisition cancels it and releases a late resource exactly once") {
        final class LateAcquire extends Pollable[String] {
          val cancels                                   = new AtomicInteger(0)
          var value: Async[String]                      = this
          def poll(onComplete: Runnable): Async[String] = value
          override def cancel(): Unit                   = cancels.incrementAndGet()
        }
        val acquire  = new LateAcquire
        val used     = new AtomicInteger(0)
        val released = new AtomicInteger(0)
        val bracket  = Async.bracketAsync[String, Int](
          () => acquire,
          _ => { used.incrementAndGet(); Async.succeed(1) },
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()
        acquire.value = Async.succeed("late")
        val result = driveWithFold(cleanup)
        pending.cancel()
        assertTrue(
          result == FoldedSuccess(()),
          acquire.cancels.get() == 1,
          used.get() == 0,
          released.get() == 1
        )
      },
      test("catchAll passes an in-flight successful acquisition to bracket cancellation for release") {
        val used                     = new AtomicInteger(0)
        val released                 = new AtomicInteger(0)
        var bracket: Pollable[Int]   = null
        var cleanup: Async[Unit]     = null
        val source: Pollable[String] = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = {
            cleanup = bracket.cancelWithCleanup()
            Async.succeed("late")
          }
        }
        val acquisition: Async[String] = source.catchAll(cause => Async.fail(cause))
        bracket = Async
          .bracketAsync[String, Int](
            () => acquisition,
            _ => { used.incrementAndGet(); Async.succeed(1) },
            _ => { released.incrementAndGet(); Async.succeed(()) }
          )
          .asInstanceOf[Pollable[Int]]
        val pending = bracket.poll(AsyncTestSupport.noopRunnable)
        val result  = driveWithFold(cleanup)
        assertTrue(
          pending.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
          result == FoldedSuccess(()),
          used.get() == 0,
          released.get() == 1
        )
      },
      test("cancelling bracket cleanup rejoins it and releases a late resource exactly once") {
        final class LateAcquire extends Pollable[String] {
          val cancels                                   = new AtomicInteger(0)
          var value: Async[String]                      = this
          def poll(onComplete: Runnable): Async[String] = value
          override def cancel(): Unit                   = cancels.incrementAndGet()
        }
        val acquire  = new LateAcquire
        val used     = new AtomicInteger(0)
        val released = new AtomicInteger(0)
        val bracket  = Async
          .bracketAsync[String, Int](
            () => acquire,
            _ => { used.incrementAndGet(); Async.succeed(1) },
            _ => { released.incrementAndGet(); Async.succeed(()) }
          )
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        val cleanup  = bracket.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
        val rejoined = cleanup.cancelWithCleanup()
        acquire.value = Async.succeed("late")
        val result = driveWithFold(rejoined)
        assertTrue(
          rejoined.asInstanceOf[AnyRef] eq cleanup.asInstanceOf[AnyRef],
          result == FoldedSuccess(()),
          acquire.cancels.get() == 1,
          used.get() == 0,
          released.get() == 1
        )
      },
      test("cancellation ignores a late acquisition operation failure") {
        val lateFailure                     = new RuntimeException("late acquisition")
        val cancels                         = new AtomicInteger(0)
        val uses                            = new AtomicInteger(0)
        val releases                        = new AtomicInteger(0)
        var acquisitionValue: Async[String] = null
        val acquisition                     = new Pollable[String] {
          acquisitionValue = this
          def poll(onComplete: Runnable): Async[String]                = acquisitionValue
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            cancels.incrementAndGet()
            Async.succeed(())
          }
        }
        val bracket = Async
          .bracketAsync[String, Int](
            () => acquisition,
            _ => { uses.incrementAndGet(); Async.succeed(1) },
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        val cleanup = bracket.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
        val pending = cleanup.poll(AsyncTestSupport.noopRunnable)
        acquisitionValue = Async.fail(lateFailure)

        assertTrue(
          pending.isInstanceOf[Pollable[?]],
          driveWithFold(cleanup) == FoldedSuccess(()),
          cancels.get() == 1,
          uses.get() == 0,
          releases.get() == 0
        )
      },
      test("acquisition cancellation failure remains primary over a late operation failure") {
        val cancelFailure                   = new RuntimeException("acquisition cancellation")
        val lateFailure                     = new RuntimeException("late acquisition")
        var acquisitionValue: Async[String] = null
        val acquisition                     = new Pollable[String] {
          acquisitionValue = this
          def poll(onComplete: Runnable): Async[String]                = acquisitionValue
          override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(cancelFailure)
        }
        val bracket = Async
          .bracketAsync[String, Int](
            () => acquisition,
            _ => Async.succeed(1),
            _ => Async.succeed(())
          )
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        val cleanup = bracket.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
        val pending = cleanup.poll(AsyncTestSupport.noopRunnable)
        acquisitionValue = Async.fail(lateFailure)

        assertTrue(
          pending.isInstanceOf[Pollable[?]],
          driveWithFold(cleanup) == FoldedFailure(cancelFailure),
          cancelFailure.getSuppressed.isEmpty
        )
      },
      test("cancellation still reports release failure for a late successful acquisition") {
        val releaseFailure                  = new RuntimeException("late release")
        val releases                        = new AtomicInteger(0)
        var acquisitionValue: Async[String] = null
        val acquisition                     = new Pollable[String] {
          acquisitionValue = this
          def poll(onComplete: Runnable): Async[String] = acquisitionValue
        }
        val bracket = Async
          .bracketAsync[String, Int](
            () => acquisition,
            _ => Async.succeed(1),
            _ => { releases.incrementAndGet(); Async.fail(releaseFailure) }
          )
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        val cleanup = bracket.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
        val pending = cleanup.poll(AsyncTestSupport.noopRunnable)
        acquisitionValue = Async.succeed("late")

        assertTrue(
          pending.isInstanceOf[Pollable[?]],
          driveWithFold(cleanup) == FoldedFailure(releaseFailure),
          releases.get() == 1
        )
      },
      test("cancellation through ensuring still releases a late acquisition exactly once") {
        final class LateAcquire extends Pollable[String] {
          val cancels                                   = new AtomicInteger(0)
          var value: Async[String]                      = this
          def poll(onComplete: Runnable): Async[String] = value
          override def cancel(): Unit                   = cancels.incrementAndGet()
        }
        val acquire  = new LateAcquire
        val released = new AtomicInteger(0)
        val bracket  = Async.bracketAsync[String, Int](
          () => acquire.ensuring(Async.succeed(())),
          _ => Async.succeed(1),
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()
        acquire.value = Async.succeed("late")

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          acquire.cancels.get() == 1,
          released.get() == 1
        )
      },
      test("cancellation through catchAll still releases a late successful acquisition") {
        final class LateAcquire extends Pollable[String] {
          val cancels                                   = new AtomicInteger(0)
          var value: Async[String]                      = this
          def poll(onComplete: Runnable): Async[String] = value
          override def cancel(): Unit                   = cancels.incrementAndGet()
        }
        val acquire  = new LateAcquire
        val released = new AtomicInteger(0)
        val bracket  = Async.bracketAsync[String, Int](
          () => acquire.catchAll(Async.fail),
          _ => Async.succeed(1),
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()
        acquire.value = Async.succeed("late")

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          acquire.cancels.get() == 1,
          released.get() == 1
        )
      },
      test("cancellation through ensuring hands a later replacement to the enclosing bracket") {
        val predecessorCancels        = new AtomicInteger(0)
        val replacementCancels        = new AtomicInteger(0)
        val released                  = new AtomicInteger(0)
        var cancellation: Async[Unit] = null
        var bracket: Async[Int]       = null
        val replacement               = new Pollable[String] {
          private var value: Async[String]                             = this
          def poll(onComplete: Runnable): Async[String]                = value
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            replacementCancels.incrementAndGet()
            value = Async.succeed("late")
            Async.succeed(())
          }
        }
        val predecessor = new Pollable[String] {
          private var polls                             = 0
          def poll(onComplete: Runnable): Async[String] = {
            polls += 1
            if (polls == 1) {
              cancellation = bracket.asInstanceOf[Pollable[Int]].cancelWithCleanup()
              this
            } else replacement
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            predecessorCancels.incrementAndGet()
            Async.succeed(())
          }
        }
        bracket = Async.bracketAsync[String, Int](
          () => predecessor.ensuring(Async.succeed(())),
          _ => Async.succeed(1),
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)

        assertTrue(
          driveWithFold(cancellation) == FoldedSuccess(()),
          predecessorCancels.get() == 1,
          replacementCancels.get() == 1,
          released.get() == 1
        )
      },
      test("cancellation cleanup drives a retained distinct acquisition replacement without a synthetic wake") {
        val wakes                     = new AtomicInteger(0)
        val released                  = new AtomicInteger(0)
        var cancellation: Async[Unit] = null
        var bracket: Async[Int]       = null
        val replacement               = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = Async.succeed("resource")
        }
        val acquisition = new Pollable[String] {
          private var polls                             = 0
          def poll(onComplete: Runnable): Async[String] = {
            polls += 1
            if (polls == 1) {
              cancellation = bracket.asInstanceOf[Pollable[Int]].cancelWithCleanup()
              this
            } else replacement
          }
        }
        bracket = Async.bracketAsync[String, Int](
          () => acquisition,
          _ => Async.succeed(1),
          _ => { released.incrementAndGet(); Async.succeed(()) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = cancellation.asInstanceOf[Pollable[Unit]]
        val first   = cleanup.poll(new Runnable { def run(): Unit = { wakes.incrementAndGet(); () } })

        assertTrue(
          foldStep(first) == FoldedSuccess(()),
          wakes.get() == 0,
          driveWithFold(cleanup) == FoldedSuccess(()),
          released.get() == 1
        )
      },
      test("cancellation racing a terminal null map acquisition still releases null exactly once") {
        val released                  = new AtomicInteger(0)
        var releasedNull              = false
        var cancellation: Async[Unit] = null
        var bracket: Async[Int]       = null
        val source                    = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = Async.succeed(())
        }
        bracket = Async.bracketAsync[String, Int](
          () =>
            source.map { _ =>
              cancellation = bracket.asInstanceOf[Pollable[Int]].cancelWithCleanup()
              null
            },
          _ => Async.succeed(1),
          resource => {
            released.incrementAndGet()
            releasedNull = resource == null
            Async.succeed(())
          }
        )

        val pending = bracket.asInstanceOf[Pollable[Int]].poll(AsyncTestSupport.noopRunnable)

        assertTrue(
          pending.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
          driveWithFold(cancellation) == FoldedSuccess(()),
          released.get() == 1,
          releasedNull
        )
      },
      test("cancellation of an ensuring acquisition preserves and releases its terminal resource") {
        val gate          = new Completer[Unit]
        val outerUses     = new AtomicInteger(0)
        val outerReleases = new AtomicInteger(0)
        val outer         = Async.bracketAsync[String, Int](
          () => Async.succeed("outer-resource").ensuring(gate),
          _ => { outerUses.incrementAndGet(); Async.succeed(1) },
          _ => { outerReleases.incrementAndGet(); Async.succeed(()) }
        )
        val pending = outer.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()
        gate.succeed(())

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          outerUses.get() == 0,
          outerReleases.get() == 1
        )
      },
      test("a cancelled nested bracket owns its output after releasing a late acquisition") {
        val acquisition = new Completer[String]
        val uses        = new AtomicInteger(0)
        val releases    = new AtomicInteger(0)
        val installed   = Async.acquireInstallCancelable[String, Int](
          () => acquisition.peek,
          _ => 1,
          _ => { releases.incrementAndGet(); Async.succeed(()) }
        )
        val outer = Async.bracketAsync[Int, Unit](
          () => installed,
          _ => { uses.incrementAndGet(); Async.succeed(()) },
          _ => Async.succeed(())
        )
        val pending = outer.asInstanceOf[Pollable[Unit]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()
        acquisition.succeed("resource")

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          uses.get() == 0,
          releases.get() == 1
        )
      },
      test("a nested install releases an acquisition that wins reentrant outer cancellation") {
        val releases             = new AtomicInteger(0)
        var outer: Async[Unit]   = null
        var cleanup: Async[Unit] = null
        val acquisition          = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = {
            val _ = onComplete
            cleanup = outer.asInstanceOf[Pollable[Unit]].cancelWithCleanup()
            Async.succeed("resource")
          }
        }
        val installed = Async.acquireInstallCancelable[String, Int](
          () => acquisition,
          _ => 1,
          _ => { releases.incrementAndGet(); Async.succeed(()) }
        )
        outer = Async.bracketAsync[Int, Unit](
          () => installed,
          _ => Async.succeed(()),
          _ => Async.succeed(())
        )
        outer.asInstanceOf[Pollable[Unit]].poll(AsyncTestSupport.noopRunnable)

        assertTrue(
          driveWithFold(cleanup) == FoldedSuccess(()),
          releases.get() == 1
        )
      },
      test("cancellation after publication does not claim ownership of the transferred output") {
        val releases  = new AtomicInteger(0)
        val installed = Async.acquireInstallCancelable[String, Int](
          () => Async.succeed("resource"),
          _ => 1,
          _ => { releases.incrementAndGet(); Async.succeed(()) }
        )
        val pending = installed.asInstanceOf[Pollable[Int]]
        val output  = pending.poll(AsyncTestSupport.noopRunnable)
        val cleanup = pending.cancelWithCleanup()

        assertTrue(
          foldStep(output) == FoldedSuccess(1),
          driveWithFold(cleanup) == FoldedSuccess(()),
          !pending.asInstanceOf[Async.CancelledOutputOwnership].cancellationOwnsOutput,
          releases.get() == 0
        )
      },
      test("reentrant acquisition cancellation drains its failing cleanup before releasing a terminal resource") {
        val cleanupFailure       = new RuntimeException("acquisition cleanup")
        val releaseFailure       = new RuntimeException("release")
        val events               = new scala.collection.mutable.ListBuffer[String]
        var cleanup: Async[Unit] = null

        val acquisitionCleanup = new Pollable[Unit] {
          private var first                           = true
          def poll(onComplete: Runnable): Async[Unit] = {
            events += "cleanup"
            if (first) { first = false; onComplete.run(); this }
            else Async.fail(cleanupFailure)
          }
        }
        var bracket: Async[String] = null
        val acquire                = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] = {
            cleanup = bracket.asInstanceOf[Pollable[String]].cancelWithCleanup()
            Async.succeed("resource")
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = acquisitionCleanup
        }
        bracket = Async.bracketAsync[String, String](
          () => acquire,
          _ => { events += "use"; Async.succeed("used") },
          _ => { events += "release"; Async.fail(releaseFailure) }
        )

        val pending = bracket.asInstanceOf[Pollable[String]].poll(AsyncTestSupport.noopRunnable)
        val result  = driveWithFold(cleanup)

        assertTrue(
          pending.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
          result == FoldedFailure(cleanupFailure),
          cleanupFailure.getSuppressed.toList == List(releaseFailure),
          events.toList == List("cleanup", "cleanup", "release")
        )
      },
      test("cancellation hands off a distinct acquisition replacement and preserves cleanup failure order") {
        val predecessorFailure = new RuntimeException("predecessor cleanup")
        val replacementFailure = new RuntimeException("replacement cleanup")
        val releaseFailure     = new RuntimeException("release")

        final class CleanupFailure(cause: Throwable) extends Pollable[Unit] {
          val polls                                   = new AtomicInteger(0)
          def poll(onComplete: Runnable): Async[Unit] = {
            polls.incrementAndGet()
            Async.fail(cause)
          }
        }

        val predecessorCleanup = new CleanupFailure(predecessorFailure)
        val replacementCleanup = new CleanupFailure(replacementFailure)
        val replacementCancels = new AtomicInteger(0)
        val replacementPolls   = new AtomicInteger(0)
        val predecessorCancels = new AtomicInteger(0)
        val predecessorPolls   = new AtomicInteger(0)
        val used               = new AtomicInteger(0)
        val released           = new AtomicInteger(0)

        val replacement = new Pollable[String] {
          private var cancelled                         = false
          def poll(onComplete: Runnable): Async[String] = {
            replacementPolls.incrementAndGet()
            if (cancelled) Async.succeed("late-resource") else this
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            replacementCancels.incrementAndGet()
            cancelled = true
            replacementCleanup
          }
        }
        val predecessor = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] =
            if (predecessorPolls.incrementAndGet() == 1) this else replacement
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            predecessorCancels.incrementAndGet()
            predecessorCleanup
          }
        }
        val bracket = Async.bracketAsync[String, Int](
          () => predecessor,
          _ => { used.incrementAndGet(); Async.succeed(1) },
          _ => { released.incrementAndGet(); Async.fail(releaseFailure) }
        )
        val pending = bracket.asInstanceOf[Pollable[Int]]
        pending.poll(AsyncTestSupport.noopRunnable)
        val result = driveWithFold(pending.cancelWithCleanup())

        assertTrue(
          result == FoldedFailure(predecessorFailure),
          predecessorFailure.getSuppressed.toList == List(replacementFailure, releaseFailure),
          predecessorCancels.get() == 1,
          replacementCancels.get() == 1,
          predecessorCleanup.polls.get() == 1,
          replacementCleanup.polls.get() == 1,
          predecessorPolls.get() == 2,
          replacementPolls.get() == 1,
          used.get() == 0,
          released.get() == 1
        )
      },
      test("pending acquisition and release failures preserve acquisition as primary") {
        val acquireFailure = new RuntimeException("acquire")
        val polls          = new AtomicInteger(0)
        val acquire        = new Pollable[String] {
          def poll(onComplete: Runnable): Async[String] =
            if (polls.incrementAndGet() == 1) this else Async.fail(acquireFailure)
        }
        val bracket = Async.bracketAsync[String, Int](() => acquire, _ => Async.succeed(1), _ => Async.succeed(()))
        val result  = driveWithFold(bracket)
        assertTrue(result == FoldedFailure(acquireFailure), polls.get() == 2)
      }
    ),
    suite("slow-path finalization")(
      test("runThenValue covers replacement, failure policy, and replay") {
        val replacement = new Pollable[Any] {
          def poll(onComplete: Runnable): Async[Any] = Async.succeed(())
        }
        val predecessor = new Pollable[Any] {
          def poll(onComplete: Runnable): Async[Any] = {
            onComplete.run()
            replacement
          }
        }
        val replacing = Async.slowPath.runThenValue(predecessor, 42, suppressFailure = false)
        val first     = replacing.asInstanceOf[Pollable[Int]].poll(AsyncTestSupport.noopRunnable)
        val replaced  = foldStep(replacing.asInstanceOf[Pollable[Int]].poll(AsyncTestSupport.noopRunnable))
        val replay    = foldStep(replacing.asInstanceOf[Pollable[Int]].poll(AsyncTestSupport.noopRunnable))

        val finalizerFailure = new RuntimeException("finalizer")
        val failedFinalizer  = new Pollable[Any] {
          def poll(onComplete: Runnable): Async[Any] = Async.fail(finalizerFailure)
        }
        val suppressed = Async.slowPath.runThenValue(failedFinalizer, 1, suppressFailure = true)
        val propagated = Async.slowPath.runThenValue(failedFinalizer, 1, suppressFailure = false)

        assertTrue(
          foldStep(first) == FoldedSuccess(42),
          replaced == FoldedSuccess(42),
          replay == FoldedSuccess(42),
          driveWithFold(suppressed) == FoldedSuccess(1),
          driveWithFold(propagated) == FoldedFailure(finalizerFailure)
        )
      },
      test("runThenValue handles cancellation reentrant from pending and throwing finalizer polls") {
        def reentrant(throwAfterCancel: Throwable, suppress: Boolean): (Folded[Int], Folded[Unit]) = {
          var wrapper: Pollable[Int] = null
          var cleanup: Async[Unit]   = null
          val fin                    = new Pollable[Any] {
            def poll(onComplete: Runnable): Async[Any] = {
              cleanup = wrapper.cancelWithCleanup()
              if (throwAfterCancel ne null) throw throwAfterCancel
              this
            }
          }
          wrapper = Async.slowPath.runThenValue(fin, 42, suppress).asInstanceOf[Pollable[Int]]
          val result = foldStep(wrapper.poll(AsyncTestSupport.noopRunnable))
          (result, driveWithFold(cleanup))
        }

        val thrown             = new RuntimeException("throw-after-cancel")
        val (pending, cleaned) = reentrant(null, suppress = false)
        val (caught, cleaned2) = reentrant(thrown, suppress = false)
        assertTrue(
          pending.isInstanceOf[FoldedPending[?]],
          cleaned == FoldedSuccess(()),
          caught.isInstanceOf[FoldedPending[?]],
          cleaned2 == FoldedSuccess(())
        )
      },
      test("ensuring suppresses throwing finalizers for null, same, and distinct primary failures") {
        def result(primary: Throwable, finalizer: Throwable): Folded[Int] = {
          val pa = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.fail(primary)
          }
          val fin = new Pollable[Any] {
            def poll(onComplete: Runnable): Async[Any] = throw finalizer
          }
          driveWithFold(Async.slowPath.ensuringAsync(pa, fin))
        }

        val primary      = new RuntimeException("primary")
        val distinct     = new RuntimeException("distinct-finalizer")
        val nullCase     = result(null, distinct)
        val sameCase     = result(primary, primary)
        val distinctCase = result(primary, distinct)
        assertTrue(
          nullCase == FoldedFailure(null),
          sameCase == FoldedFailure(primary),
          distinctCase == FoldedFailure(primary),
          primary.getSuppressed.toList == List(distinct)
        )
      },
      test("ensuring cancellation joins a distinct replacement and replays cleanup") {
        val replacement             = new CleanupOnCancel(Async.succeed(()))
        var ensuring: Pollable[Int] = null
        var cleanup1: Async[Unit]   = null
        val primary                 = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            cleanup1 = ensuring.cancelWithCleanup()
            replacement
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = Async.succeed(())
        }
        ensuring = Async.slowPath.ensuringAsync(primary, Async.succeed(())).asInstanceOf[Pollable[Int]]
        val polled   = foldStep(ensuring.poll(AsyncTestSupport.noopRunnable))
        val cleanup2 = ensuring.cancelWithCleanup()
        assertTrue(
          polled.isInstanceOf[FoldedPending[?]],
          driveWithFold(cleanup1) == FoldedSuccess(()),
          driveWithFold(cleanup2) == FoldedSuccess(()),
          replacement.cancellations.get() == 1
        )
      },
      test("ensuring cleanup captures throwing primary and finalizer polls and retains primary provenance") {
        val primaryFailure   = new RuntimeException("primary-cleanup-poll")
        val finalizerFailure = new RuntimeException("finalizer-cleanup-poll")
        val primary          = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = throw primaryFailure
          }
        }
        val finalizer = new Pollable[Any] {
          def poll(onComplete: Runnable): Async[Any] = throw finalizerFailure
        }
        val ensuring = Async.slowPath.ensuringAsync(primary, finalizer).asInstanceOf[Pollable[Int]]
        val cleanup  = ensuring.cancelWithCleanup()
        val first    = driveWithFold(cleanup)
        val replay   = driveWithFold(cleanup)
        assertTrue(
          first == FoldedFailure(primaryFailure),
          replay == FoldedFailure(primaryFailure),
          primaryFailure.getSuppressed.toList == List(finalizerFailure)
        )
      },
      test("ensuring cancellation converts a throwing cancelWithCleanup and handles same and null finalizer failures") {
        def cleanup(primaryFailure: Throwable, finalizerFailure: Throwable, throwOnCancel: Boolean): Folded[Unit] = {
          val primary = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] =
              if (throwOnCancel) throw primaryFailure else Async.fail(primaryFailure)
          }
          val finalizer = new Pollable[Any] {
            def poll(onComplete: Runnable): Async[Any] = Async.fail(finalizerFailure)
          }
          val ensuring = Async.slowPath.ensuringAsync(primary, finalizer).asInstanceOf[Pollable[Int]]
          driveWithFold(ensuring.cancelWithCleanup())
        }

        val same         = new RuntimeException("same")
        val thrownCancel = new RuntimeException("throwing-cancel")
        val nullPrimary  = cleanup(null, same, throwOnCancel = false)
        val sameFailure  = cleanup(same, same, throwOnCancel = false)
        val thrown       = cleanup(thrownCancel, null, throwOnCancel = true)
        assertTrue(
          nullPrimary == FoldedFailure(null),
          sameFailure == FoldedFailure(same),
          thrown == FoldedFailure(thrownCancel),
          same.getSuppressed.isEmpty
        )
      }
    ),
    suite("start")(
      suite("synchronous fast path")(
        test("ready value invokes observer with Right on the caller thread") {
          var fired = Option.empty[Either[Throwable, Int]]
          AsyncTestSupport.startEither(Async.succeed(42))(e => fired = Some(e))
          assertTrue(fired == Some(Right(42)))
        },
        test("ready failure invokes observer with Left on the caller thread") {
          var fired = Option.empty[Either[Throwable, Int]]
          AsyncTestSupport.startEither[Int](Async.fail(boom))(e => fired = Some(e))
          assertTrue(fired == Some(Left(boom)))
        },
        test("ready value resolves through the ZIO bridge") {
          AsyncTestSupport.runAsync(Async.succeed(7)).map(v => assertTrue(v == 7))
        },
        test("ready failure resolves through the ZIO bridge") {
          AsyncTestSupport.runAsync[Int](Async.fail(boom)).either.map(e => assertTrue(e == Left(boom)))
        }
      ),
      suite("suspended path")(
        test("completes when a fiber settles the Completer (no lost wakeup)") {
          val c = new Completer[Int]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).fork
            _     <- ZIO.succeed(c.succeed(99))
            v     <- fiber.join
          } yield assertTrue(v == 99)
        },
        test("fails when a fiber fails the Completer") {
          val c = new Completer[Int]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).either.fork
            _     <- ZIO.succeed(c.fail(boom))
            e     <- fiber.join
          } yield assertTrue(e == Left(boom))
        },
        test("completes when a fiber settles the Completer with null (terminal is published)") {
          // A suspended run that resolves to a raw null success must still
          // publish its terminal: the runner cannot reuse null as its own
          // "not yet settled" sentinel (the JS runner keeps an explicit
          // has-terminal flag; the JVM runner must agree — JVM/JS parity).
          val c = new Completer[String]
          for {
            fiber <- AsyncTestSupport.runAsync(c.peek).fork
            _     <- ZIO.succeed(c.succeed(null))
            v     <- Live.live(
                   fiber.join
                     .timeoutFail(new RuntimeException("Running never published the null terminal"))(5.seconds)
                 )
          } yield assertTrue(v == null)
        },
        test("driver advances to the pollable returned by poll (not re-polling the original)") {
          // Would never deliver under a re-poll-the-AsyncTestSupport.original driver; completes
          // only because the runner walks the chain of distinct pollables.
          AsyncTestSupport.runAsync(new StepChain(5, 0)).map(v => assertTrue(v == 5))
        },
        test("runner polls a distinct already-ready replacement without requiring its wake") {
          val replacementPolls = new AtomicInteger(0)
          val replacement      = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = {
              replacementPolls.incrementAndGet()
              Async.succeed(42)
            }
          }
          val predecessor = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = replacement
          }
          AsyncTestSupport.runAsync(AsyncTestSupport.fromPollable(predecessor)).map { value =>
            assertTrue(value == 42, replacementPolls.get() == 1)
          }
        },
        test("block polls a distinct already-ready replacement without requiring its wake") {
          val replacementPolls = new AtomicInteger(0)
          val replacement      = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = {
              replacementPolls.incrementAndGet()
              Async.succeed(42)
            }
          }
          val predecessor = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = replacement
          }
          val value = AsyncTestSupport.fromPollable(predecessor).block

          assertTrue(value == 42, replacementPolls.get() == 1)
        },
        test("map composed over a pollable that advances by returning a new pollable") {
          // Same conforming leaf as above (poll returns a NEW pending pollable, per
          // the Pollable contract: "returns a pending Async (typically itself)").
          // The combinator must keep driving it, exactly as the bare driver does,
          // rather than misreading the fresh pending pollable as a terminal value.
          val direct = AsyncTestSupport.fromPollable(new StepChain(2, 0)).block
          val mapped = scala.util.Try(AsyncTestSupport.fromPollable(new StepChain(2, 0)).map(_ + 1).block)
          assertTrue(direct == 2, mapped == scala.util.Success(3))
        }
      ),
      suite("eval runner (Async.start(body))")(
        test("evaluates the thunk off the caller and publishes its value") {
          AsyncTestSupport.runAsync(Async.start(21 * 2)).map(v => assertTrue(v == 42))
        },
        test("reifies a thrown body as a failure") {
          val running = Async.start[Int] {
            val n = 0
            if (n == 0) throw boom
            n
          }
          AsyncTestSupport.runAsync(running).either.map(e => assertTrue(e == Left(boom)))
        },
        test("reifies a Nothing-typed throwing body as a failure (no eager throw at the call site)") {
          // `Async.start(body)` is documented as "the `Async` analogue of
          // `Future.apply`", which captures a throwing body. When the by-name
          // body is statically `Nothing`-typed (e.g. `{ setup(); throw e }`,
          // `sys.error(...)`, `???`), overload resolution must still pick the
          // by-name `start(body: => A)` entry point — NOT the by-value
          // `start(fa: Async[A])` one (a bare `throw` is `Nothing <: Async[A]`),
          // which would force the body eagerly and throw at the call site.
          val started =
            try Right(Async.start { val _ = 0; throw boom })
            catch { case t: Throwable => Left(t) }
          started match {
            case Right(running) =>
              AsyncTestSupport.runAsync(running).either.map(e => assertTrue(e == Left(boom)))
            case Left(eager) =>
              // The body was forced eagerly at the call site (overload picked the
              // by-value `Async[A]` arm) instead of being captured into a Running.
              ZIO.succeed(assertTrue((eager: Throwable) == null, eager != boom))
          }
        },
        test("publishes a null body result (terminal is published)") {
          // Same publication contract as the Completer-settled null above, via
          // the by-name `Async.start(body)` entry point. Observed by polling
          // the Running directly (the standard Pollable driver protocol) so a
          // defective run fails the assertion after the bounded wait instead of
          // parking a second runner forever.
          val running = Async.start {
            val s: String = null
            s
          }
          def settled = !AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          for {
            published <- Live.live(
                           (ZIO.sleep(10.millis) *> ZIO.succeed(settled))
                             .repeatUntil(identity)
                             .timeoutTo(false)(identity)(5.seconds)
                         )
            out = running.poll(AsyncTestSupport.noopRunnable)
          } yield assertTrue(published, out == null)
        },
        test("Async.start(body) evaluating to a suspended Async wraps it (the body's tap is NOT driven)") {
          // `Async.start(body)` is `Future.apply`-shaped: it evaluates the
          // by-name body and lifts its RESULT with the standard encoding. When
          // the body is itself a suspended `Async` (a `tap` over a pending
          // pollable), the result is carried as a pollable-as-value — never run
          // as a computation. So the `tap` effect must NOT fire: driving an
          // already-built `Async` is the `fa.start` extension's job (see the
          // "drive" suite below), not the companion `Async.start(body)`.
          val fired = new AtomicBoolean(false)
          val c     = new Completer[Int]
          val body  = c.peek.tap { _ => fired.set(true); Async.succeed(()) }
          Async.start[Async[Int]](body)
          c.succeed(1)
          Live.live(ZIO.sleep(200.millis)).as(assertTrue(!fired.get()))
        }
      ),
      suite("drive extension (fa.start)")(
        test("fa.start drives a tap over a pending pollable to completion (the effect fires)") {
          // The differential partner of the eval-runner test above: the `fa.start`
          // extension DRIVES an already-built `Async`, so a `tap` composed before
          // `start` runs its effect once the leaf settles.
          val fired  = new AtomicBoolean(false)
          val c      = new Completer[Int]
          val driven = c.peek.tap { _ => fired.set(true); Async.succeed(()) }
          driven.start
          c.succeed(1)
          for {
            ok <- Live.live(
                    (ZIO.sleep(5.millis) *> ZIO.succeed(fired.get()))
                      .repeatUntil(identity)
                      .timeoutTo(false)(identity)(5.seconds)
                  )
          } yield assertTrue(ok)
        }
      ),
      suite("failure surfacing")(
        test("a Throwable escaping poll becomes Left") {
          val thrower: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = throw boom
          }
          AsyncTestSupport.runAsync(thrower).either.map(e => assertTrue(e == Left(boom)))
        },
        test("a throwing finalizer on a pending failure still surfaces the primary") {
          val c = new Completer[Int]
          val a = c.peek.ensuring(AsyncTestSupport.throwingFinalizer)
          for {
            out <- ZIO.async[Any, Nothing, Either[Throwable, Int]] { k =>
                     AsyncTestSupport.startEither(a)(res => k(ZIO.succeed(res)))
                     c.fail(AsyncTestSupport.primary)
                   }
          } yield assertTrue(out == Left(AsyncTestSupport.primary))
        }
      ),
      suite("cancellation")(
        test("cancel is idempotent and a no-op after synchronous completion") {
          var count   = 0
          val running = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
          running.cancel()
          running.cancel()
          assertTrue(count == 1)
        },
        test("cancel before completion suppresses observer") {
          val c       = new Completer[Int]
          val fired   = new AtomicBoolean(false)
          val running = AsyncTestSupport.startTap(c.peek)(_ => fired.set(true))
          running.cancel()
          c.succeed(1)
          Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
        },
        test("close (AutoCloseable) aliases cancel and is idempotent") {
          // Deterministic: a synchronously-completed run leaves no driver loop,
          // so close() (= cancel()) is a pure no-op. Driven through the
          // AutoCloseable supertype to prove the alias dispatches to cancel.
          var count                    = 0
          val running                  = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
          val closeable: AutoCloseable = running
          closeable.close()
          closeable.close()
          assertTrue(count == 1)
        },
        test("cancel is idempotent on a genuinely suspended run") {
          // `Async.never` always takes the suspended path on both platforms,
          // so the second `cancel()` exercises the already-settled no-op branch
          // (not the synchronous `CompletedRunning` path).
          val fired   = new AtomicBoolean(false)
          val running = AsyncTestSupport.startTap[Nothing](Async.never)(_ => fired.set(true))
          running.cancel()
          running.cancel()
          Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
        },
        test("cancel signals the active pending leaf exactly once") {
          val leaf    = new CancelAwareNever
          val running = AsyncTestSupport.fromPollable(leaf).start
          for {
            polled <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.polls.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
            _ = running.cancel()
            _ = running.cancel()
          } yield assertTrue(polled, leaf.cancels.get() == 1)
        },
        test("cancel follows a pending replacement instead of signalling the completed predecessor") {
          val replacement = new CancelAwareNever
          val predecessor = new CancelAwareReplacement(replacement)
          val running     = AsyncTestSupport.fromPollable(predecessor).start
          for {
            replacementPolled <- Live.live(
                                   (ZIO.sleep(1.millis) *> ZIO.succeed(replacement.polls.get() > 0))
                                     .repeatUntil(identity)
                                     .timeoutTo(false)(identity)(5.seconds)
                                 )
            _ = running.cancel()
          } yield assertTrue(
            replacementPolled,
            predecessor.polls.get() == 1,
            predecessor.cancels.get() == 0,
            replacement.cancels.get() == 1
          )
        },
        test("cancel after the pending leaf completes is a no-op") {
          val cancels = new AtomicInteger(0)
          val ready   = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(42)
            override def cancel(): Unit                = cancels.incrementAndGet()
          }
          val running = AsyncTestSupport.fromPollable(ready).start
          for {
            completed <- Live.live(
                           (ZIO.sleep(1.millis) *>
                             ZIO.succeed(!AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))))
                             .repeatUntil(identity)
                             .timeoutTo(false)(identity)(5.seconds)
                         )
            _ = running.cancel()
          } yield assertTrue(completed, cancels.get() == 0)
        },
        test("cancel propagates through every built-in suspended combinator wrapper") {
          val cases: List[(String, Async[Int] => Async[Any])] = List(
            "map"        -> ((fa: Async[Int]) => fa.map(_ + 1)),
            "flatMap"    -> ((fa: Async[Int]) => fa.flatMap(a => Async.succeed(a + 1))),
            "catchAll"   -> ((fa: Async[Int]) => fa.catchAll(_ => Async.succeed(0))),
            "zipWith"    -> ((fa: Async[Int]) => fa.zipWith(Async.succeed(1))(_ + _)),
            "tap"        -> ((fa: Async[Int]) => fa.tap(_ => Async.succeed(()))),
            "ensuring"   -> ((fa: Async[Int]) => fa.ensuring(Async.succeed(()))),
            "collectAll" -> ((fa: Async[Int]) => Async.collectAll(List(fa))),
            "observed"   -> ((fa: Async[Int]) => Async.succeed(fa))
          )
          ZIO
            .foreach(cases) { case (name, wrap) =>
              val leaf    = new CancelAwareNever
              val running = wrap(AsyncTestSupport.fromPollable(leaf)).start
              for {
                polled <- Live.live(
                            (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.polls.get() > 0))
                              .repeatUntil(identity)
                              .timeoutTo(false)(identity)(5.seconds)
                          )
                _          = running.cancel()
                cancelled <- Live.live(
                               (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.cancels.get() > 0))
                                 .repeatUntil(identity)
                                 .timeoutTo(false)(identity)(5.seconds)
                             )
              } yield (name, polled, cancelled, leaf.cancels.get())
            }
            .map(results =>
              assertTrue(results.forall { case (_, polled, cancelled, cancels) => polled && cancelled && cancels == 1 })
            )
        },
        test("wrapper cancellation joins a late replacement cleanup and aggregates failures once") {
          val cases: List[(String, Async[Int] => Async[Any])] = List(
            "map"        -> ((fa: Async[Int]) => fa.map(_ + 1)),
            "flatMap"    -> ((fa: Async[Int]) => fa.flatMap(a => Async.succeed(a + 1))),
            "catchAll"   -> ((fa: Async[Int]) => fa.catchAll(_ => Async.succeed(0))),
            "zipWith"    -> ((fa: Async[Int]) => fa.zipWith(Async.succeed(1))(_ + _)),
            "collectAll" -> ((fa: Async[Int]) => Async.collectAll(List(fa)))
          )
          ZIO
            .foreach(cases) { case (name, wrap) =>
              val primaryFailure     = new RuntimeException(s"$name-primary")
              val replacementFailure = new RuntimeException(s"$name-replacement")
              val replacementCleanup = new TwoPollCleanup(replacementFailure)
              val replacement        = new CleanupOnCancel(replacementCleanup)
              val handle             = new AtomicReference[Async.Running[Any]](null)
              val wake               = new AtomicReference[Runnable](null)
              val reports            = new AtomicInteger(0)
              val reported           = new AtomicReference[Throwable](null)
              val polls              = new AtomicInteger(0)
              val predecessor        = new Pollable[Int] {
                def poll(onComplete: Runnable): Async[Int] =
                  if (polls.incrementAndGet() == 1) { wake.set(onComplete); this }
                  else {
                    handle.get().cancel { t => reports.incrementAndGet(); reported.set(t) }
                    replacement
                  }
                override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(primaryFailure)
              }
              val running = wrap(AsyncTestSupport.fromPollable(predecessor)).start
              handle.set(running)
              for {
                armed <- Live.live(
                           (ZIO.sleep(1.millis) *> ZIO.succeed(wake.get() ne null))
                             .repeatUntil(identity)
                             .timeoutTo(false)(identity)(5.seconds)
                         )
                _       = wake.get().run()
                failed <- Live.live(
                            (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                              .repeatUntil(identity)
                              .timeoutTo(false)(identity)(5.seconds)
                          )
              } yield (
                name,
                armed,
                failed,
                replacement.cancellations.get(),
                replacementCleanup.polls.get(),
                reports.get(),
                reported.get() eq primaryFailure,
                primaryFailure.getSuppressed().toList == List(replacementFailure)
              )
            }
            .map(results =>
              assertTrue(results.forall { case (_, armed, failed, cancellations, polls, reports, primary, suppressed) =>
                armed && failed && cancellations == 1 && polls == 2 && reports == 1 && primary && suppressed
              })
            )
        },
        test("cancel drives asynchronous leaf cleanup exactly once while publication stays suppressed") {
          val cleanup  = new TwoPollCleanup(null)
          val leaf     = new CleanupOnCancel(cleanup)
          val running  = AsyncTestSupport.fromPollable(leaf).start
          val reported = new AtomicReference[Throwable](null)
          for {
            polled <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.polls.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
            _        = running.cancel(t => reported.set(t))
            _        = running.cancel(t => reported.set(t))
            cleaned <- Live.live(
                         (ZIO.sleep(1.millis) *> ZIO.succeed(cleanup.polls.get() >= 2))
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          } yield assertTrue(
            polled,
            cleaned,
            leaf.cancellations.get() == 1,
            reported.get() == null,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("a concurrent continuation cancellation joins the complete leaf-inclusive cleanup") {
          val failure      = new RuntimeException("leaf-cleanup")
          val gate         = new Completer[Unit]
          val leaf         = new CleanupOnCancel(gate.flatMap(_ => Async.fail(failure)))
          val continuation = AsyncTestSupport.fromPollable(leaf).map(_ + 1).map(_ + 1)
          val first        = continuation.asInstanceOf[Pollable[Int]].cancelWithCleanup().start
          val second       = continuation.asInstanceOf[Pollable[Int]].cancelWithCleanup().start
          val before       = second.poll(AsyncTestSupport.noopRunnable)
          for {
            _            <- ZIO.succeed(gate.succeed(()))
            firstResult  <- AsyncTestSupport.runAsync(first.either)
            secondResult <- AsyncTestSupport.runAsync(second.either)
          } yield assertTrue(
            before.isInstanceOf[Pollable[?]],
            firstResult == Left(failure),
            secondResult == Left(failure),
            leaf.cancellations.get() == 1
          )
        },
        test("reentrant cancellation followed by a nested continuation throw resolves every cleanup handoff") {
          val source                      = new Completer[Int]
          val boom                        = new RuntimeException("nested-continuation")
          var running: Async.Running[Int] = null
          val cancellation                = new AtomicReference[Async[Unit]](null)
          val effect: Async[Int]          = source
            .map[Int] { _ => cancellation.set(running.cancelWithCleanup()); throw boom }
            .map(value => value)
          running = effect.start
          source.succeed(1)
          for {
            claimed <- Live.live(
                         ZIO
                           .succeed(cancellation.get().asInstanceOf[AnyRef] ne null)
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
            cleanup <- AsyncTestSupport.runAsync(cancellation.get().either)
          } yield assertTrue(
            claimed,
            cleanup == Right(()),
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("cancel reports an asynchronous cleanup failure exactly once") {
          val cleanupFailure = new RuntimeException("cleanup")
          val cleanup        = new TwoPollCleanup(cleanupFailure)
          val leaf           = new CleanupOnCancel(cleanup)
          val running        = AsyncTestSupport.fromPollable(leaf).start
          val reports        = new AtomicInteger(0)
          val reported       = new AtomicReference[Throwable](null)
          for {
            polled <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.polls.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
            _       = running.cancel { t => reports.incrementAndGet(); reported.set(t) }
            _       = running.cancel(_ => reports.incrementAndGet())
            failed <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(
            polled,
            failed,
            leaf.cancellations.get() == 1,
            cleanup.polls.get() == 2,
            reports.get() == 1,
            reported.get() eq cleanupFailure
          )
        },
        test("reentrant cancellation joins replacement cleanup and aggregates failures once") {
          val primaryFailure     = new RuntimeException("primary-cleanup")
          val replacementFailure = new RuntimeException("replacement-cleanup")
          val replacementCleanup = new TwoPollCleanup(replacementFailure)
          val replacement        = new CleanupOnCancel(replacementCleanup)
          val handle             = new AtomicReference[Async.Running[Int]](null)
          val wake               = new AtomicReference[Runnable](null)
          val reports            = new AtomicInteger(0)
          val reported           = new AtomicReference[Throwable](null)
          val polls              = new AtomicInteger(0)
          val predecessor        = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] =
              if (polls.incrementAndGet() == 1) { wake.set(onComplete); this }
              else {
                handle.get().cancel { t => reports.incrementAndGet(); reported.set(t) }
                replacement
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(primaryFailure)
          }
          val running = AsyncTestSupport.fromPollable(predecessor).start
          handle.set(running)
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(wake.get() ne null))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _       = wake.get().run()
            failed <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(
            armed,
            failed,
            replacement.cancellations.get() == 1,
            replacementCleanup.polls.get() == 2,
            reports.get() == 1,
            reported.get() eq primaryFailure,
            primaryFailure.getSuppressed().toList == List(replacementFailure),
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("reentrant cancellation closes cleanup handoff when poll throws") {
          val cleanupFailure = new RuntimeException("cleanup")
          val handle         = new AtomicReference[Async.Running[Int]](null)
          val wake           = new AtomicReference[Runnable](null)
          val reported       = new AtomicReference[Throwable](null)
          val polls          = new AtomicInteger(0)
          val predecessor    = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] =
              if (polls.incrementAndGet() == 1) { wake.set(onComplete); this }
              else {
                handle.get().cancel(reported.set)
                throw new RuntimeException("poll")
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(cleanupFailure)
          }
          val running = AsyncTestSupport.fromPollable(predecessor).start
          handle.set(running)
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(wake.get() ne null))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _         = wake.get().run()
            observed <- Live.live(
                          (ZIO.sleep(1.millis) *> ZIO.succeed(reported.get() ne null))
                            .repeatUntil(identity)
                            .timeoutTo(false)(identity)(5.seconds)
                        )
          } yield assertTrue(
            armed,
            observed,
            reported.get() eq cleanupFailure,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("cancel of ensuring drives primary cleanup and a pending finalizer exactly once") {
          val primaryCleanup = new TwoPollCleanup(null)
          val primary        = new CleanupOnCancel(primaryCleanup)
          val finalizer      = new TwoPollCleanup(null)
          val running        = AsyncTestSupport.fromPollable(primary).ensuring(finalizer).start
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(primary.polls.get() > 0))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _        = running.cancel()
            _        = running.cancel()
            cleaned <- Live.live(
                         (ZIO.sleep(1.millis) *> ZIO.succeed(finalizer.polls.get() >= 2))
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          } yield assertTrue(
            armed,
            cleaned,
            primary.cancellations.get() == 1,
            primaryCleanup.polls.get() == 2,
            finalizer.polls.get() == 2,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("cancel of ready ensuring joins a pending finalizer without cancelling or restarting it") {
          val wake      = new AtomicReference[Runnable](null)
          val complete  = new AtomicBoolean(false)
          val completed = new AtomicInteger(0)
          val polls     = new AtomicInteger(0)
          val cancels   = new AtomicInteger(0)
          val finalizer = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              polls.incrementAndGet()
              wake.set(onComplete)
              if (complete.get()) { completed.incrementAndGet(); Async.succeed(()) }
              else this
            }
            override def cancel(): Unit = cancels.incrementAndGet()
          }
          val running = Async.succeed(1).ensuring(finalizer).start
          for {
            started <- Live.live(
                         (ZIO.sleep(1.millis) *> ZIO.succeed(wake.get() ne null))
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
            _             = running.cancel()
            pollsAtCancel = polls.get()
            _             = complete.set(true)
            _             = wake.get().run()
            joined       <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(polls.get() > pollsAtCancel))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(started, joined, completed.get() == 1, cancels.get() == 0)
        },
        test("ensuring cancellation synchronously signals the active primary") {
          val signalled = new AtomicBoolean(false)
          val primary   = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              signalled.set(true)
              Async.succeed(())
            }
          }
          val running = AsyncTestSupport.fromPollable(primary).ensuring(Async.succeed(())).start
          running.cancel()
          assertTrue(signalled.get())
        },
        test("ensuring cancellation joins a replacement returned by the racing primary poll") {
          val primaryFailure     = new RuntimeException("ensuring-primary-cleanup")
          val replacementFailure = new RuntimeException("ensuring-replacement-cleanup")
          val replacementCleanup = new TwoPollCleanup(replacementFailure)
          val replacement        = new CleanupOnCancel(replacementCleanup)
          val handle             = new AtomicReference[Async.Running[Int]](null)
          val wake               = new AtomicReference[Runnable](null)
          val reports            = new AtomicInteger(0)
          val reported           = new AtomicReference[Throwable](null)
          val polls              = new AtomicInteger(0)
          val primary            = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] =
              if (polls.incrementAndGet() == 1) { wake.set(onComplete); this }
              else {
                handle.get().cancel { t => reports.incrementAndGet(); reported.set(t) }
                replacement
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(primaryFailure)
          }
          val running = AsyncTestSupport.fromPollable(primary).ensuring(Async.succeed(())).start
          handle.set(running)
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(wake.get() ne null))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _       = wake.get().run()
            failed <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(
            armed,
            failed,
            replacement.cancellations.get() == 1,
            replacementCleanup.polls.get() == 2,
            reports.get() == 1,
            reported.get() eq primaryFailure,
            primaryFailure.getSuppressed().toList == List(replacementFailure)
          )
        },
        test("cancel of nested ensuring unwinds finalizers inner-first") {
          val order                                = new java.util.concurrent.ConcurrentLinkedQueue[String]()
          def finalizer(name: String): Async[Unit] = new Pollable[Unit] {
            private val polls                           = new AtomicInteger(0)
            def poll(onComplete: Runnable): Async[Unit] =
              if (polls.incrementAndGet() == 1) { onComplete.run(); this }
              else { order.add(name); Async.succeed(()) }
          }
          val leaf    = new CancelAwareNever
          val running =
            AsyncTestSupport.fromPollable(leaf).ensuring(finalizer("inner")).ensuring(finalizer("outer")).start
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(leaf.polls.get() > 0))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _        = running.cancel()
            unwound <- Live.live(
                         (ZIO.sleep(1.millis) *> ZIO.succeed(order.size() == 2))
                           .repeatUntil(identity)
                           .timeoutTo(false)(identity)(5.seconds)
                       )
          } yield assertTrue(armed, unwound, order.toArray.toList == List("inner", "outer"))
        },
        test("cancel reentrant from an in-flight finalizer poll resumes it without cancelling or restarting") {
          val primary   = new Completer[Int]
          val handle    = new AtomicReference[Async.Running[Int]](null)
          val reports   = new AtomicInteger(0)
          val polls     = new AtomicInteger(0)
          val cancels   = new AtomicInteger(0)
          val finalizer = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] =
              if (polls.incrementAndGet() == 1) {
                handle.get().cancel(_ => reports.incrementAndGet())
                this
              } else Async.succeed(())
            override def cancel(): Unit = cancels.incrementAndGet()
          }
          val running = primary.peek.ensuring(finalizer).start
          handle.set(running)
          primary.succeed(1)
          for {
            finished <- Live.live(
                          (ZIO.sleep(1.millis) *> ZIO.succeed(polls.get() >= 2))
                            .repeatUntil(identity)
                            .timeoutTo(false)(identity)(5.seconds)
                        )
          } yield assertTrue(
            finished,
            polls.get() == 2,
            cancels.get() == 0,
            reports.get() == 0,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("cancel reentrant from a throwing finalizer reports its failure exactly once") {
          val failure   = new RuntimeException("reentrant-finalizer")
          val handle    = new AtomicReference[Async.Running[Int]](null)
          val reports   = new AtomicInteger(0)
          val reported  = new AtomicReference[Throwable](null)
          val primary   = new Completer[Int]
          val finalizer = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              handle.get().cancel { cause => reports.incrementAndGet(); reported.set(cause) }
              throw failure
            }
          }
          val running = primary.peek.ensuring(finalizer).start
          handle.set(running)
          primary.succeed(1)
          for {
            failed <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(failed, reports.get() == 1, reported.get() eq failure)
        },
        test("ensuring finalizer failure is reported by cancellation cleanup once") {
          val failure   = new RuntimeException("ensuring-finalizer")
          val primary   = new CancelAwareNever
          val finalizer = new TwoPollCleanup(failure)
          val reports   = new AtomicInteger(0)
          val reported  = new AtomicReference[Throwable](null)
          val running   = AsyncTestSupport.fromPollable(primary).ensuring(finalizer).start
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(primary.polls.get() > 0))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _       = running.cancel { t => reports.incrementAndGet(); reported.set(t) }
            _       = running.cancel(_ => reports.incrementAndGet())
            failed <- Live.live(
                        (ZIO.sleep(1.millis) *> ZIO.succeed(reports.get() > 0))
                          .repeatUntil(identity)
                          .timeoutTo(false)(identity)(5.seconds)
                      )
          } yield assertTrue(armed, failed, finalizer.polls.get() == 2, reports.get() == 1, reported.get() eq failure)
        },
        test("cancel invoked from inside poll suppresses the terminal that same poll returns") {
          // `cancel()` is driver-level and always wins: a leaf that cancels its
          // own Running handle mid-poll (e.g. observing shutdown) must not have
          // the value it then returns published as a terminal — the handle
          // stays pending forever, exactly like a cancel from outside.
          val handle = new AtomicReference[Async.Running[Int]](null)
          val stash  = new AtomicReference[Runnable](null)
          val polls  = new AtomicInteger(0)
          val leaf   = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] =
              if (polls.incrementAndGet() == 1) { stash.set(onComplete); this }
              else { handle.get().cancel(); Async.succeed(42) }
          }
          val running = AsyncTestSupport.fromPollable(leaf).start
          handle.set(running)
          for {
            armed <- Live.live(
                       (ZIO.sleep(1.millis) *> ZIO.succeed(stash.get() ne null))
                         .repeatUntil(identity)
                         .timeoutTo(false)(identity)(5.seconds)
                     )
            _         = stash.get().run() // resume the driver: the next poll cancels mid-flight
            repolled <- Live.live(
                          (ZIO.sleep(10.millis) *> ZIO.succeed(polls.get() >= 2))
                            .repeatUntil(identity)
                            .timeoutTo(false)(identity)(5.seconds)
                        )
            _ <- Live.live(ZIO.sleep(50.millis)) // allow any (defective) publish to land
          } yield assertTrue(
            armed,
            repolled,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        },
        test("cancel of a collectAll batch mid-drain suppresses the terminal and re-polls nothing further") {
          // `collectAll` is a single Pollable iterating its inputs internally.
          // Cancelling its Running while an element is still pending must behave
          // like any other suspended cancel: the terminal list is never
          // published (the handle stays pending), and no further elements are
          // driven once the run is cancelled.
          val polled           = new AtomicInteger(0)
          def slow: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { polled.incrementAndGet(); this }
          }
          val running = Async.collectAll(List(slow, slow, slow)).start
          for {
            _ <- Live.live(ZIO.sleep(50.millis))
            _  = running.cancel()
            _ <- Live.live(ZIO.sleep(50.millis))
          } yield assertTrue(
            polled.get() >= 1,
            AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable))
          )
        }
      ),
      test("a multi-wake pollable is not re-polled after completion (JVM/JS parity)") {
        val p = new DoubleWake
        for {
          result <- AsyncTestSupport.runAsync(p)
          // Let any stray resumptions (extra microtasks on JS) drain before we read.
          _ <- Live.live(ZIO.sleep(50.millis))
        } yield assertTrue(result == 42, p.polls.get() == 2)
      },
      test("start of a nested succeed carrier preserves depth (flatten agrees with the unstarted value)") {
        // A Running is itself an Async[A], so composing the same operator over
        // the started and unstarted value must agree. `flatten` peels exactly
        // one succeed layer, and `deliverSuccess` peels nested layers one at a
        // time — so a double-succeed pollable-as-value carrier must come out of
        // `start` with its depth intact. Collapsing it to depth 1 makes the
        // post-start `flatten` re-expose the user pollable as a suspended
        // computation, replacing the value with its polled scalar.
        val inner                               = AsyncTestSupport.pollableSuccessValue
        val nested: Async[Async[Pollable[Int]]] = Async.succeed(Async.succeed(inner))
        // Observe through Async[AnyRef] (covariance) so the regression fails as
        // an assertion on every platform: blocking at the precise Pollable type
        // makes the depth-collapsed scalar trip Scala.js's CHECKED class cast
        // (an uncatchable UndefinedBehaviorError that kills the JS runner).
        val direct: AnyRef  = (nested.flatten: Async[AnyRef]).block
        val started: AnyRef = (nested.start.flatten: Async[AnyRef]).block
        assertTrue(direct eq inner, started eq inner)
      }
    ),
    suite("remaining pollable branches")(
      test("wrappers converge deep silent replacement chains without synthetic wakes") {
        val wakes = new AtomicInteger(0)
        val wake  = new Runnable { def run(): Unit = { wakes.incrementAndGet(); () } }
        final class SilentChain(remaining: Int, taken: Int) extends Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] =
            if (remaining <= 0) Async.succeed(taken)
            else new SilentChain(remaining - 1, taken + 1)
        }
        def chain     = new SilentChain(100000, 0)
        val collected = Async.collectAll(List(AsyncTestSupport.fromPollable(chain))).asInstanceOf[Pollable[List[Int]]]
        val zipped    = AsyncTestSupport
          .fromPollable(chain)
          .zipWith(AsyncTestSupport.fromPollable(chain))(_ + _)
          .asInstanceOf[Pollable[Int]]
        val observed  = Async.slowPath.observe(chain, 7)
        val finalized = Async.slowPath
          .runThenValue(AsyncTestSupport.fromPollable(chain), 9, suppressFailure = false)
          .asInstanceOf[Pollable[Int]]
        assertTrue(
          foldStep(collected.poll(wake)) == FoldedSuccess(List(100000)),
          foldStep(zipped.poll(wake)) == FoldedSuccess(200000),
          foldStep(observed.poll(wake)) == FoldedSuccess(7),
          foldStep(finalized.poll(wake)) == FoldedSuccess(9),
          wakes.get() == 0
        )
      },
      test("map, flatMap, and catchAll never turn a replacement handoff into a readiness callback") {
        def predecessor[A](replacement: Async[A], captured: AtomicReference[Runnable]): Pollable[A] =
          new Pollable[A] {
            def poll(onComplete: Runnable): Async[A] = { captured.set(onComplete); replacement }
          }
        val wakes         = new AtomicInteger(0)
        val wake          = new Runnable { def run(): Unit = { wakes.incrementAndGet(); () } }
        val mappedWake    = new AtomicReference[Runnable](null)
        val flatWake      = new AtomicReference[Runnable](null)
        val recoveredWake = new AtomicReference[Runnable](null)
        val mapped        = AsyncTestSupport
          .fromPollable(
            predecessor(new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(1) }, mappedWake)
          )
          .map(_ + 1)
          .asInstanceOf[Pollable[Int]]
        val flatMapped = AsyncTestSupport
          .fromPollable(
            predecessor(new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(2) }, flatWake)
          )
          .flatMap(value => Async.succeed(value + 1))
          .asInstanceOf[Pollable[Int]]
        val recovered = AsyncTestSupport
          .fromPollable(
            predecessor(
              new Pollable[Int] {
                def poll(onComplete: Runnable): Async[Int] = Async.fail(new RuntimeException("expected"))
              },
              recoveredWake
            )
          )
          .catchAll(_ => Async.succeed(4))
          .asInstanceOf[Pollable[Int]]
        val results = (
          foldStep(mapped.poll(wake)),
          foldStep(flatMapped.poll(wake)),
          foldStep(recovered.poll(wake)),
          wakes.get()
        )
        mappedWake.get().run()
        flatWake.get().run()
        recoveredWake.get().run()
        assertTrue(
          results == ((FoldedSuccess(2), FoldedSuccess(3), FoldedSuccess(4), 0)),
          wakes.get() == 3
        )
      },
      test("same-identity suspension retains a live callback after distinct progress") {
        val wakes       = new AtomicInteger(0)
        val pendingWake = new AtomicReference[Runnable](null)
        var ready       = false
        val pending     = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] =
            if (ready) Async.succeed(1)
            else { pendingWake.set(onComplete); this }
        }
        val predecessor = new Pollable[Int] {
          private var first                          = true
          def poll(onComplete: Runnable): Async[Int] =
            if (first) {
              first = false
              onComplete.run()
              pending
            } else pending
        }
        val effect = AsyncTestSupport
          .fromPollable(predecessor)
          .map(identity)
          .catchAll(_ => Async.succeed(-1))
          .asInstanceOf[Pollable[Int]]
        val observer = new Runnable { def run(): Unit = { wakes.incrementAndGet(); () } }

        val suspended = effect.poll(observer)
        val afterPoll = wakes.get()
        ready = true
        pendingWake.get().run()
        val afterCompletion = wakes.get()
        val terminal        = effect.poll(observer)

        assertTrue(
          suspended.asInstanceOf[AnyRef] eq effect.asInstanceOf[AnyRef],
          afterPoll == 1,
          afterCompletion == 2,
          foldStep(terminal) == FoldedSuccess(1)
        )
      },
      test("dynamically produced continuations and deep recovery cancellation are stack safe") {
        def ready(value: Int): Async[Int] = AsyncTestSupport.fromPollable(new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(value)
        })
        def dynamic(n: Int): Async[Int] =
          ready(n).flatMap(value => if (value == 0) Async.succeed(0) else dynamic(value - 1))
        val never                 = new CancelAwareNever
        var recovered: Async[Int] = AsyncTestSupport.fromPollable(never)
        var i                     = 0
        while (i < 20000) { recovered = recovered.catchAll(_ => Async.succeed(0)); i += 1 }
        val cleanup = recovered.asInstanceOf[Pollable[Int]].cancelWithCleanup()
        assertTrue(
          driveWithFold(dynamic(20000), maxPolls = 25000) == FoldedSuccess(0),
          driveWithFold(cleanup, maxPolls = 25000) == FoldedSuccess(()),
          never.cancels.get() == 1
        )
      },
      test("catchAll memoizes source and handler terminals, pending replacements, throws, failures, and null") {
        def failedSource(cause: Throwable): Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.fail(cause)
        }
        val cause  = new RuntimeException("source")
        val thrown = new RuntimeException("handler")
        val calls  = new AtomicInteger
        val same   = new Pollable[Int] {
          private var n                              = 0
          def poll(onComplete: Runnable): Async[Int] = { n += 1; if (n == 1) this else Async.succeed(11) }
        }
        val distinct = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(12)
        }
        def recovered(result: => Async[Int]) =
          AsyncTestSupport.fromPollable(failedSource(cause)).catchAll { _ => calls.incrementAndGet(); result }
        val value           = recovered(Async.succeed(10))
        val pendingSame     = recovered(same)
        val pendingDistinct = recovered(distinct)
        val failure         = recovered(Async.fail(thrown))
        val nulled          = recovered(Async.succeed(null.asInstanceOf[Int]))
        val handlerThrow    = recovered(throw thrown)
        val sourceSuccess   = AsyncTestSupport
          .fromPollable(new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(9)
          })
          .catchAll(_ => Async.succeed(0))
        val a  = driveWithFold(value)
        val a2 = driveWithFold(value)
        assertTrue(
          a == FoldedSuccess(10),
          a2 == FoldedSuccess(10),
          driveWithFold(pendingSame) == FoldedSuccess(11),
          driveWithFold(pendingDistinct) == FoldedSuccess(12),
          driveWithFold(failure) == FoldedFailure(thrown),
          driveWithFold(nulled) == FoldedSuccess(0),
          driveWithFold(handlerThrow) == FoldedFailure(thrown),
          driveWithFold(sourceSuccess) == FoldedSuccess(9),
          calls.get() == 6
        )
      },
      test("collectAll and zipWith cover replacements, failure, throw, null, and stable repeated polls") {
        def oneShot[A](result: Async[A]): Pollable[A] = new Pollable[A] {
          def poll(onComplete: Runnable): Async[A] = result
        }
        val failure     = new RuntimeException("element")
        val replacement = oneShot(Async.succeed(2))
        val first       = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = { onComplete.run(); replacement }
        }
        val all    = Async.collectAll(List(AsyncTestSupport.fromPollable(first), Async.succeed(3)))
        val failed =
          Async.collectAll(List(AsyncTestSupport.fromPollable(oneShot(Async.fail(failure))), Async.succeed(3)))
        val zipped = AsyncTestSupport
          .fromPollable(oneShot(Async.succeed(4)))
          .zipWith(AsyncTestSupport.fromPollable(oneShot(Async.succeed(5))))(_ + _)
        val combineThrow = new RuntimeException("combine")
        val throwing     = AsyncTestSupport
          .fromPollable(oneShot(Async.succeed(1)))
          .zipWith(Async.succeed(2))((_, _) => throw combineThrow)
        val nulled = AsyncTestSupport
          .fromPollable(oneShot(Async.succeed[String](null)))
          .zipWith(Async.succeed("x"))((a, b) => String.valueOf(a) + b)
        assertTrue(
          driveWithFold(all) == FoldedSuccess(List(2, 3)),
          driveWithFold(all) == FoldedSuccess(List(2, 3)),
          driveWithFold(failed) == FoldedFailure(failure),
          driveWithFold(zipped) == FoldedSuccess(9),
          driveWithFold(zipped) == FoldedSuccess(9),
          scala.util.Try(driveWithFold(throwing)).failed.toOption.contains(combineThrow),
          driveWithFold(nulled) == FoldedSuccess("nullx")
        )
      },
      test("observed follows same and distinct pending values, terminal failure, null, and cancellation") {
        val failure  = new RuntimeException("observed")
        val distinct = new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.succeed(1) }
        val same     = new Pollable[Int] {
          private var first                          = true
          def poll(onComplete: Runnable): Async[Int] =
            if (first) { first = false; this }
            else distinct
        }
        val observed = Async.slowPath.observe(same, null.asInstanceOf[String])
        val failed   = Async.slowPath.observe(
          new Pollable[Int] { def poll(onComplete: Runnable): Async[Int] = Async.fail(failure) },
          9
        )
        val never     = new CancelAwareNever
        val cancelled = Async.slowPath.observe(never, 1)
        cancelled.cancel()
        assertTrue(
          driveWithFold(observed) == FoldedSuccess(null),
          driveWithFold(failed) == FoldedFailure(failure),
          AsyncTestSupport.isPending(cancelled.poll(AsyncTestSupport.noopRunnable)),
          never.cancels.get() == 1
        )
      },
      test(
        "shared cancellation covers terminal and replacement polling plus fallback value, null, failure, and throw"
      ) {
        def leaf(result: => Async[Int]) = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = result
        }
        val wakeThrows                                          = new Runnable { def run(): Unit = throw new RuntimeException("wake") }
        val replacement                                         = leaf(Async.succeed(2))
        val shared                                              = Async.shareCancellation(leaf(replacement))
        val p1                                                  = shared.poll(wakeThrows)
        val fallbackFailure                                     = new RuntimeException("fallback-failure")
        val fallbackThrow                                       = new RuntimeException("fallback-throw")
        def cancelledTo(fallback: => Async[Int]): Pollable[Int] = {
          val p = Async.cancelTo(new CancelAwareNever, () => fallback)
          p.cancelWithCleanup().block
          val settled  = new Completer[Unit]
          val observed = p.poll(new Runnable {
            def run(): Unit = { settled.succeed(()); () }
          })
          if (AsyncTestSupport.isPending(observed)) settled.peek.block
          p
        }
        val value  = cancelledTo(Async.succeed(7))
        val nulled = cancelledTo(Async.succeed(null.asInstanceOf[Int]))
        val failed = cancelledTo(Async.fail(fallbackFailure))
        val thrown = cancelledTo(throw fallbackThrow)
        assertTrue(
          foldStep(p1) == FoldedSuccess(2),
          driveWithFold(value) == FoldedSuccess(7),
          driveWithFold(nulled) == FoldedSuccess(0),
          driveWithFold(failed) == FoldedFailure(fallbackFailure),
          driveWithFold(thrown) == FoldedFailure(fallbackThrow)
        )
      },
      test(
        "selector selection covers pending scan, rejected and throwing claims, memoized winner, cancel, and shutdown"
      ) {
        val pending       = new CancelAwareNever
        val ready         = new Completer[Int]
        val thrown        = new RuntimeException("claim")
        val readyForThrow = new Completer[Int]
        ready.succeed(2)
        readyForThrow.succeed(3)
        val selector         = Async.selector[Int](IndexedSeq(pending, ready))
        val throwingSelector = Async.selector[Int](IndexedSeq(readyForThrow))
        val rejected         = selector.selectClaim(() => false).asInstanceOf[Pollable[(Int, Int)]]
        val throwing         = throwingSelector.selectClaim(() => throw thrown)
        val accepted         = selector.select
        val rejectedPoll     = rejected.poll(AsyncTestSupport.noopRunnable)
        val throwingResult   = driveWithFold(throwing)
        val winner           = driveWithFold(accepted)
        val winnerAgain      = driveWithFold(accepted)
        val waiting          = selector.select.asInstanceOf[Pollable[(Int, Int)]]
        val waitingPoll      = waiting.poll(AsyncTestSupport.noopRunnable)
        waiting.cancel()
        val afterCancel = waiting.poll(AsyncTestSupport.noopRunnable)
        selector.shutdown.block
        throwingSelector.shutdown.block
        val closed = driveWithFold(selector.select)
        assertTrue(
          AsyncTestSupport.isPending(rejectedPoll),
          throwingResult == FoldedFailure(thrown),
          winner == FoldedSuccess((1, 2)),
          winnerAgain.isInstanceOf[FoldedFailure],
          AsyncTestSupport.isPending(waitingPoll),
          afterCancel.isInstanceOf[Failure],
          closed.isInstanceOf[FoldedFailure],
          pending.cancels.get() == 1
        )
      }
    ),
    suite("cancel")(
      test("cancel is idempotent and a no-op after synchronous completion") {
        var count   = 0
        val running = AsyncTestSupport.startTap(Async.succeed(1))(_ => count += 1)
        running.cancel()
        running.cancel()
        assertTrue(count == 1)
      },
      test("cancel before completion suppresses observer") {
        val c       = new Completer[Int]
        val fired   = new AtomicBoolean(false)
        val running = AsyncTestSupport.startTap(c.peek)(_ => fired.set(true))
        running.cancel()
        c.succeed(1)
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      },
      test("cancel is idempotent on a genuinely suspended run") {
        val fired   = new AtomicBoolean(false)
        val running = AsyncTestSupport.startTap[Nothing](Async.never)(_ => fired.set(true))
        running.cancel()
        running.cancel()
        Live.live(ZIO.sleep(100.millis)).as(assertTrue(!fired.get()))
      }
    )
  )
}
