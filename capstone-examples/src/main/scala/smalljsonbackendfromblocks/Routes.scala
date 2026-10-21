package smalljsonbackendfromblocks

import zio.http._

/**
 * Build a Small JSON Backend from Blocks — the routes
 *
 * A plain `Request => Response` function: it matches the method and the path
 * segments, calls the repository, and writes JSON bodies with the derived
 * codecs. Bad input gets a 400 with a JSON error message.
 */
class Routes(repo: BookRepo, catalog: CatalogConfig) {

  def handle(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, List("books")) =>
        Paging.from(request, catalog.pageSize) match {
          case Right(paging) => Response.json(Json.books.encodeToString(repo.page(paging)))
          case Left(error)   => Routes.error(Status.BadRequest, error)
        }

      case (Method.GET, List("books", id)) =>
        id.toLongOption.flatMap(repo.find) match {
          case Some(book) => Response.json(Json.book.encodeToString(book))
          case None       => Routes.error(Status.NotFound, ApiError(s"no book with id $id"))
        }

      case (Method.POST, List("books")) =>
        Json.newBook.decode(request.body.toArray) match {
          case Right(newBook) => Response.json(Status.Created, Json.book.encodeToString(repo.add(newBook)))
          case Left(error)    => Routes.error(Status.BadRequest, ApiError(error.message))
        }

      case _ =>
        Routes.error(Status.NotFound, ApiError(s"no route for ${request.method} ${request.path}"))
    }
}

object Routes {
  def error(status: Status, error: ApiError): Response =
    Response.json(status, Json.error.encodeToString(error))
}
