package zio.blocks.scope

import zio.test._

/**
 * Tests for the new Scope design with existential types.
 */
object ScopeScala3Spec extends ZIOSpecDefault {

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

  def spec = suite("Scope (Scala 3)")(
    suite("global scope")(
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      }
    ),
    suite("scope.scoped")(
      test("scoped executes block and closes scope") {
        var blockRan = false
        var cleaned  = false
        Scope.global.scoped { scope =>
          blockRan = true
          scope.defer { cleaned = true }
        }
        assertTrue(blockRan, cleaned)
      },
      test("scoped closes scope even on exception") {
        var cleaned = false
        try {
          Scope.global.scoped { scope =>
            scope.defer { cleaned = true }
            throw new RuntimeException("boom")
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(cleaned)
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
      test("scope.allocate returns tagged value and $ works") {
        Scope.global.scoped { scope =>
          val db     = scope.allocate(Resource.from[Database])
          val result = scope.$(db)(_.query("SELECT 1"))
          assertTrue(result == "result: SELECT 1")
        }
      }
    ),
    suite("scope.$ operator")(
      test("$ extracts value and applies function") {
        Scope.global.scoped { scope =>
          val config = scope.allocate(Resource(Config(true)))
          val debug  = scope.$(config)(_.debug)
          assertTrue(debug)
        }
      },
      test("$ on Unscoped type returns raw value") {
        Scope.global.scoped { scope =>
          val db     = scope.allocate(Resource.from[Database])
          val result = scope.$(db)(_.query("test"))
          assertTrue(result == "result: test")
        }
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via Tag subtyping") {
        Scope.global.scoped { parentScope =>
          val db = parentScope.allocate(Resource.from[Database])

          parentScope.scoped { childScope =>
            // Child scope should be able to access parent-tagged value
            val result = childScope.$(db)(_.query("child"))
            assertTrue(result == "result: child")
          }
        }
      },
      test("child scope closes before parent") {
        val order = scala.collection.mutable.ArrayBuffer.empty[String]

        Scope.global.scoped { parent =>
          parent.defer(order += "parent")

          parent.scoped { child =>
            child.defer(order += "child")
          }
        }

        assertTrue(order.toList == List("child", "parent"))
      }
    ),
    suite("Scoped monad")(
      test("map creates Scoped computation") {
        Scope.global.scoped { scope =>
          val db = scope.allocate(Resource.from[Database])

          val computation = db.map(_.query("mapped"))

          // Execute via scope.execute
          val result = scope.execute(computation)
          assertTrue(result == "result: mapped")
        }
      },
      test("map and Scoped.map composition") {
        Scope.global.scoped { scope =>
          val db = scope.allocate(Resource.from[Database])

          // Chain using Scoped.map
          val computation = db.map(_.query("a")).map(s => s.toUpperCase)

          val result = scope.execute(computation)
          assertTrue(result == "RESULT: A")
        }
      }
    ),
    suite("defer")(
      test("finalizers run in LIFO order") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        Scope.global.scoped { scope =>
          scope.defer(order += 1)
          scope.defer(order += 2)
          scope.defer(order += 3)
        }
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("package-level defer works with explicit Finalizer") {
        var cleaned = false
        Scope.global.scoped { scope =>
          scope.defer { cleaned = true }
        }
        assertTrue(cleaned)
      },
      test("all finalizers run even if one throws") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        try {
          Scope.global.scoped { scope =>
            scope.defer(order += 1)
            scope.defer(throw new RuntimeException("finalizer boom"))
            scope.defer(order += 3)
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(order.toList == List(3, 1))
      },
      test("block throws and finalizers throw: primary thrown, finalizer errors suppressed") {
        var caught: Throwable | Null = null
        try {
          Scope.global.scoped { scope =>
            scope.defer(throw new RuntimeException("finalizer 1"))
            scope.defer(throw new RuntimeException("finalizer 2"))
            throw new RuntimeException("block boom")
          }
        } catch {
          case t: RuntimeException => caught = t
        }
        val suppressed = caught.nn.getSuppressed
        assertTrue(
          caught != null,
          caught.nn.getMessage == "block boom",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      },
      test("block succeeds and finalizers throw multiple: first thrown, rest suppressed") {
        var caught: Throwable | Null = null
        try {
          Scope.global.scoped { scope =>
            scope.defer(throw new RuntimeException("finalizer 1"))
            scope.defer(throw new RuntimeException("finalizer 2"))
            scope.defer(throw new RuntimeException("finalizer 3"))
          }
        } catch {
          case t: RuntimeException => caught = t
        }
        val suppressed = caught.nn.getSuppressed
        assertTrue(
          caught != null,
          caught.nn.getMessage == "finalizer 3",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      },
      test("defer works with Finalizer capability") {
        var finalized = false
        Scope.global.scoped { scope =>
          scope.defer { finalized = true }
        }
        assertTrue(finalized)
      }
    ),
    suite("edge cases")(
      test("runtime cast is unsafe with sibling scopes (demonstration)") {
        Scope.global.scoped { parent =>
          var leaked: Any = null
          parent.scoped { child1 =>
            leaked = child1.allocate(Resource.from[Database])
          }
          parent.scoped { child2 =>
            val db = leaked.asInstanceOf[Database @@ child2.Tag]
            val r  = child2.$(db)(_.query("test"))
            assertTrue(r == "result: test")
          }
        }
      }
    )
  )
}
