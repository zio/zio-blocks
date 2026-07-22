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

package zio.blocks.async.internal

import zio.blocks.async.*

import scala.quoted.*

/**
 * Direct-style entry points for the cells whose only backend is dotty-cps-async
 * (every Scala 3 JVM build, and Scala 3 < 3.8 on JS): both macros delegate
 * straight to [[AsyncDcaTransform]]. The Scala 3.8+ JS cell has its own
 * [[AsyncDirect]] (under `js/src/main/scala-3.8`) that prefers the native
 * `js.async`/`js.await` primitives and falls back to the same transform.
 */
private[async] object AsyncDirect {

  def awaitImpl[A: Type](self: Expr[Async[A]])(using Quotes): Expr[A] =
    AsyncDcaTransform.awaitImpl(self)

  def asyncImpl[A: Type](body: Expr[A])(using Quotes): Expr[Async[A]] =
    AsyncDcaTransform.asyncImpl(body)
}
