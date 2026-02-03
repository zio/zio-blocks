package zio.blocks.schema.as.wrappers

import zio.blocks.schema._
import zio.prelude.{Assertion => PreludeAssertion, _}
import zio.test._
import zio.test.Assertion._

// === Scala 3 Opaque Types (must be at package level) ===
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

/**
 * Tests for As round-trip conversions between primitives and wrapper types:
 *   - ZIO Prelude Newtype/Subtype
 *   - Scala 3 opaque types
 *   - AnyVal case classes (value classes)
 */
object PrimitiveWrapperRoundTripSpec extends ZIOSpecDefault {

  // === ZIO Prelude Newtypes ===
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

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveWrapperRoundTripSpec")(
    suite("ZIO Prelude Newtype/Subtype")(
      test("Int <-> Age round-trip") {
        val as       = As.derived[Int, RtAge]
        val original = 30

        // Int -> Age -> Int
        val intRoundTrip = as.into(original).flatMap(as.from)

        // Age -> Int -> Age
        val age          = RtAge.make(30).toOption.get
        val ageRoundTrip = as.from(age).flatMap(as.into)

        assert(intRoundTrip)(isRight(equalTo(original))) &&
        assertTrue(ageRoundTrip.isRight)
      },
      test("String <-> Name round-trip") {
        val as       = As.derived[String, RtName]
        val original = "Alice"

        // String -> Name -> String
        val stringRoundTrip = as.into(original).flatMap(as.from)

        // Name -> String -> Name
        val name          = RtName.make("Alice").toOption.get
        val nameRoundTrip = as.from(name).flatMap(as.into)

        assert(stringRoundTrip)(isRight(equalTo(original))) &&
        assertTrue(nameRoundTrip.isRight)
      },
      test("reverse works correctly for newtypes") {
        val as      = As.derived[Int, RtAge]
        val swapped = as.reverse

        val age    = RtAge.make(25).toOption.get
        val result = swapped.into(age)

        assert(result)(isRight(equalTo(25)))
      }
    ),
    suite("Scala 3 Opaque Types")(
      test("Int <-> OpaqueAge round-trip") {
        val as       = As.derived[Int, RtOpaqueAge]
        val original = 30

        // Int -> OpaqueAge -> Int
        val intRoundTrip = as.into(original).flatMap(as.from)

        // OpaqueAge -> Int -> OpaqueAge
        val age             = RtOpaqueAge.unsafe(30)
        val opaqueRoundTrip = as.from(age).flatMap(as.into)

        assert(intRoundTrip)(isRight(equalTo(original))) &&
        assertTrue(
          opaqueRoundTrip.isRight,
          opaqueRoundTrip.map(_.toInt).getOrElse(0) == 30
        )
      },
      test("String <-> OpaqueName round-trip") {
        val as       = As.derived[String, RtOpaqueName]
        val original = "Bob"

        // String -> OpaqueName -> String
        val stringRoundTrip = as.into(original).flatMap(as.from)

        // OpaqueName -> String -> OpaqueName
        val name            = RtOpaqueName.unsafe("Bob")
        val opaqueRoundTrip = as.from(name).flatMap(as.into)

        assert(stringRoundTrip)(isRight(equalTo(original))) &&
        assertTrue(
          opaqueRoundTrip.isRight,
          opaqueRoundTrip.map(_.underlying).getOrElse("") == "Bob"
        )
      },
      test("reverse works correctly for opaque types") {
        val as      = As.derived[Int, RtOpaqueAge]
        val swapped = as.reverse

        val age    = RtOpaqueAge.unsafe(35)
        val result = swapped.into(age)

        assert(result)(isRight(equalTo(35)))
      }
    ),
    suite("AnyVal Case Classes")(
      test("Int <-> AgeWrapper round-trip") {
        val as       = As.derived[Int, RtAgeWrapper]
        val original = 30

        // Int -> AgeWrapper -> Int
        val intRoundTrip = as.into(original).flatMap(as.from)

        // AgeWrapper -> Int -> AgeWrapper
        val wrapper          = RtAgeWrapper(30)
        val wrapperRoundTrip = as.from(wrapper).flatMap(as.into)

        assert(intRoundTrip)(isRight(equalTo(original))) &&
        assert(wrapperRoundTrip)(isRight(equalTo(wrapper)))
      },
      test("String <-> NameWrapper round-trip") {
        val as       = As.derived[String, RtNameWrapper]
        val original = "Carol"

        // String -> NameWrapper -> String
        val stringRoundTrip = as.into(original).flatMap(as.from)

        // NameWrapper -> String -> NameWrapper
        val wrapper          = RtNameWrapper("Carol")
        val wrapperRoundTrip = as.from(wrapper).flatMap(as.into)

        assert(stringRoundTrip)(isRight(equalTo(original))) &&
        assert(wrapperRoundTrip)(isRight(equalTo(wrapper)))
      },
      test("reverse works correctly for AnyVal") {
        val as      = As.derived[Int, RtAgeWrapper]
        val swapped = as.reverse

        val wrapper = RtAgeWrapper(25)
        val result  = swapped.into(wrapper)

        assert(result)(isRight(equalTo(25)))
      }
    )
  )
}
