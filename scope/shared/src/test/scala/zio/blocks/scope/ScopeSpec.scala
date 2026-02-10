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
    ),
    suite("allocate")(
      test("allocate AutoCloseable directly registers close() as finalizer") {
        val (scope, close) = Scope.createTestableScope()
        var closed         = false

        class TestCloseable extends AutoCloseable {
          def value: String          = "test"
          override def close(): Unit = closed = true
        }

        val resource = scope.allocate(new TestCloseable)
        // Capture result via side effect in the function passed to $
        var captured: String = null
        scope.$(resource) { r =>
          captured = r.value
          r.value
        }

        assertTrue(captured == "test", !closed)
        close()
        assertTrue(closed)
      }
    ),
    suite("closed scope safety")(
      // These tests verify that $ and execute become lazy when called on a
      // closed scope, preventing use-after-close vulnerabilities.
      test("$ does not execute when scope is closed") {
        var readAttempted = false

        class TrackedResource extends AutoCloseable {
          var closed      = false
          def read(): Int = {
            readAttempted = true
            42
          }
          def close(): Unit = closed = true
        }

        // Capture a thunk that calls scope.$ after the scope is closed
        val lazyThunk: () => Unit = Scope.global.scoped { parent =>
          var capturedThunk: () => Unit = null

          parent.scoped { child =>
            val resource: TrackedResource @@ child.Tag =
              child.allocate(Resource(new TrackedResource))

            // Capture a thunk that uses the child scope's $ method
            capturedThunk = () => {
              // This calls child.$ on a CLOSED scope - should stay lazy
              child.$(resource)(_.read())
              ()
            }

            () // Return Unit (Unscoped)
          }
          // child scope is now CLOSED

          capturedThunk // Return the thunk that captures the closed scope
        }

        // Execute the thunk - since scope is closed, $ should stay lazy
        // and NOT actually execute the read
        lazyThunk()

        assertTrue(!readAttempted) // Read was NOT attempted because $ stayed lazy
      },
      test("execute does not run when scope is closed") {
        var executedAfterClose = false

        class TrackedResource extends AutoCloseable {
          var closed           = false
          def doWork(): String = {
            executedAfterClose = true
            "done"
          }
          def close(): Unit = closed = true
        }

        val lazyThunk: () => Unit = Scope.global.scoped { parent =>
          var capturedThunk: () => Unit = null

          parent.scoped { child =>
            val resource: TrackedResource @@ child.Tag =
              child.allocate(Resource(new TrackedResource))

            // Build a scoped computation
            val computation = resource.map(_.doWork())

            // Capture a thunk that uses the child scope's execute method
            capturedThunk = () => {
              child.execute(computation)
              ()
            }

            ()
          }

          capturedThunk
        }

        lazyThunk()

        assertTrue(!executedAfterClose) // Work was NOT done because execute stayed lazy
      },
      test("multiple calls on closed scope all stay lazy") {
        var callCount = 0

        class Counter extends AutoCloseable {
          var closed        = false
          def inc(): Int    = { callCount += 1; callCount }
          def close(): Unit = closed = true
        }

        val lazyThunk: () => Unit = Scope.global.scoped { parent =>
          var capturedThunk: () => Unit = null

          parent.scoped { child =>
            val counter: Counter @@ child.Tag =
              child.allocate(Resource(new Counter))

            capturedThunk = () => {
              // All these should stay lazy on a closed scope
              child.$(counter)(_.inc())
              child.$(counter)(_.inc())
              child.$(counter)(_.inc())
              ()
            }

            ()
          }

          capturedThunk
        }

        lazyThunk()

        assertTrue(callCount == 0) // No calls executed because scope was closed
      },
      test("$ executes eagerly when scope is open") {
        val (scope, close) = Scope.createTestableScope()
        var executed       = false

        class TrackedResource extends AutoCloseable {
          def doWork(): Unit = executed = true
          def close(): Unit  = ()
        }

        val resource = scope.allocate(Resource(new TrackedResource))
        scope.$(resource)(_.doWork())
        close()
        assertTrue(executed)
      },
      test("execute runs eagerly when scope is open") {
        val (scope, close) = Scope.createTestableScope()
        var executed       = false

        class TrackedResource extends AutoCloseable {
          def doWork(): Boolean = { executed = true; true }
          def close(): Unit     = ()
        }

        val resource    = scope.allocate(Resource(new TrackedResource))
        val computation = resource.map(_.doWork())
        scope.execute(computation)
        close()
        assertTrue(executed)
      },
      test("eager does not accidentally unwrap nested lazy scoped values") {
        val (scope, close) = Scope.createTestableScope()
        var evaluated      = false

        val base: Int @@ scope.Tag  = scope.allocate(Resource(1))
        val inner: Int @@ scope.Tag = base.map { x =>
          evaluated = true
          x + 1
        }

        val nested: (Int @@ scope.Tag) @@ scope.Tag =
          scope.$(base)(_ => inner)

        val inner2: Int @@ scope.Tag = @@.unscoped(nested)

        val notEvaluatedYet = !evaluated

        val value: Int = @@.unscoped(inner2)
        close()
        assertTrue(notEvaluatedYet, evaluated, value == 2)
      }
    )
  )
}
