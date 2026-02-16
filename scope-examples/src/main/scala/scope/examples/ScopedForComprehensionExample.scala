package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates the `$[A]` scoped type with `$` for resource access.
 *
 * This example showcases the key design of ZIO Blocks Scope:
 *   - `allocate` returns `$[A]` (a scoped value)
 *   - `(scope $ x)(f)` safely accesses scoped values with macro enforcement
 *   - `ScopedResourceOps` enables `.allocate` on `$[Resource[A]]` for chained
 *     acquisition
 *   - Operations are eager (zero-cost wrapper)
 *   - All resources are cleaned up in LIFO order when the scope exits
 */

/** A connection pool that produces connections. */
class Pool extends AutoCloseable {
  println("  [Pool] Created")
  var closed = false

  def lease(): Resource[Connection] = {
    require(!closed, "Pool is closed")
    Resource.fromAutoCloseable(new Connection(this))
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

@main def scopedForComprehensionExample(): Unit = {
  println("=== Scoped Resource Access Example ===\n")

  println("--- Pattern 1: Chaining allocates with $ ---")
  Scope.global.scoped { scope =>
    import scope._
    val pool: $[Pool] = Resource.from[Pool].allocate
    // (scope $ pool)(_.lease()) returns $[Resource[Connection]]; .allocate acquires it
    val conn: $[Connection] = (scope $ pool)(_.lease()).allocate
    val result              = (scope $ conn)(_.query("SELECT * FROM users"))
    println(s"\n  Result: ${result.value.toUpperCase}")
    // Scope exits: Connection closed, then Pool closed (LIFO)
  }

  println("\n--- Pattern 2: Mixing allocates with pure computations ---")
  Scope.global.scoped { scope =>
    import scope._
    val pool: $[Pool] = Resource.from[Pool].allocate
    // (scope $ pool)(_.lease()) returns $[Resource[Connection]]; .allocate acquires it
    val conn: $[Connection] = (scope $ pool)(_.lease()).allocate
    val prefix              = "PREFIX: "
    val result              = (scope $ conn)(_.query("SELECT name FROM employees"))
    println(s"\n  Result: $prefix${result.value}")
  }

  println("\n--- Pattern 3: Nested scopes ---")
  Scope.global.scoped { outer =>
    import outer._
    println("\n  [outer] Creating pool")
    val pool: $[Pool] = Resource.from[Pool].allocate

    // Child scope for a unit of work
    outer.scoped { inner =>
      import inner._
      println("  [inner] Acquiring connection from parent's pool")
      val p: $[Pool] = lower(pool)
      // (inner $ p)(_.lease()) returns $[Resource[Connection]]; .allocate acquires it
      val conn: $[Connection] = (inner $ p)(_.lease()).allocate
      val result              = (inner $ conn)(_.query("SELECT 1"))
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
  println("  - (scope $ x)(f) safely accesses scoped values")
  println("  - Operations are eager")
  println("  - Resources cleaned up in LIFO order on scope exit")
}
