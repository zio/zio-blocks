package zio.blocks.schema.as

import zio.test._
import zio._
import zio.blocks.schema.As

object AsProductSpec extends ZIOSpecDefault {

  // Simple case classes for bidirectional conversion
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Int)

  // Case classes with different field names (should work if Into handles it)
  case class UserV1(fullName: String, years: Int)
  case class UserV2(name: String, age: Int)

  def spec = suite("As Product Support")(
    suite("Identity Conversion")(
      test("Should convert PersonV1 to PersonV2 and back (same fields)") {
        val as    = As.derived[PersonV1, PersonV2]
        val input = PersonV1("Alice", 30)

        val forward  = as.into(input)
        val backward = forward.flatMap(as.from)

        assertTrue(
          forward == Right(PersonV2("Alice", 30)) &&
            backward == Right(PersonV1("Alice", 30))
        )
      }
    ),
    suite("Bidirectional Round-Trip")(
      test("Should round-trip PersonV1 -> PersonV2 -> PersonV1") {
        val as    = As.derived[PersonV1, PersonV2]
        val input = PersonV1("Bob", 25)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("Should round-trip PersonV2 -> PersonV1 -> PersonV2") {
        val as    = As.derived[PersonV1, PersonV2]
        val input = PersonV2("Charlie", 35)

        val result = as.from(input).flatMap(as.into)

        assertTrue(result == Right(input))
      }
    ),
    suite("Both Directions")(
      test("Should convert in both directions independently") {
        val as = As.derived[PersonV1, PersonV2]

        val forward  = as.into(PersonV1("David", 40))
        val backward = as.from(PersonV2("Eve", 45))

        assertTrue(
          forward == Right(PersonV2("David", 40)) &&
            backward == Right(PersonV1("Eve", 45))
        )
      }
    )
  )
}
