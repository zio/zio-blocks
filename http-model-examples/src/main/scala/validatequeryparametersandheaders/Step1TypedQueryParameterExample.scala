package validatequeryparametersandheaders

import zio.http._
import zio.http.schema._

import Bookstore._
import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — One Typed Query
 * Parameter
 *
 * Reads `?limit=` as an `Int`, first as a required parameter and then as an
 * optional one that falls back to a default only when the parameter is missing,
 * never when it is malformed.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.Step1TypedQueryParameterExample"
 */
object Step1TypedQueryParameterExample {
  def listBooks(request: Request): Response =
    request.query[Int]("limit") match {
      case Right(limit) => Response.json(bookListJson.encodeToString(books.take(limit)))
      case Left(error)  => Response.text(Status.BadRequest, error.message)
    }

  def listBooksWithDefault(request: Request): Response =
    optionalQuery[Int](request, "limit") match {
      case Right(limit) => Response.json(bookListJson.encodeToString(books.take(limit.getOrElse(20))))
      case Left(error)  => Response.text(Status.BadRequest, error.message)
    }

  def main(args: Array[String]): Unit = {
    JdkHttp.withServer(listBooks) { port =>
      Client.call(port, "GET", "/books?limit=1")
      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/books?limit=abc")
    }

    val request = Request.get(URL.parse("/books?limit=abc").toOption.get)
    println(request.queryOrElse[Int]("limit", 20))

    JdkHttp.withServer(listBooksWithDefault) { port =>
      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/books?limit=abc")
    }
  }
}
