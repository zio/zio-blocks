package wire

import zio.blocks.scope._
import zio.blocks.context.Context

/**
 * Demonstrates manual wire construction using fromFunction:
 *   - Wire.Shared.fromFunction for custom shared wire logic
 *   - Wire.Unique.fromFunction for custom unique wire logic
 *   - Using manual wires when macro derivation doesn't fit
 *
 * This is useful for complex initialization, conditional logic, or when you
 * need control over which dependencies to use.
 */

final case class ApiKey(value: String)

final case class HttpConfig(baseUrl: String, timeout: Int)

/**
 * A custom HTTP client that uses an API key and timeout from config.
 * Demonstrates a scenario where we want custom construction logic rather than
 * simple parameter passing.
 */
final class HttpClient(config: HttpConfig, apiKey: ApiKey) extends AutoCloseable {
  println(
    s"[HttpClient] Created with baseUrl=${config.baseUrl}, timeout=${config.timeout}ms, apiKey=${apiKey.value}"
  )

  def get(path: String): String = {
    println(s"[HttpClient] GET $path with api key")
    "response"
  }

  def close(): Unit =
    println("[HttpClient] Connection pool closed")
}

/**
 * A custom authenticator that needs the API key. Demonstrates context
 * extraction in a manual wire.
 */
final class Authenticator(apiKey: ApiKey) {
  println(s"[Authenticator] Using API key: ${apiKey.value}")

  def authenticate(): Boolean = {
    println("[Authenticator] Validating API key...")
    true
  }
}

final class ManualWireApp(client: HttpClient, auth: Authenticator) {
  def run(): Unit =
    if (auth.authenticate()) {
      val response = client.get("/api/users")
      println(s"[App] Got response: $response")
    }
}

@main def wireFromFunctionExample(): Unit = {
  println("=== Wire fromFunction (Manual Construction) Example ===\n")

  // Manually create wires using fromFunction for custom logic
  // This gives full control when macro derivation isn't suitable

  val httpClientWire: Wire.Shared[HttpConfig & ApiKey, HttpClient] =
    Wire.Shared.fromFunction { (scope, ctx) =>
      // Extract both dependencies from context
      val config = ctx.get[HttpConfig]
      val apiKey = ctx.get[ApiKey]

      // Custom initialization logic
      println("[Manual] Custom HttpClient construction")
      val client = new HttpClient(config, apiKey)

      // Register custom cleanup if needed (in addition to AutoCloseable)
      scope.defer(println("[Manual] HttpClient cleanup deferred"))

      client
    }

  val authenticatorWire: Wire.Shared[ApiKey, Authenticator] =
    Wire.Shared.fromFunction { (scope, ctx) =>
      val apiKey = ctx.get[ApiKey]

      // Custom initialization logic
      println("[Manual] Custom Authenticator construction")
      val auth = new Authenticator(apiKey)

      scope.defer(println("[Manual] Authenticator cleanup deferred"))

      auth
    }

  // Provide leaf dependencies
  val configWire = Wire(HttpConfig("https://api.example.com", 30000))
  val apiKeyWire = Wire(ApiKey("secret-key-12345"))

  // Compose into the app resource
  val appResource: Resource[ManualWireApp] = Resource.from[ManualWireApp](
    configWire,
    apiKeyWire,
    httpClientWire,
    authenticatorWire
  )

  println("[Setup] Created manual wires\n")

  // Allocate and run
  Scope.global.scoped { scope =>
    import scope._

    println("[Scope] Entering scoped region\n")

    val app = allocate(appResource)

    println("\n[App] Running application")
    $(app)(_.run())

    println("\n[Scope] Exiting scoped region - finalizers will run")
  }

  println("\n=== Example Complete ===")
}
