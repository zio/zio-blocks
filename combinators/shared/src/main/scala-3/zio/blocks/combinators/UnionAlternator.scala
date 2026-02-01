package zio.blocks.combinators

import scala.compiletime.{erasedValue, error}

/**
 * Alternates between two different types using Scala 3's union types (L | R).
 *
 * The `UnionAlternator` typeclass provides bidirectional conversion between sum
 * types and union types. It rejects same-type combinations at compile time to
 * maintain type safety.
 *
 * Same-type restriction: `UnionAlternator[Int, Int]` will fail at compile time
 * with an error message suggesting to use `Either[A, A]` or wrap in distinct
 * types.
 *
 * @tparam L
 *   The left type
 * @tparam R
 *   The right type
 *
 * @example
 *   {{{
 * val intOrString: Int | String = UnionAlternator.left(42)
 * val maybeInt: Option[Int] = UnionAlternator.unleft(intOrString)
 * val maybeString: Option[String] = UnionAlternator.unright(intOrString)
 *   }}}
 */
sealed trait UnionAlternator[L, R] {
  type Out

  /**
   * Creates an output value from a left value.
   *
   * @param l
   *   The left value
   * @return
   *   The output value representing the left case
   */
  def left(l: L): Out

  /**
   * Creates an output value from a right value.
   *
   * @param r
   *   The right value
   * @return
   *   The output value representing the right case
   */
  def right(r: R): Out

  /**
   * Attempts to extract a left value from the output.
   *
   * @param out
   *   The output value
   * @return
   *   Some(left value) if the output is a left, None otherwise
   */
  def unleft(out: Out): Option[L]

  /**
   * Attempts to extract a right value from the output.
   *
   * @param out
   *   The output value
   * @return
   *   Some(right value) if the output is a right, None otherwise
   */
  def unright(out: Out): Option[R]
}

object UnionAlternator {

  /**
   * Type alias for a UnionAlternator with a specific output type.
   */
  type WithOut[L, R, O] = UnionAlternator[L, R] { type Out = O }

  type AlternateResult[L, R] = (L, R) match {
    case (Nothing, r) => r
    case (l, Nothing) => l
    case (l, r)       => l | r
  }

  private[combinators] class NothingLeft[R] extends UnionAlternator[Nothing, R] {
    type Out = R

    def left(l: Nothing): Out = l
    def right(r: R): Out      = r

    def unleft(out: Out): Option[Nothing] = None
    def unright(out: Out): Option[R]      = Some(out.asInstanceOf[R])
  }

  private[combinators] class NothingRight[L] extends UnionAlternator[L, Nothing] {
    type Out = L

    def left(l: L): Out        = l
    def right(r: Nothing): Out = r

    def unleft(out: Out): Option[L]        = Some(out.asInstanceOf[L])
    def unright(out: Out): Option[Nothing] = None
  }

  private[combinators] class Union[L, R] extends UnionAlternator[L, R] {
    type Out = L | R

    def left(l: L): Out  = l
    def right(r: R): Out = r

    def unleft(out: Out): Option[L] =
      if (out.isInstanceOf[L @unchecked]) Some(out.asInstanceOf[L])
      else None

    def unright(out: Out): Option[R] =
      if (out.isInstanceOf[R @unchecked]) Some(out.asInstanceOf[R])
      else None
  }

  private[combinators] inline def checkNotSame[L, R]: Unit =
    inline erasedValue[L] match {
      case _: R => error("Cannot alternate same types. Use Either[A, A] or wrap in distinct types.")
      case _    => ()
    }

  inline given alternator[L, R]: WithOut[L, R, AlternateResult[L, R]] = {
    checkNotSame[L, R]

    inline erasedValue[(L, R)] match {
      case _: (Nothing, r) =>
        new NothingLeft[r].asInstanceOf[WithOut[L, R, AlternateResult[L, R]]]

      case _: (l, Nothing) =>
        new NothingRight[l].asInstanceOf[WithOut[L, R, AlternateResult[L, R]]]

      case _ =>
        new Union[L, R].asInstanceOf[WithOut[L, R, AlternateResult[L, R]]]
    }
  }
}
