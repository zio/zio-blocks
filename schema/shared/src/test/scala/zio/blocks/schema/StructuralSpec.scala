package zio.blocks.schema

import zio.test._

object StructuralSpec extends ZIOSpecDefault {
  def spec =
    suite("StructuralSpec")(test("case class converts to structural (Selectable) at compile time") {
      case class Person(name: String, age: Int)

      val schema = Schema.derived[Person]

      // Ensure `.structural` compiles and yields a Schema (Selectable or DynamicValue at present).
      // Round-trip: nominal -> selectable -> nominal
      val sel = Schema.toStructuralValue(schema, Person("Alice", 30))
      val back = Schema.fromStructuralValue(schema, sel)

      assertTrue(back == Right(Person("Alice", 30)))
    })
}
