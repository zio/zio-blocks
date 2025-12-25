package zio.blocks.schema.as.reversibility

import zio.test._
import zio.blocks.schema._

object RoundTripProductSpec extends ZIOSpecDefault {

  def spec = suite("RoundTripProductSpec")(
    suite("Round Trip for Identical Case Classes")(
      test("should round trip User -> UserDTO -> User") {
        case class User(name: String, age: Int, email: String)
        case class UserDTO(name: String, age: Int, email: String)

        val as = As.derived[User, UserDTO]
        val input = User("Alice", 30, "alice@example.com")

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip UserDTO -> User -> UserDTO") {
        case class User(name: String, age: Int, email: String)
        case class UserDTO(name: String, age: Int, email: String)

        val as = As.derived[User, UserDTO]
        val input = UserDTO("Bob", 25, "bob@example.com")

        val result = as.from(input).flatMap(as.into)

        assertTrue(result == Right(input))
      },
      test("should convert independently in both directions") {
        case class User(name: String, age: Int, email: String)
        case class UserDTO(name: String, age: Int, email: String)

        val as = As.derived[User, UserDTO]
        val user = User("Charlie", 35, "charlie@example.com")
        val dto = UserDTO("David", 40, "david@example.com")

        val forward = as.into(user)
        val backward = as.from(dto)

        assertTrue(
          forward == Right(UserDTO("Charlie", 35, "charlie@example.com")) &&
            backward == Right(User("David", 40, "david@example.com"))
        )
      }
    ),
    suite("Round Trip with Field Reordering")(
      test("should round trip with reordered fields") {
        case class V1(x: Int, y: String, z: Boolean)
        case class V2(z: Boolean, y: String, x: Int) // Reordered

        val as = As.derived[V1, V2]
        val input = V1(42, "test", true)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip V2 -> V1 -> V2 with reordered fields") {
        case class V1(x: Int, y: String, z: Boolean)
        case class V2(z: Boolean, y: String, x: Int) // Reordered

        val as = As.derived[V1, V2]
        val input = V2(false, "hello", 100)

        val result = as.from(input).flatMap(as.into)

        assertTrue(result == Right(input))
      }
    ),
    suite("Round Trip with Field Renaming")(
      test("should round trip with renamed fields (unique type matching)") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(fullName: String, yearsOld: Int) // Renamed

        val as = As.derived[PersonV1, PersonV2]
        val input = PersonV1("Alice", 30)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip PersonV2 -> PersonV1 -> PersonV2 with renamed fields") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(fullName: String, yearsOld: Int) // Renamed

        val as = As.derived[PersonV1, PersonV2]
        val input = PersonV2("Bob", 25)

        val result = as.from(input).flatMap(as.into)

        assertTrue(result == Right(input))
      }
    ),
    suite("Round Trip with Nested Structures")(
      test("should round trip nested case classes") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        case class AddressDTO(street: String, city: String)
        case class PersonDTO(name: String, address: AddressDTO)

        val as = As.derived[Person, PersonDTO]
        val input = Person("Alice", Address("Main St", "NYC"))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      }
    )
  )
}

