package scope.examples

import scala.annotation.nowarn
import zio.blocks.scope._

/**
 * Demonstrates the `$[A]` scoped type with `use` + `.get` for resource access.
 *
 * This example showcases the key design of ZIO Blocks Scope:
 *   - `allocate` returns `$[A]` (a scoped value)
 *   - `scope.use(x)(f)` safely accesses scoped values with macro enforcement
 *   - `.get` extracts pure data from `$[A]` when `A: Unscoped`
 *   - Operations are eager (zero-cost wrapper)
 *   - All resources are cleaned up in LIFO order when the scope exits
 */

/** A connection pool that produces connections. */
class Pool extends AutoCloseable {
  println("  [Pool] Created")
  var closed = false

  def lease(): Connection = {
    require(!closed, "Pool is closed")
    new Connection(this)
  }

  def close(): Unit = {
    println("  [Pool] Closed")
    closed = true
  }
}

/** A database connection leased from a pool. */
class Connection(val pool: Pool) extends AutoCloseable {
  println("    [Connection] Acquired from pool")
  var closed = false

  def query(sql: String): QueryData = {
    require(!closed && !pool.closed, "Connection or pool is closed")
    println(s"    [Connection] Executing: $sql")
    QueryData(s"result: $sql")
  }

  def close(): Unit = {
    println("    [Connection] Released")
    closed = true
  }
}

/** Query result - a pure data type that can escape the scope. */
case class QueryData(value: String) extends Unscoped[QueryData]

@nowarn("msg=.*leaked.*|.*leak.*")
@main def scopedForComprehensionExample(): Unit = {
  println("=== Scoped Resource Access Example ===\n")

  println("--- Pattern 1: Chaining allocates with use + .get ---")
  Scope.global.scoped { scope =>
    import scope._
    val pool: $[Pool]       = allocate(Resource.from[Pool])
    val rawPool             = scope.leak(pool)
    val conn: $[Connection] = allocate(Resource(rawPool.lease()))
    val result              = scope.use(conn)(_.query("SELECT * FROM users")).get
    println(s"\n  Result: ${result.value.toUpperCase}")
    // Scope exits: Connection closed, then Pool closed (LIFO)
  }

  println("\n--- Pattern 2: Mixing allocates with pure computations ---")
  Scope.global.scoped { scope =>
    import scope._
    val pool: $[Pool]       = allocate(Resource.from[Pool])
    val rawPool             = scope.leak(pool)
    val conn: $[Connection] = allocate(Resource(rawPool.lease()))
    val prefix              = "PREFIX: "
    val result              = scope.use(conn)(_.query("SELECT name FROM employees")).get
    println(s"\n  Result: $prefix${result.value}")
  }

  println("\n--- Pattern 3: Nested scopes ---")
  Scope.global.scoped { outer =>
    import outer._
    println("\n  [outer] Creating pool")
    val pool: $[Pool] = allocate(Resource.from[Pool])

    // Child scope for a unit of work
    outer.scoped { inner =>
      import inner._
      println("  [inner] Acquiring connection from parent's pool")
      val p: $[Pool]          = lower(pool)
      val rawPool             = inner.leak(p)
      val conn: $[Connection] = allocate(Resource(rawPool.lease()))
      val result              = inner.use(conn)(_.query("SELECT 1")).get
      println(s"  [inner] Result: ${result.value}")
      // inner scope exits: Connection released
    }
    println("  [outer] After inner scope - connection released, pool still alive")
    // outer scope exits: Pool closed
  }

  println("\n=== Example Complete ===")
  println("\nKey insights:")
  println("  - $[A] is the scoped value type (zero-cost wrapper)")
  println("  - allocate returns $[A]")
  println("  - scope.use(x)(f) safely accesses scoped values")
  println("  - .get extracts pure data from $[A]")
  println("  - Operations are eager")
  println("  - Resources cleaned up in LIFO order on scope exit")
}
