package sendserversentevents

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Complete Example
 *
 * A new-arrivals feed for a Scala bookshop: an HTML page at / that listens with
 * `EventSource`, and an event stream at /arrivals that sends one book every two
 * seconds, resumes from `Last-Event-ID`, answers 204 once the client has every
 * book, and logs when each stream opens and closes. Watch it with
 * `curl -N http://localhost:8080/arrivals` or open http://localhost:8080/ in a
 * browser.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.CompleteExample"
 */
object CompleteExample {
  private val opened = new AtomicInteger(0)

  def arrivals(request: Request): Response = {
    val number    = opened.incrementAndGet()
    val lastSeen  = request.headers.rawGet("last-event-id").flatMap(_.toIntOption).getOrElse(0)
    val remaining = arrivalsAfter(lastSeen)
    if (remaining.isEmpty) {
      println(s"stream $number: the client has every book, 204")
      Response(Status.NoContent)
    } else {
      println(s"stream $number: opened after event $lastSeen")
      val events = paced(remaining.updated(0, remaining.head.retry(2000)), intervalMillis = 2000)
        .ensuring(println(s"stream $number: closed"))
      eventResponse(events)
    }
  }

  val app: Request => Response = request =>
    (request.method, request.path.encode) match {
      case (Method.GET, "/") =>
        Response.ok(
          Body.fromArray(Step5BrowserPageExample.page.getBytes(StandardCharsets.UTF_8), ContentType.`text/html`)
        )
      case (Method.GET, "/arrivals") => arrivals(request)
      case _                         => Response.notFound
    }

  def main(args: Array[String]): Unit = {
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    StreamingJdkHttp.start(app, port)
    println(s"Serving http://localhost:$port/ and http://localhost:$port/arrivals (Ctrl+C to stop)")
    Thread.currentThread().join()
  }
}
