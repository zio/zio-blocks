package validatequeryparametersandheaders

import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec

/**
 * How to Validate Query Parameters and Headers and Answer 400 — the bookstore
 *
 * Three books and the JSON codec the examples use to list them.
 */
final case class Book(id: Int, title: String, authors: List[String], year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object Bookstore {
  val books: List[Book] = List(
    Book(1, "Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024),
    Book(2, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
    Book(3, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
  )

  val bookListJson: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
}
