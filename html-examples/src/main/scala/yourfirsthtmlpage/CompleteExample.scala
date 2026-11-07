package yourfirsthtmlpage

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.html._
import zio.blocks.schema.Schema

/**
 * Render Your First Server-Side HTML Page — Complete Example
 *
 * Builds the book page with its style sheet, a hostile review and embedded
 * JSON, checks the related-book items with a selector and writes the page to
 * book.html.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.CompleteExample"
 */
object CompleteExample extends App {

  final case class Book(title: String, authors: List[String], year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  final case class Review(reviewer: String, text: String, website: String)

  object Review {
    implicit val schema: Schema[Review] = Schema.derived
  }

  val zionomicon = Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024)

  val related = List(
    Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008),
    Book("Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014)
  )

  val review = Review(
    reviewer = "mallory",
    text = "Loved it! <script>alert('stolen cookies')</script>",
    website = "javascript:alert('stolen cookies')"
  )

  def bookLine(book: Book): String =
    s"${book.title} by ${book.authors.mkString(", ")} (${book.year})"

  def reviewBlock(review: Review): Dom.Element =
    div(
      className := "review",
      p(review.text),
      a(href := review.website, s"Visit ${review.reviewer}'s website")
    )

  val pageStyle: Css =
    css"""body { font-family: sans-serif; max-width: ${CssLength(40, "em")}; margin: 0 auto; }
.review { border-left: ${CssLength(4, "px")} solid ${CssColor.Rgb(200, 60, 40)}; padding-left: ${CssLength(1, "em")}; }"""

  val bookData = script(`type` := "application/json", id := "book-data", Js(ToJs[Book].toJs(zionomicon)))

  def bookPage(book: Book, related: List[Book]): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title(book.title),
        style(pageStyle)
      ),
      body(
        h1(book.title),
        p(bookLine(book)),
        h2("Related books"),
        ul(related.map(relatedBook => li(bookLine(relatedBook)))),
        h2("Reviews"),
        reviewBlock(review),
        bookData
      )
    )

  val page = bookPage(zionomicon, related)

  val items = page.select(CssSelector.element("li"))
  println(s"Related books on the page: ${items.length}")

  val document = doctype.render + "\n" + page.render(indent = 2)

  val path = Paths.get("book.html")
  Files.write(path, document.getBytes(StandardCharsets.UTF_8))

  println(s"Wrote ${path.toAbsolutePath}")
  println("Open it in your browser to see the page.")
}
