package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.{DynamicValue, Schema, PrimitiveValue}
import zio.blocks.schema.json.DiscriminatorKind

/**
 * DynamicValue encoding/decoding tests for TOON format.
 *
 * Following the same pattern as JSON codec tests (JsonBinaryCodecDeriverSpec):
 *   - roundTrip: for types that encode and decode back to equal values
 *   - encode: for types that encode correctly but may not decode to exact same
 *     type
 */
object ToonDynamicValueSpec extends ZIOSpecDefault {

  private def roundTrip(
    value: DynamicValue
  )(implicit deriver: ToonBinaryCodecDeriver = ToonBinaryCodecDeriver): TestResult = {
    val codec   = Schema.dynamic.derive(deriver)
    val encoded = codec.encodeToString(value)
    val decoded = codec.decodeFromString(encoded)
    assertTrue(decoded == Right(value)) ?? s"Encoded: '$encoded'"
  }

  private def encode(value: DynamicValue, contains: String)(implicit
    deriver: ToonBinaryCodecDeriver = ToonBinaryCodecDeriver
  ): TestResult = {
    val codec   = Schema.dynamic.derive(deriver)
    val encoded = codec.encodeToString(value)
    assertTrue(encoded.contains(contains)) ?? s"Encoded: '$encoded'"
  }

  def spec = suite("ToonDynamicValueSpec")(
    suite("DynamicValue encoding/decoding")(
      test("primitive types with roundTrip") {
        // Types that decode back correctly (decoder uses Long for integers, BigDecimal for floats)
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Unit)) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(false))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(12345678901L))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(0L))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(-1L))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.45")))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("-99.99")))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("hello"))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("hello world")))
      },
      test("primitive types encode only (no roundTrip due to type normalization)") {
        // These types encode correctly but decode to different types (Int -> Long, etc.)
        encode(DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)), "1") &&
        encode(DynamicValue.Primitive(PrimitiveValue.Short(100: Short)), "100") &&
        encode(DynamicValue.Primitive(PrimitiveValue.Int(42)), "42") &&
        encode(DynamicValue.Primitive(PrimitiveValue.Float(1.5f)), "1.5") &&
        encode(DynamicValue.Primitive(PrimitiveValue.Double(3.14159)), "3.14159") &&
        encode(DynamicValue.Primitive(PrimitiveValue.Char('X')), "X") &&
        encode(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(999))), "999")
      },
      test("record types roundTrip") {
        roundTrip(
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Long(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("hello")))
            )
          )
        ) &&
        roundTrip(
          DynamicValue.Record(
            Vector(
              ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
              ("active", DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
            )
          )
        )
      },
      test("sequence types roundTrip") {
        roundTrip(
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Long(1)),
              DynamicValue.Primitive(PrimitiveValue.Long(2)),
              DynamicValue.Primitive(PrimitiveValue.Long(3))
            )
          )
        )
      }
    ),
    suite("with different configurations")(
      test("with Field discriminator") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(42))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("test")))
      },
      test("with None discriminator") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(42))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(false))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("with Tabular array format") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(100))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("test"))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("with Inline array format") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(100))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.String("inline")))
      },
      test("with List array format") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(100))) &&
        roundTrip(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("99.99"))))
      },
      test("combined: Field discriminator + Tabular arrays") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver
            .withDiscriminatorKind(DiscriminatorKind.Field("@type"))
            .withArrayFormat(ArrayFormat.Tabular)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Long(42)))
      },
      test("combined: None discriminator + Inline arrays") {
        implicit val deriver: ToonBinaryCodecDeriver =
          ToonBinaryCodecDeriver
            .withDiscriminatorKind(DiscriminatorKind.None)
            .withArrayFormat(ArrayFormat.Inline)

        roundTrip(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      }
    )
  )
}
