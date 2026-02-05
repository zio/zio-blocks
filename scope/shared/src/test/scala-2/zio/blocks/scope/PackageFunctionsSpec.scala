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

  // Test classes for arbitrary arity (3, 4, 5, 6 parameters)
  class ServiceWith3Deps(val config: Config, val db: Database, val cache: Cache)

  class Logger {
    val prefix: String = "[LOG]"
  }

  class ServiceWith4Deps(val config: Config, val db: Database, val cache: Cache, val logger: Logger)

  class Metrics {
    val enabled: Boolean = true
  }

  class ServiceWith5Deps(
    val config: Config,
    val db: Database,
    val cache: Cache,
    val logger: Logger,
    val metrics: Metrics
  )

  class Tracer {
    val sampleRate: Double = 0.1
  }

  class ServiceWith6Deps(
    val config: Config,
    val db: Database,
    val cache: Cache,
    val logger: Logger,
    val metrics: Metrics,
    val tracer: Tracer
  )

  class AutoCloseableWith5Deps(
    val config: Config,
    val db: Database,
    val cache: Cache,
    val logger: Logger,
    val metrics: Metrics
  ) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  class ServiceWith5DepsAndScope(
    val config: Config,
    val db: Database,
    val cache: Cache,
    val logger: Logger,
    val metrics: Metrics
  )(implicit scope: Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

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
    test("scope.injected without parens creates closeable scope") {
      val closeable = Scope.global.injected[Config]
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved.debug == false)
    },
    test("top-level injected without parens creates closeable scope") {
      @annotation.unused
      implicit val s: Scope.Any = Scope.global
      val closeable             = injected[Config]
      val retrieved             = closeable.get[Config]
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
    ),
    suite("Memoization behavior")(
      test("shared[T] returns Wire.Shared with isShared=true") {
        val wire = shared[Config]
        assertTrue(wire.isShared && !wire.isUnique)
      },
      test("unique[T] returns Wire.Unique with isUnique=true") {
        val wire = unique[Config]
        assertTrue(wire.isUnique && !wire.isShared)
      },
      test("shared wire can be converted to unique and back") {
        val sharedWire = shared[Config]
        val uniqueWire = sharedWire.unique
        val backShared = uniqueWire.shared
        assertTrue(sharedWire.isShared && uniqueWire.isUnique && backShared.isShared)
      },
      test("shared[T] reuses instance from scope when already present") {
        val config            = new Config
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        val wire              = shared[SimpleService]
        val ctx1              = wire.construct(scope)
        val ctx2              = wire.construct(scope)
        val svc1              = ctx1.get[SimpleService]
        val svc2              = ctx2.get[SimpleService]
        assertTrue(
          (svc1.config eq config) && (svc2.config eq config) &&
            (svc1 ne svc2)
        )
      },
      test("unique[T] creates fresh instances on each construct call") {
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        val wire              = unique[Config]
        val ctx1              = wire.construct(scope)
        val ctx2              = wire.construct(scope)
        val cfg1              = ctx1.get[Config]
        val cfg2              = ctx2.get[Config]
        assertTrue(cfg1 ne cfg2)
      },
      test("shared[T] uses same dependency instance for multiple services") {
        val config            = new Config
        val db                = new Database
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(config).add(db)
        val scope             = Scope.makeCloseable[Config with Database, TNil](parent, depsCtx, finalizers)

        val wire1 = shared[SimpleService]
        val wire2 = shared[ServiceWith2Deps]

        val svc1 = wire1.construct(scope).get[SimpleService]
        val svc2 = wire2.construct(scope).get[ServiceWith2Deps]

        assertTrue((svc1.config eq config) && (svc2.config eq config) && (svc2.db eq db))
      },
      test("deep dependency graph shares base dependency via scope") {
        object ConstructionCounter {
          var configCount: Int  = 0
          var dbCount: Int      = 0
          var serviceCount: Int = 0
          def reset(): Unit     = { configCount = 0; dbCount = 0; serviceCount = 0 }
        }

        class CountedConfig {
          ConstructionCounter.configCount += 1
          val debug: Boolean = false
        }

        class CountedDatabase(@annotation.unused val config: CountedConfig) {
          ConstructionCounter.dbCount += 1
          val url: String = "jdbc://localhost"
        }

        class CountedService(@annotation.unused val db: CountedDatabase, @annotation.unused val config: CountedConfig) {
          ConstructionCounter.serviceCount += 1
        }

        ConstructionCounter.reset()

        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers

        val configWire = shared[CountedConfig]
        val emptyScope = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        val configCtx  = configWire.construct(emptyScope)
        val cfg        = configCtx.get[CountedConfig]

        val configScope = Scope.makeCloseable[CountedConfig, TNil](parent, configCtx, finalizers)
        val dbWire      = shared[CountedDatabase]
        val dbCtx       = dbWire.construct(configScope)
        val db          = dbCtx.get[CountedDatabase]

        val fullCtx   = configCtx.add(db)
        val fullScope = Scope.makeCloseable[CountedConfig with CountedDatabase, TNil](parent, fullCtx, finalizers)
        val svcWire   = shared[CountedService]
        val svcCtx    = svcWire.construct(fullScope)
        val svc       = svcCtx.get[CountedService]

        assertTrue(
          ConstructionCounter.configCount == 1 &&
            ConstructionCounter.dbCount == 1 &&
            ConstructionCounter.serviceCount == 1 &&
            (svc.config eq cfg) &&
            (svc.db eq db) &&
            (db.config eq cfg)
        )
      },
      test("unique wire creates new instance even when type exists in scope") {
        val existingConfig    = new Config
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Config, TNil](parent, Context(existingConfig), finalizers)

        val wire      = unique[Config]
        val ctx       = wire.construct(scope)
        val newConfig = ctx.get[Config]

        assertTrue(newConfig ne existingConfig)
      },
      test("diamond dependency pattern shares common ancestor") {
        var baseCount = 0

        class BaseConfig {
          baseCount += 1
          val value: Int = 42
        }

        class LeftService(@annotation.unused val base: BaseConfig) {
          val name = "left"
        }

        class RightService(@annotation.unused val base: BaseConfig) {
          val name = "right"
        }

        class TopService(@annotation.unused val left: LeftService, @annotation.unused val right: RightService) {
          def checkSameBase: Boolean = left.base eq right.base
        }

        baseCount = 0
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers

        val baseWire  = shared[BaseConfig]
        val baseScope = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        val baseCtx   = baseWire.construct(baseScope)
        val base      = baseCtx.get[BaseConfig]

        val scope1   = Scope.makeCloseable[BaseConfig, TNil](parent, baseCtx, finalizers)
        val leftWire = shared[LeftService]
        val leftCtx  = leftWire.construct(scope1)
        val left     = leftCtx.get[LeftService]

        val rightWire = shared[RightService]
        val rightCtx  = rightWire.construct(scope1)
        val right     = rightCtx.get[RightService]

        val fullCtx   = baseCtx.add(left).add(right)
        val fullScope =
          Scope.makeCloseable[BaseConfig with LeftService with RightService, TNil](parent, fullCtx, finalizers)
        val topWire = shared[TopService]
        val topCtx  = topWire.construct(fullScope)
        val top     = topCtx.get[TopService]

        assertTrue(
          baseCount == 1 &&
            top.checkSameBase &&
            (top.left.base eq base) &&
            (top.right.base eq base)
        )
      },
      test("construction counter verifies shared vs unique behavior") {
        object Counter {
          var count: Int    = 0
          def reset(): Unit = count = 0
        }

        class TrackedResource {
          Counter.count += 1
          val id: Int = Counter.count
        }

        Counter.reset()
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)

        val sharedWire = shared[TrackedResource]
        val uniqueWire = unique[TrackedResource]

        sharedWire.construct(scope)
        sharedWire.construct(scope)
        val sharedCountAfter2 = Counter.count

        uniqueWire.construct(scope)
        uniqueWire.construct(scope)
        val totalAfter4 = Counter.count

        assertTrue(sharedCountAfter2 == 2 && totalAfter4 == 4)
      }
    ),
    suite("Arbitrary arity (3-6 parameters)")(
      test("shared[T] works with 3 constructor parameters") {
        val wire              = shared[ServiceWith3Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache)
        val scope             = Scope.makeCloseable[Config with Database with Cache, TNil](parent, depsCtx, finalizers)
        val ctx               = wire.construct(scope)
        val svc               = ctx.get[ServiceWith3Deps]
        assertTrue(svc != null && svc.config != null && svc.db != null && svc.cache != null)
      },
      test("shared[T] works with 4 constructor parameters") {
        val wire              = shared[ServiceWith4Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger)
        val scope             =
          Scope.makeCloseable[Config with Database with Cache with Logger, TNil](parent, depsCtx, finalizers)
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith4Deps]
        assertTrue(svc != null && svc.logger.prefix == "[LOG]")
      },
      test("shared[T] works with 5 constructor parameters") {
        val wire              = shared[ServiceWith5Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope             =
          Scope
            .makeCloseable[Config with Database with Cache with Logger with Metrics, TNil](parent, depsCtx, finalizers)
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith5Deps]
        assertTrue(svc != null && svc.metrics.enabled)
      },
      test("shared[T] works with 6 constructor parameters") {
        val wire              = shared[ServiceWith6Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           =
          Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics).add(new Tracer)
        val scope =
          Scope.makeCloseable[Config with Database with Cache with Logger with Metrics with Tracer, TNil](
            parent,
            depsCtx,
            finalizers
          )
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith6Deps]
        assertTrue(svc != null && svc.tracer.sampleRate == 0.1)
      },
      test("unique[T] works with 5 constructor parameters") {
        val wire              = unique[ServiceWith5Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope             =
          Scope
            .makeCloseable[Config with Database with Cache with Logger with Metrics, TNil](parent, depsCtx, finalizers)

        val ctx1 = wire.construct(scope)
        val ctx2 = wire.construct(scope)
        val svc1 = ctx1.get[ServiceWith5Deps]
        val svc2 = ctx2.get[ServiceWith5Deps]

        assertTrue(svc1 ne svc2)
      },
      test("injected[T] works with 5 wires") {
        val closeable = Scope.global.injected[ServiceWith5Deps](
          shared[Config],
          shared[Database],
          shared[Cache],
          shared[Logger],
          shared[Metrics]
        )
        val svc = closeable.get[ServiceWith5Deps]
        closeable.close()
        assertTrue(svc != null && svc.config != null && svc.metrics.enabled)
      },
      test("injected[T] works with 6 wires") {
        val closeable = Scope.global.injected[ServiceWith6Deps](
          shared[Config],
          shared[Database],
          shared[Cache],
          shared[Logger],
          shared[Metrics],
          shared[Tracer]
        )
        val svc = closeable.get[ServiceWith6Deps]
        closeable.close()
        assertTrue(svc != null && svc.tracer.sampleRate == 0.1)
      },
      test("5-param AutoCloseable is properly closed") {
        val (parentScope, closeParent) = Scope.createTestableScope()
        val closeable                  = parentScope.injected[AutoCloseableWith5Deps](
          shared[Config],
          shared[Database],
          shared[Cache],
          shared[Logger],
          shared[Metrics]
        )
        val svc = closeable.get[AutoCloseableWith5Deps]
        assertTrue(!svc.closed)
        closeable.close()
        assertTrue(svc.closed)
      },
      test("5-param with Scope.Any param registers cleanup") {
        val (parentScope, closeParent) = Scope.createTestableScope()
        val closeable                  = parentScope.injected[ServiceWith5DepsAndScope](
          shared[Config],
          shared[Database],
          shared[Cache],
          shared[Logger],
          shared[Metrics]
        )
        val svc = closeable.get[ServiceWith5DepsAndScope]
        assertTrue(!svc.cleanedUp)
        closeable.close()
        closeParent()
        assertTrue(svc.cleanedUp)
      }
    )
  )
}
