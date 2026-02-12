package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates the `$[A]` scoped type with for-comprehension chaining.
 *
 * This example showcases the key design of ZIO Blocks Scope:
 *   - `allocate` returns `$[A]` (a scoped value)
 *   - Multiple allocates can be chained in for-comprehensions
 *   - Operations are eager (zero-cost wrapper)
 *   - All resources are cleaned up in LIFO order when the scope exits
 *
 * The for-comprehension pattern is especially useful when:
 *   - Resources depend on each other (connection depends on pool)
 *   - You want declarative resource composition
 *   - You need to combine resource acquisition with pure computations
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

@main def scopedForComprehensionExample(): Unit = {
  println("=== Scoped For-Comprehension Example ===\n")

  println("--- Pattern 1: Chaining allocates in for-comprehension ---")
  Scope.global.scoped { scope =>
    import scope._
    // Build a for-comprehension that chains resource allocations
    val result: $[String] = for {
      pool <- allocate(Resource.from[Pool])
      conn <- allocate(Resource(pool.lease()))
    } yield conn.query("SELECT * FROM users").value.toUpperCase

    // Access the result
    println(s"\n  Result: ${scope.$(result)(identity)}")
    // Scope exits: Connection closed, then Pool closed (LIFO)
  }

  println("\n--- Pattern 2: Mixing allocates with pure computations ---")
  Scope.global.scoped { scope =>
    import scope._
    val result: $[String] = for {
      pool   <- allocate(Resource.from[Pool])
      conn   <- allocate(Resource(pool.lease()))
      prefix <- scoped("PREFIX: ")
    } yield prefix + conn.query("SELECT name FROM employees").value

    println(s"\n  Result: ${scope.$(result)(identity)}")
  }

  println("\n--- Pattern 3: Nested scopes with for-comprehensions ---")
  Scope.global.scoped { outer =>
    import outer._
    println("\n  [outer] Creating pool")
    val pool: $[Pool] = allocate(Resource.from[Pool])

    // Child scope for a unit of work
    scoped { inner =>
      import inner._
      println("  [inner] Acquiring connection from parent's pool")
      val result: $[String] = for {
        // Access parent-scoped pool via lower
        p    <- lower(pool)
        conn <- allocate(Resource(p.lease()))
      } yield conn.query("SELECT 1").value

      val output = inner.$(result)(identity)
      println(s"  [inner] Result: $output")
      // inner scope exits: Connection released
    }
    println("  [outer] After inner scope - connection released, pool still alive")
    // outer scope exits: Pool closed
  }

  println("\n=== Example Complete ===")
  println("\nKey insights:")
  println("  - $[A] is the scoped value type (zero-cost wrapper)")
  println("  - allocate returns $[A]")
  println("  - flatMap chains scoped computations")
  println("  - Operations are eager")
  println("  - Resources cleaned up in LIFO order on scope exit")
}
