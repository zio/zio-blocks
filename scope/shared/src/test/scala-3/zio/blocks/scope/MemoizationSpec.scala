package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object MemoizationSpec extends ZIOSpecDefault {

  class Config {
    val debug: Boolean = false
  }

  class Database {
    val url: String = "jdbc://localhost"
  }

  class SimpleService(val config: Config)

  class ServiceWith2Deps(val config: Config, val db: Database)

  def spec = suite("Memoization behavior")(
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
      val config              = new Config
      val parent              = Scope.global
      val finalizers          = new Finalizers
      val scope               = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      given Scope.Has[Config] = scope
      val wire                = shared[SimpleService]
      val ctx1                = wire.construct
      val ctx2                = wire.construct
      val svc1                = ctx1.get[SimpleService]
      val svc2                = ctx2.get[SimpleService]
      assertTrue(
        (svc1.config `eq` config) && (svc2.config `eq` config) &&
          (svc1 `ne` svc2)
      )
    },
    test("unique[T] creates fresh instances on each construct call") {
      val parent           = Scope.global
      val finalizers       = new Finalizers
      val scope            = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      given Scope.Has[Any] = scope
      val wire             = unique[Config]
      val ctx1             = wire.construct
      val ctx2             = wire.construct
      val cfg1             = ctx1.get[Config]
      val cfg2             = ctx2.get[Config]
      assertTrue(cfg1 `ne` cfg2)
    },
    test("shared[T] uses same dependency instance for multiple services") {
      val config                         = new Config
      val db                             = new Database
      val parent                         = Scope.global
      val finalizers                     = new Finalizers
      val depsCtx                        = Context(config).add(db)
      val scope                          = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
      given Scope.Has[Config & Database] = scope

      val wire1 = shared[SimpleService]
      val wire2 = shared[ServiceWith2Deps]

      val svc1 = wire1.construct.get[SimpleService]
      val svc2 = wire2.construct.get[ServiceWith2Deps]

      assertTrue((svc1.config `eq` config) && (svc2.config `eq` config) && (svc2.db `eq` db))
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

      class CountedDatabase(val config: CountedConfig) {
        ConstructionCounter.dbCount += 1
        val url: String = "jdbc://localhost"
      }

      class CountedService(val db: CountedDatabase, val config: CountedConfig) {
        ConstructionCounter.serviceCount += 1
      }

      ConstructionCounter.reset()

      val parent     = Scope.global
      val finalizers = new Finalizers

      val configWire       = shared[CountedConfig]
      val emptyScope       = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      given Scope.Has[Any] = emptyScope
      val configCtx        = configWire.construct
      val cfg              = configCtx.get[CountedConfig]

      val configScope = Scope.makeCloseable[CountedConfig, Scope.Global](parent, configCtx, finalizers)
      val dbWire      = shared[CountedDatabase]
      val dbCtx       = dbWire.construct(using configScope)
      val db          = dbCtx.get[CountedDatabase]

      val fullCtx   = configCtx.add(db)
      val fullScope = Scope.makeCloseable[CountedConfig & CountedDatabase, Scope.Global](parent, fullCtx, finalizers)
      val svcWire   = shared[CountedService]
      val svcCtx    = svcWire.construct(using fullScope)
      val svc       = svcCtx.get[CountedService]

      assertTrue(
        ConstructionCounter.configCount == 1 &&
          ConstructionCounter.dbCount == 1 &&
          ConstructionCounter.serviceCount == 1 &&
          (svc.config `eq` cfg) &&
          (svc.db `eq` db) &&
          (db.config `eq` cfg)
      )
    },
    test("unique wire creates new instance even when type exists in scope") {
      val existingConfig      = new Config
      val parent              = Scope.global
      val finalizers          = new Finalizers
      val scope               = Scope.makeCloseable[Config, Scope.Global](parent, Context(existingConfig), finalizers)
      given Scope.Has[Config] = scope

      val wire      = unique[Config]
      val ctx       = wire.construct
      val newConfig = ctx.get[Config]

      assertTrue(newConfig `ne` existingConfig)
    },
    test("diamond dependency pattern shares common ancestor") {
      var baseCount = 0

      class BaseConfig {
        baseCount += 1
        val value: Int = 42
      }

      class LeftService(val base: BaseConfig) {
        val name = "left"
      }

      class RightService(val base: BaseConfig) {
        val name = "right"
      }

      class TopService(val left: LeftService, val right: RightService) {
        def checkSameBase: Boolean = left.base eq right.base
      }

      baseCount = 0
      val parent     = Scope.global
      val finalizers = new Finalizers

      val baseWire         = shared[BaseConfig]
      val baseScope        = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      given Scope.Has[Any] = baseScope
      val baseCtx          = baseWire.construct
      val base             = baseCtx.get[BaseConfig]

      val scope1   = Scope.makeCloseable[BaseConfig, Scope.Global](parent, baseCtx, finalizers)
      val leftWire = shared[LeftService]
      val leftCtx  = leftWire.construct(using scope1)
      val left     = leftCtx.get[LeftService]

      val rightWire = shared[RightService]
      val rightCtx  = rightWire.construct(using scope1)
      val right     = rightCtx.get[RightService]

      val fullCtx   = baseCtx.add(left).add(right)
      val fullScope =
        Scope.makeCloseable[BaseConfig & LeftService & RightService, Scope.Global](parent, fullCtx, finalizers)
      val topWire = shared[TopService]
      val topCtx  = topWire.construct(using fullScope)
      val top     = topCtx.get[TopService]

      assertTrue(
        baseCount == 1 &&
          top.checkSameBase &&
          (top.left.base `eq` base) &&
          (top.right.base `eq` base)
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
      val parent           = Scope.global
      val finalizers       = new Finalizers
      val scope            = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      given Scope.Has[Any] = scope

      val sharedWire = shared[TrackedResource]
      val uniqueWire = unique[TrackedResource]

      sharedWire.construct
      sharedWire.construct
      val sharedCountAfter2 = Counter.count

      uniqueWire.construct
      uniqueWire.construct
      val totalAfter4 = Counter.count

      assertTrue(sharedCountAfter2 == 2 && totalAfter4 == 4)
    }
  )
}
