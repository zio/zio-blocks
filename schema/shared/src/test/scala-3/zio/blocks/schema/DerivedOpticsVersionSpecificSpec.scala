package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.test._

object DerivedOpticsVersionSpecificSpec extends ZIOSpecDefault {
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
        enum Color derives Schema {
          case Red, Green, Blue

          case Custom(r: Int, g: Int, b: Int)
        }

        object Color extends DerivedOptics[Color]
        val red: Color    = Color.Red
        val custom: Color = Color.Custom(255, 128, 0)
        assertTrue(
          Color.optics.red.getOption(red) == Some(Color.Red),
          Color.optics.green.getOption(red).isEmpty,
          Color.optics.blue.getOption(red).isEmpty,
          Color.optics.custom.getOption(custom) == Some(custom)
        )
      },
      test("prism works when companion uses type alias of its own type") {
        enum AliasedColor derives Schema {
          case Red, Green, Blue

          case Custom(r: Int, g: Int, b: Int)
        }

        type AC = AliasedColor

        object AliasedColor extends DerivedOptics[AC]

        val red: AC    = AliasedColor.Red
        val custom: AC = AliasedColor.Custom(255, 0, 0)
        assertTrue(
          AliasedColor.optics.red.getOption(red) == Some(red),
          AliasedColor.optics.custom.getOption(custom) == Some(custom),
          AliasedColor.optics.green.getOption(red) == None
        )
      }
    )
  )
}
