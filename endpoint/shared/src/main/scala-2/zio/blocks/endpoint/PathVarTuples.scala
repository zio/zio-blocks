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

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * NEW, small, endpoint-scoped combinator that concatenates two `PathVars` encodings in the SAME
 * left-to-right order/element-count that `zio.blocks.combinators.Tuples.Tuples.WithOut[A,B,C]`
 * uses to combine the real runtime value type - deliberately a SEPARATE, parallel implementation
 * living in the `endpoint` module, NOT a modification of (or addition to) that shared, generic
 * typeclass. Mirrors the style of `zio.blocks.combinators.Tuples.TuplesMacros` (a Scala 2.13
 * whitebox macro computing a `TupleN` shape from its inputs' shapes).
 */
object PathVarTuples {

  /**
   * Best-effort, non-computing stand-in used ONLY inside `SegmentCodec.Combined`'s abstract
   * class-body `PathVars` declaration. Scala 2.13 has no match types, so there is no way to do
   * real conditional type-level computation there without a term-level witness - and, because
   * `Combined`'s `left`/`right` fields are typed as the plain, unrefined `SegmentCodec[A]`/
   * `SegmentCodec[B]` (not the richer `WithBoundaries[..]{type PathVars=..}` refinement), no
   * expression written here can ever be observed as more precise than this placeholder anyway.
   * The REAL, precisely-computed, flat, ordered concatenation is produced by [[Combine]] below
   * and consumed by the `~` extension method's implicit parameter - which IS externally
   * observable on a `~`-composed value and is what the acceptance tests assert against.
   */
  type Concat[L, R] = (L, R)

  /** Term-level typeclass computing the REAL ordered, flat concatenation of two `PathVars`
    * encodings - `Unit` (`NoPathVars`) is the identity element on either side; two `TupleN`/
    * `TupleM` shapes concatenate into a flat `Tuple(N+M)`, mirroring how the real runtime value
    * type is grown by `Tuples.TuplesMacros.tuplesImpl` (Tuple arity extension), so index `i` in
    * the resulting `PathVars` always lines up with index `i` in the real combined value tuple.
    */
  trait Combine[L, R] {
    type Out
  }

  object Combine {
    type WithOut[L, R, O] = Combine[L, R] { type Out = O }

    implicit def concat[L, R]: Combine[L, R] = macro PathVarTuplesMacros.concatImpl[L, R]
  }

  object PathVarTuplesMacros {
    def concatImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType    = weakTypeOf[L].dealias
      val rType    = weakTypeOf[R].dealias
      val unitType = typeOf[Unit]

      def isTuple(tpe: Type): Boolean = {
        val sym = tpe.typeSymbol
        sym.fullName.startsWith("scala.Tuple") && sym.fullName.matches("scala\\.Tuple[0-9]+")
      }

      def tupleElements(tpe: Type): List[Type] = tpe.dealias.typeArgs

      val outType =
        if (lType =:= unitType) rType
        else if (rType =:= unitType) lType
        else if (isTuple(lType) && isTuple(rType)) {
          val combined = tupleElements(lType) ++ tupleElements(rType)
          val newArity = combined.length

          if (newArity > 22) {
            c.abort(
              c.enclosingPosition,
              s"Cannot concatenate PathVars: combined arity $newArity exceeds Tuple22 limit"
            )
          }

          appliedType(symbolOf[(_, _)].owner.info.member(TypeName(s"Tuple$newArity")).asType, combined)
        } else {
          c.abort(
            c.enclosingPosition,
            s"Cannot concatenate PathVars of types $lType and $rType: expected Unit (NoPathVars) or a TupleN shape"
          )
        }

      q"""
        new _root_.zio.blocks.endpoint.PathVarTuples.Combine[$lType, $rType] {
          type Out = $outType
        }
      """
    }
  }
}
