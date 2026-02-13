package zio.blocks.scope

import zio.{Scope => _}
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
      val resource = Resource(Config("jdbc://localhost"))
      val url      = Scope.global.scoped { scope =>
        resource.make(scope).url
      }
      assertTrue(url == "jdbc://localhost")
    },
    test("Resource(value) auto-registers close for AutoCloseable") {
      val db          = new Database(Config("url"))
      val resource    = Resource(db)
      val beforeClose = Scope.global.scoped { scope =>
        resource.make(scope)
        !db.closed
      }
      assertTrue(beforeClose, db.closed)
    },
    test("Resource.shared creates from function") {
      val resource = Resource.shared[Config](_ => Config("test-url"))
      val url      = Scope.global.scoped { scope =>
        resource.make(scope).url
      }
      assertTrue(url == "test-url")
    },
    test("Resource.unique creates fresh instances") {
      var counter  = 0
      val resource = Resource.unique[Int] { _ =>
        counter += 1
        counter
      }
      val (a, b) = Scope.global.scoped { scope =>
        val a = resource.make(scope)
        val b = resource.make(scope)
        (a, b)
      }
      assertTrue(a == 1, b == 2)
    },
    test("Resource can register finalizers") {
      var finalizerRan = false
      val resource     = Resource.shared[Database] { scope =>
        import scope._
        val db = new Database(Config("url"))
        defer { db.close(); finalizerRan = true }
        db
      }
      val beforeClose = Scope.global.scoped { scope =>
        resource.make(scope)
        !finalizerRan
      }
      assertTrue(beforeClose, finalizerRan)
    },
    test("Resource.shared memoizes across multiple makes") {
      val counter  = new AtomicInteger(0)
      val resource = Resource.shared[Int] { _ =>
        counter.incrementAndGet()
      }
      val (a, b) = Scope.global.scoped { scope =>
        val a = resource.make(scope)
        val b = resource.make(scope)
        (a, b)
      }
      assertTrue(a == 1, b == 1, counter.get() == 1)
    },
    test("Resource.shared runs finalizers only when all references released") {
      var closeCalls = 0
      val resource   = Resource.shared[String] { finalizer =>
        finalizer.defer(closeCalls += 1)
        "shared-value"
      }
      var v1              = ""
      var v2              = ""
      var afterFirstClose = -1
      Scope.global.scoped { scope1 =>
        v1 = resource.make(scope1)
        scope1.scoped { scope2 =>
          v2 = resource.make(scope2)
        }
        afterFirstClose = closeCalls
      }
      assertTrue(
        v1 == "shared-value",
        v2 == "shared-value",
        afterFirstClose == 0,
        closeCalls == 1
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
      Scope.global.scoped { scope =>
        resource.make(scope)
      }
      assertTrue(order.toList == List(3, 2, 1))
    },
    test("Resource.shared collects suppressed exceptions from finalizers") {
      val resource = Resource.shared[String] { finalizer =>
        finalizer.defer(throw new RuntimeException("error1"))
        finalizer.defer(throw new RuntimeException("error2"))
        finalizer.defer(throw new RuntimeException("error3"))
        "value"
      }

      val caught =
        try {
          Scope.global.scoped { scope =>
            resource.make(scope)
          }
          Option.empty[RuntimeException]
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
    // NOTE: "Resource.shared is thread-safe under concurrent makes" is in
    // scope/jvm/src/test/.../ResourceConcurrencySpec.scala (uses JVM-only classes)
    test("Resource.shared throws if allocated after destroyed") {
      val resource = Resource.shared[String](_ => "value")
      Scope.global.scoped { scope1 =>
        resource.make(scope1)
      }
      val caught: Option[IllegalStateException] =
        try {
          Scope.global.scoped { scope2 =>
            resource.make(scope2)
          }
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
      val (acq, rel, value) = Scope.global.scoped { scope =>
        val value = resource.make(scope)
        (acquired, released, value)
      }
      assertTrue(acq, !rel, value == "value", released)
    },
    test("Resource.fromAutoCloseable registers close as finalizer") {
      class MyCloseable extends AutoCloseable {
        var closed        = false
        def close(): Unit = closed = true
      }
      val closeable   = new MyCloseable
      val resource    = Resource.fromAutoCloseable(closeable)
      val beforeClose = Scope.global.scoped { scope =>
        resource.make(scope)
        !closeable.closed
      }
      assertTrue(beforeClose, closeable.closed)
    },
    suite("Resource.from with overrides")(
      test("creates standalone Resource when all deps covered") {
        case class Cfg(debug: Boolean)
        class SimpleService(@annotation.unused config: Cfg)

        val configWire      = Wire(Cfg(true))
        val resource        = Resource.from[SimpleService](configWire)
        val isSimpleService = Scope.global.scoped { scope =>
          resource.make(scope).isInstanceOf[SimpleService]
        }
        assertTrue(isSimpleService)
      },
      test("with AutoCloseable registers finalizer") {
        case class Cfg(debug: Boolean)
        var serviceClosed = false
        class CloseableService(@annotation.unused config: Cfg) extends AutoCloseable {
          def close(): Unit = serviceClosed = true
        }

        val configWire  = Wire(Cfg(true))
        val resource    = Resource.from[CloseableService](configWire)
        val beforeClose = Scope.global.scoped { scope =>
          resource.make(scope)
          !serviceClosed
        }
        assertTrue(beforeClose, serviceClosed)
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

        val configWire = Wire(Cfg(false))
        val dbWire     = Wire.shared[Db]
        val portWire   = Wire(Port(8080))
        val resource   = Resource.from[MultiDepService](configWire, dbWire, portWire)
        val isMultiDep = Scope.global.scoped { scope =>
          resource.make(scope).isInstanceOf[MultiDepService]
        }
        assertTrue(isMultiDep)
      }
    ),
    suite("map")(
      test("transforms the resource value") {
        val portResource = Resource(8080)
        val urlResource  = portResource.map(port => s"http://localhost:$port")

        val url = Scope.global.scoped { scope =>
          urlResource.make(scope)
        }

        assertTrue(url == "http://localhost:8080")
      },
      test("preserves finalizers from original resource") {
        var closed = false

        class Conn extends AutoCloseable {
          def close(): Unit = closed = true
        }

        val connResource = Resource.fromAutoCloseable(new Conn)
        val idResource   = connResource.map(_ => "connection-id")

        val id = Scope.global.scoped { scope =>
          idResource.make(scope)
        }

        assertTrue(id == "connection-id", closed)
      }
    ),
    suite("flatMap")(
      test("sequences two resources") {
        var dbClosed       = false
        val configResource = Resource(Config("jdbc://localhost"))
        val dbResource     = configResource.flatMap { config =>
          Resource.fromAutoCloseable(new Database(config) {
            override def close(): Unit = { dbClosed = true; super.close() }
          })
        }

        val beforeClose = Scope.global.scoped { scope =>
          dbResource.make(scope)
          !dbClosed
        }

        assertTrue(beforeClose, dbClosed)
      },
      test("runs finalizers in LIFO order (inner before outer)") {
        val order = ListBuffer[String]()

        val outer    = Resource.acquireRelease("outer")(_ => order += "outer-released")
        val combined = outer.flatMap { _ =>
          Resource.acquireRelease("inner")(_ => order += "inner-released")
        }

        Scope.global.scoped { scope =>
          combined.make(scope)
        }

        assertTrue(order.toList == List("inner-released", "outer-released"))
      }
    ),
    suite("zip")(
      test("combines two resources into a tuple") {
        val dbResource    = Resource(new Database(Config("url")))
        val cacheResource = Resource(42)
        val combined      = dbResource.zip(cacheResource)

        val (isDb, cache) = Scope.global.scoped { scope =>
          val (db, cache) = combined.make(scope)
          (db.isInstanceOf[Database], cache)
        }

        assertTrue(isDb, cache == 42)
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

        Scope.global.scoped { scope =>
          val _ = combined.make(scope)
        }

        assertTrue(dbClosed, cacheClosed)
      },
      test("runs finalizers in LIFO order (second before first)") {
        val order = ListBuffer[String]()

        val first    = Resource.acquireRelease("first")(_ => order += "first-released")
        val second   = Resource.acquireRelease("second")(_ => order += "second-released")
        val combined = first.zip(second)

        Scope.global.scoped { scope =>
          combined.make(scope)
        }

        assertTrue(order.toList == List("second-released", "first-released"))
      }
    )
  )
}
