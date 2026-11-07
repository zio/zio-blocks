package servehttpmodelrequests

import zio.http._

import Bookstore._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — Serving an HTML
 * Page
 *
 * Renders the catalog page with the html module and serves it as text/html.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * servehttpmodelrequests.Step4HtmlPageExample"
 */
object Step4HtmlPageExample {
  def htmlResponse(page: String): Response =
    Response.ok(Body.fromArray(page.getBytes("UTF-8"), ContentType.`text/html`))

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) => htmlResponse(catalogPage(books).render)
      case _                 =>
        Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/")
    }
}
