package zio.blocks.scope

import zio.test._
import zio.test.Assertion.{containsString, isLeft}

/**
 * Cross-platform tests for Scope.
 */
object ScopeSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  object Config {
    implicit val unscoped: Unscoped[Config] = Unscoped.derived[Config]
  }

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  def spec = suite("Scope")(
    suite("global")(
      test("global scope exists") {
        assertTrue(Scope.global != null)
      },
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      }
    ),
    suite("scope.scoped")(
      test("scoped executes block and closes scope") {
        var cleaned           = false
        val blockRan: Boolean = Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }
          true
        }
        assertTrue(blockRan, cleaned)
      },
      test("scoped returns plain Unscoped type") {
        val result: String = Scope.global.scoped { _ =>
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("scoped unwraps child.$[A] to A") {
        val result: Int = Scope.global.scoped { child =>
          child.$(100)
        }
        assertTrue(result == 100)
      },
      test("scoped closes scope even on exception") {
        var cleaned = false
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer { cleaned = true }
            if (true) throw new RuntimeException("boom")
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(cleaned)
      },
      test("nested scoped blocks work") {
        val result: Int = Scope.global.scoped { outer =>
          val x: Int = Scope.global.scoped { inner =>
            inner.$(10)
          }
          outer.$(x + 5)
        }
        assertTrue(result == 15)
      },
      test("scoped close propagates error from finalizer") {
        val result = try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("test error"))
          }
          false
        } catch {
          case e: RuntimeException => e.getMessage == "test error"
        }
        assertTrue(result)
      },
      test("scoped runs multiple finalizers") {
        var counter = 0
        Scope.global.scoped { scope =>
          import scope._
          defer(counter += 1)
          defer(counter += 10)
        }
        assertTrue(counter == 11)
      }
    ),
    suite("Resource.from macro")(
      test("Resource.from[T] derives from no-arg constructor") {
        val isDb: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          db.map(_.isInstanceOf[Database])
        }
        assertTrue(isDb)
      },
      test("Resource.from[T] handles AutoCloseable") {
        var closed               = false
        val beforeClose: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          defer { closed = true }
          db.map(d => !d.closed)
        }
        assertTrue(beforeClose, closed)
      }
    ),
    suite("allocate")(
      test("allocate returns scoped value and use works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.use(db)(_.query("SELECT 1"))
        }
        assertTrue(captured == "result: SELECT 1")
      },
      test("allocate AutoCloseable registers close() as finalizer") {
        var closed = false

        class TestCloseable extends AutoCloseable {
          def value: String          = "test"
          override def close(): Unit = closed = true
        }

        val beforeClose: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TestCloseable] = allocate(new TestCloseable)
          val captured: $[String]        = scope.use(resource)(_.value)
          scope.use(captured)(_ => !closed)
        }
        assertTrue(beforeClose, closed)
      }
    ),
    suite("use operator")(
      test("use extracts value and applies function") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val config: $[Config] = allocate(Resource(Config(true)))
          scope.use(config)(_.debug)
        }
        assertTrue(captured == true)
      },
      test("use always returns scoped value") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.use(db)(_.query("test"))
        }
        assertTrue(captured == "result: test")
      },
      test("use with two scoped values") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]   = allocate(Resource.from[Database])
          val config: $[Config] = allocate(Resource(Config(true)))
          scope.use(db, config) { (d, c) =>
            s"${d.query("test")}-${c.debug}"
          }
        }
        assertTrue(captured == "result: test-true")
      },
      test("use with three scoped values") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val a: $[Int]    = allocate(Resource(1))
          val b: $[Int]    = allocate(Resource(2))
          val c: $[String] = allocate(Resource("x"))
          scope.use(a, b, c) { (x, y, z) =>
            s"$z${x + y}"
          }
        }
        assertTrue(captured == "x3")
      },
      test("use with four scoped values") {
        val captured: Int = Scope.global.scoped { scope =>
          import scope._
          val a: $[Int] = allocate(Resource(1))
          val b: $[Int] = allocate(Resource(2))
          val c: $[Int] = allocate(Resource(3))
          val d: $[Int] = allocate(Resource(4))
          scope.use(a, b, c, d)(_ + _ + _ + _)
        }
        assertTrue(captured == 10)
      },
      test("use with five scoped values") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val a: $[String] = allocate(Resource("h"))
          val b: $[String] = allocate(Resource("e"))
          val c: $[String] = allocate(Resource("l"))
          val d: $[String] = allocate(Resource("l"))
          val e: $[String] = allocate(Resource("o"))
          scope.use(a, b, c, d, e)(_ + _ + _ + _ + _)
        }
        assertTrue(captured == "hello")
      }
    ),
    suite("eager operations")(
      test("use executes eagerly when scope is open") {
        class TrackedResource extends AutoCloseable {
          var done              = false
          def doWork(): Boolean = { done = true; done }
          def close(): Unit     = ()
        }

        val executed: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
          scope.use(resource)(_.doWork())
        }
        assertTrue(executed)
      },
      test("map executes eagerly when scope is open") {
        class TrackedResource extends AutoCloseable {
          def doWork(): Boolean = true
          def close(): Unit     = ()
        }

        val executed: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
          resource.map(_.doWork())
        }
        assertTrue(executed)
      },
      test("nested scoped values evaluate eagerly") {
        val result: Int = Scope.global.scoped { scope =>
          import scope._
          val base: $[Int]      = allocate(Resource(1))
          val inner: $[Int]     = base.map(_ + 1)
          val nested: $[$[Int]] = scope.use(base)(_ => inner)
          nested.flatMap(identity)
        }
        assertTrue(result == 2)
      }
    ),
    suite("map/flatMap")(
      test("map works with scoped values") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          db.map(_.query("mapped"))
        }
        assertTrue(captured == "result: mapped")
      },
      test("map and use composition") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]        = allocate(Resource.from[Database])
          val computation: $[String] = db.map(_.query("a"))
          scope.use(computation)(_.toUpperCase)
        }
        assertTrue(captured == "RESULT: A")
      },
      test("map and map composition") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          db.map(_.query("a")).map(_.toUpperCase)
        }
        assertTrue(captured == "RESULT: A")
      },
      test("flatMap chains scoped values") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database]   = allocate(Resource.from[Database])
          val result: $[String] = for {
            d <- db
            r <- $(d.query("chained"))
          } yield r.toUpperCase
          result
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

        var poolClosed = false
        var connClosed = false

        val captured: String = Scope.global.scoped { scope =>
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
          } yield conn.query()
          program
        }

        assertTrue(
          captured == "connected",
          connClosed,
          poolClosed
        )
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via lower") {
        val captured: String = Scope.global.scoped { outer =>
          val db: outer.$[Database] = outer.allocate(Resource.from[Database])

          val result: String = outer.scoped { inner =>
            val innerDb: inner.$[Database] = inner.lower(db)
            inner.use(innerDb)(_.query("child"))
          }
          outer.$(result)
        }
        assertTrue(captured == "result: child")
      },
      test("child scope closes before parent") {
        val order = scala.collection.mutable.ArrayBuffer.empty[String]
        Scope.global.scoped { outer =>
          outer.defer(order += "parent")

          val _: Unit = outer.scoped { inner =>
            inner.defer(order += "child")
          }
        }
        assertTrue(order.toList == List("child", "parent"))
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
      test("package-level defer works") {
        var cleaned = false
        Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }
        }
        assertTrue(cleaned)
      },
      test("defer works with Finalizer capability") {
        var finalized = false
        Scope.global.scoped { scope =>
          import scope._
          defer { finalized = true }
        }
        assertTrue(finalized)
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
        var caught: Throwable = null
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("finalizer 1"))
            defer(throw new RuntimeException("finalizer 2"))
            if (true) throw new RuntimeException("block boom")
          }
        } catch {
          case t: RuntimeException => caught = t
        }
        val suppressed = caught.getSuppressed
        assertTrue(
          caught != null,
          caught.getMessage == "block boom",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      },
      test("block succeeds and finalizers throw multiple: first thrown, rest suppressed") {
        var caught: Throwable = null
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
        val suppressed = caught.getSuppressed
        assertTrue(
          caught != null,
          caught.getMessage == "finalizer 3",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      }
    ),
    suite("compile-time safety")(
      test("cannot directly call methods on scoped value") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource.from[Database])
            db.query("test")
          }
        """))(isLeft(containsString("Recursive value") || containsString("is not a member")))
      },
      test("closure cannot be returned from scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            () => "captured"
          }
        """))(isLeft)
      },
      test("resourceful value cannot escape scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          val db: Database = Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource.from[Database])
            db
          }
        """))(isLeft)
      },
      test("use returns $[B], not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val result: String = scope.use(db)(identity)
            result
          }
        """))(isLeft)
      },
      test("map returns $[B], not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val result: String = db.map(_.toUpperCase)
            result
          }
        """))(isLeft)
      }
    ),
    suite("Unscoped constraint")(
      test("String can be returned from scoped block") {
        val result: String = Scope.global.scoped { _ =>
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("Int can be returned from scoped block") {
        val result: Int = Scope.global.scoped { _ =>
          42
        }
        assertTrue(result == 42)
      },
      test("Unit can be returned from scoped block") {
        var sideEffect = false
        Scope.global.scoped { _ =>
          sideEffect = true
        }
        assertTrue(sideEffect)
      },
      test("custom Unscoped type can be returned") {
        val result: Config = Scope.global.scoped { scope =>
          import scope._
          val data: $[Boolean] = allocate(Resource(true))
          data.map(Config(_))
        }
        assertTrue(result.debug)
      }
    ),
    suite("type inference")(
      test("map works with allocate") {
        val captured: Int = Scope.global.scoped { scope =>
          import scope._
          val resource: $[CloseableResource] = allocate(Resource(new CloseableResource("hello")))
          resource.map(_.name.length)
        }
        assertTrue(captured == 5)
      },
      test("use operator works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val resource: $[CloseableResource] = allocate(Resource(new CloseableResource("hello")))
          scope.use(resource)(_.name)
        }
        assertTrue(captured == "hello")
      },
      test("for-comprehension works") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val a: $[CloseableResource] = allocate(Resource(new CloseableResource("hello")))
          val b: $[CloseableResource] = allocate(Resource(new CloseableResource("world")))
          val result: $[Boolean]      = for {
            x <- a
            y <- b
          } yield x.name.length == y.name.length
          result
        }
        assertTrue(captured == true)
      },
      test("returning raw Unscoped values works") {
        val captured: Option[String] = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource(new Database))
          scope.use(db)(d => Option(d.query("SELECT 1")))
        }
        assertTrue(captured.contains("result: SELECT 1"))
      },
      test("scoped block returns Unscoped Int") {
        val result: Int = Scope.global.scoped { scope =>
          import scope._
          defer(())
          100
        }
        assertTrue(result == 100)
      }
    ),
    suite("closed scope defense")(
      test("isClosed is false while scope is open") {
        val result: Boolean = Scope.global.scoped { scope =>
          scope.$(scope.isClosed)
        }
        assertTrue(!result)
      },
      test("isClosed is true after scope closes") {
        val child = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        child.close()
        assertTrue(child.isClosed)
      },
      test("use on closed scope returns null instead of executing function") {
        val child   = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        val wrapped = child.$(42)
        child.close()
        var fnRan = false
        val result = child.use(wrapped) { v =>
          fnRan = true
          v + 1
        }
        assertTrue(!fnRan, (result: Any) == null)
      },
      test("allocate on closed scope returns null instead of acquiring resource") {
        val child = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        child.close()
        val result = child.allocate(Resource(new Database))
        assertTrue(child.isClosed, (result: Any) == null)
      },
      test("map on closed scope returns null instead of executing function") {
        val child   = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        val wrapped = child.$("hello")
        child.close()
        import child._
        var fnRan = false
        val result = wrapped.map { v =>
          fnRan = true
          v.toUpperCase
        }
        assertTrue(!fnRan, (result: Any) == null)
      },
      test("flatMap on closed scope returns null instead of executing function") {
        val child   = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        val wrapped = child.$(99)
        child.close()
        import child._
        var fnRan = false
        val result = wrapped.flatMap { v =>
          fnRan = true
          child.$(v + 1)
        }
        assertTrue(!fnRan, (result: Any) == null)
      },
      test("scoped on closed scope creates born-closed child") {
        val child = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        child.close()
        var innerClosed = false
        child.scoped { inner =>
          innerClosed = inner.isClosed
        }
        assertTrue(child.isClosed, innerClosed)
      },
      test("defer on closed scope is silently ignored") {
        val child = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        child.close()
        var finalizerRan = false
        child.defer { finalizerRan = true }
        assertTrue(child.isClosed, !finalizerRan)
      },
      test("$ on closed scope returns null") {
        val child = new Scope.Child[Scope.global.type](Scope.global, new zio.blocks.scope.internal.Finalizers)
        child.close()
        val result = child.$(42)
        assertTrue((result: Any) == null)
      },
      test("global scope isClosed is always false") {
        assertTrue(!Scope.global.isClosed)
      }
    )
  )
}
