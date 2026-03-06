package zio.blocks.combinators

import scala.compiletime.erasedValue

object Eithers {

  type LeftNest[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => LeftNest[Either[L, x], y]
    case _            => Either[L, R]
  }

  type Canonicalize[E] = E match {
    case Either[l, r] => CanonicalizeEither[l, r]
    case _            => E
  }

  type CanonicalizeEither[L, R] <: Either[?, ?] = R match {
    case Either[x, y] => CanonicalizeEither[Either[Canonicalize[L], Canonicalize[x]], y]
    case _            => Either[Canonicalize[L], Canonicalize[R]]
  }

  type LeftOf[A] = A match {
    case Either[l, r] => l
  }

  type RightOf[A] = A match {
    case Either[l, r] => r
  }

  trait Eithers[L, R] {
    type Out

    def combine(either: Either[L, R]): Out

    def separate(out: Out): Either[L, R]
  }

  object Eithers {
    type WithOut[L, R, O] = Eithers[L, R] { type Out = O }

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

  def combine[L, R](either: Either[L, R])(using e: Eithers[L, R]): e.Out = e.combine(either)

  /** Separates a canonicalized Either `out` back into its original `Either[L, R]` form.
    *
    * @tparam L   the left alternative type
    * @tparam R   the right alternative type
    * @param e    the typeclass instance that knows how to separate `Out` back into `Either[L, R]`
    * @param out  the canonicalized Either value to separate
    * @return     an `Either[L, R]` recovered from the canonical form
    */
  def separate[L, R](using e: Eithers[L, R])(out: e.Out): Either[L, R] = e.separate(out)
}
