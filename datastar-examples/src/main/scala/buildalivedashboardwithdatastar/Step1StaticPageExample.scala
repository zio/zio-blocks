package buildalivedashboardwithdatastar

import zio.blocks.html._
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — Step 1: A Page with
 * Signals
 *
 * Serves a page that loads Datastar, declares one signal and shows it with
 * `dataText`. Nothing on the page can change the signal yet.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.Step1StaticPageExample"
 */
object Step1StaticPageExample {
  val readings = Signal[Int]("readings")

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(
        dataSignals(readings := 0),
        p("Readings taken: ", span(dataText := readings))
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) => htmlResponse(doctype.render + page.render)
      case _                 => notFound(request)
    }

  def main(args: Array[String]): Unit =
    if (args.nonEmpty) openInBrowser(serve(app, args))
    else StreamingJdkHttp.withServer(app)(port => Client.call(port, "GET", "/"))
}
