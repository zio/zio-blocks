package sendserversentevents

import servehttpmodelrequests.JdkHttp
import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Streaming the Feed as It
 * Happens
 *
 * Serves one book per second, first through the H-35 adapter, which collects
 * the whole body before sending it, then through the streaming adapter, which
 * sends each event as soon as it is produced. The client prints when each event
 * arrived.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step3LiveFeedExample"
 */
object Step3LiveFeedExample {
  val app: Request => Response =
    _ => eventResponse(paced(arrivalsAfter(0).take(3), intervalMillis = 1000))

  def main(args: Array[String]): Unit = {
    println("== H-35 adapter (JdkHttp)")
    JdkHttp.withServer(app)(port => FeedClient.watch(port, "/arrivals"))
    println("== Streaming adapter (StreamingJdkHttp)")
    StreamingJdkHttp.withServer(app)(port => FeedClient.watch(port, "/arrivals"))
  }
}
