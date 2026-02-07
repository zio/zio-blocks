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

  def spec = suite("Memoization behavior (Scala 2)")(
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
      val config     = new Config
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      val wire       = shared[SimpleService]
      val svc1       = wire.make(scope)
      val svc2       = wire.make(scope)
      assertTrue(
        (svc1.config eq config) && (svc2.config eq config) &&
          (svc1 ne svc2)
      )
    },
    test("unique[T] creates fresh instances on each construct call") {
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val wire       = unique[Config]
      val cfg1       = wire.make(scope)
      val cfg2       = wire.make(scope)
      assertTrue(cfg1 ne cfg2)
    },
    test("shared[T] uses same dependency instance for multiple services") {
      val config     = new Config
      val db         = new Database
      val parent     = Scope.global
      val finalizers = new Finalizers
      val depsCtx    = Context(config).add(db)
      val scope      = Scope.makeCloseable[Config with Database, Scope.Global](parent, depsCtx, finalizers)

      val wire1 = shared[SimpleService]
      val wire2 = shared[ServiceWith2Deps]

      val svc1 = wire1.make(scope)
      val svc2 = wire2.make(scope)

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

      val parent     = Scope.global
      val finalizers = new Finalizers

      val configWire = shared[CountedConfig]
      val emptyScope = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val cfg        = configWire.make(emptyScope)

      val configCtx   = Context(cfg)
      val configScope = Scope.makeCloseable[CountedConfig, Scope.Global](parent, configCtx, finalizers)
      val dbWire      = shared[CountedDatabase]
      val db          = dbWire.make(configScope)

      val fullCtx   = configCtx.add(db)
      val fullScope = Scope.makeCloseable[CountedConfig with CountedDatabase, Scope.Global](parent, fullCtx, finalizers)
      val svcWire   = shared[CountedService]
      val svc       = svcWire.make(fullScope)

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
      val existingConfig = new Config
      val parent         = Scope.global
      val finalizers     = new Finalizers
      val scope          = Scope.makeCloseable[Config, Scope.Global](parent, Context(existingConfig), finalizers)

      val wire      = unique[Config]
      val newConfig = wire.make(scope)

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
      val parent     = Scope.global
      val finalizers = new Finalizers

      val baseWire  = shared[BaseConfig]
      val baseScope = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val base      = baseWire.make(baseScope)

      val baseCtx  = Context(base)
      val scope1   = Scope.makeCloseable[BaseConfig, Scope.Global](parent, baseCtx, finalizers)
      val leftWire = shared[LeftService]
      val left     = leftWire.make(scope1)

      val rightWire = shared[RightService]
      val right     = rightWire.make(scope1)

      val fullCtx   = baseCtx.add(left).add(right)
      val fullScope =
        Scope.makeCloseable[BaseConfig with LeftService with RightService, Scope.Global](parent, fullCtx, finalizers)
      val topWire = shared[TopService]
      val top     = topWire.make(fullScope)

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
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)

      val sharedWire = shared[TrackedResource]
      val uniqueWire = unique[TrackedResource]

      sharedWire.make(scope)
      sharedWire.make(scope)
      val sharedCountAfter2 = Counter.count

      uniqueWire.make(scope)
      uniqueWire.make(scope)
      val totalAfter4 = Counter.count

      assertTrue(sharedCountAfter2 == 2 && totalAfter4 == 4)
    }
  )
}
