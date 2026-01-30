package zio.blocks.typeid

import zio.test._

object RecursiveTypeIdSpec extends ZIOSpecDefault {

  // Note: Direct recursive type aliases like `type RecursiveAlias = (Int, Option[RecursiveAlias])`
  // are illegal in Scala 3.5+. Use case classes for recursive types instead.
  case class RecursiveNode(value: Int, next: Option[RecursiveNode])

  case class RecursiveCase(value: Int, next: Option[RecursiveCase])

  def spec = suite("RecursiveTypeId")(
    test("derives TypeId for recursive case class") {
      val typeId = TypeId.of[RecursiveCase]
      assertTrue(
        typeId.fullName.contains("RecursiveCase"),
        typeId.isCaseClass
      )
    },
    test("recursive case class TypeId is consistent") {
      val typeId1 = TypeId.of[RecursiveCase]
      val typeId2 = TypeId.of[RecursiveCase]
      assertTrue(typeId1 == typeId2)
    },
    test("recursive node derives TypeId") {
      val typeId = TypeId.of[RecursiveNode]
      assertTrue(
        typeId.fullName.contains("RecursiveNode"),
        typeId.isCaseClass
      )
    }
  )
}
