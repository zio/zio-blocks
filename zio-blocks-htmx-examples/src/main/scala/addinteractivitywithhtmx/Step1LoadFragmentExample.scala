package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx._

import Library._

/**
 * Add Interactivity to a Page with HTMX — Load a Fragment With a Button
 *
 * A button with `hx-get` asks the server for the book list and swaps it into
 * the `results` div. The server answers `GET /books` with a fragment only.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step1LoadFragmentExample"
 */
object Step1LoadFragmentExample {
  val loadButton: Dom.Element =
    button(
      hxGet    := "/books",
      hxTarget := HxTarget.css("#results"),
      hxSwap   := HxSwap.InnerHTML
    )("Show all books")

  val page: Dom.Element = shell(loadButton, div(id := "results"))

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)           => htmlResponse(page)
      case (Method.GET, List("books")) => htmlResponse(bookList(books))
      case _                           => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    println(loadButton.render)
    println()
    call(app, "/")
    call(app, "/books")
  }
}
