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
        val config = summon[Scope.Has[Config]].get[Config]
        val impl   = new DatabaseImpl(config)
        defer(impl.close())
        impl
      }
    }
  }

  // Test classes for Scope.Has parameter handling
  class ServiceWithScopeHas(val config: Config)(using scope: Scope.Has[Database]) {
    val db: Database = scope.get[Database]
  }

  class ServiceWithMultipleScopeHas(using configScope: Scope.Has[Config], dbScope: Scope.Has[Database]) {
    val config: Config = configScope.get[Config]
    val db: Database   = dbScope.get[Database]
  }

  class ServiceWithMixedParams(val cache: Cache)(using configScope: Scope.Has[Config], anyScope: Scope.Any) {
    val config: Config     = configScope.get[Config]
    var cleanedUp: Boolean = false
    anyScope.defer { cleanedUp = true }
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
      val closeable   = Scope.makeCloseable[Config, Scope.Global](Scope.global, Context(config), new Finalizers)
      given Scope.Any = closeable
      defer { cleaned = true }
      closeable.close()
      assertTrue(cleaned)
    },
    test("$ retrieves scoped value from scope inside .use block") {
      val config    = new Config
      val closeable = Scope.makeCloseable[Config, Scope.Global](Scope.global, Context(config), new Finalizers)
      closeable.use {
        val retrieved = $[Config]
        // retrieved is Config @@ closeable.Tag, use $ operator to check
        val isDebug: Boolean = retrieved.$(_.debug)
        assertTrue(!isDebug)
      }
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
        val wire       = shared[Config]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
        val config     = wire.make(scope)
        assertTrue(config.debug == false)
      },
      test("derives wire for class with one dependency") {
        val wire       = shared[SimpleService]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val configCtx  = Context(new Config)
        val scope      = Scope.makeCloseable[Config, Scope.Global](parent, configCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc != null)
      },
      test("derives wire for class with two dependencies") {
        val wire       = shared[ServiceWith2Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc != null)
      },
      test("handles AutoCloseable") {
        val wire                       = shared[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, Scope.Global](parentScope, Context.empty, finalizers)
        val instance                   = wire.make(scope)
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("uses Wireable when available") {
        val wire       = shared[DatabaseTrait]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val configCtx  = Context(new Config)
        val scope      = Scope.makeCloseable[Config, Scope.Global](parent, configCtx, finalizers)
        val db         = wire.make(scope)
        assertTrue(db.query() == "querying with false")
      }
    ),
    suite("unique[T]")(
      test("derives wire for no-arg class") {
        val wire       = unique[Config]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
        val config     = wire.make(scope)
        assertTrue(config.debug == false)
      },
      test("derives wire for class with dependency") {
        val wire       = unique[SimpleService]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val configCtx  = Context(new Config)
        val scope      = Scope.makeCloseable[Config, Scope.Global](parent, configCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc != null)
      },
      test("handles AutoCloseable") {
        val wire                       = unique[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, Scope.Global](parentScope, Context.empty, finalizers)
        val instance                   = wire.make(scope)
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
        val wireable   = Wireable.from[Config]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
        val config     = wireable.wire.make(scope)
        assertTrue(config.debug == false)
      },
      test("creates Wireable for class with dependency") {
        val wireable   = Wireable.from[SimpleService]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val configCtx  = Context(new Config)
        val scope      = Scope.makeCloseable[Config, Scope.Global](parent, configCtx, finalizers)
        val svc        = wireable.wire.make(scope)
        assertTrue(svc != null)
      },
      test("handles AutoCloseable") {
        val wireable                   = Wireable.from[CloseableConfig]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val scope                      = Scope.makeCloseable[Any, Scope.Global](parentScope, Context.empty, finalizers)
        val instance                   = wireable.wire.make(scope)
        assertTrue(!instance.closed)
        scope.close()
        assertTrue(instance.closed)
      },
      test("manual Wireable for trait with implementation") {
        // With Wireable.Typed, the In type is preserved in the type signature
        val wireable: Wireable.Typed[Config, DatabaseTrait] = summon[Wireable.Typed[Config, DatabaseTrait]]
        val wire: Wire[Config, DatabaseTrait]               = wireable.wire
        val parent                                          = Scope.global
        val finalizers                                      = new Finalizers
        val configCtx                                       = Context(new Config)
        val scope                                           = Scope.makeCloseable[Config, Scope.Global](parent, configCtx, finalizers)
        val db                                              = wire.make(scope)
        assertTrue(db.query() == "querying with false")
      }
    ),
    suite("Scope.Has parameter handling")(
      test("Wireable.from extracts Y from Scope.Has[Y] in using params") {
        // ServiceWithScopeHas(config: Config)(using Scope.Has[Database])
        // In should be Config & Database
        val wireable   = Wireable.from[ServiceWithScopeHas]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wireable.wire.make(scope)
        assertTrue(svc.config != null && svc.db != null)
      },
      test("Wireable.from handles multiple Scope.Has params") {
        // ServiceWithMultipleScopeHas(using Scope.Has[Config], Scope.Has[Database])
        // In should be Config & Database
        val wireable   = Wireable.from[ServiceWithMultipleScopeHas]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wireable.wire.make(scope)
        assertTrue(svc.config != null && svc.db != null)
      },
      test("Wireable.from handles mix of regular params, Scope.Has, and Scope.Any") {
        // ServiceWithMixedParams(cache: Cache)(using Scope.Has[Config], Scope.Any)
        // In should be Cache & Config
        val wireable                   = Wireable.from[ServiceWithMixedParams]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Cache).add(new Config)
        val scope                      = Scope.makeCloseable[Cache & Config, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wireable.wire.make(scope)
        assertTrue(svc.cache != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("Wireable.from extracts Y from Scope.Has[Y] in regular params") {
        // ServiceWithScopeHasInRegularParams(configScope: Scope.Has[Config], cache: Cache)
        // In should be Config & Cache
        val wireable   = Wireable.from[ServiceWithScopeHasInRegularParams]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Cache)
        val scope      = Scope.makeCloseable[Config & Cache, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wireable.wire.make(scope)
        assertTrue(svc.config != null && svc.cache != null)
      },
      test("Wireable.from handles multiple param lists with Scope.Has") {
        // ServiceWithMultipleParamLists(cache: Cache)(dbScope: Scope.Has[Database])(using Scope.Any)
        // In should be Cache & Database
        val wireable                   = Wireable.from[ServiceWithMultipleParamLists]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Cache).add(new Database)
        val scope                      = Scope.makeCloseable[Cache & Database, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wireable.wire.make(scope)
        assertTrue(svc.cache != null && svc.db != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("shared[T] extracts Y from Scope.Has[Y] in using params") {
        val wire       = shared[ServiceWithScopeHas]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc.config != null && svc.db != null)
      },
      test("shared[T] handles multiple Scope.Has params") {
        val wire       = shared[ServiceWithMultipleScopeHas]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc.config != null && svc.db != null)
      },
      test("shared[T] handles mix of regular params, Scope.Has, and Scope.Any") {
        val wire                       = shared[ServiceWithMixedParams]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Cache).add(new Config)
        val scope                      = Scope.makeCloseable[Cache & Config, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wire.make(scope)
        assertTrue(svc.cache != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("unique[T] extracts Y from Scope.Has[Y] in using params") {
        val wire       = unique[ServiceWithScopeHas]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database)
        val scope      = Scope.makeCloseable[Config & Database, Scope.Global](parent, depsCtx, finalizers)
        val svc        = wire.make(scope)
        assertTrue(svc.config != null && svc.db != null)
      }
    ),
    suite("Conversation examples")(
      test("UserProfileService: regular params with Scope.Any") {
        // UserProfileService(database: Database, config: Config)(using Scope.Any)
        // In = Database & Config
        val wireable                   = Wireable.from[UserProfileService]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Database).add(new Config)
        val scope                      = Scope.makeCloseable[Database & Config, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wireable.wire.make(scope)
        assertTrue(svc.database != null && svc.config != null && !svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("MergeSort: multiple Scope.Has params in one list with value param") {
        // MergeSort(socket: Scope.Has[SocketStream], file: Scope.Has[FileReader], output: Scope.Has[FileWriter], mergeType: MergeType)
        // In = SocketStream & FileReader & FileWriter & MergeType
        val wireable   = Wireable.from[MergeSort]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context[SocketStream](new SocketStream1)
          .add[FileReader](new FileReader1)
          .add[FileWriter](new FileWriter1)
          .add(new MergeType("quick"))
        val scope =
          Scope.makeCloseable[SocketStream & FileReader & FileWriter & MergeType, Scope.Global](
            parent,
            depsCtx,
            finalizers
          )
        val svc = wireable.wire.make(scope)
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
          .makeCloseable[SocketStream & FileReader & FileWriter & MergeType, Scope.Global](
            parentScope,
            depsCtx,
            finalizers
          )
        val svc = wireable.wire.make(scope)
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
        val wireable                   = Wireable.from[ComplexService]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Config).add(new Database).add(new Cache)
        val scope                      = Scope.makeCloseable[Config & Database & Cache, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wireable.wire.make(scope)
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
        val scope                      = Scope.makeCloseable[Any, Scope.Global](parentScope, Context.empty, finalizers)
        val svc                        = wireable.wire.make(scope)
        assertTrue(!svc.cleanedUp)
        scope.close()
        assertTrue(svc.cleanedUp)
      },
      test("AutoCloseableWithScopeHas: AutoCloseable with Scope.Has") {
        val wireable                   = Wireable.from[AutoCloseableWithScopeHas]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Config)
        val scope                      = Scope.makeCloseable[Config, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wireable.wire.make(scope)
        assertTrue(svc.config != null && !svc.closed)
        scope.close()
        assertTrue(svc.closed)
      },
      test("shared[MergeSort] works with complex Scope.Has params") {
        val wire       = shared[MergeSort]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context[SocketStream](new SocketStream1)
          .add[FileReader](new FileReader1)
          .add[FileWriter](new FileWriter1)
          .add(new MergeType("heap"))
        val scope =
          Scope.makeCloseable[SocketStream & FileReader & FileWriter & MergeType, Scope.Global](
            parent,
            depsCtx,
            finalizers
          )
        val svc = wire.make(scope)
        assertTrue(svc.mergeType.name == "heap")
      },
      test("shared[ComplexService] works with mixed params") {
        val wire                       = shared[ComplexService]
        val (parentScope, closeParent) = Scope.createTestableScope()
        val finalizers                 = new Finalizers
        val depsCtx                    = Context(new Config).add(new Database).add(new Cache)
        val scope                      = Scope.makeCloseable[Config & Database & Cache, Scope.Global](parentScope, depsCtx, finalizers)
        val svc                        = wire.make(scope)
        assertTrue(svc.config != null && svc.db != null && svc.cache != null)
      },
      test("distinct types in scope are retrieved correctly") {
        class Config(val name: String)
        class Database(val url: String)
        class Cache(val size: Int)

        val config                                 = new Config("prod")
        val db                                     = new Database("jdbc://localhost")
        val cache                                  = new Cache(100)
        val parent                                 = Scope.global
        val finalizers                             = new Finalizers
        val depsCtx                                = Context(config).add(db).add(cache)
        val scope                                  = Scope.makeCloseable[Config & Database & Cache, Scope.Global](parent, depsCtx, finalizers)
        given Scope.Has[Config & Database & Cache] = scope
        val retrievedConfig: Config                = scope.get[Config]
        val retrievedDb: Database                  = scope.get[Database]
        val retrievedCache: Cache                  = scope.get[Cache]
        assertTrue(
          retrievedConfig.name == "prod",
          retrievedDb.url == "jdbc://localhost",
          retrievedCache.size == 100,
          retrievedConfig eq config,
          retrievedDb eq db,
          retrievedCache eq cache
        )
      }
    )
  )
}
