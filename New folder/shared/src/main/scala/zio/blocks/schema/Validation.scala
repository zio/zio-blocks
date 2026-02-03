package zio.blocks.schema

sealed trait Validation[+A]

object Validation {
  case object None extends Validation[Nothing]

  sealed trait Numeric[+A] extends Validation[A]

  object Numeric {
    case object Positive extends Numeric[Nothing]

    case object Negative extends Numeric[Nothing]

    case object NonPositive extends Numeric[Nothing]

    case object NonNegative extends Numeric[Nothing]

    case class Range[A](min: Option[A], max: Option[A]) extends Numeric[A]

    case class Set[A](values: scala.collection.immutable.Set[A]) extends Numeric[A]
  }

  sealed trait String extends Validation[Predef.String]

  object String {
    case object NonEmpty extends String

    case object Empty extends String

    case object Blank extends String

    case object NonBlank extends String

    case class Length(min: Option[scala.Int], max: Option[scala.Int]) extends String

    case class Pattern(regex: Predef.String) extends String
  }
}
