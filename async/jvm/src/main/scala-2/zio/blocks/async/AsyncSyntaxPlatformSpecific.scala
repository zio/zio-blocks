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

import java.util.concurrent.CompletableFuture

import scala.concurrent.{ExecutionContext, Future}

/**
 * Scala 2 / JVM egress syntax for [[Async]]: `fa.toFuture` /
 * `fa.toCompletableFuture`. Mixed into the `async` package object so
 * `import zio.blocks.async._` exposes them. Bodies forward to the internal
 * `AsyncInterop` implementation.
 */
private[async] trait AsyncSyntaxPlatformSpecific {

  implicit class AsyncInteropOps[A](private val fa: Async[A]) {

    /**
     * Convert `fa` into a [[scala.concurrent.Future]] that completes with the
     * same value or error. A not-yet-complete `fa` is driven on `ec`, so this
     * returns immediately and the future completes when `fa` does.
     */
    def toFuture(implicit ec: ExecutionContext): Future[A] = AsyncInterop.toFuture(fa)

    /**
     * Convert `fa` into a [[java.util.concurrent.CompletableFuture]] that
     * completes with the same value or error, for Java-shaped consumers.
     */
    def toCompletableFuture(implicit ec: ExecutionContext): CompletableFuture[A] =
      AsyncInterop.toCompletableFuture(fa)
  }
}
