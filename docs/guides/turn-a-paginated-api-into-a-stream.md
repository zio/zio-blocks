---
id: turn-a-paginated-api-into-a-stream
title: "How to Turn a Paginated API into a Stream"
description: "Walk a page-by-page API with Stream.unfold or Stream.unfoldAsync, stop on the last page, decode each item and report failed requests as typed errors."
keywords:
  - "Stream"
  - "unfold"
  - "unfoldAsync"
  - "Pagination"
  - "Schema"
  - "JSON"
---

In this guide, we will turn a page-by-page HTTP API into a `Stream` that yields one decoded item at a time. We will walk the pages with `Stream.unfold`, switch to `Stream.unfoldAsync` so that each page request does not block a thread, stop when the API reports no next page, decode every item with a `Schema`, and turn a failed request into a typed error. By the end, you will have a stream of `Issue` values read from a tracker API that serves three pages.

We'll take an incremental approach. We start with a walk that produces pages, then flatten the pages into items, then add error handling, and finally replace the blocking walk with a non-blocking one. The examples use a fake API that serves pages from memory, so they run without a network.

The [streaming migration guide](./streaming-library-migration.md) already shows a synchronous paged walk with `Stream.unfold`, using page numbers and stopping on an empty page. This guide covers the cases that walk leaves out: an opaque next-page cursor, stopping on the last page without an extra request, `unfoldAsync`, typed errors, and decoding each item.

## The Problem

A paginated API returns part of a collection per request, plus a token that names the next part. A caller who wants the whole collection has to loop: request a page, process its items, keep the token, and stop when the token is missing. Each caller writes that loop again, and each one has to get the cursor handling, the end condition and the error handling right.

For example, consider reading every issue from a tracker that returns a `next` cursor on each page:

```scala mdoc:compile-only
// Without a stream library: a loop with mutable state that you keep correct by hand.
def fetchPage(cursor: Option[String]): Either[String, (List[String], Option[String])] = ???

var cursor: Option[String] = None
var done                   = false
val titles                 = scala.collection.mutable.ListBuffer.empty[String]
while (!done) {
  fetchPage(cursor) match {
    case Left(error) => throw new RuntimeException(error)
    case Right((items, next)) =>
      titles ++= items
      cursor = next
      done = next.isEmpty
  }
}
```

This loop works, but the state lives in two `var`s, the error path throws, and the items are still raw text. Every caller that needs the issues as values repeats the loop, and a bug in the end condition shows up as an extra request or a missed last page. A stream makes the walk a value that the caller composes with other stream operators, and it keeps the cursor inside the walk.

## Prerequisites

The examples need the `streams`, `async` and `schema` modules. Add them to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema" % "@VERSION@",
  "dev.zio" %% "zio-blocks-streams" % "@VERSION@",
  "dev.zio" %% "zio-blocks-async" % "@VERSION@"
)
```

The code below uses these imports:

```scala mdoc:silent
import zio.blocks.async._
import zio.blocks.schema._
import zio.blocks.streams._
```

This guide assumes you have read [Your First Streaming Pipeline](./your-first-streaming-pipeline.md) and know how to build a `Stream` and run it with `runCollect`. For the schema part, [Your First Schema](./your-first-schema.md) shows how `Schema.derived` and `fromJson` work.

## The Sample API

The tracker's endpoint returns a page of issues and a `next` cursor. Each item is JSON text, as an HTTP body carries it. The last page has no `next`. The fake API below serves three pages from a map and records each cursor it was asked for, so we can count the requests.

```scala mdoc:silent
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
```

`FakeTrackerApi#fetchPage` returns an `Either` for a failed request, so the walk can see the failure as a value. `FakeTrackerApi#fetchPageAsync` gives the same answer as an `Async`, which is what the non-blocking walk later in this guide consumes.

## Walking Pages with Stream.unfold

`Stream.unfold` produces elements from a state. We start with the state `Position.First`, and each step returns the element to emit together with the state for the next step. Here, the element is the response of one page request, and the state is the cursor of the next page.

`Stream.unfold` has this signature:

```scala
def unfold[S, A](s: S)(f: S => Option[(A, S)]): Stream[Nothing, A]
```

To start a walk, the state is the first position. To continue, we turn the position into a cursor, request that page, and return the page together with the next position:

```scala mdoc:silent
/** Turns one page response into the next walk step: a failure ends the walk, and a page without `next` is the last. */
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
```

The stream's element type is `Either[PageFailed, RawPage]` and not `RawPage`. A failed request must end the walk, and `Stream.unfold` cannot fail: its error type is `Nothing`, and an exception thrown inside `f` is a defect. Carrying the failure as an element lets the next section turn it into a typed error.

Running the stream walks the pages in order, and the fake records each cursor:

```scala mdoc
def walkPages(): Unit = {
  val api     = FakeTrackerApi.threePages
  val emitted = pages(api).runCollect
  println(s"pages emitted: ${emitted.map(_.size)}")
  println(s"cursors requested: ${api.requests}")
}

walkPages()
```

The first request has no cursor, and each later one uses the `next` value of the page before it.

## Stopping on the Last Page

The walk must stop when a page has no `next` cursor. `advance` handles that by moving to `Position.Done`, and the `unfold` step returns `None` for `Position.Done`, which ends the stream. The last page is emitted with state `Done`, so the walk ends without asking the API for a page that does not exist.

The output above shows three requests for three pages. The last request is for `c3`, the cursor the second page returned, and nothing asks for a fourth page. If the state after the last page were `First` or a page number instead, the walk would make one extra request that comes back empty.

:::warning
Do not treat an empty page as the end of the walk unless the API documents that. Many cursor APIs return a last page with items and no `next`, so an empty-page check requests one page too many and can hide a real empty result. Use the `next` value.
:::

## Flattening Pages and Decoding Items

A caller usually wants issues, not pages. We flatten each page into its items with `flatMap`, then decode each item with the `Issue` schema. The `fromJson` extension method on `String` parses the text and returns `Either[SchemaError, Issue]`, so a bad item becomes a `Left` that we can turn into a stream failure:

```scala mdoc:silent
/** Decodes each item of a page with the `Issue` schema; a bad item fails the stream. */
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
```

The `flatMap` in `flatten` does two things. A page failure becomes `Stream.fail` with the `PageFailed` value, which ends the stream with that error. A successful page goes to `decodeItems`, which emits its issues one at a time. The stream's error type is now `IssueError`, so `runCollect` returns `Either[IssueError, Chunk[Issue]]`:

```scala mdoc
def listTitles(): Unit = {
  val api = FakeTrackerApi.threePages
  println(issues(api).runCollect.map(_.map(_.title)))
  println(s"cursors requested: ${api.requests}")
}

listTitles()
```

The titles come back in page order, and the decode step gives us values of the `Issue` case class instead of JSON text.

## Handling Errors

Two things can fail. A page request can fail, and an item can fail to decode. Both reach the caller as typed values in the `Left` of `runCollect`, so a caller matches on them and does not catch exceptions.

A failed request ends the stream at that page, so the walk does not request the pages after it. The fake below fails on the second page:

```scala mdoc
def failOnSecondPage(): Unit = {
  val flaky = new FakeTrackerApi(
    Map(
      None       -> Right(RawPage(List(FakeTrackerApi.item(1, "Login fails on Safari", "open")), Some("c2"))),
      Some("c2") -> Left("503 Service Unavailable")
    )
  )
  println(issues(flaky).runCollect)
  println(s"cursors requested: ${flaky.requests}")
}

failOnSecondPage()
```

The result is a `PageFailed` for cursor `c2`, and the requests stop there. Nothing asked for a page after `c2`.

A malformed item fails the same way, with an `ItemFailed` that holds the item's text and the decoder's message:

```scala mdoc
def decodeBadItem(): Unit = {
  val malformed = new FakeTrackerApi(
    Map(None -> Right(RawPage(List("""{"id":"six","title":"Broken","status":"open"}"""), None)))
  )
  println(issues(malformed).runCollect)
}

decodeBadItem()
```

The message names the path to the field that failed, here `.id`, so the caller can log the exact item.

A stream does not remember how far it got. Running the same stream value again starts the walk from `Position.First`, so a retry is a second run. Here the same value runs twice and both runs request all three pages:

```scala mdoc
def runTwice(): Unit = {
  val api = FakeTrackerApi.threePages
  val s   = issues(api)
  println(s.runCollect.map(_.size))
  println(s.runCollect.map(_.size))
  println(s"cursors requested: ${api.requests}")
}

runTwice()
```

The count of requests shows six entries: the first run asks for three pages and the second run asks for the same three again. To retry a failed walk, call `runCollect` again, with a limit on the attempts that you choose.

:::tip
To skip bad items instead of failing, replace the `Stream.fail` in `decodeItems` with `Stream.empty` and count the skipped items in your own code. Skipping hides data, so do it only when the caller can live without the missing items.
:::

## Fetching Without Blocking with unfoldAsync

The walk so far calls `fetchPage` directly, so the thread that runs the stream waits for each page. `Stream.unfoldAsync` takes the same walk with each step returning an `Async`, and the waiting happens on the `Async`, not on the caller's thread. Its signature is:

```scala
def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]]): Stream[Nothing, A]
```

Each step returns an `Async` that yields `Some((element, nextState))` or `None`. At most one step runs at a time, so the walk requests the next page only after the previous page arrived. A failed `Async` is a defect, which is why the fetch returns an `Either` and never fails the `Async` itself:

```scala mdoc:silent
/** The same walk with `unfoldAsync`: each page request returns an `Async`. */
def pagesAsync(api: FakeTrackerApi): Stream[Nothing, Either[PageFailed, RawPage]] =
  Stream.unfoldAsync[Position, Either[PageFailed, RawPage]](Position.First) {
    case Position.Done => Async.succeed(Option.empty[(Either[PageFailed, RawPage], Position)])
    case position      => api.fetchPageAsync(cursorOf(position)).map(result => Some(advance(result)))
  }

def issuesAsync(api: FakeTrackerApi): Stream[IssueError, Issue] = flatten(pagesAsync(api))
```

Only the walk changed. `advance`, `cursorOf`, `decodeItems` and `flatten` are the same, so the typed errors, the last-page rule and the decoding work the same way. The fake's `fetchPageAsync` starts the request with `Async.start`, which runs it on another thread, so the walk does not hold the caller's thread while the page loads.

```scala mdoc
def listTitlesAsync(): Unit = {
  val api = FakeTrackerApi.threePages
  println(issuesAsync(api).runCollect.map(_.map(_.title)))
  println(s"cursors requested: ${api.requests}")
}

listTitlesAsync()
```

The output matches the synchronous walk. Use `unfoldAsync` when each page request is itself asynchronous, for example an HTTP client that returns a future or a callback. Use `unfold` when the fetch is a plain function that returns its result. For how to wrap a callback-based client in an `Async`, see [Wrap Callback and Future APIs](./wrap-callback-and-future-apis.md).

## Putting It Together

The complete program puts the pieces in one file: the non-blocking walk, the typed errors and the decoding, run once on the three-page API and once on a tracker whose second page fails. It has the same sample API as the sections above, so the only difference from them is that the program runs both cases in order.

```scala mdoc:embed:zio-blocks-examples/paginated-api/src/main/scala/paginatedapi/CompleteExample.scala
```

Running it prints the titles of all five issues, the three cursors requested, and the `PageFailed` for the failing second page:

```text
Right(Chunk(Login fails on Safari,Typo in footer,Add dark mode,Crash on empty cart,Rotate API keys))
cursors requested: List(None, Some(c2), Some(c3))
Left(PageFailed(Some(c2),503 Service Unavailable))
```

## Running the Examples

All runnable code from this guide is in [`zio-blocks-examples/paginated-api/src/main/scala/paginatedapi/`](https://github.com/zio/zio-blocks/tree/main/zio-blocks-examples/paginated-api/src/main/scala/paginatedapi). Run any file from that folder with `scala-cli run <File>.scala`, or from `zio-blocks-examples` with `sbt "paginatedApi/runMain paginatedapi.<ObjectName>"`.

## Going Further

To read a real API, replace `FakeTrackerApi#fetchPage` with a call to your HTTP client and parse the response body into a `RawPage`. Keep the `Either` result, so a non-2xx status becomes a `PageFailed` and the walk stops with a typed error.

If the API returns the next cursor in a response header instead of the body, read that header in `fetchPage` and fill `RawPage#next` from it. The walk does not change.

For the full operator list on `Stream`, see the [Stream reference](../reference/streams/core/stream.md). For the rules of `Async`, including cancellation, see the [Async reference](../reference/async.md).
