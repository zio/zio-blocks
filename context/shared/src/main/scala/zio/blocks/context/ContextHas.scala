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

package zio.blocks.context

import scala.annotation.implicitNotFound

/**
 * Compile-time evidence that a context of type `Ctx` provides all types in `A`.
 *
 * Resolves when `Ctx <: A`. For intersection types this means every component
 * of `A` must appear in `Ctx`:
 * {{{
 * // resolves: (Server & Database & Logger) <: (Database & Logger)
 * implicitly[ContextHas[Server & Database & Logger, Database & Logger]]
 *
 * // fails:   (Server & Database) is not <: (Database & Logger)
 * implicitly[ContextHas[Server & Database, Database & Logger]]
 * // → "Context[Server & Database] does not provide Database & Logger.
 * //    Compare the two types — each component of Database & Logger
 * //    not in Server & Database must be added via context.add(value)."
 * }}}
 *
 * Typical usage — one implicit checks everything:
 * {{{
 * def serve[ReqCtx, Ctx](routes: Routes[ReqCtx], context: Context[Ctx])(implicit
 *   ev: ContextHas[Ctx, ReqCtx & Server]
 * ): ServerHandle = ???
 * }}}
 */
@implicitNotFound(
  "Context[${Ctx}] does not provide ${A}. Compare the two types — each component of ${A} not in ${Ctx} must be added via context.add(value)."
)
trait ContextHas[Ctx, A]

object ContextHas {

  /** Automatically available when A is part of the Ctx intersection type. */
  implicit def evidence[Ctx <: A, A]: ContextHas[Ctx, A] = instance.asInstanceOf[ContextHas[Ctx, A]]

  private val instance: ContextHas[Any, Any] = new ContextHas[Any, Any] {}
}
