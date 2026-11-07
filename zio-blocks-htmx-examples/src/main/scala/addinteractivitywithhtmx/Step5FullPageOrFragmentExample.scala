package addinteractivitywithhtmx

import scala.concurrent.duration._
import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import Bookshelf._

/**
 * Add Interactivity to a Page with HTMX — Step 5: One URL, a Full Page or a
 * Fragment
 *
 * The controls push each search into the address bar with `hx-push-url`, so
 * `/search?q=...` must also work when the browser loads it directly. The server
 * checks the `HX-Request` header: an htmx request gets the result list only,
 * and any other request gets the whole page with the controls filled in.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step5FullPageOrFragmentExample"
 */
object Step5FullPageOrFragmentExample {
  def searchBox(query: String): Dom.Element.Void =
    input(
      `type`      := "search",
      name        := "q",
      value       := query,
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
      hxTarget    := HxTarget.css("#results"),
      hxInclude   := HxTarget.css("#filters"),
      hxPushUrl   := HxUrlUpdate(true)
    )

  def categoryFilter(current: String): Dom.Element =
    select(
      name      := "category",
      hxGet     := "/search",
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#filters"),
      hxPushUrl := HxUrlUpdate(true)
    )(
      option(value := "")("All categories") ::
        categories.map(category => option(value := category)(category).when(category == current)(selected))
    )

  def page(query: String, category: String): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        htmxScript
      ),
      body(
        h1("Scala Bookshelf"),
        div(id := "filters", searchBox(query), categoryFilter(category)),
        div(id := "results", resultList(findBooks(query, category)))
      )
    )

  /**
   * True when htmx sent the request to swap part of the page. On a history
   * cache miss htmx 2 sends `HX-Request: true` as well, together with
   * `HX-History-Restore-Request: true`, and then it needs the whole page.
   */
  def wantsFragment(request: Request): Boolean =
    request.header(HxRequest).exists(_.enabled) &&
      !request.header(HxHistoryRestoreRequest).exists(_.enabled)

  def searchPage(request: Request): Response = {
    val query    = request.queryParams.getFirst("q").getOrElse("")
    val category = request.queryParams.getFirst("category").getOrElse("")
    val html     =
      if (wantsFragment(request)) resultList(findBooks(query, category)).render
      else doctype.render + page(query, category).render
    htmlResponse(html).addHeader("vary", "hx-request")
  }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) | (Method.GET, List("search")) => searchPage(request)
      case _                                                => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    val port = serve(app, args)
    Client.call(port, "GET", "/search?q=zio&category=Effects")
    val htmxRequest = Request.get(URL.parse("/search?q=zio&category=Effects").toOption.get).addHeader(HxRequest(true))
    println("htmx request for /search?q=zio&category=Effects ->")
    println(app(htmxRequest).body.asString())
    openInBrowser(port)
  }
}
