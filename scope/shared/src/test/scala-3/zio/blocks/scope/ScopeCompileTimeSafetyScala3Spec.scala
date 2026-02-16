package zio.blocks.scope

import zio.test._
import zio.test.Assertion.isLeft

/**
 * Scala 3-only compile-time safety tests.
 *
 * Tests that require Scala 3 syntax (e.g. `derives`, union types).
 * Cross-platform tests live in ScopeSpec.
 */
object ScopeCompileTimeSafetyScala3Spec extends ZIOSpecDefault {

  def spec = suite("Scope compile-time safety (Scala 3)")(
    test("returning scope itself is rejected") {
      assertZIO(typeCheck("""
        import zio.blocks.scope._

        Scope.global.scoped { scope =>
          import scope._
          scope
        }
      """))(isLeft)
    },
    test("custom Unscoped type via derives") {
      case class TxResult(value: Int) derives Unscoped

      val result: TxResult = Scope.global.scoped { scope =>
        import scope._
        val data: $[Int] = allocate(Resource(42))
        TxResult(data.get)
      }
      assertTrue(result.value == 42)
    }
  )
}
