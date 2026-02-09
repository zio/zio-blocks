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
    test("shared[T] injects ProxyFinalizer and cleanup runs on scope close") {
      var finalizerClass: String = ""

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        var cleaned = false
        finalizerClass = finalizer.getClass.getName
        defer { cleaned = true }
      }

      val wire           = shared[Service]
      val configWire     = Wire(Config("test"))
      val resource       = wire.toResource(configWire)
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
    test("shared[T] multiple defers run in LIFO order") {
      var order: List[Int] = Nil

      class Service(val n: Int)(implicit finalizer: Finalizer) {
        defer { order = 1 :: order }
        defer { order = 2 :: order }
        defer { order = 3 :: order }
      }

      val wire           = shared[Service]
      val intWire        = Wire(42)
      val resource       = wire.toResource(intWire)
      val (scope, close) = Scope.createTestableScope()
      resource.make(scope)
      close()

      assertTrue(order == List(1, 2, 3)) // LIFO: 3 registered last, runs first
    },
    test("unique[T] injects Finalizer directly and cleanup runs") {
      var finalizerClass: String = ""

      class Service(val config: Config)(implicit finalizer: Finalizer) {
        var cleaned = false
        finalizerClass = finalizer.getClass.getName
        defer { cleaned = true }
      }

      val wire           = unique[Service]
      val configWire     = Wire(Config("test"))
      val resource       = wire.toResource(configWire)
      val (scope, close) = Scope.createTestableScope()
      val service        = resource.make(scope)

      val cleanedBefore = service.cleaned
      close()
      val cleanedAfter = service.cleaned

      // unique uses Resource.Unique which passes scope's Finalizer directly
      assertTrue(
        finalizerClass.contains("Finalizers") || finalizerClass.contains("Scope"),
        !cleanedBefore,
        cleanedAfter
      )
    }
  )
}
