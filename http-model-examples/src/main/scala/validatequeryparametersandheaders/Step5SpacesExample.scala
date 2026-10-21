package validatequeryparametersandheaders

import com.sun.net.httpserver.{HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress}
import zio.http._
import zio.http.schema._

import Bookstore._
import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — Values with
 * Spaces and Plus Signs
 *
 * Shows that `%20` decodes to a space but `+` stays a plus sign, and serves the
 * same handler through a wrapper that decodes the raw query string as form
 * data, so that `+` from an HTML form becomes a space.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.Step5SpacesExample"
 */
object Step5SpacesExample {
  def byAuthor(request: Request): Response =
    request.query[String]("author") match {
      case Right(author) => Response.json(bookListJson.encodeToString(books.filter(_.authors.contains(author))))
      case Left(error)   => badRequest(List(Problem.query(error)))
    }

  def formQueryHandler(app: Request => Response): HttpHandler = exchange => {
    val rawQuery = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
    val params   = Form.fromString(rawQuery).entries.foldLeft(QueryParams.empty) { case (acc, (key, value)) =>
      acc.add(key, value)
    }
    JdkHttp.handler(request => app(request.updateUrl(_.copy(queryParams = params)))).handle(exchange)
  }

  def main(args: Array[String]): Unit = {
    JdkHttp.withServer(byAuthor) { port =>
      Client.call(port, "GET", "/books?author=Martin%20Odersky")
      Client.call(port, "GET", "/books?author=Martin+Odersky")
    }

    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
    server.createContext("/", formQueryHandler(byAuthor))
    server.start()
    try Client.call(server.getAddress.getPort, "GET", "/books?author=Martin+Odersky")
    finally server.stop(0)
  }
}
