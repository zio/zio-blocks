package addinteractivitywithhtmx

import scala.concurrent.duration._

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Library._

/**
 * Add Interactivity to a Page with HTMX — the complete bookshelf
 *
 * A live-search bookshelf: a debounced search box and a category menu that
 * update the results without a page reload, push the query into the address
 * bar, and fall back to a full page when the request does not come from htmx.
 * The server stays up until you press Ctrl-C; open the printed address in a
 * browser.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.CompleteExample"
 */
object CompleteExample {
  def searchBox(query: String): Dom =
    input(
      id          := "q",
      `type`      := "search",
      name        := "q",
      value       := query,
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").delay(300.millis).changed,
      hxTarget    := HxTarget.css("#results"),
      hxInclude   := HxTarget.css("#category"),
      hxSync      := HxSync(HxTarget.This, HxSyncStrategy.Replace),
      hxPushUrl   := HxUrlUpdate.Enabled
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
    val port   = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    val server = JdkHttp.start(app, port)
    println(s"Scala Bookshelf is running at http://localhost:${server.getAddress.getPort}/ (Ctrl-C to stop)")
    Thread.currentThread.join()
  }
}
