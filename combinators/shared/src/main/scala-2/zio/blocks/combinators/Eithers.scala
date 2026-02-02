package zio.blocks.combinators

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

  object Combiner extends CombinerLowPriority {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    implicit def nested[L, X, Y](implicit
      inner: Combiner[Either[L, X], Y]
    ): WithOut[L, Either[X, Y], inner.Out] =
      new Combiner[L, Either[X, Y]] {
        type Out = inner.Out

        def combine(either: Either[L, Either[X, Y]]): inner.Out =
          either match {
            case Left(l)         => inner.combine(Left(Left(l)))
            case Right(Left(x))  => inner.combine(Left(Right(x)))
            case Right(Right(y)) => inner.combine(Right(y))
          }
      }
  }

  trait CombinerLowPriority {
    implicit def atomic[L, R]: Combiner.WithOut[L, R, Either[L, R]] =
      new Combiner[L, R] {
        type Out = Either[L, R]

        def combine(either: Either[L, R]): Either[L, R] = either
      }
  }

  object Separator {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    implicit def separator[L, R]: WithTypes[Either[L, R], L, R] =
      new Separator[Either[L, R]] {
        type Left  = L
        type Right = R

        def separate(a: Either[L, R]): Either[L, R] = a
      }
  }
}
