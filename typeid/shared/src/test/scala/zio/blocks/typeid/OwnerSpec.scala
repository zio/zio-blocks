package zio.blocks.typeid

import zio.test._

object OwnerSpec extends ZIOSpecDefault {
  def spec = suite("Owner")(
    test("should create root owner") {
      assertTrue(Owner.Root.segments.isEmpty && Owner.Root.asString == "")
    },
    test("scala owner should be correct") {
      assertTrue(Owner.scala.asString == "scala")
    }
  )
}
