package sendserversentevents

import java.nio.charset.StandardCharsets
import zio.http._

/**
 * How to Send Server-Sent Events with http-model — Watching the Feed in a
 * Browser
 *
 * Serves a small HTML page at / whose script opens the feed at /arrivals with
 * the browser's built-in `EventSource` and lists each book as it arrives. The
 * server keeps running until you stop it with Ctrl+C.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step5BrowserPageExample" and open http://localhost:8080/
 */
object Step5BrowserPageExample {
  val page: String =
    """<!DOCTYPE html>
      |<html>
      |<head><meta charset="utf-8"><title>New arrivals</title></head>
      |<body>
      |<h1>New arrivals</h1>
      |<p id="status">Connecting...</p>
      |<ul id="books"></ul>
      |<script>
      |  const status = document.getElementById("status");
      |  const source = new EventSource("/arrivals");
      |  source.addEventListener("arrival", (event) => {
      |    const [title, authors] = event.data.split("\n");
      |    const item = document.createElement("li");
      |    item.textContent = title + " by " + authors;
      |    document.getElementById("books").append(item);
      |    status.textContent = "Last event id: " + event.lastEventId;
      |  });
      |  source.onerror = () => {
      |    status.textContent = source.readyState === EventSource.CLOSED ? "All books have arrived." : "Reconnecting...";
      |  };
      |</script>
      |</body>
      |</html>
      |""".stripMargin

  def app(intervalMillis: Long): Request => Response = request =>
    (request.method, request.path.encode) match {
      case (Method.GET, "/") =>
        Response.ok(Body.fromArray(page.getBytes(StandardCharsets.UTF_8), ContentType.`text/html`))
      case (Method.GET, "/arrivals") => Step4ResumeExample.arrivals(request, intervalMillis)
      case _                         => Response.notFound
    }

  def main(args: Array[String]): Unit = {
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    StreamingJdkHttp.start(app(intervalMillis = 1000), port)
    println(s"Open http://localhost:$port/ (Ctrl+C to stop)")
    Thread.currentThread().join()
  }
}
