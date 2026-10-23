package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

/**
 * Add Interactivity to a Page with HTMX — Step 2: Fetch a Fragment with hx-get
 *
 * The page starts with an empty results area and a button. Clicking the button
 * asks the server for `/books` and puts the HTML it returns into `#results`.
 * The server answers with a fragment, not a whole page.
 *
 * Run with: sbt "runMain addinteractivitywithhtmx.Step2FragmentEndpointExample"
 */
object Step2FragmentEndpointExample {
  val htmxScript: String = "https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js"

  def bookList(books: List[Book]): Dom.Element =
    ul(books.map(book => li(s"${book.title} by ${book.authors.mkString(", ")}")))

  def page(): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Book search"),
        script(src := htmxScript)
      ),
      body(
        h1("Scala books"),
        button(
          hxGet    := "/books",
          hxTarget := HxTarget.css("#results"),
          "Show all books"
        ),
        div(id := "results")
      )
    )

  def htmlResponse(dom: Dom): Response =
    Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))

  def app(request: Request): Response =
    request.path.encode match {
      case "/"      => htmlResponse(page())
      case "/books" => htmlResponse(bookList(Books.all))
      case other    => Response.text(Status.NotFound, s"No route for $other")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/")
      Client.call(port, "GET", "/books")
    }
}
