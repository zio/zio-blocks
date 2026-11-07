package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 3: Decode JSON
 * Back Into a Value
 *
 * Decodes the JSON with fromJson and checks that the round trip returns the
 * original value.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept3DecodeExample"
 */
object Concept3DecodeExample extends App {

  case class Book(title: String, year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", 2024)

  val zionomiconJson = zionomicon.toJsonString

  val decoded = zionomiconJson.fromJson[Book]

  println(decoded)
  println(decoded == Right(zionomicon))
}
