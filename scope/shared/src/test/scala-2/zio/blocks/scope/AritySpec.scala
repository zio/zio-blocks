package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object AritySpec extends ZIOSpecDefault {

  class Config {
    val debug: Boolean = false
  }

  class Database {
    val url: String = "jdbc://localhost"
  }

  class Cache {
    val size: Int = 100
  }

  class Logger {
    val prefix: String = "[LOG]"
  }

  class Metrics {
    val enabled: Boolean = true
  }

  class Tracer {
    val sampleRate: Double = 0.1
  }

  class ServiceWith3Deps(val config: Config, val db: Database, val cache: Cache)

  class ServiceWith4Deps(val config: Config, val db: Database, val cache: Cache, val logger: Logger)

  class ServiceWith5Deps(
    val config: Config,
    val db: Database,
    val cache: Cache,
    val logger: Logger,
    val metrics: Metrics
  )

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
  )(implicit scope: Scope.Any) {
    var cleanedUp: Boolean = false
    defer { cleanedUp = true }
  }

  def spec = suite("Arbitrary arity support (Scala 2)")(
    suite("3-6 parameter arity")(
      test("shared[T] works with 3 constructor parameters") {
        val wire       = shared[ServiceWith3Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database).add(new Cache)
        val scope      = Scope.makeCloseable[Config with Database with Cache, Scope.Global](parent, depsCtx, finalizers)
        val ctx        = wire.construct(scope)
        val svc        = ctx.get[ServiceWith3Deps]
        assertTrue(svc != null && svc.config != null && svc.db != null && svc.cache != null)
      },
      test("shared[T] works with 4 constructor parameters") {
        val wire       = shared[ServiceWith4Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database).add(new Cache).add(new Logger)
        val scope      =
          Scope.makeCloseable[Config with Database with Cache with Logger, Scope.Global](parent, depsCtx, finalizers)
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith4Deps]
        assertTrue(svc != null && svc.logger.prefix == "[LOG]")
      },
      test("shared[T] works with 5 constructor parameters") {
        val wire       = shared[ServiceWith5Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope      =
          Scope
            .makeCloseable[Config with Database with Cache with Logger with Metrics, Scope.Global](
              parent,
              depsCtx,
              finalizers
            )
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith5Deps]
        assertTrue(svc != null && svc.metrics.enabled)
      },
      test("shared[T] works with 6 constructor parameters") {
        val wire       = shared[ServiceWith6Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    =
          Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics).add(new Tracer)
        val scope =
          Scope.makeCloseable[Config with Database with Cache with Logger with Metrics with Tracer, Scope.Global](
            parent,
            depsCtx,
            finalizers
          )
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith6Deps]
        assertTrue(svc != null && svc.tracer.sampleRate == 0.1)
      },
      test("unique[T] works with 5 constructor parameters") {
        val wire       = unique[ServiceWith5Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Config).add(new Database).add(new Cache).add(new Logger).add(new Metrics)
        val scope      =
          Scope
            .makeCloseable[Config with Database with Cache with Logger with Metrics, Scope.Global](
              parent,
              depsCtx,
              finalizers
            )

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
    ),
    suite("20-parameter arity")(
      test("shared[T] works with 20 constructor parameters") {
        val wire       = shared[ServiceWith20Deps]
        val parent     = Scope.global
        val finalizers = new Finalizers
        val depsCtx    = Context(new Dep1)
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
        val scope = Scope.makeCloseable[
          Dep1
            with Dep2
            with Dep3
            with Dep4
            with Dep5
            with Dep6
            with Dep7
            with Dep8
            with Dep9
            with Dep10
            with Dep11
            with Dep12
            with Dep13
            with Dep14
            with Dep15
            with Dep16
            with Dep17
            with Dep18
            with Dep19
            with Dep20,
          Scope.Global
        ](parent, depsCtx, finalizers)
        val ctx = wire.construct(scope)
        val svc = ctx.get[ServiceWith20Deps]
        assertTrue(
          svc != null &&
            svc.d1.id == 1 &&
            svc.d10.id == 10 &&
            svc.d20.id == 20
        )
      },
      test("injected[T] works with 20 wires") {
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
        assertTrue(svc != null && svc.d1.id == 1 && svc.d20.id == 20)
      },
      test("20-param AutoCloseable is properly closed") {
        val (parentScope, _) = Scope.createTestableScope()
        val closeable        = parentScope.injected[AutoCloseableWith20Deps](
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
    )
  )
}
