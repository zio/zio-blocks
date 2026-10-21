package smalljsonbackendfromblocks

import zio.blocks.config._
import zio.blocks.scope._

/**
 * Build a Small JSON Backend from Blocks — Wire, Serve and Shut Down
 *
 * Builds the whole service graph from layered settings inside a scope, serves a
 * few requests, and closes the services in reverse order. Then shows the report
 * for a bad setting.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept8WiringExample"
 */
object Concept8WiringExample {
  def main(args: Array[String]): Unit = {
    val env = ConfigSource.fromMap(Map("db.file" -> TempDb.file()), "env")

    val counts = Scope.global.scoped { scope =>
      import scope._
      val service = allocate(Bookstore.app(Settings.layered(env)))
      val port    = $(service)(_.port)

      Client.call(port, "GET", "/books")
      Client.call(
        port,
        "POST",
        "/books",
        """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
      )
      Client.call(port, "GET", "/books?offset=2")

      $(service)(_.telemetry.countsByStatus())
    }
    println(s"Requests by status: $counts")

    val badEnv = ConfigSource.fromMap(Map("db.file" -> TempDb.file(), "catalog.pageSize" -> "ten"), "env")
    try {
      Scope.global.scoped { scope =>
        import scope._
        allocate(Bookstore.app(Settings.layered(badEnv)))
        ()
      }
    } catch {
      case error: ConfigLoadException => println(error.getMessage)
    }
  }
}
