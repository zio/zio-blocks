package zio.blocks.scope.examples

import zio.blocks.scope._

/**
 * Configuration for database connection.
 *
 * @param host
 *   the database server hostname
 * @param port
 *   the database server port
 * @param database
 *   the database name to connect to
 */
final case class DbConfig(host: String, port: Int, database: String) {
  def connectionUrl: String = s"jdbc:postgresql://$host:$port/$database"
}

/**
 * Represents the result of a database query.
 *
 * @param rows
 *   the result set as a list of row maps
 */
final case class QueryResult(rows: List[Map[String, String]]) {
  def isEmpty: Boolean = rows.isEmpty
  def size: Int        = rows.size
}

/**
 * Simulates a database connection with lifecycle management.
 *
 * This class demonstrates how AutoCloseable resources integrate with ZIO Blocks
 * Scope. When allocated via `scope.allocate(Resource(...))`, the `close()`
 * method is automatically registered as a finalizer.
 *
 * @param config
 *   the database configuration
 */
final class Database(config: DbConfig) extends AutoCloseable {
  private var connected = false

  def connect(): Unit = {
    println(s"[Database] Connecting to ${config.connectionUrl}...")
    connected = true
    println(s"[Database] Connected successfully")
  }

  def query(sql: String): QueryResult = {
    require(connected, "Database not connected")
    println(s"[Database] Executing: $sql")
    sql match {
      case s if s.contains("users") =>
        QueryResult(
          List(
            Map("id" -> "1", "name" -> "Alice"),
            Map("id" -> "2", "name" -> "Bob")
          )
        )
      case s if s.contains("orders") =>
        QueryResult(
          List(
            Map("order_id" -> "101", "user_id" -> "1", "total" -> "99.99"),
            Map("order_id" -> "102", "user_id" -> "2", "total" -> "149.50")
          )
        )
      case _ =>
        QueryResult(List(Map("result" -> "OK")))
    }
  }

  override def close(): Unit = {
    println(s"[Database] Closing connection to ${config.connectionUrl}")
    connected = false
  }
}

/**
 * Demonstrates basic resource lifecycle management with ZIO Blocks Scope.
 *
 * This example shows:
 *   - Allocating an AutoCloseable resource with automatic cleanup
 *   - Using `scope.$` to access scoped values and execute queries
 *   - LIFO finalizer ordering (last allocated = first closed)
 *
 * When the scope exits, all registered finalizers run in reverse order,
 * ensuring proper cleanup even if exceptions occur.
 */
@main def runDatabaseExample(): Unit = {
  println("=== Database Connection Example ===\n")

  val config = DbConfig("localhost", 5432, "myapp")

  Scope.global.scoped { scope =>
    println("[Scope] Entering scoped region\n")

    // Allocate the database resource. Because Database extends AutoCloseable,
    // its close() method is automatically registered as a finalizer.
    val db = scope.allocate(Resource {
      val database = new Database(config)
      database.connect()
      database
    })

    // Use scope.$ to access the scoped value and execute queries.
    // The result (QueryResult) is Unscoped so it escapes the scope safely.
    val users = scope.$(db)(_.query("SELECT * FROM users"))
    println(s"[Result] Found ${users.size} users: ${users.rows.map(_("name")).mkString(", ")}\n")

    val orders = scope.$(db)(_.query("SELECT * FROM orders WHERE status = 'pending'"))
    println(s"[Result] Found ${orders.size} orders\n")

    val health = scope.$(db)(_.query("SELECT 1 AS health_check"))
    println(s"[Result] Health check: ${health.rows.head("result")}\n")

    println("[Scope] Exiting scoped region - finalizers will run in LIFO order")
  }

  println("\n=== Example Complete ===")
}
