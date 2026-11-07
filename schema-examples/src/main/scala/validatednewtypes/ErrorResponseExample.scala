package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Validated Newtypes: Build the Error Response
 *
 * Turns the collected SchemaError into an ErrorResponse with one FieldError per
 * problem and prints it as JSON.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.ErrorResponseExample"
 */
object ErrorResponseExample extends App {

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

  decodeAll("""{"isbn":"978-ZIO","title":"Zionomicon","pages":"many"}""").left.foreach { e =>
    println(ErrorResponse.schema.jsonCodec.encodeToString(toResponse(e)))
  }
}
