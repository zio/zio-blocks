package validatequeryparametersandheaders

import java.util.UUID
import zio.http._
import zio.http.schema._

import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — Typed Headers
 *
 * Reads an optional `X-Request-Id` header as a `UUID`, answering 400 when it is
 * malformed, and the `Accept-Language` header with the typed
 * `Header.AcceptLanguage` codec, falling back to English.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.Step4HeadersExample"
 */
object Step4HeadersExample {
  def requestId(request: Request): Checked[Option[UUID]] =
    request.headers.header[UUID]("X-Request-Id") match {
      case Right(id)                    => Right(Some(id))
      case Left(HeaderError.Missing(_)) => Right(None)
      case Left(error)                  => Left(List(Problem.header(error)))
    }

  def language(request: Request): String =
    request.headers.header(Header.AcceptLanguage) match {
      case Right(accept) => accept.languages.headOption.fold("en")(_.tag)
      case Left(_)       => "en"
    }

  def catalog(request: Request): Response =
    requestId(request) match {
      case Right(id)      => Response.text(s"Catalog in ${language(request)} for request ${id.getOrElse("(none)")}")
      case Left(problems) => badRequest(problems)
    }

  def main(args: Array[String]): Unit = {
    val request = Request
      .get(URL.parse("/books").toOption.get)
      .addHeader("Accept-Language", "de-CH, en;q=0.8")
    println(request.headerOrElse[String]("Accept-Language", "en"))
    println(request.headers.header(Header.AcceptLanguage))

    JdkHttp.withServer(catalog) { port =>
      HeaderClient.get(port, "/books")
      HeaderClient.get(
        port,
        "/books",
        "X-Request-Id"    -> "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        "Accept-Language" -> "de-CH, en;q=0.8"
      )
      HeaderClient.get(port, "/books", "X-Request-Id" -> "42")
    }
  }
}
