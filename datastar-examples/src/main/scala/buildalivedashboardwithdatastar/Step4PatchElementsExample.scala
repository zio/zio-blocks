package buildalivedashboardwithdatastar

import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — Step 4: Swapping a
 * Fragment
 *
 * The answer to a click now has two events: a `patchSignals` that sets the
 * counter and a `patchElements` that replaces the `#latest` paragraph with the
 * reading for the selected city.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.Step4PatchElementsExample"
 */
object Step4PatchElementsExample {
  val readings = Signal[Int]("readings")
  val city     = Signal[String]("city")

  final case class Signals(readings: Int, city: String)
  object Signals {
    implicit val schema: Schema[Signals] = Schema.derived
  }

  /**
   * The paragraph Datastar swaps in; it keeps the id, so the next patch finds
   * it.
   */
  def latest(reading: Reading): Dom.Element =
    p(
      id := "latest",
      s"${reading.city}: ${reading.celsius} °C, wind ${reading.windKmh} km/h, humidity ${reading.humidity}%"
    )

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(
        dataSignals(readings := 0, city := "Oslo"),
        select(dataBind(city), option("Oslo"), option("Lisbon"), option("Nairobi")),
        p("Readings taken: ", span(dataText := readings)),
        button(dataOn.click := js"@post('/reading')", "Take a reading"),
        p(id                := "latest", "No reading yet.")
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)              => htmlResponse(doctype.render + page.render)
      case (Method.POST, List("reading")) =>
        readSignals[Signals](request) match {
          case Right(signals) =>
            val taken = signals.readings + 1
            eventResponse(
              DatastarEvent.patchSignals(readings := taken).renderSSE,
              DatastarEvent.patchElements(latest(Weather.reading(signals.city, taken))).renderSSE
            )
          case Left(error) => Response.text(Status.BadRequest, error)
        }
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit =
    if (args.nonEmpty) openInBrowser(serve(app, args))
    else
      StreamingJdkHttp.withServer(app) { port =>
        Client.call(port, "POST", "/reading", """{"readings":4,"city":"Oslo"}""")
      }
}
