package zio.blocks.typeid

import zio.test._

object TypeReprSpec extends ZIOSpecDefault {
  def spec = suite("TypeRepr")(
    test("should create Ref") {
      val intId = TypeId.derive[Int]
      val ref = zio.blocks.typeid.TypeRepr.Ref(intId)
      assertTrue(ref.isInstanceOf[zio.blocks.typeid.TypeRepr.Ref])
    },
    test("AnyType should be a singleton") {
      assertTrue(zio.blocks.typeid.TypeRepr.AnyType == zio.blocks.typeid.TypeRepr.AnyType)
    }
  )
}
