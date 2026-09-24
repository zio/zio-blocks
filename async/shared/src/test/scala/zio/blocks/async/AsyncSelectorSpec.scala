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

import java.util.concurrent.atomic.AtomicInteger
import zio.ZIO
import zio.test._

object AsyncSelectorSpec extends ZIOSpecDefault {
  private class CountedPending(cleanup: Async[Unit] = Async.succeed(())) extends Pollable[Int] {
    val polls          = new AtomicInteger
    val cancellations  = new AtomicInteger
    private var wakers = List.empty[Runnable]

    def poll(onComplete: Runnable): Async[Int] = synchronized {
      polls.incrementAndGet()
      if (!wakers.exists(_ eq onComplete)) wakers = onComplete :: wakers
      this
    }

    def distinctWakers: Int = synchronized(wakers.size)

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      cancellations.incrementAndGet()
      cleanup
    }
  }

  def spec = suite("AsyncSelectorSpec")(
    test("round-robin cursor advances exactly across ready slots") {
      val selector = Async.selector(Vector(Async.succeed(0), Async.succeed(1), Async.succeed(2)))
      for {
        winners <- ZIO.foreach((0 until 3).toList)(_ => AsyncTestSupport.runAsync(selector.select).map(_._1))
        _       <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(winners == List(0, 1, 2))
    },
    test("repeated ready selection retains round-robin ownership and pending losers") {
      val pending  = new CountedPending
      val selector = Async.selector(Vector[Async[Int]](Async.succeed(0), Async.succeed(10), pending))
      var seen     = List.empty[(Int, Int)]
      val handler  = new AsyncSelector.ReadyHandler[Int] {
        def apply(index: Int, value: Int): Boolean = {
          seen = seen :+ ((index, value))
          selector.replace(index, Async.succeed(value + 1))
          true
        }
      }
      for {
        _ <- AsyncTestSupport.runAsync(selector.selectClaimReadyRepeat(() => true, 6, handler))
        _ <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(
        seen == List((0, 0), (1, 10), (0, 1), (1, 11), (0, 2), (1, 12)),
        pending.distinctWakers <= 1,
        pending.cancellations.get() == 1
      )
    },
    test("pending losers retain one stable listener across repeated selections") {
      val loser    = new CountedPending
      val selector = Async.selector(Vector[Async[Int]](loser, Async.succeed(1)))
      for {
        winners <- ZIO.foreach((0 until 10000).toList) { _ =>
                     AsyncTestSupport.runAsync(selector.select).map { winner =>
                       selector.replace(1, Async.succeed(1))
                       winner._1
                     }
                   }
        _ <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(winners.forall(_ == 1), loser.distinctWakers == 1, loser.cancellations.get() == 1)
    },
    test("close signals every loser before joining and waits for asynchronous cleanup") {
      val gate     = new Completer[Unit]
      val first    = new CountedPending(gate)
      val second   = new CountedPending
      val selector = Async.selector(Vector[Async[Int]](first, second))
      val closing  = selector.shutdown.start
      val before   = closing.poll(AsyncTestSupport.noopRunnable)
      for {
        _ <- ZIO.succeed(gate.succeed(()))
        _ <- AsyncTestSupport.runAsync(closing)
        _ <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(
        before.isInstanceOf[Pollable[?]],
        first.cancellations.get() == 1,
        second.cancellations.get() == 1
      )
    },
    test("shutdown completion observes cleanup from outside its cancellation batch") {
      val releaseGate     = new Completer[Unit]
      val releaseStarted  = new Completer[Unit]
      val releases        = new AtomicInteger
      val cancellableLeaf = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int]                   = this
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          releases.incrementAndGet()
          releaseStarted.succeed(())
          releaseGate
        }
      }
      val selector             = Async.selector(Vector[Async[Int]](cancellableLeaf.map(_ + 1)))
      var closing: Async[Unit] = null
      val trigger              = new Pollable[Unit] {
        def poll(onComplete: Runnable): Async[Unit]                  = this
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          closing = selector.shutdown
          closing
        }
      }
      val root    = trigger.map(identity).asInstanceOf[Pollable[Unit]]
      val cleanup = Async.cancelWithCleanup(root).start
      for {
        _     <- AsyncTestSupport.runAsync(releaseStarted)
        before = closing.asInstanceOf[Pollable[Unit]].poll(AsyncTestSupport.noopRunnable)
        _      = releaseGate.succeed(())
        _     <- AsyncTestSupport.runAsync(cleanup)
        _     <- AsyncTestSupport.runAsync(closing)
      } yield assertTrue(before.isInstanceOf[Pollable[?]], releases.get() == 1)
    },
    test("reentrant shutdown and a cancelled waiter join the same detached cleanup") {
      val gate                         = new Completer[Unit]
      var selector: AsyncSelector[Int] = null
      var reentered: Async[Unit]       = null
      val first                        = new CountedPending {
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          cancellations.incrementAndGet()
          reentered = selector.shutdown
          Async.succeed(())
        }
      }
      val second = new CountedPending(gate)
      selector = Async.selector(Vector[Async[Int]](first, second))
      val abandoned = selector.shutdown.start
      abandoned.cancel()
      val reentrantPending = reentered.start
      val secondPending    = selector.shutdown.start
      val beforeReentrant  = reentrantPending.poll(AsyncTestSupport.noopRunnable)
      val beforeSecond     = secondPending.poll(AsyncTestSupport.noopRunnable)
      for {
        _ <- ZIO.succeed(gate.succeed(()))
        _ <- AsyncTestSupport.runAsync(reentrantPending)
        _ <- AsyncTestSupport.runAsync(secondPending)
      } yield assertTrue(
        beforeReentrant.isInstanceOf[Pollable[?]],
        beforeSecond.isInstanceOf[Pollable[?]],
        first.cancellations.get() == 1,
        second.cancellations.get() == 1
      )
    },
    test("close cancels a mapped never-completing loser without waiting for its value") {
      val never    = new Completer[Int]
      val selector = Async.selector(Vector[Async[Int]](Async.fail(new RuntimeException("winner")), never.map(_ + 1)))
      for {
        selected <- AsyncTestSupport.runAsync(selector.select.either)
        _        <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(selected.isLeft)
    },
    test("close joins all cleanup failures in slot order") {
      val firstFailure  = new RuntimeException("first")
      val secondFailure = new RuntimeException("second")
      val first         = new CountedPending(Async.fail(firstFailure))
      val second        = new CountedPending(Async.fail(secondFailure))
      val selector      = Async.selector(Vector[Async[Int]](first, second))
      AsyncTestSupport.runAsync(selector.shutdown.either).map { result =>
        assertTrue(
          result == Left(firstFailure),
          firstFailure.getSuppressed.toList == List(secondFailure),
          first.cancellations.get() == 1,
          second.cancellations.get() == 1
        )
      }
    },
    test("a null cleanup failure is retained and replayed by shutdown") {
      val child    = new CountedPending(Async.fail(null))
      val selector = Async.selector(Vector[Async[Int]](child))
      for {
        first  <- AsyncTestSupport.runAsync(selector.shutdown.either)
        replay <- AsyncTestSupport.runAsync(selector.shutdown.either)
      } yield assertTrue(first == Left(null), replay == Left(null), child.cancellations.get() == 1)
    },
    test("replacement construction is deferred outside selector and caller monitors") {
      val calls    = new AtomicInteger
      val selector = Async.selectorWithCapacity[Int](1, Vector.empty)
      selector.synchronized {
        selector.replace(0, { calls.incrementAndGet(); Async.succeed(1) })
        Predef.assert(calls.get() == 0)
      }
      AsyncTestSupport.runAsync(selector.shutdown).map(_ => assertTrue(calls.get() == 0))
    },
    test("known replacement preserves ready selection and pending shutdown ownership") {
      val pending  = new CountedPending
      val selector = Async.selectorWithCapacity[Int](1, Vector.empty)
      selector.replaceKnown(0, Async.succeed(1))
      for {
        winner <- AsyncTestSupport.runAsync(selector.select)
        _       = selector.replaceKnown(0, pending)
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(winner == ((0, 1)), pending.cancellations.get() == 1)
    },
    test("an initializing slot does not hide a later ready slot") {
      val calls    = new AtomicInteger
      val pending  = new Completer[Int]
      val selector = Async.selectorWithCapacity[Int](2, Vector.empty)
      selector.replace(
        0, {
          calls.incrementAndGet()
          pending
        }
      )
      selector.replace(1, Async.succeed(1))
      for {
        winner <- AsyncTestSupport.runAsync(selector.select)
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(winner == ((1, 1)), calls.get() <= 1)
    },
    test("a completed winner is removed exactly once") {
      val pending  = new CountedPending
      val selector = Async.selector(Vector[Async[Int]](Async.succeed(1), pending))
      for {
        first       <- AsyncTestSupport.runAsync(selector.select)
        next         = selector.select.start
        stillPending = next.poll(AsyncTestSupport.noopRunnable).isInstanceOf[Pollable[?]]
        _            = next.cancel()
        _           <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(first == ((0, 1)), stillPending)
    },
    test("cancelled selection unregisters its waiter") {
      val pending   = new CountedPending
      val selector  = Async.selector(Vector[Async[Int]](pending))
      val wakeCount = new AtomicInteger
      val selection = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      val waiter    = new Runnable { def run(): Unit = wakeCount.incrementAndGet() }
      selection.poll(waiter)
      selection.cancel()
      AsyncTestSupport.runAsync(selector.shutdown).map(_ => assertTrue(wakeCount.get() == 0))
    },
    test("cancellation racing a terminal poll preserves the winner for the next selection") {
      val polls                           = new AtomicInteger
      var selection: Pollable[(Int, Int)] = null
      val input                           = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          val _ = onComplete
          polls.incrementAndGet()
          selection.cancel()
          Async.succeed(1)
        }
      }
      val selector = Async.selector(Vector[Async[Int]](input))
      selection = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      val abandoned = selection.poll(AsyncTestSupport.noopRunnable)
      for {
        winner <- AsyncTestSupport.runAsync(selector.select)
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(
        abandoned.isInstanceOf[Failure],
        winner == ((0, 1)),
        polls.get() == 1
      )
    },
    test("a selection that loses a completed slot remains registered for its replacement") {
      val first      = new Completer[Int]
      val selector   = Async.selector(Vector[Async[Int]](first))
      val left       = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      val right      = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      val leftWakes  = new AtomicInteger
      val rightWakes = new AtomicInteger
      val leftWake   = new Runnable { def run(): Unit = leftWakes.incrementAndGet() }
      val rightWake  = new Runnable { def run(): Unit = rightWakes.incrementAndGet() }
      left.poll(leftWake)
      right.poll(rightWake)
      first.succeed(1)
      for {
        winner            <- AsyncTestSupport.runAsync(left)
        loserStillPending  = right.poll(rightWake).isInstanceOf[Pollable[?]]
        wakesBeforeReplace = rightWakes.get()
        _                  = selector.replace(0, Async.succeed(2))
        replacement       <- AsyncTestSupport.runAsync(right)
        _                 <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(
        winner == ((0, 1)),
        loserStillPending,
        rightWakes.get() > wakesBeforeReplace,
        replacement == ((0, 2))
      )
    },
    test("a distinct pending poll replacement becomes the slot's running input") {
      val polls       = new AtomicInteger
      val replacement = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          polls.incrementAndGet()
          Async.succeed(2)
        }
      }
      val predecessor = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = replacement
      }
      val selector = Async.selector(Vector[Async[Int]](predecessor))
      for {
        winner <- AsyncTestSupport.runAsync(selector.select)
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(winner == ((0, 2)), polls.get() == 1)
    },
    test("shutdown racing a reserved poll cancels and joins its distinct replacement") {
      val failure       = new RuntimeException("replacement-cleanup")
      val gate          = new Completer[Unit]
      val cancellations = new AtomicInteger
      val replacement   = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int]                   = this
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          cancellations.incrementAndGet()
          gate.flatMap(_ => Async.fail(failure))
        }
      }
      var selector: AsyncSelector[Int] = null
      var closing: Async[Unit]         = null
      val predecessor                  = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          closing = selector.shutdown
          replacement
        }
      }
      selector = Async.selector(Vector[Async[Int]](predecessor))
      val selected = selector.select.asInstanceOf[Pollable[(Int, Int)]].poll(AsyncTestSupport.noopRunnable)
      val running  = closing.start
      val before   = running.poll(AsyncTestSupport.noopRunnable)
      for {
        _      <- ZIO.succeed(gate.succeed(()))
        result <- AsyncTestSupport.runAsync(running.either)
      } yield assertTrue(
        selected.isInstanceOf[Failure],
        before.isInstanceOf[Pollable[?]],
        result == Left(failure),
        cancellations.get() == 1
      )
    },
    test("reentrant shutdown from poll fails closed without claiming") {
      var selector: AsyncSelector[Int] = null
      var closing: Async[Unit]         = null
      val claims                       = new AtomicInteger
      val cancellations                = new AtomicInteger
      val input                        = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          closing = selector.shutdown
          Async.succeed(1)
        }
        override private[async] def cancelWithCleanup(): Async[Unit] = {
          cancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      selector = Async.selector(Vector[Async[Int]](input))
      for {
        result <- AsyncTestSupport.runAsync(selector.selectClaim { () => claims.incrementAndGet(); true }.either)
        _      <- AsyncTestSupport.runAsync(closing)
      } yield assertTrue(result.isLeft, claims.get() == 0, cancellations.get() == 1)
    },
    test("claim runs externally: accepted claim racing shutdown transfers ownership without cancellation") {
      val input = new CountedPending {
        override def poll(onComplete: Runnable): Async[Int] = {
          polls.incrementAndGet()
          Async.succeed(1)
        }
      }
      var selector: AsyncSelector[Int] = null
      var closing: Async[Unit]         = null
      selector = Async.selector(Vector[Async[Int]](input))
      for {
        selected <- AsyncTestSupport.runAsync(selector.selectClaim { () => closing = selector.shutdown; true }.either)
        _        <- AsyncTestSupport.runAsync(closing)
      } yield assertTrue(selected == Right((0, 1)), input.cancellations.get() == 0)
    },
    test("rejected claim racing shutdown retains ownership and shutdown joins cleanup") {
      val gate  = new Completer[Unit]
      val input = new CountedPending(gate) {
        override def poll(onComplete: Runnable): Async[Int] = {
          polls.incrementAndGet()
          Async.succeed(1)
        }
      }
      var selector: AsyncSelector[Int] = null
      var closing: Async[Unit]         = null
      selector = Async.selector(Vector[Async[Int]](input))
      for {
        selected <- AsyncTestSupport.runAsync(selector.selectClaim { () => closing = selector.shutdown; false }.either)
        running   = closing.start
        before    = running.poll(AsyncTestSupport.noopRunnable)
        _        <- ZIO.succeed(gate.succeed(()))
        _        <- AsyncTestSupport.runAsync(running)
      } yield assertTrue(selected.isLeft, before.isInstanceOf[Pollable[?]], input.cancellations.get() == 1)
    },
    test("rejected then accepted claim polls the underlying input once") {
      val input = new CountedPending {
        override def poll(onComplete: Runnable): Async[Int] = {
          polls.incrementAndGet()
          Async.succeed(1)
        }
      }
      val selector = Async.selector(Vector[Async[Int]](input))
      val rejected = selector.selectClaim(() => false).asInstanceOf[Pollable[(Int, Int)]]
      val first    = rejected.poll(AsyncTestSupport.noopRunnable)
      for {
        winner <- AsyncTestSupport.runAsync(selector.selectClaim(() => true))
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(first.isInstanceOf[Pollable[?]], winner == ((0, 1)), input.polls.get() == 1)
    },
    test("rejected null success is cached for the next claimant") {
      val polls = new AtomicInteger
      val input = new Pollable[String] {
        def poll(onComplete: Runnable): Async[String] = {
          polls.incrementAndGet()
          Async.succeed(null)
        }
      }
      val selector = Async.selector(Vector[Async[String]](input))
      val rejected = selector.selectClaim(() => false).asInstanceOf[Pollable[(Int, String)]]
      val first    = rejected.poll(AsyncTestSupport.noopRunnable)
      for {
        winner <- AsyncTestSupport.runAsync(selector.selectClaim(() => true))
        _      <- AsyncTestSupport.runAsync(selector.shutdown)
      } yield assertTrue(first.isInstanceOf[Pollable[?]], winner == ((0, null)), polls.get() == 1)
    },
    test("consumed and closed selections fail deterministically") {
      val selector  = Async.selector(Vector[Async[Int]](Async.succeed(1)))
      val selection = selector.select.asInstanceOf[Pollable[(Int, Int)]]
      for {
        winner   <- AsyncTestSupport.runAsync(selection)
        consumed <- AsyncTestSupport.runAsync(selection.poll(AsyncTestSupport.noopRunnable).either)
        _        <- AsyncTestSupport.runAsync(selector.shutdown)
        closed   <- AsyncTestSupport.runAsync(selector.select.either)
      } yield assertTrue(
        winner == ((0, 1)),
        consumed.left.exists(_.getMessage == "selection was already consumed"),
        closed.left.exists(_.getMessage == "selector is closed")
      )
    },
    test("poll exceptions are selectable failures and throwing claims consume only their selection") {
      val pollFailure  = new RuntimeException("poll")
      val claimFailure = new RuntimeException("claim")
      val throwingPoll = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = throw pollFailure
      }
      val first  = Async.selector(Vector[Async[Int]](throwingPoll))
      val second = Async.selector(Vector[Async[Int]](Async.succeed(2)))
      for {
        pollResult  <- AsyncTestSupport.runAsync(first.select.either)
        claimResult <- AsyncTestSupport.runAsync(second.selectClaim(() => throw claimFailure).either)
        winner      <- AsyncTestSupport.runAsync(second.select)
        _           <- AsyncTestSupport.runAsync(first.shutdown)
        _           <- AsyncTestSupport.runAsync(second.shutdown)
      } yield assertTrue(pollResult == Left(pollFailure), claimResult == Left(claimFailure), winner == ((0, 2)))
    },
    test("pending slots are skipped and replacement validation covers sparse selectors") {
      val pending      = new CountedPending
      val selector     = Async.selectorWithCapacity[Int](3, Vector(1 -> pending, 2 -> Async.succeed(2)))
      val invalidSize  = scala.util.Try(Async.selectorWithCapacity[Int](0, Vector.empty)).failed.toOption
      val invalidIndex =
        scala.util.Try(Async.selectorWithCapacity[Int](2, Vector(2 -> Async.succeed(1)))).failed.toOption
      val duplicate = scala.util
        .Try(
          Async.selectorWithCapacity[Int](2, Vector(0 -> Async.succeed(1), 0 -> Async.succeed(2)))
        )
        .failed
        .toOption
      val replaceArmed = scala.util.Try(selector.replace(1, Async.succeed(3))).failed.toOption
      val replaceIndex = scala.util.Try(selector.replace(-1, Async.succeed(3))).failed.toOption
      for {
        winner       <- AsyncTestSupport.runAsync(selector.select)
        _            <- AsyncTestSupport.runAsync(selector.shutdown)
        replaceClosed = scala.util.Try(selector.replace(2, Async.succeed(3))).failed.toOption
      } yield assertTrue(
        selector.size == 3,
        winner == ((2, 2)),
        invalidSize.exists(_.isInstanceOf[IllegalArgumentException]),
        invalidIndex.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        duplicate.exists(_.isInstanceOf[IllegalArgumentException]),
        replaceArmed.exists(_.isInstanceOf[IllegalStateException]),
        replaceIndex.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        replaceClosed.exists(_.isInstanceOf[IllegalStateException])
      )
    },
    test("shutdown converts a throwing cancellation into its exact cleanup failure") {
      val failure = new RuntimeException("cancel")
      val child   = new CountedPending {
        override private[async] def cancelWithCleanup(): Async[Unit] = throw failure
      }
      val selector = Async.selector(Vector[Async[Int]](child))
      AsyncTestSupport.runAsync(selector.shutdown.either).map(result => assertTrue(result == Left(failure)))
    }
  )
}
