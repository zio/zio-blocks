package scope.examples

import zio.blocks.scope._

/**
 * HTTP Client Pipeline Example
 *
 * Demonstrates building computations using `Scoped` that defer execution until
 * explicitly run via `scope.execute(scopedComputation)`. This shows how to
 * compose operations over scoped resources lazily.
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
 * Demonstrates building and executing a Scoped computation pipeline.
 *
 * Key concepts:
 *   - `(scopedValue).map(f)` builds a `Scoped` computation lazily
 *   - `(scopedValue).flatMap(f)` chains scoped computations
 *   - `scope.$(scopedValue) { v => ... }` executes and applies a function
 *   - The computation only runs when explicitly executed
 */
@main def httpClientPipelineExample(): Unit = {
  println("=== HTTP Client Pipeline Example ===\n")
  val config = ApiConfig("https://api.example.com", "secret-key-123")

  Scope.global.scoped { scope =>
    // Step 1: Allocate the HTTP client (automatically cleaned up when scope closes)
    val client: HttpClient @@ scope.Tag = scope.allocate(Resource[HttpClient](new HttpClient(config)))

    // Step 2: Build lazy computations using map
    // These define WHAT to do, but don't execute yet
    println("Building pipeline (nothing executes yet)...")

    // First computation: fetch and parse users
    val fetchUsers: ParsedData @@ scope.Tag =
      client.map { c =>
        val response = c.get("/users")
        JsonParser.parse(response.body)
      }

    // Second computation: fetch and parse orders
    val fetchOrders: ParsedData @@ scope.Tag =
      client.map { c =>
        val response = c.get("/orders")
        JsonParser.parse(response.body)
      }

    // Third computation: post analytics event
    val postAnalytics: ParsedData @@ scope.Tag =
      client.map { c =>
        val response = c.post("/analytics", """{"event":"fetch_complete"}""")
        JsonParser.parse(response.body)
      }

    println("Pipeline definitions built. Now executing each step...\n")

    // Step 3: Execute each computation using scope.$
    // Use scope.$ to apply a function to the scoped value
    println("--- Executing: fetchUsers ---")
    scope.$(scope.execute(fetchUsers)) { users =>
      println(s"\n=== Users Result ===")
      println(s"Users data: ${users.values}")
    }

    println("\n--- Executing: fetchOrders ---")
    scope.$(scope.execute(fetchOrders)) { orders =>
      println(s"\n=== Orders Result ===")
      println(s"Orders data: ${orders.values}")
    }

    println("\n--- Executing: postAnalytics ---")
    scope.$(scope.execute(postAnalytics)) { analytics =>
      println(s"\n=== Analytics Result ===")
      println(s"Analytics: ${analytics.values}")
    }
  }

  println("\n[Scope closed - HttpClient was automatically cleaned up]")
}
