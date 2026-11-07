package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Decode Part of the Tree
 * Into a Case Class
 *
 * Decodes one book and every book into Book, prints the errors for a malformed
 * book from both decoders, and writes an updated book back into the document.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step7DecodeSubTreeExample"
 */
object Step7DecodeSubTreeExample {

  case class Book(title: String, authors: List[String], price: BigDecimal, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    println(bookstore.get(p".books[0]").as[Book])

    println(bookstore.get(p".books[*]").asAll[Book].map(_.map(_.title)))

    val badBook: Json =
      Json.parse("""{"title": "Functional Programming in Scala", "price": "44.99"}""").getOrElse(Json.Null)

    println(badBook.as[Book])

    println(Schema[Book].fromDynamicValue(badBook.toDynamicValue))

    val zionomicon: Book = bookstore.get(p".books[0]").as[Book].getOrElse(sys.error("no first book"))

    val reprinted: Json = bookstore.set(p".books[0]", Json.from(zionomicon.copy(price = BigDecimal("39.99"))))

    println(reprinted.get(p".books[0]").one.map(_.print))
  }
}
