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

/**
 * Defines a group of HTTP endpoints in a single block and returns them as a
 * statically-typed [[scala.NamedTuple]].
 *
 * Each statement in the block must be either:
 *   - `val name = Endpoint(...)` — exposed as member `.name`
 *   - a bare `Endpoint(...)` expression — auto-named from its route render,
 *     e.g. `` .`GET /user/{userId}` `` (RFC 6570 `{var}` style, method prefix,
 *     multi-method rendered as `GET#|POST`)
 *
 * Constant-prefix nesting (`"api" / endpoints { ... }`) composes prefixes into
 * every child route at the description level. Capturing prefixes
 * (`PathCodec.int("id") / endpoints { ... }`) additionally widen each child's
 * static `PathInput`.
 *
 * @param body
 *   the block of endpoint declarations; every statement must be an
 *   `Endpoint(...)` construction, optionally bound to a `val`
 * @return
 *   a `NamedTuple[Names, Values]` pairing each declared name with its
 *   fully-typed `Endpoint`
 * @note
 *   Scala 3.7+ only (named tuples); prefix grouping (`"api" / endpoints { ...
 *   }` or `PathCodec.int("id") / endpoints { ... }`) is available via
 *   `import zio.blocks.endpoint.*` (no extra import required).
 */
transparent inline def endpoints(inline body: Any): Any =
  ${ EndpointGroupMacro.build('body) }

extension [A, PV](codec: PathCodec[A] { type PathVars = PV }) {

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

  /**
   * Delegate for ordinary `PathCodec` composition. When
   * `import zio.blocks.endpoint.*` brings the grouping `/` into lexical scope,
   * it would otherwise shadow `PathCodec.PathCodecOps./` and break
   * `PathCodec.int("a") / PathCodec.int("b")`. Providing the same operator here
   * preserves that composition byte-for-byte.
   */
  def concat[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: zio.blocks.combinators.Tuples.Tuples.WithOut[A, B, C],
    _pathVarsCombiner: PathCodec.PathVarsCombiner[PV, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } = {
    val _ = _pathVarsCombiner
    PathCodec.combineUnrefined(codec, that)(combiner).asInstanceOf[PathCodec[C] { type PathVars = PVC }]
  }

  /** Symbolic alias for [[concat]] — `codec / codec`. */
  def /[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: zio.blocks.combinators.Tuples.Tuples.WithOut[A, B, C],
    _pathVarsCombiner: PathCodec.PathVarsCombiner[PV, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } =
    concat(that)(combiner, _pathVarsCombiner)
}
