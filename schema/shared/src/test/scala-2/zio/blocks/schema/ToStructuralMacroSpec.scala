package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object ToStructuralMacroSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ToStructuralMacroSpec")(
    test("derives ToStructural using explicit call") {
      implicit val ts = ToStructural.derived[Point]

      val p = Point(10, 20)
      val s = ts.toStructural(p)

      // Access via Dynamic (StructuralValue)
      val sv = s.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("x") == 10) &&
      assertTrue(sv.selectDynamic("y") == 20)
    },
    test("structuralSchema returns valid schema") {
      implicit val ts = ToStructural.derived[Person]
      implicit val schema: Schema[Person] = Schema.derived[Person]

      val sSchema = ts.structuralSchema

      assertTrue(sSchema.reflect.typeName.name == "{age: Int, name: String}")
    }
  )
}
