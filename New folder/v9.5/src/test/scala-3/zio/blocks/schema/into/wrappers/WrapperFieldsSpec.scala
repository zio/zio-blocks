package zio.blocks.schema.into.wrappers

import zio.blocks.schema._
import zio.prelude.{Assertion => PreludeAssertion, _}
import zio.test._

// === Opaque Types for wrapper fields ===
opaque type UserId = Long
object UserId {
  def apply(value: Long): Either[String, UserId] =
    if (value > 0) Right(value)
    else Left(s"Invalid user id: $value")

  def unsafe(value: Long): UserId = value

  extension (id: UserId) def toLong: Long = id
}

opaque type Email = String
object Email {
  def apply(value: String): Either[String, Email] =
    if (value.contains("@")) Right(value)
    else Left(s"Invalid email: $value")

  def unsafe(value: String): Email = value

  extension (email: Email) def underlying: String = email
}

/**
 * Tests for Into/As conversions between case classes where fields are wrapper
 * types.
 *
 * This tests the scenario where:
 *   - Source has primitive field, target has wrapper field (e.g., Long ->
 *     UserId)
 *   - Source has wrapper field, target has primitive field (e.g., UserId ->
 *     Long)
 *   - Both have wrapper fields but different types
 */
object WrapperFieldsSpec extends ZIOSpecDefault {

  // === ZIO Prelude Newtypes ===
  object Types {
    object Age extends Subtype[Int] {
      override def assertion: PreludeAssertion[Int] = PreludeAssertion.between(0, 150)
    }
    type Age = Age.Type

    object Name extends Newtype[String] {
      override def assertion: PreludeAssertion[String] = !PreludeAssertion.isEmptyString
    }
    type Name = Name.Type
  }

  // === Single-field case class wrappers ===
  case class WrappedId(value: Long) extends AnyVal
  case class WrappedName(value: String)

  // === Test case classes ===

  // Source with primitives, target with wrappers
  case class UserRaw(id: Long, name: String, age: Int)
  case class UserValidated(id: UserId, name: Email, age: Types.Age)

  // Same field structure but with different wrapper combinations
  case class PersonPrimitive(name: String, age: Int)
  case class PersonWrapped(name: Types.Name, age: Types.Age)

  // Single-field case class wrapper tests
  case class RecordWithId(id: Long, label: String)
  case class RecordWithWrappedId(id: WrappedId, label: String)

  def spec = suite("WrapperFieldsSpec")(
    suite("Opaque Type Fields")(
      test("Into: primitive fields -> opaque type fields") {
        val into = Into.derived[UserRaw, UserValidated]

        val raw    = UserRaw(1L, "test@example.com", 25)
        val result = into.into(raw)

        assertTrue(
          result.isRight,
          result.toOption.get.id.toLong == 1L,
          result.toOption.get.name.underlying == "test@example.com",
          result.toOption.get.age == Types.Age.make(25).toOption.get
        )
      },
      test("Into: primitive fields -> opaque type fields (validation failure)") {
        val into = Into.derived[UserRaw, UserValidated]

        val raw    = UserRaw(-1L, "invalid-email", 200)
        val result = into.into(raw)

        assertTrue(result.isLeft)
      },
      test("Into: opaque type fields -> primitive fields") {
        val into = Into.derived[UserValidated, UserRaw]

        val validated = UserValidated(
          UserId.unsafe(42L),
          Email.unsafe("user@example.com"),
          Types.Age.make(30).toOption.get
        )
        val result = into.into(validated)

        assertTrue(
          result.isRight,
          result.toOption.get.id == 42L,
          result.toOption.get.name == "user@example.com",
          result.toOption.get.age == 30
        )
      }
    ),
    suite("ZIO Prelude Newtype Fields")(
      test("Into: primitive fields -> newtype fields") {
        val into = Into.derived[PersonPrimitive, PersonWrapped]

        val primitive = PersonPrimitive("Alice", 25)
        val result    = into.into(primitive)

        assertTrue(
          result.isRight,
          result.toOption.get.name == Types.Name.make("Alice").toOption.get,
          result.toOption.get.age == Types.Age.make(25).toOption.get
        )
      },
      test("Into: primitive fields -> newtype fields (validation failure)") {
        val into = Into.derived[PersonPrimitive, PersonWrapped]

        val primitive = PersonPrimitive("", 200) // empty name, invalid age
        val result    = into.into(primitive)

        assertTrue(result.isLeft)
      },
      test("Into: newtype fields -> primitive fields") {
        val into = Into.derived[PersonWrapped, PersonPrimitive]

        val wrapped = PersonWrapped(
          Types.Name.make("Bob").toOption.get,
          Types.Age.make(30).toOption.get
        )
        val result = into.into(wrapped)

        assertTrue(
          result.isRight,
          result.toOption.get.name == "Bob",
          result.toOption.get.age == 30
        )
      }
    ),
    suite("AnyVal/Single-field Case Class Fields")(
      test("Into: primitive field -> single-field case class field") {
        val into = Into.derived[RecordWithId, RecordWithWrappedId]

        val raw    = RecordWithId(123L, "test")
        val result = into.into(raw)

        assertTrue(
          result.isRight,
          result.toOption.get.id.value == 123L,
          result.toOption.get.label == "test"
        )
      },
      test("Into: single-field case class field -> primitive field") {
        val into = Into.derived[RecordWithWrappedId, RecordWithId]

        val wrapped = RecordWithWrappedId(WrappedId(456L), "label")
        val result  = into.into(wrapped)

        assertTrue(
          result.isRight,
          result.toOption.get.id == 456L,
          result.toOption.get.label == "label"
        )
      }
    ),
    suite("As Round-trips with Wrapper Fields")(
      test("As: primitive <-> newtype fields round-trip") {
        val as = As.derived[PersonPrimitive, PersonWrapped]

        val original  = PersonPrimitive("Carol", 40)
        val roundTrip = as.into(original).flatMap(as.from)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == original
        )
      },
      test("As: wrapper -> primitive -> wrapper round-trip") {
        val as = As.derived[PersonPrimitive, PersonWrapped]

        val wrapped = PersonWrapped(
          Types.Name.make("Dave").toOption.get,
          Types.Age.make(35).toOption.get
        )
        val roundTrip = as.from(wrapped).flatMap(as.into)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == wrapped
        )
      }
    )
  )
}
