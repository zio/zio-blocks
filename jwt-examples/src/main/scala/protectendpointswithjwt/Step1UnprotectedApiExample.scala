package protectendpointswithjwt

import zio.http._

import Bookstore._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens: Step 1 — The Unprotected
 * API
 *
 * Serves the bookstore with no authentication: the caller names whose orders to
 * show, and anyone can change a price.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.Step1UnprotectedApiExample"
 */
object Step1UnprotectedApiExample {

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books"))              => listBooks
      case (Method.GET, List("orders"))             => ordersOf(request.queryParams.getFirst("user").getOrElse(""))
      case (Method.PUT, List("books", id, "price")) => setPrice(id, request.body.text)
      case _                                        => notFound(request)
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/orders?user=reader-1024")
      Client.call(port, "PUT", "/books/1/price", "1")
    }
}
