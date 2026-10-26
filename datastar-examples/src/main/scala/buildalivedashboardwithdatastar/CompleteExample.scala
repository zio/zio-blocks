package buildalivedashboardwithdatastar

import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.blocks.streams.Stream
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — the complete example
 *
 * One page with a "Take a reading" button and a live dashboard. The button
 * `POST`s the signals; the server decodes them and answers with a
 * `patchSignals` and a `patchElements` event. The dashboard opens one
 * `GET /dashboard/stream` when the page loads and receives a new table every
 * second.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.CompleteExample", then open
 * http://localhost:8080/
 */
object CompleteExample {
  val readings = Signal[Int]("readings")
  val city     = Signal[String]("city")

  final case class Signals(readings: Int, city: String)
  object Signals {
    implicit val schema: Schema[Signals] = Schema.derived
  }

  def latest(reading: Reading): Dom.Element =
    p(
      id := "latest",
      s"${reading.city}: ${reading.celsius} °C, wind ${reading.windKmh} km/h, humidity ${reading.humidity}%"
    )

  def dashboard(tick: Int): Dom.Element = {
    val header = tr(th("City"), th("Temperature"), th("Wind"), th("Humidity"))
    val rows   = Weather.readings(tick).map { reading =>
      tr(td(reading.city), td(s"${reading.celsius} °C"), td(s"${reading.windKmh} km/h"), td(s"${reading.humidity}%"))
    }
    table(id := "dashboard", (header +: rows)*)
  }

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(
        dataSignals(readings := 0, city := "Oslo"),
        select(dataBind(city), option("Oslo"), option("Lisbon"), option("Nairobi")),
        p("Readings taken: ", span(dataText := readings)),
        button(dataOn.click := js"@post('/reading')", "Take a reading"),
        p(id                := "latest", "No reading yet.")
      ),
      div(dataInit := js"@get('/dashboard/stream')", dashboard(0))
    )

  def ticks(intervalMillis: Long): Stream[Nothing, String] =
    Stream
      .fromIterable(LazyList.from(1))
      .tapEach { _ =>
        Thread.sleep(intervalMillis)
        ()
      }
      .map(tick => DatastarEvent.patchElements(dashboard(tick)).renderSSE)

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
      case (Method.GET, List("dashboard", "stream")) => eventResponse(ticks(1000))
      case _                                         => notFound(request)
    }

  def main(args: Array[String]): Unit =
    openInBrowser(serve(app, args))
}
