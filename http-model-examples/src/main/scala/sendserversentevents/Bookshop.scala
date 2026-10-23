package sendserversentevents

import java.nio.charset.StandardCharsets
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaType
import zio.blocks.streams.Stream
import zio.http._

/**
 * How to Send Server-Sent Events with http-model — the new-arrivals feed
 *
 * Six Scala books that a bookshop announces one at a time, the event for each
 * arrival, the `text/event-stream` content type, and the helpers that turn a
 * stream of events into a streaming `Response`.
 */
final case class Book(title: String, authors: String)

object Bookshop {
  val books: Vector[Book] = Vector(
    Book("Zionomicon", "John A. De Goes and Adam Fraser"),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners and Frank Sommers"),
    Book("Functional Programming in Scala", "Michael Pilquist, Rúnar Bjarnason and Paul Chiusano"),
    Book("Scala with Cats", "Noel Welsh and Dave Gurnell"),
    Book("Hands-on Scala Programming", "Li Haoyi"),
    Book("Essential Effects", "Adam Rosien")
  )

  /** The event for the book at `position` (1-based) in the arrivals list. */
  def arrival(position: Int, book: Book): ServerSentEvent[String] =
    ServerSentEvent(s"${book.title}\n${book.authors}", "arrival").id(position.toString)

  /** Every arrival after the first `lastSeen` books. */
  def arrivalsAfter(lastSeen: Int): Vector[ServerSentEvent[String]] =
    books.zipWithIndex.drop(lastSeen).map { case (book, index) => arrival(index + 1, book) }

  /** Emits the events one at a time, waiting `intervalMillis` before each. */
  def paced(events: Vector[ServerSentEvent[String]], intervalMillis: Long): Stream[Nothing, ServerSentEvent[String]] =
    Stream.fromIterable(events).tapEach(_ => Thread.sleep(intervalMillis))

  /** `text/event-stream`; the media type registry has no constant for it. */
  val eventStream: ContentType = ContentType(MediaType("text", "event-stream"))

  /** Renders each event to its wire text and encodes it as UTF-8 bytes. */
  def encode(events: Stream[Nothing, ServerSentEvent[String]]): Stream[Nothing, Byte] =
    events.flatMap(event => Stream.fromChunk(Chunk.fromArray(event.render.getBytes(StandardCharsets.UTF_8))))

  /** A `200 OK` whose body streams the events as they are produced. */
  def eventResponse(events: Stream[Nothing, ServerSentEvent[String]]): Response =
    Response.ok(Body.fromStream(encode(events), eventStream)).addHeader(Header.CacheControl.NoCache)
}
