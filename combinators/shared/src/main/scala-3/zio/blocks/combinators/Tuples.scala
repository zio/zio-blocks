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

import scala.compiletime.constValue
import scala.util.NotGiven

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
   * The tuple `T` with every nested tuple spliced in, at any depth:
   * `Flatten[((Int, String), Boolean)]` is `(Int, String, Boolean)`. A
   * non-tuple `T` becomes the one-element tuple `T *: EmptyTuple`.
   */
  type Flatten[T] <: Tuple = T match {
    case EmptyTuple => EmptyTuple
    case h *: t     =>
      h match {
        case Tuple => Tuple.Concat[Flatten[h & Tuple], Flatten[t]]
        case _     => h *: Flatten[t]
      }
    case _ => T *: EmptyTuple
  }

  /**
   * `A` and `B` flattened into one tuple, the result type of combining two
   * tuples.
   */
  type Combined[A, B] = Flatten[A *: B *: EmptyTuple]

  /** Every element of the non-empty tuple `T` but the last. */
  type Init[T <: Tuple] <: Tuple = T match {
    case x *: EmptyTuple => EmptyTuple
    case x *: xs         => x *: Init[xs]
  }

  /** The last element of the non-empty tuple `T`. */
  type Last[T <: Tuple] = T match {
    case x *: EmptyTuple => x
    case x *: xs         => Last[xs]
  }

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
  object Tuples extends TuplesLowPriority {

    /**
     * A `Tuples` instance whose result type is `O`, for a signature that needs
     * to name the result.
     */
    type WithOut[L, R, O] = Tuples[L, R] { type Out = O }

    /**
     * Drops a `Unit` on the left: `combine((), r)` is `r`. Combining `()` with
     * `()` does not compile, because this instance and `rightUnit` both apply.
     */
    given leftUnit[R]: WithOut[Unit, R, R] = new Tuples[Unit, R] {
      type Out = R
      def combine(l: Unit, r: R): R   = r
      def separate(out: R): (Unit, R) = ((), out)
    }

    /** Drops a `Unit` on the right: `combine(l, ())` is `l`. */
    given rightUnit[L]: WithOut[L, Unit, L] = new Tuples[L, Unit] {
      type Out = L
      def combine(l: L, r: Unit): L   = l
      def separate(out: L): (L, Unit) = (out, ())
    }

    /** Drops an `EmptyTuple` on the left: `combine(EmptyTuple, r)` is `r`. */
    given leftEmptyTuple[R]: WithOut[EmptyTuple, R, R] = new Tuples[EmptyTuple, R] {
      type Out = R
      def combine(l: EmptyTuple, r: R): R   = r
      def separate(out: R): (EmptyTuple, R) = (EmptyTuple, out)
    }

    /** Drops an `EmptyTuple` on the right: `combine(l, EmptyTuple)` is `l`. */
    given rightEmptyTuple[L]: WithOut[L, EmptyTuple, L] = new Tuples[L, EmptyTuple] {
      type Out = L
      def combine(l: L, r: EmptyTuple): L   = l
      def separate(out: L): (L, EmptyTuple) = (out, EmptyTuple)
    }

    /**
     * Concatenates two non-empty tuples and flattens tuples nested inside
     * either of them: `((1, 2), 3)` with `(4, 5)` is `(1, 2, 3, 4, 5)`.
     */
    inline given tupleTuple[L <: NonEmptyTuple, R <: NonEmptyTuple]: WithOut[L, R, Combined[L, R]] =
      TupleTupleInstance[L, R](constValue[Tuple.Size[Flatten[L]]])

    /**
     * Appends a non-tuple `R` to a non-empty tuple: `(8080, "localhost")` with
     * `true` is `(8080, "localhost", true)`. Tuples nested in `L` stay as they
     * are.
     */
    inline given tupleValue[L <: NonEmptyTuple, R](using
      NotGiven[R <:< Tuple]
    ): WithOut[L, R, Tuple.Concat[L, Tuple1[R]]] =
      TupleValueInstance[L, R](constValue[Tuple.Size[L]])

    /**
     * Prepends a non-tuple `L` to a non-empty tuple: `8080` with
     * `("localhost", true)` is `(8080, "localhost", true)`. Tuples nested in
     * `R` stay as they are.
     */
    given valueTuple[L, R <: NonEmptyTuple](using NotGiven[L <:< Tuple]): WithOut[L, R, Tuple.Concat[Tuple1[L], R]] =
      ValueTupleInstance[L, R]()

    private[combinators] class TupleTupleInstance[L <: NonEmptyTuple, R <: NonEmptyTuple](flatLeftSize: Int)
        extends Tuples[L, R] {
      type Out = Combined[L, R]

      def combine(left: L, right: R): Combined[L, R] =
        flattenTuple(left ++ right).asInstanceOf[Combined[L, R]]

      def separate(out: Combined[L, R]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(flatLeftSize)
        (l.asInstanceOf[L], r.asInstanceOf[R])
      }
    }

    private[combinators] class TupleValueInstance[L <: NonEmptyTuple, R](leftSize: Int) extends Tuples[L, R] {
      type Out = Tuple.Concat[L, Tuple1[R]]

      def combine(left: L, right: R): Tuple.Concat[L, Tuple1[R]] =
        (left ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L, Tuple1[R]]]

      def separate(out: Tuple.Concat[L, Tuple1[R]]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(leftSize)
        (l.asInstanceOf[L], r.asInstanceOf[Tuple1[R]].head)
      }
    }

    private[combinators] class ValueTupleInstance[L, R <: NonEmptyTuple]() extends Tuples[L, R] {
      type Out = Tuple.Concat[Tuple1[L], R]

      def combine(left: L, right: R): Tuple.Concat[Tuple1[L], R] =
        (Tuple1(left) ++ right).asInstanceOf[Tuple.Concat[Tuple1[L], R]]

      def separate(out: Tuple.Concat[Tuple1[L], R]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(1)
        (l.asInstanceOf[Tuple1[L]].head, r.asInstanceOf[R])
      }
    }
  }

  /** Holds the fallback instance, which applies when no other one does. */
  trait TuplesLowPriority {

    /** Pairs two values that are neither tuples nor `Unit`: `(l, r)`. */
    given fallback[L, R]: Tuples.WithOut[L, R, (L, R)] = new Tuples[L, R] {
      type Out = (L, R)
      def combine(l: L, r: R): (L, R)   = (l, r)
      def separate(out: (L, R)): (L, R) = out
    }
  }

  private def flattenTuple(t: Tuple): Tuple = t match {
    case EmptyTuple            => EmptyTuple
    case (head: Tuple) *: tail => flattenTuple(head) ++ flattenTuple(tail)
    case head *: tail          => head *: flattenTuple(tail)
  }

  /**
   * Combines `l` and `r` into one flat value with the derived `Tuples.Tuples`
   * instance.
   *
   * {{{
   * Tuples.combine("localhost", 8080) // (localhost,8080)
   * }}}
   */
  def combine[L, R](l: L, r: R)(using t: Tuples[L, R]): t.Out = t.combine(l, r)
}
