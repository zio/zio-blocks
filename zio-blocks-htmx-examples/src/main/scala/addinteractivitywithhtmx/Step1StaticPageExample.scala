package addinteractivitywithhtmx

import zio.blocks.html._

/**
 * Add Interactivity to a Page with HTMX — Step 1: The Static Page
 *
 * Renders the book search page with no HTMX attributes. The search box and the
 * category list look right, but nothing reacts when the reader uses them.
 *
 * Run with: sbt "runMain addinteractivitywithhtmx.Step1StaticPageExample"
 */
object Step1StaticPageExample {
  def bookList(books: List[Book]): Dom.Element =
    ul(books.map(book => li(s"${book.title} by ${book.authors.mkString(", ")}")))

  def page(): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Book search")
      ),
      body(
        h1("Scala books"),
        input(`type` := "search", name := "q", placeholder := "Search books..."),
        select(
          name := "category",
          option(value := "", "All"),
          option(value := "Language", "Language"),
          option(value := "Functional programming", "Functional programming"),
          option(value := "ZIO", "ZIO")
        ),
        div(id := "results", bookList(Books.all))
      )
    )

  def main(args: Array[String]): Unit =
    println(page().render(indent = 2))
}
