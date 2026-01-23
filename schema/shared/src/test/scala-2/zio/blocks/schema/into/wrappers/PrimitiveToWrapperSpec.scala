package zio.blocks.schema.into.wrappers

import zio.blocks.schema._
import zio.prelude._
import zio.test._
import zio.test.Assertion._

/**
 * Scala 2 tests for direct Into conversions between primitives and wrapper
 * types:
 *   - ZIO Prelude Newtype/Subtype
 *   - AnyVal case classes (value classes)
 *
 * Note: Opaque types are Scala 3 only.
 */
object PrimitiveToWrapperSpec extends ZIOSpecDefault {

  // === ZIO Prelude Newtypes ===
  object PreludeDomain {
    object Age extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type Age = Age.Type

    object Name extends Newtype[String] {
      override def assertion = assert(!zio.prelude.Assertion.isEmptyString)
    }
    type Name = Name.Type
  }

  // === AnyVal Case Classes ===
  case class AgeWrapper(value: Int)     extends AnyVal
  case class NameWrapper(value: String) extends AnyVal

  import PreludeDomain._

  def spec = suite("PrimitiveToWrapperSpec")(
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
