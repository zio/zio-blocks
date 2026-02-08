package zio.blocks.scope

import zio.{ZIO, Scope => _}
import zio.test._
import zio.blocks.context.Context

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

object ResourceSpec extends ZIOSpecDefault {

  case class Config(url: String)

  class Database(@scala.annotation.unused config: Config) extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  def spec = suite("Resource")(
    test("Resource(value) creates a shared resource") {
      val resource       = Resource(Config("jdbc://localhost"))
      val (scope, close) = Scope.createTestableScope()
      val config         = resource.make(scope)
      close()
      assertTrue(config.url == "jdbc://localhost")
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
    test("Wire.toResource converts Wire.Shared to Resource.Shared") {
      val wire = Wire.Shared.fromFunction[Config, Database] { (scope, ctx) =>
        val config = ctx.get[Config]
        val db     = new Database(config)
        scope.defer(db.close())
        db
      }
      val deps           = Context(Config("test-url"))
      val resource       = wire.toResource(deps)
      val (scope, close) = Scope.createTestableScope()
      val db             = resource.make(scope)
      close()
      assertTrue(resource.isInstanceOf[Resource.Shared[?]], db.isInstanceOf[Database], db.closed)
    },
    test("Wire.toResource converts Wire.Unique to Resource.Unique") {
      var counter = 0
      val wire    = Wire.Unique.fromFunction[Config, Int] { (_, _) =>
        counter += 1
        counter
      }
      val deps           = Context(Config("url"))
      val resource       = wire.toResource(deps)
      val (scope, close) = Scope.createTestableScope()
      val a              = resource.make(scope)
      val b              = resource.make(scope)
      close()
      assertTrue(resource.isInstanceOf[Resource.Unique[?]], a == 1, b == 2)
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
    }
  )
}
