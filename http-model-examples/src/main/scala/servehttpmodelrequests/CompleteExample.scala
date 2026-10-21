package servehttpmodelrequests

import zio.http._

import Bookstore._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — Complete Example
 *
 * A tiny bookstore served by the JDK HTTP server through the http-model
 * adapter: an HTML catalog at /, a JSON list at /books with an optional
 * `?limit=`, one book at /books/{id}, JSON input at POST /books, and a 404
 * fallback. The program calls every route with `java.net.http.HttpClient` and
 * stops the server when it is done.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * servehttpmodelrequests.CompleteExample"
 */
object CompleteExample {
  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  def listBooks(request: Request): Response =
    request.queryParams.getFirst("limit") match {
      case None      => Response.json(bookListJson.encodeToString(books))
      case Some(raw) =>
        raw.toIntOption.filter(_ >= 0) match {
          case Some(limit) => Response.json(bookListJson.encodeToString(books.take(limit)))
          case None        => Response.text(Status.BadRequest, s"limit must be a non-negative integer, got '$raw'")
        }
    }

  def getBook(id: String): Response =
    books.find(_.id.toString == id) match {
      case Some(book) => Response.json(bookJson.encodeToString(book))
      case None       => Response.text(Status.NotFound, s"No book with id $id")
    }

  def addBook(request: Request): Response =
    bookJson.decode(request.body.toArray) match {
      case Right(book) => Response.json(Status.Created, bookJson.encodeToString(book))
      case Left(error) => Response.text(Status.BadRequest, s"Invalid book: ${error.message}")
    }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) =>
        Response.ok(Body.fromArray(catalogPage(books).render.getBytes("UTF-8"), ContentType.`text/html`))
      case (Method.GET, List("books"))     => listBooks(request)
      case (Method.GET, List("books", id)) => getBook(id)
      case (Method.POST, List("books"))    => addBook(request)
      case _                               => notFound(request)
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/")
      Client.call(port, "GET", "/books?limit=2")
      Client.call(port, "GET", "/books/1")
      Client.call(port, "HEAD", "/books/1")
      Client.call(port, "GET", "/books/42")
      Client.call(
        port,
        "POST",
        "/books",
        """{"id":4,"title":"Scala with Cats","authors":["Noel Welsh","Dave Gurnell"],"year":2017}"""
      )
      Client.call(port, "POST", "/books", """{"id":4,"title":"Scala with Cats"}""")
      Client.call(port, "GET", "/authors")
    }
}
