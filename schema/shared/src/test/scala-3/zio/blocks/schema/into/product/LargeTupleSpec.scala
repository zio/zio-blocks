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

  // Case class with 25 fields
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

  // Case class with 30 fields of mixed types
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

  // Type aliases for large tuples
  type Tuple25 = (
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int,
    Int
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
      test("converts 25-field case class to 25-element tuple") {
        val source = Large25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[Large25, Tuple25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result == Right(
            (
              1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
            )
          )
        )
      },
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
    suite("Large Tuple to Case Class")(
      test("converts 25-element tuple to 25-field case class") {
        val source: Tuple25 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[Tuple25, Large25]
        val result = into.into(source)

        val expected = Large25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )

        assertTrue(result == Right(expected))
      },
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
      }
    ),
    suite("Large Tuple to Large Tuple")(
      test("converts 25-element tuple to identical 25-element tuple") {
        val source: Tuple25 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[Tuple25, Tuple25]
        val result = into.into(source)

        assertTrue(result == Right(source))
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
      }
    ),
    suite("Round Trip with Large Tuples")(
      test("case class -> tuple -> case class preserves data") {
        val original = Large25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val toTuple     = Into.derived[Large25, Tuple25]
        val toCaseClass = Into.derived[Tuple25, Large25]

        val roundTrip = for {
          tuple  <- toTuple.into(original)
          result <- toCaseClass.into(tuple)
        } yield result

        assertTrue(roundTrip == Right(original))
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
      }
    ),
    suite("Edge Cases")(
      test("converts case class with exactly 23 fields (just over 22 limit)") {
        case class Fields23(
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
          f23: Int
        )
        type Tuple23 = (
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int
        )

        val source = Fields23(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23
        )
        val into   = Into.derived[Fields23, Tuple23]
        val result = into.into(source)

        assertTrue(result.isRight)
      },
      test("handles all zeros in large tuple") {
        val source: Tuple25 = (
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )
        val into   = Into.derived[Tuple25, Large25]
        val result = into.into(source)

        val expected = Large25(
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        assertTrue(result == Right(expected))
      },
      test("handles negative values in large tuple") {
        val source = Large25(
          -1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -15, -16, -17, -18, -19, -20, -21, -22, -23, -24,
          -25
        )
        val into   = Into.derived[Large25, Tuple25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.productElement(0) == -1,
          result.toOption.get.productElement(24) == -25
        )
      }
    ),
    suite("Type Coercion with Large Tuples")(
      test("widens Int to Long in large case class to tuple conversion") {
        // Case class with 25 Int fields, target tuple with 25 Long fields
        case class IntFields25(
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
        type LongTuple25 = (
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long
        )

        val source = IntFields25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[IntFields25, LongTuple25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.productElement(0) == 1L,
          result.toOption.get.productElement(24) == 25L
        )
      },
      test("widens Int to Long in large tuple to case class conversion") {
        // Tuple with 25 Int elements, target case class with 25 Long fields
        type IntTuple25 = (
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int
        )
        case class LongFields25(
          f1: Long,
          f2: Long,
          f3: Long,
          f4: Long,
          f5: Long,
          f6: Long,
          f7: Long,
          f8: Long,
          f9: Long,
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

        val source: IntTuple25 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[IntTuple25, LongFields25]
        val result = into.into(source)

        val expected = LongFields25(
          1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L,
          25L
        )

        assertTrue(result == Right(expected))
      },
      test("widens Int to Long in large tuple to tuple conversion") {
        type IntTuple25 = (
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int
        )
        type LongTuple25 = (
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long,
          Long
        )

        val source: IntTuple25 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[IntTuple25, LongTuple25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.productElement(0) == 1L,
          result.toOption.get.productElement(12) == 13L,
          result.toOption.get.productElement(24) == 25L
        )
      },
      test("widens mixed types in large tuple") {
        // Mix of Byte, Short, Int widening to Int, Long, Long
        case class MixedSmall25(
          f1: Byte,
          f2: Short,
          f3: Int,
          f4: Byte,
          f5: Short,
          f6: Int,
          f7: Byte,
          f8: Short,
          f9: Int,
          f10: Byte,
          f11: Short,
          f12: Int,
          f13: Byte,
          f14: Short,
          f15: Int,
          f16: Byte,
          f17: Short,
          f18: Int,
          f19: Byte,
          f20: Short,
          f21: Int,
          f22: Byte,
          f23: Short,
          f24: Int,
          f25: Byte
        )
        case class MixedLarge25(
          f1: Int,
          f2: Int,
          f3: Long,
          f4: Int,
          f5: Int,
          f6: Long,
          f7: Int,
          f8: Int,
          f9: Long,
          f10: Int,
          f11: Int,
          f12: Long,
          f13: Int,
          f14: Int,
          f15: Long,
          f16: Int,
          f17: Int,
          f18: Long,
          f19: Int,
          f20: Int,
          f21: Long,
          f22: Int,
          f23: Int,
          f24: Long,
          f25: Int
        )

        val source = MixedSmall25(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        )
        val into   = Into.derived[MixedSmall25, MixedLarge25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.f1 == 1,
          result.toOption.get.f3 == 3L,
          result.toOption.get.f25 == 25
        )
      },
      test("uses implicit Into instance for large tuple element conversion") {
        case class Wrapper(value: Int)
        case class WrappedFields25(
          f1: Wrapper,
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
        case class UnwrappedFields25(
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

        // Provide an implicit Into[Wrapper, Int]
        given Into[Wrapper, Int] with {
          def into(w: Wrapper): Either[SchemaError, Int] = Right(w.value)
        }

        val source = WrappedFields25(
          Wrapper(100),
          2,
          3,
          4,
          5,
          6,
          7,
          8,
          9,
          10,
          11,
          12,
          13,
          14,
          15,
          16,
          17,
          18,
          19,
          20,
          21,
          22,
          23,
          24,
          25
        )
        val into   = Into.derived[WrappedFields25, UnwrappedFields25]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.f1 == 100,
          result.toOption.get.f25 == 25
        )
      },
      test("implicit Into failure propagates in large tuple conversion") {
        case class Wrapper(value: Int)
        case class WrappedFields25(
          f1: Wrapper,
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
        case class UnwrappedFields25(
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

        // Provide an implicit Into[Wrapper, Int] that fails for negative values
        given Into[Wrapper, Int] with {
          def into(w: Wrapper): Either[SchemaError, Int] =
            if (w.value >= 0) Right(w.value)
            else Left(SchemaError.conversionFailed(Nil, "Wrapper value must be non-negative"))
        }

        val source = WrappedFields25(
          Wrapper(-1),
          2,
          3,
          4,
          5,
          6,
          7,
          8,
          9,
          10,
          11,
          12,
          13,
          14,
          15,
          16,
          17,
          18,
          19,
          20,
          21,
          22,
          23,
          24,
          25
        )
        val into   = Into.derived[WrappedFields25, UnwrappedFields25]
        val result = into.into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("Nested Large Tuples")(
      test("converts case class with nested large tuple field") {
        case class Outer(name: String, data: Tuple25)
        case class OuterB(name: String, data: Large25)

        given Into[Tuple25, Large25] = Into.derived[Tuple25, Large25]

        val source = Outer(
          "test",
          (
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
          )
        )
        val into   = Into.derived[Outer, OuterB]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.name == "test",
          result.toOption.get.data.f1 == 1,
          result.toOption.get.data.f25 == 25
        )
      },
      test("converts case class with large tuple to case class with nested case class") {
        case class SourceOuter(id: Int, values: Tuple25)
        case class TargetOuter(id: Int, values: Large25)

        given Into[Tuple25, Large25] = Into.derived[Tuple25, Large25]

        val source = SourceOuter(
          42,
          (
            10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230,
            240, 250
          )
        )
        val into   = Into.derived[SourceOuter, TargetOuter]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.id == 42,
          result.toOption.get.values.f1 == 10,
          result.toOption.get.values.f25 == 250
        )
      },
      test("converts deeply nested structure with large tuple") {
        case class Level3(data: Tuple25)
        case class Level2(inner: Level3, extra: Int)
        case class Level1(nested: Level2, name: String)

        case class Level3B(data: Large25)
        case class Level2B(inner: Level3B, extra: Int)
        case class Level1B(nested: Level2B, name: String)

        given Into[Tuple25, Large25] = Into.derived[Tuple25, Large25]
        given Into[Level3, Level3B]  = Into.derived[Level3, Level3B]
        given Into[Level2, Level2B]  = Into.derived[Level2, Level2B]

        val source = Level1(
          Level2(
            Level3(
              (
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
              )
            ),
            999
          ),
          "deep"
        )
        val into   = Into.derived[Level1, Level1B]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.name == "deep",
          result.toOption.get.nested.extra == 999,
          result.toOption.get.nested.inner.data.f1 == 1,
          result.toOption.get.nested.inner.data.f25 == 25
        )
      },
      test("converts large case class nested inside another case class") {
        case class Container(header: String, body: Large25, footer: Int)
        case class ContainerB(header: String, body: Tuple25, footer: Int)

        given Into[Large25, Tuple25] = Into.derived[Large25, Tuple25]

        val source = Container(
          "header",
          Large25(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
          ),
          42
        )
        val into   = Into.derived[Container, ContainerB]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.header == "header",
          result.toOption.get.body.productElement(0) == 1,
          result.toOption.get.body.productElement(24) == 25,
          result.toOption.get.footer == 42
        )
      },
      test("converts optional large tuple field") {
        case class WithOptionalTuple(id: Int, data: Option[Tuple25])
        case class WithOptionalCaseClass(id: Int, data: Option[Large25])

        given Into[Tuple25, Large25] = Into.derived[Tuple25, Large25]

        val sourceWithSome = WithOptionalTuple(
          1,
          Some(
            (
              1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
            )
          )
        )
        val sourceWithNone = WithOptionalTuple(2, None)

        val into       = Into.derived[WithOptionalTuple, WithOptionalCaseClass]
        val resultSome = into.into(sourceWithSome)
        val resultNone = into.into(sourceWithNone)

        assertTrue(
          resultSome.isRight,
          resultSome.toOption.get.data.isDefined,
          resultSome.toOption.get.data.get.f1 == 1,
          resultNone.isRight,
          resultNone.toOption.get.data.isEmpty
        )
      },
      test("converts list of large tuples to list of case classes") {
        case class WrapperA(items: List[Tuple25])
        case class WrapperB(items: List[Large25])

        given Into[Tuple25, Large25] = Into.derived[Tuple25, Large25]

        val source = WrapperA(
          List(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25),
            (10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230,
              240, 250)
          )
        )
        val into   = Into.derived[WrapperA, WrapperB]
        val result = into.into(source)

        assertTrue(
          result.isRight,
          result.toOption.get.items.length == 2,
          result.toOption.get.items(0).f1 == 1,
          result.toOption.get.items(1).f1 == 10
        )
      }
    ),
    suite("Large Tuple Type Coercion in Complex Structures")(
      test("converts Either with large tuples on both sides") {
        case class LeftData(errors: Tuple25)
        case class RightData(success: Large25)

        case class LeftDataB(errors: Large25)
        case class RightDataB(success: Tuple25)

        case class ContainerA(result: Either[LeftData, RightData])
        case class ContainerB(result: Either[LeftDataB, RightDataB])

        given Into[Tuple25, Large25]      = Into.derived[Tuple25, Large25]
        given Into[Large25, Tuple25]      = Into.derived[Large25, Tuple25]
        given Into[LeftData, LeftDataB]   = Into.derived[LeftData, LeftDataB]
        given Into[RightData, RightDataB] = Into.derived[RightData, RightDataB]

        val sourceLeft = ContainerA(
          Left(
            LeftData(
              (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
            )
          )
        )
        val sourceRight = ContainerA(
          Right(
            RightData(
              Large25(
                10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220,
                230, 240, 250
              )
            )
          )
        )

        val into = Into.derived[ContainerA, ContainerB]

        val resultLeft  = into.into(sourceLeft)
        val resultRight = into.into(sourceRight)

        assertTrue(
          resultLeft.isRight,
          resultLeft.toOption.get.result.isLeft,
          resultLeft.toOption.get.result.swap.toOption.get.errors.f1 == 1,
          resultRight.isRight,
          resultRight.toOption.get.result.isRight,
          resultRight.toOption.get.result.toOption.get.success.productElement(0) == 10
        )
      }
    )
  )
}
