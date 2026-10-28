package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates how to break a circular dependency in ZIO Blocks Scope.
 *
 * The `Resource.from[T]` macro analyzes the dependency graph at compile time.
 * It is meant to reject circular dependencies with an error that shows the
 * cycle path, but in 0.0.56 a cycle makes the macro crash instead (a known bug,
 * #1767), so the cycle shown below is a shape to avoid.
 *
 * ==The Problem==
 * Circular dependencies (A → B → A) cannot be resolved by constructor injection
 * because neither service can be instantiated without the other already
 * existing.
 *
 * ==The Solution==
 * Break the cycle by introducing an interface (trait) that one service depends
 * on, allowing the implementation to be provided separately. This is a standard
 * Dependency Inversion Principle pattern.
 */

// ─────────────────────────────────────────────────────────────────────────────
// PROBLEMATIC: Circular Dependency (do not compile)
// ─────────────────────────────────────────────────────────────────────────────

// These classes form a cycle: ServiceA → ServiceB → ServiceA
// Uncommenting the Resource.from call below crashes the macro in 0.0.56 (#1767);
// it does not print a cycle diagram.

// class ServiceA(b: ServiceB) {
//   def greet(): String = s"A says hello, B says: ${b.respond()}"
// }
//
// class ServiceB(a: ServiceA) {
//   def respond(): String = s"B responds, A type: ${a.getClass.getSimpleName}"
// }
//
// Attempting to wire this should fail at compile time:
// val circularResource = Resource.from[ServiceA]()
//
// Intended compile error (not produced in 0.0.56):
// ┌────────────────────────────┐
// │                            ▼
//     ServiceA ──► ServiceB
// ▲                            │
// └────────────────────────────┘
//
// Break the cycle by:
//   • Introducing an interface/trait
//   • Using lazy initialization
//   • Restructuring dependencies

// ─────────────────────────────────────────────────────────────────────────────
// SOLUTION: Break the cycle with an interface
// ─────────────────────────────────────────────────────────────────────────────

/** Interface that ServiceA depends on, breaking the compile-time cycle. */
trait ServiceBApi {
  def respond(): String
}

/** Concrete implementation of ServiceA that depends only on the interface. */
class ServiceAImpl(b: ServiceBApi) {
  def greet(): String = s"A says hello, B says: ${b.respond()}"
}

/** Concrete implementation of ServiceB without any dependency on A. */
class ServiceBImpl extends ServiceBApi {
  override def respond(): String = "B responds successfully"
}

/** Application that composes both services. */
class Application(a: ServiceAImpl, @annotation.unused b: ServiceBApi) {
  def run(): String = a.greet()
}

/**
 * Demonstrates the working (non-circular) pattern.
 *
 * The dependency graph is now: Application → ServiceAImpl → ServiceBApi ↘
 * ServiceBApi
 *
 * ServiceBImpl provides ServiceBApi, and there is no cycle.
 */
@main def circularDependencyDemoExample(): Unit = {
  println("=== Circular Dependency Demo ===\n")
  println("Demonstrating how to break a dependency cycle.\n")

  Scope.global.scoped { scope =>
    import scope._
    println("Creating application with proper dependency structure...")

    // Wire.shared[ServiceBImpl] provides both ServiceBImpl and ServiceBApi (via subtyping)
    val app: $[Application] = allocate(
      Resource.from[Application](
        Wire.shared[ServiceBImpl]
      )
    )

    println(s"Result: ${$(app)(_.run())}")
    println("\nNo cycles in the dependency graph - application wired successfully.")
  }

  println("\n─── Key Takeaways ───")
  println("• Resource.from[T] should reject cycles at compile time (not yet in 0.0.56)")
  println("• Break cycles by introducing interfaces/traits")
  println("• The Dependency Inversion Principle resolves most cycles")
}
