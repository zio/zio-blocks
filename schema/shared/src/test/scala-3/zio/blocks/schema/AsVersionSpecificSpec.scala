package zio.blocks.schema

import zio.blocks.schema._
import zio.prelude.{Assertion => PreludeAssertion, _}
import zio.test._
import zio.test.Assertion._

opaque type RoundTripAge = Int
object RoundTripAge {
  def apply(value: Int): Either[String, RoundTripAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")

  def unsafe(value: Int): RoundTripAge = value

  extension (age: RoundTripAge) def toInt: Int = age
}

opaque type RoundTripEmail = String
object RoundTripEmail {
  def apply(value: String): Either[String, RoundTripEmail] =
    if (value.contains("@")) Right(value)
    else Left(s"Invalid email: $value")

  def unsafe(value: String): RoundTripEmail = value

  extension (email: RoundTripEmail) def value: String = email
}

opaque type RoundTripPositiveInt = Int
object RoundTripPositiveInt {
  def apply(value: Int): Either[String, RoundTripPositiveInt] =
    if (value > 0) Right(value)
    else Left(s"Value must be positive: $value")

  def unsafe(value: Int): RoundTripPositiveInt = value

  extension (pi: RoundTripPositiveInt) def toInt: Int = pi
}

opaque type RtOpaqueAge = Int
object RtOpaqueAge {
  def apply(value: Int): Either[String, RtOpaqueAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")

  def unsafe(value: Int): RtOpaqueAge = value

  extension (age: RtOpaqueAge) def toInt: Int = age
}

opaque type RtOpaqueName = String
object RtOpaqueName {
  def apply(value: String): Either[String, RtOpaqueName] =
    if (value.nonEmpty) Right(value)
    else Left("Name cannot be empty")

  def unsafe(value: String): RtOpaqueName = value

  extension (name: RtOpaqueName) def underlying: String = name
}

object AsVersionSpecificSpec extends SchemaBaseSpec {

  case class PersonRawA(name: String, age: Int)
  case class PersonValidatedB(name: String, age: RoundTripAge)

  case class UserRawA(email: String, score: Int)
  case class UserValidatedB(email: RoundTripEmail, score: RoundTripPositiveInt)

  object PreludeDomain {
    object RtAge extends Subtype[Int] {
      override def assertion: PreludeAssertion[Int] = PreludeAssertion.between(0, 150)
    }
    type RtAge = RtAge.Type

    object RtName extends Newtype[String] {
      override def assertion: PreludeAssertion[String] = !PreludeAssertion.isEmptyString
    }
    type RtName = RtName.Type
  }

  // === AnyVal Case Classes ===
  case class RtAgeWrapper(value: Int)     extends AnyVal
  case class RtNameWrapper(value: String) extends AnyVal

  import PreludeDomain._
  import RtOpaqueAge.toInt
  import RtOpaqueName.underlying

  def spec: Spec[TestEnvironment, Any] = suite("AsVersionSpecificSpec")(
    suite("Opaque Type Round Trip Spec")(
      test("valid age round-trips through opaque type") {
        val original = PersonRawA("Alice", 30)
        val as       = As.derived[PersonRawA, PersonValidatedB]
        assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
      },
      test("invalid age fails into direction") {
        assert(As.derived[PersonRawA, PersonValidatedB].into(PersonRawA("Invalid", -5)))(isLeft)
      },
      test("valid email and score round-trip") {
        val original = UserRawA("user@example.com", 100)
        val as       = As.derived[UserRawA, UserValidatedB]
        assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
      },
      test("invalid email fails") {
        assert(As.derived[UserRawA, UserValidatedB].into(UserRawA("invalid-email", 100)))(isLeft)
      },
      test("round-trip from validated to raw and back") {
        val validated = PersonValidatedB("Carol", RoundTripAge.unsafe(40))
        val as        = As.derived[PersonRawA, PersonValidatedB]
        assert(as.from(validated).flatMap(a => as.into(a)))(isRight(equalTo(validated)))
      },
      test("swapped As works correctly") {
        val validated = PersonValidatedB("Dave", RoundTripAge.unsafe(35))
        val result    = As.derived[PersonRawA, PersonValidatedB].reverse.into(validated)
        assert(result)(isRight(equalTo(PersonRawA("Dave", 35))))
      }
    ),
    suite("ZIO Prelude Newtype/Subtype")(
      test("Int <-> Age round-trip") {
        val as       = As.derived[Int, RtAge]
        val original = 30
        val age      = RtAge.make(30).toOption.get
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(age).flatMap(as.into).isRight)
      },
      test("String <-> Name round-trip") {
        val as       = As.derived[String, RtName]
        val original = "Alice"
        val name     = RtName.make("Alice").toOption.get
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(name).flatMap(as.into).isRight)
      },
      test("reverse works correctly for newtypes") {
        assert(As.derived[Int, RtAge].reverse.into(RtAge.make(25).toOption.get))(isRight(equalTo(25)))
      }
    ),
    suite("Scala 3 Opaque Types")(
      test("Int <-> OpaqueAge round-trip") {
        val as       = As.derived[Int, RtOpaqueAge]
        val original = 30
        val age      = RtOpaqueAge.unsafe(30)
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(age).flatMap(as.into).map(_.toInt).getOrElse(0) == 30)
      },
      test("String <-> OpaqueName round-trip") {
        val as       = As.derived[String, RtOpaqueName]
        val original = "Bob"
        val name     = RtOpaqueName.unsafe("Bob")
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(name).flatMap(as.into).map(_.underlying).getOrElse("") == "Bob")
      },
      test("reverse works correctly for opaque types") {
        assert(As.derived[Int, RtOpaqueAge].reverse.into(RtOpaqueAge.unsafe(35)))(isRight(equalTo(35)))
      }
    ),
    suite("AnyVal Case Classes")(
      test("Int <-> AgeWrapper round-trip") {
        val as       = As.derived[Int, RtAgeWrapper]
        val original = 30
        // AgeWrapper -> Int -> AgeWrapper
        val wrapper = RtAgeWrapper(30)
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assert(as.from(wrapper).flatMap(as.into))(isRight(equalTo(wrapper)))
      },
      test("String <-> NameWrapper round-trip") {
        val as       = As.derived[String, RtNameWrapper]
        val original = "Carol"
        val wrapper  = RtNameWrapper("Carol")
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assert(as.from(wrapper).flatMap(as.into))(isRight(equalTo(wrapper)))
      },
      test("reverse works correctly for AnyVal") {
        assert(As.derived[Int, RtAgeWrapper].reverse.into(RtAgeWrapper(25)))(isRight(equalTo(25)))
      }
    )
  )
}
