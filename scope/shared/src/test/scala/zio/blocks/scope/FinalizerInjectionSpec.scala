package zio.blocks.scope

import zio.{Scope => _}
import zio.test._

/**
 * Tests that verify Finalizer and Scope injection works correctly in the
 * wire/resource macros.
 *
 * Classes can accept an implicit Finalizer or Scope parameter to register
 * cleanup logic. The macros automatically inject the appropriate value when
 * constructing such classes.
 */
object FinalizerInjectionSpec extends ZIOSpecDefault {

  case class Config(url: String)

  def spec = suite("Finalizer injection")(
    test("Wire.shared[T] injects Scope and cleanup runs on scope close") {
      var cleaned = false

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        finalizer.defer { cleaned = true }
      }

      val wire       = Wire.shared[Service]
      val configWire = Wire(Config("test"))
      val resource   = Resource.from[Service](wire, configWire)

      val cleanedBefore = Scope.global.scoped { scope =>
        resource.make(scope)
        cleaned
      }

      assertTrue(
        !cleanedBefore,
        cleaned
      )
    },
    test("Wire.shared[T] multiple defers run in LIFO order") {
      var order: List[Int] = Nil

      class Service(val n: Int)(implicit finalizer: Finalizer) {
        finalizer.defer { order = 1 :: order }
        finalizer.defer { order = 2 :: order }
        finalizer.defer { order = 3 :: order }
      }

      val wire     = Wire.shared[Service]
      val intWire  = Wire(42)
      val resource = Resource.from[Service](wire, intWire)

      Scope.global.scoped { scope =>
        val _ = resource.make(scope)
      }

      assertTrue(order == List(1, 2, 3)) // LIFO: 3 registered last, runs first
    },
    test("Wire.unique[T] injects Scope and cleanup runs") {
      var cleaned = false

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        finalizer.defer { cleaned = true }
      }

      val wire       = Wire.unique[Service]
      val configWire = Wire(Config("test"))
      val resource   = Resource.from[Service](wire, configWire)

      val cleanedBefore = Scope.global.scoped { scope =>
        resource.make(scope)
        cleaned
      }

      // The important thing is cleanup runs correctly
      assertTrue(
        !cleanedBefore,
        cleaned
      )
    },
    test("Wire.shared[T] injects Scope when class takes implicit Scope") {
      var cleaned    = false
      var scopeClass = ""

      class Service(val config: Config)(implicit scope: Scope) {
        scopeClass = scope.getClass.getName
        scope.defer { cleaned = true }
      }

      val wire       = Wire.shared[Service]
      val configWire = Wire(Config("test"))
      val resource   = Resource.from[Service](wire, configWire)

      val cleanedBefore = Scope.global.scoped { scope =>
        resource.make(scope)
        cleaned
      }

      assertTrue(
        scopeClass.contains("Child"),
        !cleanedBefore,
        cleaned
      )
    },
    test("Injected Scope allows creating child scopes via scoped") {
      var childScopeUsed = false

      class Service(val config: Config)(implicit scope: Scope) {
        scope.scoped { _ =>
          childScopeUsed = true
        }
      }

      val wire       = Wire.shared[Service]
      val configWire = Wire(Config("test"))
      val resource   = Resource.from[Service](wire, configWire)

      Scope.global.scoped { scope =>
        val _ = resource.make(scope)
      }

      assertTrue(childScopeUsed)
    },
    test("DeferHandle.cancel() truly removes the entry") {
      val fins   = new zio.blocks.scope.internal.Finalizers
      var called = false
      val handle = fins.add { called = true }
      assertTrue(fins.size == 1)
      handle.cancel()
      assertTrue(fins.size == 0)
      fins.runAll()
      assertTrue(!called)
    },
    test("Resource.Shared with scope injection works") {
      var cleanupRan = false

      val resource = Resource.shared[String] { scope =>
        scope.defer { cleanupRan = true }
        "shared-value"
      }

      val result = Scope.global.scoped { scope =>
        resource.make(scope)
      }

      assertTrue(result == "shared-value", cleanupRan)
    }
  )
}
