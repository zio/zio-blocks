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

  class ServiceWithScope(val name: String)(using Scope.Any) {
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

  object DatabaseTrait {
    given Wireable.Typed[Config, DatabaseTrait] = new Wireable[DatabaseTrait] {
      type In = Config
      def wire: Wire[Config, DatabaseTrait] = Wire.Shared[Config, DatabaseTrait] {
        val scope  = summon[Scope.Has[Config]]
        val config = scope.get[Config]
        val impl   = new DatabaseImpl(config)
        scope.defer(impl.close())
        Context[DatabaseTrait](impl)
      }
    }
  }

  def spec = suite("package functions (Scala 3)")(
    test("defer delegates to scope") {
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val f                 = new Finalizers
      val config            = new Config
      val s                 = Scope.makeCloseable[Config, TNil](parent, Context(config), f)
      {
        given Scope.Any = s
        defer { cleaned = true }
      }
      s.close()
      assertTrue(cleaned)
    },
    test("get delegates to scope") {
      val parent: Scope.Any = Scope.global
      val config            = new Config
      val f                 = new Finalizers
      val s                 = Scope.makeCloseable[Config, TNil](parent, Context(config), f)
      val retrieved         = {
        given Scope.Has[Config] = s
        get[Config]
      }
      s.close()
      assertTrue(retrieved eq config)
    },
    test("injectedValue creates closeable scope") {
      val parent: Scope.Any = Scope.global
      val config            = new Config
      val closeable         = {
        given Scope.Any = parent
        injectedValue(config)
      }
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved eq config)
    },
    test("injectedValue registers AutoCloseable cleanup") {
      val parent: Scope.Any = Scope.global
      val resource          = new CloseableResource("test")
      val closeable         = {
        given Scope.Any = parent
        injectedValue(resource)
      }
      closeable.close()
      assertTrue(resource.closed)
    },
    suite("shared[T]")(
      test("derives wire for no-arg class") {
        val wire              = shared[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]  = scope
        val ctx               = wire.construct
        assertTrue(ctx.get[Config].debug == false)
      },
      test("derives wire for class with one dependency") {
        val wire                = shared[SimpleService]
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val configCtx           = Context(new Config)
        val scope               = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config] = scope
        val ctx                 = wire.construct
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("derives wire for class with two dependencies") {
        val wire                           = shared[ServiceWith2Deps]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wire.construct
        assertTrue(ctx.get[ServiceWith2Deps] != null)
      },
      test("handles AutoCloseable") {
        val wire                       = shared[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        given Scope.Has[Any]           = scope
        val ctx                        = wire.construct
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("uses Wireable when available") {
        val wire                = shared[DatabaseTrait]
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val configCtx           = Context(new Config)
        val scope               = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config] = scope
        val ctx                 = wire.construct
        val db                  = ctx.get[DatabaseTrait]
        assertTrue(db.query() == "querying with false")
      }
    ),
    suite("unique[T]")(
      test("derives wire for no-arg class") {
        val wire              = unique[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]  = scope
        val ctx               = wire.construct
        assertTrue(ctx.get[Config].debug == false)
      },
      test("derives wire for class with dependency") {
        val wire                = unique[SimpleService]
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val configCtx           = Context(new Config)
        val scope               = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config] = scope
        val ctx                 = wire.construct
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("handles AutoCloseable") {
        val wire                       = unique[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        given Scope.Has[Any]           = scope
        val ctx                        = wire.construct
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      }
    ),
    suite("injected[T]")(
      test("creates scope for no-arg class") {
        given Scope.Any = Scope.global
        val closeable   = injected[Config]()
        val config      = closeable.get[Config]
        closeable.close()
        assertTrue(config.debug == false)
      },
      test("creates scope with wires for dependencies") {
        given Scope.Any = Scope.global
        val closeable   = injected[SimpleService](shared[Config])
        val svc         = closeable.get[SimpleService]
        closeable.close()
        assertTrue(svc != null)
      },
      test("handles AutoCloseable") {
        given Scope.Any = Scope.global
        val closeable   = injected[CloseableConfig]()
        val instance    = closeable.get[CloseableConfig]
        assertTrue(!instance.closed)
        closeable.close()
        assertTrue(instance.closed)
      },
      test("close cleans up AutoCloseable") {
        given Scope.Any = Scope.global
        val closeable   = injected[CloseableConfig]()
        val instance    = closeable.get[CloseableConfig]
        assertTrue(!instance.closed)
        closeable.close()
        assertTrue(instance.closed)
      },
      test("uses Wireable when available") {
        val wire                = shared[DatabaseTrait]
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val configCtx           = Context(new Config)
        val scope               = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config] = scope
        val ctx                 = wire.construct
        val db                  = ctx.get[DatabaseTrait]
        assertTrue(db.query().contains("querying"))
      }
    ),
    suite("Wireable.from")(
      test("creates Wireable for no-arg class") {
        val wireable          = Wireable.from[Config]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]  = scope
        val ctx               = wireable.wire.construct
        assertTrue(ctx.get[Config].debug == false)
      },
      test("creates Wireable for class with dependency") {
        val wireable            = Wireable.from[SimpleService]
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val configCtx           = Context(new Config)
        val scope               = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config] = scope
        val ctx                 = wireable.wire.construct
        assertTrue(ctx.get[SimpleService] != null)
      },
      test("handles AutoCloseable") {
        val wireable                   = Wireable.from[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        given Scope.Has[Any]           = scope
        val ctx                        = wireable.wire.construct
        val instance                   = ctx.get[CloseableConfig]
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("manual Wireable for trait with implementation") {
        // With Wireable.Typed, the In type is preserved in the type signature
        val wireable: Wireable.Typed[Config, DatabaseTrait] = summon[Wireable.Typed[Config, DatabaseTrait]]
        val wire: Wire[Config, DatabaseTrait]               = wireable.wire
        val parent: Scope.Any                               = Scope.global
        val finalizers                                      = new Finalizers
        val configCtx                                       = Context(new Config)
        val scope                                           = Scope.makeCloseable[Config, TNil](parent, configCtx, finalizers)
        given Scope.Has[Config]                             = scope
        val ctx                                             = wire.construct
        val db                                              = ctx.get[DatabaseTrait]
        assertTrue(db.query() == "querying with false")
      }
    )
  )
}
