package zio.blocks.scope

import zio.{Scope => _}
import zio.test._

/**
 * Scala 2-specific tests that Scope and Finalizer can be injected in various
 * constructor parameter positions via Wire.shared, Wire.unique, and
 * Resource.from macros.
 */
object InjectionPositionScala2Spec extends ZIOSpecDefault {

  case class Config(url: String)

  def spec = suite("Injection position (Scala 2)")(
    suite("Wire.shared")(
      test("Finalizer in first value param list") {
        var cleaned = false

        class Svc(finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope in first value param list") {
        var cleaned = false

        class Svc(scope: Scope) {
          scope.defer { cleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Finalizer in implicit param list with dep") {
        var cleaned = false

        class Svc(val config: Config)(implicit finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val wire       = Wire.shared[Svc]
        val configWire = Wire(Config("test"))
        val resource   = Resource.from[Svc](wire, configWire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope in implicit param list with dep") {
        var cleaned = false

        class Svc(val config: Config)(implicit scope: Scope) {
          scope.defer { cleaned = true }
        }

        val wire       = Wire.shared[Svc]
        val configWire = Wire(Config("test"))
        val resource   = Resource.from[Svc](wire, configWire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Finalizer as only implicit param") {
        var cleaned = false

        class Svc()(implicit finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope as only implicit param") {
        var cleaned = false

        class Svc()(implicit scope: Scope) {
          scope.defer { cleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      }
    ),
    suite("Wire.unique")(
      test("Finalizer in first value param list") {
        var cleaned = false

        class Svc(finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val wire     = Wire.unique[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope in first value param list") {
        var cleaned = false

        class Svc(scope: Scope) {
          scope.defer { cleaned = true }
        }

        val wire     = Wire.unique[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope in implicit param list with dep") {
        var cleaned = false

        class Svc(val config: Config)(implicit scope: Scope) {
          scope.defer { cleaned = true }
        }

        val wire       = Wire.unique[Svc]
        val configWire = Wire(Config("test"))
        val resource   = Resource.from[Svc](wire, configWire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Finalizer as only implicit param") {
        var cleaned = false

        class Svc()(implicit finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val wire     = Wire.unique[Svc]
        val resource = Resource.from[Svc](wire)

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      }
    ),
    suite("Resource.from (zero-dep)")(
      test("Finalizer in implicit param list") {
        var cleaned = false

        class Svc()(implicit finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val resource = Resource.from[Svc]

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope in implicit param list") {
        var cleaned = false

        class Svc()(implicit scope: Scope) {
          scope.defer { cleaned = true }
        }

        val resource = Resource.from[Svc]

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Finalizer as only value param") {
        var cleaned = false

        class Svc(finalizer: Finalizer) {
          finalizer.defer { cleaned = true }
        }

        val resource = Resource.from[Svc]

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      },
      test("Scope as only value param") {
        var cleaned = false

        class Svc(scope: Scope) {
          scope.defer { cleaned = true }
        }

        val resource = Resource.from[Svc]

        val cleanedBefore = Scope.global.scoped { scope =>
          resource.make(scope)
          cleaned
        }

        assertTrue(!cleanedBefore, cleaned)
      }
    ),
    suite("Edge cases")(
      test("Both Scope and Finalizer in implicit list") {
        var scopeCleaned     = false
        var finalizerCleaned = false

        class Svc()(implicit scope: Scope, finalizer: Finalizer) {
          scope.defer { scopeCleaned = true }
          finalizer.defer { finalizerCleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val (scopeBefore, finBefore) = Scope.global.scoped { scope =>
          resource.make(scope)
          (scopeCleaned, finalizerCleaned)
        }

        assertTrue(!scopeBefore, !finBefore, scopeCleaned, finalizerCleaned)
      },
      test("Two Scope params in implicit list") {
        var cleaned1 = false
        var cleaned2 = false

        class Svc()(implicit s1: Scope, s2: Scope) {
          s1.defer { cleaned1 = true }
          s2.defer { cleaned2 = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val (before1, before2) = Scope.global.scoped { scope =>
          resource.make(scope)
          (cleaned1, cleaned2)
        }

        assertTrue(!before1, !before2, cleaned1, cleaned2)
      }
    )
  )
}
