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
        assertTrue(roundTrip.isRight)
      },
      test("Validation.Numeric.Range with None bounds round-trips") {
        val v: Validation[_] = Validation.Numeric.Range[Int](None, None)
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      },
      test("Validation.Numeric.Set round-trips") {
        val v: Validation[_] = Validation.Numeric.Set(Set(1, 2, 3))
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
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
