package zio.blocks.scope

import zio.test._

object FactorySpec extends ZIOSpecDefault {

  case class Config(url: String)

  class Database(@scala.annotation.unused config: Config) extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  def spec = suite("Factory")(
    test("Factory(value) creates a shared factory") {
      val factory        = Factory(Config("jdbc://localhost"))
      val (scope, close) = Scope.createTestableScope()
      val config         = factory.make(scope)
      close()
      assertTrue(config.url == "jdbc://localhost")
    },
    test("Factory.shared creates from function") {
      val factory        = Factory.shared[Config](_ => Config("test-url"))
      val (scope, close) = Scope.createTestableScope()
      val config         = factory.make(scope)
      close()
      assertTrue(config.url == "test-url")
    },
    test("Factory.unique creates fresh instances") {
      var counter = 0
      val factory = Factory.unique[Int] { _ =>
        counter += 1
        counter
      }
      val (scope, close) = Scope.createTestableScope()
      val a              = factory.make(scope)
      val b              = factory.make(scope)
      close()
      assertTrue(a == 1, b == 2)
    },
    test("Factory can register finalizers") {
      val factory = Factory.shared[Database] { scope =>
        val db = new Database(Config("url"))
        scope.defer(db.close())
        db
      }
      val (scope, close) = Scope.createTestableScope()
      val db             = factory.make(scope)
      val beforeClose    = db.closed
      close()
      val afterClose = db.closed
      assertTrue(!beforeClose, afterClose)
    },
    test("Wire.toFactory converts Wire.Shared to Factory.Shared") {
      val wire = Wire.Shared.fromFunction[Config, Database] { scope =>
        val config = scope.get[Config]
        new Database(config)
      }
      val deps           = zio.blocks.context.Context(Config("test-url"))
      val factory        = wire.toFactory(deps)
      val (scope, close) = Scope.createTestableScope()
      val db             = factory.make(scope)
      close()
      assertTrue(factory.isInstanceOf[Factory.Shared[?]], db.isInstanceOf[Database])
    },
    test("Wire.toFactory converts Wire.Unique to Factory.Unique") {
      var counter = 0
      val wire    = Wire.Unique.fromFunction[Config, Int] { _ =>
        counter += 1
        counter
      }
      val deps           = zio.blocks.context.Context(Config("url"))
      val factory        = wire.toFactory(deps)
      val (scope, close) = Scope.createTestableScope()
      val a              = factory.make(scope)
      val b              = factory.make(scope)
      close()
      assertTrue(factory.isInstanceOf[Factory.Unique[?]], a == 1, b == 2)
    }
  )
}
