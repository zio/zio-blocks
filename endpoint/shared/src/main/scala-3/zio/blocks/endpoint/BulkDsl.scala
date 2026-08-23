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

package zio.blocks.endpoint

import zio.blocks.endpoint.PathCodec

/**
 * Opt-in grouping DSL for bulk endpoint creation: `String`/`PathCodec` prefixes
 * combined with `endpoints { ... }` blocks via `/`.
 *
 * These operators live in a dedicated object (not the package scope) so they
 * never lexically shadow upstream's own `/` operators (`PathCodecOps./`,
 * `RoutePatternOps./`, ...). Import them explicitly:
 * {{{
 * import zio.blocks.endpoint.BulkDsl.*
 *
 * val api = "api" / endpoints {
 *   val users = Endpoint(Method.GET / "users")
 * }
 * val byId = PathCodec.int("id") / endpoints {
 *   val orders = Endpoint(Method.GET / "orders")
 * }
 * }}}
 */
object BulkDsl {

  extension (prefix: String) {
    transparent inline def /[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V])(using
      nv: NestPrefix[V]
    ): NamedTuple.NamedTuple[N, V] =
      nv.nest(nt.asInstanceOf[V], prefix).asInstanceOf[NamedTuple.NamedTuple[N, V]]
  }

  extension [A](codec: PathCodec[A]) {
    @scala.annotation.nowarn("msg=unused")
    transparent inline def /[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V])(using
      nv: NestPrefix[V]
    ): Any =
      ${ EndpointGroupMacro.prefixGroup('codec, 'nt) }
  }
}
