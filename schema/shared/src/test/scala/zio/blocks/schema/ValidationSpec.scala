package zio.blocks.schema

import zio.test._

object ValidationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ValidationSpec")(
    suite("Schema[Validation[_]] round-trip")(
      test("Validation.None round-trips") {
        val v: Validation[_] = Validation.None
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.None))
      },
      test("Validation.Numeric.Positive round-trips") {
        val v: Validation[_] = Validation.Numeric.Positive
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Positive))
      },
      test("Validation.Numeric.Negative round-trips") {
        val v: Validation[_] = Validation.Numeric.Negative
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Negative))
      },
      test("Validation.Numeric.NonPositive round-trips") {
        val v: Validation[_] = Validation.Numeric.NonPositive
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.NonPositive))
      },
      test("Validation.Numeric.NonNegative round-trips") {
        val v: Validation[_] = Validation.Numeric.NonNegative
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.NonNegative))
      },
      test("Validation.Numeric.Range round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1), Some(100))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1), Some(100))))
      },
      test("Validation.Numeric.Range with None bounds round-trips") {
        val v: Validation[_] = Validation.Numeric.Range[Int](None, None)
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range[Any](None, None)))
      },
      test("Validation.Numeric.Set round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1, 2, 3))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1, 2, 3))))
      },
      test("Validation.Numeric.Range with Long values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1L), Some(100L))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1L), Some(100L))))
      },
      test("Validation.Numeric.Range with Double values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1.5), Some(100.5))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1.5), Some(100.5))))
      },
      test("Validation.Numeric.Set with Long values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1L, 2L, 3L))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1L, 2L, 3L))))
      },
      test("Validation.Numeric.Set with Double values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1.5, 2.5, 3.5))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1.5, 2.5, 3.5))))
      },
      test("Validation.Numeric.Set with BigDecimal values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(BigDecimal("1.1"), BigDecimal("2.2")))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(BigDecimal("1.1"), BigDecimal("2.2")))))
      },
      test("Validation.Numeric.Range with Byte values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1.toByte), Some(100.toByte))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1.toByte), Some(100.toByte))))
      },
      test("Validation.Numeric.Range with Short values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1.toShort), Some(100.toShort))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1.toShort), Some(100.toShort))))
      },
      test("Validation.Numeric.Range with Float values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(1.5f), Some(100.5f))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(1.5f), Some(100.5f))))
      },
      test("Validation.Numeric.Range with BigInt values round-trips") {
        val v: Validation[_] = Validation.Numeric.Range(Some(BigInt(1)), Some(BigInt(100)))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Range(Some(BigInt(1)), Some(BigInt(100)))))
      },
      test("Validation.Numeric.Set with Byte values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1.toByte, 2.toByte, 3.toByte))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1.toByte, 2.toByte, 3.toByte))))
      },
      test("Validation.Numeric.Set with Short values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1.toShort, 2.toShort, 3.toShort))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1.toShort, 2.toShort, 3.toShort))))
      },
      test("Validation.Numeric.Set with Float values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1.5f, 2.5f, 3.5f))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(1.5f, 2.5f, 3.5f))))
      },
      test("Validation.Numeric.Set with BigInt values round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(BigInt(1), BigInt(2), BigInt(3)))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Set(Set(BigInt(1), BigInt(2), BigInt(3)))))
      },
      test("Validation.String.NonEmpty round-trips") {
        val v: Validation[_] = Validation.String.NonEmpty
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.NonEmpty))
      },
      test("Validation.String.Empty round-trips") {
        val v: Validation[_] = Validation.String.Empty
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.Empty))
      },
      test("Validation.String.Blank round-trips") {
        val v: Validation[_] = Validation.String.Blank
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.Blank))
      },
      test("Validation.String.NonBlank round-trips") {
        val v: Validation[_] = Validation.String.NonBlank
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.NonBlank))
      },
      test("Validation.String.Length round-trips") {
        val v: Validation[_] = Validation.String.Length(Some(5), Some(10))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.Length(Some(5), Some(10))))
      },
      test("Validation.String.Length with None bounds round-trips") {
        val v: Validation[_] = Validation.String.Length(None, None)
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.Length(None, None)))
      },
      test("Validation.String.Pattern round-trips") {
        val v: Validation[_] = Validation.String.Pattern("^[a-z]+$")
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.Pattern("^[a-z]+$")))
      }
    )
  )
}
