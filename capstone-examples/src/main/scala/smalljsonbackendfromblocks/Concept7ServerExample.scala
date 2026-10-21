package smalljsonbackendfromblocks

/**
 * Build a Small JSON Backend from Blocks — Serve the Routes over HTTP
 *
 * Starts `HttpService` on a free port, sends two real HTTP requests, and stops
 * the server.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept7ServerExample"
 */
object Concept7ServerExample {
  def main(args: Array[String]): Unit = {
    val db      = new Db(DbConfig(TempDb.file()))
    val routes  = new Routes(new BookRepo(db), CatalogConfig(pageSize = 2))
    val service = new HttpService(routes, new RequestTelemetry, ServerConfig(port = 0))

    Client.call(service.port, "GET", "/books/1")
    Client.call(service.port, "GET", "/books?limit=abc")

    service.close()
    db.close()
  }
}
