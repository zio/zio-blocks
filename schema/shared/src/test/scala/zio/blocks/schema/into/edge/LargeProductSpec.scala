package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for large product conversions (22+ fields to exceed Scala 2 tuple
 * limit).
 */
object LargeProductSpec extends ZIOSpecDefault {

  // 10-field case classes (original tests)
  case class Large10A(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int
  )
  case class Large10B(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int
  )
  case class Large10L(
    f01: Long,
    f02: Long,
    f03: Long,
    f04: Long,
    f05: Long,
    f06: Long,
    f07: Long,
    f08: Long,
    f09: Long,
    f10: Long
  )

  // 22-field case classes (boundary case - exceeds Scala 2's tuple limit of 22)
  case class Large22A(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
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
    f22: Int
  )
  case class Large22B(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
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
    f22: Int
  )
  case class Large22L(
    f01: Long,
    f02: Long,
    f03: Long,
    f04: Long,
    f05: Long,
    f06: Long,
    f07: Long,
    f08: Long,
    f09: Long,
    f10: Long,
    f11: Long,
    f12: Long,
    f13: Long,
    f14: Long,
    f15: Long,
    f16: Long,
    f17: Long,
    f18: Long,
    f19: Long,
    f20: Long,
    f21: Long,
    f22: Long
  )

  // 25-field case classes (comfortably beyond tuple limit)
  case class Large25A(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
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
  case class Large25B(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
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
  case class Large25L(
    f01: Long,
    f02: Long,
    f03: Long,
    f04: Long,
    f05: Long,
    f06: Long,
    f07: Long,
    f08: Long,
    f09: Long,
    f10: Long,
    f11: Long,
    f12: Long,
    f13: Long,
    f14: Long,
    f15: Long,
    f16: Long,
    f17: Long,
    f18: Long,
    f19: Long,
    f20: Long,
    f21: Long,
    f22: Long,
    f23: Long,
    f24: Long,
    f25: Long
  )

  def spec: Spec[TestEnvironment, Any] = suite("LargeProductSpec")(
    suite("10-field case classes")(
      test("case class with 10 fields - same types") {
        val source = Large10A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10A, Large10B].into(source)
        assert(result)(isRight(equalTo(Large10B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))))
      },
      test("case class with 10 fields - with coercion") {
        val source = Large10A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10A, Large10L].into(source)
        assert(result)(isRight(equalTo(Large10L(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))))
      }
    ),
    suite("22-field case classes (boundary case)")(
      test("case class with 22 fields - same types") {
        val source = Large22A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val result = Into.derived[Large22A, Large22B].into(source)
        assert(result)(
          isRight(equalTo(Large22B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)))
        )
      },
      test("case class with 22 fields - with coercion Int to Long") {
        val source = Large22A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val result = Into.derived[Large22A, Large22L].into(source)
        assert(result)(
          isRight(
            equalTo(
              Large22L(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L,
                22L)
            )
          )
        )
      }
    ),
    suite("25-field case classes (beyond tuple limit)")(
      test("case class with 25 fields - same types") {
        val source =
          Large25A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val result = Into.derived[Large25A, Large25B].into(source)
        assert(result)(
          isRight(
            equalTo(Large25B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25))
          )
        )
      },
      test("case class with 25 fields - with coercion Int to Long") {
        val source =
          Large25A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val result = Into.derived[Large25A, Large25L].into(source)
        assert(result)(
          isRight(
            equalTo(
              Large25L(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L,
                22L, 23L, 24L, 25L)
            )
          )
        )
      }
    )
  )
}
