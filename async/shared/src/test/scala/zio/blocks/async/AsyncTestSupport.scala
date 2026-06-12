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

import zio._

/** Shared fixtures and helpers for async module tests. */
private[async] object AsyncTestSupport {

  val boom: Throwable  = new RuntimeException("boom")
  val boom2: Throwable = new RuntimeException("boom2")

  val noopRunnable: Runnable = () => ()

  def pollableSuccessValue: Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
  }

  def syncReadyPollable[A](value: A): Pollable[A] = new Pollable[A] {
    def poll(onComplete: Runnable): Async[A] = Async.succeed(value)
  }

  def syncReadyAfterPollable[A](value: A, pollsNeeded: Int): Pollable[A] =
    new Pollable[A] {
      private var remaining                    = pollsNeeded
      def poll(onComplete: Runnable): Async[A] =
        if (remaining <= 0) Async.succeed(value)
        else {
          remaining -= 1
          onComplete.run()
          this
        }
    }

  def failAfter(t: Throwable, pollsNeeded: Int): Pollable[Nothing] =
    new Pollable[Nothing] {
      private var remaining                          = pollsNeeded
      def poll(onComplete: Runnable): Async[Nothing] =
        if (remaining <= 0) Async.fail(t)
        else { remaining -= 1; onComplete.run(); this }
    }

  def succeedAfter[A](value: A, pollsNeeded: Int): Pollable[A] =
    new Pollable[A] {
      private var remaining                    = pollsNeeded
      def poll(onComplete: Runnable): Async[A] =
        if (remaining <= 0) Async.succeed(value)
        else { remaining -= 1; onComplete.run(); this }
    }

  def fromPollable[A](p: Pollable[A]): Async[A] = p

  def blockAsLeftCause(fa: Async[Any]): Option[Throwable] =
    try {
      fa.block
      None
    } catch {
      case Failure.NullCauseMarker => Some(null)
      case t: Throwable            => Some(t)
    }

  def isPending(fa: Async[Any]): Boolean = {
    val any: Any = fa
    any.isInstanceOf[Pollable[?]] && !any.isInstanceOf[Failure]
  }

  def pollOnce[A](fa: Async[A]): Async[A] =
    fa.asInstanceOf[Pollable[A]].poll(noopRunnable)

  def driveToEnd[A](start: Async[A], maxPolls: Int = 32): Async[A] = {
    var cur: Any = start
    var i        = 0
    while (cur.isInstanceOf[Pollable[?]] && !cur.isInstanceOf[Failure] && i < maxPolls) {
      cur = cur.asInstanceOf[Pollable[A]].poll(noopRunnable)
      i += 1
    }
    cur.asInstanceOf[Async[A]]
  }

  def outcome[A](fa: Async[A]): Either[Throwable, A] = {
    val any: Any = fa
    if (any.isInstanceOf[Failure]) Left(any.asInstanceOf[Failure].cause)
    else Right(any.asInstanceOf[A])
  }

  def pending[A]: (Completer[A], Async[A]) = {
    val c = new Completer[A]
    (c, c.peek)
  }

  /**
   * Witness `pollable-as-value` on chains that recover from `Async[Int]` (and
   * similar) to `Async[Pollable[Int]]`. `catchAll` requires `A1 >: A`, and
   * `Pollable[Int]` is not a supertype of `Int`.
   */
  def pollableValueAsync(fa: Async[Any]): Async[Pollable[Int]] =
    fa.asInstanceOf[Async[Pollable[Int]]]

  def runAsync[A](fa: Async[A]): Task[A] =
    ZIO.async[Any, Throwable, A] { k =>
      val running = Async.start(fa)
      object Resume {
        def apply(next: Async[A]): Unit = {
          val any = next.asInstanceOf[Any]
          if (any.isInstanceOf[Failure]) k(ZIO.fail(any.asInstanceOf[Failure].cause))
          else if (any.isInstanceOf[Async.Running[?]]) ()
          else k(ZIO.succeed(any.asInstanceOf[A]))
        }
      }
      val onComplete = new Runnable { def run(): Unit = Resume(running.poll(this)) }
      Resume(running.poll(onComplete))
      ()
    }

  /** Start `fa` and observe its terminal `Either` (compositional `start`). */
  def startEither[A](fa: Async[A])(observe: Either[Throwable, A] => Unit): Async.Running[Either[Throwable, A]] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure] || !AsyncEncoding.isSuspended(any))
      Async.start(fa.either.tap { e => observe(e); Async.succeed(()) })
    else {
      val running = Async.start(fa)
      Async.start(running.either.tap { e => observe(e); Async.succeed(()) })
    }
  }

  /** Start `fa` and observe each success value (compositional `start`). */
  def startTap[A](fa: Async[A])(observe: A => Unit): Async.Running[A] =
    Async.start(fa.tap { a => observe(a); Async.succeed(()) })

  val sideFx: Throwable      = new RuntimeException("side-fx")
  val handlerFx: Throwable   = new RuntimeException("handler-throw")
  val combineFx: Throwable   = new RuntimeException("combine-throw")
  val mapperFx: Throwable    = new RuntimeException("mapper-throw")
  val leftSent: Throwable    = new RuntimeException("left-sentinel")
  val midSent: Throwable     = new RuntimeException("mid-sentinel")
  val rightSent: Throwable   = new RuntimeException("right-sentinel")
  val primary: Throwable     = new RuntimeException("primary")
  val finPoll: Throwable     = new RuntimeException("finalizer-poll-throw")
  val outerPoll: Throwable   = new RuntimeException("outer-poll-throw")
  val contPoll: Throwable    = new RuntimeException("flatMap-cont-poll-throw")
  val handlerPoll: Throwable = new RuntimeException("handler-poll-throw")
  val primaryPoll: Throwable = new RuntimeException("primary-poll-throw")
  val leftBoom: Throwable    = new RuntimeException("left")
  val rightBoom: Throwable   = new RuntimeException("right")
  val original: Throwable    = new RuntimeException("original")
  val promiseBody: Throwable = new RuntimeException("promise-body")

  def unwindFutureEither[A](e: Either[Throwable, A]): Either[Throwable, A] =
    e.left.map(Failure.unwindCause)

  def blockAsNullCause(fa: Async[Any]): Either[Throwable, Unit] =
    try {
      fa.block
      Right(())
    } catch {
      case Failure.NullCauseMarker => Left(null)
      case t: Throwable            => Left(t)
    }

  def throwingOuter: Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = throw outerPoll
  }

  def throwingContinuation: Pollable[String] = new Pollable[String] {
    def poll(onComplete: Runnable): Async[String] = throw contPoll
  }

  def throwingFinalizer: Pollable[Any] = new Pollable[Any] {
    def poll(onComplete: Runnable): Async[Any] = throw finPoll
  }

  def throwingRecovery: Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = throw handlerPoll
  }

  def throwingTapEffect: Pollable[Unit] = new Pollable[Unit] {
    def poll(onComplete: Runnable): Async[Unit] = throw finPoll
  }

  def throwingPrimary: Pollable[Int] = new Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = throw primaryPoll
  }

  def trailingPollable(tail: Pollable[Int]): Pollable[Int] = tail

  def pendingNullFail: (Completer[Unit], Async[Nothing]) = {
    val c = new Completer[Unit]
    (c, c.peek.flatMap(_ => Async.fail(null)))
  }
}
