package generatejsonschemaandvalidatepayloads

import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Generate JSON Schema from a Type and Validate Payloads Against It — Generate and Publish the Contract
 *
 * Derives the JSON Schema contract for BookSubmission, prints it pretty and compact, writes it to
 * a temporary file and parses the file back.
 *
 * Run with: cd schema-examples && sbt "runMain generatejsonschemaandvalidatepayloads.Step1GenerateContractExample"
 */
object Step1GenerateContractExample extends App {

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

  println(contract)
  println(contract.toJson.print)

  val contractText: String = contract.toJson.print(WriterConfig.withIndentionStep2)

  val contractFile: Path = Files.createTempFile("book-submission", ".schema.json")
  Files.writeString(contractFile, contractText)
  println(s"Wrote contract to $contractFile")

  println(JsonSchema.parse(Files.readString(contractFile)) == Right(contract))

}
