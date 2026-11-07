package validatequeryparametersandheaders

import zio.http._

import Bookstore._
import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — A JSON 400 That
 * Names Every Problem
 *
 * Reads `?limit=` and `?offset=`, checks their ranges after decoding, and
 * answers 400 with a JSON body that lists every bad parameter at once.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.Step2BadRequestExample"
 */
object Step2BadRequestExample {
  final case class Paging(limit: Int, offset: Int)

  def paging(request: Request): Checked[Paging] = {
    val limit = checkedQuery[Int](request, "limit")
      .map(_.getOrElse(20))
      .flatMap(check[Int]("limit", "must be between 1 and 100")(n => n >= 1 && n <= 100))
    val offset = checkedQuery[Int](request, "offset")
      .map(_.getOrElse(0))
      .flatMap(check[Int]("offset", "must not be negative")(_ >= 0))
    both(limit, offset).map { case (limit, offset) => Paging(limit, offset) }
  }

  def listBooks(request: Request): Response =
    paging(request) match {
      case Right(page)    => Response.json(bookListJson.encodeToString(books.slice(page.offset, page.offset + page.limit)))
      case Left(problems) => badRequest(problems)
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(listBooks) { port =>
      Client.call(port, "GET", "/books?limit=1&offset=2")
      Client.call(port, "GET", "/books?limit=abc&offset=xyz")
      Client.call(port, "GET", "/books?limit=500&offset=-1")
    }
}
