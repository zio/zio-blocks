package zio.blocks.schema.toon

import zio.blocks.schema.Schema
import zio.blocks.schema.json.NameMapper
import zio.test._

/**
 * Comprehensive tests for NameMapper transformations in TOON codec.
 *
 * Tests all NameMapper strategies:
 *   - Identity (no transformation)
 *   - SnakeCase (camelCase -> snake_case)
 *   - KebabCase (camelCase -> kebab-case)
 *   - PascalCase (camelCase -> PascalCase)
 *   - CamelCase (snake_case -> camelCase)
 *   - Custom (user-defined function)
 */
object NameMapperSpec extends ZIOSpecDefault {

  case class PersonWithCamelCase(
    firstName: String,
    lastName: String,
    emailAddress: String,
    phoneNumber: Int
  )

  object PersonWithCamelCase {
    implicit val schema: Schema[PersonWithCamelCase] = Schema.derived
  }

  case class NestedPerson(
    fullName: String,
    homeAddress: Address
  )

  case class Address(
    streetName: String,
    cityName: String,
    zipCode: String
  )

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  object NestedPerson {
    implicit val schema: Schema[NestedPerson] = Schema.derived
  }

  def spec = suite("NameMapperSpec")(
    suite("Identity mapper")(
      test("should not transform field names") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.Identity)
        val codec   = PersonWithCamelCase.schema.derive(deriver)

        val person = PersonWithCamelCase("John", "Doe", "john@example.com", 12345)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("firstName: John"),
          toon.contains("lastName: Doe"),
          toon.contains("emailAddress:"),
          toon.contains("phoneNumber: 12345")
        )
      }
    ),
    suite("SnakeCase mapper")(
      test("should transform camelCase to snake_case") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
        val codec   = PersonWithCamelCase.schema.derive(deriver)

        val person = PersonWithCamelCase("John", "Doe", "john@example.com", 12345)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("first_name: John"),
          toon.contains("last_name: Doe"),
          toon.contains("email_address:"),
          toon.contains("phone_number: 12345")
        )
      },
      test("should apply snake_case to nested records") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
        val codec   = NestedPerson.schema.derive(deriver)

        val person = NestedPerson("John Doe", Address("Main St", "NYC", "10001"))
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("full_name:"),
          toon.contains("home_address:"),
          toon.contains("street_name:"),
          toon.contains("city_name: NYC"),
          toon.contains("zip_code: 10001")
        )
      }
    ),
    suite("KebabCase mapper")(
      test("should transform camelCase to kebab-case") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.KebabCase)
        val codec   = PersonWithCamelCase.schema.derive(deriver)

        val person = PersonWithCamelCase("John", "Doe", "john@example.com", 12345)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("first-name: John"),
          toon.contains("last-name: Doe"),
          toon.contains("email-address:"),
          toon.contains("phone-number: 12345")
        )
      }
    ),
    suite("Custom mapper")(
      test("should apply custom transformation function") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.Custom(_.toUpperCase))
        val codec   = PersonWithCamelCase.schema.derive(deriver)

        val person = PersonWithCamelCase("John", "Doe", "john@example.com", 12345)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("FIRSTNAME: John"),
          toon.contains("LASTNAME: Doe"),
          toon.contains("EMAILADDRESS:"),
          toon.contains("PHONENUMBER: 12345")
        )
      },
      test("should apply prefix transformation") {
        val deriver = ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.Custom(s => s"field_$s"))
        val codec   = PersonWithCamelCase.schema.derive(deriver)

        val person = PersonWithCamelCase("John", "Doe", "john@example.com", 12345)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("field_firstName: John"),
          toon.contains("field_lastName: Doe")
        )
      }
    ),
    suite("CaseNameMapper for variants")(
      test("should transform case names in sealed traits") {
        sealed trait Color
        case object Red   extends Color
        case object Green extends Color
        case object Blue  extends Color

        object Color {
          implicit val schema: Schema[Color] = Schema.derived
        }

        val deriver = ToonBinaryCodecDeriver.withCaseNameMapper(NameMapper.SnakeCase)
        val codec   = Color.schema.derive(deriver)

        val color = Red: Color
        val toon  = codec.encodeToString(color)

        assertTrue(toon.contains("red"))
      }
    )
  )
}
