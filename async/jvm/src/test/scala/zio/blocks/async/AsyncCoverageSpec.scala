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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/** Deterministic probes for the fire-and-forget cancellation entry points. */
object AsyncCoverageSpec extends ZIOSpecDefault {
  private final class Pending(cleaned: AtomicInteger, latch: CountDownLatch = null) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int]                   = this
    override private[async] def cancelWithCleanup(): Async[Unit] = {
      cleaned.incrementAndGet()
      if (latch ne null) latch.countDown()
      Async.succeed(())
    }
  }

  private final class FailingPending[A](cancels: AtomicInteger, failure: Throwable) extends Pollable[A] {
    def poll(onComplete: Runnable): Async[A]                     = this
    override private[async] def cancelWithCleanup(): Async[Unit] = {
      cancels.incrementAndGet()
      Async.fail(failure)
    }
  }

  private final class Reentrant[A](
    root: AtomicReference[Pollable[_]],
    cleanup: AtomicReference[Async[Unit]],
    polls: AtomicInteger,
    cancels: AtomicInteger,
    cancelFailure: Throwable,
    result: () => Async[A]
  ) extends Pollable[A] {
    def poll(onComplete: Runnable): Async[A] = {
      polls.incrementAndGet()
      cleanup.set(root.get().cancelWithCleanup())
      result()
    }
    override private[async] def cancelWithCleanup(): Async[Unit] = {
      cancels.incrementAndGet()
      if (cancelFailure eq null) Async.succeed(()) else Async.fail(cancelFailure)
    }
  }

  private def suppressed(t: Throwable): List[Throwable] = t.getSuppressed.toList

  def spec = suite("AsyncCoverageSpec")(
    test("cancel entry points initiate cleanup for every owning wrapper") {
      ZIO.attemptBlocking {
        val cleaned = new AtomicInteger
        val latch   = new CountDownLatch(3)

        Async.shareCancellation(new Pending(cleaned, latch)).cancel()
        Async
          .acquireCancelable[Int](() => 1, _ => { cleaned.incrementAndGet(); Async.succeed(()) })
          .asInstanceOf[Pollable[Int]]
          .cancel()
        Async
          .bracketAsync[Int, Int](() => Async.succeed(1), _ => new Pending(cleaned, latch), _ => Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
          .cancel()
        AsyncTestSupport
          .fromPollable(new Pending(cleaned, latch))
          .map(identity)
          .asInstanceOf[Pollable[Int]]
          .cancel()
        AsyncTestSupport
          .fromPollable(new Pending(cleaned, latch))
          .ensuring(Async.succeed(()))
          .asInstanceOf[Pollable[Int]]
          .cancel()
        Async.reschedule[Int](() => new Pending(cleaned, latch)).asInstanceOf[Pollable[Int]].cancel()

        assertTrue(latch.await(5, TimeUnit.SECONDS), cleaned.get() >= 3)
      }
    },
    test("throwing callbacks are isolated while replacement callbacks remain observable") {
      ZIO.attemptBlocking {
        val wakes       = new AtomicInteger
        val replacement = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(42)
        }
        val source = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = replacement
        }
        val throwing = new Runnable { def run(): Unit = throw new RuntimeException("wake") }
        val mapped   = AsyncTestSupport.fromPollable(source).map(_ + 1).asInstanceOf[Pollable[Int]]
        mapped.poll(throwing)

        val callback = new Runnable { def run(): Unit = wakes.incrementAndGet() }
        val zipped   = AsyncTestSupport
          .fromPollable(source)
          .zipWith(AsyncTestSupport.fromPollable(source))(_ + _)
          .asInstanceOf[Pollable[Int]]
        val value = zipped.poll(callback)
        assertTrue(value.block == 84, wakes.get() == 0)
      }
    },
    test("reschedule cancellation before start is idempotent and cleans the known child once") {
      ZIO.attemptBlocking {
        val cleaned     = new AtomicInteger
        val rescheduled = Async
          .rescheduleKnown[Int](new Pending(cleaned), () => ())
          .asInstanceOf[Pollable[Int]]

        val first  = rescheduled.cancelWithCleanup()
        val second = rescheduled.cancelWithCleanup()

        assertTrue(first.block == (), second.block == (), cleaned.get() == 1)
      }
    },
    test("default and operation cancellation capture throwing hooks and ready reschedule cancellation") {
      ZIO.attemptBlocking {
        val defaultFailure   = new RuntimeException("default cancel")
        val operationFailure = new RuntimeException("operation cancel")
        val successfulLeaf   = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = this
        }
        val throwingLeaf = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = this
          override def cancel(): Unit                = throw defaultFailure
        }
        val successfulOperation = new Async.Operation[Int] {
          def poll(onComplete: Runnable): Async[Int]   = this
          protected def cancelOperation(): Async[Unit] = Async.succeed(())
        }
        val throwingOperation = new Async.Operation[Int] {
          def poll(onComplete: Runnable): Async[Int]   = this
          protected def cancelOperation(): Async[Unit] = throw operationFailure
        }
        val readyReschedule = Async
          .rescheduleKnown[Int](Async.succeed(1), () => ())
          .asInstanceOf[Pollable[Int]]
        successfulOperation.cancel()
        throwingOperation.cancel()

        assertTrue(
          successfulLeaf.cancelWithCleanup().either.block == Right(()),
          throwingLeaf.cancelWithCleanup().either.block == Left(defaultFailure),
          successfulOperation.cancelWithCleanup().either.block == Right(()),
          throwingOperation.cancelWithCleanup().either.block == Left(operationFailure),
          readyReschedule.cancelWithCleanup().either.block == Right(())
        )
      }
    },
    test("slow-path entry points preserve immediate failures and finalizer policy") {
      ZIO.attempt {
        val failure    = new RuntimeException("slow path")
        val failed     = Async.fail(failure)
        val mapped     = Async.slowPath.mapAsync[Int, Int](failed, _ + 1)
        val flatMapped = Async.slowPath.flatMapAsync[Int, Int](failed, value => Async.succeed(value + 1))
        val recovered  = Async.slowPath.catchAllAsync[Int, Int](failed, _ => Async.succeed(42))
        val suppressed = Async.slowPath.runThenValue[Int](failed, 7, suppressFailure = true)
        val propagated = Async.slowPath.runThenValue[Int](failed, 7, suppressFailure = false)
        val blocked    =
          try { Async.slowPath.awaitSuspended(failed.asInstanceOf[Pollable[Int]]); null }
          catch { case cause: Throwable => cause }

        assertTrue(
          mapped.asInstanceOf[AnyRef] eq failed.asInstanceOf[AnyRef],
          flatMapped.asInstanceOf[AnyRef] eq failed.asInstanceOf[AnyRef],
          recovered.block == 42,
          suppressed.block == 7,
          propagated.asInstanceOf[AnyRef] eq failed.asInstanceOf[AnyRef],
          blocked eq failure
        )
      }
    },
    test("catchAll distinguishes a throwing source poll from throwing and replacement handlers") {
      ZIO.attempt {
        val sourceBoom     = new RuntimeException("source-poll")
        val handlerBoom    = new RuntimeException("handler-poll")
        val handled        = new AtomicInteger
        val throwingSource = AsyncTestSupport.fromPollable(new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = throw sourceBoom
        })
        val recovered = throwingSource.catchAll { _ =>
          handled.incrementAndGet()
          Async.succeed(-1)
        }
        val sourceThrown =
          try { AsyncTestSupport.pollOnce(recovered); null }
          catch { case t: Throwable => t }

        val throwingHandler = AsyncTestSupport
          .fromPollable(AsyncTestSupport.failAfter(new RuntimeException("source-failure"), 0))
          .catchAll(_ =>
            AsyncTestSupport.fromPollable(new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] = throw handlerBoom
            })
          )
        val handlerThrown =
          try { AsyncTestSupport.driveToEnd(AsyncTestSupport.pollOnce(throwingHandler)); null }
          catch { case cause: Throwable => cause }

        val replacement    = AsyncTestSupport.syncReadyPollable(73)
        val pendingHandler = AsyncTestSupport
          .fromPollable(AsyncTestSupport.failAfter(new RuntimeException("replace-source"), 0))
          .catchAll(_ =>
            AsyncTestSupport.fromPollable(new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] = replacement
            })
          )
        val pendingResult = AsyncTestSupport.pollOnce(pendingHandler)

        assertTrue(
          sourceThrown eq sourceBoom,
          handled.get() == 0,
          handlerThrown eq handlerBoom,
          AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(pendingResult)) == Right(73)
        )
      }
    },
    test("zipWith drives left and right replacements and preserves failures") {
      ZIO.attempt {
        val leftFailure                               = new RuntimeException("left-replacement")
        val rightFailure                              = new RuntimeException("right-replacement")
        def replacing[A](next: Pollable[A]): Async[A] = AsyncTestSupport.fromPollable(new Pollable[A] {
          def poll(onComplete: Runnable): Async[A] = next
        })

        val leftFailed = replacing[Int](AsyncTestSupport.failAfter(leftFailure, 0))
          .zipWith(Async.succeed(2))(_ + _)
        val rightFailed = Async
          .succeed(2)
          .zipWith(replacing[Int](AsyncTestSupport.failAfter(rightFailure, 0)))(_ + _)
        val bothReady = replacing(AsyncTestSupport.syncReadyPollable(20))
          .zipWith(replacing(AsyncTestSupport.syncReadyPollable(22)))(_ + _)

        assertTrue(
          AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(leftFailed)) == Left(leftFailure),
          AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(rightFailed)) == Left(rightFailure),
          AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(bothReady)) == Right(42)
        )
      }
    },
    test("run-then-value suppresses throwing finalizers only for ensuring") {
      ZIO.attempt {
        val finalizerBoom                  = new RuntimeException("finalizer-poll")
        def throwingFinalizer: Async[Unit] = AsyncTestSupport.fromPollable(new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = throw finalizerBoom
        })

        val ensured   = Async.succeed(9).ensuring(throwingFinalizer)
        val tapped    = Async.succeed(9).tap(_ => throwingFinalizer)
        val tapThrown =
          try { AsyncTestSupport.pollOnce(tapped); null }
          catch { case t: Throwable => t }

        assertTrue(
          AsyncTestSupport.outcome(AsyncTestSupport.driveToEnd(ensured)) == Right(9),
          tapThrown eq finalizerBoom
        )
      }
    },
    test("acquireCancelable reentrant polling and cancellation publish exactly one late cleanup") {
      ZIO.attemptBlocking {
        def cancelledAcquire(result: Either[Throwable, Int], releaseResult: Int => Async[Unit]) = {
          val entered                = new CountDownLatch(1)
          val proceed                = new CountDownLatch(1)
          val releases               = new AtomicInteger
          val reentrant              = new AtomicReference[Async[Int]]
          val pollResult             = new AtomicReference[Async[Int]]
          var pending: Pollable[Int] = null
          pending = Async
            .acquireCancelable[Int](
              () => {
                reentrant.set(pending.poll(AsyncTestSupport.noopRunnable))
                entered.countDown()
                proceed.await()
                result.fold(throw _, identity)
              },
              value => { releases.incrementAndGet(); releaseResult(value) }
            )
            .asInstanceOf[Pollable[Int]]
          val driver = new Thread(() => pollResult.set(pending.poll(AsyncTestSupport.noopRunnable)))
          driver.start()
          entered.await()
          val cleanup = pending.cancelWithCleanup()
          proceed.countDown()
          driver.join()
          (pending, reentrant.get(), pollResult.get(), cleanup.either.block, releases.get())
        }

        val acquireFailure = new RuntimeException("late acquire")
        val releaseFailure = new RuntimeException("late release")
        val failed         = cancelledAcquire(Left(acquireFailure), _ => Async.succeed(()))
        val released       = cancelledAcquire(Right(42), _ => Async.fail(releaseFailure))
        val nullRelease    = cancelledAcquire(Right(43), _ => null)
        val throwing       = cancelledAcquire(Right(44), _ => throw releaseFailure)

        assertTrue(
          failed._2.asInstanceOf[AnyRef] eq failed._1.asInstanceOf[AnyRef],
          failed._3.asInstanceOf[AnyRef] eq failed._1.asInstanceOf[AnyRef],
          failed._4 == Right(()),
          failed._5 == 0,
          released._4 == Left(releaseFailure),
          released._5 == 1,
          nullRelease._4.left.exists(_.isInstanceOf[NullPointerException]),
          nullRelease._5 == 1,
          throwing._4 == Left(releaseFailure),
          throwing._5 == 1
        )
      }
    },
    test("bracket cancellation owns use cleanup failures and suppresses release failure once") {
      ZIO.attemptBlocking {
        val useFailure     = new RuntimeException("use cleanup")
        val releaseFailure = new RuntimeException("release cleanup")
        val useCancels     = new AtomicInteger
        val releases       = new AtomicInteger
        val releasedValue  = new AtomicReference[String]
        val use            = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = this
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            useCancels.incrementAndGet()
            throw useFailure
          }
        }
        val bracket = Async
          .bracketAsync[String, Int](
            () => Async.succeed("resource"),
            _ => use,
            resource => {
              releasedValue.set(resource)
              releases.incrementAndGet()
              Async.fail(releaseFailure)
            }
          )
          .asInstanceOf[Pollable[Int]]
        bracket.poll(AsyncTestSupport.noopRunnable)
        val cleanup = bracket.cancelWithCleanup()
        val result  = cleanup.either.block
        val again   = bracket.cancelWithCleanup().either.block

        assertTrue(
          result == Left(useFailure),
          again == Left(useFailure),
          useFailure.getSuppressed.toList == List(releaseFailure),
          useCancels.get() == 1,
          releases.get() == 1,
          releasedValue.get() == "resource",
          bracket.poll(AsyncTestSupport.noopRunnable).asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef]
        )
      }
    },
    test("run-then-value joins reentrant cancellation for every child poll result") {
      ZIO.attempt {
        def run(suppress: Boolean, kind: String) = {
          val primary                    = new RuntimeException(s"run-primary-$suppress-$kind")
          val replacement                = new RuntimeException(s"run-replacement-$suppress-$kind")
          val rootRef                    = new AtomicReference[Pollable[_]]
          val cleanupRef                 = new AtomicReference[Async[Unit]]
          val polls                      = new AtomicInteger
          val cancels                    = new AtomicInteger
          val replacementCancels         = new AtomicInteger
          lazy val same: Reentrant[Unit] = new Reentrant(
            rootRef,
            cleanupRef,
            polls,
            cancels,
            primary,
            () =>
              kind match {
                case "failure"  => Async.fail(new RuntimeException("finalizer-result"))
                case "terminal" => Async.succeed(())
                case "same"     => same
                case _          => new FailingPending[Unit](replacementCancels, replacement)
              }
          )
          val async = if (suppress) Async.succeed(7).ensuring(same) else Async.succeed(7).tap(_ => same)
          val root  = async.asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val result  = root.poll(new Runnable { def run(): Unit = () })
          val cleanup = cleanupRef.get().either.block
          (root, result, cleanup, primary, replacement, polls.get(), cancels.get(), replacementCancels.get())
        }

        val cases = List("failure", "terminal", "same", "replacement").map(run(suppress = false, _))
        assertTrue(cases.zip(List("failure", "terminal", "same", "replacement")).forall { case (c, kind) =>
          val expectedSuppressed = if (c._8 == 1) List(c._5) else Nil
          ((kind == "failure") || (c._2.asInstanceOf[AnyRef] eq c._1.asInstanceOf[AnyRef])) &&
          c._3.left.exists(t => (t eq c._4) && suppressed(t) == expectedSuppressed) &&
          c._6 == 1 && c._7 == 1 && c._8 == (if (expectedSuppressed.isEmpty) 0 else 1)
        })
      }
    },
    test("catchAll closes reentrant failure and recovery-result handoffs exactly once") {
      ZIO.attempt {
        def sourceCase() = {
          val primary = new RuntimeException("catch-source-cleanup")
          val rootRef = new AtomicReference[Pollable[_]]
          val cleanup = new AtomicReference[Async[Unit]]
          val polls   = new AtomicInteger
          val cancels = new AtomicInteger
          val handled = new AtomicInteger
          val child   = new Reentrant[Int](
            rootRef,
            cleanup,
            polls,
            cancels,
            primary,
            () => Async.fail(new RuntimeException("source"))
          )
          val root = AsyncTestSupport
            .fromPollable(child)
            .catchAll { _ => handled.incrementAndGet(); Async.succeed(1) }
            .asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val result = root.poll(AsyncTestSupport.noopRunnable)
          (root, result, cleanup.get().either.block, primary, polls.get(), cancels.get(), handled.get())
        }
        def recoveryCase(kind: String) = {
          val primary                    = new RuntimeException(s"catch-primary-$kind")
          val replacement                = new RuntimeException(s"catch-replacement-$kind")
          val rootRef                    = new AtomicReference[Pollable[_]]
          val cleanup                    = new AtomicReference[Async[Unit]]
          val polls                      = new AtomicInteger
          val cancels                    = new AtomicInteger
          val replCancels                = new AtomicInteger
          val handlers                   = new AtomicInteger
          lazy val inner: Reentrant[Int] = new Reentrant(
            rootRef,
            cleanup,
            polls,
            cancels,
            primary,
            () =>
              kind match {
                case "failure"  => Async.fail(new RuntimeException("recovery"))
                case "terminal" => Async.succeed(9)
                case "same"     => inner
                case _          => new FailingPending[Int](replCancels, replacement)
              }
          )
          val root = AsyncTestSupport
            .fromPollable(AsyncTestSupport.failAfter(new RuntimeException("source"), 0))
            .catchAll { _ => handlers.incrementAndGet(); inner }
            .asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val handed = root.poll(AsyncTestSupport.noopRunnable)
          rootRef.set(handed.asInstanceOf[Pollable[Int]])
          val result = handed.asInstanceOf[Pollable[Int]].poll(AsyncTestSupport.noopRunnable)
          (
            root,
            handed,
            result,
            cleanup.get().either.block,
            primary,
            replacement,
            polls.get(),
            cancels.get(),
            replCancels.get(),
            handlers.get()
          )
        }

        val source     = sourceCase()
        val recoveries = List("failure", "terminal", "same", "replacement").map(recoveryCase)
        assertTrue(
          source._2.asInstanceOf[AnyRef] eq source._1.asInstanceOf[AnyRef],
          source._3 == Left(source._4),
          source._5 == 1,
          source._6 == 1,
          source._7 == 0,
          recoveries.forall(c =>
            (c._2.asInstanceOf[AnyRef] ne c._1.asInstanceOf[AnyRef]) &&
              c._4.left.exists(t => (t eq c._5) && suppressed(t).isEmpty) &&
              c._7 == 1 && c._8 == 1 && c._9 == 0 && c._10 == 1
          )
        )
      }
    },
    test("zipWith joins reentrant cancellation from either child for all poll results") {
      ZIO.attempt {
        def run(left: Boolean, kind: String) = {
          val primary                    = new RuntimeException(s"zip-primary-$left-$kind")
          val replacement                = new RuntimeException(s"zip-replacement-$left-$kind")
          val rootRef                    = new AtomicReference[Pollable[_]]
          val cleanup                    = new AtomicReference[Async[Unit]]
          val polls                      = new AtomicInteger
          val cancels                    = new AtomicInteger
          val replCancels                = new AtomicInteger
          lazy val child: Reentrant[Int] = new Reentrant(
            rootRef,
            cleanup,
            polls,
            cancels,
            primary,
            () =>
              kind match {
                case "failure"  => Async.fail(new RuntimeException("zip-result"))
                case "terminal" => Async.succeed(20)
                case "same"     => child
                case _          => new FailingPending[Int](replCancels, replacement)
              }
          )
          val async =
            if (left) AsyncTestSupport.fromPollable(child).zipWith(Async.succeed(22))(_ + _)
            else Async.succeed(22).zipWith(AsyncTestSupport.fromPollable(child))(_ + _)
          val root = async.asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val result = root.poll(AsyncTestSupport.noopRunnable)
          (
            root,
            result,
            cleanup.get().either.block,
            primary,
            replacement,
            polls.get(),
            cancels.get(),
            replCancels.get()
          )
        }
        val cases = for {
          left <- List(true, false); kind <- List("failure", "terminal", "same", "replacement")
        } yield (run(left, kind), kind)
        assertTrue(cases.forall { case (c, kind) =>
          ((kind == "failure") || (c._2.asInstanceOf[AnyRef] eq c._1.asInstanceOf[AnyRef])) &&
          c._3.left.exists(t => (t eq c._4) && suppressed(t) == (if (c._8 == 1) List(c._5) else Nil)) &&
          c._6 == 1 && c._7 == 1 && c._8 <= 1
        })
      }
    },
    test("collectAll joins reentrant cancellation for failure, terminal, same, and replacement") {
      ZIO.attempt {
        def run(kind: String) = {
          val primary                    = new RuntimeException(s"collect-primary-$kind")
          val replacement                = new RuntimeException(s"collect-replacement-$kind")
          val rootRef                    = new AtomicReference[Pollable[_]]
          val cleanup                    = new AtomicReference[Async[Unit]]
          val polls                      = new AtomicInteger
          val cancels                    = new AtomicInteger
          val replCancels                = new AtomicInteger
          lazy val child: Reentrant[Int] = new Reentrant(
            rootRef,
            cleanup,
            polls,
            cancels,
            primary,
            () =>
              kind match {
                case "failure"  => Async.fail(new RuntimeException("collect-result"))
                case "terminal" => Async.succeed(1)
                case "same"     => child
                case _          => new FailingPending[Int](replCancels, replacement)
              }
          )
          val root = Async
            .collectAll(List(AsyncTestSupport.fromPollable(child), Async.succeed(2)))
            .asInstanceOf[Pollable[List[Int]]]
          rootRef.set(root)
          val result = root.poll(AsyncTestSupport.noopRunnable)
          (
            root,
            result,
            cleanup.get().either.block,
            primary,
            replacement,
            polls.get(),
            cancels.get(),
            replCancels.get()
          )
        }
        val cases = List("failure", "terminal", "same", "replacement").map(run)
        assertTrue(cases.zip(List("failure", "terminal", "same", "replacement")).forall { case (c, kind) =>
          ((kind == "failure") || (c._2.asInstanceOf[AnyRef] eq c._1.asInstanceOf[AnyRef])) &&
          c._3.left.exists(t => (t eq c._4) && suppressed(t) == (if (c._8 == 1) List(c._5) else Nil)) &&
          c._6 == 1 && c._7 == 1 && c._8 <= 1
        })
      }
    },
    test("selector poll handoffs survive reentrant shutdown and selection cancellation") {
      ZIO.attempt {
        val pendingCancels                      = new AtomicInteger
        val pendingClose                        = new AtomicReference[Async[Unit]]
        var pendingSelector: AsyncSelector[Int] = null
        val pendingChild                        = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            pendingClose.set(pendingSelector.shutdown)
            this
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            pendingCancels.incrementAndGet()
            Async.succeed(())
          }
        }
        pendingSelector = Async.selector(IndexedSeq(pendingChild))
        val pendingResult = AsyncTestSupport.pollOnce(pendingSelector.select)

        val terminalCancels                         = new AtomicInteger
        var terminalSelection: Pollable[(Int, Int)] = null
        val terminalChild                           = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = {
            terminalSelection.cancel()
            Async.succeed(11)
          }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            terminalCancels.incrementAndGet()
            Async.succeed(())
          }
        }
        val terminalSelector = Async.selector(IndexedSeq(terminalChild))
        terminalSelection = terminalSelector.select.asInstanceOf[Pollable[(Int, Int)]]
        val terminalResult = terminalSelection.poll(AsyncTestSupport.noopRunnable)
        terminalSelection.cancel()
        terminalSelector.shutdown.block

        val rejectedCancels                      = new AtomicInteger
        val rejectedClose                        = new AtomicReference[Async[Unit]]
        var rejectedSelector: AsyncSelector[Int] = null
        val rejectedChild                        = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int]                   = Async.succeed(17)
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            rejectedCancels.incrementAndGet()
            Async.succeed(())
          }
        }
        rejectedSelector = Async.selector(IndexedSeq(rejectedChild))
        val rejected = rejectedSelector.selectClaim { () =>
          rejectedClose.set(rejectedSelector.shutdown)
          false
        }
        val rejectedResult = AsyncTestSupport.pollOnce(rejected)
        rejectedClose.get().block

        assertTrue(
          AsyncTestSupport.outcome(pendingResult).left.exists(_.getMessage == "selector is closed"),
          pendingClose.get().either.block == Right(()),
          pendingCancels.get() == 1,
          AsyncTestSupport.outcome(terminalResult).left.exists(_.getMessage == "selector is closed"),
          terminalCancels.get() == 1,
          AsyncTestSupport.outcome(rejectedResult).left.exists(_.getMessage == "selector is closed"),
          rejectedCancels.get() == 1
        )
      }
    },
    test("shared cancellation joins a reentrant throwing poll once and isolates waiter and fallback throws") {
      ZIO.attempt {
        val pollFailure           = new RuntimeException("shared-poll")
        val fallbackFailure       = new RuntimeException("shared-fallback")
        val cancels               = new AtomicInteger
        val polls                 = new AtomicInteger
        val wakes                 = new AtomicInteger
        val awakened              = new CountDownLatch(1)
        val cleanupRef            = new AtomicReference[Async[Unit]]
        var shared: Pollable[Int] = null
        val child                 = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] =
            if (polls.incrementAndGet() == 1) this
            else {
              cleanupRef.set(shared.cancelWithCleanup())
              throw pollFailure
            }
          override private[async] def cancelWithCleanup(): Async[Unit] = {
            cancels.incrementAndGet()
            Async.succeed(())
          }
        }
        shared = Async.cancelTo(child, () => throw fallbackFailure)
        val countingWaiter = new Runnable {
          def run(): Unit = {
            wakes.incrementAndGet()
            awakened.countDown()
          }
        }
        val throwingWaiter = new Runnable { def run(): Unit = throw new RuntimeException("waiter") }
        shared.poll(countingWaiter)
        shared.poll(throwingWaiter)
        val first   = cleanupRef.get().either.block
        val second  = shared.cancelWithCleanup().either.block
        val settled = shared.poll(countingWaiter).either.block
        val woke    = awakened.await(10, TimeUnit.SECONDS)

        assertTrue(
          first == Right(()),
          second == Right(()),
          settled == Left(fallbackFailure),
          cancels.get() == 1,
          woke,
          wakes.get() == 1,
          pollFailure.getSuppressed.toList.isEmpty
        )
      }
    },
    test("reset supervisor deduplicates waiters and aggregates throwing reset and close paths in order") {
      ZIO.attemptBlocking {
        val resetConstructorFailure = new RuntimeException("reset-constructor")
        val closeConstructorFailure = new RuntimeException("close-constructor")
        val direct                  = Async
          .superviseResetUnit(() => throw resetConstructorFailure, () => throw closeConstructorFailure)
          .asInstanceOf[Pollable[Unit]]
        val directResult = direct.cancelWithCleanup().either.block

        val pollFailure    = new RuntimeException("reset-poll")
        val closeFailure   = new RuntimeException("close-poll")
        val wakeCount      = new AtomicInteger
        val waiter         = new Runnable { def run(): Unit = wakeCount.incrementAndGet() }
        val throwingWaiter = new Runnable { def run(): Unit = throw new RuntimeException("reset-waiter") }
        val throwingReset  = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = throw pollFailure
        }
        val throwingClose = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = throw closeFailure
        }
        val supervised = Async
          .superviseResetUnit(() => throwingReset, () => throwingClose)
          .asInstanceOf[Pollable[Unit]]
        supervised.poll(waiter)
        supervised.poll(waiter)
        supervised.poll(throwingWaiter)
        val cleanup = supervised.cancelWithCleanup()
        supervised.poll(waiter)
        supervised.poll(waiter)
        val cleanupResult = cleanup.either.block
        val repeated      = supervised.cancelWithCleanup().either.block

        assertTrue(
          directResult == Left(resetConstructorFailure),
          resetConstructorFailure.getSuppressed.toList == List(closeConstructorFailure),
          cleanupResult == Left(pollFailure),
          repeated == Left(pollFailure),
          pollFailure.getSuppressed.toList == List(closeFailure),
          wakeCount.get() == 3
        )
      }
    }
  )
}
