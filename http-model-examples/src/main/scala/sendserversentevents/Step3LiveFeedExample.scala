package sendserversentevents

import servehttpmodelrequests.JdkHttp
import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Streaming the Feed as It
 * Happens
 *
 * Serves one book per second, first through `JdkHttp`, the adapter from How to
 * Serve http-model Requests with the JDK HTTP Server, which collects the whole
 * body before sending it, then through the streaming adapter, which sends each
 * event as soon as it is produced. The client prints when each event arrived.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step3LiveFeedExample"
 */
object Step3LiveFeedExample {
  val app: Request => Response =
    _ => eventResponse(paced(arrivalsAfter(0).take(3), intervalMillis = 1000))

  def main(args: Array[String]): Unit = {
    println("== JdkHttp (collects the body first)")
    JdkHttp.withServer(app)(port => FeedClient.watch(port, "/arrivals"))
    println("== Streaming adapter (StreamingJdkHttp)")
    StreamingJdkHttp.withServer(app)(port => FeedClient.watch(port, "/arrivals"))
  }
}
