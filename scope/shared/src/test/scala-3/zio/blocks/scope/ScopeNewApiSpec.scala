package zio.blocks.scope

import zio.test._

/**
 * Tests for the new Scope design with existential types.
 */
object ScopeNewApiSpec extends ZIOSpecDefault {

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

  class ResourceWithScope()(using scope: Scope[?, ?]) {
    var cleanedUp = false
    scope.defer { cleanedUp = true }
  }

  def spec = suite("New Scope API")(
    suite("global scope")(
      test("global scope exists") {
        assertTrue(Scope.global != null)
      },
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      },
      test("testable global scope close runs finalizers") {
        val (scope, close) = Scope.createTestableScope()
        var ran            = false
        scope.defer { ran = true }
        close()
        assertTrue(ran)
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
    suite("scope.create and Resource")(
      test("Resource[T] macro derives from no-arg constructor") {
        val resource       = Resource[Database]
        val (scope, close) = Scope.createTestableScope()
        val db             = resource.make(scope)
        close()
        assertTrue(db.isInstanceOf[Database])
      },
      test("Resource[T] macro handles AutoCloseable") {
        val resource       = Resource[Database]
        val (scope, close) = Scope.createTestableScope()
        val db             = resource.make(scope)
        assertTrue(!db.closed)
        close()
        assertTrue(db.closed)
      },
      test("scope.create returns tagged value and $ works") {
        Scope.global.scoped { scope =>
          val db     = scope.create(Resource[Database])
          val result = scope.$(db)(_.query("SELECT 1"))
          assertTrue(result == "result: SELECT 1")
        }
      }
    ),
    suite("scope.$ operator")(
      test("$ extracts value and applies function") {
        Scope.global.scoped { scope =>
          val config = scope.create(Resource(Config(true)))
          val debug  = scope.$(config)(_.debug)
          assertTrue(debug)
        }
      },
      test("$ on Unscoped type returns raw value") {
        Scope.global.scoped { scope =>
          val db     = scope.create(Resource[Database])
          val result = scope.$(db)(_.query("test"))
          assertTrue(result == "result: test")
        }
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via Tag subtyping") {
        Scope.global.scoped { parentScope =>
          val db = parentScope.create(Resource[Database])

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
          val db = scope.create(Resource[Database])

          val computation = db.map(_.query("mapped"))

          // Execute via scope.apply
          val result = scope(computation)
          assertTrue(result == "result: mapped")
        }
      },
      test("map and Scoped.map composition") {
        Scope.global.scoped { scope =>
          val db = scope.create(Resource[Database])

          // Chain using Scoped.map
          val computation = db.map(_.query("a")).map(s => s.toUpperCase)

          val result = scope(computation)
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
      test("package-level defer with using scope") {
        var cleaned = false
        Scope.global.scoped { scope =>
          given Scope[?, ?] = scope
          defer { cleaned = true }
        }
        assertTrue(cleaned)
      }
    ),
    suite("Wire")(
      test("shared[T] returns Wire.Shared") {
        val wire = shared[Config]
        assertTrue(wire.isShared && !wire.isUnique)
      },
      test("unique[T] returns Wire.Unique") {
        val wire = unique[Config]
        assertTrue(wire.isUnique && !wire.isShared)
      },
      test("wire.toResource creates Resource") {
        // Config has a Boolean constructor param, so the wire needs Boolean dependency
        val wire = shared[Config]
        val deps = zio.blocks.context.Context[Boolean](true)

        val resource       = wire.toResource(deps)
        val (scope, close) = Scope.createTestableScope()
        val result         = resource.make(scope)
        close()
        assertTrue(result.debug)
      }
    )
  )
}
