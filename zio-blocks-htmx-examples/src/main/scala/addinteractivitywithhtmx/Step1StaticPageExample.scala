package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._

import Bookshelf._

/**
 * Add Interactivity to a Page with HTMX — Step 1: A Static Page That Loads htmx
 *
 * Serves the bookshelf as an ordinary server-rendered page whose head loads the
 * htmx script. Nothing on the page is interactive yet.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.Step1StaticPageExample"
 */
object Step1StaticPageExample {
  def page: Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        htmxScript
      ),
      body(
        h1("Scala Bookshelf"),
        div(id := "results", resultList(books))
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) => htmlResponse(doctype.render + page.render)
      case _                 => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    val port = serve(app, args)
    Client.call(port, "GET", "/")
    openInBrowser(port)
  }
}
