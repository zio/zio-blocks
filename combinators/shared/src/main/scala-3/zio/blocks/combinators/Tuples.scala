package zio.blocks.combinators

import scala.compiletime.{constValue, erasedValue}

/**
 * Tuple operations: combining values into flat tuples and separating them.
 *
 * The `Tuples` module provides two complementary typeclasses:
 *   - `Combiner[L, R]`: Combines two values into a flattened output
 *   - `Separator[A]`: Separates a combined value back into its parts
 *
 * Key behaviors:
 *   - Unit identity: `combine((), a)` returns `a`
 *   - EmptyTuple identity: `combine(EmptyTuple, a)` returns `a`
 *   - Tuple flattening: `combine((a, b), c)` returns `(a, b, c)`
 *
 * @example
 *   {{{
 * import zio.blocks.combinators.Tuples._
 *
 * val combined: (Int, String, Boolean) = Combiner.combine((1, "a"), true)
 * val (left, right) = Separator.separate(combined)
 *   }}}
 */
object Tuples {

  /**
   * Combines two values into a single output value with tuple flattening.
   *
   * @tparam L
   *   The left input type
   * @tparam R
   *   The right input type
   */
  trait Combiner[L, R] {
    type Out

    /**
     * Combines two values into a single output value.
     *
     * @param l
     *   The left value
     * @param r
     *   The right value
     * @return
     *   The combined output
     */
    def combine(l: L, r: R): Out
  }

  /**
   * Separates a combined value back into its constituent parts.
   *
   * @tparam A
   *   The combined input type
   */
  trait Separator[A] {
    type Left
    type Right

    /**
     * Separates a combined value back into its constituent parts.
     *
     * @param a
     *   The combined value
     * @return
     *   A tuple of the original left and right values
     */
    def separate(a: A): (Left, Right)
  }

  object Combiner {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    type CombineResult[L, R] <: Any = (L, R) match {
      case (Unit, r)       => r
      case (l, Unit)       => l
      case (EmptyTuple, r) => r
      case (l, EmptyTuple) => l
      case (Tuple, Tuple)  => Tuple
      case (Tuple, Any)    => Tuple
      case _               => (L, R)
    }

    inline given combiner[L, R]: WithOut[L, R, CombineResult[L, R]] =
      inline erasedValue[(L, R)] match {
        case _: (Unit, r) =>
          LeftUnitCombiner[r]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]

        case _: (l, Unit) =>
          RightUnitCombiner[l]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]

        case _: (EmptyTuple, r) =>
          LeftEmptyTupleCombiner[r]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]

        case _: (l, EmptyTuple) =>
          RightEmptyTupleCombiner[l]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]

        case _: (l, r) =>
          inline if constValue[IsTuple[l]] && constValue[IsTuple[r]] then
            TupleTupleCombiner[l, r]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
          else inline if constValue[IsTuple[l]] then
            TupleValueCombiner[l, r]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
          else FallbackCombiner[L, R]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
      }

    private[combinators] class LeftUnitCombiner[R] extends Combiner[Unit, R] {
      type Out = R
      def combine(l: Unit, r: R): R = r
    }

    private[combinators] class RightUnitCombiner[L] extends Combiner[L, Unit] {
      type Out = L
      def combine(l: L, r: Unit): L = l
    }

    private[combinators] class LeftEmptyTupleCombiner[R] extends Combiner[EmptyTuple, R] {
      type Out = R
      def combine(l: EmptyTuple, r: R): R = r
    }

    private[combinators] class RightEmptyTupleCombiner[L] extends Combiner[L, EmptyTuple] {
      type Out = L
      def combine(l: L, r: EmptyTuple): L = l
    }

    private[combinators] class TupleTupleCombiner[L, R] extends Combiner[L, R] {
      type Out = Tuple.Concat[L & Tuple, R & Tuple]
      def combine(left: L, right: R): Tuple.Concat[L & Tuple, R & Tuple] =
        (left.asInstanceOf[Tuple] ++ right.asInstanceOf[Tuple]).asInstanceOf[Tuple.Concat[L & Tuple, R & Tuple]]
    }

    private[combinators] class TupleValueCombiner[L, R] extends Combiner[L, R] {
      type Out = Tuple.Concat[L & Tuple, Tuple1[R]]
      def combine(left: L, right: R): Tuple.Concat[L & Tuple, Tuple1[R]] =
        (left.asInstanceOf[Tuple] ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L & Tuple, Tuple1[R]]]
    }

    private[combinators] class FallbackCombiner[L, R] extends Combiner[L, R] {
      type Out = (L, R)
      def combine(l: L, r: R): (L, R) = (l, r)
    }

    private[combinators] type IsTuple[T] <: Boolean = T match {
      case Tuple => true
      case _     => false
    }
  }

  object Separator {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    inline given separator[L, R](using c: Combiner.WithOut[L, R, ?]): WithTypes[c.Out, L, R] =
      inline erasedValue[(L, R)] match {
        case _: (Unit, r) =>
          LeftUnitSeparator[r]().asInstanceOf[WithTypes[c.Out, L, R]]

        case _: (l, Unit) =>
          RightUnitSeparator[l]().asInstanceOf[WithTypes[c.Out, L, R]]

        case _: (EmptyTuple, r) =>
          LeftEmptyTupleSeparator[r]().asInstanceOf[WithTypes[c.Out, L, R]]

        case _: (l, EmptyTuple) =>
          RightEmptyTupleSeparator[l]().asInstanceOf[WithTypes[c.Out, L, R]]

        case _: (l, r) =>
          inline if constValue[Combiner.IsTuple[l]] && constValue[Combiner.IsTuple[r]] then
            TupleTupleSeparator[l, r](constValue[Tuple.Size[l & Tuple]]).asInstanceOf[WithTypes[c.Out, L, R]]
          else inline if constValue[Combiner.IsTuple[l]] then
            TupleValueSeparator[l, r](constValue[Tuple.Size[l & Tuple]]).asInstanceOf[WithTypes[c.Out, L, R]]
          else FallbackSeparator[L, R]().asInstanceOf[WithTypes[c.Out, L, R]]
      }

    private[combinators] class LeftUnitSeparator[R] extends Separator[R] {
      type Left  = Unit
      type Right = R
      def separate(a: R): (Unit, R) = ((), a)
    }

    private[combinators] class RightUnitSeparator[L] extends Separator[L] {
      type Left  = L
      type Right = Unit
      def separate(a: L): (L, Unit) = (a, ())
    }

    private[combinators] class LeftEmptyTupleSeparator[R] extends Separator[R] {
      type Left  = EmptyTuple
      type Right = R
      def separate(a: R): (EmptyTuple, R) = (EmptyTuple, a)
    }

    private[combinators] class RightEmptyTupleSeparator[L] extends Separator[L] {
      type Left  = L
      type Right = EmptyTuple
      def separate(a: L): (L, EmptyTuple) = (a, EmptyTuple)
    }

    private[combinators] class TupleTupleSeparator[L, R](sizeL: Int)
        extends Separator[Tuple.Concat[L & Tuple, R & Tuple]] {
      type Left  = L
      type Right = R
      def separate(a: Tuple.Concat[L & Tuple, R & Tuple]): (L, R) = {
        val (prefix, suffix) = a.splitAt(sizeL)
        (prefix.asInstanceOf[L], suffix.asInstanceOf[R])
      }
    }

    private[combinators] class TupleValueSeparator[L, R](sizeL: Int)
        extends Separator[Tuple.Concat[L & Tuple, Tuple1[R]]] {
      type Left  = L
      type Right = R
      def separate(a: Tuple.Concat[L & Tuple, Tuple1[R]]): (L, R) = {
        val (prefix, suffix) = a.splitAt(sizeL)
        (prefix.asInstanceOf[L], suffix.asInstanceOf[Tuple1[R]].head)
      }
    }

    private[combinators] class FallbackSeparator[L, R] extends Separator[(L, R)] {
      type Left  = L
      type Right = R
      def separate(a: (L, R)): (L, R) = a
    }
  }
}
