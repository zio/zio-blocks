package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

object LargeProductSpec extends ZIOSpecDefault {

  def spec = suite("LargeProductSpec")(
    suite("Large Case Classes (>22 fields)")(
      test("should convert large case class (25 fields) to copy") {
        // Case class with 25 fields (exceeds standard tuple limit of 22)
        case class Large25(
          f1: Int,
          f2: Int,
          f3: Int,
          f4: Int,
          f5: Int,
          f6: Int,
          f7: Int,
          f8: Int,
          f9: Int,
          f10: Int,
          f11: Int,
          f12: Int,
          f13: Int,
          f14: Int,
          f15: Int,
          f16: Int,
          f17: Int,
          f18: Int,
          f19: Int,
          f20: Int,
          f21: Int,
          f22: Int,
          f23: Int,
          f24: Int,
          f25: Int
        )

        case class Large25Copy(
          f1: Int,
          f2: Int,
          f3: Int,
          f4: Int,
          f5: Int,
          f6: Int,
          f7: Int,
          f8: Int,
          f9: Int,
          f10: Int,
          f11: Int,
          f12: Int,
          f13: Int,
          f14: Int,
          f15: Int,
          f16: Int,
          f17: Int,
          f18: Int,
          f19: Int,
          f20: Int,
          f21: Int,
          f22: Int,
          f23: Int,
          f24: Int,
          f25: Int
        )

        val derivation = Into.derived[Large25, Large25Copy]
        val input      = Large25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { large =>
          assertTrue(large.f1 == 1)
          assertTrue(large.f25 == 25)
          assertTrue(large.f13 == 13)
        }
      },
      test("should convert large case class to itself (identity)") {
        case class Large25(
          f1: Int,
          f2: Int,
          f3: Int,
          f4: Int,
          f5: Int,
          f6: Int,
          f7: Int,
          f8: Int,
          f9: Int,
          f10: Int,
          f11: Int,
          f12: Int,
          f13: Int,
          f14: Int,
          f15: Int,
          f16: Int,
          f17: Int,
          f18: Int,
          f19: Int,
          f20: Int,
          f21: Int,
          f22: Int,
          f23: Int,
          f24: Int,
          f25: Int
        )

        val derivation = Into.derived[Large25, Large25]
        val input      = Large25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { large =>
          assertTrue(large.f1 == 1)
          assertTrue(large.f25 == 25)
        }
      },
      test("should handle large case class with different field types") {
        case class LargeMixed(
          f1: Int,
          f2: String,
          f3: Boolean,
          f4: Long,
          f5: Double,
          f6: Int,
          f7: String,
          f8: Boolean,
          f9: Long,
          f10: Double,
          f11: Int,
          f12: String,
          f13: Boolean,
          f14: Long,
          f15: Double,
          f16: Int,
          f17: String,
          f18: Boolean,
          f19: Long,
          f20: Double,
          f21: Int,
          f22: String,
          f23: Boolean,
          f24: Long,
          f25: Double
        )

        case class LargeMixedCopy(
          f1: Int,
          f2: String,
          f3: Boolean,
          f4: Long,
          f5: Double,
          f6: Int,
          f7: String,
          f8: Boolean,
          f9: Long,
          f10: Double,
          f11: Int,
          f12: String,
          f13: Boolean,
          f14: Long,
          f15: Double,
          f16: Int,
          f17: String,
          f18: Boolean,
          f19: Long,
          f20: Double,
          f21: Int,
          f22: String,
          f23: Boolean,
          f24: Long,
          f25: Double
        )

        val derivation = Into.derived[LargeMixed, LargeMixedCopy]
        val input      = LargeMixed(
          1,
          "a",
          true,
          1L,
          1.0,
          2,
          "b",
          false,
          2L,
          2.0,
          3,
          "c",
          true,
          3L,
          3.0,
          4,
          "d",
          false,
          4L,
          4.0,
          5,
          "e",
          true,
          5L,
          5.0
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { large =>
          assertTrue(large.f1 == 1)
          assertTrue(large.f2 == "a")
          assertTrue(large.f3 == true)
          assertTrue(large.f25 == 5.0)
        }
      }
    )
  )
}
