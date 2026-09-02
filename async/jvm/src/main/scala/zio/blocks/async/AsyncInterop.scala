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
 * JVM-only interop between [[Async]] and the standard async types: Scala's
 * [[scala.concurrent.Future]] and Java's [[CompletionStage]] /
 * [[CompletableFuture]].
 *
 * Both directions preserve success and failure: a future that succeeds becomes
 * an [[Async]] that succeeds with the same value, and a future that fails
 * becomes one that fails with the same error (and vice versa). Scala.js offers
 * the analogous `Future` conversions plus `js.Promise` interop in place of
 * `CompletionStage` / `CompletableFuture`.
 *
 * Internal implementation: the public surface is [[Async.fromFuture]] /
 * [[Async.fromCompletionStage]] on the companion and the `fa.toFuture` /
 * `fa.toCompletableFuture` extension methods (see
 * `AsyncSyntaxPlatformSpecific`), which forward here.
 */
private[async] object AsyncInterop {

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `future`. No `ExecutionContext` is required.
   */
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

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `stage`.
   */
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

  /**
   * Convert `fa` into a [[scala.concurrent.Future]] that completes with the
   * same value or error. If `fa` is not yet complete, it is driven on `ec`, so
   * this call returns immediately and the future completes when `fa` does.
   */
  def toFuture[A](fa: Async[A])(implicit ec: ExecutionContext): Future[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failFuture(any.asInstanceOf[Failure].cause)
    // `requiresDriver` also routes a depth-1 pollable-as-value carrier through
    // the executor: delivering it drives the user pollable for effects, which
    // may suspend — the caller must not be parked for that.
    else if (AsyncEncoding.requiresDriver(any)) {
      val p = Promise[A]()
      ec.execute(new Runnable {
        def run(): Unit =
          try p.success(Async.slowPath.block[A](fa))
          catch { case t: Throwable => failPromise(p, Failure.unwindCause(t)) }
      })
      p.future
    } else
      try Future.successful(Async.slowPath.block[A](fa))
      catch { case t: Throwable => failFuture(Failure.unwindCause(t)) }
  }

  /**
   * Convert `fa` into a [[CompletableFuture]] that completes with the same
   * value or error, for consumers in Java-shaped APIs. Behaves like
   * [[toFuture]]: a not-yet-complete `fa` is driven on `ec` and the returned
   * future completes when `fa` does.
   */
  def toCompletableFuture[A](fa: Async[A])(implicit ec: ExecutionContext): CompletableFuture[A] = {
    val any = fa.asInstanceOf[Any]
    val cf  = new CompletableFuture[A]()
    if (any.isInstanceOf[Failure]) {
      completeCfExceptionally(cf, any.asInstanceOf[Failure].cause)
      cf
    } else if (AsyncEncoding.requiresDriver(any)) { // see toFuture: includes depth-1 carriers
      ec.execute(new Runnable {
        def run(): Unit =
          try { cf.complete(Async.slowPath.block[A](fa)); () }
          catch {
            case t: Throwable => completeCfExceptionally(cf, t); ()
          }
      })
      cf
    } else
      try { cf.complete(Async.slowPath.block[A](fa)); cf }
      catch {
        case t: Throwable => completeCfExceptionally(cf, t); cf
      }
  }

  /**
   * `CompletableFuture` wraps the original cause in a
   * [[java.util.concurrent.CompletionException]] when chaining. Unwrap one
   * level so the failure exposed via [[Async.fail]] matches what the producer
   * threw, rather than a generic completion-exception wrapper.
   */
  private def unwrapCompletionException(t: Throwable): Throwable = {
    val raw = t match {
      case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
      case ee: java.util.concurrent.ExecutionException if ee.getCause ne null  => ee.getCause
      case other                                                               => other
    }
    Failure.unwindCause(raw)
  }

  /** `CompletableFuture` rejects `null` exceptional completions on the JVM. */
  private def completeCfExceptionally[A](cf: CompletableFuture[A], t: Throwable): Unit = {
    val cause = Failure.unwindCause(t)
    if (cause eq null) cf.completeExceptionally(Failure.NullCauseMarker)
    else cf.completeExceptionally(cause)
  }

  /** Scala `Future.failed` rejects a `null` exception on the JVM. */
  private def failFuture[A](cause: Throwable): Future[A] =
    if (cause eq null) Future.failed(Failure.NullCauseMarker)
    else Future.failed(cause)

  /** `Promise.failure` likewise rejects a `null` exception. */
  private def failPromise[A](p: Promise[A], cause: Throwable): Unit = {
    if (cause eq null) p.failure(Failure.NullCauseMarker)
    else p.failure(cause)
    ()
  }
}
