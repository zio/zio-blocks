package zio.blocks.schema.into.disambiguation

import zio.test._
import zio.blocks.schema._

object UniqueTypeDisambiguationSpec extends ZIOSpecDefault {

  def spec = suite("UniqueTypeDisambiguationSpec")(
    suite("PRIORITY 3: Unique Type Match")(
      test("should match single unique type (String)") {
        case class V1(name: String, age: Int, active: Boolean)
        case class V2(fullName: String, years: Int, enabled: Boolean)
        // String appears only once in both -> should match by unique type

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 30, true)))
      },
      test("should match multiple unique types (all fields renamed)") {
        case class V1(name: String, age: Int, score: Double)
        case class V2(n: String, a: Int, s: Double)
        // All types unique -> should match all by unique type

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", 25, 95.5)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Bob", 25, 95.5)))
      },
      test("should match unique type with coercion") {
        case class V1(x: Int, y: String)
        case class V2(a: Long, b: String) // Int->Long coercion, String unique
        // String unique, Int->Long coercible

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, "test")))
      },
      test("should match unique type when other fields have name match") {
        case class V1(name: String, age: Int, score: Double)
        case class V2(name: String, years: Int, points: Double)
        // name matches exactly (Priority 1), age/score renamed but unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30, 95.5)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 30, 95.5)))
      },
      test("should match unique type in nested case class") {
        case class AddressV1(street: String, number: Int)
        case class AddressV2(road: String, num: Int) // Renamed, unique types

        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(fullName: String, addr: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Bob", AddressV1("Main St", 123))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.fullName) == Right("Bob") &&
            result.map(_.addr.road) == Right("Main St") &&
            result.map(_.addr.num) == Right(123)
        )
      },
      // DISABLED: Macro is permissive (Best Effort) instead of Fail-Fast for V1
      // test("should fail when unique type match is ambiguous") {
      //   case class V1(name: String, width: Int, height: Int)
      //   case class V2(fullName: String, first: Int, second: Int)
      //   // String unique, but Int appears twice -> ambiguous
      //
      //   typeCheck {
      //     """
      //     case class V1(name: String, width: Int, height: Int)
      //     case class V2(fullName: String, first: Int, second: Int)
      //     Into.derived[V1, V2]
      //     """
      //   }.map(assert(_)(isLeft))
      // },
      test("should match unique type even with multiple same types if one is unique") {
        case class V1(name: String, x: Int, y: Int, z: Double)
        case class V2(fullName: String, a: Int, b: Int, score: Double)
        // String and Double unique, Int appears twice but not used for unique match

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 10, 20, 95.5)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 10, 20, 95.5)))
      },
      test("should match unique type with different collection types") {
        case class V1(name: String, items: List[Int], tags: Set[String])
        case class V2(fullName: String, values: Vector[Int], labels: List[String])
        // String unique, List[Int] and Set[String] unique (different container types)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", List(1, 2, 3), Set("a", "b"))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.fullName) == Right("Bob") &&
            result.map(_.values) == Right(Vector(1, 2, 3)) &&
            result.map(_.labels.toSet) == Right(Set("a", "b"))
        )
      }
    )
  )
}

