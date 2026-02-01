package zio.blocks.combinators

import scala.compiletime.{constValue, erasedValue}

/**
 * Zips two values into a flattened tuple, potentially discarding Unit or
 * EmptyTuple values.
 *
 * The `Zippable` typeclass is similar to `Combiner` but is unidirectional (no
 * separation). It provides flags to indicate when values are discarded:
 *   - Unit identity: `zip((), a)` returns `a` with `discardsLeft = true`
 *   - EmptyTuple identity: `zip(EmptyTuple, a)` returns `a` with
 *     `discardsLeft = true`
 *   - Tuple flattening: `zip((a, b), c)` returns `(a, b, c)`
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
 * val result: (Int, String) = Zippable.zip(1, "hello")
 * val discarded: String = Zippable.zip((), "hello") // discardsLeft = true
 *   }}}
 */
sealed trait Zippable[L, R] {
  type Out

  /**
   * Zips two values into a single output value.
   *
   * @param left
   *   The left value
   * @param right
   *   The right value
   * @return
   *   The zipped output
   */
  def zip(left: L, right: R): Out

  /**
   * Indicates whether the left value is discarded (e.g., Unit or EmptyTuple).
   */
  def discardsLeft: Boolean = false

  /**
   * Indicates whether the right value is discarded (e.g., Unit or EmptyTuple).
   */
  def discardsRight: Boolean = false
}

object Zippable {

  /**
   * Type alias for a Zippable with a specific output type.
   */
  type WithOut[L, R, O] = Zippable[L, R] { type Out = O }

  type ZipResult[L, R] <: Any = (L, R) match {
    case (Unit, r)       => r
    case (l, Unit)       => l
    case (EmptyTuple, r) => r
    case (l, EmptyTuple) => l
    case (Tuple, Tuple)  => Tuple
    case (Tuple, Any)    => Tuple
    case _               => (L, R)
  }

  inline given zippable[L, R]: WithOut[L, R, ZipResult[L, R]] =
    inline erasedValue[(L, R)] match {
      case _: (Unit, r) =>
        LeftUnitZippable[r]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]

      case _: (l, Unit) =>
        RightUnitZippable[l]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]

      case _: (EmptyTuple, r) =>
        LeftEmptyTupleZippable[r]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]

      case _: (l, EmptyTuple) =>
        RightEmptyTupleZippable[l]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]

      case _: (l, r) =>
        inline if constValue[IsTuple[l]] && constValue[IsTuple[r]] then
          TupleTupleZippable[l, r]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]
        else inline if constValue[IsTuple[l]] then
          TupleValueZippable[l, r]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]
        else FallbackZippable[L, R]().asInstanceOf[WithOut[L, R, ZipResult[L, R]]]
    }

  private[combinators] class LeftUnitZippable[R] extends Zippable[Unit, R] {
    type Out = R
    override val discardsLeft: Boolean = true
    def zip(left: Unit, right: R): R   = right
  }

  private[combinators] class RightUnitZippable[L] extends Zippable[L, Unit] {
    type Out = L
    override val discardsRight: Boolean = true
    def zip(left: L, right: Unit): L    = left
  }

  private[combinators] class LeftEmptyTupleZippable[R] extends Zippable[EmptyTuple, R] {
    type Out = R
    override val discardsLeft: Boolean     = true
    def zip(left: EmptyTuple, right: R): R = right
  }

  private[combinators] class RightEmptyTupleZippable[L] extends Zippable[L, EmptyTuple] {
    type Out = L
    override val discardsRight: Boolean    = true
    def zip(left: L, right: EmptyTuple): L = left
  }

  private[combinators] class TupleTupleZippable[L, R] extends Zippable[L, R] {
    type Out = Tuple.Concat[L & Tuple, R & Tuple]
    def zip(left: L, right: R): Tuple.Concat[L & Tuple, R & Tuple] =
      (left.asInstanceOf[Tuple] ++ right.asInstanceOf[Tuple]).asInstanceOf[Tuple.Concat[L & Tuple, R & Tuple]]
  }

  private[combinators] class TupleValueZippable[L, R] extends Zippable[L, R] {
    type Out = Tuple.Concat[L & Tuple, Tuple1[R]]
    def zip(left: L, right: R): Tuple.Concat[L & Tuple, Tuple1[R]] =
      (left.asInstanceOf[Tuple] ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L & Tuple, Tuple1[R]]]
  }

  private[combinators] class FallbackZippable[L, R] extends Zippable[L, R] {
    type Out = (L, R)
    def zip(left: L, right: R): (L, R) = (left, right)
  }

  private[combinators] type IsTuple[T] <: Boolean = T match {
    case Tuple => true
    case _     => false
  }
}
