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
 * NEW, small, endpoint-scoped combinator that concatenates two `PathVars` encodings in the SAME
 * left-to-right order/element-count that `zio.blocks.combinators.Tuples.Tuples.WithOut[A,B,C]`
 * uses to combine the real runtime value type - deliberately a SEPARATE, parallel implementation
 * living in the `endpoint` module, NOT a modification of (or addition to) that shared, generic
 * typeclass.
 */
private[endpoint] object PathVarTuples {

  /**
   * Ordered concatenation of `L` and `R`, structurally mirroring `scala.Tuple.Concat`'s own
   * recursive match-type definition, but deliberately left UNBOUNDED (no `<: Tuple` constraint
   * on the type parameters) so it can be applied directly to `SegmentCodec`'s unbounded
   * `PathVars` abstract type member (e.g. `left.PathVars`/`right.PathVars` inside
   * `SegmentCodec.Combined`'s class body, where `left`/`right` are typed as the plain
   * `SegmentCodec[A]`/`SegmentCodec[B]` and so cannot statically be proven `<: Tuple`).
   * Non-capturing sides (`NoPathVars` = `EmptyTuple`) contribute zero elements, matching the
   * `EmptyTuple` base case.
   */
  type Concat[L, R] = L match {
    case EmptyTuple => R
    case h *: t     => h *: Concat[t, R]
  }

  /**
   * Term-level typeclass mirror of [[Concat]], used by the `~` extension method so the composed
   * value's INFERRED `PathVars` type parameter (`PVC`, a normal method type parameter unified via
   * `given` resolution, exactly like `Tuples.Tuples.WithOut[A,B,C]` infers `C`) is the ordered
   * concatenation - this is what is actually externally observable on a `~`-composed value,
   * independent of what `SegmentCodec.Combined`'s own class body declares.
   */
  trait Combine[L, R] {
    type Out
  }

  object Combine {
    type WithOut[L, R, O] = Combine[L, R] { type Out = O }

    given instance[L, R]: WithOut[L, R, Concat[L, R]] =
      new Combine[L, R] { type Out = Concat[L, R] }
  }
}
