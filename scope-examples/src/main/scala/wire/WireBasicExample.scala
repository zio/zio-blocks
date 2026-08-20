package wire

import zio.blocks.scope._
import zio.blocks.context.Context

/**
 * Demonstrates basic Wire construction patterns:
 *   - Wire.shared[T] macro derivation
 *   - Wire.apply(value) to lift a value
 *   - Converting wires to Resources
 *   - Using wires in a dependency graph
 */

final case class DbConfig(host: String, port: Int) {
  def url: String = s"jdbc:postgresql://$host:$port/db"
}

final class Database(config: DbConfig) extends AutoCloseable {
  println(s"[Database] Connecting to ${config.url}")

  def query(sql: String): String = {
    println(s"[Database] Executing: $sql")
    "result"
  }

  def close(): Unit =
    println("[Database] Connection closed")
}

final class UserService(db: Database) {
  println("[UserService] Initialized")

  def getUser(id: Int): String = {
    db.query(s"SELECT * FROM users WHERE id = $id")
    s"User(id=$id, name=Alice)"
  }
}

final class BasicApp(service: UserService) {
  def run(): Unit = {
    val user = service.getUser(1)
    println(s"[App] Got: $user")
  }
}

@main def wireBasicExample(): Unit = {
  println("=== Wire Basic Construction Example ===\n")

  // Create the dependency leaf (config) using Wire.apply
  val configWire: Wire.Shared[Any, DbConfig] =
    Wire(DbConfig("localhost", 5432))

  // Derive wires for Database and UserService using the macro
  val dbWire: Wire.Shared[DbConfig, Database] =
    Wire.shared[Database]

  val serviceWire: Wire.Shared[Database, UserService] =
    Wire.shared[UserService]

  val appWire: Wire.Shared[UserService, BasicApp] =
    Wire.shared[BasicApp]

  println("[Setup] Created all wires\n")

  // Use Resource.from to automatically compose the dependency graph
  val appResource: Resource[BasicApp] = Resource.from[BasicApp](
    configWire,
    dbWire,
    serviceWire,
    appWire
  )

  println("[Setup] Composed resource graph\n")

  // Allocate within a scope
  Scope.global.scoped { scope =>
    import scope._

    println("[Scope] Entering scoped region\n")

    val app: $[BasicApp] = allocate(appResource)

    println("\n[App] Running application")
    $(app)(_.run())

    println("\n[Scope] Exiting scoped region - finalizers will run")
  }

  println("\n=== Example Complete ===")
}
