package addinteractivitywithhtmx

import scala.concurrent.duration._

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Library._

/**
 * Add Interactivity to a Page with HTMX — Filter by Category
 *
 * A category menu next to the search box. Each control uses `hx-include` to
 * send the other control's value, so one request always carries both.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step3CategoryFilterExample"
 */
object Step3CategoryFilterExample {
  val searchBox: Dom =
    input(
      id        := "q",
      `type`    := "search",
      name      := "q",
      hxGet     := "/search",
      hxTrigger := HxTrigger("keyup").delay(300.millis).changed,
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#category")
    )

  val categoryMenu: Dom.Element =
    select(
      id        := "category",
      name      := "category",
      hxGet     := "/search",
      hxTrigger := HxTrigger.change,
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#q")
    )(
      option(value := "")("All categories"),
      categories.map(c => option(value := c)(c))
    )

  val page: Dom.Element = shell(searchBox, categoryMenu, div(id := "results")(bookList(books)))

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => htmlResponse(page)
      case (Method.GET, List("search")) =>
        htmlResponse(bookList(matching(queryValue(request, "q"), queryValue(request, "category"))))
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    println(categoryMenu.render)
    println()
    call(app, "/search?q=&category=Functional")
    call(app, "/search?q=scala&category=Language")
    call(app, "/search?q=zio&category=Language")
  }
}
