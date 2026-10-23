package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Bookshelf._

/**
 * Add Interactivity to a Page with HTMX — Step 2: Your First htmx Request
 *
 * A button with `hx-get` and `hx-target` asks the server for the `/books`
 * fragment and swaps it into the `#results` element, without reloading the
 * page.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step2FirstHtmxRequestExample"
 */
object Step2FirstHtmxRequestExample {
  val showAllButton: Dom.Element =
    button(hxGet := "/books", hxTarget := HxTarget.css("#results"))("Show all books")

  def page: Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        htmxScript
      ),
      body(
        h1("Scala Bookshelf"),
        showAllButton,
        div(id := "results", p("Click the button to load the books."))
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)           => htmlResponse(doctype.render + page.render)
      case (Method.GET, List("books")) => htmlResponse(resultList(books).render)
      case _                           => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    val port = serve(app, args)
    Client.call(port, "GET", "/books")
    openInBrowser(port)
  }
}
