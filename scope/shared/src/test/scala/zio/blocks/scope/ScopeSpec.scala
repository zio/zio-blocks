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
        import scope._
        var ran = false
        defer { ran = true }
        close()
        assertTrue(ran)
      },
      test("testable global scope close propagates error") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        defer(throw new RuntimeException("test error"))
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
        import scope._
        var counter = 0
        defer(counter += 1)
        defer(counter += 10)
        close()
        assertTrue(counter == 11)
      },
      test("testable scope closeOrThrow throws first exception") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        defer(throw new RuntimeException("boom"))

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
        import scope._
        var cleaned = false
        defer { cleaned = true }
        close()
        assertTrue(cleaned)
      }
    ),
    suite("allocate")(
      test("allocate AutoCloseable directly registers close() as finalizer") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var closed = false

        class TestCloseable extends AutoCloseable {
          def value: String          = "test"
          override def close(): Unit = closed = true
        }

        val resource: $[TestCloseable] = allocate(new TestCloseable)
        var captured: String           = null
        $(resource) { r =>
          captured = r.value
          r.value
        }

        assertTrue(captured == "test", !closed)
        close()
        assertTrue(closed)
      }
    ),
    suite("eager operations")(
      test("$ executes eagerly when scope is open") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var executed = false

        class TrackedResource extends AutoCloseable {
          def doWork(): Unit = executed = true
          def close(): Unit  = ()
        }

        val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
        $(resource)(_.doWork())
        close()
        assertTrue(executed)
      },
      test("map executes eagerly when scope is open") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var executed = false

        class TrackedResource extends AutoCloseable {
          def doWork(): Boolean = { executed = true; true }
          def close(): Unit     = ()
        }

        val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
        locally(resource.map(_.doWork()))
        close()
        assertTrue(executed)
      },
      test("nested scoped values evaluate eagerly") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var evaluated = false

        val base: $[Int]  = allocate(Resource(1))
        val inner: $[Int] = base.map { x =>
          evaluated = true
          x + 1
        }

        val nested: $[$[Int]] = $(base)(_ => inner)

        locally(nested.flatMap(identity))

        assertTrue(evaluated)
        close()
        assertCompletes
      }
    )
  )
}
