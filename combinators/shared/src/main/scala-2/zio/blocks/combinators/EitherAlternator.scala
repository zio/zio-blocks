package zio.blocks.combinators

/**
 * Alternates between two different types using Either[L, R].
 *
 * The `EitherAlternator` typeclass provides bidirectional conversion between
 * sum types and Either. It rejects same-type combinations at compile time
 * via implicit ambiguity to maintain type safety.
 *
 * Same-type restriction: `EitherAlternator[Int, Int]` will fail at compile time
 * due to ambiguous implicit resolution.
 *
 * @tparam L The left type
 * @tparam R The right type
 *
 * @example
 * {{{
 * val either: Either[Int, String] = EitherAlternator.left(42)
 * val maybeInt: Option[Int] = EitherAlternator.unleft(either)
 * val maybeString: Option[String] = EitherAlternator.unright(either)
 * }}}
 */
sealed trait EitherAlternator[L, R] {
  type Out
  
  /**
   * Creates an Either from a left value.
   *
   * @param l The left value
   * @return Left(l)
   */
  def left(l: L): Out
  
  /**
   * Creates an Either from a right value.
   *
   * @param r The right value
   * @return Right(r)
   */
  def right(r: R): Out
  
  /**
   * Attempts to extract a left value from the Either.
   *
   * @param out The Either value
   * @return Some(left value) if the Either is Left, None otherwise
   */
  def unleft(out: Out): Option[L]
  
  /**
   * Attempts to extract a right value from the Either.
   *
   * @param out The Either value
   * @return Some(right value) if the Either is Right, None otherwise
   */
  def unright(out: Out): Option[R]
  
  /**
   * Always returns false. This method exists for compatibility.
   */
  def isSameType: Boolean = false
}

object EitherAlternator extends EitherAlternatorLowPriority {
  /**
   * Type alias for an EitherAlternator with a specific output type.
   */
  type WithOut[L, R, O] = EitherAlternator[L, R] { type Out = O }

  // These two create ambiguity when L =:= R, causing compile error
  implicit def sameTypeLeft[A]: WithOut[A, A, Nothing] = sys.error("Cannot alternate same types")
  implicit def sameTypeRight[A]: WithOut[A, A, Nothing] = sys.error("Cannot alternate same types")
}

private[combinators] trait EitherAlternatorLowPriority {
  implicit def different[L, R]: EitherAlternator.WithOut[L, R, Either[L, R]] =
    new EitherAlternator[L, R] {
      type Out = Either[L, R]
      def left(l: L): Out = Left(l)
      def right(r: R): Out = Right(r)
      def unleft(out: Out): Option[L] = out.left.toOption
      def unright(out: Out): Option[R] = out.toOption
    }
}
