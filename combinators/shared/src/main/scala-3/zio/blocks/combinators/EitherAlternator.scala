package zio.blocks.combinators

import scala.compiletime.{erasedValue, error}

/**
 * Alternates between two different types using Either[L, R].
 *
 * The `EitherAlternator` typeclass provides bidirectional conversion between
 * sum types and Either. It rejects same-type combinations at compile time to
 * maintain type safety.
 *
 * Same-type restriction: `EitherAlternator[Int, Int]` will fail at compile time
 * with an error message suggesting to use the same type directly instead.
 *
 * @tparam L
 *   The left type
 * @tparam R
 *   The right type
 *
 * @example
 *   {{{
 * val either: Either[Int, String] = EitherAlternator.left(42)
 * val maybeInt: Option[Int] = EitherAlternator.unleft(either)
 * val maybeString: Option[String] = EitherAlternator.unright(either)
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

  /**
   * Always returns false. This method exists for compatibility.
   */
  def isSameType: Boolean = false
}

object EitherAlternator {

  /**
   * Type alias for an EitherAlternator with a specific output type.
   */
  type WithOut[L, R, O] = EitherAlternator[L, R] { type Out = O }

  private[combinators] inline def checkNotSame[L, R]: Unit =
    inline erasedValue[L] match {
      case _: R => error("Cannot alternate same types with EitherAlternator. Use the same type directly instead.")
      case _    => ()
    }

  private[combinators] final class EitherAlternatorImpl[L, R] extends EitherAlternator[L, R] {
    type Out = Either[L, R]
    def left(l: L): Out              = Left(l)
    def right(r: R): Out             = Right(r)
    def unleft(out: Out): Option[L]  = out.left.toOption
    def unright(out: Out): Option[R] = out.toOption
  }

  inline given alternator[L, R]: WithOut[L, R, Either[L, R]] = {
    checkNotSame[L, R]
    new EitherAlternatorImpl[L, R]
  }
}
