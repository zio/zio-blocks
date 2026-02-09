package zio.blocks.scope

import zio.{ZIO, Scope => _}
import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

object ResourceSpec extends ZIOSpecDefault {

  case class Config(url: String)

  class Database(@scala.annotation.unused config: Config) extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  def spec = suite("Resource")(
    test("Resource(value) creates a resource for non-AutoCloseable") {
      val resource       = Resource(Config("jdbc://localhost"))
      val (scope, close) = Scope.createTestableScope()
      val config         = resource.make(scope)
      close()
      assertTrue(config.url == "jdbc://localhost")
    },
    test("Resource(value) auto-registers close for AutoCloseable") {
      val db             = new Database(Config("url"))
      val resource       = Resource(db)
      val (scope, close) = Scope.createTestableScope()
      val result         = resource.make(scope)
      assertTrue(!result.closed)
      close()
      assertTrue(result.closed)
    },
    test("Resource.shared creates from function") {
      val resource       = Resource.shared[Config](_ => Config("test-url"))
      val (scope, close) = Scope.createTestableScope()
      val config         = resource.make(scope)
      close()
      assertTrue(config.url == "test-url")
    },
    test("Resource.unique creates fresh instances") {
      var counter  = 0
      val resource = Resource.unique[Int] { _ =>
        counter += 1
        counter
      }
      val (scope, close) = Scope.createTestableScope()
      val a              = resource.make(scope)
      val b              = resource.make(scope)
      close()
      assertTrue(a == 1, b == 2)
    },
    test("Resource can register finalizers") {
      val resource = Resource.shared[Database] { scope =>
        val db = new Database(Config("url"))
        scope.defer(db.close())
        db
      }
      val (scope, close) = Scope.createTestableScope()
      val db             = resource.make(scope)
      val beforeClose    = db.closed
      close()
      val afterClose = db.closed
      assertTrue(!beforeClose, afterClose)
    },
    test("Resource.shared memoizes across multiple makes") {
      val counter  = new AtomicInteger(0)
      val resource = Resource.shared[Int] { _ =>
        counter.incrementAndGet()
      }
      val (scope, close) = Scope.createTestableScope()
      val a              = resource.make(scope)
      val b              = resource.make(scope)
      close()
      assertTrue(a == 1, b == 1, counter.get() == 1)
    },
    test("Resource.shared runs finalizers only when all references released") {
      var closeCalls = 0
      val resource   = Resource.shared[String] { finalizer =>
        finalizer.defer(closeCalls += 1)
        "shared-value"
      }

      val (scope1, close1) = Scope.createTestableScope()
      val (scope2, close2) = Scope.createTestableScope()

      val v1 = resource.make(scope1)
      val v2 = resource.make(scope2)

      close1()
      val afterFirstClose = closeCalls

      close2()
      val afterSecondClose = closeCalls

      assertTrue(
        v1 == "shared-value",
        v2 == "shared-value",
        afterFirstClose == 0,
        afterSecondClose == 1
      )
    },
    test("Resource.shared runs finalizers in LIFO order") {
      val order    = ListBuffer[Int]()
      val resource = Resource.shared[String] { finalizer =>
        finalizer.defer(order += 1)
        finalizer.defer(order += 2)
        finalizer.defer(order += 3)
        "value"
      }
      val (scope, close) = Scope.createTestableScope()
      resource.make(scope)
      close()
      assertTrue(order.toList == List(3, 2, 1))
    },
    test("Resource.shared collects suppressed exceptions from finalizers") {
      val resource = Resource.shared[String] { finalizer =>
        finalizer.defer(throw new RuntimeException("error1"))
        finalizer.defer(throw new RuntimeException("error2"))
        finalizer.defer(throw new RuntimeException("error3"))
        "value"
      }
      val (scope, close) = Scope.createTestableScope()
      resource.make(scope)

      val caught =
        try {
          close()
          None
        } catch {
          case e: RuntimeException => Some(e)
        }

      assertTrue(
        caught.isDefined,
        caught.get.getMessage == "error3",
        caught.get.getSuppressed.length == 2,
        caught.get.getSuppressed.apply(0).getMessage == "error2",
        caught.get.getSuppressed.apply(1).getMessage == "error1"
      )
    },
    test("Resource.shared is thread-safe under concurrent makes") {
      for {
        counter      <- ZIO.succeed(new AtomicInteger(0))
        closeCounter <- ZIO.succeed(new AtomicInteger(0))
        resource      = Resource.shared[Int] { finalizer =>
                     finalizer.defer { closeCounter.incrementAndGet(); () }
                     counter.incrementAndGet()
                   }
        (scope, close) = Scope.createTestableScope()
        results       <- ZIO.foreachPar(1 to 20) { _ =>
                     ZIO.attempt(resource.make(scope))
                   }
        _ <- ZIO.succeed(close())
      } yield assertTrue(
        results.forall(_ == 1),
        counter.get() == 1,
        closeCounter.get() == 1
      )
    },
    test("Resource.shared throws if allocated after destroyed") {
      val resource         = Resource.shared[String](_ => "value")
      val (scope1, close1) = Scope.createTestableScope()
      resource.make(scope1)
      close1()

      val (scope2, _) = Scope.createTestableScope()
      val caught      =
        try {
          resource.make(scope2)
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
      assertTrue(
        caught.isDefined,
        caught.get.getMessage.contains("destroyed")
      )
    },
    test("Resource.acquireRelease registers release as finalizer") {
      var acquired = false
      var released = false
      val resource = Resource.acquireRelease {
        acquired = true
        "value"
      } { _ =>
        released = true
      }
      val (scope, close) = Scope.createTestableScope()
      val value          = resource.make(scope)
      assertTrue(acquired, !released, value == "value")
      close()
      assertTrue(released)
    },
    test("Resource.fromAutoCloseable registers close as finalizer") {
      class MyCloseable extends AutoCloseable {
        var closed        = false
        def close(): Unit = closed = true
      }
      val resource       = Resource.fromAutoCloseable(new MyCloseable)
      val (scope, close) = Scope.createTestableScope()
      val closeable      = resource.make(scope)
      assertTrue(!closeable.closed)
      close()
      assertTrue(closeable.closed)
    },
    suite("Resource.from with overrides")(
      test("creates standalone Resource when all deps covered") {
        case class Cfg(debug: Boolean)
        class SimpleService(@annotation.unused config: Cfg)

        val configWire     = Wire(Cfg(true))
        val resource       = Resource.from[SimpleService](configWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[SimpleService])
      },
      test("with AutoCloseable registers finalizer") {
        case class Cfg(debug: Boolean)
        class CloseableService(@annotation.unused config: Cfg) extends AutoCloseable {
          var closed        = false
          def close(): Unit = closed = true
        }

        val configWire     = Wire(Cfg(true))
        val resource       = Resource.from[CloseableService](configWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        assertTrue(!service.closed)
        close()
        assertTrue(service.closed)
      },
      test("with multiple dependencies") {
        case class Cfg(debug: Boolean)
        case class Port(value: Int)
        class Db extends AutoCloseable {
          var closed        = false
          def close(): Unit = closed = true
        }
        class MultiDepService(
          @annotation.unused config: Cfg,
          @annotation.unused db: Db,
          @annotation.unused port: Port
        )

        val configWire     = Wire(Cfg(false))
        val dbWire         = shared[Db]
        val portWire       = Wire(Port(8080))
        val resource       = Resource.from[MultiDepService](configWire, dbWire, portWire)
        val (scope, close) = Scope.createTestableScope()
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[MultiDepService])
      }
    ),
    suite("map")(
      test("transforms the resource value") {
        val portResource = Resource(8080)
        val urlResource  = portResource.map(port => s"http://localhost:$port")

        val (scope, close) = Scope.createTestableScope()
        val url            = urlResource.make(scope)
        close()

        assertTrue(url == "http://localhost:8080")
      },
      test("preserves finalizers from original resource") {
        var closed = false

        class Conn extends AutoCloseable {
          def close(): Unit = closed = true
        }

        val connResource = Resource.fromAutoCloseable(new Conn)
        val idResource   = connResource.map(_ => "connection-id")

        val (scope, close) = Scope.createTestableScope()
        val id             = idResource.make(scope)

        assertTrue(id == "connection-id", !closed)
        close()
        assertTrue(closed)
      }
    ),
    suite("flatMap")(
      test("sequences two resources") {
        val configResource = Resource(Config("jdbc://localhost"))
        val dbResource     = configResource.flatMap { config =>
          Resource.fromAutoCloseable(new Database(config))
        }

        val (scope, close) = Scope.createTestableScope()
        val db             = dbResource.make(scope)

        assertTrue(!db.closed)
        close()
        assertTrue(db.closed)
      },
      test("runs finalizers in LIFO order (inner before outer)") {
        val order = ListBuffer[String]()

        val outer    = Resource.acquireRelease("outer")(_ => order += "outer-released")
        val combined = outer.flatMap { _ =>
          Resource.acquireRelease("inner")(_ => order += "inner-released")
        }

        val (scope, close) = Scope.createTestableScope()
        combined.make(scope)
        close()

        assertTrue(order.toList == List("inner-released", "outer-released"))
      }
    ),
    suite("zip")(
      test("combines two resources into a tuple") {
        val dbResource    = Resource(new Database(Config("url")))
        val cacheResource = Resource(42)
        val combined      = dbResource.zip(cacheResource)

        val (scope, close) = Scope.createTestableScope()
        val (db, cache)    = combined.make(scope)
        close()

        assertTrue(db.isInstanceOf[Database], cache == 42)
      },
      test("runs both finalizers") {
        var dbClosed    = false
        var cacheClosed = false

        class Db extends AutoCloseable {
          def close(): Unit = dbClosed = true
        }
        class Cache extends AutoCloseable {
          def close(): Unit = cacheClosed = true
        }

        val dbResource    = Resource.fromAutoCloseable(new Db)
        val cacheResource = Resource.fromAutoCloseable(new Cache)
        val combined      = dbResource.zip(cacheResource)

        val (scope, close) = Scope.createTestableScope()
        combined.make(scope)

        assertTrue(!dbClosed, !cacheClosed)
        close()
        assertTrue(dbClosed, cacheClosed)
      },
      test("runs finalizers in LIFO order (second before first)") {
        val order = ListBuffer[String]()

        val first    = Resource.acquireRelease("first")(_ => order += "first-released")
        val second   = Resource.acquireRelease("second")(_ => order += "second-released")
        val combined = first.zip(second)

        val (scope, close) = Scope.createTestableScope()
        combined.make(scope)
        close()

        assertTrue(order.toList == List("second-released", "first-released"))
      }
    ),
    suite("contextual")(
      test("wraps value in a Context") {
        case class AppConfig(name: String)
        val resource    = Resource(AppConfig("myapp"))
        val ctxResource = resource.contextual[AppConfig]

        val (scope, close) = Scope.createTestableScope()
        val ctx            = ctxResource.make(scope)
        close()

        assertTrue(ctx.get[AppConfig].name == "myapp")
      },
      test("preserves finalizers from original resource") {
        var closed = false

        class Service extends AutoCloseable {
          def close(): Unit = closed = true
        }

        val resource    = Resource.fromAutoCloseable(new Service)
        val ctxResource = resource.contextual[Service]

        val (scope, close) = Scope.createTestableScope()
        val ctx            = ctxResource.make(scope)

        assertTrue(ctx.get[Service].isInstanceOf[Service], !closed)
        close()
        assertTrue(closed)
      },
      test("works with shared resources") {
        case class SharedService(id: Int)
        val counter  = new AtomicInteger(0)
        val resource = Resource.shared[SharedService] { _ =>
          SharedService(counter.incrementAndGet())
        }
        val ctxResource = resource.contextual[SharedService]

        val (scope1, close1) = Scope.createTestableScope()
        val (scope2, close2) = Scope.createTestableScope()

        val ctx1 = ctxResource.make(scope1)
        val ctx2 = ctxResource.make(scope2)

        close1()
        close2()

        assertTrue(
          ctx1.get[SharedService].id == 1,
          ctx2.get[SharedService].id == 1,
          counter.get() == 1
        )
      }
    ),
    suite("++")(
      test("combines two resource Contexts") {
        case class Database(url: String)
        case class Cache(size: Int)

        val dbResource    = Resource(Database("jdbc://localhost")).contextual[Database]
        val cacheResource = Resource(Cache(100)).contextual[Cache]
        val combined      = dbResource ++ cacheResource

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(
          ctx.get[Database].url == "jdbc://localhost",
          ctx.get[Cache].size == 100
        )
      },
      test("preserves finalizers from both resources") {
        var connClosed  = false
        var cacheClosed = false

        class Conn extends AutoCloseable {
          def close(): Unit = connClosed = true
        }
        class CacheService extends AutoCloseable {
          def close(): Unit = cacheClosed = true
        }

        val connResource  = Resource.fromAutoCloseable(new Conn).contextual[Conn]
        val cacheResource = Resource.fromAutoCloseable(new CacheService).contextual[CacheService]
        val combined      = connResource ++ cacheResource

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)

        assertTrue(
          ctx.get[Conn].isInstanceOf[Conn],
          ctx.get[CacheService].isInstanceOf[CacheService],
          !connClosed,
          !cacheClosed
        )
        close()
        assertTrue(connClosed, cacheClosed)
      },
      test("right resource Context takes precedence for duplicate types") {
        case class Config(value: String)

        val resource1 = Resource(Config("from-first")).contextual[Config]
        val resource2 = Resource(Config("from-second")).contextual[Config]
        val combined  = resource1 ++ resource2

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(ctx.get[Config].value == "from-second")
      },
      test("chains multiple ++ operations") {
        case class A(a: Int)
        case class B(b: String)
        case class C(c: Boolean)

        val resourceA = Resource(A(1)).contextual[A]
        val resourceB = Resource(B("two")).contextual[B]
        val resourceC = Resource(C(true)).contextual[C]
        val combined  = resourceA ++ resourceB ++ resourceC

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(
          ctx.get[A].a == 1,
          ctx.get[B].b == "two",
          ctx.get[C].c
        )
      },
      test("works with shared resources") {
        case class SharedDb(id: Int)
        case class Config(env: String)

        val counter    = new AtomicInteger(0)
        val dbResource = Resource
          .shared[SharedDb] { _ =>
            SharedDb(counter.incrementAndGet())
          }
          .contextual[SharedDb]
        val configResource = Resource(Config("prod")).contextual[Config]
        val combined       = dbResource ++ configResource

        val (scope1, close1) = Scope.createTestableScope()
        val (scope2, close2) = Scope.createTestableScope()

        val ctx1 = combined.make(scope1)
        val ctx2 = combined.make(scope2)

        close1()
        close2()

        assertTrue(
          ctx1.get[SharedDb].id == 1,
          ctx2.get[SharedDb].id == 1,
          ctx1.get[Config].env == "prod",
          ctx2.get[Config].env == "prod",
          counter.get() == 1
        )
      }
    ),
    suite(":+")(
      test("appends a resource value to resource Context") {
        case class Database(url: String)
        case class Cache(size: Int)

        val dbResource    = Resource(Database("jdbc://localhost")).contextual[Database]
        val cacheResource = Resource(Cache(100))
        val combined      = dbResource :+ cacheResource

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(
          ctx.get[Database].url == "jdbc://localhost",
          ctx.get[Cache].size == 100
        )
      },
      test("preserves finalizers from both resources") {
        var connClosed   = false
        var loggerClosed = false

        class Conn extends AutoCloseable {
          def close(): Unit = connClosed = true
        }
        class Logger(val name: String) extends AutoCloseable {
          def close(): Unit = loggerClosed = true
        }

        val connResource   = Resource.fromAutoCloseable(new Conn).contextual[Conn]
        val loggerResource = Resource.fromAutoCloseable(new Logger("app"))
        val combined       = connResource :+ loggerResource

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)

        assertTrue(
          ctx.get[Conn].isInstanceOf[Conn],
          ctx.get[Logger].name == "app",
          !connClosed,
          !loggerClosed
        )
        close()
        assertTrue(connClosed, loggerClosed)
      },
      test("later resource value takes precedence for duplicate types") {
        case class Config(value: String)

        val resource1 = Resource(Config("first")).contextual[Config]
        val resource2 = Resource(Config("second"))
        val combined  = resource1 :+ resource2

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(ctx.get[Config].value == "second")
      },
      test("chains multiple :+ operations") {
        case class A(a: Int)
        case class B(b: String)
        case class C(c: Boolean)

        val resourceA = Resource(A(1)).contextual[A]
        val resourceB = Resource(B("two"))
        val resourceC = Resource(C(true))
        val combined  = resourceA :+ resourceB :+ resourceC

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(
          ctx.get[A].a == 1,
          ctx.get[B].b == "two",
          ctx.get[C].c
        )
      },
      test("works with shared resources") {
        case class SharedDb(id: Int)
        case class Config(env: String)

        val counter    = new AtomicInteger(0)
        val dbResource = Resource
          .shared[SharedDb] { _ =>
            SharedDb(counter.incrementAndGet())
          }
          .contextual[SharedDb]
        val configResource = Resource(Config("prod"))
        val combined       = dbResource :+ configResource

        val (scope1, close1) = Scope.createTestableScope()
        val (scope2, close2) = Scope.createTestableScope()

        val ctx1 = combined.make(scope1)
        val ctx2 = combined.make(scope2)

        close1()
        close2()

        assertTrue(
          ctx1.get[SharedDb].id == 1,
          ctx2.get[SharedDb].id == 1,
          ctx1.get[Config].env == "prod",
          ctx2.get[Config].env == "prod",
          counter.get() == 1
        )
      },
      test("can be combined with ++") {
        case class A(a: Int)
        case class B(b: String)
        case class C(c: Boolean)

        val resourceA = Resource(A(1)).contextual[A]
        val resourceB = Resource(B("two")).contextual[B]
        val resourceC = Resource(C(true))
        val combined  = (resourceA ++ resourceB) :+ resourceC

        val (scope, close) = Scope.createTestableScope()
        val ctx            = combined.make(scope)
        close()

        assertTrue(
          ctx.get[A].a == 1,
          ctx.get[B].b == "two",
          ctx.get[C].c
        )
      }
    )
  )
}
