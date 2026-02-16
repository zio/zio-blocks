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
    def status(): String           = if (closed) "closed" else "open"
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
      test("scoped returns value directly") {
        val result: Int = Scope.global.scoped { _ =>
          100
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
        val result: Int = Scope.global.scoped { _ =>
          val x: Int = Scope.global.scoped { _ =>
            10
          }
          x + 5
        }
        assertTrue(result == 15)
      },
      test("scoped close propagates error from finalizer") {
        val result = try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("test error")): Unit
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
          defer(counter += 10): Unit
        }
        assertTrue(counter == 11)
      }
    ),
    suite("Resource.from macro")(
      test("Resource.from[T] derives from no-arg constructor") {
        val isDb: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.isInstanceOf[Database]).get
        }
        assertTrue(isDb)
      },
      test("Resource.from[T] handles AutoCloseable") {
        var closed               = false
        val beforeClose: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          defer { closed = true }
          scope.$(db)(d => !d.closed).get
        }
        assertTrue(beforeClose, closed)
      }
    ),
    suite("allocate")(
      test("allocate returns scoped value and $ works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("SELECT 1")).get
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
          val captured: String           = scope.$(resource)(_.value).get
          captured == "test" && !closed
        }
        assertTrue(beforeClose, closed)
      }
    ),
    suite("$ operator")(
      test("$ extracts value and applies function") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val config: $[Config] = allocate(Resource(Config(true)))
          scope.$(config)(_.debug).get
        }
        assertTrue(captured == true)
      },
      test("$ always returns scoped value") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("test")).get
        }
        assertTrue(captured == "result: test")
      },
      test("$ with chained method calls") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("test").toUpperCase).get
        }
        assertTrue(captured == "RESULT: TEST")
      },
      test("$ with multiple references to param") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(d => d.query("a") + " " + d.query("b")).get
        }
        assertTrue(captured == "result: a result: b")
      }
    ),
    suite("eager operations")(
      test("$ executes eagerly when scope is open") {
        class TrackedResource extends AutoCloseable {
          var done              = false
          def doWork(): Boolean = { done = true; done }
          def close(): Unit     = ()
        }

        val executed: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
          scope.$(resource)(_.doWork()).get
        }
        assertTrue(executed)
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via lower") {
        val captured: String = Scope.global.scoped { outer =>
          val db: outer.$[Database] = outer.allocate(Resource.from[Database])

          val result: String = outer.scoped { inner =>
            import inner._
            val innerDb: inner.$[Database] = inner.lower(db)
            inner.$(innerDb)(_.query("child")).get
          }
          result
        }
        assertTrue(captured == "result: child")
      },
      test("child scope closes before parent") {
        val order = scala.collection.mutable.ArrayBuffer.empty[String]
        Scope.global.scoped { outer =>
          outer.defer(order += "parent")

          outer.scoped { inner =>
            inner.defer(order += "child"): Unit
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
          defer(order += 3): Unit
        }
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("package-level defer works") {
        var cleaned = false
        Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }: Unit
        }
        assertTrue(cleaned)
      },
      test("defer works with Finalizer capability") {
        var finalized = false
        Scope.global.scoped { scope =>
          import scope._
          defer { finalized = true }: Unit
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
            defer(order += 3): Unit
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
            defer(throw new RuntimeException("finalizer 2")): Unit
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
            defer(throw new RuntimeException("finalizer 3")): Unit
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
    suite("use macro rejects unsafe patterns")(
      test("passing param as argument is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => println(a))
          }
        """))(isLeft)
      },
      test("capturing in nested lambda is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => () => a.query("test"))
          }
        """))(isLeft)
      },
      test("storing param in var is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            var stash: Any = null
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            var someVar: Any = null
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => { someVar = a; 42 })
          }
        """))(isLeft)
      },
      test("returning param directly (identity) is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => a)
          }
        """))(isLeft)
      },
      test("tuple construction with param is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => (a, 1))
          }
        """))(isLeft)
      },
      test("constructor argument with param is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          case class Wrapper(value: Any)

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => new Wrapper(a))
          }
        """))(isLeft)
      },
      test("match expression where param could escape via pattern binding is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => d match { case x => x })
          }
        """))(isLeft)
      },
      test("if-expression where param is branch result is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => if (true) d else null)
          }
        """))(isLeft)
      }
    ),
    suite("use macro allows safe patterns")(
      test("simple method call is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("SELECT 1")).get
        }
        assertTrue(captured == "result: SELECT 1")
      },
      test("chained method calls are allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("test").toUpperCase).get
        }
        assertTrue(captured == "RESULT: TEST")
      },
      test("multiple receiver uses are allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(a => a.query("a") + " " + a.query("b")).get
        }
        assertTrue(captured == "result: a result: b")
      },
      test("method with non-param args is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.query("raw arg")).get
        }
        assertTrue(captured == "result: raw arg")
      },
      test("field access is allowed") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.closed).get
        }
        assertTrue(captured == false)
      },
      test("arity-0 method call is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          scope.$(db)(_.status()).get
        }
        assertTrue(captured == "open")
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
          Config(scope.$(data)(_.booleanValue()).get)
        }
        assertTrue(result.debug)
      }
    ),
    suite("type inference")(
      test("use operator works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val resource: $[CloseableResource] = allocate(Resource(new CloseableResource("hello")))
          scope.$(resource)(_.name).get
        }
        assertTrue(captured == "hello")
      },
      test("returning raw Unscoped values works") {
        val captured: Option[String] = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource(new Database))
          scope.$(db)(d => Option(d.query("SELECT 1"))).get
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
    suite(".get")(
      test("get works for Unscoped types") {
        val (nVal, sVal) = Scope.global.scoped { scope =>
          import scope._
          val n: $[Int]    = allocate(Resource(42))
          val s: $[String] = allocate(Resource("hello"))
          (n.get, s.get)
        }
        assertTrue(nVal == 42, sVal == "hello")
      },
      test("ScopedResourceOps.allocate works for $[Resource[A]]") {
        class Outer extends AutoCloseable {
          def makeInner: Resource[Inner] = Resource.fromAutoCloseable(new Inner)
          def close(): Unit              = ()
        }

        class Inner extends AutoCloseable {
          def value: String = "inner"
          def close(): Unit = ()
        }

        val result: String = Scope.global.scoped { scope =>
          import scope._
          val outer: $[Outer] = allocate(Resource.fromAutoCloseable(new Outer))
          val inner: $[Inner] = (scope $ outer)(_.makeInner).allocate
          scope.$(inner)(_.value).get
        }
        assertTrue(result == "inner")
      },
      test("Resource[A] cannot be .get-ed (not Unscoped)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._
          Scope.global.scoped { scope =>
            import scope._
            val r: $[Resource[Int]] = allocate(Resource(Resource(42)))
            r.get
            ()
          }
        """))(isLeft)
      },
      test("get does not compile for non-Unscoped types") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class MyResource extends AutoCloseable {
            def close(): Unit = ()
          }

          Scope.global.scoped { scope =>
            import scope._
            val r: $[MyResource] = allocate(Resource(new MyResource))
            r.get
            ()
          }
        """))(isLeft)
      }
    ),
    suite("closed scope defense")(
      test("isClosed is false while scope is open") {
        val result: Boolean = Scope.global.scoped { scope =>
          !scope.isClosed
        }
        assertTrue(result)
      },
      test("isClosed is true after scope closes") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        assertTrue(child.isClosed)
      },
      test("$ on closed scope does not execute function") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        val wrapped = child.allocate(Resource(new CloseableResource("test")))
        child.close()
        var fnRan  = false
        val result = child.$(wrapped) { v =>
          fnRan = true
          v.name
        }
        assertTrue(!fnRan, (result: Any) == null)
      },
      test("allocate on closed scope returns null instead of acquiring resource") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        val result = child.allocate(Resource(new Database))
        assertTrue(child.isClosed, (result: Any) == null)
      },
      test("scoped on closed scope creates born-closed child") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var innerClosed = false
        child.scoped { inner =>
          innerClosed = inner.isClosed
        }
        assertTrue(child.isClosed, innerClosed)
      },
      test("defer on closed scope is silently ignored") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var finalizerRan = false
        child.defer { finalizerRan = true }
        assertTrue(child.isClosed, !finalizerRan)
      },
      test("global scope isClosed is always false") {
        assertTrue(!Scope.global.isClosed)
      }
    )
  )
}
