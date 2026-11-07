package buildalivedashboardwithdatastar

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.html._
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — Step 2: A Button That
 * Posts
 *
 * The page gets a button. Clicking it makes Datastar `POST /reading`; the
 * server counts the click in memory and answers with a `patchSignals` event
 * that sets the signal.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.Step2CounterExample"
 */
object Step2CounterExample {
  val readings = Signal[Int]("readings")

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(
        dataSignals(readings := 0),
        p("Readings taken: ", span(dataText := readings)),
        button(dataOn.click := js"@post('/reading')", "Take a reading")
      )
    )

  def newApp(): Request => Response = {
    val taken = new AtomicInteger(0)
    request =>
      (request.method, request.path.segments.toList) match {
        case (Method.GET, Nil)              => htmlResponse(doctype.render + page.render)
        case (Method.POST, List("reading")) =>
          eventResponse(DatastarEvent.patchSignals(readings := taken.incrementAndGet()).renderSSE)
        case _ => notFound(request)
      }
  }

  def main(args: Array[String]): Unit =
    if (args.nonEmpty) openInBrowser(serve(newApp(), args))
    else
      StreamingJdkHttp.withServer(newApp()) { port =>
        Client.call(port, "POST", "/reading", """{"readings":0}""")
        Client.call(port, "POST", "/reading", """{"readings":0}""")
      }
}
