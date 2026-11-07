// Run from this folder:  sbt "shareOneModelJVM/runMain shareonemodel.Concept4ServeJsonExample"
package shareonemodel

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import zio.blocks.schema._

/**
 * Share One Model: the JVM server encodes the shared model as JSON.
 *
 * Serves `Sample.list` at `http://localhost:8090/tasks` until you press Ctrl+C.
 */
object Concept4ServeJsonExample {
  def main(args: Array[String]): Unit = {
    val server = HttpServer.create(new InetSocketAddress("localhost", 8090), 0)
    server.createContext(
      "/tasks",
      (exchange: HttpExchange) => {
        val body = Sample.list.toJsonString.getBytes(StandardCharsets.UTF_8)
        exchange.getResponseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, body.length.toLong)
        exchange.getResponseBody.write(body)
        exchange.close()
      }
    )
    server.start()
    println("Serving the shared model on http://localhost:8090/tasks")
  }
}
