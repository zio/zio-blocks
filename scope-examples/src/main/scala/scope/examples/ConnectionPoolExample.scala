package scope.examples

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

  val poolResource: Resource[ConnectionPool] =
    Resource.fromAutoCloseable(new ConnectionPool(poolConfig))

  Scope.global.scoped { appScope =>
    import appScope._
    println("[App] Allocating pool\n")
    val pool: $[ConnectionPool] = allocate(poolResource)

    println("--- ServiceA doing work (connection scoped to this block) ---")
    appScope.scoped { workScope =>
      import workScope._
      val p: $[ConnectionPool]   = lower(pool)
      val c: $[PooledConnection] = (workScope $ p)(_.acquire).allocate
      val result                 = (workScope $ c)(_.execute("SELECT * FROM service_a_table")).get
      println(s"  [ServiceA] Got: $result")
    }
    println()

    println("--- ServiceB doing work ---")
    appScope.scoped { workScope =>
      import workScope._
      val p: $[ConnectionPool]   = lower(pool)
      val c: $[PooledConnection] = (workScope $ p)(_.acquire).allocate
      val result                 = (workScope $ c)(_.execute("SELECT * FROM service_b_table")).get
      println(s"  [ServiceB] Got: $result")
    }
    println()

    println("--- Multiple connections in same scope ---")
    appScope.scoped { workScope =>
      import workScope._
      val p: $[ConnectionPool]   = lower(pool)
      val a: $[PooledConnection] = (workScope $ p)(_.acquire).allocate
      val b: $[PooledConnection] = (workScope $ p)(_.acquire).allocate
      val aId                    = (workScope $ a)(_.id).get
      val bId                    = (workScope $ b)(_.id).get
      println(s"  [Parallel] Using connections $aId and $bId")
      (workScope $ a)(_.execute("UPDATE table_a SET x = 1"))
      (workScope $ b)(_.execute("UPDATE table_b SET y = 2"))
      ()
    }
    println()

    println("[App] All work complete, exiting app scope...")
  }

  println("\n=== Example Complete ===")
  println("\nKey insight: pool.acquire returns Resource[PooledConnection],")
  println("forcing proper scoped allocation and automatic release.")
}
