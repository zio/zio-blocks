package smalljsonbackendfromblocks

import zio.http._
import zio.http.schema._

/**
 * Build a Small JSON Backend from Blocks — checked query parameters
 *
 * Reads `limit` and `offset` from a request as typed values, falls back to a
 * default when one is missing, and returns an `ApiError` when one is malformed
 * or out of range.
 */
final case class Paging(limit: Int, offset: Int)

object Paging {
  val maxLimit = 50

  def from(request: Request, pageSize: Int): Either[ApiError, Paging] =
    for {
      limit  <- intParam(request, "limit", pageSize)
      offset <- intParam(request, "offset", 0)
      _      <- check(limit >= 1 && limit <= maxLimit, s"limit must be between 1 and $maxLimit, got $limit")
      _      <- check(offset >= 0, s"offset must not be negative, got $offset")
    } yield Paging(limit, offset)

  private def intParam(request: Request, name: String, default: Int): Either[ApiError, Int] =
    request.query[Int](name) match {
      case Right(value)                     => Right(value)
      case Left(QueryParamError.Missing(_)) => Right(default)
      case Left(malformed)                  => Left(ApiError(malformed.message))
    }

  private def check(ok: Boolean, message: => String): Either[ApiError, Unit] =
    if (ok) Right(()) else Left(ApiError(message))
}
