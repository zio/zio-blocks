package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates auto-wiring a layered web service using
 * `Resource.from[T](wires*)`.
 *
 * The macro automatically derives wires for concrete classes (Database,
 * UserRepository, UserController) while requiring only the leaf config value to
 * be provided explicitly. Resources are cleaned up in LIFO order when the scope
 * closes.
 *
 * Layer hierarchy:
 * {{{
 *   AppConfig (leaf value via Wire)
 *       ↓
 *   Database (auto-wired, AutoCloseable)
 *       ↓
 *   UserRepository (auto-wired)
 *       ↓
 *   UserController (auto-wired, AutoCloseable)
 * }}}
 */

/** Application configuration - the leaf dependency provided via Wire(value). */
case class WebAppConfig(dbUrl: String, serverPort: Int)

/** Domain model for users. */
case class User(id: Long, name: String, email: String)

/** Database layer - acquires a connection and releases it on close. */
class WebDatabase(config: WebAppConfig) extends AutoCloseable {
  println(s"  [WebDatabase] Connecting to ${config.dbUrl}")

  def execute(sql: String): Int = {
    println(s"  [WebDatabase] Executing: $sql")
    1
  }

  def close(): Unit = println("  [WebDatabase] Connection closed")
}

/** Repository layer - provides data access using the database. */
class UserRepository(db: WebDatabase) {
  println("  [UserRepository] Initialized")

  private var nextId = 1L

  def findById(id: Long): Option[User] = {
    db.execute(s"SELECT * FROM users WHERE id = $id")
    if (id > 0) Some(User(id, "Alice", "alice@example.com")) else None
  }

  def save(user: User): Long = {
    db.execute(s"INSERT INTO users VALUES (${user.id}, '${user.name}', '${user.email}')")
    val id = nextId
    nextId += 1
    id
  }
}

/** Controller layer - handles HTTP requests using the repository. */
class UserController(repo: UserRepository) extends AutoCloseable {
  println("  [UserController] Ready to serve requests")

  def getUser(id: Long): String =
    repo.findById(id).map(u => s"User(${u.id}, ${u.name})").getOrElse("Not found")

  def createUser(name: String, email: String): String = {
    val id = repo.save(User(0, name, email))
    s"Created user with id=$id"
  }

  def close(): Unit = println("  [UserController] Shutting down")
}

/**
 * Entry point demonstrating the auto-wiring feature.
 *
 * Only `Wire(config)` is provided; the macro derives wires for Database,
 * UserRepository, and UserController from their constructors.
 */
@main def layeredWebServiceExample(): Unit = {
  val config = WebAppConfig(dbUrl = "jdbc:postgresql://localhost:5432/mydb", serverPort = 8080)

  println("=== Constructing layers (order: config → database → repository → controller) ===")

  // Resource.from auto-wires the entire dependency graph
  val controllerResource: Resource[UserController] = Resource.from[UserController](
    Wire(config)
  )

  // Allocate within a scoped block; cleanup runs on scope exit
  Scope.global.scoped { scope =>
    import scope._
    val controller: $[UserController] = allocate(controllerResource)

    println("\n=== Handling requests ===")
    println(s"  GET /users/1  → ${scope.use(controller)(_.getUser(1))}")
    println(s"  POST /users   → ${scope.use(controller)(_.createUser("Bob", "bob@example.com"))}")

    println("\n=== Scope closing (LIFO cleanup: controller → database) ===")
  }

  println("=== Done ===")
}
