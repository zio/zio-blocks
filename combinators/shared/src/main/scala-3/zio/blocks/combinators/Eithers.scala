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
   * Canonicalizes a type, recursively canonicalizing nested Eithers.
   */
  type Canonicalize[E] = E match {
    case Either[l, r] => CanonicalizeEither[l, r]
    case _            => E
  }

  /**
   * Canonicalizes an Either by first canonicalizing both branches, then
   * reassociating to left-nested form.
   */
  type CanonicalizeEither[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => CanonicalizeEither[Either[Canonicalize[L], Canonicalize[x]], y]
    case _            => Either[Canonicalize[L], Canonicalize[R]]
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

    inline given combiner[L, R]: WithOut[L, R, CanonicalizeEither[L, R]] =
      inline erasedValue[L] match {
        case _: Either[a, b] =>
          inline erasedValue[R] match {
            case _: Either[x, y] =>
              LeftNestedNestedCombiner[a, b, x, y]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
            case _ =>
              LeftNestedCombiner[a, b, R]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
          }
        case _ =>
          inline erasedValue[R] match {
            case _: Either[x, y] =>
              NestedCombiner[L, x, y]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
            case _ =>
              AtomicCombiner[L, R]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
          }
      }

    private[combinators] class AtomicCombiner[L, R] extends Combiner[L, R] {
      type Out = Either[L, R]

      def combine(either: Either[L, R]): Either[L, R] = either
    }

    private[combinators] class NestedCombiner[L, X, Y](using
      inner: Combiner.WithOut[Either[L, X], Y, CanonicalizeEither[Either[L, X], Y]]
    ) extends Combiner[L, Either[X, Y]] {
      type Out = CanonicalizeEither[Either[L, X], Y]

      def combine(either: Either[L, Either[X, Y]]): CanonicalizeEither[Either[L, X], Y] =
        either match {
          case Left(l)         => inner.combine(Left(Left(l)))
          case Right(Left(x))  => inner.combine(Left(Right(x)))
          case Right(Right(y)) => inner.combine(Right(y))
        }
    }

    private[combinators] class LeftNestedCombiner[A, B, R](using
      val leftCombiner: Combiner[A, B]
    ) extends Combiner[Either[A, B], R] {
      type Out = Either[leftCombiner.Out, R]

      def combine(either: Either[Either[A, B], R]): Either[leftCombiner.Out, R] =
        either match {
          case Left(inner) => Left(leftCombiner.combine(inner))
          case Right(r)    => Right(r)
        }
    }

    private[combinators] class LeftNestedNestedCombiner[A, B, X, Y](using
      val leftCombiner: Combiner[A, B],
      val inner: Combiner.WithOut[Either[leftCombiner.Out, X], Y, CanonicalizeEither[Either[leftCombiner.Out, X], Y]]
    ) extends Combiner[Either[A, B], Either[X, Y]] {
      type Out = CanonicalizeEither[Either[leftCombiner.Out, X], Y]

      def combine(either: Either[Either[A, B], Either[X, Y]]): CanonicalizeEither[Either[leftCombiner.Out, X], Y] =
        either match {
          case Left(l)         => inner.combine(Left(Left(leftCombiner.combine(l))))
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

  def combine[L, R](either: Either[L, R])(using c: Combiner[L, R]): c.Out = c.combine(either)
  def separate[A](a: A)(using s: Separator[A]): Either[s.Left, s.Right] = s.separate(a)
}
