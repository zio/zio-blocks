package smalljsonbackendfromblocks

import zio.blocks.config._
import zio.blocks.scope._

/**
 * Build a Small JSON Backend from Blocks — Putting It Together
 *
 * The bookstore backend as a program: settings from `BOOKSTORE_*` environment
 * variables over the YAML file over the defaults, a startup report, the whole
 * service graph in one scope, a few requests through the real server, and a
 * shutdown in reverse order.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.CompleteExample"
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    // 1. Layer the real environment over the YAML file over the defaults.
    val settings = Settings.layered(Settings.environment)

    // 2. Report the settings in effect, or every bad one, before starting anything.
    Config.loadWithProvenance[BookstoreConfig](settings) match {
      case Right(loaded) => println(loaded.dump())
      case Left(errors)  =>
        errors.foreach(error => println(error.message))
        sys.exit(1)
    }

    // 3. Build the service graph, serve requests, and close it in reverse order.
    Scope.global.scoped { scope =>
      import scope._
      val service = allocate(Bookstore.app(settings))
      val port    = $(service)(_.port)

      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/books/1")
      Client.call(
        port,
        "POST",
        "/books",
        """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
      )
      Client.call(port, "GET", "/books?limit=abc")
    }
  }
}
