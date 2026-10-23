package addinteractivitywithhtmx

import scala.concurrent.duration._

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers.{HxHistoryRestoreRequest, HxRequest}

import Library._

/**
 * Add Interactivity to a Page with HTMX — Full Page or Fragment
 *
 * One URL, two answers. A request from htmx carries `HX-Request: true` and gets
 * a fragment; a plain browser request, or htmx restoring a page from history,
 * gets the full page with the controls filled in from the query string.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step4FullPageOrFragmentExample"
 */
object Step4FullPageOrFragmentExample {
  def searchBox(query: String): Dom =
    input(
      id        := "q",
      `type`    := "search",
      name      := "q",
      value     := query,
      hxGet     := "/search",
      hxTrigger := HxTrigger("keyup").delay(300.millis).changed,
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#category"),
      hxPushUrl := HxUrlUpdate.Enabled
    )

  def categoryMenu(selectedCategory: String): Dom =
    select(
      id        := "category",
      name      := "category",
      hxGet     := "/search",
      hxTrigger := HxTrigger.change,
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#q"),
      hxPushUrl := HxUrlUpdate.Enabled
    )(
      option(value := "")("All categories"),
      categories.map(c => option(value := c)(c).when(c == selectedCategory)(selected))
    )

  def searchPage(query: String, category: String, found: List[Book]): Dom =
    shell(searchBox(query), categoryMenu(category), div(id := "results")(bookList(found)))

  def search(request: Request): Response = {
    val query    = queryValue(request, "q")
    val category = queryValue(request, "category")
    val found    = matching(query, category)
    val body     = if (wantsFragment(request)) bookList(found) else searchPage(query, category, found)
    htmlResponse(body).addHeaders(Headers("vary" -> "HX-Request"))
  }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => htmlResponse(searchPage("", "", books))
      case (Method.GET, List("search")) => search(request)
      case _                            => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    println("A browser opens /search?q=cats&category=Functional directly:")
    call(app, "/search?q=cats&category=Functional")
    println("htmx sends the same URL with HX-Request: true:")
    call(app, "/search?q=cats&category=Functional", Headers(HxRequest.name -> "true"))
    println("htmx restores the page after the back button:")
    call(
      app,
      "/search?q=cats&category=Functional",
      Headers(HxRequest.name -> "true", HxHistoryRestoreRequest.name -> "true")
    )
  }
}
