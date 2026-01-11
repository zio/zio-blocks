package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for large product conversions. */
object LargeProductSpec extends ZIOSpecDefault {

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

  def spec: Spec[TestEnvironment, Any] = suite("LargeProductSpec")(
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
  )
}
