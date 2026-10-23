package addinteractivitywithhtmx

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/**
 * Add Interactivity to a Page with HTMX — a client that acts like htmx
 *
 * `Client#call` sends no extra headers. This helper sends `HX-Request: true`,
 * the header htmx adds to every request it makes, and prints the status and the
 * body, so we can see what the server returns for an htmx request.
 */
object HxClient {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def get(port: Int, target: String): Unit = {
    val request = HttpRequest
      .newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
      .header("HX-Request", "true")
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println(s"GET $target with HX-Request -> ${response.statusCode}")
    println(response.body)
  }
}
