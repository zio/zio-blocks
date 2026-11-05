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

import scala.compiletime.erasedValue

/**
 * Rewrites a nested `Either` into one canonical, left-nested shape, and back.
 * `Either[Int, Either[String, Boolean]]` becomes
 * `Either[Either[Int, String], Boolean]`: the alternatives keep their order
 * from left to right, and only the nesting changes. Nested `Either`s on the
 * left side are rewritten the same way.
 *
 * Use it when the same alternatives arrive with different nesting and you need
 * one shape, to encode them or to match on them once. The `Eithers.Eithers`
 * type class does the work, and `Eithers.combine` is the shortcut. Both Scala
 * versions produce the same shape.
 *
 * {{{
 * val failure: Either[Int, Either[String, Boolean]] = Right(Left("timeout"))
 *
 * Eithers.combine(failure) // Left(Right(timeout))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/combinators/ Combinators]]
 */
object Eithers {

  /**
   * Re-nests `Either[L, R]` to the left by peeling `Either`s off the right
   * side: `LeftNest[Int, Either[String, Boolean]]` is
   * `Either[Either[Int, String], Boolean]`. Unlike `CanonicalizeEither`, it
   * leaves an `Either` inside `L` as it is.
   */
  type LeftNest[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => LeftNest[Either[L, x], y]
    case _            => Either[L, R]
  }

  /**
   * The left-nested form of `E` when `E` is an `Either`, and `E` itself
   * otherwise.
   */
  type Canonicalize[E] = E match {
    case Either[l, r] => CanonicalizeEither[l, r]
    case _            => E
  }

  /**
   * The left-nested form of `Either[L, R]`, with nested `Either`s on both sides
   * rewritten too; the `Out` of the derived instance.
   */
  type CanonicalizeEither[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => CanonicalizeEither[Either[Canonicalize[L], Canonicalize[x]], y]
    case _            => Either[Canonicalize[L], Canonicalize[R]]
  }

  /** The left type of the `Either` type `A`. */
  type LeftOf[A] = A match {
    case Either[l, r] => l
  }

  /** The right type of the `Either` type `A`. */
  type RightOf[A] = A match {
    case Either[l, r] => r
  }

  /**
   * The type class behind `Eithers.combine`: `combine` reshapes an
   * `Either[L, R]` into the left-nested `Out`, and `separate` restores the
   * original nesting. The compiler derives an instance for any `L` and `R`.
   *
   * {{{
   * def roundTrip[L, R](e: Either[L, R])(implicit t: Eithers.Eithers[L, R]): Either[L, R] =
   *   t.separate(t.combine(e))
   *
   * val failure: Either[Int, Either[String, Boolean]] = Right(Left("timeout"))
   * roundTrip(failure) // Right(Left(timeout))
   * }}}
   *
   * @tparam L
   *   the left type of the input `Either`
   * @tparam R
   *   the right type of the input `Either`
   */
  trait Eithers[L, R] {

    /**
     * The left-nested form of `Either[L, R]`:
     * `Either[Either[Int, String], Boolean]` for `L = Int` and
     * `R = Either[String, Boolean]`, and `Either[L, R]` itself when neither
     * side is an `Either`.
     */
    type Out

    /**
     * Moves the value of `either` to the same alternative in the left-nested
     * shape: with `R = Either[String, Boolean]`, `Left(1)` becomes
     * `Left(Left(1))`, `Right(Left("timeout"))` becomes `Left(Right(timeout))`,
     * and `Right(Right(true))` becomes `Right(true)`.
     */
    def combine(either: Either[L, R]): Out

    /**
     * Moves a value of the left-nested shape back to the original nesting, so
     * `separate(combine(e))` is `e`.
     */
    def separate(out: Out): Either[L, R]
  }

  /**
   * Holds the derived `Eithers.Eithers` instance; `WithOut` names an instance
   * by its result type.
   */
  object Eithers {

    /**
     * An `Eithers.Eithers` instance whose result type is `O`, for a signature
     * that needs to name the result.
     */
    type WithOut[L, R, O] = Eithers[L, R] { type Out = O }

    /**
     * Derives the instance for `L` and `R` at compile time; its `Out` is
     * `CanonicalizeEither[L, R]`.
     */
    inline given eithers[L, R]: WithOut[L, R, CanonicalizeEither[L, R]] =
      inline erasedValue[L] match {
        case _: Either[a, b] =>
          inline erasedValue[R] match {
            case _: Either[x, y] =>
              LeftNestedNestedInstance[a, b, x, y]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
            case _ =>
              LeftNestedInstance[a, b, R]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
          }
        case _ =>
          inline erasedValue[R] match {
            case _: Either[x, y] =>
              NestedInstance[L, x, y]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
            case _ =>
              AtomicInstance[L, R]().asInstanceOf[WithOut[L, R, CanonicalizeEither[L, R]]]
          }
      }

    private[combinators] class AtomicInstance[L, R] extends Eithers[L, R] {
      type Out = Either[L, R]

      def combine(either: Either[L, R]): Either[L, R] = either

      def separate(out: Either[L, R]): Either[L, R] = out
    }

    private[combinators] class NestedInstance[L, X, Y](using
      inner: Eithers.WithOut[Either[L, X], Y, CanonicalizeEither[Either[L, X], Y]]
    ) extends Eithers[L, Either[X, Y]] {
      type Out = CanonicalizeEither[Either[L, X], Y]

      def combine(either: Either[L, Either[X, Y]]): CanonicalizeEither[Either[L, X], Y] =
        either match {
          case Left(l)         => inner.combine(Left(Left(l)))
          case Right(Left(x))  => inner.combine(Left(Right(x)))
          case Right(Right(y)) => inner.combine(Right(y))
        }

      def separate(out: CanonicalizeEither[Either[L, X], Y]): Either[L, Either[X, Y]] =
        inner.separate(out) match {
          case Left(Left(l))  => Left(l)
          case Left(Right(x)) => Right(Left(x))
          case Right(y)       => Right(Right(y))
        }
    }

    private[combinators] class LeftNestedInstance[A, B, R](using
      val leftEithers: Eithers[A, B]
    ) extends Eithers[Either[A, B], R] {
      type Out = Either[leftEithers.Out, R]

      def combine(either: Either[Either[A, B], R]): Either[leftEithers.Out, R] =
        either match {
          case Left(ab) => Left(leftEithers.combine(ab))
          case Right(r) => Right(r)
        }

      def separate(out: Either[leftEithers.Out, R]): Either[Either[A, B], R] =
        out match {
          case Left(ab) => Left(leftEithers.separate(ab))
          case Right(r) => Right(r)
        }
    }

    private[combinators] class LeftNestedNestedInstance[A, B, X, Y](using
      val leftEithers: Eithers[A, B],
      val inner: Eithers.WithOut[Either[leftEithers.Out, X], Y, CanonicalizeEither[Either[leftEithers.Out, X], Y]]
    ) extends Eithers[Either[A, B], Either[X, Y]] {
      type Out = CanonicalizeEither[Either[leftEithers.Out, X], Y]

      def combine(
        either: Either[Either[A, B], Either[X, Y]]
      ): CanonicalizeEither[Either[leftEithers.Out, X], Y] =
        either match {
          case Left(l)         => inner.combine(Left(Left(leftEithers.combine(l))))
          case Right(Left(x))  => inner.combine(Left(Right(x)))
          case Right(Right(y)) => inner.combine(Right(y))
        }

      def separate(out: CanonicalizeEither[Either[leftEithers.Out, X], Y]): Either[Either[A, B], Either[X, Y]] =
        inner.separate(out) match {
          case Left(Left(l))  => Left(leftEithers.separate(l))
          case Left(Right(x)) => Right(Left(x))
          case Right(y)       => Right(Right(y))
        }
    }
  }

  /**
   * Reshapes `either` into its left-nested form with the derived
   * `Eithers.Eithers` instance.
   *
   * {{{
   * val ok: Either[Int, Either[String, Boolean]] = Right(Right(true))
   * Eithers.combine(ok) // Right(true)
   * }}}
   */
  def combine[L, R](either: Either[L, R])(using e: Eithers[L, R]): e.Out = e.combine(either)
}
