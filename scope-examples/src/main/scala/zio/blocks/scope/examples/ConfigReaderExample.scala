package zio.blocks.scope.examples

import zio.blocks.scope._

/**
 * Demonstrates the `Unscoped` marker trait and `ScopeEscape` behavior.
 *
 * ==Key Concepts==
 *
 *   - '''`Unscoped`''' marks pure data types that can safely escape a scope
 *   - '''`ScopeEscape`''' controls whether `scope.$` returns raw `A` or
 *     `A @@ S`
 *   - Pure data escapes freely; resources remain scope-bound
 *
 * ==Example Scenario==
 *
 * A configuration reader produces `ConfigData` (pure, escapable data), while a
 * secret store holds resources that must remain scoped to prevent leakage.
 */

// ---------------------------------------------------------------------------
// Domain Types
// ---------------------------------------------------------------------------

/**
 * Pure configuration data that can safely escape any scope.
 *
 * By deriving `Unscoped`, we declare this type contains no resources. When
 * extracted via `scope.$`, the raw `ConfigData` is returned instead of
 * `ConfigData @@ Tag`.
 */
case class ConfigData(
  appName: String,
  version: String,
  settings: Map[String, String]
) derives Unscoped

/**
 * Reads configuration files from disk.
 *
 * This is a resource (holds file handles, caches) and must be closed.
 */
class ConfigReader extends AutoCloseable {
  private var closed = false

  /** Reads and parses configuration from the given path. */
  def readConfig(@annotation.unused path: String): ConfigData = {
    require(!closed, "ConfigReader is closed")
    // Simulate reading a config file
    ConfigData(
      appName = "MyApplication",
      version = "1.0.0",
      settings = Map(
        "database.host" -> "localhost",
        "database.port" -> "5432",
        "log.level"     -> "INFO"
      )
    )
  }

  override def close(): Unit = {
    closed = true
    println("  [ConfigReader] Closed.")
  }
}

/**
 * Manages access to application secrets.
 *
 * This resource maintains connections and caches; it should NOT have an
 * `Unscoped` instance. Attempting to escape it from a child scope yields
 * `SecretStore @@ Tag` (still scoped), not raw `SecretStore`.
 */
class SecretStore extends AutoCloseable {
  private var closed = false

  /** Retrieves a secret by key. */
  def getSecret(key: String): String = {
    require(!closed, "SecretStore is closed")
    s"secret-value-for-$key"
  }

  override def close(): Unit = {
    closed = true
    println("  [SecretStore] Closed.")
  }
}

// ---------------------------------------------------------------------------
// Main Example
// ---------------------------------------------------------------------------

@main def runConfigReaderExample(): Unit = {
  println("=== ScopeEscape & Unscoped Example ===\n")

  // ConfigData escapes because it derives Unscoped
  val escapedConfig: ConfigData = Scope.global.scoped { outer =>
    val reader = outer.allocate(Resource(new ConfigReader))

    // Extract config in a nested scope — it escapes as raw ConfigData
    outer.scoped { inner =>
      inner.$(reader)(_.readConfig("/etc/app/config.json"))
    }
  }

  println("Escaped config (used outside scope):")
  println(s"  App: ${escapedConfig.appName} v${escapedConfig.version}")
  escapedConfig.settings.foreach { case (k, v) => println(s"    $k = $v") }
  println()

  // SecretStore does NOT escape — it remains scoped
  println("SecretStore stays scoped (cannot escape child scope):")
  Scope.global.scoped { scope =>
    val secrets = scope.allocate(Resource(new SecretStore))

    // This returns SecretStore @@ scope.Tag, not raw SecretStore
    val stillScoped = scope.$(secrets)(identity)

    // We can use it within this scope via scope.$
    val dbPassword = scope.$(stillScoped)(_.getSecret("database.password"))
    println(s"  Retrieved secret: $dbPassword")
  }
  println("\n=== Example Complete ===")
}
