package sendserversentevents

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets

/**
 * How to Send Server-Sent Events with http-model — a test client
 *
 * Opens an event stream with `HttpURLConnection`, prints the status and the
 * headers that matter for SSE, then prints each line as it arrives. After each
 * event it prints how long after the request the event arrived.
 */
object FeedClient {
  def watch(port: Int, target: String, lastEventId: Option[String] = None, maxEvents: Int = Int.MaxValue): Unit = {
    val connection = URI.create(s"http://127.0.0.1:$port$target").toURL.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestProperty("accept", "text/event-stream")
    lastEventId.foreach(id => connection.setRequestProperty("last-event-id", id))
    val started = System.nanoTime()
    try {
      val status                       = connection.getResponseCode
      def header(name: String): String = Option(connection.getHeaderField(name)).getOrElse("none")
      println(
        s"GET $target -> $status [content-type: ${header("content-type")}, cache-control: ${header("cache-control")}, " +
          s"transfer-encoding: ${header("transfer-encoding")}]"
      )
      if (status == 200) {
        val reader = new BufferedReader(new InputStreamReader(connection.getInputStream, StandardCharsets.UTF_8))
        var events = 0
        var line   = reader.readLine()
        while (line != null && events < maxEvents) {
          if (line.isEmpty) {
            events += 1
            println(f"-- event $events received after ${(System.nanoTime() - started) / 1e9}%.1f s")
          } else println(line)
          if (events < maxEvents) line = reader.readLine()
        }
      }
    } finally connection.disconnect()
  }
}
