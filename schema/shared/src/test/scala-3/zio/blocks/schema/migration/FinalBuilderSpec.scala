package zio.blocks.schema.migration

import zio.test.*
import zio.blocks.schema.*
 // ম্যাক্রো ইনজেকশন

object FinalBuilderSpec extends ZIOSpecDefault {
  case class SubData(id: Int)
  case class MainData(sub: SubData, name: String)
  case class TargetData(sub: SubData, fullName: String, version: Int)

  implicit val sourceSchema: Schema[MainData] = Schema.derived[MainData]
  implicit val targetSchema: Schema[TargetData] = Schema.derived[TargetData]

  def spec = suite("Final Scala 3 Builder Verification")(
    test("Complex migration with nested rename and field addition") {
      val builder = MigrationBuilder.make[MainData, TargetData]
        .renameField(_.name, _.fullName) // ম্যাক্রো ব্যবহার
        .addField(_.version, DynamicValue.Primitive(PrimitiveValue.Int(1)))
        .build

      val input = MainData(SubData(101), "Scala Expert")
      val result = builder.apply(input)

      val expected = TargetData(SubData(101), "Scala Expert", 1)
      assertTrue(result == Right(expected))
    }
  )
}