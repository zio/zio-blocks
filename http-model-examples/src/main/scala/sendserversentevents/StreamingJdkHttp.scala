package sendserversentevents

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.io.{FilterOutputStream, IOException, OutputStream}
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.{Executors, ThreadFactory}
import scala.util.control.NonFatal
import servehttpmodelrequests.JdkHttp
import zio.blocks.streams.Sink
import zio.http._

/**
 * How to Send Server-Sent Events with http-model — the streaming adapter
 *
 * The adapter from How to Serve http-model Requests with the JDK HTTP Server
 * (`servehttpmodelrequests.JdkHttp`) with two changes for event streams: a
 * `text/event-stream` body is written as it is produced, with chunked transfer
 * encoding and a flush after every event, instead of being collected with
 * `Body#toArray`; and each exchange runs on its own thread, so one open stream
 * does not block every other request. Other responses still go through
 * `JdkHttp.writeResponse`.
 */
object StreamingJdkHttp {

  /** Flushes the underlying stream at every blank line, the end of an event. */
  final class FlushPerEvent(out: OutputStream) extends FilterOutputStream(out) {
    private var previous = -1

    override def write(b: Int): Unit = {
      out.write(b)
      if (b == '\n' && previous == '\n') out.flush()
      previous = b
    }
  }

  def isEventStream(response: Response): Boolean =
    response.body.contentType.mediaType.fullType == "text/event-stream"

  /**
   * Sends the status and headers at once, then pulls the body stream and writes
   * each event as soon as it is rendered. A response length of 0 tells the JDK
   * server that the length is unknown, so it uses chunked transfer encoding.
   * Throws `IOException` when the client has gone away.
   */
  def writeEventStream(exchange: HttpExchange, response: Response): Unit = {
    val out = exchange.getResponseHeaders
    response.headers.toList.foreach { case (name, value) =>
      if (name != "content-length" && name != "transfer-encoding") out.add(name, value)
    }
    if (!response.headers.has("content-type")) out.set("content-type", response.body.contentType.render)
    exchange.sendResponseHeaders(response.status.code, 0L)
    response.body.stream.run(Sink.fromOutputStream(new FlushPerEvent(exchange.getResponseBody)))
    ()
  }

  /**
   * Like `JdkHttp.handler`, but sends event streams with `writeEventStream`. An
   * `IOException` while writing means the client closed the connection, which
   * is how every event stream ends from the client's side.
   */
  def handler(app: Request => Response): HttpHandler = exchange =>
    try {
      val response =
        try JdkHttp.toRequest(exchange).fold(identity, app)
        catch {
          case NonFatal(error) =>
            System.err.println(s"Handler failed: $error")
            Response.text(Status.InternalServerError, "Internal Server Error")
        }
      if (isEventStream(response)) writeEventStream(exchange, response)
      else JdkHttp.writeResponse(exchange, response)
    } catch {
      case _: IOException => ()
    } finally exchange.close()

  private val daemonThreads: ThreadFactory = runnable => {
    val thread = new Thread(runnable, "sse-exchange")
    thread.setDaemon(true)
    thread
  }

  /**
   * Starts a server on the loopback interface with one thread per open
   * exchange. Port 0 asks the operating system for a free port.
   */
  def start(app: Request => Response, port: Int = 0): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
    server.createContext("/", handler(app))
    server.setExecutor(Executors.newCachedThreadPool(daemonThreads))
    server.start()
    server
  }

  /**
   * Starts a server on a free port, passes the port to `use`, then stops it.
   */
  def withServer[A](app: Request => Response)(use: Int => A): A = {
    val server = start(app)
    try use(server.getAddress.getPort)
    finally server.stop(0)
  }
}
