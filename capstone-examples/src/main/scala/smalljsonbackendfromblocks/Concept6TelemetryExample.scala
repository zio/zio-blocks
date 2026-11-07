package smalljsonbackendfromblocks

import zio.blocks.telemetry.trace

/**
 * Build a Small JSON Backend from Blocks — Log, Trace and Count Requests
 *
 * Wraps the routes with `RequestTelemetry`, sends three requests, and prints
 * the spans and the request counts by status.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept6TelemetryExample"
 */
object Concept6TelemetryExample {
  def main(args: Array[String]): Unit = {
    val db        = new Db(DbConfig(TempDb.file()))
    val telemetry = new RequestTelemetry
    val app       = telemetry.wrap(new Routes(new BookRepo(db), CatalogConfig(pageSize = 2)).handle)

    app(Demo.get("/books/1"))
    app(Demo.get("/books/42"))
    app(Demo.get("/books?limit=abc"))

    println(trace.collectedSpans.map(_.name))
    println(telemetry.countsByStatus())

    db.close()
  }
}
