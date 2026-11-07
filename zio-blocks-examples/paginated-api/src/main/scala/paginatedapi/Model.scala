package paginatedapi

import zio.blocks.async._
import zio.blocks.schema._

/** An issue as the tracker's JSON API returns it. */
final case class Issue(id: Long, title: String, status: String)

object Issue {
  implicit val schema: Schema[Issue] = Schema.derived
}

/**
 * One response of the paged endpoint. `items` holds each issue as JSON text,
 * and `next` is the cursor of the following page, or `None` on the last page.
 */
final case class RawPage(items: List[String], next: Option[String])

/** Why reading the issues failed: a page request or a single item. */
sealed trait IssueError
final case class PageFailed(cursor: Option[String], reason: String) extends IssueError
final case class ItemFailed(item: String, reason: String)           extends IssueError

/** Where the walk goes next: to the first page, to the page after a cursor, or nowhere. */
sealed trait Position
object Position {
  case object First                      extends Position
  final case class After(cursor: String) extends Position
  case object Done                       extends Position
}

/**
 * A stand-in for an HTTP client that serves `pages` from memory, so the
 * examples run without a network. It records each cursor it is asked for.
 */
final class FakeTrackerApi(pages: Map[Option[String], Either[String, RawPage]]) {
  private val log = scala.collection.mutable.ListBuffer.empty[Option[String]]

  /** The cursors requested so far, in order. */
  def requests: List[Option[String]] = log.synchronized(log.toList)

  def fetchPage(cursor: Option[String]): Either[PageFailed, RawPage] = {
    log.synchronized { log += cursor }
    pages.get(cursor) match {
      case Some(Right(page))  => Right(page)
      case Some(Left(reason)) => Left(PageFailed(cursor, reason))
      case None               => Left(PageFailed(cursor, "unknown cursor"))
    }
  }

  /** The same request, answered on another thread instead of the caller's. */
  def fetchPageAsync(cursor: Option[String]): Async[Either[PageFailed, RawPage]] =
    Async.start(fetchPage(cursor)).map(identity)
}

object FakeTrackerApi {
  def item(id: Long, title: String, status: String): String =
    s"""{"id":$id,"title":"$title","status":"$status"}"""

  /** Three pages of issues; the third page has no `next` cursor. */
  def threePages: FakeTrackerApi =
    new FakeTrackerApi(
      Map(
        None -> Right(
          RawPage(
            List(item(1, "Login fails on Safari", "open"), item(2, "Typo in footer", "closed")),
            Some("c2")
          )
        ),
        Some("c2") -> Right(
          RawPage(
            List(item(3, "Add dark mode", "open"), item(4, "Crash on empty cart", "open")),
            Some("c3")
          )
        ),
        Some("c3") -> Right(RawPage(List(item(5, "Rotate API keys", "closed")), None))
      )
    )
}
