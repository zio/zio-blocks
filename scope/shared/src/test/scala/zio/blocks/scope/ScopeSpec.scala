package zio.blocks.scope

import zio.test._

/**
 * Cross-platform tests for Scope.
 */
object ScopeSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  case class Database(url: String)

  def spec = suite("Scope")(
    suite("global")(
      test("global scope exists") {
        assertTrue(Scope.global != null)
      },
      test("testable global scope close runs finalizers") {
        val (scope, close) = Scope.createTestableScope()
        var ran            = false
        scope.defer { ran = true }
        close()
        assertTrue(ran)
      },
      test("testable global scope close propagates error") {
        val (scope, close) = Scope.createTestableScope()
        scope.defer(throw new RuntimeException("test error"))
        val result = try {
          close()
          false
        } catch {
          case e: RuntimeException => e.getMessage == "test error"
        }
        assertTrue(result)
      },
      test("testable scope runs multiple finalizers") {
        val (scope, close) = Scope.createTestableScope()
        var counter        = 0
        scope.defer(counter += 1)
        scope.defer(counter += 10)
        close()
        assertTrue(counter == 11)
      },
      test("testable scope closeOrThrow throws first exception") {
        val (scope, close) = Scope.createTestableScope()
        scope.defer(throw new RuntimeException("boom"))

        val threw = try {
          close()
          false
        } catch {
          case e: RuntimeException => e.getMessage == "boom"
        }
        assertTrue(threw)
      },
      test("testable scope closeOrThrow does not throw on success and runs finalizers") {
        val (scope, close) = Scope.createTestableScope()
        var cleaned        = false
        scope.defer { cleaned = true }
        close()
        assertTrue(cleaned)
      }
    )
  )
}
