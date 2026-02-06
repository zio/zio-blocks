package zio.blocks.scope

import zio.test._

object WireableSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  case class DbConfig(url: String)
  class Database(@annotation.unused config: DbConfig)

  def spec = suite("Wireable")(
    test("trait exists") {
      val wireable = new Wireable[Config] {
        type In = Any
        def wire: Wire[Any, Config] = Wire(Config(true))
      }
      assertTrue(wireable.wire != null)
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
    }
  )
}
