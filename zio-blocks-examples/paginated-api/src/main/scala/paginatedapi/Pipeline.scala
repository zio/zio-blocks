package paginatedapi

import zio.blocks.async._
import zio.blocks.schema._
import zio.blocks.streams._

object Pipeline {

  /**
   * Turns one page response into the next walk step: a failure ends the walk,
   * and a page without `next` is the last.
   */
  def advance(result: Either[PageFailed, RawPage]): (Either[PageFailed, RawPage], Position) =
    result match {
      case Left(failure) => (Left(failure), Position.Done)
      case Right(page)   => (Right(page), page.next.fold[Position](Position.Done)(Position.After(_)))
    }

  def cursorOf(position: Position): Option[String] =
    position match {
      case Position.After(cursor) => Some(cursor)
      case _                      => None
    }

  /** One element per page request, walked with `unfold`. */
  def pages(api: FakeTrackerApi): Stream[Nothing, Either[PageFailed, RawPage]] =
    Stream.unfold[Position, Either[PageFailed, RawPage]](Position.First) {
      case Position.Done => None
      case position      => Some(advance(api.fetchPage(cursorOf(position))))
    }

  /** The same walk with `unfoldAsync`: each page request returns an `Async`. */
  def pagesAsync(api: FakeTrackerApi): Stream[Nothing, Either[PageFailed, RawPage]] =
    Stream.unfoldAsync[Position, Either[PageFailed, RawPage]](Position.First) {
      case Position.Done => Async.succeed(Option.empty[(Either[PageFailed, RawPage], Position)])
      case position      => api.fetchPageAsync(cursorOf(position)).map(result => Some(advance(result)))
    }

  /**
   * Decodes each item of a page with the `Issue` schema; a bad item fails the
   * stream.
   */
  def decodeItems(page: RawPage): Stream[IssueError, Issue] =
    Stream.fromIterable(page.items).flatMap { raw =>
      raw.fromJson[Issue] match {
        case Right(issue) => Stream(issue)
        case Left(error)  => Stream.fail(ItemFailed(raw, error.message))
      }
    }

  private def flatten(pages: Stream[Nothing, Either[PageFailed, RawPage]]): Stream[IssueError, Issue] =
    pages.flatMap {
      case Left(failure) => Stream.fail(failure)
      case Right(page)   => decodeItems(page)
    }

  /** Every issue of every page, or the first typed error. */
  def issues(api: FakeTrackerApi): Stream[IssueError, Issue] = flatten(pages(api))

  def issuesAsync(api: FakeTrackerApi): Stream[IssueError, Issue] = flatten(pagesAsync(api))
}
