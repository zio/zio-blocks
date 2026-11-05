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
 * Compile-time evidence that a `Context[Ctx]` provides every type in `A`. It
 * resolves when `Ctx` is a subtype of `A`; for an intersection `A`, each of its
 * components must be in `Ctx` (or be a supertype of a type in `Ctx`).
 *
 * Use it as one implicit parameter that checks a whole set of services, where
 * `Context#get` checks one type at a time. When it does not resolve, the
 * compiler reports `Context[Ctx] does not provide A. Compare the two types —
 * each component of A not in Ctx must be added via context.add(value).`, with
 * the two types written out in full.
 *
 * {{{
 * trait Server
 * trait Database
 * trait Logger
 *
 * // resolves: (Server & Database & Logger) <: (Database & Logger)
 * implicitly[ContextHas[Server & Database & Logger, Database & Logger]]
 *
 * // does not compile: (Server & Database) is not <: (Database & Logger)
 * // implicitly[ContextHas[Server & Database, Database & Logger]]
 * }}}
 *
 * A typical use is a method that needs a context holding its own services plus
 * whatever its argument needs:
 * {{{
 * trait Server
 * trait Routes[ReqCtx]
 * trait ServerHandle
 *
 * def serve[ReqCtx, Ctx](routes: Routes[ReqCtx], context: Context[Ctx])(implicit
 *   ev: ContextHas[Ctx, ReqCtx & Server]
 * ): ServerHandle = ???
 * }}}
 *
 * @tparam Ctx
 *   the type parameter of the context, an intersection of the stored types
 * @tparam A
 *   the types the context must provide
 * @see
 *   [[https://zioblocks.com/reference/context/ Context]]
 */
@implicitNotFound(
  "Context[${Ctx}] does not provide ${A}. Compare the two types — each component of ${A} not in ${Ctx} must be added via context.add(value)."
)
trait ContextHas[Ctx, A]

/** Provides the `ContextHas` evidence the compiler resolves. */
object ContextHas {

  /**
   * Provides the evidence whenever `Ctx` is a subtype of `A`. It returns one
   * shared instance, so resolving it allocates nothing.
   */
  implicit def evidence[Ctx <: A, A]: ContextHas[Ctx, A] = instance.asInstanceOf[ContextHas[Ctx, A]]

  private val instance: ContextHas[Any, Any] = new ContextHas[Any, Any] {}
}
