package validatequeryparametersandheaders

import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.JsonCodec
import zio.http._
import zio.http.schema._

/**
 * How to Validate Query Parameters and Headers and Answer 400 — shared helpers
 *
 * The helpers the guide builds section by section: an optional query parameter
 * that still rejects malformed values, a `Problem` for every bad parameter or
 * header, and a 400 response that lists them all as JSON.
 */
final case class Problem(in: String, name: String, reason: String)

object Problem {
  implicit val schema: Schema[Problem] = Schema.derived

  def query(error: QueryParamError): Problem = error match {
    case QueryParamError.Missing(key)             => Problem("query", key, "is required")
    case QueryParamError.Malformed(key, _, cause) => Problem("query", key, cause)
  }

  def header(error: HeaderError): Problem = error match {
    case HeaderError.Missing(name)             => Problem("header", name, "is required")
    case HeaderError.Malformed(name, _, cause) => Problem("header", name, cause)
  }

  def decoded(name: String)(error: SchemaError): List[Problem] =
    error.errors.map {
      case SchemaError.MissingField(_, field) => Problem("query", field, "is required")
      case other                              => Problem("query", name, other.message)
    }
}

final case class ErrorBody(problems: List[Problem])

object ErrorBody {
  implicit val schema: Schema[ErrorBody] = Schema.derived

  val json: JsonCodec[ErrorBody] = Schema[ErrorBody].jsonCodec
}

object Params {

  /** A decoded value, or every problem found while decoding it. */
  type Checked[A] = Either[List[Problem], A]

  def optionalQuery[T: Schema](request: Request, key: String): Either[QueryParamError, Option[T]] =
    request.query[T](key) match {
      case Left(QueryParamError.Missing(_)) => Right(None)
      case other                            => other.map(Some(_))
    }

  def badRequest(problems: List[Problem]): Response =
    Response.json(Status.BadRequest, ErrorBody.json.encodeToString(ErrorBody(problems)))

  def checkedQuery[T: Schema](request: Request, key: String): Checked[Option[T]] =
    optionalQuery[T](request, key).left.map(error => List(Problem.query(error)))

  def check[A](name: String, reason: String)(ok: A => Boolean)(value: A): Checked[A] =
    if (ok(value)) Right(value) else Left(List(Problem("query", name, reason)))

  def both[A, B](a: Checked[A], b: Checked[B]): Checked[(A, B)] =
    (a, b) match {
      case (Right(x), Right(y)) => Right((x, y))
      case _                    => Left(a.swap.getOrElse(Nil) ++ b.swap.getOrElse(Nil))
    }
}
