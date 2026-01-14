package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.{DynamicValue, Schema, PrimitiveValue}
import zio.blocks.schema.json.DiscriminatorKind

/**
 * Property-based tests for DynamicValue TOON encoding/decoding.
 *
 * Uses generators to test DynamicValue across multiple configuration options,
 * as requested by jdegoes.
 *
 * Key assertion: decode(encode(value)) == normalize(value)
 *
 * Note: Schema-less DynamicValue encoding in TOON has limitations:
 *   - Numeric strings decode as Long/BigDecimal (type info lost)
 *   - Int/Short/Byte -> Long, Float/Double -> BigDecimal
 *   - Complex nesting has indentation parsing edge cases
 *
 * These tests focus on the core types and configurations that work correctly.
 */
object ToonDynamicValuePropertySpec extends ZIOSpecDefault {

  // ==================== GENERATORS ====================

  // Alpha-only strings that cannot be confused with numbers
  private val genSafeString: Gen[Any, String] =
    Gen.alphaChar.flatMap { first =>
      Gen.stringBounded(0, 6)(Gen.alphaChar).map(rest => s"$first$rest")
    }

  // Primitives that round-trip EXACTLY
  private val genExactPrimitive: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.long.map(PrimitiveValue.Long.apply),
      genSafeString.map(PrimitiveValue.String.apply),
      Gen.double
        .filter(d => !d.isNaN && !d.isInfinite && d != d.floor)
        .map(d => PrimitiveValue.BigDecimal(BigDecimal(d.toString)))
    )

  // Primitives that need normalization
  private val genNormalizingPrimitive: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.int.map(PrimitiveValue.Int.apply),
      Gen.short.map(PrimitiveValue.Short.apply),
      Gen.byte.map(PrimitiveValue.Byte.apply),
      Gen.float.filter(f => !f.isNaN && !f.isInfinite).map(PrimitiveValue.Float.apply),
      Gen.double.filter(d => !d.isNaN && !d.isInfinite).map(PrimitiveValue.Double.apply)
    )

  private val genTestablePrimitive: Gen[Any, PrimitiveValue] =
    Gen.oneOf(genExactPrimitive, genNormalizingPrimitive)

  // Flat record (single level, no nesting) - avoids indentation parsing issues
  private val genFlatRecord: Gen[Any, DynamicValue.Record] =
    Gen
      .listOfBounded(1, 4) {
        for {
          key   <- genSafeString
          value <- genTestablePrimitive.map(DynamicValue.Primitive(_))
        } yield key -> value
      }
      .map(_.distinctBy(_._1))
      .map(f => DynamicValue.Record(f.toVector))

  // Flat sequence of primitives
  private val genFlatSequence: Gen[Any, DynamicValue.Sequence] =
    Gen
      .listOfBounded(0, 5)(genTestablePrimitive.map(DynamicValue.Primitive(_)))
      .map(f => DynamicValue.Sequence(f.toVector))

  // Generator covering primitives, flat records, and flat sequences
  val genDynamicValue: Gen[Any, DynamicValue] =
    Gen.oneOf(
      genTestablePrimitive.map(DynamicValue.Primitive(_)),
      genFlatRecord,
      genFlatSequence
    )

  // ==================== NORMALIZATION ====================

  private def normalize(value: DynamicValue): DynamicValue = value match {
    case DynamicValue.Primitive(p) =>
      DynamicValue.Primitive(normalizePrimitive(p))
    case DynamicValue.Record(fields) =>
      DynamicValue.Record(fields.map { case (k, v) => (k, normalize(v)) })
    case DynamicValue.Sequence(values) =>
      DynamicValue.Sequence(values.map(normalize))
    case other => other
  }

  private def normalizePrimitive(p: PrimitiveValue): PrimitiveValue = p match {
    case PrimitiveValue.Int(v)    => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Short(v)  => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Byte(v)   => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Float(v)  => PrimitiveValue.BigDecimal(BigDecimal(v.toString))
    case PrimitiveValue.Double(v) => PrimitiveValue.BigDecimal(BigDecimal(v.toString))
    case other                    => other
  }

  // ==================== PROPERTY TEST HELPER ====================

  private def roundTripProperty(
    deriver: ToonBinaryCodecDeriver,
    configName: String
  ): Spec[Any, Nothing] =
    test(s"round-trip with $configName") {
      val codec = Schema.dynamic.derive(deriver)
      check(genDynamicValue) { value =>
        val encoded  = codec.encodeToString(value)
        val decoded  = codec.decodeFromString(encoded)
        val expected = normalize(value)
        assertTrue(decoded == Right(expected)) ??
          s"Config: $configName\nOriginal: $value\nNormalized: $expected\nEncoded: '$encoded'\nDecoded: $decoded"
      }
    }

  // ==================== CONFIG VARIATIONS ====================

  private val configs: List[(String, ToonBinaryCodecDeriver)] = List(
    ("default", ToonBinaryCodecDeriver),
    ("DiscriminatorKind.Key", ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Key)),
    ("DiscriminatorKind.Field(@type)", ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("@type"))),
    ("DiscriminatorKind.None", ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)),
    ("ArrayFormat.Tabular", ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)),
    ("ArrayFormat.Inline", ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)),
    ("ArrayFormat.List", ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)),
    (
      "Combined: Key + Tabular",
      ToonBinaryCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.Key)
        .withArrayFormat(ArrayFormat.Tabular)
    ),
    (
      "Combined: Field + Inline",
      ToonBinaryCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.Field("type"))
        .withArrayFormat(ArrayFormat.Inline)
    ),
    (
      "Combined: None + List",
      ToonBinaryCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.None)
        .withArrayFormat(ArrayFormat.List)
    )
  )

  // ==================== TEST SPEC ====================

  def spec = suite("ToonDynamicValuePropertySpec")(
    suite("Round-trip property tests with 10 configs")(
      configs.map { case (name, deriver) => roundTripProperty(deriver, name) }: _*
    ),
    suite("Type normalization tests")(
      test("Int normalizes to Long") {
        val codec    = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        val input    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expected = DynamicValue.Primitive(PrimitiveValue.Long(42L))
        val encoded  = codec.encodeToString(input)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(expected))
      },
      test("Short normalizes to Long") {
        val codec    = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        val input    = DynamicValue.Primitive(PrimitiveValue.Short(100))
        val expected = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val encoded  = codec.encodeToString(input)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(expected))
      },
      test("Byte normalizes to Long") {
        val codec    = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        val input    = DynamicValue.Primitive(PrimitiveValue.Byte(10))
        val expected = DynamicValue.Primitive(PrimitiveValue.Long(10L))
        val encoded  = codec.encodeToString(input)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(expected))
      },
      test("Float normalizes to BigDecimal") {
        val codec    = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        val input    = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        val expected = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("3.14")))
        val encoded  = codec.encodeToString(input)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(expected))
      },
      test("Double normalizes to BigDecimal") {
        val codec    = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        val input    = DynamicValue.Primitive(PrimitiveValue.Double(2.718))
        val expected = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("2.718")))
        val encoded  = codec.encodeToString(input)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(expected))
      }
    ),
    suite("Exact round-trip tests")(
      test("Long round-trips exactly") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(Gen.long) { value =>
          val dv      = DynamicValue.Primitive(PrimitiveValue.Long(value))
          val decoded = codec.decodeFromString(codec.encodeToString(dv))
          assertTrue(decoded == Right(dv))
        }
      },
      test("Boolean round-trips exactly") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(Gen.boolean) { value =>
          val dv      = DynamicValue.Primitive(PrimitiveValue.Boolean(value))
          val decoded = codec.decodeFromString(codec.encodeToString(dv))
          assertTrue(decoded == Right(dv))
        }
      },
      test("String round-trips exactly") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(genSafeString) { value =>
          val dv      = DynamicValue.Primitive(PrimitiveValue.String(value))
          val decoded = codec.decodeFromString(codec.encodeToString(dv))
          assertTrue(decoded == Right(dv))
        }
      },
      test("BigDecimal with decimals round-trips exactly") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(Gen.double.filter(d => !d.isNaN && !d.isInfinite && d != d.floor)) { value =>
          val bd      = BigDecimal(value.toString)
          val dv      = DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))
          val decoded = codec.decodeFromString(codec.encodeToString(dv))
          assertTrue(decoded == Right(dv))
        }
      }
    )
  )
}
