package buildalivedashboardwithdatastar

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.html._
import zio.blocks.streams.Stream
import zio.http._
import zio.http.datastar._

import Glue._

/**
 * Build a Live Counter and Dashboard with Datastar — Step 5: A Live Dashboard
 *
 * The page opens one `GET /dashboard/stream` when it loads, and the server
 * keeps that response open, sending a new `patchElements` event with the whole
 * table every second. The program also shows what the server sees when the
 * client goes away.
 *
 * Run with: cd datastar-examples && sbt "runMain
 * buildalivedashboardwithdatastar.Step5LiveDashboardExample"
 */
object Step5LiveDashboardExample {

  def dashboard(tick: Int): Dom.Element = {
    val header = tr(th("City"), th("Temperature"), th("Wind"), th("Humidity"))
    val rows   = Weather.readings(tick).map { reading =>
      tr(td(reading.city), td(s"${reading.celsius} °C"), td(s"${reading.windKmh} km/h"), td(s"${reading.humidity}%"))
    }
    table(id := "dashboard", (header +: rows)*)
  }

  def page: Dom.Element =
    Glue.page("Weather Station")(
      div(dataInit := js"@get('/dashboard/stream')", dashboard(0))
    )

  /**
   * One `patchElements` event per tick, `intervalMillis` apart, until the
   * client leaves.
   */
  def ticks(intervalMillis: Long, sent: AtomicInteger): Stream[Nothing, String] =
    Stream
      .fromIterable(LazyList.from(1))
      .tapEach { _ =>
        Thread.sleep(intervalMillis)
        sent.incrementAndGet()
        ()
      }
      .map(tick => DatastarEvent.patchElements(dashboard(tick)).renderSSE)

  def newApp(intervalMillis: Long = 1000, sent: AtomicInteger = new AtomicInteger(0)): Request => Response =
    request =>
      (request.method, request.path.segments.toList) match {
        case (Method.GET, Nil)                         => htmlResponse(doctype.render + page.render)
        case (Method.GET, List("dashboard", "stream")) =>
          eventResponse(
            ticks(intervalMillis, sent).ensuring(println(s"server: stream closed after ${sent.get} events"))
          )
        case _ => notFound(request)
      }

  def main(args: Array[String]): Unit =
    if (args.nonEmpty) openInBrowser(serve(newApp(), args))
    else
      StreamingJdkHttp.withServer(newApp(intervalMillis = 300)) { port =>
        Client.watch(port, "/dashboard/stream", maxLines = 6)
        println("client: disconnected")
        Thread.sleep(1500)
      }
}
