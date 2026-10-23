package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import scala.concurrent.duration._

/**
 * Add Interactivity to a Page with HTMX — Step 4: A Category Filter
 *
 * Choosing a category sends a request, and so does typing. Each control
 * includes the value of the other with `hx-include`, so both filters always
 * travel together.
 *
 * Run with: sbt "runMain addinteractivitywithhtmx.Step4CategoryFilterExample"
 */
object Step4CategoryFilterExample {
  val htmxScript: String = "https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js"

  def bookList(books: List[Book]): Dom.Element =
    if (books.isEmpty) p("No books match your search.")
    else ul(books.map(book => li(s"${book.title} by ${book.authors.mkString(", ")} (${book.category})")))

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
          hxTarget    := HxTarget.css("#results"),
          hxInclude   := HxTarget.css("#category")
        ),
        select(
          id        := "category",
          name      := "category",
          hxGet     := "/books",
          hxTrigger := HxTrigger.change,
          hxTarget  := HxTarget.css("#results"),
          hxInclude := HxTarget.css("#q"),
          option(value := "", "All"),
          option(value := "Language", "Language"),
          option(value := "Functional programming", "Functional programming"),
          option(value := "ZIO", "ZIO")
        ),
        div(id := "results", bookList(Books.all))
      )
    )

  def htmlResponse(dom: Dom): Response =
    Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))

  /** Reads a query parameter, turning htmx's `+` back into a space. */
  def queryParam(request: Request, name: String): String =
    request.queryParams.getFirst(name).map(_.replace('+', ' ')).getOrElse("")

  def app(request: Request): Response =
    request.path.encode match {
      case "/"      => htmlResponse(page())
      case "/books" =>
        val books = Books.search(queryParam(request, "q"), queryParam(request, "category"))
        htmlResponse(bookList(books))
      case other => Response.text(Status.NotFound, s"No route for $other")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books?q=&category=Language")
      Client.call(port, "GET", "/books?q=scala&category=Functional+programming")
    }
}
