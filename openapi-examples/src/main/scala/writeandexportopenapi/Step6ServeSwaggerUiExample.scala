package writeandexportopenapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs._
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}
import com.sun.net.httpserver.HttpServer
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Write an OpenAPI Document and Export It as JSON or YAML: Step 6 —
 * Serving the Document to Swagger UI
 *
 * Serves the document at http://localhost:8080/openapi.json with the JDK's
 * built-in HTTP server, fetches it once to show that it works, and keeps
 * serving until you press Enter.
 *
 * Run with: cd openapi-examples && sbt "runMain
 * writeandexportopenapi.Step6ServeSwaggerUiExample"
 */
object Step6ServeSwaggerUiExample {

  final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  final case class ApiError(message: String)

  object ApiError {
    implicit val schema: Schema[ApiError] = Schema.derived
  }

  val sampleBooks: List[Book] = List(
    Book("Zionomicon", "John A. De Goes", 2024, None),
    Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")),
    Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
  )

  val (bookRef, _)  = Schema[Book].toRefSchema
  val (errorRef, _) = Schema[ApiError].toRefSchema

  val componentSchemas: Map[String, SchemaObject] = OpenAPIGen.schemas(Schema[Book], Schema[ApiError])

  val bookListSchema: ReferenceOr[SchemaObject] = ReferenceOr.Value(
    SchemaObject(
      jsonSchema = Json.Object(
        "type"  -> Json.String("array"),
        "items" -> Json.Object("$ref" -> Json.String("#/components/schemas/Book"))
      )
    )
  )

  val limitParam: Parameter = Parameter(
    name = "limit",
    in = ParameterLocation.Query,
    description = Some(md"Maximum number of books to return."),
    schema = Some(Schema[Int].toInlineSchema)
  )

  val idParam: Parameter = Parameter(
    name = "id",
    in = ParameterLocation.Path,
    required = true,
    description = Some(md"Catalog ID of the book."),
    schema = Some(Schema[String].toInlineSchema)
  )

  val booksFound: Response = Response(
    description = md"The books in the catalog.",
    content = ChunkMap(
      "application/json" -> MediaType(schema = Some(bookListSchema), example = Some(Json.from(sampleBooks)))
    )
  )

  val bookFound: Response = Response(
    description = md"The book.",
    content = ChunkMap("application/json" -> MediaType(schema = Some(bookRef)))
  )

  val bookNotFound: Response = Response(
    description = md"No book has this ID.",
    content = ChunkMap("application/json" -> MediaType(schema = Some(errorRef)))
  )

  val paths: Paths = Paths(
    ChunkMap(
      "/books" -> PathItem(
        get = Some(
          Operation(
            operationId = Some("listBooks"),
            parameters = Chunk(ReferenceOr.Value(limitParam)),
            responses = Responses(ChunkMap("200" -> ReferenceOr.Value(booksFound)))
          )
        )
      ),
      "/books/{id}" -> PathItem(
        parameters = Chunk(ReferenceOr.Value(idParam)),
        get = Some(
          Operation(
            operationId = Some("getBook"),
            responses = Responses(
              ChunkMap(
                "200" -> ReferenceOr.Value(bookFound),
                "404" -> ReferenceOr.Value(bookNotFound)
              )
            )
          )
        )
      )
    )
  )

  val components: Components = Components(
    schemas = ChunkMap.from(componentSchemas.map { case (name, schema) => name -> ReferenceOr.Value(schema) }),
    securitySchemes = ChunkMap(
      "bearerAuth" -> ReferenceOr.Value(SecurityScheme.HTTP(scheme = "bearer", bearerFormat = Some("JWT")))
    )
  )

  val requireToken: SecurityRequirement = SecurityRequirement(ChunkMap("bearerAuth" -> Chunk.empty))

  val api: OpenAPI = OpenAPI(
    openapi = "3.1.0",
    info = Info(title = "Bookstore API", version = "1.0.0"),
    paths = Some(paths),
    components = Some(components),
    security = Chunk(requireToken)
  )

  val json: Json = openAPICodec.encodeValue(api)

  // The handler reads `body` from another thread, so the serving code runs in `main`, not in the
  // initializer of an `App` object, where the request would deadlock on the class initialization.
  def main(args: Array[String]): Unit = {
    val body: Array[Byte] = json.print.getBytes(UTF_8)

    val server: HttpServer = HttpServer.create(new InetSocketAddress(8080), 0)
    server.createContext(
      "/openapi.json",
      exchange => {
        exchange.getResponseHeaders.set("Content-Type", "application/json")
        // Swagger UI runs on another origin, so the browser needs permission to fetch the document.
        exchange.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, body.length.toLong)
        exchange.getResponseBody.write(body)
        exchange.close()
      }
    )
    server.start()

    println("Serving the document at http://localhost:8080/openapi.json")

    val client: HttpClient             = HttpClient.newHttpClient()
    val request: HttpRequest           = HttpRequest.newBuilder(URI.create("http://localhost:8080/openapi.json")).build()
    val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())
    println(s"GET /openapi.json -> ${response.statusCode()}")
    println(s"Content-Type: ${response.headers().firstValue("Content-Type").orElse("-")}")
    println(s"Body starts with: ${response.body().take(80)}")

    println("Point Swagger UI at the URL above. Press Enter to stop the server.")
    scala.io.StdIn.readLine()
    server.stop(0)
  }
}
