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
      var finalizerClass: String = ""

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        var cleaned = false
        finalizerClass = finalizer.getClass.getName
        finalizer.defer { cleaned = true }
      }

      val wire           = Wire.shared[Service]
      val configWire     = Wire(Config("test"))
      val resource       = Resource.from[Service](wire, configWire)
      val (scope, close) = Scope.createTestableScope()
      val service        = resource.make(scope)

      val cleanedBefore = service.cleaned
      close()
      val cleanedAfter = service.cleaned

      assertTrue(
        finalizerClass.contains("ProxyFinalizer"),
        !cleanedBefore,
        cleanedAfter
      )
    },
    test("Wire.shared[T] multiple defers run in LIFO order") {
      var order: List[Int] = Nil

      class Service(val n: Int)(implicit finalizer: Finalizer) {
        finalizer.defer { order = 1 :: order }
        finalizer.defer { order = 2 :: order }
        finalizer.defer { order = 3 :: order }
      }

      val wire           = Wire.shared[Service]
      val intWire        = Wire(42)
      val resource       = Resource.from[Service](wire, intWire)
      val (scope, close) = Scope.createTestableScope()
      resource.make(scope)
      close()

      assertTrue(order == List(1, 2, 3)) // LIFO: 3 registered last, runs first
    },
    test("Wire.unique[T] injects Finalizer and cleanup runs") {
      class Service(val config: Config)(implicit finalizer: Finalizer) {
        var cleaned = false
        finalizer.defer { cleaned = true }
      }

      val wire           = Wire.unique[Service]
      val configWire     = Wire(Config("test"))
      val resource       = Resource.from[Service](wire, configWire)
      val (scope, close) = Scope.createTestableScope()
      val service        = resource.make(scope)

      val cleanedBefore = service.cleaned
      close()
      val cleanedAfter = service.cleaned

      // The important thing is cleanup runs correctly
      assertTrue(
        !cleanedBefore,
        cleanedAfter
      )
    }
  )
}
