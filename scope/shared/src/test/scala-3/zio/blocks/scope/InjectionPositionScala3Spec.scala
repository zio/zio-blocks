package zio.blocks.scope

import zio.{Scope => _}
import zio.test._

/**
 * Tests that Scope and Finalizer can be injected in various constructor
 * parameter positions via the Wire and Resource macros in Scala 3.
 *
 * Covers value param lists, second param lists, `using` param lists, and edge
 * cases with multiple injected params.
 */
object InjectionPositionScala3Spec extends ZIOSpecDefault {

  case class Config(url: String)

  def spec = suite("Injection position (Scala 3)")(
    suite("Wire macros")(
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
      test("Finalizer in second value param list") {
        var cleaned = false

        class Svc(val config: Config)(finalizer: Finalizer) {
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
      test("Scope in second value param list") {
        var cleaned = false

        class Svc(val config: Config)(scope: Scope) {
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
      test("Finalizer in using param list") {
        var cleaned = false

        class Svc(val config: Config)(using finalizer: Finalizer) {
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
      test("Scope in using param list") {
        var cleaned = false

        class Svc(val config: Config)(using scope: Scope) {
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
      test("Finalizer as only param (using)") {
        var cleaned = false

        class Svc(using finalizer: Finalizer) {
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
      test("Scope as only param (using)") {
        var cleaned = false

        class Svc(using scope: Scope) {
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
      test("Wire.unique injects Finalizer in first value param list") {
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
      test("Both Scope and Finalizer in same param list") {
        var scopeCleaned     = false
        var finalizerCleaned = false

        class Svc(scope: Scope, finalizer: Finalizer) {
          scope.defer { scopeCleaned = true }
          finalizer.defer { finalizerCleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val before = Scope.global.scoped { scope =>
          resource.make(scope)
          (scopeCleaned, finalizerCleaned)
        }

        assertTrue(!before._1, !before._2, scopeCleaned, finalizerCleaned)
      },
      test("Two Scope params in same param list") {
        var cleaned1 = false
        var cleaned2 = false

        class Svc(s1: Scope, s2: Scope) {
          s1.defer { cleaned1 = true }
          s2.defer { cleaned2 = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val before = Scope.global.scoped { scope =>
          resource.make(scope)
          (cleaned1, cleaned2)
        }

        assertTrue(!before._1, !before._2, cleaned1, cleaned2)
      },
      test("Scope in value list + Finalizer in using list") {
        var scopeCleaned     = false
        var finalizerCleaned = false

        class Svc(scope: Scope)(using finalizer: Finalizer) {
          scope.defer { scopeCleaned = true }
          finalizer.defer { finalizerCleaned = true }
        }

        val wire     = Wire.shared[Svc]
        val resource = Resource.from[Svc](wire)

        val before = Scope.global.scoped { scope =>
          resource.make(scope)
          (scopeCleaned, finalizerCleaned)
        }

        assertTrue(!before._1, !before._2, scopeCleaned, finalizerCleaned)
      }
    ),
    suite("Resource.from (zero-dependency)")(
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
      test("Scope in using param list") {
        var cleaned = false

        class Svc()(using scope: Scope) {
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
    )
  )
}
