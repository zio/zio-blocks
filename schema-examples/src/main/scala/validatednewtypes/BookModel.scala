package validatednewtypes

import zio.blocks.schema._

/**
 * Validated Newtypes: shared model
 *
 * Isbn, Title, Price and PageCount are newtypes with private constructors and
 * smart constructors. Each companion turns `parse` into a Schema with
 * `Schema#transform`. Book derives its Schema from the four newtype schemas.
 * This file has no main method; the other examples in this package use it.
 */
final case class Isbn private (value: String)

object Isbn {
  def parse(input: String): Either[SchemaError, Isbn] = {
    val digits = input.filterNot(c => c == '-' || c == ' ')
    if (digits.length != 13 || !digits.forall(_.isDigit))
      Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    else {
      val weighted = digits.init.zipWithIndex.map { case (c, i) => c.asDigit * (if (i % 2 == 0) 1 else 3) }
      if ((10 - weighted.sum % 10) % 10 == digits.last.asDigit) Right(new Isbn(digits))
      else Left(SchemaError.validationFailed(s"ISBN check digit is wrong in '$input'"))
    }
  }

  implicit val schema: Schema[Isbn] =
    Schema[String].transform(s => parse(s).fold(e => throw e, identity), _.value)
}

final case class Title private (value: String)

object Title {
  def parse(input: String): Either[SchemaError, Title] = {
    val trimmed = input.trim
    if (trimmed.isEmpty) Left(SchemaError.validationFailed("title must not be blank"))
    else if (trimmed.length > 200)
      Left(SchemaError.validationFailed(s"title must be at most 200 characters, got ${trimmed.length}"))
    else Right(new Title(trimmed))
  }

  implicit val schema: Schema[Title] =
    Schema[String].transform(s => parse(s).fold(e => throw e, identity), _.value)
}

final case class Price private (amount: BigDecimal)

object Price {
  def parse(amount: BigDecimal): Either[SchemaError, Price] =
    if (amount > 0) Right(new Price(amount))
    else Left(SchemaError.validationFailed(s"price must be positive, got $amount"))

  implicit val schema: Schema[Price] =
    Schema[BigDecimal].transform(a => parse(a).fold(e => throw e, identity), _.amount)
}

final case class PageCount private (value: Int)

object PageCount {
  def parse(value: Int): Either[SchemaError, PageCount] =
    if (value > 0) Right(new PageCount(value))
    else Left(SchemaError.validationFailed(s"page count must be positive, got $value"))

  implicit val schema: Schema[PageCount] =
    Schema[Int].transform(n => parse(n).fold(e => throw e, identity), _.value)
}

final case class Book(isbn: Isbn, title: Title, price: Price, pages: PageCount)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
