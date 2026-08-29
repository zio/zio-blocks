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
import scala.language.implicitConversions

/**
 * Opt-in grouping DSL for bulk endpoint creation: `PathCodec` prefixes combined
 * with `endpoints { ... }` blocks via `/`.
 *
 * String prefixes work via an implicit `Conversion[String, PathCodec[Unit]]`
 * provided here, so `"api" / endpoints { ... }` resolves through the
 * `PathCodec` extension (no String-specific operator). Import the DSL
 * explicitly:
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
 *
 * Prefix grouping requires an inline `endpoints { ... }` block (`val g =
 * endpoints { ... }; "api" / g` is not supported — the macro must see the block
 * literal). Nested `"v1" / endpoints { ... }` statements inside a block are
 * lifted to named members automatically.
 */
object BulkDsl {

  /**
   * Auto-convert a constant String prefix to a literal `PathCodec[Unit]` so
   * that `"api" / endpoints { ... }` goes through the `PathCodec` `/`
   * extension. Available when `import zio.blocks.endpoint.BulkDsl.*` is in
   * scope.
   */
  given Conversion[String, PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars }] with {
    def apply(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
      PathCodec.literal(value)
  }

  extension [A](codec: PathCodec[A]) {

    /**
     * Prefix a bulk group with a capturing path codec — named alias for `/`.
     *
     * @param nt
     *   the `endpoints { ... }` group
     * @return
     *   the same group with `codec`'s segment prepended to every leaf; static
     *   `PathInput` is widened positionally (e.g. `Int` prefix + `Int` child
     *   yields `(Int, Int)`)
     */
    transparent inline def nest[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V]): Any =
      ${ EndpointGroupMacro.prefixGroupCodec('codec, 'nt) }

    /** Symbolic alias for [[nest]] — `codec / endpoints { ... }`. */
    transparent inline def /[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V]): Any =
      nest(nt)
  }
  // Mirrors of upstream's implicit-scope `/` operators. Importing BulkDsl.*
  // brings a lexical `/` into scope, which would otherwise shadow those
  // implicits and break ordinary `Method.GET / path` chains inside
  // DSL-using files; these delegates preserve them byte-for-byte.
  extension (method: zio.http.Method) {

    /**
     * Named alias for `method / path` — creates a [[RoutePattern]] from a
     * method and a path codec.
     */
    def toRoute[A, PV](path: PathCodec[A] { type PathVars = PV }): RoutePattern[A] { type PathVars = PV } =
      RoutePattern(method, path).asInstanceOf[RoutePattern[A] { type PathVars = PV }]

    /** Symbolic alias for [[toRoute]] — `method / path`. */
    def /[A, PV](path: PathCodec[A] { type PathVars = PV }): RoutePattern[A] { type PathVars = PV } =
      toRoute(path)
  }
  extension [A, PV](self: RoutePattern[A] { type PathVars = PV }) {

    /**
     * Named alias for `route / path` — appends a path codec, combining value
     * and `PathVars` tracks.
     */
    def concat[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
      combiner: zio.blocks.combinators.Tuples.Tuples.WithOut[A, B, C],
      _pathVarsCombiner: PathCodec.RoutePathVarsCombiner[PV, PV2, PVC]
    ): RoutePattern[C] { type PathVars = PVC } = {
      val _ = _pathVarsCombiner
      self
        .copy(pathCodec = PathCodec.combineUnrefined(self.pathCodec, that)(combiner))
        .asInstanceOf[RoutePattern[C] { type PathVars = PVC }]
    }

    /** Symbolic alias for [[concat]] — `route / path`. */
    def /[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
      combiner: zio.blocks.combinators.Tuples.Tuples.WithOut[A, B, C],
      _pathVarsCombiner: PathCodec.RoutePathVarsCombiner[PV, PV2, PVC]
    ): RoutePattern[C] { type PathVars = PVC } = {
      val _ = _pathVarsCombiner
      concat(that)(combiner, _pathVarsCombiner)
    }
  }
}
