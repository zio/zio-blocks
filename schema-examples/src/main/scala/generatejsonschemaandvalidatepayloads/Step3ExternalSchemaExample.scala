package generatejsonschemaandvalidatepayloads

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Generate JSON Schema from a Type and Validate Payloads Against It — Load an External Schema
 *
 * Parses the catalog team's JSON Schema, checks payloads against it, and decodes payloads through
 * Schema.fromJsonSchema.
 *
 * Run with: cd schema-examples && sbt "runMain generatejsonschemaandvalidatepayloads.Step3ExternalSchemaExample"
 */
object Step3ExternalSchemaExample extends App {

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


  def validate(schema: JsonSchema, payload: String): Unit =
    Json.parse(payload) match {
      case Left(parseError) => println(s"not JSON: ${parseError.message}")
      case Right(json)      =>
        schema.check(json) match {
          case None        => println("valid")
          case Some(error) => println(error.message)
        }
    }

  val catalogContractText: String =
    """{
      |  "$schema": "https://json-schema.org/draft/2020-12/schema",
      |  "type": "object",
      |  "properties": {
      |    "title": { "type": "string", "minLength": 1 },
      |    "firstAuthor": { "type": "string", "minLength": 1 },
      |    "year": { "type": "integer", "minimum": 1450 },
      |    "isbn": { "type": "string", "pattern": "^97[89][0-9]{10}$" },
      |    "tags": { "type": "array", "items": { "type": "string" }, "uniqueItems": true }
      |  },
      |  "required": ["title", "firstAuthor", "year"],
      |  "additionalProperties": false
      |}""".stripMargin

  val catalogContract: JsonSchema =
    JsonSchema.parse(catalogContractText).fold(error => throw error, identity)

  validate(catalogContract, """{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")

  validate(
    catalogContract,
    """{"title":"","firstAuthor":"Paul Chiusano","year":2014,"isbn":"978-1617290657","tags":["fp","fp"]}"""
  )

  val catalogSchema: Schema[Json] = Schema.fromJsonSchema(catalogContract)

  println(
    catalogSchema.jsonCodec.decode(
      """{"title":"Programming in Scala","firstAuthor":"Martin Odersky","year":2008,"isbn":"9780981531601","tags":["scala"]}"""
    )
  )

  catalogSchema.jsonCodec.decode(
    """{"title":"","firstAuthor":"Paul Chiusano","year":2014,"isbn":"978-1617290657","tags":["fp","fp"]}"""
  ) match {
    case Left(error) => println(error.message)
    case Right(json) => println(s"decoded: $json")
  }

  // The decoder treats every listed property as required, so this conforming payload fails without an isbn
  println(
    catalogSchema.jsonCodec.decode("""{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")
  )

}
