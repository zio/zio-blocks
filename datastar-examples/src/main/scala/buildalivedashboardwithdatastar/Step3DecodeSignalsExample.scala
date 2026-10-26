package buildalivedashboardwithdatastar

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — Step 3: Reading the
 * Signals
 *
 * The server stops counting by itself. It decodes the signals the browser sends
 * into a case class with the schema JSON codec, and answers with the next
 * value. A `GET` carries the same JSON in the `datastar` query parameter.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.Step3DecodeSignalsExample"
 */
object Step3DecodeSignalsExample {
  val readings = Signal[Int]("readings")
  val city     = Signal[String]("city")

  final case class Signals(readings: Int, city: String)
  object Signals {
    implicit val schema: Schema[Signals] = Schema.derived
  }

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(
        dataSignals(readings := 0, city := "Oslo"),
        select(dataBind(city), option("Oslo"), option("Lisbon"), option("Nairobi")),
        p("Readings taken: ", span(dataText := readings)),
        button(dataOn.click := js"@post('/reading')", "Take a reading")
      )
    )

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)              => htmlResponse(doctype.render + page.render)
      case (Method.POST, List("reading")) =>
        readSignals[Signals](request) match {
          case Right(signals) => eventResponse(DatastarEvent.patchSignals(readings := signals.readings + 1).renderSSE)
          case Left(error)    => Response.text(Status.BadRequest, error)
        }
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit =
    if (args.nonEmpty) openInBrowser(serve(app, args))
    else
      StreamingJdkHttp.withServer(app) { port =>
        Client.call(port, "POST", "/reading", """{"readings":4,"city":"Oslo"}""")
        Client.call(port, "POST", "/reading", """{"readings":4,"city":"Oslo","extra":true}""")
        Client.call(port, "POST", "/reading", """{"readings":"four"}""")
        println(
          readSignals[Signals](
            Request.get(
              URL
                .parse(
                  "/reading?datastar=" + URLEncoder.encode("""{"readings":7,"city":"Lisbon"}""", StandardCharsets.UTF_8)
                )
                .toOption
                .get
            )
          )
        )
      }
}
