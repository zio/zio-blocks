package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context

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
    }
  )
}
