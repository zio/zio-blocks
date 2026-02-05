package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object PackageFunctionsSpec extends ZIOSpecDefault {

  class Config {
    val debug: Boolean = false
  }

  class Database {
    val url: String = "jdbc://localhost"
  }

  class Cache {
    val size: Int = 100
  }

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  class CloseableConfig extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  class ServiceWithScope(val name: String)(implicit scope: Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  class SimpleService(val config: Config)

  class ServiceWith2Deps(val config: Config, val db: Database)

  class AutoCloseableService(val config: Config) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  trait DatabaseTrait {
    def query(): String
  }

  class DatabaseImpl(val config: Config) extends DatabaseTrait with AutoCloseable {
    var closed: Boolean = false
    def query(): String = s"querying with ${config.debug}"
    def close(): Unit   = closed = true
  }

  implicit val databaseTraitWireable: Wireable.Typed[Config, DatabaseTrait] = new Wireable[DatabaseTrait] {
    type In = Config
    def wire: Wire[Config, DatabaseTrait] = Wire.Shared.fromFunction[Config, DatabaseTrait] { scope =>
      val config = scope.get[Config]
      val impl   = new DatabaseImpl(config)
      scope.defer(impl.close())
      Context[DatabaseTrait](impl)
    }
  }

  def spec = suite("package functions (Scala 2)")(
    test("defer registers cleanup on scope") {
      var cleaned   = false
      val config    = new Config
      val closeable = Scope.makeCloseable[Config, TNil](Scope.global, Context(config), new Finalizers)
      defer { cleaned = true }(closeable)
      closeable.close()
      assertTrue(cleaned)
    },
    test("$ retrieves from scope") {
      val config    = new Config
      val closeable = Scope.makeCloseable[Config, TNil](Scope.global, Context(config), new Finalizers)
      val retrieved = $[Config](closeable, implicitly)
      closeable.close()
      assertTrue(retrieved eq config)
    },
    test("scope.injected creates closeable scope") {
      val closeable = Scope.global.injected[Config]()
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved.debug == false)
    },
    test("scope.injected with wires builds dependencies") {
      val closeable = Scope.global.injected[SimpleService](shared[Config])
      val svc       = closeable.get[SimpleService]
      closeable.close()
      assertTrue(svc != null && svc.config != null)
    },
    test("scope.injected handles AutoCloseable") {
      val closeable = Scope.global.injected[CloseableConfig]()
      val instance  = closeable.get[CloseableConfig]
      assertTrue(!instance.closed)
      closeable.close()
      assertTrue(instance.closed)
    },
    suite("shared[T]")(
      test("derives wire for no-arg class") {
        val wire              = shared[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        // With whitebox macro, In type is inferred correctly - no cast needed
        val ctx = wire.construct(scope)
        assertTrue(ctx.get[Config].debug == false)
      },
      test("derives wire for class with one dependency") {
        val wire              = shared[SimpleService]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val configCtx         = Context(new Config)
        val scope             = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        val ctx               = wire.construct(scope)
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("derives wire for class with two dependencies") {
        val wire              = shared[ServiceWith2Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database)
        val scope             = Scope.makeCloseable[Config with Database, TNil](parent, depsCtx, finalizers)
        val ctx               = wire.construct(scope)
        assertTrue(ctx.get[ServiceWith2Deps] != null)
      },
      test("handles AutoCloseable") {
        val wire                       = shared[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        val ctx                        = wire.construct(scope)
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("uses Wireable when available") {
        // The In type from the implicit Wireable is now preserved by the whitebox macro
        val wire              = shared[DatabaseTrait]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val configCtx         = Context(new Config)
        val scope             = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        val ctx               = wire.construct(scope)
        val db                = ctx.get[DatabaseTrait]
        assertTrue(db.query() == "querying with false")
      }
    ),
    suite("unique[T]")(
      test("derives wire for no-arg class") {
        val wire              = unique[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        val ctx               = wire.construct(scope)
        assertTrue(ctx.get[Config].debug == false)
      },
      test("derives wire for class with dependency") {
        val wire              = unique[SimpleService]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val configCtx         = Context(new Config)
        val scope             = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        val ctx               = wire.construct(scope)
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("handles AutoCloseable") {
        val wire                       = unique[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        val ctx                        = wire.construct(scope)
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      }
    ),
    suite("top-level injected[T]")(
      test("creates scope for no-arg class") {
        val closeable = injected[Config]()(Scope.global)
        val config    = closeable.get[Config]
        closeable.close()
        assertTrue(config.debug == false)
      },
      test("creates scope with wires for dependencies") {
        val closeable = injected[SimpleService](shared[Config])(Scope.global)
        val svc       = closeable.get[SimpleService]
        closeable.close()
        assertTrue(svc != null)
      },
      test("handles AutoCloseable cleanup") {
        val closeable = injected[CloseableConfig]()(Scope.global)
        val instance  = closeable.get[CloseableConfig]
        assertTrue(!instance.closed)
        closeable.close()
        assertTrue(instance.closed)
      }
    ),
    suite("Wireable.from")(
      test("creates Wireable for no-arg class") {
        val wireable          = Wireable.from[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        // With whitebox macro, wire type is inferred correctly - no cast needed
        val ctx = wireable.wire.construct(scope)
        assertTrue(ctx.get[Config].debug == false)
      },
      test("creates Wireable for class with dependency") {
        val wireable          = Wireable.from[SimpleService]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val configCtx         = Context(new Config)
        val scope             = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        // In type is inferred as Config - wire works directly
        val ctx = wireable.wire.construct(scope)
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("handles AutoCloseable") {
        val wireable                   = Wireable.from[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        val ctx                        = wireable.wire.construct(scope)
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("manual Wireable for trait with implementation") {
        val wireable: Wireable.Typed[Config, DatabaseTrait] = implicitly[Wireable.Typed[Config, DatabaseTrait]]
        val wire: Wire[Config, DatabaseTrait]               = wireable.wire
        val parent: Scope.Any                               = Scope.global
        val finalizers                                      = new Finalizers
        val configCtx                                       = Context(new Config)
        val scope                                           = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        val ctx                                             = wire.construct(scope)
        val db                                              = ctx.get[DatabaseTrait]
        assertTrue(db.query() == "querying with false")
      },
      test("In type is properly inferred by whitebox macro") {
        // This test verifies that the In type member is correctly inferred
        val wireable = Wireable.from[SimpleService]
        // The wire should have type Wire[Config, SimpleService], not Wire[Any, SimpleService]
        // This assignment would fail to compile if In was inferred as Any
        val wire: Wire[Config, SimpleService] = wireable.wire
        assertTrue(wire != null)
      }
    )
  )
}
