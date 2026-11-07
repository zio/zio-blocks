package validatequeryparametersandheaders

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * How to Serve http-model Requests with the JDK HTTP Server — a test client
 *
 * Sends one request with the JDK's `java.net.http.HttpClient` and prints the
 * status code, the `Content-Type` and `Content-Length` headers, and the body.
 */
object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def call(port: Int, method: String, target: String, body: String = ""): Unit = {
    val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
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
    if (response.body.nonEmpty) println(response.body)
  }
}
