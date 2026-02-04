package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers
import scala.collection.mutable

object ScopeSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  case class Database(url: String)
  case class Cache(size: Int)

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  def spec = suite("Scope")(
    suite("global")(
      test("global scope exists") {
        assertTrue(Scope.global != null)
      },
      test("global scope get throws for missing type") {
        val result = try {
          Scope.global.asInstanceOf[Scope[Context[Config] :: TNil]].get[Config]
          false
        } catch {
          case _: IllegalStateException => true
        }
        assertTrue(result)
      },
      test("global scope defer works") {
        var ran          = false
        val s: Scope.Any = Scope.global
        s.defer { ran = true }
        assertTrue(!ran)
      },
      test("testable global scope close runs finalizers") {
        val (scope, close) = Scope.createTestableScope()
        var ran            = false
        scope.defer { ran = true }
        close()
        assertTrue(ran)
      },
      test("testable global scope close propagates error") {
        val (scope, close) = Scope.createTestableScope()
        scope.defer(throw new RuntimeException("test error"))
        val result = try {
          close()
          false
        } catch {
          case e: RuntimeException => e.getMessage == "test error"
        }
        assertTrue(result)
      },
      test("closeGlobal method exists") {
        Scope.createTestableScope()._2()
        assertTrue(true)
      }
    ),
    suite("Closeable")(
      test("run executes block and closes scope") {
        var blockRan          = false
        var cleaned           = false
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        finalizers.add { cleaned = true }
        val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        closeable.run { ctx =>
          blockRan = true
          assertTrue(ctx.get[Config] == config)
        }
        assertTrue(blockRan, cleaned)
      },
      test("close is idempotent") {
        var closeCount        = 0
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        finalizers.add(closeCount += 1)
        val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        closeable.close()
        closeable.close()
        closeable.close()
        assertTrue(closeCount == 1)
      },
      test("get retrieves from context") {
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        val retrieved         = closeable.get[Config]
        closeable.close()
        assertTrue(retrieved == config)
      },
      test("get retrieves from parent context") {
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val db                = Database("jdbc://localhost")
        val f1                = new Finalizers
        val f2                = new Finalizers

        val scope1    = Scope.makeCloseable[Config, TNil](parent, Context(config), f1)
        val scope2    = Scope.makeCloseable[Database, Context[Config] :: TNil](scope1, Context(db), f2)
        val retrieved = scope2.get[Config]

        scope2.close()
        scope1.close()
        assertTrue(retrieved == config)
      },
      test("get throws for missing service") {
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

        val result = try {
          closeable.asInstanceOf[Scope[Context[Database] :: TNil]].get[Database]
          false
        } catch {
          case _: NoSuchElementException => true
        }
        closeable.close()
        assertTrue(result)
      },
      test("defer registers finalizer") {
        var cleaned           = false
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        closeable.defer { cleaned = true }
        assertTrue(!cleaned)
        closeable.close()
        assertTrue(cleaned)
      },
      test("defer not registered after close") {
        var cleaned           = false
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        closeable.close()
        closeable.defer { cleaned = true }
        assertTrue(!cleaned)
      }
    ),
    suite("Finalizers")(
      test("run in LIFO order") {
        val order             = mutable.Buffer[Int]()
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        finalizers.add(order += 1)
        finalizers.add(order += 2)
        finalizers.add(order += 3)
        val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        closeable.close()
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("run even on exception") {
        var cleaned           = false
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        finalizers.add { cleaned = true }
        val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
        try {
          closeable.run(_ => throw new RuntimeException("boom"))
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(cleaned)
      },
      test("exception in finalizer is propagated") {
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val finalizers        = new Finalizers
        finalizers.add(throw new RuntimeException("finalizer boom"))
        val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

        val result = try {
          closeable.close()
          false
        } catch {
          case e: RuntimeException => e.getMessage == "finalizer boom"
        }
        assertTrue(result)
      },
      test("isClosed returns correct state") {
        val finalizers = new Finalizers
        assertTrue(!finalizers.isClosed)
        finalizers.runAll()
        assertTrue(finalizers.isClosed)
      },
      test("size returns correct count") {
        val finalizers = new Finalizers
        assertTrue(finalizers.size == 0)
        finalizers.add(())
        assertTrue(finalizers.size == 1)
        finalizers.add(())
        assertTrue(finalizers.size == 2)
      },
      test("add after close is ignored") {
        val finalizers = new Finalizers
        finalizers.runAll()
        finalizers.add(())
        assertTrue(finalizers.size == 0)
      },
      test("runAll is idempotent") {
        val finalizers = new Finalizers
        var count      = 0
        finalizers.add(count += 1)
        finalizers.runAll()
        finalizers.runAll()
        assertTrue(count == 1)
      }
    ),
    suite("nested scopes")(
      test("get from grandparent") {
        val parent: Scope.Any = Scope.global
        val config            = Config(true)
        val db                = Database("jdbc://localhost")
        val cache             = Cache(100)
        val f1                = new Finalizers
        val f2                = new Finalizers
        val f3                = new Finalizers

        val scope1 = Scope.makeCloseable[Config, TNil](parent, Context(config), f1)
        val scope2 = Scope.makeCloseable[Database, Context[Config] :: TNil](scope1, Context(db), f2)
        val scope3 =
          Scope.makeCloseable[Cache, Context[Database] :: Context[Config] :: TNil](scope2, Context(cache), f3)

        val retrievedConfig = scope3.get[Config]
        val retrievedDb     = scope3.get[Database]
        val retrievedCache  = scope3.get[Cache]

        scope3.close()
        scope2.close()
        scope1.close()

        assertTrue(retrievedConfig == config, retrievedDb == db, retrievedCache == cache)
      }
    ),
    suite("InStack")(
      test("evidence for head") {
        val ev = implicitly[InStack[Config, Context[Config] :: TNil]]
        assertTrue(ev != null)
      },
      test("evidence for tail") {
        val ev = implicitly[InStack[Config, Context[Database] :: Context[Config] :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Wire")(
      test("Wire.value creates shared wire") {
        val wire = Wire.value(Config(true))
        assertTrue(wire.isInstanceOf[Wire.Shared[_, _]])
      },
      test("Wire.value construction works") {
        val wire                  = Wire.value(Config(true))
        val parent: Scope.Any     = Scope.global
        val finalizers            = new Finalizers
        val scope: Scope.Has[Any] = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]      = scope
        val ctx                   = wire.construct
        assertTrue(ctx.get[Config].debug)
      },
      test("Wire.Shared constructs context") {
        val wire: Wire.Shared[Any, Config] = Wire.Shared[Any, Config] {
          Context(Config(debug = true))
        }
        val parent: Scope.Any     = Scope.global
        val finalizers            = new Finalizers
        val scope: Scope.Has[Any] = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]      = scope
        val ctx                   = wire.construct
        assertTrue(ctx.get[Config].debug)
      },
      test("Wire.Unique constructs context") {
        val wire: Wire.Unique[Any, Config] = Wire.Unique[Any, Config] {
          Context(Config(debug = false))
        }
        val parent: Scope.Any     = Scope.global
        val finalizers            = new Finalizers
        val scope: Scope.Has[Any] = Scope.makeCloseable[Any, TNil](parent, Context.empty, finalizers)
        given Scope.Has[Any]      = scope
        val ctx                   = wire.construct
        assertTrue(!ctx.get[Config].debug)
      },
      test("Wire.isShared and isUnique") {
        val sharedWire = Wire.value(Config(true))
        val uniqueWire = sharedWire.unique
        assertTrue(sharedWire.isShared, !sharedWire.isUnique)
        assertTrue(!uniqueWire.isShared, uniqueWire.isUnique)
      },
      test("Wire.shared and unique conversions") {
        val sharedWire   = Wire.value(Config(true))
        val uniqueWire   = sharedWire.unique
        val backToShared = uniqueWire.shared
        assertTrue(sharedWire.isShared, uniqueWire.isUnique, backToShared.isShared)
      }
    ),
    suite("Wireable")(
      test("trait exists") {
        val wireable = new Wireable[Config] {
          type In = Any
          def wire: Wire[Any, Config] = Wire.value(Config(true))
        }
        assertTrue(wireable.wire != null)
      }
    )
  )
}
