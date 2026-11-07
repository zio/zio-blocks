package addinteractivitywithhtmx

import scala.concurrent.duration._
import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Bookshelf._

/**
 * Add Interactivity to a Page with HTMX — Step 4: A Category Filter
 *
 * Adds a category drop-down next to the search box. Both controls send
 * `GET /search` with `hx-include` pointing at their shared `#filters`
 * container, so every request carries the search text and the category.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step4CategoryFilterExample"
 */
object Step4CategoryFilterExample {
  val searchBox: Dom.Element.Void =
    input(
      `type`      := "search",
      name        := "q",
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
      hxTarget    := HxTarget.css("#results"),
      hxInclude   := HxTarget.css("#filters")
    )

  val categoryFilter: Dom.Element =
    select(
      name      := "category",
      hxGet     := "/search",
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#filters")
    )(
      option(value := "")("All categories") :: categories.map(category => option(value := category)(category))
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
        div(id := "filters", searchBox, categoryFilter),
        div(id := "results", resultList(books))
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => htmlResponse(doctype.render + page.render)
      case (Method.GET, List("search")) =>
        val query    = request.queryParams.getFirst("q").getOrElse("")
        val category = request.queryParams.getFirst("category").getOrElse("")
        htmlResponse(resultList(findBooks(query, category)).render)
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    val port = serve(app, args)
    Client.call(port, "GET", "/search?q=&category=Effects")
    Client.call(port, "GET", "/search?q=scala&category=Language")
    openInBrowser(port)
  }
}
