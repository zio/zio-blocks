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

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

/**
 * Scala 2 / Scala.js egress syntax for [[Async]]: `fa.toFuture` /
 * `fa.toJsPromise`. Mixed into the `async` package object so
 * `import zio.blocks.async._` exposes them. Bodies forward to the internal
 * `AsyncInterop` implementation.
 */
private[async] trait AsyncSyntaxPlatformSpecific {

  /**
   * Adds `toFuture` to [[Async]], with the platform's other conversion:
   * `toCompletableFuture` on the JVM, `toJsPromise` on Scala.js.
   */
  implicit class AsyncInteropOps[A](private val fa: Async[A]) {

    /**
     * Converts this `Async` into a [[scala.concurrent.Future]] that completes
     * with the same value or failure (for a `null` cause, an internal marker
     * that `Async.fromFuture` turns back into `null`). A ready value or failure
     * gives a completed future. For a pending one the call returns at once and
     * the `Async` keeps running without blocking a thread: on the JVM it runs
     * on `ForkJoinPool.commonPool()` and `ec` runs only the short steps that
     * check it and complete the future; on Scala.js its first step runs in the
     * caller and the later ones run on `ec` (after a long run of ready steps,
     * on a `setTimeout` macrotask).
     *
     * Hand each pending `Async` to one consumer. On the JVM, driving it from
     * two, such as `toFuture` and `block`, can hang forever, a known bug. Call
     * `start` once and share the `Async.Running` instead.
     *
     * {{{
     * import scala.concurrent.{ExecutionContext, Future}
     *
     * implicit val ec: ExecutionContext = ExecutionContext.global
     * val price              = new Completer[Int]
     * val quote: Future[Int] = price.peek.map(_ + 5).toFuture
     * quote.isCompleted // false
     * price.succeed(120)
     * quote.foreach(println) // prints 125 once the future completes; a JVM main must wait for it
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
     */
    def toFuture(implicit ec: ExecutionContext): Future[A] = AsyncInterop.toFuture(fa)

    /**
     * Converts this `Async` into a [[scala.scalajs.js.Promise]] that resolves
     * with its value or rejects with its failure's `Throwable` (for a `null`
     * cause, an internal marker that `Async.fromJsPromise` turns back into
     * `null`). It needs no `ExecutionContext`: a pending `Async` takes its
     * first step in the caller and the later ones on `JSExecutionContext.queue`
     * (after a long run of ready steps, on a `setTimeout` macrotask). Hand each
     * pending `Async` to one consumer, as with `toFuture`.
     *
     * {{{
     * // no-check: Scala.js only
     * import scala.scalajs.js
     *
     * val port: js.Promise[Int] = Async.succeed(8080).toJsPromise
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
     */
    def toJsPromise: js.Promise[A] = AsyncInterop.toJsPromise(fa)
  }
}
