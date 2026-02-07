package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object WireableSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  case class DbConfig(url: String)
  class Database(@annotation.unused config: DbConfig)

  def spec = suite("Wireable")(
    test("trait can be implemented and wire constructs correctly") {
      val wireable = new Wireable[Config] {
        type In = Any
        def wire: Wire[Any, Config] = Wire(Config(debug = true))
      }
      val parent                         = Scope.global
      val finalizers                     = new Finalizers
      implicit val scope: Scope.Has[Any] =
        Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val ctx    = wireable.wire.construct
      val config = ctx.get[Config]
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
    }
  )
}
