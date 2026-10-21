package servehttpmodelrequests

import zio.http._

import Bookstore._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — Reading Query
 * Parameters and a JSON Body
 *
 * Reads `?limit=` from the query string and decodes a JSON request body with
 * the codec derived from `Book`'s schema, answering 400 on bad input.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * servehttpmodelrequests.Step3QueryAndJsonExample"
 */
object Step3QueryAndJsonExample {
  def listBooks(request: Request): Response =
    request.queryParams.getFirst("limit") match {
      case None      => Response.json(bookListJson.encodeToString(books))
      case Some(raw) =>
        raw.toIntOption.filter(_ >= 0) match {
          case Some(limit) => Response.json(bookListJson.encodeToString(books.take(limit)))
          case None        => Response.text(Status.BadRequest, s"limit must be a non-negative integer, got '$raw'")
        }
    }

  def addBook(request: Request): Response =
    bookJson.decode(request.body.toArray) match {
      case Right(book) => Response.json(Status.Created, bookJson.encodeToString(book))
      case Left(error) => Response.text(Status.BadRequest, s"Invalid book: ${error.message}")
    }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books"))  => listBooks(request)
      case (Method.POST, List("books")) => addBook(request)
      case _                            =>
        Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books?limit=1")
      Client.call(port, "GET", "/books?limit=all")
      Client.call(
        port,
        "POST",
        "/books",
        """{"id":4,"title":"Scala with Cats","authors":["Noel Welsh","Dave Gurnell"],"year":2017}"""
      )
      Client.call(port, "POST", "/books", """{"id":4,"title":"Scala with Cats"}""")
      Client.call(port, "POST", "/books", """{"id":"four"}""")
    }
}
