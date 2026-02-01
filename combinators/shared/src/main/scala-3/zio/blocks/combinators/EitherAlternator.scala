package zio.blocks.combinators

/**
 * Alternates between two types using Either[L, R].
 *
 * The `EitherAlternator` typeclass provides bidirectional conversion between
 * sum types and Either. Unlike `UnionAlternator`, same-type combinations are
 * allowed because `Left` and `Right` wrappers provide positional information to
 * distinguish the two sides.
 *
 * @tparam L
 *   The left type
 * @tparam R
 *   The right type
 *
 * @example
 *   {{{
 * // Different types
 * val either: Either[Int, String] = EitherAlternator.left(42)
 * val maybeInt: Option[Int] = EitherAlternator.unleft(either)
 *
 * // Same types - valid because Left/Right are distinguishable
 * val alt = summon[EitherAlternator[Int, Int]]
 * val left: Either[Int, Int] = alt.left(1)   // Left(1)
 * val right: Either[Int, Int] = alt.right(2) // Right(2)
 *   }}}
 */
sealed trait EitherAlternator[L, R] {
  type Out

  /**
   * Creates an Either from a left value.
   *
   * @param l
   *   The left value
   * @return
   *   Left(l)
   */
  def left(l: L): Out

  /**
   * Creates an Either from a right value.
   *
   * @param r
   *   The right value
   * @return
   *   Right(r)
   */
  def right(r: R): Out

  /**
   * Attempts to extract a left value from the Either.
   *
   * @param out
   *   The Either value
   * @return
   *   Some(left value) if the Either is Left, None otherwise
   */
  def unleft(out: Out): Option[L]

  /**
   * Attempts to extract a right value from the Either.
   *
   * @param out
   *   The Either value
   * @return
   *   Some(right value) if the Either is Right, None otherwise
   */
  def unright(out: Out): Option[R]
}

object EitherAlternator {

  /**
   * Type alias for an EitherAlternator with a specific output type.
   */
  type WithOut[L, R, O] = EitherAlternator[L, R] { type Out = O }

  private[combinators] final class EitherAlternatorImpl[L, R] extends EitherAlternator[L, R] {
    type Out = Either[L, R]
    def left(l: L): Out              = Left(l)
    def right(r: R): Out             = Right(r)
    def unleft(out: Out): Option[L]  = out.left.toOption
    def unright(out: Out): Option[R] = out.toOption
  }

  inline given alternator[L, R]: WithOut[L, R, Either[L, R]] =
    new EitherAlternatorImpl[L, R]
}
