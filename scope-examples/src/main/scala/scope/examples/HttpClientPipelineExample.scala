package scope.examples

import zio.blocks.scope._

/**
 * HTTP Client Pipeline Example
 *
 * Demonstrates building computations using scoped values with
 * for-comprehensions and the `$` operator. Operations are eager with the new
 * opaque type API.
 */

/** API configuration containing base URL and authentication credentials. */
final case class ApiConfig(baseUrl: String, apiKey: String)

/** Parsed JSON data as a simple key-value store. */
final case class ParsedData(values: Map[String, String]) derives Unscoped

/** HTTP response containing status, body, and headers. */
final case class HttpResponse(statusCode: Int, body: String, headers: Map[String, String])

/** Stateless JSON parser that converts raw JSON strings to structured data. */
object JsonParser {
  def parse(json: String): ParsedData = {
    println(s"  [JsonParser] Parsing ${json.take(50)}...")
    val entries = json.stripPrefix("{").stripSuffix("}").split(",").map(_.trim).filter(_.nonEmpty)
    val values  = entries.flatMap { entry =>
      entry.split(":").map(_.trim.stripPrefix("\"").stripSuffix("\"")) match {
        case Array(k, v) => Some(k -> v)
        case _           => None
      }
    }.toMap
    ParsedData(values)
  }
}

/**
 * HTTP client that manages a connection to an API server.
 *
 * Implements `AutoCloseable` so the scope automatically registers cleanup.
 */
final class HttpClient(config: ApiConfig) extends AutoCloseable {
  println(s"  [HttpClient] Opening connection to ${config.baseUrl}")

  def get(path: String): HttpResponse = {
    println(s"  [HttpClient] GET $path")
    HttpResponse(200, s"""{"path":"$path","data":"sample"}""", Map("X-Api-Key" -> config.apiKey))
  }

  def post(path: String, body: String): HttpResponse = {
    println(s"  [HttpClient] POST $path with body: $body")
    HttpResponse(201, s"""{"created":true,"echo":"$body"}""", Map("Content-Type" -> "application/json"))
  }

  override def close(): Unit =
    println(s"  [HttpClient] Closing connection to ${config.baseUrl}")
}

/**
 * Demonstrates using scoped values with the opaque type API.
 *
 * Key concepts:
 *   - `allocate` returns `$[A]` (scoped value)
 *   - `scope.use(scopedValue)(f)` applies a function to the underlying value
 *   - Operations are eager (zero-cost wrapper)
 *   - For-comprehensions chain scoped values naturally
 */
@main def httpClientPipelineExample(): Unit = {
  println("=== HTTP Client Pipeline Example ===\n")
  val config = ApiConfig("https://api.example.com", "secret-key-123")

  Scope.global.scoped { scope =>
    import scope._
    // Step 1: Allocate the HTTP client (automatically cleaned up when scope closes)
    val client: $[HttpClient] = allocate(Resource[HttpClient](new HttpClient(config)))

    // Step 2: Use the client to fetch and parse data
    println("Executing requests...\n")

    // Fetch and parse users
    println("--- Fetching: users ---")
    val users: $[ParsedData] =
      client.map { c =>
        val response = c.get("/users")
        JsonParser.parse(response.body)
      }

    // Fetch and parse orders
    println("\n--- Fetching: orders ---")
    val orders: $[ParsedData] =
      client.map { c =>
        val response = c.get("/orders")
        JsonParser.parse(response.body)
      }

    // Post analytics event
    println("\n--- Posting: analytics ---")
    val analytics: $[ParsedData] =
      client.map { c =>
        val response = c.post("/analytics", """{"event":"fetch_complete"}""")
        JsonParser.parse(response.body)
      }

    // Step 3: Access all results together using multi-arity use
    scope.use(users, orders, analytics) { (u, o, a) =>
      println(s"\n=== Users Result ===")
      println(s"Users data: ${u.values}")
      println(s"\n=== Orders Result ===")
      println(s"Orders data: ${o.values}")
      println(s"\n=== Analytics Result ===")
      println(s"Analytics: ${a.values}")
    }
  }

  println("\n[Scope closed - HttpClient was automatically cleaned up]")
}
