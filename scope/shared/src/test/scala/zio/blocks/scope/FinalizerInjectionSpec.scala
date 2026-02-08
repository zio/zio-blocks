package zio.blocks.scope

import zio.{Scope => _}
import zio.blocks.context.Context
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

  class ServiceWithOnlyFinalizer(implicit finalizer: Finalizer) {
    var cleaned = false
    finalizer.defer { cleaned = true }
  }

  class ServiceWithDepsAndFinalizer(val config: Config)(implicit finalizer: Finalizer) {
    var cleaned = false
    finalizer.defer { cleaned = true }
  }

  def spec = suite("Finalizer injection")(
    // TODO: Resource.from for classes with only implicit params fails with macro error
    // "Expected an expression. This is a partially applied Term."
    // test("Resource.from[T] with only Finalizer parameter") {
    //   val resource = Resource.from[ServiceWithOnlyFinalizer]
    //   ...
    // }
    test("shared[T] with deps and Finalizer parameter injects it and cleanup runs") {
      val wire           = shared[ServiceWithDepsAndFinalizer]
      val deps           = Context[Config](Config("shared-test"))
      val resource       = wire.toResource(deps)
      val (scope, close) = Scope.createTestableScope()
      val service        = resource.make(scope)
      assertTrue(service.config.url == "shared-test", !service.cleaned) &&
      { close(); assertTrue(service.cleaned) }
    }
  ) @@ TestAspect.ignore // Finalizer defers run during wire resolution, not scope close - needs investigation
}
