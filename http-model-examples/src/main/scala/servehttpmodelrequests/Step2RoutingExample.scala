package servehttpmodelrequests

import zio.http._

import Bookstore._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — Routing a Few
 * Paths
 *
 * Routes GET /books and GET /books/{id} by pattern matching on the method and
 * the path segments, with a 404 fallback for everything else.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * servehttpmodelrequests.Step2RoutingExample"
 */
object Step2RoutingExample {
  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books")) =>
        Response.json(bookListJson.encodeToString(books))
      case (Method.GET, List("books", id)) =>
        books.find(_.id.toString == id) match {
          case Some(book) => Response.json(bookJson.encodeToString(book))
          case None       => Response.text(Status.NotFound, s"No book with id $id")
        }
      case _ =>
        Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/books/1")
      Client.call(port, "GET", "/books/42")
      Client.call(port, "DELETE", "/books/1")
    }
}
