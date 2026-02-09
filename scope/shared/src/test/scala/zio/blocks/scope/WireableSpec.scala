package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context

object WireableSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  case class DbConfig(url: String)
  class Database(@annotation.unused config: DbConfig)
  class DatabaseWithConfig(@annotation.unused config: Config)

  def spec = suite("Wireable")(
    test("trait can be implemented and wire constructs correctly") {
      val wireable = new Wireable[Config] {
        type In = Any
        def wire: Wire[Any, Config] = Wire(Config(debug = true))
      }
      val (scope, close) = Scope.createTestableScope()
      val config         = wireable.wire.make(scope, Context.empty)
      close()
      assertTrue(wireable.wire.isShared, config.debug)
    },
    test("Wireable.apply creates wireable from value") {
      val wireable: Wireable.Typed[Any, Config] = Wireable(Config(true))
      val wire: Wire[Any, Config]               = wireable.wire
      assertTrue(wire.isShared)
    },
    test("Wireable.fromWire creates wireable from wire") {
      val wire                                         = shared[Database]
      val wireable: Wireable.Typed[DbConfig, Database] = Wireable.fromWire(wire)
      val w: Wire[DbConfig, Database]                  = wireable.wire
      assertTrue(w eq wire)
    },
    suite("Wireable.from with overrides")(
      test("reduces In type by covered dependencies") {
        class Service(@annotation.unused config: Config, @annotation.unused db: DatabaseWithConfig)

        val configWire = Wire(Config(true))
        val wireable   = Wireable.from[Service](configWire)

        val dbWire         = Wire(new DatabaseWithConfig(Config(true)))
        val (scope, close) = Scope.createTestableScope()
        val resource       = wireable.wire.toResource(dbWire)
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[Service])
      },
      test("with all dependencies covered has In = Any") {
        class SimpleService(@annotation.unused config: Config)

        val configWire = Wire(Config(true))
        val wireable   = Wireable.from[SimpleService](configWire)

        val (scope, close) = Scope.createTestableScope()
        val resource       = wireable.wire.toResource()
        val service        = resource.make(scope)
        close()
        assertTrue(service.isInstanceOf[SimpleService])
      }
    )
  )
}
