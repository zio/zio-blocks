package zio.blocks.scope

import zio.test._

/**
 * Tests for the new Scope design with scope-local opaque types.
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
          import scope._
          blockRan = true
          defer { cleaned = true }
        }
        assertTrue(blockRan, cleaned)
      },
      test("scoped closes scope even on exception") {
        var cleaned = false
        try {
          Scope.global.scoped[Nothing] { scope =>
            import scope._
            defer { cleaned = true }
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
      test("allocate returns scoped value and $ works") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          val _: $[String]    = $(db) { d =>
            val result = d.query("SELECT 1")
            captured = result
            result
          }
        }
        assertTrue(captured == "result: SELECT 1")
      }
    ),
    suite("$ operator")(
      test("$ extracts value and applies function") {
        var captured: Boolean | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val config: $[Config] = allocate(Resource(Config(true)))
          val _: $[Boolean]     = $(config) { c =>
            val debug = c.debug
            captured = debug
            debug
          }
        }
        assertTrue(captured == true)
      },
      test("$ always returns scoped value") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          val _: $[String]    = $(db) { d =>
            val result = d.query("test")
            captured = result
            result
          }
        }
        assertTrue(captured == "result: test")
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via lower") {
        val (parent, closeParent) = Scope.createTestableScope()
        import parent._
        var captured: String | Null = null
        val db: $[Database]         = allocate(Resource.from[Database])

        parent.scoped { child =>
          import child._
          val childDb      = lower(db)
          val _: $[String] = $(childDb) { d =>
            val result = d.query("child")
            captured = result
            result
          }
        }
        closeParent()
        assertTrue(captured == "result: child")
      },
      test("child scope closes before parent") {
        val (parent, closeParent) = Scope.createTestableScope()
        import parent._
        val order = scala.collection.mutable.ArrayBuffer.empty[String]
        defer(order += "parent")

        parent.scoped { child =>
          import child._
          defer(order += "child")
        }
        closeParent()
        assertTrue(order.toList == List("child", "parent"))
      }
    ),
    suite("map/flatMap (eager operations)")(
      test("map works with scoped values") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]   = allocate(Resource.from[Database])
          val result: $[String] = db.map { d =>
            val r = d.query("mapped")
            captured = r
            r
          }
          captured.nn
        }
        assertTrue(captured == "result: mapped")
      },
      test("map and map composition") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]   = allocate(Resource.from[Database])
          val result: $[String] = db.map(_.query("a")).map { s =>
            val r = s.toUpperCase
            captured = r
            r
          }
          captured.nn
        }
        assertTrue(captured == "RESULT: A")
      },
      test("flatMap chains scoped values") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]   = allocate(Resource.from[Database])
          val result: $[String] = for {
            d <- db
            r <- wrap(d.query("chained"))
          } yield {
            val upper = r.toUpperCase
            captured = upper
            upper
          }
          captured.nn
        }
        assertTrue(captured == "RESULT: CHAINED")
      },
      test("for-comprehension with multiple allocates") {
        class Pool {
          var closed              = false
          def lease(): Connection = new Connection(this)
          def close(): Unit       = closed = true
        }
        class Connection(val pool: Pool) extends AutoCloseable {
          var closed          = false
          def query(): String = "connected"
          def close(): Unit   = closed = true
        }

        var poolClosed              = false
        var connClosed              = false
        var captured: String | Null = null

        Scope.global.scoped { scope =>
          import scope._
          val program: $[String] = for {
            pool <- allocate(Resource.acquireRelease(new Pool) { p =>
                      poolClosed = true
                      p.close()
                    })
            conn <- allocate(Resource.acquireRelease(pool.lease()) { c =>
                      connClosed = true
                      c.close()
                    })
          } yield {
            val result = conn.query()
            captured = result
            result
          }
          captured.nn
        }

        assertTrue(
          captured == "connected",
          connClosed,
          poolClosed
        )
      }
    ),
    suite("defer")(
      test("finalizers run in LIFO order") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        Scope.global.scoped { scope =>
          import scope._
          defer(order += 1)
          defer(order += 2)
          defer(order += 3)
        }
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("package-level defer works with explicit Finalizer") {
        var cleaned = false
        Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }
        }
        assertTrue(cleaned)
      },
      test("all finalizers run even if one throws") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer(order += 1)
            defer(throw new RuntimeException("finalizer boom"))
            defer(order += 3)
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(order.toList == List(3, 1))
      },
      test("block throws and finalizers throw: primary thrown, finalizer errors suppressed") {
        var caught: Throwable | Null = null
        try {
          Scope.global.scoped[Nothing] { scope =>
            import scope._
            defer(throw new RuntimeException("finalizer 1"))
            defer(throw new RuntimeException("finalizer 2"))
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
            import scope._
            defer(throw new RuntimeException("finalizer 1"))
            defer(throw new RuntimeException("finalizer 2"))
            defer(throw new RuntimeException("finalizer 3"))
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
          import scope._
          defer { finalized = true }
        }
        assertTrue(finalized)
      }
    ),
    suite("edge cases")(
      test("runtime cast is unsafe with sibling scopes (demonstration)") {
        val (parent, closeParent)   = Scope.createTestableScope()
        var captured: String | Null = null
        var leaked: Any             = null

        parent.scoped { child1 =>
          import child1._
          leaked = allocate(Resource.from[Database])
        }
        parent.scoped { child2 =>
          import child2._
          val db           = leaked.asInstanceOf[$[Database]]
          val _: $[String] = $(db) { d =>
            val result = d.query("test")
            captured = result
            result
          }
        }
        closeParent()
        assertTrue(captured == "result: test")
      }
    )
  )
}
