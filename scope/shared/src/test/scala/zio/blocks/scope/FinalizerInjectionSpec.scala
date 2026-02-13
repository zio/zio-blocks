package zio.blocks.scope

import zio.{Scope => _}
import zio.test._

/**
 * Tests that verify Finalizer injection works correctly in the wire/resource
 * macros.
 *
 * Classes can accept an implicit Finalizer parameter to register cleanup logic.
 * The macros automatically inject the Finalizer when constructing such classes.
 */
object FinalizerInjectionSpec extends ZIOSpecDefault {

  case class Config(url: String)

  def spec = suite("Finalizer injection")(
    test("Wire.shared[T] injects ProxyFinalizer and cleanup runs on scope close") {
      var finalizerClass = ""
      var cleaned        = false

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        finalizerClass = finalizer.getClass.getName
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
        finalizerClass.contains("ProxyFinalizer"),
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
    test("Wire.unique[T] injects Finalizer and cleanup runs") {
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
    }
  )
}
