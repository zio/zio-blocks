package validatequeryparametersandheaders

import java.util.UUID
import zio.blocks.schema.Schema
import zio.http._
import zio.http.schema._

import Bookstore._
import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — Complete
 * Example
 *
 * `GET /books` with paging (`limit` 1 to 100, `offset` 0 or more), a filter by
 * author and year decoded with a derived `QueryCodec`, and an optional
 * `X-Request-Id` header. Every bad parameter and header is reported in one JSON
 * 400 response.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.CompleteExample"
 */
object CompleteExample {
  final case class Paging(limit: Int, offset: Int)

  final case class BookFilter(author: Option[String], year: Option[Int])

  object BookFilter {
    implicit val schema: Schema[BookFilter] = Schema.derived

    val codec: QueryCodec[BookFilter] = Schema[BookFilter].derive(DefaultQueryFormat)
  }

  def paging(request: Request): Checked[Paging] = {
    val limit = checkedQuery[Int](request, "limit")
      .map(_.getOrElse(20))
      .flatMap(check[Int]("limit", "must be between 1 and 100")(n => n >= 1 && n <= 100))
    val offset = checkedQuery[Int](request, "offset")
      .map(_.getOrElse(0))
      .flatMap(check[Int]("offset", "must not be negative")(_ >= 0))
    both(limit, offset).map { case (limit, offset) => Paging(limit, offset) }
  }

  def filter(request: Request): Checked[BookFilter] =
    BookFilter.codec.decode(request.queryParams).left.map(Problem.decoded("filter"))

  def requestId(request: Request): Checked[Option[UUID]] =
    request.headers.header[UUID]("X-Request-Id") match {
      case Right(id)                    => Right(Some(id))
      case Left(HeaderError.Missing(_)) => Right(None)
      case Left(error)                  => Left(List(Problem.header(error)))
    }

  def matches(filter: BookFilter)(book: Book): Boolean =
    filter.author.forall(book.authors.contains) && filter.year.forall(_ == book.year)

  def listBooks(request: Request): Response =
    both(both(paging(request), filter(request)), requestId(request)) match {
      case Right(((page, bookFilter), _)) =>
        val found = books.filter(matches(bookFilter)).slice(page.offset, page.offset + page.limit)
        Response.json(bookListJson.encodeToString(found))
      case Left(problems) => badRequest(problems)
    }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books")) => listBooks(request)
      case _                           =>
        Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
    }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(app) { port =>
      Client.call(port, "GET", "/books?author=Martin%20Odersky")
      Client.call(port, "GET", "/books?limit=1&offset=1")
      Client.call(port, "GET", "/books?limit=0&offset=-1&year=abc")
      HeaderClient.get(port, "/books?year=2024", "X-Request-Id" -> "3f2504e0-4f89-11d3-9a0c-0305e82c3301")
      HeaderClient.get(port, "/books?limit=abc", "X-Request-Id" -> "42")
    }
}
