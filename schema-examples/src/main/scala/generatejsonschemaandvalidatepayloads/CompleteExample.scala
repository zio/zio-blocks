package generatejsonschemaandvalidatepayloads

import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Generate JSON Schema from a Type and Validate Payloads Against It — Complete Example
 *
 * Derives the contract, writes it to a file, validates payloads against it, loads the catalog
 * team's schema and compares typed decoding with JSON Schema validation.
 *
 * Run with: cd schema-examples && sbt "runMain generatejsonschemaandvalidatepayloads.CompleteExample"
 */
object CompleteExample extends App {

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

  // Generate and publish the contract
  println("=== Generate and publish the contract ===")
  val contract: JsonSchema = BookSubmission.schema.toJsonSchema

  println(contract)
  println(contract.toJson.print)

  val contractText: String = contract.toJson.print(WriterConfig.withIndentionStep2)

  val contractFile: Path = Files.createTempFile("book-submission", ".schema.json")
  Files.writeString(contractFile, contractText)
  println(s"Wrote contract to $contractFile")

  println(JsonSchema.parse(Files.readString(contractFile)) == Right(contract))

  // Validate untyped payloads
  println("=== Validate untyped payloads ===")
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


  // Load an external schema
  println("=== Load an external schema ===")
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


  // Typed decoding versus JSON Schema validation
  println("=== Typed decoding versus JSON Schema validation ===")
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
