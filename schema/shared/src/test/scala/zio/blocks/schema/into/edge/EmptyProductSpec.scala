package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for empty product (case class with no fields) conversions.
 *
 * Covers:
 *   - Empty case class to empty case class
 *   - Empty case class to/from case class with optional fields
 *   - Empty case class to/from case class with default values
 *   - Case object conversions
 */
object EmptyProductSpec extends ZIOSpecDefault {

  // === Empty Case Classes ===
  case class EmptyA()
  case class EmptyB()
  case class EmptyC()

  // === Case Objects ===
  case object EmptyObjectA
  case object EmptyObjectB

  // === Target types with optional/default fields ===
  case class WithOptional(name: Option[String], age: Option[Int])
  case class WithDefaults(name: String = "default", count: Int = 0)
  case class WithMixed(name: String = "unnamed", extra: Option[String])
  case class WithAllTypes(
    str: Option[String],
    num: Option[Int],
    flag: Option[Boolean],
    decimal: Option[Double]
  )

  def spec: Spec[TestEnvironment, Any] = suite("EmptyProductSpec")(
    suite("Empty Case Class to Empty Case Class")(
      test("converts empty case class to another empty case class") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, EmptyB].into(source)

        assert(result)(isRight(equalTo(EmptyB())))
      },
      test("converts empty case class to itself") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("chain of empty case class conversions") {
        val source = EmptyA()
        val intoAB = Into.derived[EmptyA, EmptyB]
        val intoBC = Into.derived[EmptyB, EmptyC]

        val resultAB = intoAB.into(source)
        val resultBC = resultAB.flatMap(b => intoBC.into(b))

        assert(resultBC)(isRight(equalTo(EmptyC())))
      }
    ),
    suite("Case Object to Case Object")(
      test("converts case object to another case object") {
        val result = Into.derived[EmptyObjectA.type, EmptyObjectB.type].into(EmptyObjectA)

        assert(result)(isRight(equalTo(EmptyObjectB)))
      },
      test("converts case object to itself") {
        val result = Into.derived[EmptyObjectA.type, EmptyObjectA.type].into(EmptyObjectA)

        assert(result)(isRight(equalTo(EmptyObjectA)))
      }
    ),
    suite("Case Object to Empty Case Class")(
      test("converts case object to empty case class") {
        val result = Into.derived[EmptyObjectA.type, EmptyA].into(EmptyObjectA)

        assert(result)(isRight(equalTo(EmptyA())))
      }
    ),
    suite("Empty Case Class to Case Object")(
      test("converts empty case class to case object") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, EmptyObjectA.type].into(source)

        assert(result)(isRight(equalTo(EmptyObjectA)))
      }
    ),
    suite("Empty to Optional Fields")(
      test("empty case class converts to all-optional with None values") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithOptional].into(source)

        assert(result)(isRight(equalTo(WithOptional(None, None))))
      },
      test("case object converts to all-optional with None values") {
        val result = Into.derived[EmptyObjectA.type, WithOptional].into(EmptyObjectA)

        assert(result)(isRight(equalTo(WithOptional(None, None))))
      },
      test("empty converts to multiple optional types") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithAllTypes].into(source)

        assert(result)(isRight(equalTo(WithAllTypes(None, None, None, None))))
      }
    ),
    suite("Empty to Default Values")(
      test("empty case class converts using all defaults") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithDefaults].into(source)

        assert(result)(isRight(equalTo(WithDefaults("default", 0))))
      },
      test("case object converts using all defaults") {
        val result = Into.derived[EmptyObjectA.type, WithDefaults].into(EmptyObjectA)

        assert(result)(isRight(equalTo(WithDefaults("default", 0))))
      }
    ),
    suite("Empty to Mixed Optional and Default")(
      test("empty case class converts with mixed optional/default") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithMixed].into(source)

        assert(result)(isRight(equalTo(WithMixed("unnamed", None))))
      },
      test("case object converts with mixed optional/default") {
        val result = Into.derived[EmptyObjectA.type, WithMixed].into(EmptyObjectA)

        assert(result)(isRight(equalTo(WithMixed("unnamed", None))))
      }
    ),
    suite("From Optional/Default to Empty")(
      test("all-optional with None converts to empty case class") {
        val source = WithOptional(None, None)
        val result = Into.derived[WithOptional, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("all-optional with Some values drops them when converting to empty") {
        val source = WithOptional(Some("Alice"), Some(30))
        val result = Into.derived[WithOptional, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("all-optional converts to case object") {
        val source = WithOptional(None, None)
        val result = Into.derived[WithOptional, EmptyObjectA.type].into(source)

        assert(result)(isRight(equalTo(EmptyObjectA)))
      },
      test("with-defaults converts to empty case class") {
        val source = WithDefaults("test", 42)
        val result = Into.derived[WithDefaults, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("with-defaults converts to case object") {
        val source = WithDefaults("test", 42)
        val result = Into.derived[WithDefaults, EmptyObjectA.type].into(source)

        assert(result)(isRight(equalTo(EmptyObjectA)))
      }
    )
  )
}
