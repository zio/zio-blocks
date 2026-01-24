package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.test._
import scala.language.implicitConversions

object DerivedOpticsVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsVersionSpecificSpec")(
    suite("Lens generation for case classes with derives keyword")(
      test("lens has correct types (compile-time check)") {
        case class Person(name: String, age: Int) derives Schema

        object Person extends DerivedOptics

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

        object Color extends DerivedOptics
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

        object AliasedColor extends DerivedOptics.Of[AC]

        val red: AC    = AliasedColor.Red
        val custom: AC = AliasedColor.Custom(255, 0, 0)
        assertTrue(
          AliasedColor.optics.red.getOption(red) == Some(red),
          AliasedColor.optics.custom.getOption(custom) == Some(custom),
          AliasedColor.optics.green.getOption(red) == None
        )
      }
    ),
    suite("Direct field access for Scala 3")(
      test("direct access works for case class with derives") {
        case class Point(x: Int, y: Int) derives Schema

        object Point extends DerivedOptics

        val xLens: Lens[Point, Int] = Point.x
        val yLens: Lens[Point, Int] = Point.y
        val p                       = Point(10, 20)
        assertTrue(
          xLens.get(p) == 10,
          yLens.get(p) == 20
        )
      },
      test("direct access works for enum prisms") {
        enum Status derives Schema {
          case Active, Inactive
        }

        object Status extends DerivedOptics

        // The conversion provides direct field access, but enum singleton cases
        // are typed as Prism[Status, Status] rather than Prism[Status, Status.Active.type]
        val activePrism = Status.active
        assertTrue(
          activePrism.getOption(Status.Active) == Some(Status.Active),
          activePrism.getOption(Status.Inactive) == None
        )
      },
      test("user-defined members take precedence over derived optics") {
        case class Foo(x: Int) derives Schema

        object Foo extends DerivedOptics {
          def x: String = "user-defined"
        }

        assertTrue(
          Foo.x == "user-defined",
          Foo.optics.x.isInstanceOf[Lens[Foo, Int]]
        )
      },
      test("caching works with optics access") {
        case class Box(value: String) derives Schema

        object Box extends DerivedOptics

        val lens1 = Box.optics.value
        val lens2 = Box.optics.value
        assertTrue(lens1 eq lens2)
      }
    )
  )
}
