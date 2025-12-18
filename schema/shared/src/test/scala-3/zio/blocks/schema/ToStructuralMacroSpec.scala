package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object ToStructuralMacroSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int) derives ToStructural
  case class Point(x: Int, y: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ToStructuralMacroSpec")(
    test("derives ToStructural using 'derives' syntax") {
      val ts = implicitly[ToStructural[Person]]

      val p = Person("Alice", 30)
      val s = ts.toStructural(p)

      // Access via Selectable
      val sv = s.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("name") == "Alice") &&
      assertTrue(sv.selectDynamic("age") == 30)
    },
    test("derives ToStructural using explicit call") {
      implicit val ts: ToStructural[Point] = ToStructural.derived[Point]

      val p = Point(10, 20)
      val s = ts.toStructural(p)

      val sv = s.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("x") == 10) &&
      assertTrue(sv.selectDynamic("y") == 20)
    },
    test("structuralSchema returns valid schema") {
      val ts                              = implicitly[ToStructural[Person]]
      implicit val schema: Schema[Person] = Schema.derived[Person]

      val sSchema = ts.structuralSchema

      assertTrue(sSchema.reflect.typeName.name == "{age: Int, name: String}")
    }
  )
}
