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
import scala.NamedTuple
import zio.blocks.endpoint.AuthType
import zio.blocks.endpoint.Endpoint
import zio.blocks.endpoint.PathCodec

/**
 * Runtime type-class: nest a constant path prefix into every Endpoint leaf of a
 * group. Internal — not part of public API.
 *
 * @tparam NT
 *   the group shape — an `Endpoint` leaf, a `NamedTuple`/tuple of leaves and
 *   nested groups, or `EmptyTuple`
 */
private[endpoint] trait NestPrefix[NT] {

  /**
   * Nest a constant path prefix into every Endpoint leaf of the group.
   *
   * @param nt
   *   the group value — an `Endpoint` leaf, a `NamedTuple`/tuple of leaves and
   *   nested groups, or `EmptyTuple`
   * @param prefix
   *   the constant path prefix to prepend (e.g. `"api"`)
   * @return
   *   a new group value with the prefix composed into every leaf
   */
  def nest(nt: NT, prefix: String): NT
}
private[endpoint] object NestPrefix {
  // Endpoint leaf: route.nest(PathCodec.literal(prefix)) delegates to
  // RoutePattern.nest which uses PathCodec.combineUnrefined; PathVars are combined
  // via PathCodec.Concat as (left.PathVars, right.PathVars) where a literal prefix
  // has PathVars = EmptyTuple so the child's PathVars are preserved positionally.
  given endpointNest[PathInput, Input, Err, Output, Auth <: AuthType]
    : NestPrefix[Endpoint[PathInput, Input, Err, Output, Auth]] with {
    def nest(
      ep: Endpoint[PathInput, Input, Err, Output, Auth],
      prefix: String
    ): Endpoint[PathInput, Input, Err, Output, Auth] =
      ep.copy(route = ep.route.nest(PathCodec.literal(prefix)))
  }
  given emptyNest: NestPrefix[EmptyTuple] with {
    def nest(t: EmptyTuple, prefix: String): EmptyTuple = t
  }
  // Tuple cons: recurse head + tail (element can be Endpoint or a nested NamedTuple)
  given consNest[H, T <: Tuple](using nh: NestPrefix[H], nt: NestPrefix[T]): NestPrefix[H *: T] with {
    def nest(t: H *: T, prefix: String): H *: T =
      nh.nest(t.head, prefix) *: nt.nest(t.tail, prefix)
  }
  // NamedTuple: the runtime value IS the values-tuple; recurse into it and re-wrap preserving names
  given namedTupleNest[N <: Tuple, V <: Tuple](using nv: NestPrefix[V]): NestPrefix[NamedTuple.NamedTuple[N, V]] with {
    def nest(nt: NamedTuple.NamedTuple[N, V], prefix: String): NamedTuple.NamedTuple[N, V] =
      nv.nest(nt.asInstanceOf[V], prefix).asInstanceOf[NamedTuple.NamedTuple[N, V]]
  }
}
