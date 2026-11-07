package smalljsonbackendfromblocks

import zio.blocks.config._
import zio.blocks.scope._

/**
 * Build a Small JSON Backend from Blocks — the server and the wiring
 *
 * `HttpService` serves the instrumented routes with the JDK HTTP server and
 * stops it in `close()`. `Bookstore.app` builds the whole service graph from
 * one layered settings source.
 */
class HttpService(routes: Routes, val telemetry: RequestTelemetry, config: ServerConfig) extends AutoCloseable {
  private val server = JdkHttp.start(telemetry.wrap(routes.handle), config.port)
  println("HttpService: started")

  def port: Int = server.getAddress.getPort

  def close(): Unit = {
    server.stop(0)
    println("HttpService: stopped")
  }
}

object Bookstore {
  def app(settings: ConfigSource): Resource[HttpService] =
    Resource.from[HttpService](
      Wire(settings),
      Config.wire[ServerConfig]("server"),
      Config.wire[DbConfig]("db"),
      Config.wire[CatalogConfig]("catalog")
    )
}
