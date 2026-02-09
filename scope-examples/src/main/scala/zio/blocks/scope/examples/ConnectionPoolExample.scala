package zio.blocks.scope.examples

import zio.blocks.scope._
import java.util.concurrent.atomic.AtomicInteger

/**
 * Demonstrates `Resource.Shared` with reference counting and nested resource
 * acquisition.
 *
 * This example shows a realistic connection pool pattern where:
 *   - The pool itself is a shared resource (created once, ref-counted)
 *   - Individual connections are resources that must be allocated in a scope
 *   - `pool.acquire` returns `Resource[PooledConnection]`, forcing proper
 *     scoping
 *
 * This pattern is common for database pools, HTTP client pools, and thread
 * pools.
 */

/** Configuration for the connection pool. */
final case class PoolConfig(maxConnections: Int, timeout: Long)

/**
 * A connection retrieved from the pool.
 *
 * Connections are resources - they must be released back to the pool when done.
 * This is enforced by making `acquire` return a `Resource[PooledConnection]`.
 */
final class PooledConnection(val id: Int, pool: ConnectionPool) extends AutoCloseable {
  println(s"    [Conn#$id] Acquired from pool")

  def execute(sql: String): String = {
    println(s"    [Conn#$id] Executing: $sql")
    s"Result from connection $id"
  }

  override def close(): Unit =
    pool.release(this)
}

/**
 * A connection pool that manages pooled connections.
 *
 * Key design: `acquire` returns `Resource[PooledConnection]`, not a raw
 * connection. This forces callers to allocate the connection in a scope,
 * ensuring proper release even if exceptions occur.
 */
final class ConnectionPool(config: PoolConfig) extends AutoCloseable {
  private val nextId  = new AtomicInteger(0)
  private val active  = new AtomicInteger(0)
  private val _closed = new AtomicInteger(0)

  println(s"  [Pool] Created with max ${config.maxConnections} connections")

  /**
   * Acquires a connection from the pool.
   *
   * Returns a `Resource[PooledConnection]` that must be allocated in a scope.
   * The connection is automatically released when the scope exits.
   */
  def acquire: Resource[PooledConnection] = Resource.acquireRelease {
    if (_closed.get() > 0) throw new IllegalStateException("Pool is closed")
    if (active.get() >= config.maxConnections)
      throw new IllegalStateException(s"Pool exhausted (max: ${config.maxConnections})")

    val id   = nextId.incrementAndGet()
    val conn = new PooledConnection(id, this)
    active.incrementAndGet()
    println(s"  [Pool] Active connections: ${active.get()}/${config.maxConnections}")
    conn
  } { conn =>
    // Release is handled by PooledConnection.close()
    conn.close()
  }

  private[examples] def release(conn: PooledConnection): Unit = {
    val count = active.decrementAndGet()
    println(s"    [Conn#${conn.id}] Released back to pool (active: $count)")
  }

  def activeConnections: Int = active.get()

  override def close(): Unit =
    if (_closed.compareAndSet(0, 1)) {
      println(s"  [Pool] *** POOL CLOSED *** (served ${nextId.get()} total connections)")
    }
}

@main def connectionPoolExample(): Unit = {
  println("=== Connection Pool with Resource-based Acquire ===\n")

  val poolConfig = PoolConfig(maxConnections = 3, timeout = 5000L)

  // The pool itself is a resource
  val poolResource: Resource[ConnectionPool] =
    Resource.fromAutoCloseable(new ConnectionPool(poolConfig))

  Scope.global.scoped { appScope =>
    println("[App] Allocating pool\n")
    val pool = appScope.allocate(poolResource)

    // Get the raw pool for use in nested scopes
    val rawPool = @@.unscoped(pool)

    // Each unit of work gets its own connection scope
    println("--- ServiceA doing work (connection scoped to this block) ---")
    appScope.scoped { workScope =>
      // pool.acquire returns Resource[PooledConnection] - must allocate!
      val conn   = workScope.allocate(rawPool.acquire)
      val result = workScope.$(conn)(_.execute("SELECT * FROM service_a_table"))
      println(s"  [ServiceA] Got: $result")
      // Connection automatically released when workScope exits
    }
    println()

    println("--- ServiceB doing work ---")
    appScope.scoped { workScope =>
      val conn   = workScope.allocate(rawPool.acquire)
      val result = workScope.$(conn)(_.execute("SELECT * FROM service_b_table"))
      println(s"  [ServiceB] Got: $result")
    }
    println()

    // Demonstrate multiple concurrent connections from the same pool
    println("--- Multiple connections in same scope ---")
    appScope.scoped { workScope =>
      // Both allocate from the same pool, each gets their own connection
      val connA = workScope.allocate(rawPool.acquire)
      val connB = workScope.allocate(rawPool.acquire)

      println(s"  [Parallel] Using connections ${workScope.$(connA)(_.id)} and ${workScope.$(connB)(_.id)}")
      workScope.$(connA)(_.execute("UPDATE table_a SET x = 1"))
      workScope.$(connB)(_.execute("UPDATE table_b SET y = 2"))

      // Both connections released in LIFO order when scope exits
    }
    println()

    println("[App] All work complete, exiting app scope...")
  }

  println("\n=== Example Complete ===")
  println("\nKey insight: pool.acquire returns Resource[PooledConnection],")
  println("forcing proper scoped allocation and automatic release.")
}
