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

import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.util.function.BiConsumer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure => SFailure, Success => SSuccess}

/**
 * Converts between [[Async]] and the JVM's standard asynchronous types: Scala's
 * [[scala.concurrent.Future]] and Java's [[CompletionStage]] and
 * [[CompletableFuture]]. Use these conversions to adopt `Async` in one part of
 * a program while the code around it keeps passing futures.
 *
 * Callers reach them through `Async.fromFuture` and `Async.fromCompletionStage`
 * on the companion and the `toFuture` and `toCompletableFuture` extension
 * methods, which forward here. Each direction keeps the value or the failure.
 * Scala.js has the same `Future` conversions, with `js.Promise` in place of
 * `CompletionStage` and `CompletableFuture`.
 *
 * {{{
 * import scala.concurrent.{ExecutionContext, Future}
 *
 * implicit val ec: ExecutionContext = ExecutionContext.global
 * def loadPrice(sku: String): Future[Int] = Future.successful(120)
 * val withTax: Future[Int] = Async.fromFuture(loadPrice("sku-1")).map(_ + 24).toFuture
 * withTax.value // Some(Success(144))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
 */
private[async] object AsyncInterop {

  /** Implements `Async.fromFuture`. */
  def fromFuture[A](future: Future[A]): Async[A] = {
    val cur = future.value
    if (cur.isDefined) cur.get match {
      case SSuccess(v) => Async.succeed(v)
      case SFailure(t) => Async.fail(Failure.unwindCause(t))
    }
    else
      Async.promiseInternal[A] { c =>
        future.onComplete {
          case SSuccess(v) => c.succeed(v)
          case SFailure(t) => c.fail(Failure.unwindCause(t))
        }(ExecutionContext.parasitic)
      }
  }

  /** Implements `Async.fromCompletionStage`. */
  def fromCompletionStage[A](stage: CompletionStage[A]): Async[A] = {
    val cf = stage.toCompletableFuture
    if (cf.isDone)
      try Async.succeed(cf.get())
      catch { case t: Throwable => Async.fail(unwrapCompletionException(t)) }
    else
      Async.promiseInternal[A] { c =>
        cf.whenComplete(new BiConsumer[A, Throwable] {
          def accept(value: A, error: Throwable): Unit =
            if (error eq null) c.succeed(value)
            else c.fail(unwrapCompletionException(error))
        })
        ()
      }
  }

  /** Implements the `toFuture` extension method. */
  def toFuture[A](fa: Async[A])(implicit ec: ExecutionContext): Future[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failFuture(any.asInstanceOf[Failure].cause)
    else if (AsyncEncoding.requiresDriver(any)) {
      val p = Promise[A]()
      drive(fa, (value: A) => { p.success(value); () }, (cause: Throwable) => failPromise(p, cause))
      p.future
    } else
      try Future.successful(Async.slowPath.block[A](fa))
      catch { case t: Throwable => failFuture(Failure.unwindCause(t)) }
  }

  /** Implements the `toCompletableFuture` extension method. */
  def toCompletableFuture[A](fa: Async[A])(implicit ec: ExecutionContext): CompletableFuture[A] = {
    val any = fa.asInstanceOf[Any]
    val cf  = new CompletableFuture[A]()
    if (any.isInstanceOf[Failure]) {
      completeCfExceptionally(cf, any.asInstanceOf[Failure].cause)
      cf
    } else if (AsyncEncoding.requiresDriver(any)) { // see toFuture: includes depth-1 carriers
      drive(fa, (value: A) => { cf.complete(value); () }, (cause: Throwable) => completeCfExceptionally(cf, cause))
      cf
    } else
      try { cf.complete(Async.slowPath.block[A](fa)); cf }
      catch {
        case t: Throwable => completeCfExceptionally(cf, t); cf
      }
  }

  /**
   * Completes `cf` exceptionally, with `Failure.NullCauseMarker` in place of a
   * `null` cause, which `completeExceptionally` rejects.
   */
  private def completeCfExceptionally[A](cf: CompletableFuture[A], t: Throwable): Unit = {
    val cause = Failure.unwindCause(t)
    if (cause eq null) cf.completeExceptionally(Failure.NullCauseMarker)
    else cf.completeExceptionally(cause)
  }

  /**
   * Starts `fa` on the async runner (the common `ForkJoinPool`) and reports its
   * outcome to `succeed` or `fail` from a step that runs on `ec`.
   */
  private def drive[A](
    fa: Async[A],
    succeed: A => Unit,
    fail: Throwable => Unit
  )(implicit ec: ExecutionContext): Unit = {
    val running             = Async.startRegistered(fa)(_ => ())
    lazy val step: Runnable = new Runnable {
      def run(): Unit =
        try
          Async.foldStep(running.poll(wake))(new Async.StepFold[A, Unit] {
            def success(value: A): Unit           = succeed(value)
            def failure(cause: Throwable): Unit   = fail(cause)
            def pending(value: Pollable[A]): Unit = ()
          })
        catch { case cause: Throwable => fail(Failure.unwindCause(cause)) }
    }
    lazy val wake: Runnable = new Runnable {
      def run(): Unit =
        try ec.execute(step)
        catch { case cause: Throwable => fail(Failure.unwindCause(cause)) }
    }
    wake.run()
  }

  /**
   * A failed `Future`, with `Failure.NullCauseMarker` in place of a `null`
   * cause, which `fromFuture` turns back into `null`.
   */
  private def failFuture[A](cause: Throwable): Future[A] =
    if (cause eq null) Future.failed(Failure.NullCauseMarker)
    else Future.failed(cause)

  /** Fails `p`, with `Failure.NullCauseMarker` in place of a `null` cause. */
  private def failPromise[A](p: Promise[A], cause: Throwable): Unit = {
    if (cause eq null) p.failure(Failure.NullCauseMarker)
    else p.failure(cause)
    ()
  }

  /**
   * Unwraps one level of [[java.util.concurrent.CompletionException]] or
   * [[java.util.concurrent.ExecutionException]] that has a cause, so the
   * `Async` fails with what the producer threw. A stage that fails with its own
   * `ExecutionException` is unwrapped as well when it completes after the
   * conversion, but not when it has already failed (`cf.get()` wraps it again),
   * a known bug.
   */
  private def unwrapCompletionException(t: Throwable): Throwable = {
    val raw = t match {
      case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
      case ee: java.util.concurrent.ExecutionException if ee.getCause ne null  => ee.getCause
      case other                                                               => other
    }
    Failure.unwindCause(raw)
  }
}
