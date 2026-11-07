package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 2: Encode a Value
 * to JSON
 *
 * Encodes a Book to a compact JSON string with toJsonString.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept2EncodeExample"
 */
object Concept2EncodeExample extends App {

  case class Book(title: String, year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", 2024)

  val zionomiconJson = zionomicon.toJsonString

  println(zionomiconJson)
}
