package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicValue._
import zio.blocks.schema.JavaTimeGen._
import zio.test.Gen

object DynamicValueGen {
  val genPrimitiveValue: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.unit.map(_ => PrimitiveValue.Unit),
      Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
      Gen.int.map(PrimitiveValue.Int.apply),
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.byte.map(PrimitiveValue.Byte.apply),
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.double.map(PrimitiveValue.Double.apply),
      Gen.float.map(PrimitiveValue.Float.apply),
      Gen.long.map(PrimitiveValue.Long.apply),
      Gen.short.map(PrimitiveValue.Short.apply),
      Gen.char.filter(x => x >= ' ' && x <= 0xd800 || x >= 0xdfff).map(PrimitiveValue.Char.apply),
      Gen.bigInt(BigInt(0), BigInt(1000000000)).map(PrimitiveValue.BigInt.apply),
      Gen.bigDecimal(BigDecimal(0), BigDecimal(1000000000)).map(PrimitiveValue.BigDecimal.apply),
      genDayOfWeek.map(PrimitiveValue.DayOfWeek.apply),
      genDuration.map(PrimitiveValue.Duration.apply),
      genInstant.map(PrimitiveValue.Instant.apply),
      genLocalDate.map(PrimitiveValue.LocalDate.apply),
      genLocalDateTime.map(PrimitiveValue.LocalDateTime.apply),
      genLocalTime.map(PrimitiveValue.LocalTime.apply),
      genMonth.map(PrimitiveValue.Month.apply),
      genMonthDay.map(PrimitiveValue.MonthDay.apply),
      genOffsetDateTime.map(PrimitiveValue.OffsetDateTime.apply),
      genOffsetTime.map(PrimitiveValue.OffsetTime.apply),
      genPeriod.map(PrimitiveValue.Period.apply),
      genYear.map(PrimitiveValue.Year.apply),
      genYearMonth.map(PrimitiveValue.YearMonth.apply),
      genZoneId.map(PrimitiveValue.ZoneId.apply),
      genZoneOffset.map(PrimitiveValue.ZoneOffset.apply),
      genZonedDateTime.map(PrimitiveValue.ZonedDateTime.apply),
      Gen.uuid.map(PrimitiveValue.UUID.apply),
      Gen.currency.map(PrimitiveValue.Currency.apply)
    )

  // Null generator
  val genNull: Gen[Any, DynamicValue.Null.type] = Gen.const(DynamicValue.Null)

  // Depth-limited generators to keep test execution time manageable
  val genDynamicValue: Gen[Any, DynamicValue] = genDynamicValueWithDepth(2)

  private[this] def genDynamicValueWithDepth(maxDepth: Int): Gen[Any, DynamicValue] =
    if (maxDepth <= 0) Gen.oneOf(genPrimitiveValue.map(Primitive(_)), genNull)
    else {
      Gen.oneOf(
        genPrimitiveValue.map(Primitive(_)),
        genRecordWithDepth(maxDepth - 1),
        genVariantWithDepth(maxDepth - 1),
        genSequenceWithDepth(maxDepth - 1),
        genMapWithDepth(maxDepth - 1),
        genNull
      )
    }

  val genRecord: Gen[Any, Record] = genRecordWithDepth(2)

  private[this] def genRecordWithDepth(maxDepth: Int): Gen[Any, Record] = Gen
    .listOfBounded(0, 5) {
      for {
        key   <- Gen.alphaNumericStringBounded(1, 10) // Avoid empty string keys
        value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
      } yield key -> value
    }
    .map(_.distinctBy(_._1)) // Now safe since all keys are non-empty strings
    .map(f => Record(Chunk.from(f)))

  val genVariant: Gen[Any, Variant] = genVariantWithDepth(2)

  private[this] def genVariantWithDepth(maxDepth: Int): Gen[Any, Variant] = for {
    caseName <- Gen.alphaNumericStringBounded(1, 10) // Avoid empty string case names
    value    <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
  } yield Variant(caseName, value)

  val genSequence: Gen[Any, Sequence] = genSequenceWithDepth(2)

  private[this] def genSequenceWithDepth(maxDepth: Int): Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 5)(
        if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
      )
      .map(f => Sequence(Chunk.from(f)))

  val genAlphaNumericSequence: Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 5)(
        Gen
          .oneOf(
            Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
            Gen.int.map(PrimitiveValue.Int.apply)
          )
          .map(Primitive(_))
      )
      .map(f => Sequence(Chunk.from(f)))

  val genMap: Gen[Any, DynamicValue.Map] = genMapWithDepth(2)

  private[this] def genMapWithDepth(maxDepth: Int): Gen[Any, Map] =
    Gen
      .listOfBounded(0, 5) {
        for {
          // Only use non-empty string keys to avoid duplicate JSON key issues
          key   <- Gen.alphaNumericStringBounded(1, 10).map(s => Primitive(PrimitiveValue.String(s)))
          value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
        } yield key -> value
      }
      .map(_.distinctBy(_._1.value)) // Now safe since all keys are non-empty strings
      .map(list => Map(Chunk.from(list)))
}
