package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates the `Unscoped` marker trait behavior.
 *
 * ==Key Concepts==
 *
 *   - '''`Unscoped`''' marks pure data types that can safely escape a scope
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
 * returned from `scoped { ... }`, the raw `ConfigData` is returned.
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

  def readConfig(@annotation.unused path: String): ConfigData = {
    require(!closed, "ConfigReader is closed")
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
 * `Unscoped` instance. It cannot escape the scope.
 */
class SecretStore extends AutoCloseable {
  private var closed = false

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
  println("=== Unscoped Example ===\n")

  // ConfigData is Unscoped, so it escapes the scope as raw ConfigData
  val escapedConfig: ConfigData = Scope.global.scoped { scope =>
    import scope._
    val reader: $[ConfigReader] = allocate(Resource(new ConfigReader))

    // (scope $ reader)(f) returns $[ConfigData]; .get extracts the pure data
    (scope $ reader)(_.readConfig("/etc/app/config.json")).get
  }

  println("Escaped config (used outside scope):")
  println(s"  App: ${escapedConfig.appName} v${escapedConfig.version}")
  escapedConfig.settings.foreach { case (k, v) => println(s"    $k = $v") }
  println()

  println("SecretStore stays scoped:")
  Scope.global.scoped { scope =>
    import scope._
    val secrets: $[SecretStore] = allocate(Resource(new SecretStore))

    (scope $ secrets) { s =>
      val dbPassword = s.getSecret("database.password")
      println(s"  Retrieved secret: $dbPassword")
    }
    ()
  }
  println("\n=== Example Complete ===")
}
