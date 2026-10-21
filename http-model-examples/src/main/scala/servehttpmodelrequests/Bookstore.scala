package servehttpmodelrequests

import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec

/**
 * How to Serve http-model Requests with the JDK HTTP Server — the bookstore
 * model
 *
 * Three Scala books, the JSON codecs derived from their schema, and an HTML
 * catalog page built with the html module.
 */
final case class Book(id: Int, title: String, authors: List[String], year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object Bookstore {
  val books: List[Book] = List(
    Book(1, "Zionomicon", List("John A. De Goes", "Adam Fraser"), 2024),
    Book(2, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
    Book(3, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
  )

  val bookJson: JsonCodec[Book]           = Schema[Book].jsonCodec
  val bookListJson: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec

  def catalogPage(books: List[Book]): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf")
      ),
      body(
        h1("Scala Bookshelf"),
        ul(books.map(book => li(a(href := s"/books/${book.id}")(s"${book.title} (${book.year})"))))
      )
    )
}
