package generatejsonschemaandvalidatepayloads

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Generate JSON Schema from a Type and Validate Payloads Against It —
 * Compare Typed Decoding with Validation
 *
 * Decodes one bad payload with fromJson[BookSubmission] and validates it with
 * JsonSchema#check.
 *
 * Run with: cd schema-examples && sbt "runMain
 * generatejsonschemaandvalidatepayloads.Step4TypedVersusUntypedExample"
 */
object Step4TypedVersusUntypedExample extends App {

  @Modifier.noExtraFields()
  case class BookSubmission(
    title: String,
    firstAuthor: String,
    year: Int,
    isbn: Option[String],
    tags: List[String]
  )

  object BookSubmission {
    implicit val schema: Schema[BookSubmission] = Schema.derived
  }

  val contract: JsonSchema = BookSubmission.schema.toJsonSchema

  val badPayload = """{"title":"Zionomicon","year":"2024","publisher":"Ziverge"}"""

  println("Typed decoding (first error only):")
  badPayload.fromJson[BookSubmission] match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"decoded: $book")
  }

  println("JSON Schema validation (all errors):")
  Json.parse(badPayload) match {
    case Left(parseError) => println(s"not JSON: ${parseError.message}")
    case Right(json)      =>
      contract.check(json) match {
        case None        => println("valid")
        case Some(error) => println(error.message)
      }
  }

}
