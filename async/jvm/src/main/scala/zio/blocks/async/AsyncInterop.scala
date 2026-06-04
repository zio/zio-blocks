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
 * Both directions are lossless w.r.t. success and failure:
 *
 *   - Conversions FROM a future inspect for synchronous completion and collapse
 *     to a raw value or an [[Async.fail]] when possible; otherwise they
 *     construct a [[Completer]]-backed [[Async]] whose waker is fired by the
 *     future's callback.
 *   - Conversions TO a future use the [[Async]] surface — `.block` on a worker
 *     thread (Scala `Future`) or a chained completion (CompletionStage via
 *     `.flatMap`) — and propagate the eventual outcome.
 *
 * Scala.js has analogous wrappers in its own `AsyncInterop`.
 */
object AsyncInterop {

  /**
   * Construct an [[Async]] that mirrors `future`. Already-completed futures
   * collapse to a raw value or an [[Async.fail]]; otherwise the completion is
   * routed through a [[Completer]].
   *
   * No explicit `ExecutionContext` is required: the future's existing
   * `onComplete` machinery is reused with `parasitic`-style direct execution
   * inside the callback (we only flip CAS state and wake — no user work).
   */
  def fromFuture[A](future: Future[A]): Async[A] = {
    val cur = future.value
    if (cur.isDefined) cur.get match {
      case SSuccess(v) => Async.succeed(v)
      case SFailure(t) => Async.fail(t)
    }
    else
      Async.promiseInternal[A] { c =>
        future.onComplete {
          case SSuccess(v) => c.succeed(v)
          case SFailure(t) => c.fail(t)
        }(ExecutionContext.parasitic)
      }
  }

  /**
   * Construct an [[Async]] that mirrors `stage`. Already-completed stages
   * collapse to a raw value or [[Async.fail]]; otherwise the completion is
   * routed through a [[Completer]].
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
   * Convert `fa` into a [[scala.concurrent.Future]]. Already-resolved values
   * and failures collapse synchronously; suspended pollables are awaited on
   * `ec` (so this call returns immediately; the future completes when the
   * pollable does).
   */
  def toFuture[A](fa: Async[A])(implicit ec: ExecutionContext): Future[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) Future.failed(any.asInstanceOf[Failure].cause)
    else if (any.isInstanceOf[Pollable[_]]) {
      val p = Promise[A]()
      ec.execute(new Runnable {
        def run(): Unit =
          try p.success(fa.block)
          catch { case t: Throwable => p.failure(t); () }
      })
      p.future
    } else Future.successful(any.asInstanceOf[A])
  }

  /**
   * Convert `fa` into a [[CompletableFuture]]. Mirrors [[toFuture]] but uses
   * `CompletableFuture` machinery so consumers in Java-shaped APIs can chain
   * without a `Future`-to-stage conversion.
   */
  def toCompletableFuture[A](fa: Async[A])(implicit ec: ExecutionContext): CompletableFuture[A] = {
    val any = fa.asInstanceOf[Any]
    val cf  = new CompletableFuture[A]()
    if (any.isInstanceOf[Failure]) {
      cf.completeExceptionally(any.asInstanceOf[Failure].cause)
      cf
    } else if (any.isInstanceOf[Pollable[_]]) {
      ec.execute(new Runnable {
        def run(): Unit =
          try { cf.complete(fa.block); () }
          catch { case t: Throwable => cf.completeExceptionally(t); () }
      })
      cf
    } else {
      cf.complete(any.asInstanceOf[A])
      cf
    }
  }

  /**
   * `CompletableFuture` wraps the original cause in a
   * [[java.util.concurrent.CompletionException]] when chaining. Unwrap one
   * level so the failure exposed via [[Async.fail]] matches what the producer
   * threw, rather than a generic completion-exception wrapper.
   */
  private def unwrapCompletionException(t: Throwable): Throwable = t match {
    case ce: java.util.concurrent.CompletionException if ce.getCause ne null => ce.getCause
    case ee: java.util.concurrent.ExecutionException if ee.getCause ne null  => ee.getCause
    case other                                                               => other
  }
}
