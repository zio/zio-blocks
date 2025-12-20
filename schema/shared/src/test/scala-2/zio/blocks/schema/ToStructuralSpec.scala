package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object ToStructuralSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ToStructuralSpec")(
    test("manually created ToStructural instance works") {
      // Define structural type alias
      type PersonStructural = { def name: String; def age: Int }

      // Create manual instance
      implicit val personToStructural: ToStructural[Person] { type StructuralType = PersonStructural } =
        new ToStructural[Person] {
          type StructuralType = PersonStructural

          def toStructural(value: Person): StructuralType =
            new StructuralValue(Map("name" -> value.name, "age" -> value.age)).asInstanceOf[StructuralType]

          def structuralSchema(implicit schema: Schema[Person]): Schema[StructuralType] =
            Schema.derived[PersonStructural]
        }

      val person = Person("Alice", 30)
      val ts     = ToStructural[Person]

      // Test conversion
      val structural = ts.toStructural(person)

      // Access fields via cast to StructuralValue (since StructuralValue is the runtime backing)
      val sv   = structural.asInstanceOf[StructuralValue]
      val name = sv.selectDynamic("name").asInstanceOf[String]
      val age  = sv.selectDynamic("age").asInstanceOf[Int]

      // Test schema generation
      implicit val personSchema: Schema[Person] = Schema.derived[Person]
      val schema                                = ts.structuralSchema

      assertTrue(name == "Alice") &&
      assertTrue(age == 30) &&
      assertTrue(schema != null) &&
      assertTrue(schema.reflect.typeName.name == "{age:Int,name:String}")
    }
  )
}
