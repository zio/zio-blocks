package validatequeryparametersandheaders

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * How to Validate Query Parameters and Headers and Answer 400 — a test client
 * that sends headers
 *
 * Sends one `GET` request with the given request headers and prints the status
 * code, the headers that were sent, and the response body.
 */
object HeaderClient {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def get(port: Int, target: String, headers: (String, String)*): Unit = {
    val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target")).GET()
    headers.foreach { case (name, value) => builder.header(name, value) }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    val sent     = headers.map { case (name, value) => s"$name: $value" }.mkString(" [", ", ", "]")
    println(s"GET $target${if (headers.isEmpty) "" else sent} -> ${response.statusCode}")
    if (response.body.nonEmpty) println(response.body)
  }
}
