package zio.blocks.scope

import zio.test._

/**
 * Tests for the new Scope design with opaque types (Scala 2 version).
 *
 * Now uses the macro-based `scoped { child => ... }` syntax which works
 * natively in Scala 2 without SAM conversion issues.
 */
object ScopeScala2Spec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  def spec = suite("Scope (Scala 2)")(
    suite("global scope")(
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      }
    ),
    suite("macro-based scoped method")(
      test("scoped with lambda syntax") {
        val result: Int = Scope.global.scoped { child =>
          child.wrap(42)
        }
        assertTrue(result == 42)
      },
      test("scoped returns plain Unscoped type") {
        val result: String = Scope.global.scoped { _ =>
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("scoped unwraps child.$[A] to A") {
        val result: Int = Scope.global.scoped { child =>
          child.wrap(100)
        }
        assertTrue(result == 100)
      },
      test("nested scoped blocks work") {
        val result: Int = Scope.global.scoped { outer =>
          val x: Int = Scope.global.scoped { inner =>
            inner.wrap(10)
          }
          outer.wrap(x + 5)
        }
        assertTrue(result == 15)
      }
    ),
    suite("Resource.from macro")(
      test("Resource.from[T] macro derives from no-arg constructor") {
        val resource       = Resource.from[Database]
        val (scope, close) = Scope.createTestableScope()
        val db             = resource.make(scope)
        close()
        assertTrue(db.isInstanceOf[Database])
      },
      test("Resource.from[T] macro handles AutoCloseable") {
        val resource       = Resource.from[Database]
        val (scope, close) = Scope.createTestableScope()
        val db             = resource.make(scope)
        assertTrue(!db.closed)
        close()
        assertTrue(db.closed)
      },
      test("allocate returns scoped value and $ works") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var captured: String = null
        val db: $[Database]  = allocate(Resource.from[Database])
        $(db) { d =>
          val result = d.query("SELECT 1")
          captured = result
          result
        }
        close()
        assertTrue(captured == "result: SELECT 1")
      }
    ),
    suite("$ operator")(
      test("$ extracts value and applies function") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var captured: Boolean = false
        val config: $[Config] = allocate(Resource(Config(true)))
        $(config) { c =>
          val debug = c.debug
          captured = debug
          debug
        }
        close()
        assertTrue(captured == true)
      },
      test("$ always returns scoped value") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var captured: String = null
        val db: $[Database]  = allocate(Resource.from[Database])
        $(db) { d =>
          val result = d.query("test")
          captured = result
          result
        }
        close()
        assertTrue(captured == "result: test")
      }
    ),
    suite("Scoped monad")(
      test("map creates Scoped computation") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var captured: String = null
        val db: $[Database]  = allocate(Resource.from[Database])
        val computation      = db.map { d =>
          val result = d.query("mapped")
          captured = result
          result
        }
        locally(computation)
        close()
        assertTrue(captured == "result: mapped")
      },
      test("map and $ composition") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var captured: String       = null
        val db: $[Database]        = allocate(Resource.from[Database])
        val computation: $[String] = db.map(_.query("a"))
        $(computation) { query =>
          val result = query.toUpperCase
          captured = result
          result
        }
        close()
        assertTrue(captured == "RESULT: A")
      }
    ),
    suite("defer")(
      test("finalizers run in LIFO order") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        defer(order += 1)
        defer(order += 2)
        defer(order += 3)
        close()
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("package-level defer works with explicit Finalizer") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var cleaned = false
        defer { cleaned = true }
        close()
        assertTrue(cleaned)
      },
      test("defer works with Finalizer capability") {
        val (scope, close) = Scope.createTestableScope()
        import scope._
        var finalized = false
        defer { finalized = true }
        close()
        assertTrue(finalized)
      }
    )
  )
}
