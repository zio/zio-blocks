package zio.blocks.combinators

import scala.quoted.*
import scala.reflect.TypeTest

/**
 * Union operations: combining values into flat union types and separating them.
 *
 * The `Unions` module provides a unified typeclass `Unions[L, R]` that both
 * combines an `Either[L, R]` into a union type `L | R` and separates it back.
 */
object Unions {

  trait Unions[L, R] {
    type Out

    def combine(either: Either[L, R]): Out

    def separate(out: Out): Either[L, R]
  }

  object Unions {
    type WithOut[L, R, O] = Unions[L, R] { type Out = O }

    inline given unions[L, R](using tt: TypeTest[L | R, R]): WithOut[L, R, L | R] =
      ${ unionsMacro[L, R]('tt) }

    private def unionsMacro[L: Type, R: Type](
      tt: Expr[TypeTest[L | R, R]]
    )(using Quotes): Expr[WithOut[L, R, L | R]] = {
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

      val rRepr = TypeRepr.of[R]
      rRepr.dealias match {
        case _: OrType =>
          report.errorAndAbort(
            "The right type of a Unions.Unions must not be a union type. " +
              "Use a simple (non-union) type for R to ensure the separator peels exactly one type."
          )
        case _ => // ok
      }

      '{ new UnionInstance[L, R](using $tt) }
    }

    private[combinators] class UnionInstance[L, R](using tt: TypeTest[L | R, R]) extends Unions[L, R] {
      type Out = L | R

      def combine(either: Either[L, R]): L | R = either match {
        case Left(l)  => l
        case Right(r) => r
      }

      def separate(out: L | R): Either[L, R] = out match {
        case tt(r) => Right(r)
        case _     => Left(out.asInstanceOf[L])
      }
    }
  }

  def combine[L, R](either: Either[L, R])(using u: Unions[L, R]): u.Out = u.combine(either)

  /** Separates a union type value `out` back into an `Either[L, R]`.
    *
    * Uses a runtime type test to discriminate whether `out` is an instance of `R`; if so, returns
    * `Right(r)`, otherwise returns `Left(l)`.  `R` must not itself be a union type — the macro
    * enforces this at compile time.
    *
    * @tparam L   the left alternative type (must not overlap with `R`)
    * @tparam R   the right alternative type (must not be a union type)
    * @param out  the `L | R` value to separate
    * @param u    the typeclass instance that performs the type-test discrimination
    * @return     `Right(r)` if `out` is an `R`, or `Left(l)` otherwise
    */
  def separate[L, R](out: L | R)(using u: Unions[L, R]): Either[L, R] = u.separate(out)
}
