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
          import scope._
          blockRan = true
          defer { cleaned = true }
        }
        assertTrue(blockRan, cleaned)
      },
      test("scoped closes scope even on exception") {
        var cleaned = false
        try {
          Scope.global.scoped { scope =>
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
      test("allocate returns tagged value and $ works") {
        // Capture result via side effect in the function passed to $
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: Database @@ scope.ScopeTag = allocate(Resource.from[Database])
          // $ executes immediately, so side effect happens now
          $(db) { d =>
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
          val config: Config @@ scope.ScopeTag = allocate(Resource(Config(true)))
          $(config) { c =>
            val debug = c.debug
            captured = debug
            debug
          }
        }
        assertTrue(captured == true)
      },
      test("$ always returns tagged value") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: Database @@ scope.ScopeTag = allocate(Resource.from[Database])
          $(db) { d =>
            val result = d.query("test")
            captured = result
            result
          }
        }
        assertTrue(captured == "result: test")
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via Tag subtyping") {
        var captured: String | Null = null
        Scope.global.scoped { parentScope =>
          import parentScope._
          val db: Database @@ parentScope.ScopeTag = allocate(Resource.from[Database])

          parentScope.scoped { childScope =>
            import childScope._
            $(db) { d =>
              val result = d.query("child")
              captured = result
              result
            }
            () // Return Unit (Unscoped)
          }
        }
        assertTrue(captured == "result: child")
      },
      test("child scope closes before parent") {
        val order = scala.collection.mutable.ArrayBuffer.empty[String]

        Scope.global.scoped { parent =>
          import parent._
          defer(order += "parent")

          parent.scoped { child =>
            import child._
            defer(order += "child")
          }
        }

        assertTrue(order.toList == List("child", "parent"))
      }
    ),
    suite("Scoped monad")(
      test("map creates Scoped computation") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: Database @@ scope.ScopeTag = allocate(Resource.from[Database])
          val computation                    = db.map { d =>
            val result = d.query("mapped")
            captured = result
            result
          }
          execute(computation) // execute runs the computation
        }
        assertTrue(captured == "result: mapped")
      },
      test("map and Scoped.map composition") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: Database @@ scope.ScopeTag = allocate(Resource.from[Database])
          val computation                    = db.map(_.query("a")).map { s =>
            val result = s.toUpperCase
            captured = result
            result
          }
          execute(computation)
        }
        assertTrue(captured == "RESULT: A")
      },
      test("flatMap chains scoped computations") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: Database @@ scope.ScopeTag = allocate(Resource.from[Database])
          val computation                    = for {
            d <- db
            r <- Scoped(d.query("chained"))
          } yield {
            val result = r.toUpperCase
            captured = result
            result
          }
          execute(computation)
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
          val program = for {
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
          execute(program)
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
          Scope.global.scoped { scope =>
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
        // This demonstrates that runtime casts can bypass type safety
        // The type system prevents this, but asInstanceOf can circumvent it
        var captured: String | Null = null
        Scope.global.scoped { parent =>
          var leaked: Any = null
          parent.scoped { child1 =>
            import child1._
            leaked = allocate(Resource.from[Database])
            () // Return Unit
          }
          parent.scoped { child2 =>
            import child2._
            val db = leaked.asInstanceOf[Database @@ child2.ScopeTag]
            $(db) { d =>
              val result = d.query("test")
              captured = result
              result
            }
            () // Return Unit
          }
        }
        assertTrue(captured == "result: test")
      }
    ),

    suite("scope + scoped value leak prevention")(
      test("scoped on closed scope creates already-closed child preventing use-after-close") {
        var accessedAfterClose = false
        var resourceClosed     = false

        class TrackedResource extends AutoCloseable {
          def read(): Int = {
            accessedAfterClose = resourceClosed
            42
          }
          def close(): Unit = resourceClosed = true
        }

        // The attack: return both the child scope AND a scoped value from global.scoped
        // globalScope ScopeLift instance allows ANY type to escape from GlobalTag parent
        // Use a case class to bundle them with matching existential types
        case class Leaked[T <: Scope.GlobalTag](scope: Scope[Scope.GlobalTag, T], value: TrackedResource @@ T)

        val leaked = Scope.global.scoped { child =>
          import child._
          val resource = allocate(Resource(new TrackedResource))
          Leaked(child, resource)
        }
        // Child scope is now CLOSED, but we have both the scope and the scoped value

        assertTrue(resourceClosed) // Finalizer ran, resource is closed

        // Attempted attack: call scoped() on the closed scope
        // Before fix: scoped() would create an open child, allowing eager execution
        // After fix: scoped() creates an already-closed child, so $ stays lazy
        var result: Int = 0
        leaked.scope.scoped { newChild =>
          import newChild._
          // newChild is created as already-closed (because parent is closed)
          // $ checks isClosed → true → stays lazy, doesn't execute
          $(leaked.value) { r =>
            result = r.read() // This never executes!
          }
          ()
        }

        // Fix verified: resource was NOT accessed after close
        assertTrue(!accessedAfterClose, result == 0)
      }
    )
  )
}
