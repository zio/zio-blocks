package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 large tuples (>22 elements).
 *
 * Scala 3 supports tuples of any size using the *: cons syntax. This spec tests
 * conversions between:
 *   - Large tuples and case classes
 *   - Large case classes and tuples
 *   - Large tuples to large tuples
 */
object LargeTupleSpec extends ZIOSpecDefault {

  case class Mixed30(
    s1: String,
    i1: Int,
    s2: String,
    i2: Int,
    s3: String,
    i3: Int,
    s4: String,
    i4: Int,
    s5: String,
    i5: Int,
    s6: String,
    i6: Int,
    s7: String,
    i7: Int,
    s8: String,
    i8: Int,
    s9: String,
    i9: Int,
    s10: String,
    i10: Int,
    s11: String,
    i11: Int,
    s12: String,
    i12: Int,
    s13: String,
    i13: Int,
    s14: String,
    i14: Int,
    s15: String,
    i15: Int
  )

  type MixedTuple30 = (
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int,
    String,
    Int
  )

  def spec = suite("LargeTupleSpec")(
    suite("Case Class to Large Tuple")(
      test("converts 30-field mixed case class to 30-element tuple") {
        val source = Mixed30(
          "a",
          1,
          "b",
          2,
          "c",
          3,
          "d",
          4,
          "e",
          5,
          "f",
          6,
          "g",
          7,
          "h",
          8,
          "i",
          9,
          "j",
          10,
          "k",
          11,
          "l",
          12,
          "m",
          13,
          "n",
          14,
          "o",
          15
        )
        val into   = Into.derived[Mixed30, MixedTuple30]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.productElement(0) == "a",
          result.toOption.get.productElement(1) == 1,
          result.toOption.get.productElement(28) == "o",
          result.toOption.get.productElement(29) == 15
        )
      }
    ),
    test("converts 30-element mixed tuple to 30-field case class") {
      val source: MixedTuple30 = (
        "a",
        1,
        "b",
        2,
        "c",
        3,
        "d",
        4,
        "e",
        5,
        "f",
        6,
        "g",
        7,
        "h",
        8,
        "i",
        9,
        "j",
        10,
        "k",
        11,
        "l",
        12,
        "m",
        13,
        "n",
        14,
        "o",
        15
      )
      val into   = Into.derived[MixedTuple30, Mixed30]
      val result = into.into(source)

      assertTrue(
        result.isRight,
        result.toOption.get.s1 == "a",
        result.toOption.get.i1 == 1,
        result.toOption.get.s15 == "o",
        result.toOption.get.i15 == 15
      )
    },
    test("converts 30-element mixed tuple to identical tuple") {
      val source: MixedTuple30 = (
        "a",
        1,
        "b",
        2,
        "c",
        3,
        "d",
        4,
        "e",
        5,
        "f",
        6,
        "g",
        7,
        "h",
        8,
        "i",
        9,
        "j",
        10,
        "k",
        11,
        "l",
        12,
        "m",
        13,
        "n",
        14,
        "o",
        15
      )
      val into   = Into.derived[MixedTuple30, MixedTuple30]
      val result = into.into(source)

      assertTrue(result == Right(source))
    },
    test("mixed type round trip preserves data") {
      val original = Mixed30(
        "a",
        1,
        "b",
        2,
        "c",
        3,
        "d",
        4,
        "e",
        5,
        "f",
        6,
        "g",
        7,
        "h",
        8,
        "i",
        9,
        "j",
        10,
        "k",
        11,
        "l",
        12,
        "m",
        13,
        "n",
        14,
        "o",
        15
      )
      val toTuple     = Into.derived[Mixed30, MixedTuple30]
      val toCaseClass = Into.derived[MixedTuple30, Mixed30]

      val roundTrip = for {
        tuple  <- toTuple.into(original)
        result <- toCaseClass.into(tuple)
      } yield result

      assertTrue(roundTrip == Right(original))
    },
    suite("Nested Large Tuples")(
      test("converts case class with nested large tuple field") {
        case class Outer(name: String, data: MixedTuple30)
        case class OuterB(name: String, data: Mixed30)

        given Into[MixedTuple30, Mixed30] = Into.derived[MixedTuple30, Mixed30]

        val source = Outer(
          "test",
          (
            "a",
            1,
            "b",
            2,
            "c",
            3,
            "d",
            4,
            "e",
            5,
            "f",
            6,
            "g",
            7,
            "h",
            8,
            "i",
            9,
            "j",
            10,
            "k",
            11,
            "l",
            12,
            "m",
            13,
            "n",
            14,
            "o",
            15
          )
        )
        val into   = Into.derived[Outer, OuterB]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.name == "test",
          result.toOption.get.data.s1 == "a",
          result.toOption.get.data.i15 == 15
        )
      }
    )
  )
}
