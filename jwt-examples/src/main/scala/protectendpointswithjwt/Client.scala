package protectendpointswithjwt

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * How to Serve http-model Requests with the JDK HTTP Server — a test client
 *
 * Sends one request with the JDK's `java.net.http.HttpClient` and prints the
 * status code, the `Content-Type` and `Content-Length` headers, and the body.
 *
 * Copied from the JDK HTTP server guide with two additions for this guide: an
 * optional `Authorization` header value to send, and the `WWW-Authenticate`
 * header printed when the response has one.
 */
object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def call(port: Int, method: String, target: String, body: String = "", authorization: String = ""): Unit = {
    val plain   = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
    val builder = if (authorization.isEmpty) plain else plain.header("authorization", authorization)
    val request =
      if (body.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
      else
        builder
          .method(method, HttpRequest.BodyPublishers.ofString(body))
          .header("content-type", "application/json")
          .build()
    val response                     = client.send(request, HttpResponse.BodyHandlers.ofString())
    def header(name: String): String = response.headers.firstValue(name).orElse("none")
    println(s"$method $target -> ${response.statusCode} [${header("content-type")}, ${header("content-length")} bytes]")
    response.headers.firstValue("www-authenticate").ifPresent(value => println(s"www-authenticate: $value"))
    if (response.body.nonEmpty) println(response.body)
  }
}
