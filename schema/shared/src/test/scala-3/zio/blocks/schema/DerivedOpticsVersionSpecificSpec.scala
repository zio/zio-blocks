package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.test._

object DerivedOpticsVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsVersionSpecificSpec")(
    suite("Lens generation for case classes with derives keyword")(
      test("lens has correct types (compile-time check)") {
        case class Person(name: String, age: Int) derives Schema

        object Person extends DerivedOptics[Person]

        val nameLens: Lens[Person, String] = Person.optics.name
        val ageLens: Lens[Person, Int]     = Person.optics.age
        val person                         = Person("Test", 25)
        assertTrue(
          Person.optics eq Person.optics,
          nameLens eq Person.optics.name,
          nameLens.get(person) == "Test",
          ageLens.get(person) == 25
        )
      }
    ),
    suite("Prism generation for Scala 3 enums")(
      test("prism for enum cases") {
        assertCompletes
      },
      test("prism works when companion uses type alias of its own type") {
        assertCompletes
      }
    )
  )
}
