package returnhtmxfragments

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import zio.http._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — the adapter
 *
 * Glue between the JDK's built-in `com.sun.net.httpserver.HttpServer` and a
 * plain `Request => Response` function. It is meant for examples, tools and
 * small services, not for production traffic.
 */
object JdkHttp {

  /**
   * Reads the method, URL, headers and body of an exchange into a `Request`.
   * Returns `Left` with a ready-made error response when the method is unknown
   * (501) or the request target is not a valid URL (400).
   */
  def toRequest(exchange: HttpExchange): Either[Response, Request] = {
    val uri    = exchange.getRequestURI
    val target = uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
    for {
      method <- Method.fromString(exchange.getRequestMethod).toRight(Response(Status.NotImplemented))
      url    <- URL.parse(target).left.map(error => Response.text(Status.BadRequest, error))
    } yield {
      val headers = exchange.getRequestHeaders.asScala.foldLeft(Headers.empty) { case (acc, (name, values)) =>
        values.asScala.foldLeft(acc)((withValue, value) => withValue.add(name, value))
      }
      val contentType =
        headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)
      val body    = Body.fromArray(exchange.getRequestBody.readAllBytes(), contentType)
      val version = Version.fromString(exchange.getProtocol).getOrElse(Version.`HTTP/1.1`)
      Request(method, url, headers, body, version)
    }
  }

  /**
   * Writes the status, headers and body of a `Response` to an exchange. The JDK
   * server computes `Content-Length` itself, so the response's own
   * `content-length` and `transfer-encoding` headers are skipped.
   */
  def writeResponse(exchange: HttpExchange, response: Response): Unit = {
    val out = exchange.getResponseHeaders
    response.headers.toList.foreach { case (name, value) =>
      if (name != "content-length" && name != "transfer-encoding") out.add(name, value)
    }
    val bytes = response.body.toArray
    if (bytes.nonEmpty && !response.headers.has("content-type"))
      out.set("content-type", response.body.contentType.render)
    val code   = response.status.code
    val isHead = exchange.getRequestMethod == "HEAD"
    if (isHead && bytes.nonEmpty) out.set("content-length", bytes.length.toString)
    val noBody = isHead || code == 204 || code == 304 || bytes.isEmpty
    exchange.sendResponseHeaders(code, if (noBody) -1L else bytes.length.toLong)
    if (!noBody) exchange.getResponseBody.write(bytes)
  }

  /**
   * Turns an application into an `HttpHandler`. A `HEAD` request runs the `GET`
   * route and drops the body; an exception in the application becomes a 500
   * response; a failure while writing the response is logged and, if no headers
   * have gone out yet, also becomes a 500; the exchange is closed on every
   * path.
   */
  def handler(app: Request => Response): HttpHandler = exchange =>
    try {
      val response =
        try
          toRequest(exchange) match {
            case Left(error)                                     => error
            case Right(request) if request.method == Method.HEAD => app(request.copy(method = Method.GET))
            case Right(request)                                  => app(request)
          }
        catch {
          case NonFatal(error) =>
            System.err.println(s"Handler failed: $error")
            Response.text(Status.InternalServerError, "Internal Server Error")
        }
      try writeResponse(exchange, response)
      catch {
        case NonFatal(error) =>
          System.err.println(s"Writing the response failed: $error")
          if (exchange.getResponseCode == -1) {
            exchange.getResponseHeaders.clear()
            exchange.sendResponseHeaders(Status.InternalServerError.code, -1L)
          }
      }
    } finally exchange.close()

  /**
   * Starts a server on the loopback interface. Port 0 asks the operating system
   * for a free port; read it back with `server.getAddress.getPort`.
   */
  def start(app: Request => Response, port: Int = 0): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
    server.createContext("/", handler(app))
    server.start()
    server
  }

  /**
   * Starts a server on a free loopback port, passes the port to `use`, and
   * stops the server when `use` returns or throws.
   */
  def withServer[A](app: Request => Response)(use: Int => A): A = {
    val server = start(app)
    try use(server.getAddress.getPort)
    finally server.stop(0)
  }
}
