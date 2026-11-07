package buildalivedashboardwithdatastar

import java.nio.charset.StandardCharsets
import zio.blocks.chunk.Chunk
import zio.blocks.html._
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.blocks.streams.Stream
import zio.http._

/**
 * Build a Live Counter and Dashboard with Datastar — the glue
 *
 * Three things the datastar module does not provide yet: the `<script
 * type="module">` tag that loads Datastar, a way to read the signals the
 * browser sends, and a `Response` that streams Datastar events. Every step
 * reuses them.
 */
object Glue {

  /** The Datastar release the tutorial was checked against. */
  val datastarVersion: String = "1.0.4"

  /** Loads Datastar from the jsDelivr CDN as a JavaScript module. */
  val datastarScript: Dom.Element =
    script(
      `type` := "module",
      src    := s"https://cdn.jsdelivr.net/gh/starfederation/datastar@v$datastarVersion/bundles/datastar.js"
    )

  /** A page that loads Datastar, with a heading and the given content. */
  def page(heading: String)(content: Dom.Element*): Dom.Element =
    html(lang := "en")(
      head(meta(charset := "utf-8"), title(heading), datastarScript),
      body(h1(heading), content)
    )

  /** A 200 OK response with `Content-Type: text/html`. */
  def htmlResponse(markup: String): Response =
    Response.ok(Body.fromArray(markup.getBytes(StandardCharsets.UTF_8), ContentType.`text/html`))

  /** A 404 for any route the step does not handle. */
  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  /** `text/event-stream`; the media type registry has no constant for it. */
  val eventStream: ContentType = ContentType(MediaType("text", "event-stream"))

  /**
   * A 200 OK whose body streams the already rendered events as they are
   * produced. Each string is the `renderSSE` text of one Datastar event.
   */
  def eventResponse(events: Stream[Nothing, String]): Response = {
    val bytes = events.flatMap(event => Stream.fromChunk(Chunk.fromArray(event.getBytes(StandardCharsets.UTF_8))))
    Response.ok(Body.fromStream(bytes, eventStream)).addHeader(Header.CacheControl.NoCache)
  }

  /** A response that sends the given rendered events and then ends. */
  def eventResponse(first: String, rest: String*): Response =
    eventResponse(Stream.fromIterable(first +: rest))

  /**
   * Reads the signals Datastar sent with a request: the JSON body of a `POST`,
   * or the `datastar` query parameter of a `GET`. Returns the decoding error as
   * text when the JSON does not match `A`.
   */
  def readSignals[A](request: Request)(implicit schema: Schema[A]): Either[String, A] = {
    val json =
      if (request.method == Method.GET) request.url.queryParams.getFirst("datastar").getOrElse("")
      else request.body.asString()
    schema.jsonCodec.decode(json).left.map(_.toString)
  }

  /**
   * Starts the server on the port given as the first argument (8080 by default)
   * and keeps it running until the program is stopped with Ctrl+C.
   */
  def serve(app: Request => Response, args: Array[String]): Int = {
    val port   = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    val server = StreamingJdkHttp.start(app, port)
    sys.addShutdownHook(server.stop(0))
    port
  }

  def openInBrowser(port: Int): Unit =
    println(s"Open http://localhost:$port/ in a browser. Press Ctrl+C to stop.")
}
