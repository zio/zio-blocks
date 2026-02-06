package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

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
        // Global scope has no services, so get should throw at runtime.
        // We use asInstanceOf to bypass compile-time checks and test runtime behavior.
        val threw = scala.util.Try {
          Scope.global.asInstanceOf[Scope.Has[Config]].get[Config]
        }.isFailure
        assertTrue(threw)
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
      test("close is idempotent") {
        var closeCount = 0
        val parent     = Scope.global
        val config     = Config(true)
        val finalizers = new Finalizers
        finalizers.add(closeCount += 1)
        val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
        closeable.close()
        closeable.close()
        closeable.close()
        assertTrue(closeCount == 1)
      },
      test("get retrieves from context") {
        val parent     = Scope.global
        val config     = Config(true)
        val finalizers = new Finalizers
        val closeable  = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
        val retrieved  = closeable.get[Config]
        closeable.close()
        assertTrue(retrieved == config)
      },
      test("get retrieves from parent context") {
        val parent = Scope.global
        val config = Config(true)
        val db     = Database("jdbc://localhost")
        val f1     = new Finalizers
        val f2     = new Finalizers

        val scope1    = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), f1)
        val scope2    = Scope.makeCloseable[Database, Scope.::[Config, Scope.Global]](scope1, Context(db), f2)
        val retrieved = scope2.get[Config]

        scope2.close()
        scope1.close()
        assertTrue(retrieved == config)
      },
      test("get throws for missing service") {
        val parent     = Scope.global
        val config     = Config(true)
        val finalizers = new Finalizers
        val closeable  = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

        // Try to get Database which is not in the scope - should throw at runtime.
        // We use asInstanceOf to bypass compile-time checks and test runtime behavior.
        val threw = scala.util.Try {
          closeable.asInstanceOf[Scope.Has[Database]].get[Database]
        }.isFailure
        closeable.close()
        assertTrue(threw)
      },
      test("defer registers finalizer") {
        var cleaned    = false
        val parent     = Scope.global
        val config     = Config(true)
        val finalizers = new Finalizers
        val closeable  = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
        closeable.defer { cleaned = true }
        assertTrue(!cleaned)
        closeable.close()
        assertTrue(cleaned)
      },
      test("defer not registered after close") {
        var cleaned    = false
        val parent     = Scope.global
        val config     = Config(true)
        val finalizers = new Finalizers
        val closeable  = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
        closeable.close()
        closeable.defer { cleaned = true }
        assertTrue(!cleaned)
      }
    ),
    suite("nested scopes")(
      test("get from grandparent") {
        val parent = Scope.global
        val config = Config(true)
        val db     = Database("jdbc://localhost")
        val cache  = Cache(100)
        val f1     = new Finalizers
        val f2     = new Finalizers
        val f3     = new Finalizers

        val scope1 = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), f1)
        val scope2 = Scope.makeCloseable[Database, Scope.::[Config, Scope.Global]](scope1, Context(db), f2)
        val scope3 =
          Scope.makeCloseable[Cache, Scope.::[Database, Scope.::[Config, Scope.Global]]](scope2, Context(cache), f3)

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
        val ev = implicitly[InStack[Config, Scope.::[Config, Scope]]]
        assertTrue(ev != null)
      },
      test("evidence for tail") {
        val ev = implicitly[InStack[Config, Scope.::[Database, Scope.::[Config, Scope]]]]
        assertTrue(ev != null)
      }
    )
  )
}
