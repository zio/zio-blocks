package yourfirsthtmlpage

import zio.blocks.html._
import zio.blocks.schema.Schema

/**
 * Render Your First Server-Side HTML Page — Concept 6: Check the Page With Selectors
 *
 * Selects the li items of the book page, counts them and prints their text.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.Concept6SelectExample"
 */
object Concept6SelectExample extends App {

  final case class Book(title: String, authors: List[String], year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024)

  val related = List(
    Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
    Book("Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
  )

  def bookLine(book: Book): String =
    s"${book.title} by ${book.authors.mkString(", ")} (${book.year})"

  def bookPage(book: Book, related: List[Book]): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title(book.title)
      ),
      body(
        h1(book.title),
        p(bookLine(book)),
        h2("Related books"),
        ul(related.map(relatedBook => li(bookLine(relatedBook))))
      )
    )

  val page = bookPage(zionomicon, related)

  val items = page.select(CssSelector.element("li"))

  println(items.length)

  items.texts.foreach(println)
}
