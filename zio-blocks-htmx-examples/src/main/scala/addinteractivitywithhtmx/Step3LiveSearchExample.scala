package addinteractivitywithhtmx

import scala.concurrent.duration._
import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Bookshelf._

/**
 * Add Interactivity to a Page with HTMX — Step 3: A Debounced Live Search
 *
 * A search box sends `GET /search?q=...` 300 milliseconds after the reader
 * stops typing, and only when the text has changed. The server answers with the
 * matching books as a fragment.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step3LiveSearchExample"
 */
object Step3LiveSearchExample {
  val searchBox: Dom.Element.Void =
    input(
      `type`      := "search",
      name        := "q",
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
      hxTarget    := HxTarget.css("#results")
    )

  def page: Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        htmxScript
      ),
      body(
        h1("Scala Bookshelf"),
        searchBox,
        div(id := "results", resultList(books))
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => htmlResponse(doctype.render + page.render)
      case (Method.GET, List("search")) =>
        val query = request.queryParams.getFirst("q").getOrElse("")
        htmlResponse(resultList(findBooks(query, "")).render)
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    val port = serve(app, args)
    Client.call(port, "GET", "/search?q=zio")
    Client.call(port, "GET", "/search?q=haskell")
    openInBrowser(port)
  }
}
