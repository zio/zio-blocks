package smalljsonbackendfromblocks

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import zio.blocks.config._
import zio.blocks.scope._
import zio.test._

/**
 * Build a Small JSON Backend from Blocks — Test the Backend
 *
 * Calls the routes directly against a fresh database file for each test, and
 * sends one request through the real server built from the settings.
 *
 * Run with: cd capstone-examples && sbt "testOnly
 * smalljsonbackendfromblocks.BookstoreSpec"
 */
object BookstoreSpec extends ZIOSpecDefault {

  def withRoutes[A](use: Routes => A): A = {
    val db = new Db(DbConfig(Demo.tempDbFile()))
    try use(new Routes(new BookRepo(db), CatalogConfig(pageSize = 2)))
    finally db.close()
  }

  def call(routes: Routes, request: zio.http.Request): (Int, String) = {
    val response = routes.handle(request)
    (response.status.code, response.body.text)
  }

  def spec = suite("Bookstore")(
    test("GET /books returns the first page") {
      val result = withRoutes(call(_, Demo.get("/books?limit=1")))
      assertTrue(
        result == (
          200,
          """[{"id":1,"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024,"stock":3,"priceCents":4500}]"""
        )
      )
    },
    test("GET /books/{id} returns 404 for a missing book") {
      val result = withRoutes(call(_, Demo.get("/books/42")))
      assertTrue(result == (404, """{"error":"no book with id 42"}"""))
    },
    test("GET /books returns 400 for a malformed limit") {
      val result = withRoutes(call(_, Demo.get("/books?limit=abc")))
      assertTrue(
        result == (400, """{"error":"Malformed query parameter 'limit' value 'abc': Cannot parse 'abc' as Int"}""")
      )
    },
    test("POST /books stores the book and returns it with its id") {
      val body   = """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
      val result = withRoutes { routes =>
        call(routes, Demo.post("/books", body))
        call(routes, Demo.get("/books/4"))
      }
      assertTrue(
        result == (
          200,
          """{"id":4,"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
        )
      )
    },
    test("the server built from the settings answers over HTTP") {
      val env    = ConfigSource.fromMap(Map("db.file" -> Demo.tempDbFile()), "env")
      val result = Scope.global.scoped { scope =>
        import scope._
        val service = allocate(Bookstore.app(Settings.layered(env)))
        val port    = $(service)(_.port)
        val client  = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/books/2")).build()
        val reply   = client.send(request, HttpResponse.BodyHandlers.ofString())
        (reply.statusCode, reply.body)
      }
      assertTrue(
        result == (
          200,
          """{"id":2,"title":"Programming in Scala","firstAuthor":"Martin Odersky","year":2008,"isbn":"9780981531601","stock":5,"priceCents":3999}"""
        )
      )
    }
  ) @@ TestAspect.sequential
}
