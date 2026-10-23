package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import scala.concurrent.duration._

/**
 * Add Interactivity to a Page with HTMX — Step 3: A Debounced Live Search
 *
 * The search box asks the server for `/books?q=...` after each key press, but
 * only once the reader pauses for 300 milliseconds and only when the text has
 * really changed. The server filters the catalog and returns the fragment.
 *
 * Run with: sbt "runMain addinteractivitywithhtmx.Step3DebouncedSearchExample"
 */
object Step3DebouncedSearchExample {
  val htmxScript: String = "https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js"

  def bookList(books: List[Book]): Dom.Element =
    if (books.isEmpty) p("No books match your search.")
    else ul(books.map(book => li(s"${book.title} by ${book.authors.mkString(", ")}")))

  def page(): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Book search"),
        script(src := htmxScript)
      ),
      body(
        h1("Scala books"),
        input(
          `type`      := "search",
          id          := "q",
          name        := "q",
          placeholder := "Search books...",
          hxGet       := "/books",
          hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
          hxTarget    := HxTarget.css("#results")
        ),
        div(id := "results", bookList(Books.all))
      )
    )

  def htmlResponse(dom: Dom): Response =
    Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))

  /**
   * Reads a query parameter. htmx encodes a space as `+`, and `QueryParams`
   * keeps `+` as a plus sign, so we turn it back into a space. A typed plus
   * sign (`%2B`) is affected the same way until the known bug is fixed.
   */
  def queryParam(request: Request, name: String): String =
    request.queryParams.getFirst(name).map(_.replace('+', ' ')).getOrElse("")

  def app(request: Request): Response =
    request.path.encode match {
      case "/"      => htmlResponse(page())
      case "/books" => htmlResponse(bookList(Books.search(queryParam(request, "q"), "")))
      case other    => Response.text(Status.NotFound, s"No route for $other")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books?q=zio")
      Client.call(port, "GET", "/books?q=functional+programming")
    }
}
