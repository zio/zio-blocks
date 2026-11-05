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

package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Combines two values into one flat tuple, and splits such a tuple back into
 * its two parts. `Tuples.combine(8080, "localhost")` is `(8080, "localhost")`,
 * and combining that with `true` appends to it instead of nesting it:
 * `(8080, "localhost", true)`. A `Unit` on either side disappears.
 *
 * Use it to build an API that collects values one at a time (path segments,
 * query parameters, zipped results) and hands the caller one flat tuple. The
 * `Tuples.Tuples` type class does the work, and its `Out` member is the result
 * type.
 *
 * The Scala versions differ when the right side is a tuple. On Scala 3 it is
 * concatenated: `(8080, "localhost")` with `(true, 3)` is
 * `(8080, "localhost", true, 3)`; `EmptyTuple` disappears like `Unit`, and the
 * result can be longer than 22 elements. On Scala 2 it stays one nested
 * element, `(8080, "localhost", (true, 3))` (a known bug); a result longer than
 * 22 elements does not compile, and neither does a `Tuple1` on the left.
 *
 * {{{
 * Tuples.combine(8080, "localhost")         // (8080,localhost)
 * Tuples.combine((8080, "localhost"), true) // (8080,localhost,true)
 * Tuples.combine((), 8080)                  // 8080
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/combinators/ Combinators]]
 */
object Tuples {

  /**
   * The type class behind `Tuples.combine`: `combine` merges an `L` and an `R`
   * into `Out`, and `separate` splits an `Out` back into `(L, R)`. The compiler
   * derives an instance for the `L` and `R` of a call, except for `Unit` with
   * `Unit` and, on Scala 2, for a `Tuple1` or a 22-element tuple on the left;
   * ask for one as an implicit parameter, as `Tuples.combine` does.
   *
   * {{{
   * def roundTrip[L, R](l: L, r: R)(implicit t: Tuples.Tuples[L, R]): (L, R) =
   *   t.separate(t.combine(l, r))
   *
   * roundTrip((8080, "localhost"), true) // ((8080,localhost),true)
   * }}}
   *
   * @tparam L
   *   the left value's type
   * @tparam R
   *   the right value's type
   */
  trait Tuples[L, R] {

    /**
     * The combined type: `(Int, String, Boolean)` for `L = (Int, String)` and
     * `R = Boolean`, `R` when `L` is `Unit`.
     */
    type Out

    /** Merges `l` and `r` into one value of type `Out`. */
    def combine(l: L, r: R): Out

    /**
     * Splits a combined value back into its two parts, so
     * `separate(combine(l, r))` is `(l, r)`. On Scala 3, when both sides are
     * tuples and either one holds a nested tuple, the part comes back flattened
     * and reading it throws a `ClassCastException` (a known bug).
     */
    def separate(out: Out): (L, R)
  }

  /**
   * Holds the `Tuples` instances; `WithOut` names an instance by its result
   * type.
   */
  object Tuples extends TuplesLowPriority1 {

    /**
     * A `Tuples` instance whose result type is `O`, for a signature that needs
     * to name the result.
     */
    type WithOut[L, R, O] = Tuples[L, R] { type Out = O }

    /**
     * Drops a `Unit` on the left: `combine((), r)` is `r`. Combining `()` with
     * `()` does not compile, because this instance and `rightUnit` both apply.
     */
    implicit def leftUnit[A]: WithOut[Unit, A, A] =
      new Tuples[Unit, A] {
        type Out = A
        def combine(l: Unit, r: A): A   = r
        def separate(out: A): (Unit, A) = ((), out)
      }

    /** Drops a `Unit` on the right: `combine(l, ())` is `l`. */
    implicit def rightUnit[A]: WithOut[A, Unit, A] =
      new Tuples[A, Unit] {
        type Out = A
        def combine(l: A, r: Unit): A   = l
        def separate(out: A): (A, Unit) = (out, ())
      }
  }

  /**
   * Holds the macro-derived instance, which has lower priority than the `Unit`
   * instances.
   */
  trait TuplesLowPriority1 {

    /**
     * Derives the instance for `L` and `R` with a macro. A tuple on the left
     * gets `R` appended as one more element; any other `L` gives the pair
     * `(L, R)`.
     */
    implicit def combineTuple[L, R]: Tuples[L, R] = macro TuplesMacros.tuplesImpl[L, R]
  }

  /**
   * Combines `l` and `r` into one flat value with the derived `Tuples.Tuples`
   * instance.
   *
   * {{{
   * Tuples.combine("localhost", 8080) // (localhost,8080)
   * }}}
   */
  def combine[L, R](l: L, r: R)(implicit c: Tuples[L, R]): c.Out = c.combine(l, r)

  /** The Scala 2 macro that derives `Tuples.Tuples` instances. */
  private[combinators] object TuplesMacros {

    private def isTuple(c: whitebox.Context)(tpe: c.universe.Type): Boolean = {
      val sym = tpe.typeSymbol
      sym.fullName.startsWith("scala.Tuple") && sym.fullName.matches("scala\\.Tuple[0-9]+")
    }

    private def tupleArity(c: whitebox.Context)(tpe: c.universe.Type): Int = {
      val name = tpe.typeSymbol.fullName
      name.stripPrefix("scala.Tuple").toInt
    }

    private def tupleElements(c: whitebox.Context)(tpe: c.universe.Type): List[c.universe.Type] =
      tpe.dealias.typeArgs

    def tuplesImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType = weakTypeOf[L].dealias
      val rType = weakTypeOf[R].dealias

      val unitType = typeOf[Unit]

      if (lType =:= unitType) {
        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $rType
            def combine(l: $lType, r: $rType): $rType = r
            def separate(out: $rType): ($lType, $rType) = ((), out)
          }
        """
      } else if (isTuple(c)(lType)) {
        val arity    = tupleArity(c)(lType)
        val elements = tupleElements(c)(lType)

        if (arity >= 22) {
          c.abort(c.enclosingPosition, s"Cannot combine Tuple$arity with another element: would exceed Tuple22 limit")
        }

        val newArity   = arity + 1
        val newTypes   = elements :+ rType
        val outType    = appliedType(symbolOf[(_, _)].owner.info.member(TypeName(s"Tuple$newArity")).asType, newTypes)
        val lAccessors = (1 to arity).map(i => q"l.${TermName(s"_$i")}")
        val allArgs    = lAccessors :+ q"r"

        val sepLAccessors = (1 to arity).map(i => q"out.${TermName(s"_$i")}")
        val sepR          = q"out.${TermName(s"_$newArity")}"

        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (..$allArgs)
            def separate(out: $outType): ($lType, $rType) = ((..$sepLAccessors), $sepR)
          }
        """
      } else {
        val outType = appliedType(typeOf[(_, _)].typeConstructor, List(lType, rType))
        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (l, r)
            def separate(out: $outType): ($lType, $rType) = out
          }
        """
      }
    }
  }
}
