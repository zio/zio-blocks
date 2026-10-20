package generatejsonschemaandvalidatepayloads

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Generate JSON Schema from a Type and Validate Payloads Against It —
 * Validate Untyped Payloads
 *
 * Parses JSON text with Json.parse and validates it against the generated
 * contract with JsonSchema#check for valid and invalid submissions.
 *
 * Run with: cd schema-examples && sbt "runMain
 * generatejsonschemaandvalidatepayloads.Step2ValidatePayloadsExample"
 */
object Step2ValidatePayloadsExample extends App {

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

  def validate(schema: JsonSchema, payload: String): Unit =
    Json.parse(payload) match {
      case Left(parseError) => println(s"not JSON: ${parseError.message}")
      case Right(json)      =>
        schema.check(json) match {
          case None        => println("valid")
          case Some(error) => println(error.message)
        }
    }

  // A complete submission passes
  validate(
    contract,
    """{"title":"Programming in Scala","firstAuthor":"Martin Odersky","year":2008,"isbn":"9780981531601","tags":["scala","fundamentals"]}"""
  )

  // A submission without isbn or tags passes too
  validate(contract, """{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")

  // An unknown field, a string year and a missing author: every problem is reported
  validate(contract, """{"title":"Zionomicon","year":"2024","publisher":"Ziverge"}""")

  // A year outside the Int range
  validate(
    contract,
    """{"title":"Functional Programming in Scala","firstAuthor":"Paul Chiusano","year":20140000000}"""
  )

  // A yes-or-no answer
  println(
    contract.conforms(
      Json
        .parse("""{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")
        .fold(error => throw error, identity)
    )
  )

}
