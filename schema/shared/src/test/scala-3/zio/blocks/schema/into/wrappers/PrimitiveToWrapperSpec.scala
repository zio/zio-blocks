package zio.blocks.schema.into.wrappers

import zio.blocks.schema._
import zio.prelude.{Assertion => PreludeAssertion, _}
import zio.test._
import zio.test.Assertion._

// === Scala 3 Opaque Types (must be at package level) ===
opaque type OpaqueAge = Int
object OpaqueAge {
  def apply(value: Int): Either[String, OpaqueAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")

  def unsafe(value: Int): OpaqueAge = value

  extension (age: OpaqueAge) def toInt: Int = age
}

opaque type OpaqueName = String
object OpaqueName {
  def apply(value: String): Either[String, OpaqueName] =
    if (value.nonEmpty) Right(value)
    else Left("Name cannot be empty")

  def unsafe(value: String): OpaqueName = value

  extension (name: OpaqueName) def underlying: String = name
}

/**
 * Tests for direct Into conversions between primitives and wrapper types:
 *   - ZIO Prelude Newtype/Subtype
 *   - Scala 3 opaque types
 *   - AnyVal case classes (value classes)
 */
object PrimitiveToWrapperSpec extends ZIOSpecDefault {

  // === ZIO Prelude Newtypes ===
  object PreludeDomain {
    object Age extends Subtype[Int] {
      override def assertion: PreludeAssertion[Int] = PreludeAssertion.between(0, 150)
    }
    type Age = Age.Type

    object Name extends Newtype[String] {
      override def assertion: PreludeAssertion[String] = !PreludeAssertion.isEmptyString
    }
    type Name = Name.Type
  }

  // === AnyVal Case Classes ===
  case class AgeWrapper(value: Int)     extends AnyVal
  case class NameWrapper(value: String) extends AnyVal

  import PreludeDomain._
  import OpaqueAge.toInt
  import OpaqueName.underlying

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveToWrapperSpec")(
    suite("ZIO Prelude Newtype/Subtype")(
      suite("Into: Primitive -> Newtype")(
        test("Int -> Age (valid)") {
          val into   = Into.derived[Int, Age]
          val result = into.into(30)
          assertTrue(result.isRight)
        },
        test("Int -> Age (invalid - negative)") {
          val into   = Into.derived[Int, Age]
          val result = into.into(-5)
          assertTrue(result.isLeft)
        },
        test("Int -> Age (invalid - too high)") {
          val into   = Into.derived[Int, Age]
          val result = into.into(200)
          assertTrue(result.isLeft)
        },
        test("String -> Name (valid)") {
          val into   = Into.derived[String, Name]
          val result = into.into("Alice")
          assertTrue(result.isRight)
        },
        test("String -> Name (invalid - empty)") {
          val into   = Into.derived[String, Name]
          val result = into.into("")
          assertTrue(result.isLeft)
        }
      ),
      suite("Into: Newtype -> Primitive")(
        test("Age -> Int") {
          val into   = Into.derived[Age, Int]
          val age    = Age.make(30).toOption.get
          val result = into.into(age)
          assert(result)(isRight(equalTo(30)))
        },
        test("Name -> String") {
          val into   = Into.derived[Name, String]
          val name   = Name.make("Alice").toOption.get
          val result = into.into(name)
          assert(result)(isRight(equalTo("Alice")))
        }
      )
    ),
    suite("Scala 3 Opaque Types")(
      suite("Into: Primitive -> Opaque")(
        test("Int -> OpaqueAge (valid)") {
          val into   = Into.derived[Int, OpaqueAge]
          val result = into.into(30)
          assertTrue(
            result.isRight,
            result.map(_.toInt).getOrElse(0) == 30
          )
        },
        test("Int -> OpaqueAge (invalid - negative)") {
          val into   = Into.derived[Int, OpaqueAge]
          val result = into.into(-5)
          assertTrue(result.isLeft)
        },
        test("String -> OpaqueName (valid)") {
          val into   = Into.derived[String, OpaqueName]
          val result = into.into("Bob")
          assertTrue(
            result.isRight,
            result.map(_.underlying).getOrElse("") == "Bob"
          )
        },
        test("String -> OpaqueName (invalid - empty)") {
          val into   = Into.derived[String, OpaqueName]
          val result = into.into("")
          assertTrue(result.isLeft)
        }
      ),
      suite("Into: Opaque -> Primitive")(
        test("OpaqueAge -> Int") {
          val into   = Into.derived[OpaqueAge, Int]
          val age    = OpaqueAge.unsafe(30)
          val result = into.into(age)
          assert(result)(isRight(equalTo(30)))
        },
        test("OpaqueName -> String") {
          val into   = Into.derived[OpaqueName, String]
          val name   = OpaqueName.unsafe("Carol")
          val result = into.into(name)
          assert(result)(isRight(equalTo("Carol")))
        }
      )
    ),
    suite("AnyVal Case Classes")(
      suite("Into: Primitive -> AnyVal")(
        test("Int -> AgeWrapper") {
          val into   = Into.derived[Int, AgeWrapper]
          val result = into.into(30)
          assert(result)(isRight(equalTo(AgeWrapper(30))))
        },
        test("String -> NameWrapper") {
          val into   = Into.derived[String, NameWrapper]
          val result = into.into("Dave")
          assert(result)(isRight(equalTo(NameWrapper("Dave"))))
        }
      ),
      suite("Into: AnyVal -> Primitive")(
        test("AgeWrapper -> Int") {
          val into   = Into.derived[AgeWrapper, Int]
          val result = into.into(AgeWrapper(30))
          assert(result)(isRight(equalTo(30)))
        },
        test("NameWrapper -> String") {
          val into   = Into.derived[NameWrapper, String]
          val result = into.into(NameWrapper("Eve"))
          assert(result)(isRight(equalTo("Eve")))
        }
      )
    )
  )
}
