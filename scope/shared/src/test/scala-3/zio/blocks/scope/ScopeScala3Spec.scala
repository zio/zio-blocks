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

  class ResourceWithScope()(using scope: Scope[?, ?]) {
    var cleanedUp = false
    scope.defer { cleanedUp = true }
  }

  def spec = suite("Scope (Scala 3)")(
    suite("global scope")(
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      },
      test("global scope escapes all results as raw values") {
        Scope.global.scoped { scope =>
          val str         = scope.allocate(Resource("test"))
          val raw: String = scope.$(str)(identity)
          assertTrue(raw == "test")
        }
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
    suite("scope.allocate and Resource")(
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
      },
      test("unscoped types escape as raw values") {
        Scope.global.scoped { parent =>
          parent.scoped { child =>
            val str            = child.allocate(Resource("hello"))
            val result: String = child.$(str)(_.toUpperCase)
            assertTrue(result == "HELLO")
          }
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
      },
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
    ),
    suite("Scoped monad")(
      test("map creates Scoped computation") {
        Scope.global.scoped { scope =>
          val db = scope.allocate(Resource.from[Database])

          val computation = db.map(_.query("mapped"))

          // Execute via scope.apply
          val result = scope(computation)
          assertTrue(result == "result: mapped")
        }
      },
      test("map and Scoped.map composition") {
        Scope.global.scoped { scope =>
          val db = scope.allocate(Resource.from[Database])

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
    ),
    suite("Wireable.from with overrides")(
      test("Wireable.from[T](wires) reduces In type by covered dependencies") {
        // Service depends on Config and Database, provide Config wire
        class Service(config: Config, db: Database)

        val configWire = Wire(Config(true))
        val wireable   = Wireable.from[Service](configWire)

        // The remaining In type should be Database
        val deps           = zio.blocks.context.Context[Database](new Database)
        val (scope, close) = Scope.createTestableScope()
        val resource       = wireable.wire.toResource(deps)
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[Service])
      },
      test("Wireable.from[T](wires) with all dependencies covered has In = Any") {
        class SimpleService(config: Config)

        val configWire = Wire(Config(true))
        val wireable   = Wireable.from[SimpleService](configWire)

        // All deps covered, so we can use empty Context (Any)
        val deps           = zio.blocks.context.Context.empty
        val (scope, close) = Scope.createTestableScope()
        val resource       = wireable.wire.toResource(deps)
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[SimpleService])
      }
    ),
    suite("Resource.from with overrides")(
      test("Resource.from[T](wires) creates standalone Resource when all deps covered") {
        class SimpleService(config: Config)

        val configWire     = Wire(Config(true))
        val resource       = Resource.from[SimpleService](configWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[SimpleService])
      },
      test("Resource.from[T](wires) with AutoCloseable registers finalizer") {
        class CloseableService(config: Config) extends AutoCloseable {
          var closed        = false
          def close(): Unit = closed = true
        }

        val configWire     = Wire(Config(true))
        val resource       = Resource.from[CloseableService](configWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        assertTrue(!service.closed)
        close()
        assertTrue(service.closed)
      },
      test("Resource.from[T](wires) with multiple dependencies") {
        case class Port(value: Int)
        class MultiDepService(config: Config, db: Database, port: Port)

        val configWire     = Wire(Config(false))
        val dbWire         = shared[Database]
        val portWire       = Wire(Port(8080))
        val resource       = Resource.from[MultiDepService](configWire, dbWire, portWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[MultiDepService])
      }
    )
  )
}
