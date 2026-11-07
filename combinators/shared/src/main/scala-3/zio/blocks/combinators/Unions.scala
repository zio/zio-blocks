/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.combinators

import scala.quoted.*
import scala.reflect.TypeTest

/**
 * Converts an `Either[L, R]` into the union `L | R`, and back. Scala 3 only.
 *
 * Use it to expose a union type in an API while code underneath works with
 * `Either`, or the other way round. The `Unions.Unions` type class does the
 * work. `combine` drops the `Left`/`Right` wrapper; `separate` decides the side
 * with a run-time type test for `R`, so it is reliable only when no value of
 * `L` passes that test. If one type is a subtype of the other (`Shape` and
 * `Circle`, either way round), or the two differ only in erased type arguments
 * (`List[Int]` and `List[String]`), a left value that passes the test comes
 * back as `Right`.
 *
 * {{{
 * val port: Int | String = Unions.combine(Left(8080): Either[Int, String]) // 8080
 *
 * val u = summon[Unions.Unions[Int, String]]
 * u.separate("localhost": Int | String) // Right(localhost)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/combinators/ Combinators]]
 */
object Unions {

  /**
   * The type class behind `Unions.combine`: `combine` turns an `Either[L, R]`
   * into the union `Out`, which is `L | R`, and `separate` turns it back.
   *
   * @tparam L
   *   the left type; it can be a union itself
   * @tparam R
   *   the right type; it must not be a union
   */
  trait Unions[L, R] {

    /** The union type `L | R`. */
    type Out

    /** Returns the value inside `either`, typed as the union. */
    def combine(either: Either[L, R]): Out

    /**
     * Returns `Right` when `out` passes the run-time type test for `R`, and
     * `Left` otherwise.
     *
     * {{{
     * val u = summon[Unions.Unions[Int, String]]
     * u.separate(8080: Int | String) // Left(8080)
     * }}}
     */
    def separate(out: Out): Either[L, R]
  }

  /**
   * Holds the derived `Unions.Unions` instance; `WithOut` names an instance by
   * its result type.
   */
  object Unions {

    /** A `Unions.Unions` instance whose result type is `O`. */
    type WithOut[L, R, O] = Unions[L, R] { type Out = O }

    /**
     * Derives the instance for `L` and `R`, using a `TypeTest` for `R`. It does
     * not compile when `L` and `R` share a type (`Union types must contain
     * unique types. Found overlapping types: Int. ...`), or when `R` is a union
     * (`The right type of a Unions.Unions must not be a union type. ...`). Only
     * equal types count as shared: a subtype relation between `L` and `R` is
     * accepted.
     */
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

  /**
   * Returns the value inside `either`, typed as `L | R`, with the derived
   * `Unions.Unions` instance.
   */
  def combine[L, R](either: Either[L, R])(using u: Unions[L, R]): u.Out = u.combine(either)
}
