package sendserversentevents

import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Resuming from Last-Event-ID
 *
 * Reads the `Last-Event-ID` header that a reconnecting client sends, streams
 * only the books after that id, asks the client to wait two seconds before it
 * reconnects, and answers `204 No Content` once the client has every book.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step4ResumeExample"
 */
object Step4ResumeExample {
  def lastSeen(request: Request): Int =
    request.headers.rawGet("last-event-id").flatMap(_.toIntOption).getOrElse(0)

  def arrivals(request: Request, intervalMillis: Long): Response = {
    val remaining = arrivalsAfter(lastSeen(request))
    if (remaining.isEmpty) Response(Status.NoContent)
    else eventResponse(paced(remaining.updated(0, remaining.head.retry(2000)), intervalMillis))
  }

  def main(args: Array[String]): Unit =
    StreamingJdkHttp.withServer(arrivals(_, intervalMillis = 200)) { port =>
      FeedClient.watch(port, "/arrivals", lastEventId = Some("4"))
      FeedClient.watch(port, "/arrivals", lastEventId = Some("6"))
    }
}
