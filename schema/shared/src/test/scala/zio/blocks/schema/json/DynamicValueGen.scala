package zio.blocks.schema.json

import zio.blocks.schema.DynamicValue.{Primitive, Record, Sequence, Variant}
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test.Gen
import java.time._
import scala.util.Try

object DynamicValueGen {
  protected def genPrimitiveValue: Gen[Any, PrimitiveValue] = {
    val genDuration = Gen.oneOf(
      Gen.long(Long.MinValue / 86400, Long.MaxValue / 86400).map(Duration.ofDays),
      Gen.long(Long.MinValue / 3600, Long.MaxValue / 3600).map(Duration.ofHours),
      Gen.long(Long.MinValue / 60, Long.MaxValue / 60).map(Duration.ofMinutes),
      Gen.long(Long.MinValue, Long.MaxValue).map(Duration.ofSeconds)
    )
    val genYear = Gen.oneOf(Gen.int(-9999, 9999), Gen.int(-999999999, 999999999)).map(Year.of)
    val genInstant = for {
      epochSecond    <- Gen.long(Instant.MIN.getEpochSecond, Instant.MAX.getEpochSecond)
      nanoAdjustment <- Gen.long(Long.MinValue, Long.MaxValue)
    } yield Try(Instant.ofEpochSecond(epochSecond, nanoAdjustment)).getOrElse(Instant.EPOCH)
    val genLocalDate = for {
      year  <- genYear
      month <- Gen.int(1, 12)
      day   <- Gen.int(1, Month.of(month).length(year.isLeap))
    } yield LocalDate.of(year.getValue, month, day)
    val genLocalTime = for {
      hour   <- Gen.int(0, 23)
      minute <- Gen.int(0, 59)
      second <- Gen.int(0, 59)
      nano   <- Gen.oneOf(Gen.int(0, 999999999), Gen.int(0, 999999).map(_ * 1000), Gen.int(0, 999).map(_ * 1000000))
    } yield LocalTime.of(hour, minute, second, nano)
    val genLocalDateTime = for {
      localDate <- genLocalDate
      localTime <- genLocalTime
    } yield LocalDateTime.of(localDate, localTime)
    val genMonth = for {
      month <- Gen.int(1, 12)
    } yield Month.of(month)
    val genMonthDay = for {
      month <- Gen.int(1, 12)
      day   <- Gen.int(1, 29)
    } yield MonthDay.of(month, day)
    Gen.oneOf(
      Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
      Gen.int.map(PrimitiveValue.Int.apply),
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.byte.map(PrimitiveValue.Byte.apply),
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.double.map(PrimitiveValue.Double.apply),
      Gen.float.map(PrimitiveValue.Float.apply),
      Gen.long.map(PrimitiveValue.Long.apply),
      Gen.short.map(PrimitiveValue.Short.apply),
      Gen.char.map(PrimitiveValue.Char.apply),
      Gen.bigInt(BigInt(0), BigInt(1000000000)).map(PrimitiveValue.BigInt.apply),
      Gen.bigDecimal(BigDecimal(0), BigDecimal(1000000000)).map(PrimitiveValue.BigDecimal.apply),
      Gen.int(1, 7).map(x => PrimitiveValue.DayOfWeek(DayOfWeek.of(x))),
      genDuration.map(PrimitiveValue.Duration.apply),
      genInstant.map(PrimitiveValue.Instant.apply),
      genLocalDate.map(PrimitiveValue.LocalDate.apply),
      genLocalDateTime.map(PrimitiveValue.LocalDateTime.apply),
      genLocalTime.map(PrimitiveValue.LocalTime.apply),
      genMonth.map(PrimitiveValue.Month.apply),
      genMonthDay.map(PrimitiveValue.MonthDay.apply)
    )
  }

  // Depth-limited generators for Scala Native compatibility
  def genDynamicValue: Gen[Any, DynamicValue] = genDynamicValueWithDepth(2)

  private def genDynamicValueWithDepth(maxDepth: Int): Gen[Any, DynamicValue] =
    if (maxDepth <= 0) {
      // At max depth, only generate primitives
      genPrimitiveValue.map(Primitive(_))
    } else {
      Gen.oneOf(
        genPrimitiveValue.map(Primitive(_)),
        genRecordWithDepth(maxDepth - 1),
        genVariantWithDepth(maxDepth - 1),
        genSequenceWithDepth(maxDepth - 1),
        genMapWithDepth(maxDepth - 1)
      )
    }

  def genRecord: Gen[Any, Record] = genRecordWithDepth(2)

  private def genRecordWithDepth(maxDepth: Int): Gen[Any, Record] = Gen
    .listOfBounded(0, 5) { // Reduced from 10 to 5 for Native compatibility
      for {
        key   <- Gen.alphaNumericStringBounded(1, 10) // Avoid empty string keys
        value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
      } yield key -> value
    }
    .map(f => Record(f.toIndexedSeq))

  def genVariant: Gen[Any, Variant] = genVariantWithDepth(2)

  private def genVariantWithDepth(maxDepth: Int): Gen[Any, Variant] = for {
    caseName <- Gen.alphaNumericStringBounded(1, 10) // Avoid empty string case names
    value    <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
  } yield Variant(caseName, value)

  def genSequence: Gen[Any, Sequence] = genSequenceWithDepth(2)

  private def genSequenceWithDepth(maxDepth: Int): Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 5)(
        if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
      )
      .map(f => Sequence(f.toIndexedSeq))

  def genAlphaNumericSequence: Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 5)(
        Gen
          .oneOf(
            Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
            Gen.int.map(PrimitiveValue.Int.apply)
          )
          .map(Primitive(_))
      )
      .map(f => Sequence(f.toIndexedSeq))

  def genMap: Gen[Any, DynamicValue.Map] = genMapWithDepth(2)

  private def genMapWithDepth(maxDepth: Int): Gen[Any, DynamicValue.Map] =
    Gen
      .listOfBounded(0, 5) {
        for {
          // Only use non-empty string keys to avoid duplicate JSON key issues
          key   <- Gen.alphaNumericStringBounded(1, 10).map(s => Primitive(PrimitiveValue.String(s)))
          value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
        } yield key -> value
      }
      .map(_.distinctBy(_._1.value)) // Now safe since all keys are non-empty strings
      .map(list => DynamicValue.Map(list.toIndexedSeq))
}
