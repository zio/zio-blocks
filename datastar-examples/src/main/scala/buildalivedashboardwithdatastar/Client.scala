package buildalivedashboardwithdatastar

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets

/**
 * Build a Live Counter and Dashboard with Datastar — a test client
 *
 * Sends requests the way Datastar does, with the JDK's
 * `java.net.http.HttpClient`, and prints what comes back, so every step can be
 * checked without a browser.
 */
object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  private def request(port: Int, method: String, target: String, body: String): HttpRequest = {
    val builder = HttpRequest
      .newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
      .header("Datastar-Request", "true")
    if (body.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
    else
      builder
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .header("content-type", "application/json")
        .build()
  }

  /** Sends one request and prints the status, the content type and the body. */
  def call(port: Int, method: String, target: String, body: String = ""): Unit = {
    val response = client.send(request(port, method, target, body), HttpResponse.BodyHandlers.ofString())
    val shown    = response.headers.firstValue("content-type").orElse("none")
    println(s"$method $target -> ${response.statusCode} [$shown]")
    if (response.body.nonEmpty) println(response.body)
  }

  /**
   * Opens an event stream, prints the first `maxLines` lines as they arrive,
   * and then closes the connection, the way a browser tab that goes away does.
   */
  def watch(port: Int, target: String, maxLines: Int): Unit = {
    val response = client.send(request(port, "GET", target, ""), HttpResponse.BodyHandlers.ofInputStream())
    val reader   = new BufferedReader(new InputStreamReader(response.body, StandardCharsets.UTF_8))
    try Iterator.continually(reader.readLine()).takeWhile(_ != null).take(maxLines).foreach(println)
    finally reader.close()
  }
}
