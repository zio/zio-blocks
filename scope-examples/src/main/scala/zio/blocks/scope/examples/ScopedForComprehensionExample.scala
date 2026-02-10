package zio.blocks.scope.examples

import zio.blocks.scope._

/**
 * Demonstrates the unified `A @@ S` scoped type with for-comprehension
 * chaining.
 *
 * This example showcases the key design of ZIO Blocks Scope:
 *   - `scope.allocate` returns `A @@ scope.Tag` (a deferred scoped computation)
 *   - Multiple allocates can be chained in for-comprehensions
 *   - The resulting computation is executed via `scope.execute`
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
    // Build a program that chains resource allocations
    val program: String @@ scope.Tag = for {
      pool <- scope.allocate(Resource.from[Pool])
      conn <- scope.allocate(Resource(pool.lease()))
    } yield conn.query("SELECT * FROM users").value.toUpperCase

    // Execute the program - resources acquired, computation runs
    println(s"\n  Result: ${scope.execute(program)}")
    // Scope exits: Connection closed, then Pool closed (LIFO)
  }

  println("\n--- Pattern 2: Mixing allocates with pure computations ---")
  Scope.global.scoped { scope =>
    val program: String @@ scope.Tag = for {
      pool   <- scope.allocate(Resource.from[Pool])
      conn   <- scope.allocate(Resource(pool.lease()))
      prefix <- Scoped("PREFIX: ") // lift pure value into Scoped
    } yield prefix + conn.query("SELECT name FROM employees").value

    println(s"\n  Result: ${scope.execute(program)}")
  }

  println("\n--- Pattern 3: Nested scopes with for-comprehensions ---")
  Scope.global.scoped { outer =>
    println("\n  [outer] Creating pool")
    val pool = outer.allocate(Resource.from[Pool])

    // Child scope for a unit of work
    outer.scoped { inner =>
      println("  [inner] Acquiring connection from parent's pool")
      val program: String @@ inner.Tag = for {
        // Access parent-scoped pool (contravariance allows this)
        p    <- pool
        conn <- inner.allocate(Resource(p.lease()))
      } yield conn.query("SELECT 1").value

      val output = inner.execute(program)
      println(s"  [inner] Result: $output")
      // inner scope exits: Connection released
    }
    println("  [outer] After inner scope - connection released, pool still alive")
    // outer scope exits: Pool closed
  }

  println("\n=== Example Complete ===")
  println("\nKey insights:")
  println("  - A @@ S is a deferred computation (thunk)")
  println("  - scope.allocate returns A @@ scope.Tag")
  println("  - flatMap chains scoped computations")
  println("  - scope.execute runs the composed program")
  println("  - Resources cleaned up in LIFO order on scope exit")
}
