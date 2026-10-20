package yourfirsthtmlpage

import zio.blocks.html._
import zio.blocks.schema.Schema

/**
 * Render Your First Server-Side HTML Page — Concept 5: Embed the Book as JSON
 *
 * Embeds the book as JSON in a script element and shows how < and > in a
 * review are escaped.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.Concept5JsonStateExample"
 */
object Concept5JsonStateExample extends App {

  final case class Book(title: String, authors: List[String], year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  final case class Review(reviewer: String, text: String, website: String)

  object Review {
    implicit val schema: Schema[Review] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024)

  val review = Review(
    reviewer = "mallory",
    text = "Loved it! <script>alert('stolen cookies')</script>",
    website = "javascript:alert('stolen cookies')"
  )

  val bookData = script(`type` := "application/json", id := "book-data", Js(ToJs[Book].toJs(zionomicon)))

  println(bookData.render)

  println(ToJs[Review].toJs(review))
}
