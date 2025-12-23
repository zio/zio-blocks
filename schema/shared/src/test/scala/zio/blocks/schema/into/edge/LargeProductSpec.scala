package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for large product (case class with many fields) conversions.
 *
 * Covers:
 *   - Case classes with 20+ fields
 *   - Field matching strategies with many fields
 *   - Type coercion across many fields
 *   - Optional fields mixed with required fields
 */
object LargeProductSpec extends ZIOSpecDefault {

  // === Large case class with 22 fields ===
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

  // === Large case class with type coercion ===
  case class Large22Widened(
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

  // === Large case class with mixed types ===
  case class LargeMixedA(
    id: Long,
    name: String,
    active: Boolean,
    count: Int,
    score: Double,
    tag: String,
    level: Int,
    rate: Float,
    enabled: Boolean,
    value: Int,
    label: String,
    index: Int,
    factor: Double,
    flag: Boolean,
    amount: Int,
    title: String,
    rank: Int,
    ratio: Float,
    status: Boolean,
    total: Int
  )

  case class LargeMixedB(
    id: Long,
    name: String,
    active: Boolean,
    count: Long,
    score: Double,
    tag: String,
    level: Long,
    rate: Double,
    enabled: Boolean,
    value: Long,
    label: String,
    index: Long,
    factor: Double,
    flag: Boolean,
    amount: Long,
    title: String,
    rank: Long,
    ratio: Double,
    status: Boolean,
    total: Long
  )

  // === Large with optional fields ===
  case class Large10Required(
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

  case class Large10WithOptional(
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
    opt1: Option[String],
    opt2: Option[Int],
    opt3: Option[Boolean]
  )

  // === Large with defaults ===
  case class Large10WithDefaults(
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
    extra1: String = "default1",
    extra2: Int = 0,
    extra3: Boolean = true
  )

  // === Large with reordered fields ===
  case class Large10Ordered(
    a: Int,
    b: Int,
    c: Int,
    d: Int,
    e: Int,
    f: Int,
    g: Int,
    h: Int,
    i: Int,
    j: Int
  )

  case class Large10Reordered(
    j: Int,
    i: Int,
    h: Int,
    g: Int,
    f: Int,
    e: Int,
    d: Int,
    c: Int,
    b: Int,
    a: Int
  )

  def spec: Spec[TestEnvironment, Any] = suite("LargeProductSpec")(
    suite("22-Field Case Class")(
      test("converts 22-field case class with same types") {
        val source = Large22A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val result = Into.derived[Large22A, Large22B].into(source)

        assert(result)(
          isRight(equalTo(Large22B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)))
        )
      },
      test("widens all 22 Int fields to Long") {
        val source = Large22A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val result = Into.derived[Large22A, Large22Widened].into(source)

        assert(result)(
          isRight(
            equalTo(
              Large22Widened(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L,
                21L, 22L)
            )
          )
        )
      }
    ),
    suite("20-Field Mixed Types")(
      test("converts 20-field case class with mixed types and coercion") {
        val source = LargeMixedA(
          1L,
          "name",
          true,
          10,
          3.14,
          "tag",
          5,
          2.5f,
          false,
          100,
          "label",
          7,
          6.28,
          true,
          200,
          "title",
          9,
          1.5f,
          false,
          300
        )
        val result = Into.derived[LargeMixedA, LargeMixedB].into(source)

        assert(result)(
          isRight(
            equalTo(
              LargeMixedB(
                1L,
                "name",
                true,
                10L,
                3.14,
                "tag",
                5L,
                2.5,
                false,
                100L,
                "label",
                7L,
                6.28,
                true,
                200L,
                "title",
                9L,
                1.5f.toDouble,
                false,
                300L
              )
            )
          )
        )
      }
    ),
    suite("Large with Optional Fields")(
      test("adds optional fields to large product") {
        val source = Large10Required(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10Required, Large10WithOptional].into(source)

        assert(result)(isRight(equalTo(Large10WithOptional(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, None, None, None))))
      }
    ),
    suite("Large with Default Values")(
      test("uses default values for missing fields in large product") {
        val source = Large10Required(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10Required, Large10WithDefaults].into(source)

        assert(result)(isRight(equalTo(Large10WithDefaults(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, "default1", 0, true))))
      }
    ),
    suite("Large with Reordered Fields")(
      test("matches fields by name in large product regardless of order") {
        val source = Large10Ordered(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10Ordered, Large10Reordered].into(source)

        // Fields matched by name: a->a, b->b, etc.
        assert(result)(isRight(equalTo(Large10Reordered(10, 9, 8, 7, 6, 5, 4, 3, 2, 1))))
      }
    ),
    suite("Identity on Large Products")(
      test("converts large product to itself") {
        val source = Large22A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val result = Into.derived[Large22A, Large22A].into(source)

        assert(result)(isRight(equalTo(source)))
      }
    ),
    suite("Dropping Fields in Large Product")(
      test("converts large product to smaller product") {
        val source = Large10WithOptional(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, Some("ignored"), Some(99), Some(true))
        val result = Into.derived[Large10WithOptional, Large10Required].into(source)

        assert(result)(isRight(equalTo(Large10Required(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))))
      }
    )
  )
}
