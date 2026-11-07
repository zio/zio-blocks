package protectpageswithbasicauthorasessioncookie

import zio.http._
import protectpageswithbasicauthorasessioncookie.BasicAuth._
import protectpageswithbasicauthorasessioncookie.Logout._
import protectpageswithbasicauthorasessioncookie.Roastery._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Putting It
 * Together
 *
 * Serves the whole roastery on port 8080 until Ctrl+C: the session-cookie pages
 * at `/login`, `/admin` and `/logout`, and `/orders.csv` behind Basic
 * authentication.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.CompleteExample"
object CompleteExample {
  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("orders.csv")) => basicAuth((_, _) => ordersCsv)(request)
      case _                                => staffApp(request)
    }

  def main(args: Array[String]): Unit = {
    JdkHttp.start(app, 8080)
    println("Serving http://localhost:8080/ (Ctrl+C to stop)")
    Thread.currentThread.join() // Block the main thread; the server runs on its own threads.
  }
}
