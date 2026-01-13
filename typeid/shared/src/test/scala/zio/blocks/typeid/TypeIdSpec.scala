package zio.blocks.typeid

import zio.test._

object TypeIdSpec extends ZIOSpecDefault {
  def spec = suite("TypeId")(
    test("should derive TypeId for Int") {
      val intId = TypeId.derive[Int]
      assertTrue(intId.name == "Int")
    },
    test("should create nominal TypeId") {
      val id = TypeId.nominal[String]("MyType", Owner.Root, Nil)
      assertTrue(id.name == "MyType" && id.owner == Owner.Root)
    }
  )
}
