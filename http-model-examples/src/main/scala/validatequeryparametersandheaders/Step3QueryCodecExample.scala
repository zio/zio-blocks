package validatequeryparametersandheaders

import zio.blocks.schema.Schema
import zio.http._
import zio.http.schema._

import Bookstore._
import Params._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — A Whole Filter
 * from a Derived QueryCodec
 *
 * Decodes `?author=` and `?year=` into a `BookFilter` case class with a
 * `QueryCodec` derived from its schema, and shows that the codec does not use a
 * field's default value when the parameter is missing.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * validatequeryparametersandheaders.Step3QueryCodecExample"
 */
object Step3QueryCodecExample {
  final case class BookFilter(author: Option[String], year: Option[Int])

  object BookFilter {
    implicit val schema: Schema[BookFilter] = Schema.derived

    val codec: QueryCodec[BookFilter] = Schema[BookFilter].derive(DefaultQueryFormat)
  }

  def matches(filter: BookFilter)(book: Book): Boolean =
    filter.author.forall(book.authors.contains) && filter.year.forall(_ == book.year)

  def listBooks(request: Request): Response =
    BookFilter.codec.decode(request.queryParams) match {
      case Right(filter) => Response.json(bookListJson.encodeToString(books.filter(matches(filter))))
      case Left(error)   => badRequest(Problem.decoded("filter")(error))
    }

  final case class DefaultedPaging(limit: Int = 20, offset: Int = 0)

  object DefaultedPaging {
    implicit val schema: Schema[DefaultedPaging] = Schema.derived

    val codec: QueryCodec[DefaultedPaging] = Schema[DefaultedPaging].derive(DefaultQueryFormat)
  }

  def main(args: Array[String]): Unit = {
    JdkHttp.withServer(listBooks) { port =>
      Client.call(port, "GET", "/books?year=2014")
      Client.call(port, "GET", "/books?author=Martin%20Odersky&year=2008")
      Client.call(port, "GET", "/books?year=abc")
    }

    println(DefaultedPaging.codec.decode(QueryParams.fromEncoded("limit=5&offset=10")))
    println(DefaultedPaging.codec.decode(QueryParams.fromEncoded("limit=5")))
  }
}
