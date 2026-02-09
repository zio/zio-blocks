package zio.blocks.scope.examples

import zio.blocks.scope._
import java.util.concurrent.atomic.AtomicInteger

/**
 * Demonstrates `Resource.Shared` with reference counting.
 *
 * Multiple components share a connection pool; the pool's finalizer runs only
 * when the last user's scope exits. This pattern is useful for expensive
 * resources (database pools, HTTP clients, thread pools) that should be
 * initialized once and shared across components.
 */

/** Configuration for the connection pool. */
final case class PoolConfig(maxConnections: Int, timeout: Long)

/** A connection retrieved from the pool. */
final case class PooledConnection(id: Int)

/**
 * A connection pool that manages pooled connections.
 *
 * The pool tracks active connections and provides acquire/release semantics.
 * Implements `AutoCloseable` so it can be used with `Resource.shared`.
 */
final class ConnectionPool(config: PoolConfig) extends AutoCloseable {
  private val nextId  = new AtomicInteger(0)
  private val active  = new AtomicInteger(0)
  private val _closed = new AtomicInteger(0)

  def acquire(): PooledConnection = {
    if (_closed.get() > 0) throw new IllegalStateException("Pool is closed")
    val conn = PooledConnection(nextId.incrementAndGet())
    active.incrementAndGet()
    println(s"  [Pool] Acquired connection ${conn.id} (active: ${active.get()})")
    conn
  }

  def release(conn: PooledConnection): Unit = {
    active.decrementAndGet()
    println(s"  [Pool] Released connection ${conn.id} (active: ${active.get()})")
  }

  def activeConnections: Int = active.get()

  override def close(): Unit =
    if (_closed.compareAndSet(0, 1)) {
      println(s"  [Pool] *** POOL CLOSED *** (was serving up to ${config.maxConnections} connections)")
    }
}

/** First service that uses the shared connection pool. */
final class ServiceA(pool: ConnectionPool) {
  def doWork(): Unit = {
    val conn = pool.acquire()
    try {
      println(s"  [ServiceA] Working with connection ${conn.id}")
      Thread.sleep(50)
    } finally pool.release(conn)
  }
}

/** Second service that uses the shared connection pool. */
final class ServiceB(pool: ConnectionPool) {
  def doWork(): Unit = {
    val conn = pool.acquire()
    try {
      println(s"  [ServiceB] Working with connection ${conn.id}")
      Thread.sleep(50)
    } finally pool.release(conn)
  }
}

@main def connectionPoolExample(): Unit = {
  println("=== Connection Pool Sharing Example ===\n")

  val poolConfig = PoolConfig(maxConnections = 10, timeout = 5000L)

  val sharedPoolResource: Resource[ConnectionPool] = Resource.shared { finalizer =>
    println("[Main] Creating shared ConnectionPool...")
    val pool = new ConnectionPool(poolConfig)
    finalizer.defer {
      println("[Main] Running shared pool finalizer...")
      pool.close()
    }
    pool
  }

  Scope.global.scoped { outerScope =>
    println("[Main] Entering outer scope\n")

    outerScope.scoped { scopeA =>
      println("[ScopeA] Allocating shared pool (ref count -> 1)")
      val poolA    = scopeA.allocate(sharedPoolResource)
      val serviceA = new ServiceA(@@.unscoped(poolA))

      scopeA.scoped { scopeB =>
        println("[ScopeB] Allocating shared pool (ref count -> 2)")
        val poolB    = scopeB.allocate(sharedPoolResource)
        val serviceB = new ServiceB(@@.unscoped(poolB))

        println("\n--- Both services working concurrently ---")
        serviceA.doWork()
        serviceB.doWork()
        println("--- Work complete ---\n")

        println("[ScopeB] Exiting (ref count -> 1)")
      }
      println("[ScopeB] Exited — pool still open (ServiceA still using it)\n")

      println("[ScopeA] Doing more work after ScopeB closed...")
      serviceA.doWork()
      println()

      println("[ScopeA] Exiting (ref count -> 0)")
    }
    println("[ScopeA] Exited — pool finalizer ran (last reference gone)\n")
  }

  println("=== Example Complete ===")
}
