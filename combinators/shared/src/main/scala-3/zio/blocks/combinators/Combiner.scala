package zio.blocks.combinators

import scala.compiletime.{constValue, erasedValue}

/**
 * Combines two values into a flattened tuple with bidirectional support.
 *
 * The `Combiner` typeclass enables combining values `L` and `R` into an output
 * type `Out`, with the ability to separate them back. It handles:
 *   - Unit identity: `combine((), a)` returns `a`
 *   - EmptyTuple identity: `combine(EmptyTuple, a)` returns `a`
 *   - Tuple flattening: `combine((a, b), c)` returns `(a, b, c)`
 *
 * Scala 2 limitation: Maximum tuple arity is 22. Scala 3 has no arity limits.
 *
 * @tparam L
 *   The left input type
 * @tparam R
 *   The right input type
 *
 * @example
 *   {{{
 * val combined: (Int, String, Boolean) = Combiner.combine((1, "a"), true)
 * val (tuple, bool) = Combiner.separate(combined)
 *   }}}
 */
sealed trait Combiner[L, R] {
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

  /**
   * Separates a combined value back into its constituent parts.
   *
   * @param out
   *   The combined value
   * @return
   *   A tuple of the original left and right values
   */
  def separate(out: Out): (L, R)
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
          TupleTupleCombiner[l, r](constValue[Tuple.Size[l & Tuple]]).asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
        else inline if constValue[IsTuple[l]] then
          TupleValueCombiner[l, r](constValue[Tuple.Size[l & Tuple]]).asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
        else FallbackCombiner[L, R]().asInstanceOf[WithOut[L, R, CombineResult[L, R]]]
    }

  private[combinators] class LeftUnitCombiner[R] extends Combiner[Unit, R] {
    type Out = R
    def combine(l: Unit, r: R): R   = r
    def separate(out: R): (Unit, R) = ((), out)
  }

  private[combinators] class RightUnitCombiner[L] extends Combiner[L, Unit] {
    type Out = L
    def combine(l: L, r: Unit): L   = l
    def separate(out: L): (L, Unit) = (out, ())
  }

  private[combinators] class LeftEmptyTupleCombiner[R] extends Combiner[EmptyTuple, R] {
    type Out = R
    def combine(l: EmptyTuple, r: R): R   = r
    def separate(out: R): (EmptyTuple, R) = (EmptyTuple, out)
  }

  private[combinators] class RightEmptyTupleCombiner[L] extends Combiner[L, EmptyTuple] {
    type Out = L
    def combine(l: L, r: EmptyTuple): L   = l
    def separate(out: L): (L, EmptyTuple) = (out, EmptyTuple)
  }

  private[combinators] class TupleTupleCombiner[L, R](sizeL: Int) extends Combiner[L, R] {
    type Out = Tuple.Concat[L & Tuple, R & Tuple]
    def combine(left: L, right: R): Tuple.Concat[L & Tuple, R & Tuple] =
      (left.asInstanceOf[Tuple] ++ right.asInstanceOf[Tuple]).asInstanceOf[Tuple.Concat[L & Tuple, R & Tuple]]
    def separate(out: Tuple.Concat[L & Tuple, R & Tuple]): (L, R) = {
      val (prefix, suffix) = out.splitAt(sizeL)
      (prefix.asInstanceOf[L], suffix.asInstanceOf[R])
    }
  }

  private[combinators] class TupleValueCombiner[L, R](sizeL: Int) extends Combiner[L, R] {
    type Out = Tuple.Concat[L & Tuple, Tuple1[R]]
    def combine(left: L, right: R): Tuple.Concat[L & Tuple, Tuple1[R]] =
      (left.asInstanceOf[Tuple] ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L & Tuple, Tuple1[R]]]
    def separate(out: Tuple.Concat[L & Tuple, Tuple1[R]]): (L, R) = {
      val (prefix, suffix) = out.splitAt(sizeL)
      (prefix.asInstanceOf[L], suffix.asInstanceOf[Tuple1[R]].head)
    }
  }

  private[combinators] class FallbackCombiner[L, R] extends Combiner[L, R] {
    type Out = (L, R)
    def combine(l: L, r: R): (L, R)   = (l, r)
    def separate(out: (L, R)): (L, R) = out
  }

  private[combinators] type IsTuple[T] <: Boolean = T match {
    case Tuple => true
    case _     => false
  }
}
