package smalljsonbackendfromblocks

/**
 * Build a Small JSON Backend from Blocks — Route Requests
 *
 * Calls the routes directly, with no server: a page of books, one book, a
 * missing book, a new book, and two bad requests.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept5RoutesExample"
 */
object Concept5RoutesExample {
  def main(args: Array[String]): Unit = {
    val db     = new Db(DbConfig(TempDb.file()))
    val routes = new Routes(new BookRepo(db), CatalogConfig(pageSize = 2))

    Demo.show(routes.handle(Demo.get("/books")))
    Demo.show(routes.handle(Demo.get("/books/2")))
    Demo.show(routes.handle(Demo.get("/books/42")))
    Demo.show(
      routes.handle(
        Demo.post(
          "/books",
          """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
        )
      )
    )
    Demo.show(routes.handle(Demo.get("/books?limit=abc")))
    Demo.show(routes.handle(Demo.post("/books", """{"title":"Scala with Cats"}""")))

    db.close()
  }
}
