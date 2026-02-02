package zio.blocks.combinators

import scala.compiletime.erasedValue

/**
 * Either operations: combining values into left-nested canonical form and
 * separating them.
 *
 * The `Eithers` module provides two complementary typeclasses:
 *   - `Combiner[L, R]`: Combines an Either[L, R] into a left-nested canonical
 *     form
 *   - `Separator[A]`: Separates a combined value by peeling the rightmost
 *     alternative
 *
 * Key behaviors:
 *   - Canonical form is left-nested: `Either[Either[Either[A, B], C], D]`
 *   - Combiner takes `Either[L, R]` as input to enable type inference
 *   - Separator peels the rightmost alternative, returning
 *     `Either[Left, Right]`
 *
 * @example
 *   {{{
 * import zio.blocks.combinators.Eithers._
 *
 * // Combine reassociates to left-nested form
 * val combined = Combiner.combine(Right(Right(true)): Either[Int, Either[String, Boolean]])
 * // Result: Right(true): Either[Either[Int, String], Boolean]
 *
 * // Separate peels the rightmost alternative
 * val separated = Separator.separate(Right(true): Either[Either[Int, String], Boolean])
 * // Result: Right(true): Either[Either[Int, String], Boolean]
 *   }}}
 */
object Eithers {

  /**
   * Computes the left-nested canonical form for Either[L, R].
   *
   * Recursively reassociates right-nested Eithers to left-nested form:
   *   - `Either[L, Either[X, Y]]` becomes `LeftNest[Either[L, X], Y]`
   *   - Atomic right types remain as `Either[L, R]`
   */
  type LeftNest[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => LeftNest[Either[L, x], y]
    case _            => Either[L, R]
  }

  /**
   * Extracts the left type from a left-nested Either.
   */
  type LeftOf[A] = A match {
    case Either[l, r] => l
  }

  /**
   * Extracts the right type from a left-nested Either.
   */
  type RightOf[A] = A match {
    case Either[l, r] => r
  }

  /**
   * Combines an Either[L, R] into a left-nested canonical form.
   *
   * @tparam L
   *   The left input type
   * @tparam R
   *   The right input type
   */
  trait Combiner[L, R] {
    type Out

    /**
     * Combines an Either[L, R] into left-nested canonical form.
     *
     * @param either
     *   The Either value to canonicalize
     * @return
     *   The left-nested canonical form
     */
    def combine(either: Either[L, R]): Out
  }

  /**
   * Separates a combined value by peeling the rightmost alternative.
   *
   * @tparam A
   *   The combined input type
   */
  trait Separator[A] {
    type Left
    type Right

    /**
     * Separates a combined value by peeling the rightmost alternative.
     *
     * @param a
     *   The combined value
     * @return
     *   Either[Left, Right] where Right is the rightmost alternative
     */
    def separate(a: A): Either[Left, Right]
  }

  object Combiner {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    inline given combiner[L, R]: WithOut[L, R, LeftNest[L, R]] =
      inline erasedValue[R] match {
        case _: Either[x, y] =>
          NestedCombiner[L, x, y]().asInstanceOf[WithOut[L, R, LeftNest[L, R]]]
        case _ =>
          AtomicCombiner[L, R]().asInstanceOf[WithOut[L, R, LeftNest[L, R]]]
      }

    private[combinators] class AtomicCombiner[L, R] extends Combiner[L, R] {
      type Out = Either[L, R]

      def combine(either: Either[L, R]): Either[L, R] = either
    }

    private[combinators] class NestedCombiner[L, X, Y](using
      inner: Combiner.WithOut[Either[L, X], Y, LeftNest[Either[L, X], Y]]
    ) extends Combiner[L, Either[X, Y]] {
      type Out = LeftNest[Either[L, X], Y]

      def combine(either: Either[L, Either[X, Y]]): LeftNest[Either[L, X], Y] =
        either match {
          case Left(l)         => inner.combine(Left(Left(l)))
          case Right(Left(x))  => inner.combine(Left(Right(x)))
          case Right(Right(y)) => inner.combine(Right(y))
        }
    }
  }

  object Separator {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    inline given separator[L, R]: WithTypes[Either[L, R], L, R] =
      new SeparatorImpl[L, R]

    private[combinators] class SeparatorImpl[L, R] extends Separator[Either[L, R]] {
      type Left  = L
      type Right = R

      def separate(a: Either[L, R]): Either[L, R] = a
    }
  }
}
