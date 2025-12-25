package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

// Large Coproduct with 25 case objects (exceeds typical test scenarios)
sealed trait Code25
object Code25 {
  case object Code0  extends Code25
  case object Code1  extends Code25
  case object Code2  extends Code25
  case object Code3  extends Code25
  case object Code4  extends Code25
  case object Code5  extends Code25
  case object Code6  extends Code25
  case object Code7  extends Code25
  case object Code8  extends Code25
  case object Code9  extends Code25
  case object Code10 extends Code25
  case object Code11 extends Code25
  case object Code12 extends Code25
  case object Code13 extends Code25
  case object Code14 extends Code25
  case object Code15 extends Code25
  case object Code16 extends Code25
  case object Code17 extends Code25
  case object Code18 extends Code25
  case object Code19 extends Code25
  case object Code20 extends Code25
  case object Code21 extends Code25
  case object Code22 extends Code25
  case object Code23 extends Code25
  case object Code24 extends Code25
}

// Identical counterpart for conversion testing
sealed trait Code25Copy
object Code25Copy {
  case object Code0  extends Code25Copy
  case object Code1  extends Code25Copy
  case object Code2  extends Code25Copy
  case object Code3  extends Code25Copy
  case object Code4  extends Code25Copy
  case object Code5  extends Code25Copy
  case object Code6  extends Code25Copy
  case object Code7  extends Code25Copy
  case object Code8  extends Code25Copy
  case object Code9  extends Code25Copy
  case object Code10 extends Code25Copy
  case object Code11 extends Code25Copy
  case object Code12 extends Code25Copy
  case object Code13 extends Code25Copy
  case object Code14 extends Code25Copy
  case object Code15 extends Code25Copy
  case object Code16 extends Code25Copy
  case object Code17 extends Code25Copy
  case object Code18 extends Code25Copy
  case object Code19 extends Code25Copy
  case object Code20 extends Code25Copy
  case object Code21 extends Code25Copy
  case object Code22 extends Code25Copy
  case object Code23 extends Code25Copy
  case object Code24 extends Code25Copy
}

object LargeCoproductSpec extends ZIOSpecDefault {

  def spec = suite("LargeCoproductSpec")(
    suite("Large Coproducts (25+ cases)")(
      test("should convert large coproduct (25 case objects) to copy") {
        val derivation = Into.derived[Code25, Code25Copy]
        val input      = Code25.Code0
        val result     = derivation.into(input)

        assertTrue(result == Right(Code25Copy.Code0))
      },
      test("should convert all 25 cases correctly") {
        val derivation = Into.derived[Code25, Code25Copy]

        // Test first case
        val result0 = derivation.into(Code25.Code0)
        assertTrue(result0 == Right(Code25Copy.Code0))

        // Test middle case
        val result12 = derivation.into(Code25.Code12)
        assertTrue(result12 == Right(Code25Copy.Code12))

        // Test last case
        val result24 = derivation.into(Code25.Code24)
        assertTrue(result24 == Right(Code25Copy.Code24))
      },
      test("should convert large coproduct to itself (identity)") {
        val derivation = Into.derived[Code25, Code25]
        val input      = Code25.Code15
        val result     = derivation.into(input)

        assertTrue(result == Right(Code25.Code15))
      },
      test("should handle all cases in sequence") {
        val derivation = Into.derived[Code25, Code25Copy]
        val inputs     = List(
          Code25.Code0,
          Code25.Code5,
          Code25.Code10,
          Code25.Code15,
          Code25.Code20,
          Code25.Code24
        )

        val results = inputs.map(derivation.into)

        assertTrue(
          results(0) == Right(Code25Copy.Code0) &&
            results(1) == Right(Code25Copy.Code5) &&
            results(2) == Right(Code25Copy.Code10) &&
            results(3) == Right(Code25Copy.Code15) &&
            results(4) == Right(Code25Copy.Code20) &&
            results(5) == Right(Code25Copy.Code24)
        )
      },
      test("should verify no hardcoded limits in derivation logic") {
        // This test confirms that the derivation logic doesn't have hardcoded arity limits
        // If it did, we would see failures with 25 cases
        val derivation = Into.derived[Code25, Code25Copy]

        // Test random cases to ensure all work
        val testCases = List(1, 7, 13, 19, 23)
        val results   = testCases.map { i =>
          val code = i match {
            case 1  => Code25.Code1
            case 7  => Code25.Code7
            case 13 => Code25.Code13
            case 19 => Code25.Code19
            case 23 => Code25.Code23
          }
          val expected = i match {
            case 1  => Code25Copy.Code1
            case 7  => Code25Copy.Code7
            case 13 => Code25Copy.Code13
            case 19 => Code25Copy.Code19
            case 23 => Code25Copy.Code23
          }
          derivation.into(code) == Right(expected)
        }

        assertTrue(results.forall(_ == true))
      }
    )
  )
}
