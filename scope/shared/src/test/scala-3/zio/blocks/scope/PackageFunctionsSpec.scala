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
        val config = $[Config]
        val impl   = new DatabaseImpl(config)
        defer(impl.close())
        Context[DatabaseTrait](impl)
      }
    }
  }

  def spec = suite("package functions (Scala 3)")(
    test("defer registers cleanup on scope") {
      var cleaned     = false
      val config      = new Config
      val closeable   = Scope.makeCloseable[Config, TNil](Scope.global, Context(config), new Finalizers)
      given Scope.Any = closeable
      defer { cleaned = true }
      closeable.close()
      assertTrue(cleaned)
    },
    test("$ retrieves from scope") {
      val config              = new Config
      val closeable           = Scope.makeCloseable[Config, TNil](Scope.global, Context(config), new Finalizers)
      given Scope.Has[Config] = closeable
      val retrieved           = $[Config]
      closeable.close()
      assertTrue(retrieved eq config)
    },
    test("scope.injected creates closeable scope") {
      val closeable = Scope.global.injected[Config]()
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved.debug == false) // Config was constructed
    },
    test("scope.injected with wires builds dependencies") {
      val config    = new Config
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
    suite("top-level injected[T]")(
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
      test("handles AutoCloseable cleanup") {
        given Scope.Any = Scope.global
        val closeable   = injected[CloseableConfig]()
        val instance    = closeable.get[CloseableConfig]
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
