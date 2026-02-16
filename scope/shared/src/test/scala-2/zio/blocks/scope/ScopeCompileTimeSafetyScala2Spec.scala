package zio.blocks.scope

import zio.test._
import zio.test.Assertion.{containsString, isLeft}

/**
 * Scala 2-only compile-time safety tests.
 *
 * Tests that verify the Scala 2 specific compile error behavior. Cross-platform
 * tests live in ScopeSpec.
 */
object ScopeCompileTimeSafetyScala2Spec extends ZIOSpecDefault {

  def spec = suite("Scope compile-time safety (Scala 2)")(
    test("scoped rejects non-Unscoped return type") {
      assertZIO(typeCheck("""
        import zio.blocks.scope._

        class Database extends AutoCloseable {
          var closed = false
          def query(sql: String): String = s"res: $sql"
          def close(): Unit = closed = true
        }

        // The scoped block rejects () => String since there's no Unscoped instance
        Scope.global.scoped { child =>
          import child._
          () => "leaked"
        }
      """))(isLeft(containsString("Unscoped")))
    }
  )
}
