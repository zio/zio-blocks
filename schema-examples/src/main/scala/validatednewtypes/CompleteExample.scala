package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Validated Newtypes: Putting It Together, Complete Example
 *
 * Defines the four newtypes and Book, decodes a batch of three submissions with
 * every error collected, and prints either the decoded book or the 400 response
 * body for each.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.CompleteExample"
 */
object CompleteExample extends App {

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

  def decodeAll(json: String): Either[SchemaError, Book] =
    Json.parse(json).flatMap(parsed => Book.schema.fromDynamicValue(parsed.toDynamicValue))

  final case class FieldError(field: String, message: String)

  object FieldError {
    implicit val schema: Schema[FieldError] = Schema.derived
  }

  final case class ErrorResponse(status: Int, errors: List[FieldError])

  object ErrorResponse {
    implicit val schema: Schema[ErrorResponse] = Schema.derived
  }

  def fieldOf(error: SchemaError.Single): String = {
    val path = error.source.nodes.collect { case DynamicOptic.Node.Field(name) => name }
    error match {
      case missing: SchemaError.MissingField => (path :+ missing.fieldName).mkString(".")
      case _                                 => path.mkString(".")
    }
  }

  def detailOf(error: SchemaError.Single): String = error match {
    case e: SchemaError.ConversionFailed    => e.details
    case _: SchemaError.MissingField        => "is required"
    case e: SchemaError.ExpectationMismatch => e.expectation
    case e: SchemaError.Message             => e.details
    case other                              => other.message
  }

  def toResponse(error: SchemaError): ErrorResponse =
    ErrorResponse(400, error.errors.map(e => FieldError(fieldOf(e), detailOf(e))))

  val submissions: List[String] = List(
    """{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":44.99,"pages":320}""",
    """{"isbn":"978-ZIO","title":"Zionomicon","price":-45,"pages":0}""",
    """{"isbn":"978-0-9971480-0-9","title":"Programming in Scala","price":49.95,"pages":0}"""
  )

  submissions.foreach { json =>
    decodeAll(json) match {
      case Right(book) => println(s"Accepted: $book")
      case Left(error) => println(ErrorResponse.schema.jsonCodec.encodeToString(toResponse(error)))
    }
  }
}
