package zio.blocks.scope

import zio.test._

object WireableSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Wireable")(
    test("trait exists") {
      val wireable = new Wireable[Config] {
        type In = Any
        def wire: Wire[Any, Config] = Wire.value(Config(true))
      }
      assertTrue(wireable.wire != null)
    }
  )
}
