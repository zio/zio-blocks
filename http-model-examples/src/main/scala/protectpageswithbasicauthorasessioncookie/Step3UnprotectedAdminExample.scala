package protectpageswithbasicauthorasessioncookie

import zio.blocks.html._
import zio.http._
import protectpageswithbasicauthorasessioncookie.Roastery._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — The Roastery Admin
 * Area
 *
 * Serves the unprotected roastery and shows that both the staff page and the
 * export answer with every customer's order.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step3UnprotectedAdminExample"
object Step3UnprotectedAdminExample {
  def main(args: Array[String]): Unit = {
    def unprotectedApp(request: Request): Response =
      (request.method, request.path.segments.toList) match {
        case (Method.GET, Nil)                => home
        case (Method.GET, List("admin"))      => ordersPage(p("Anyone can read this page."))
        case (Method.GET, List("orders.csv")) => ordersCsv
        case _                                => notFound(request)
      }

    JdkHttp.withServer(unprotectedApp) { port =>
      Client.call(port, "GET", "/admin")
      Client.call(port, "GET", "/orders.csv")
    }
  }
}
