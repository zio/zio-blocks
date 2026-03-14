package scope.examples

import zio.blocks.scope.Scope.OpenScope
import zio.blocks.scope.{Resource, Scope}

object MyApp extends App {
  import zio.blocks.scope._

  case class Config(host: String, port: Int)

  class Logger(config: Config) extends AutoCloseable {
    def log(msg: String): Unit = println(s"[$msg] from ${config.host}:${config.port}")
    def close(): Unit          = log("Logger shutting down")
  }

  class Service(logger: Logger) extends AutoCloseable {
    def run(): Unit   = logger.log("Service running")
    def close(): Unit = logger.log("Service shutting down")
  }

  // Provide wires for Config; the macro derives Logger and Service automatically
  val serviceResource = Resource.from[Service](
    Wire(Config("localhost", 8080))
  )

  Scope.global.scoped { scope =>
    import scope._
    val service: $[Service] = allocate(serviceResource)

    $(service)(_.run())
  }

}

object MyApp2 extends App {

  case class Config(url: String)
  val resource = Resource(Config("jdbc://localhost"))
  val url      = Scope.global.scoped { scope =>
    resource.make(scope).url
  }
}
