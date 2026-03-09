package wire

import zio.blocks.scope._
import java.util.concurrent.atomic.AtomicInteger

/**
 * Demonstrates the semantic difference between shared and unique wires:
 *   - Shared wires: same instance across dependents (reference-counted)
 *   - Unique wires: fresh instance per allocation
 *
 * Uses a counter to track how many times each service is instantiated.
 */

final class Counter {
  private val count = new AtomicInteger(0)

  def next(): Int = count.incrementAndGet()

  def value: Int = count.get()
}

final class ServiceA(counter: Counter) {
  val id = counter.next()
  println(s"[ServiceA] Initialized with counter id=$id")
}

final class ServiceB(counter: Counter) {
  val id = counter.next()
  println(s"[ServiceB] Initialized with counter id=$id")
}

final class SharedDependencyApp(a: ServiceA, b: ServiceB) {
  def checkSharing(): Boolean = {
    // If Counter is shared, both services got the same instance
    val aCountId = a.id
    val bCountId = b.id
    println(s"[SharedDependencyApp] ServiceA counter id=$aCountId, ServiceB counter id=$bCountId")
    aCountId != bCountId && a.id < b.id // Sequential IDs from same Counter
  }
}

final class UniqueDependencyApp(a: ServiceA, b: ServiceB) {
  def checkUniqueness(): Boolean = {
    // If Counter is unique, services got different instances (different starting IDs)
    val aCountId = a.id
    val bCountId = b.id
    println(s"[UniqueDependencyApp] ServiceA counter id=$aCountId, ServiceB counter id=$bCountId")
    aCountId != bCountId // Different Counter instances
  }
}

@main def wireSharedUniqueExample(): Unit = {
  println("=== Wire Shared vs Unique Example ===\n")

  println("--- Test 1: Shared Counter (diamond pattern) ---\n")

  // Using a SHARED Counter: both ServiceA and ServiceB share the same Counter instance
  val sharedResource: Resource[SharedDependencyApp] = Resource.from[SharedDependencyApp](
    Wire.shared[Counter] // Shared: one instance across the graph
  )

  Scope.global.scoped { scope =>
    import scope._

    println("[Scope] Entering scoped region\n")

    val app      = allocate(sharedResource)
    val isShared = $(app)(_.checkSharing())

    println(s"\n[Result] Counter was shared: $isShared\n")
  }

  println("\n--- Test 2: Unique Counter ---\n")

  // Using a UNIQUE Counter: each dependency gets a fresh Counter instance
  val uniqueResource: Resource[UniqueDependencyApp] = Resource.from[UniqueDependencyApp](
    Wire.unique[Counter] // Unique: fresh instance per dependency
  )

  Scope.global.scoped { scope =>
    import scope._

    println("[Scope] Entering scoped region\n")

    val app      = allocate(uniqueResource)
    val isUnique = $(app)(_.checkUniqueness())

    println(s"\n[Result] Counters were unique: $isUnique\n")
  }

  println("\n=== Example Complete ===")
}
