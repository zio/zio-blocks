package zio.blocks.combinators

import scala.compiletime.constValue
import scala.util.NotGiven

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
   * Recursively flattens nested tuples into a flat tuple.
   *
   * Examples:
   *   - `Flatten[(Int, (String, Boolean))]` = `(Int, String, Boolean)`
   *   - `Flatten[((Int, String), (Boolean, Double))]` =
   *     `(Int, String, Boolean, Double)`
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
   * The canonical combined type of A and B, fully flattened.
   */
  type Combined[A, B] = Flatten[A *: B *: EmptyTuple]

  /**
   * Returns all elements of a tuple except the last one.
   *
   * Examples: Init[(Int, String, Boolean)] = (Int, String) Init[(Int,)] =
   * EmptyTuple
   */
  type Init[T <: Tuple] <: Tuple = T match {
    case x *: EmptyTuple => EmptyTuple
    case x *: xs         => x *: Init[xs]
  }

  /**
   * Returns the last element of a tuple.
   *
   * Examples: Last[(Int, String, Boolean)] = Boolean Last[(Int,)] = Int
   */
  type Last[T <: Tuple] = T match {
    case x *: EmptyTuple => x
    case x *: xs         => Last[xs]
  }

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

  private def flattenTuple(t: Tuple): Tuple = t match {
    case EmptyTuple            => EmptyTuple
    case (head: Tuple) *: tail => flattenTuple(head) ++ flattenTuple(tail)
    case head *: tail          => head *: flattenTuple(tail)
  }

  object Combiner extends CombinerLowPriority {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    given leftUnit[R]: WithOut[Unit, R, R] = new Combiner[Unit, R] {
      type Out = R
      def combine(l: Unit, r: R): R = r
    }

    given rightUnit[L]: WithOut[L, Unit, L] = new Combiner[L, Unit] {
      type Out = L
      def combine(l: L, r: Unit): L = l
    }

    given leftEmptyTuple[R]: WithOut[EmptyTuple, R, R] = new Combiner[EmptyTuple, R] {
      type Out = R
      def combine(l: EmptyTuple, r: R): R = r
    }

    given rightEmptyTuple[L]: WithOut[L, EmptyTuple, L] = new Combiner[L, EmptyTuple] {
      type Out = L
      def combine(l: L, r: EmptyTuple): L = l
    }

    given tupleTuple[L <: NonEmptyTuple, R <: NonEmptyTuple]: WithOut[L, R, Combined[L, R]] =
      TupleTupleCombiner[L, R]()

    given tupleValue[L <: NonEmptyTuple, R](using NotGiven[R <:< Tuple]): WithOut[L, R, Tuple.Concat[L, Tuple1[R]]] =
      TupleValueCombiner[L, R]()

    given valueTuple[L, R <: NonEmptyTuple](using NotGiven[L <:< Tuple]): WithOut[L, R, Tuple.Concat[Tuple1[L], R]] =
      ValueTupleCombiner[L, R]()

    private[combinators] class TupleTupleCombiner[L <: Tuple, R <: Tuple]() extends Combiner[L, R] {
      type Out = Combined[L, R]
      def combine(left: L, right: R): Combined[L, R] =
        flattenTuple(left ++ right).asInstanceOf[Combined[L, R]]
    }

    private[combinators] class TupleValueCombiner[L <: Tuple, R]() extends Combiner[L, R] {
      type Out = Tuple.Concat[L, Tuple1[R]]
      def combine(left: L, right: R): Tuple.Concat[L, Tuple1[R]] =
        (left ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L, Tuple1[R]]]
    }

    private[combinators] class ValueTupleCombiner[L, R <: Tuple]() extends Combiner[L, R] {
      type Out = Tuple.Concat[Tuple1[L], R]
      def combine(left: L, right: R): Tuple.Concat[Tuple1[L], R] =
        (Tuple1(left) ++ right).asInstanceOf[Tuple.Concat[Tuple1[L], R]]
    }
  }

  trait CombinerLowPriority {
    given fallback[L, R]: Combiner.WithOut[L, R, (L, R)] = new Combiner[L, R] {
      type Out = (L, R)
      def combine(l: L, r: R): (L, R) = (l, r)
    }
  }

  object Separator extends SeparatorLowPriority {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    given leftUnit[R]: WithTypes[R, Unit, R] = new Separator[R] {
      type Left  = Unit
      type Right = R
      def separate(a: R): (Unit, R) = ((), a)
    }

    given rightUnit[L]: WithTypes[L, L, Unit] = new Separator[L] {
      type Left  = L
      type Right = Unit
      def separate(a: L): (L, Unit) = (a, ())
    }

    given leftEmptyTuple[R]: WithTypes[R, EmptyTuple, R] = new Separator[R] {
      type Left  = EmptyTuple
      type Right = R
      def separate(a: R): (EmptyTuple, R) = (EmptyTuple, a)
    }

    given rightEmptyTuple[L]: WithTypes[L, L, EmptyTuple] = new Separator[L] {
      type Left  = L
      type Right = EmptyTuple
      def separate(a: L): (L, EmptyTuple) = (a, EmptyTuple)
    }

    /**
     * Canonicalizing separator for non-empty tuples. Flattens the input tuple
     * first, then extracts init (all but last) and last.
     *
     * Example: `separate(((1, "a"), (true, 3.0)))` first flattens to
     * `(1, "a", true, 3.0)`, then produces `((1, "a", true), 3.0)`.
     */
    inline given canonicalSeparator[A <: NonEmptyTuple](using
      ev: Flatten[A] <:< NonEmptyTuple
    ): WithTypes[A, Init[Flatten[A]], Last[Flatten[A]]] =
      CanonicalSeparator[A, Init[Flatten[A]], Last[Flatten[A]]](
        constValue[Tuple.Size[Init[Flatten[A]]]]
      )

    private[combinators] class CanonicalSeparator[A <: Tuple, I <: Tuple, L](sizeInit: Int) extends Separator[A] {
      type Left  = I
      type Right = L
      def separate(a: A): (I, L) = {
        val flat              = flattenTuple(a)
        val (init, lastTuple) = flat.splitAt(sizeInit)
        (init.asInstanceOf[I], lastTuple.asInstanceOf[Tuple1[L]].head)
      }
    }
  }

  trait SeparatorLowPriority {
    given fallback[L, R]: Separator.WithTypes[(L, R), L, R] = new Separator[(L, R)] {
      type Left  = L
      type Right = R
      def separate(a: (L, R)): (L, R) = a
    }
  }
}
