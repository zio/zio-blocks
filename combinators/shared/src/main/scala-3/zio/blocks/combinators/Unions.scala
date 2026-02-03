package zio.blocks.combinators

import scala.quoted.*
import scala.reflect.TypeTest

/**
 * Union operations: combining values into flat union types and separating them.
 *
 * The `Unions` module provides two complementary typeclasses:
 *   - `Combiner[L, R]`: Combines an Either[L, R] into a union type L | R
 *   - `Separator[A]`: Separates a union value by discriminating the rightmost
 *     type
 *
 * Key behaviors:
 *   - Canonical form is flat union: `A | B | C | D`
 *   - Combiner takes `Either[L, R]` as input and produces union `L | R`
 *   - Separator returns `Either[Left, Right]` because you can't pattern match
 *     on union membership without type tests - it bridges the untagged world to
 *     the tagged world
 *
 * Caveat: Union discrimination is fragile for erased types (e.g.,
 * `List[Int] | List[String]`). Works reliably for distinct concrete types.
 *
 * @example
 *   {{{
 * import zio.blocks.combinators.Unions._
 *
 * // Combine Either to union
 * val combined: Int | String = Combiner.combine(Left(42): Either[Int, String])
 * // Result: 42: Int | String
 *
 * // Separate discriminates rightmost type
 * val separated = Separator.separate(true: Int | String | Boolean)
 * // Result: Right(true): Either[Int | String, Boolean]
 *   }}}
 */
object Unions {

  /**
   * Combines an Either[L, R] into a union type L | R.
   *
   * @tparam L
   *   The left input type
   * @tparam R
   *   The right input type
   */
  trait Combiner[L, R] {
    type Out

    /**
     * Combines an Either[L, R] into a union type.
     *
     * @param either
     *   The Either value to convert to union
     * @return
     *   The union type L | R
     */
    def combine(either: Either[L, R]): Out
  }

  /**
   * Separates a union value by discriminating the rightmost type.
   *
   * @tparam A
   *   The union input type
   */
  trait Separator[A] {
    type Left
    type Right

    /**
     * Separates a union value by discriminating the rightmost type.
     *
     * @param a
     *   The union value
     * @return
     *   Either[Left, Right] where Right is the rightmost type in the union
     */
    def separate(a: A): Either[Left, Right]
  }

  object Combiner {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    given combiner[L, R]: WithOut[L, R, L | R] =
      new UnionCombiner[L, R]

    private[combinators] class UnionCombiner[L, R] extends Combiner[L, R] {
      type Out = L | R

      def combine(either: Either[L, R]): L | R = either match {
        case Left(l)  => l
        case Right(r) => r
      }
    }
  }

  object Separator {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    /**
     * Creates a Separator for union type L | R.
     *
     * Requires that L and R are distinct types with no overlap. If any type
     * appears in both L and R (e.g., `Int | String | Boolean` vs
     * `Int | String | Char`), compilation will fail with an error listing the
     * overlapping types.
     *
     * Union types must be unique. Use Either, a wrapper type, opaque type, or
     * newtype to distinguish values of the same underlying type.
     *
     * @tparam L
     *   The left type in the union
     * @tparam R
     *   The right type in the union
     * @param tt
     *   TypeTest for discriminating R from the union
     */
    inline given separator[L, R](using tt: TypeTest[L | R, R]): WithTypes[L | R, L, R] =
      ${ separatorMacro[L, R]('tt) }

    private def separatorMacro[L: Type, R: Type](
      tt: Expr[TypeTest[L | R, R]]
    )(using Quotes): Expr[WithTypes[L | R, L, R]] = {
      import quotes.reflect.*

      def flattenUnion(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
        case OrType(left, right) => flattenUnion(left) ++ flattenUnion(right)
        case other               => List(other)
      }

      val lTypes = flattenUnion(TypeRepr.of[L])
      val rTypes = flattenUnion(TypeRepr.of[R])

      val overlap = lTypes.filter { lType =>
        rTypes.exists(rType => lType =:= rType)
      }

      if (overlap.nonEmpty) {
        val overlapNames = overlap.map(_.typeSymbol.name).mkString(", ")
        report.errorAndAbort(
          s"Union types must contain unique types. Found overlapping types: $overlapNames. " +
            "Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        )
      }

      '{ new UnionSeparator[L, R](using $tt) }
    }

    private[combinators] class UnionSeparator[L, R](using tt: TypeTest[L | R, R]) extends Separator[L | R] {
      type Left  = L
      type Right = R

      def separate(a: L | R): Either[L, R] = a match {
        case tt(r) => Right(r)
        case _     => Left(a.asInstanceOf[L])
      }
    }
  }

  def combine[L, R](either: Either[L, R])(using c: Combiner[L, R]): c.Out = c.combine(either)
  def separate[A](a: A)(using s: Separator[A]): Either[s.Left, s.Right]   = s.separate(a)
}
