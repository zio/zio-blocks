package sendserversentevents

import java.util.concurrent.atomic.AtomicInteger
import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Noticing When a Client
 * Leaves
 *
 * Counts the events the server produces and logs, with `Stream#ensuring`, when
 * the stream closes. The client reads two events and disconnects. The first
 * write after that still succeeds; the server notices on the second one, about
 * two seconds later.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step6DisconnectExample"
 */
object Step6DisconnectExample {
  def app(request: Request): Response = {
    val produced = new AtomicInteger(0)
    val events   = paced(arrivalsAfter(0), intervalMillis = 1000)
      .tapEach(_ => produced.incrementAndGet())
      .ensuring(println(s"server: stream closed after producing ${produced.get} events"))
    eventResponse(events)
  }

  def main(args: Array[String]): Unit =
    StreamingJdkHttp.withServer(app) { port =>
      FeedClient.watch(port, "/arrivals", maxEvents = 2)
      println("client: disconnected")
      Thread.sleep(3500)
    }
}
