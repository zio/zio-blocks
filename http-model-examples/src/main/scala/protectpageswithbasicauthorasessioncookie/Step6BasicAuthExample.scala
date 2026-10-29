package protectpageswithbasicauthorasessioncookie

import zio.blocks.html._
import zio.http._
import protectpageswithbasicauthorasessioncookie.BasicAuth._
import protectpageswithbasicauthorasessioncookie.Roastery._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Asking for
 * Credentials With a 401 Challenge
 *
 * Prints the challenge, then serves the staff page and the export behind
 * `basicAuth` and calls them with and without credentials.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step6BasicAuthExample"
object Step6BasicAuthExample {
  def main(args: Array[String]): Unit = {
    println(basicChallenge.header(Header.WWWAuthenticate).map(_.renderedValue))

    def basicApp(request: Request): Response =
      (request.method, request.path.segments.toList) match {
        case (Method.GET, Nil)                => home
        case (Method.GET, List("admin"))      => basicAuth((user, _) => ordersPage(p(s"Signed in as $user")))(request)
        case (Method.GET, List("orders.csv")) => basicAuth((_, _) => ordersCsv)(request)
        case _                                => notFound(request)
      }

    def basic(username: String, password: String): String =
      Header.Authorization.Basic(username, password).renderedValue

    JdkHttp.withServer(basicApp) { port =>
      Client.call(port, "GET", "/admin")
      Client.call(port, "GET", "/admin", authorization = basic("grace", "coffee and cake"))
      Client.call(port, "GET", "/admin", authorization = basic("grace", "tea and biscuits at four"))
      Client.call(port, "GET", "/orders.csv", authorization = basic("ada", "correct horse battery staple"))
    }
  }
}
