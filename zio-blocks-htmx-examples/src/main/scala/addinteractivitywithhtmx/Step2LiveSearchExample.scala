package addinteractivitywithhtmx

import scala.concurrent.duration._

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Library._

/**
 * Add Interactivity to a Page with HTMX — Live Search With a Debounce
 *
 * A search box fires `GET /search?q=...` 300 ms after the reader stops typing,
 * and only when the text changed. The server filters the books and answers with
 * a fragment.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step2LiveSearchExample"
 */
object Step2LiveSearchExample {
  val searchBox: Dom =
    input(
      `type`      := "search",
      name        := "q",
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").delay(300.millis).changed,
      hxTarget    := HxTarget.css("#results")
    )

  val page: Dom.Element = shell(searchBox, div(id := "results")(bookList(books)))

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => htmlResponse(page)
      case (Method.GET, List("search")) => htmlResponse(bookList(matching(queryValue(request, "q"), "")))
      case _                            => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    println(searchBox.render)
    println()
    call(app, "/search?q=zio")
    call(app, "/search?q=functional+programming")
    call(app, "/search?q=quantum")
  }
}
