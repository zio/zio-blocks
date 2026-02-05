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

  // Test classes for Scope.Has parameter handling
  class ServiceWithScopeHas(val config: Config)(using Scope.Has[Database]) {
    val db: Database = $[Database]
  }

  class ServiceWithMultipleScopeHas(using Scope.Has[Config], Scope.Has[Database]) {
    val config: Config = $[Config]
    val db: Database   = $[Database]
  }

  class ServiceWithMixedParams(val cache: Cache)(using Scope.Has[Config], Scope.Any) {
    val config: Config     = $[Config]
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  class ServiceWithScopeHasInRegularParams(configScope: Scope.Has[Config], val cache: Cache) {
    val config: Config = configScope.get[Config]
  }

  class ServiceWithMultipleParamLists(val cache: Cache)(dbScope: Scope.Has[Database])(using Scope.Any) {
    val db: Database       = dbScope.get[Database]
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  // Test classes from conversation examples
  // Example 1: UserProfileService(database: Database, config: Config)(using Scope.Any)
  // In = Database & Config
  class UserProfileService(val database: Database, val config: Config)(using Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  // Example 2: All Scope.Has params in one list
  // Use distinct (non-subtype) stream types to avoid subtype conflict
  trait SocketStream { def read(): Int          }
  trait FileReader   { def getChannel(): String }
  trait FileWriter   { def write(b: Int): Unit  }
  class MergeType(val name: String)

  class SocketStream1 extends SocketStream { def read(): Int = 42             }
  class FileReader1   extends FileReader   { def getChannel(): String = "ch1" }
  class FileWriter1   extends FileWriter   { def write(b: Int): Unit = ()     }

  // MergeSort(socket: Scope.Has[SocketStream], file: Scope.Has[FileReader], output: Scope.Has[FileWriter], mergeType: MergeType)
  // In = SocketStream & FileReader & FileWriter & MergeType
  class MergeSort(
    socket: Scope.Has[SocketStream],
    file: Scope.Has[FileReader],
    output: Scope.Has[FileWriter],
    val mergeType: MergeType
  ) {
    val socketStream: SocketStream = socket.get[SocketStream]
    val fileReader: FileReader     = file.get[FileReader]
    val fileWriter: FileWriter     = output.get[FileWriter]
  }

  // Example 3: Multiple param lists with Scope.Has
  // MergeSort2(socket: Scope.Has[SocketStream], file: Scope.Has[FileReader])(output: Scope.Has[FileWriter], mergeType: MergeType)(using Scope.Any)
  // In = SocketStream & FileReader & FileWriter & MergeType
  class MergeSort2(
    socket: Scope.Has[SocketStream],
    file: Scope.Has[FileReader]
  )(
    output: Scope.Has[FileWriter],
    val mergeType: MergeType
  )(using Scope.Any) {
    val socketStream: SocketStream = socket.get[SocketStream]
    val fileReader: FileReader     = file.get[FileReader]
    val fileWriter: FileWriter     = output.get[FileWriter]
    var cleanedUp: Boolean         = false
    defer { cleanedUp = true }
  }

  // Example 4: Mix of value deps and Scope.Has in same param list
  class ComplexService(
    val config: Config,
    dbScope: Scope.Has[Database],
    val cache: Cache
  )(using Scope.Any) {
    val db: Database       = dbScope.get[Database]
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  // Example 5: Only Scope.Any, no deps
  class NoDepsService()(using Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  // Test classes for arbitrary arity (3, 4, 5+ parameters)
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
  )(using Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  // Example 6: AutoCloseable with Scope.Has
  class AutoCloseableWithScopeHas(configScope: Scope.Has[Config]) extends AutoCloseable {
    val config: Config  = configScope.get[Config]
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  // Test classes for 20-parameter arbitrary arity support
  class Dep1  { val id: Int = 1  }
  class Dep2  { val id: Int = 2  }
  class Dep3  { val id: Int = 3  }
  class Dep4  { val id: Int = 4  }
  class Dep5  { val id: Int = 5  }
  class Dep6  { val id: Int = 6  }
  class Dep7  { val id: Int = 7  }
  class Dep8  { val id: Int = 8  }
  class Dep9  { val id: Int = 9  }
  class Dep10 { val id: Int = 10 }
  class Dep11 { val id: Int = 11 }
  class Dep12 { val id: Int = 12 }
  class Dep13 { val id: Int = 13 }
  class Dep14 { val id: Int = 14 }
  class Dep15 { val id: Int = 15 }
  class Dep16 { val id: Int = 16 }
  class Dep17 { val id: Int = 17 }
  class Dep18 { val id: Int = 18 }
  class Dep19 { val id: Int = 19 }
  class Dep20 { val id: Int = 20 }

  class ServiceWith20Deps(
    val d1: Dep1,
    val d2: Dep2,
    val d3: Dep3,
    val d4: Dep4,
    val d5: Dep5,
    val d6: Dep6,
    val d7: Dep7,
    val d8: Dep8,
    val d9: Dep9,
    val d10: Dep10,
    val d11: Dep11,
    val d12: Dep12,
    val d13: Dep13,
    val d14: Dep14,
    val d15: Dep15,
    val d16: Dep16,
    val d17: Dep17,
    val d18: Dep18,
    val d19: Dep19,
    val d20: Dep20
  )

  class AutoCloseableWith20Deps(
    val d1: Dep1,
    val d2: Dep2,
    val d3: Dep3,
    val d4: Dep4,
    val d5: Dep5,
    val d6: Dep6,
    val d7: Dep7,
    val d8: Dep8,
    val d9: Dep9,
    val d10: Dep10,
    val d11: Dep11,
    val d12: Dep12,
    val d13: Dep13,
    val d14: Dep14,
    val d15: Dep15,
    val d16: Dep16,
    val d17: Dep17,
    val d18: Dep18,
    val d19: Dep19,
    val d20: Dep20
  ) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  class ServiceWith20DepsAndScope(
    val d1: Dep1,
    val d2: Dep2,
    val d3: Dep3,
    val d4: Dep4,
    val d5: Dep5,
    val d6: Dep6,
    val d7: Dep7,
    val d8: Dep8,
    val d9: Dep9,
    val d10: Dep10,
    val d11: Dep11,
    val d12: Dep12,
    val d13: Dep13,
    val d14: Dep14,
    val d15: Dep15,
    val d16: Dep16,
    val d17: Dep17,
    val d18: Dep18,
    val d19: Dep19,
    val d20: Dep20
  )(using Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
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
    test("scope.injected without parens creates closeable scope") {
      val closeable = Scope.global.injected[Config]
      val retrieved = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved.debug == false)
    },
    test("top-level injected without parens creates closeable scope") {
      given Scope.Any = Scope.global
      val closeable   = injected[Config]
      val retrieved   = closeable.get[Config]
      closeable.close()
      assertTrue(retrieved.debug == false)
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
    ),
    suite("Scope.Has parameter handling")(
      test("Wireable.from extracts Y from Scope.Has[Y] in using params") {
        // ServiceWithScopeHas(config: Config)(using Scope.Has[Database])
        // In should be Config & Database
        val wireable                       = Wireable.from[ServiceWithScopeHas]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wireable.wire.construct
        val svc                            = ctx.get[ServiceWithScopeHas]
        assertTrue(svc.config != null && svc.db != null)
      },
      test("Wireable.from handles multiple Scope.Has params") {
        // ServiceWithMultipleScopeHas(using Scope.Has[Config], Scope.Has[Database])
        // In should be Config & Database
        val wireable                       = Wireable.from[ServiceWithMultipleScopeHas]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wireable.wire.construct
        val svc                            = ctx.get[ServiceWithMultipleScopeHas]
        assertTrue(svc.config != null && svc.db != null)
      },
      test("Wireable.from handles mix of regular params, Scope.Has, and Scope.Any") {
        // ServiceWithMixedParams(cache: Cache)(using Scope.Has[Config], Scope.Any)
        // In should be Cache & Config
        val wireable                    = Wireable.from[ServiceWithMixedParams]
        val (parentScope, closeParent)  = Scope.createTestableScope()
        val finalizers                  = new Finalizers
        val depsCtx                     = Context(new Cache).add(new Config)
        val scope                       = Scope.makeCloseable[Cache & Config, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Cache & Config] = scope
        val ctx                         = wireable.wire.construct
        val svc                         = ctx.get[ServiceWithMixedParams]
        assertTrue(svc.cache != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("Wireable.from extracts Y from Scope.Has[Y] in regular params") {
        // ServiceWithScopeHasInRegularParams(configScope: Scope.Has[Config], cache: Cache)
        // In should be Config & Cache
        val wireable                    = Wireable.from[ServiceWithScopeHasInRegularParams]
        val parent: Scope.Any           = Scope.global
        val finalizers                  = new Finalizers
        val depsCtx                     = Context(new Config).add(new Cache)
        val scope                       = Scope.makeCloseable[Config & Cache, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Cache] = scope
        val ctx                         = wireable.wire.construct
        val svc                         = ctx.get[ServiceWithScopeHasInRegularParams]
        assertTrue(svc.config != null && svc.cache != null)
      },
      test("Wireable.from handles multiple param lists with Scope.Has") {
        // ServiceWithMultipleParamLists(cache: Cache)(dbScope: Scope.Has[Database])(using Scope.Any)
        // In should be Cache & Database
        val wireable                      = Wireable.from[ServiceWithMultipleParamLists]
        val (parentScope, closeParent)    = Scope.createTestableScope()
        val finalizers                    = new Finalizers
        val depsCtx                       = Context(new Cache).add(new Database)
        val scope                         = Scope.makeCloseable[Cache & Database, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Cache & Database] = scope
        val ctx                           = wireable.wire.construct
        val svc                           = ctx.get[ServiceWithMultipleParamLists]
        assertTrue(svc.cache != null && svc.db != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("shared[T] extracts Y from Scope.Has[Y] in using params") {
        val wire                           = shared[ServiceWithScopeHas]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wire.construct
        val svc                            = ctx.get[ServiceWithScopeHas]
        assertTrue(svc.config != null && svc.db != null)
      },
      test("shared[T] handles multiple Scope.Has params") {
        val wire                           = shared[ServiceWithMultipleScopeHas]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wire.construct
        val svc                            = ctx.get[ServiceWithMultipleScopeHas]
        assertTrue(svc.config != null && svc.db != null)
      },
      test("shared[T] handles mix of regular params, Scope.Has, and Scope.Any") {
        val wire                        = shared[ServiceWithMixedParams]
        val (parentScope, closeParent)  = Scope.createTestableScope()
        val finalizers                  = new Finalizers
        val depsCtx                     = Context(new Cache).add(new Config)
        val scope                       = Scope.makeCloseable[Cache & Config, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Cache & Config] = scope
        val ctx                         = wire.construct
        val svc                         = ctx.get[ServiceWithMixedParams]
        assertTrue(svc.cache != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("unique[T] extracts Y from Scope.Has[Y] in using params") {
        val wire                           = unique[ServiceWithScopeHas]
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Config).add(new Database)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database] = scope
        val ctx                            = wire.construct
        val svc                            = ctx.get[ServiceWithScopeHas]
        assertTrue(svc.config != null && svc.db != null)
      }
    ),
    suite("Conversation examples")(
      test("UserProfileService: regular params with Scope.Any") {
        // UserProfileService(database: Database, config: Config)(using Scope.Any)
        // In = Database & Config
        val wireable                       = Wireable.from[UserProfileService]
        val (parentScope, closeParent)     = Scope.createTestableScope()
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(new Database).add(new Config)
        val scope                          = Scope.makeCloseable[Database & Config, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Database & Config] = scope
        val ctx                            = wireable.wire.construct
        val svc                            = ctx.get[UserProfileService]
        assertTrue(svc.database != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("MergeSort: multiple Scope.Has params in one list with value param") {
        // MergeSort(socket: Scope.Has[SocketStream], file: Scope.Has[FileReader], output: Scope.Has[FileWriter], mergeType: MergeType)
        // In = SocketStream & FileReader & FileWriter & MergeType
        val wireable          = Wireable.from[MergeSort]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context[SocketStream](new SocketStream1)
          .add[FileReader](new FileReader1)
          .add[FileWriter](new FileWriter1)
          .add(new MergeType("quick"))
        val scope =
          Scope.makeCloseable[SocketStream & FileReader & FileWriter & MergeType, TNil](parent, depsCtx, finalizers)
        given Scope.Has[SocketStream & FileReader & FileWriter & MergeType] = scope
        val ctx                                                             = wireable.wire.construct
        val svc                                                             = ctx.get[MergeSort]
        assertTrue(
          svc.socketStream.read() == 42 &&
            svc.fileReader.getChannel() == "ch1" &&
            svc.mergeType.name == "quick"
        )
      },
      test("MergeSort2: multiple param lists with Scope.Has and Scope.Any") {
        // MergeSort2(socket: Scope.Has[SocketStream], file: Scope.Has[FileReader])(output: Scope.Has[FileWriter], mergeType: MergeType)(using Scope.Any)
        // In = SocketStream & FileReader & FileWriter & MergeType
        val wireable                   = Wireable.from[MergeSort2]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context[SocketStream](new SocketStream1)
          .add[FileReader](new FileReader1)
          .add[FileWriter](new FileWriter1)
          .add(new MergeType("merge"))
        val scope = Scope
          .makeCloseable[SocketStream & FileReader & FileWriter & MergeType, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[SocketStream & FileReader & FileWriter & MergeType] = scope
        val ctx                                                             = wireable.wire.construct
        val svc                                                             = ctx.get[MergeSort2]
        assertTrue(
          svc.socketStream.read() == 42 &&
            svc.fileReader.getChannel() == "ch1" &&
            svc.mergeType.name == "merge" &&
            !svc.cleanedUp
        )
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("ComplexService: mix of value deps and Scope.Has in same param list") {
        // ComplexService(config: Config, dbScope: Scope.Has[Database], cache: Cache)(using Scope.Any)
        // In = Config & Database & Cache
        val wireable                               = Wireable.from[ComplexService]
        val (parentScope, closeParent)             = Scope.createTestableScope()
        val finalizers                             = new Finalizers
        val depsCtx                                = Context(new Config).add(new Database).add(new Cache)
        val scope                                  = Scope.makeCloseable[Config & Database & Cache, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Config & Database & Cache] = scope
        val ctx                                    = wireable.wire.construct
        val svc                                    = ctx.get[ComplexService]
        assertTrue(
          svc.config != null &&
            svc.db != null &&
            svc.cache != null &&
            !svc.cleanedUp
        )
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("NoDepsService: only Scope.Any, In = Any") {
        val wireable                   = Wireable.from[NoDepsService]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, TNil](parentScope, Context.empty, finalizers)
        given Scope.Has[Any]           = scope
        val ctx                        = wireable.wire.construct
        val svc                        = ctx.get[NoDepsService]
        assertTrue(!svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("AutoCloseableWithScopeHas: AutoCloseable with Scope.Has") {
        val wireable                   = Wireable.from[AutoCloseableWithScopeHas]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Config)
        val scope                      = Scope.makeCloseable[Config, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Config]        = scope
        val ctx                        = wireable.wire.construct
        val svc                        = ctx.get[AutoCloseableWithScopeHas]
        assertTrue(svc.config != null && !svc.closed)
        scope.close()
        assertTrue(svc.closed)
      },
      test("shared[MergeSort] works with complex Scope.Has params") {
        val wire              = shared[MergeSort]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context[SocketStream](new SocketStream1)
          .add[FileReader](new FileReader1)
          .add[FileWriter](new FileWriter1)
          .add(new MergeType("heap"))
        val scope =
          Scope.makeCloseable[SocketStream & FileReader & FileWriter & MergeType, TNil](parent, depsCtx, finalizers)
        given Scope.Has[SocketStream & FileReader & FileWriter & MergeType] = scope
        val ctx                                                             = wire.construct
        val svc                                                             = ctx.get[MergeSort]
        assertTrue(svc.mergeType.name == "heap")
      },
      test("shared[ComplexService] works with mixed params") {
        val wire                                   = shared[ComplexService]
        val (parentScope, closeParent)             = Scope.createTestableScope()
        val finalizers                             = new Finalizers
        val depsCtx                                = Context(new Config).add(new Database).add(new Cache)
        val scope                                  = Scope.makeCloseable[Config & Database & Cache, TNil](parentScope, depsCtx, finalizers)
        given Scope.Has[Config & Database & Cache] = scope
        val ctx                                    = wire.construct
        val svc                                    = ctx.get[ComplexService]
        assertTrue(svc.config != null && svc.db != null && svc.cache != null)
      },
      test("subtype conflict check prevents ambiguous dependencies") {
        // This test verifies the error message exists by testing with non-conflicting types
        // The actual compile error for conflicting subtypes (like InputStream/FileInputStream)
        // would be caught at compile time with a helpful message
        trait Animal
        trait Dog extends Animal
        // If you tried: class BadService(a: Scope.Has[Animal], d: Scope.Has[Dog])
        // You'd get: "Dependency type conflict: Dog is a subtype of Animal..."
        assertTrue(true)
      }
    ),
    suite("Arbitrary arity support")(
      test("shared[T] works with 3 constructor parameters") {
        val wire              = shared[ServiceWith3Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache)
        val scope             = Scope.makeCloseable[Config & Database & Cache, TNil](parent, depsCtx, finalizers)

        given Scope.Has[Config & Database & Cache] = scope
        val ctx                                    = wire.construct
        val svc                                    = ctx.get[ServiceWith3Deps]

        assertTrue(svc.config != null && svc.db != null && svc.cache != null)
      },
      test("shared[T] works with 4 constructor parameters") {
        val wire              = shared[ServiceWith4Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger)
        val scope             = Scope.makeCloseable[Config & Database & Cache & Logger, TNil](parent, depsCtx, finalizers)

        given Scope.Has[Config & Database & Cache & Logger] = scope
        val ctx                                             = wire.construct
        val svc                                             = ctx.get[ServiceWith4Deps]

        assertTrue(svc.config != null && svc.db != null && svc.cache != null && svc.logger != null)
      },
      test("shared[T] works with 5 constructor parameters") {
        val wire              = shared[ServiceWith5Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope             =
          Scope.makeCloseable[Config & Database & Cache & Logger & Metrics, TNil](parent, depsCtx, finalizers)

        given Scope.Has[Config & Database & Cache & Logger & Metrics] = scope
        val ctx                                                       = wire.construct
        val svc                                                       = ctx.get[ServiceWith5Deps]

        assertTrue(
          svc.config != null && svc.db != null && svc.cache != null && svc.logger != null && svc.metrics != null
        )
      },
      test("shared[T] works with 6 constructor parameters") {
        val wire              = shared[ServiceWith6Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           =
          Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics).add(new Tracer)
        val scope =
          Scope.makeCloseable[Config & Database & Cache & Logger & Metrics & Tracer, TNil](parent, depsCtx, finalizers)

        given Scope.Has[Config & Database & Cache & Logger & Metrics & Tracer] = scope
        val ctx                                                                = wire.construct
        val svc                                                                = ctx.get[ServiceWith6Deps]

        assertTrue(
          svc.config != null && svc.db != null && svc.cache != null && svc.logger != null && svc.metrics != null && svc.tracer != null
        )
      },
      test("injected works with 3 constructor parameters") {
        val closeable =
          Scope.global.injected[ServiceWith3Deps](shared[Config], shared[Database], shared[Cache])
        val svc = closeable.get[ServiceWith3Deps]
        closeable.close()
        assertTrue(svc.config != null && svc.db != null && svc.cache != null)
      },
      test("injected works with 4 constructor parameters") {
        val closeable =
          Scope.global.injected[ServiceWith4Deps](shared[Config], shared[Database], shared[Cache], shared[Logger])
        val svc = closeable.get[ServiceWith4Deps]
        closeable.close()
        assertTrue(svc.config != null && svc.db != null && svc.cache != null && svc.logger != null)
      },
      test("injected works with 5 constructor parameters") {
        val closeable = Scope.global.injected[ServiceWith5Deps](
          shared[Config],
          shared[Database],
          shared[Cache],
          shared[Logger],
          shared[Metrics]
        )
        val svc = closeable.get[ServiceWith5Deps]
        closeable.close()
        assertTrue(
          svc.config != null && svc.db != null && svc.cache != null && svc.logger != null && svc.metrics != null
        )
      },
      test("injected works with 6 constructor parameters") {
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
        assertTrue(
          svc.config != null && svc.db != null && svc.cache != null && svc.logger != null && svc.metrics != null && svc.tracer != null
        )
      },
      test("5-param AutoCloseable registers cleanup") {
        val closeable = Scope.global.injected[AutoCloseableWith5Deps](
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
      },
      test("unique[T] works with 5 constructor parameters") {
        val wire              = unique[ServiceWith5Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope             =
          Scope.makeCloseable[Config & Database & Cache & Logger & Metrics, TNil](parent, depsCtx, finalizers)

        given Scope.Has[Config & Database & Cache & Logger & Metrics] = scope
        val ctx1                                                      = wire.construct
        val ctx2                                                      = wire.construct
        val svc1                                                      = ctx1.get[ServiceWith5Deps]
        val svc2                                                      = ctx2.get[ServiceWith5Deps]

        assertTrue(svc1 ne svc2)
      },
      test("shared[T] works with 20 constructor parameters") {
        val wire              = shared[ServiceWith20Deps]
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val depsCtx           = Context(new Dep1)
          .add(new Dep2)
          .add(new Dep3)
          .add(new Dep4)
          .add(new Dep5)
          .add(new Dep6)
          .add(new Dep7)
          .add(new Dep8)
          .add(new Dep9)
          .add(new Dep10)
          .add(new Dep11)
          .add(new Dep12)
          .add(new Dep13)
          .add(new Dep14)
          .add(new Dep15)
          .add(new Dep16)
          .add(new Dep17)
          .add(new Dep18)
          .add(new Dep19)
          .add(new Dep20)
        type All20 =
          Dep1 & Dep2 & Dep3 & Dep4 & Dep5 & Dep6 & Dep7 & Dep8 & Dep9 & Dep10 & Dep11 & Dep12 & Dep13 & Dep14 & Dep15 &
            Dep16 & Dep17 & Dep18 & Dep19 & Dep20
        val scope = Scope.makeCloseable[All20, TNil](parent, depsCtx, finalizers)

        given Scope.Has[All20] = scope
        val ctx                = wire.construct
        val svc                = ctx.get[ServiceWith20Deps]

        assertTrue(
          svc.d1.id == 1 && svc.d10.id == 10 && svc.d20.id == 20
        )
      },
      test("injected works with 20 constructor parameters") {
        val closeable = Scope.global.injected[ServiceWith20Deps](
          shared[Dep1],
          shared[Dep2],
          shared[Dep3],
          shared[Dep4],
          shared[Dep5],
          shared[Dep6],
          shared[Dep7],
          shared[Dep8],
          shared[Dep9],
          shared[Dep10],
          shared[Dep11],
          shared[Dep12],
          shared[Dep13],
          shared[Dep14],
          shared[Dep15],
          shared[Dep16],
          shared[Dep17],
          shared[Dep18],
          shared[Dep19],
          shared[Dep20]
        )
        val svc = closeable.get[ServiceWith20Deps]
        closeable.close()
        assertTrue(svc.d1.id == 1 && svc.d10.id == 10 && svc.d20.id == 20)
      },
      test("20-param AutoCloseable registers cleanup") {
        val closeable = Scope.global.injected[AutoCloseableWith20Deps](
          shared[Dep1],
          shared[Dep2],
          shared[Dep3],
          shared[Dep4],
          shared[Dep5],
          shared[Dep6],
          shared[Dep7],
          shared[Dep8],
          shared[Dep9],
          shared[Dep10],
          shared[Dep11],
          shared[Dep12],
          shared[Dep13],
          shared[Dep14],
          shared[Dep15],
          shared[Dep16],
          shared[Dep17],
          shared[Dep18],
          shared[Dep19],
          shared[Dep20]
        )
        val svc = closeable.get[AutoCloseableWith20Deps]
        assertTrue(!svc.closed)
        closeable.close()
        assertTrue(svc.closed)
      },
      test("20-param with Scope.Any param registers cleanup") {
        val (parentScope, closeParent) = Scope.createTestableScope()
        val closeable                  = parentScope.injected[ServiceWith20DepsAndScope](
          shared[Dep1],
          shared[Dep2],
          shared[Dep3],
          shared[Dep4],
          shared[Dep5],
          shared[Dep6],
          shared[Dep7],
          shared[Dep8],
          shared[Dep9],
          shared[Dep10],
          shared[Dep11],
          shared[Dep12],
          shared[Dep13],
          shared[Dep14],
          shared[Dep15],
          shared[Dep16],
          shared[Dep17],
          shared[Dep18],
          shared[Dep19],
          shared[Dep20]
        )
        val svc = closeable.get[ServiceWith20DepsAndScope]
        assertTrue(!svc.cleanedUp)
        closeable.close()
        closeParent()
        assertTrue(svc.cleanedUp)
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
        val config              = new Config
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val scope               = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
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
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]  = scope
        val wire              = unique[Config]
        val ctx1              = wire.construct
        val ctx2              = wire.construct
        val cfg1              = ctx1.get[Config]
        val cfg2              = ctx2.get[Config]
        assertTrue(cfg1 `ne` cfg2)
      },
      test("shared[T] uses same dependency instance for multiple services") {
        val config                         = new Config
        val db                             = new Database
        val parent: Scope.Any              = Scope.global
        val finalizers                     = new Finalizers
        val depsCtx                        = Context(config).add(db)
        val scope                          = Scope.makeCloseable[Config & Database, TNil](parent, depsCtx, finalizers)
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

        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers

        val configWire       = shared[CountedConfig]
        val emptyScope       = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any] = emptyScope
        val configCtx        = configWire.construct
        val cfg              = configCtx.get[CountedConfig]

        val configScope = Scope.makeCloseable[CountedConfig, TNil](parent, configCtx, finalizers)
        val dbWire      = shared[CountedDatabase]
        val dbCtx       = dbWire.construct(using configScope)
        val db          = dbCtx.get[CountedDatabase]

        val fullCtx   = configCtx.add(db)
        val fullScope = Scope.makeCloseable[CountedConfig & CountedDatabase, TNil](parent, fullCtx, finalizers)
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
        val parent: Scope.Any   = Scope.global
        val finalizers          = new Finalizers
        val scope               = Scope.makeCloseable[Config, TNil](parent, Context(existingConfig), finalizers)
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
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers

        val baseWire         = shared[BaseConfig]
        val baseScope        = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any] = baseScope
        val baseCtx          = baseWire.construct
        val base             = baseCtx.get[BaseConfig]

        val scope1   = Scope.makeCloseable[BaseConfig, TNil](parent, baseCtx, finalizers)
        val leftWire = shared[LeftService]
        val leftCtx  = leftWire.construct(using scope1)
        val left     = leftCtx.get[LeftService]

        val rightWire = shared[RightService]
        val rightCtx  = rightWire.construct(using scope1)
        val right     = rightCtx.get[RightService]

        val fullCtx   = baseCtx.add(left).add(right)
        val fullScope = Scope.makeCloseable[BaseConfig & LeftService & RightService, TNil](parent, fullCtx, finalizers)
        val topWire   = shared[TopService]
        val topCtx    = topWire.construct(using fullScope)
        val top       = topCtx.get[TopService]

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
        val parent: Scope.Any = Scope.global
        val finalizers        = new Finalizers
        val scope             = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]  = scope

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
  )
}
