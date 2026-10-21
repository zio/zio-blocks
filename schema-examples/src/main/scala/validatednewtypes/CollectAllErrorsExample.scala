package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Validated Newtypes: Collect Every Error
 *
 * Decodes JSON through Json#toDynamicValue and Schema#fromDynamicValue, which
 * collects every error, including a wrong type and a missing field. A
 * BookSubmission value is validated the same way.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.CollectAllErrorsExample"
 */
object CollectAllErrorsExample extends App {

  def decodeAll(json: String): Either[SchemaError, Book] =
    Json.parse(json).flatMap(parsed => Book.schema.fromDynamicValue(parsed.toDynamicValue))

  val zionomiconJson = """{"isbn":"978-ZIO","title":"Zionomicon","price":-45,"pages":0}"""

  decodeAll(zionomiconJson).left.foreach(e => println(e.message))

  decodeAll("""{"isbn":"978-ZIO","title":"Zionomicon","pages":"many"}""").left.foreach(e => println(e.message))

  println(
    decodeAll("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":45,"pages":320}""")
  )

  final case class BookSubmission(isbn: String, title: String, price: BigDecimal, pages: Int)

  object BookSubmission {
    implicit val schema: Schema[BookSubmission] = Schema.derived
  }

  val submission = BookSubmission("978-ZIO", "Zionomicon", BigDecimal(-45), 0)

  Book.schema
    .fromDynamicValue(BookSubmission.schema.toDynamicValue(submission))
    .left
    .foreach(e => println(e.message))
}
