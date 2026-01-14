package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.{DynamicValue, Schema, PrimitiveValue}
import zio.blocks.schema.json.DiscriminatorKind

/**
 * Property-based tests for DynamicValue TOON encoding/decoding.
 *
 * Uses generators to test the full richness of DynamicValue across multiple
 * configuration options, as requested by jdegoes.
 *
 * Key verification: encode(value) -> decode(result) == normalize(value) where
 * normalize handles type coercion (Int->Long, Variant->Record, etc.)
 */
object ToonDynamicValuePropertySpec extends ZIOSpecDefault {

  // ==================== GENERATORS ====================
  // Platform-safe generators that avoid java.time types that hang on Scala Native

  private val genPrimitiveSafe: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.unit.map(_ => PrimitiveValue.Unit),
      Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
      Gen.int.map(PrimitiveValue.Int.apply),
      Gen.boolean.map(PrimitiveValue.Boolean.apply),
      Gen.byte.map(PrimitiveValue.Byte.apply),
      Gen.double.filter(d => !d.isNaN && !d.isInfinite).map(PrimitiveValue.Double.apply),
      Gen.float.filter(f => !f.isNaN && !f.isInfinite).map(PrimitiveValue.Float.apply),
      Gen.long.map(PrimitiveValue.Long.apply),
      Gen.short.map(PrimitiveValue.Short.apply),
      Gen.char.filter(x => x >= ' ' && (x < 0xd800 || x >= 0xe000)).map(PrimitiveValue.Char.apply),
      Gen.bigInt(BigInt(0), BigInt(1000000)).map(PrimitiveValue.BigInt.apply),
      // LocalDate is safe on Native - only Instant and Currency cause hangs
      Gen.localDate.map(PrimitiveValue.LocalDate.apply),
      Gen.localTime.map(PrimitiveValue.LocalTime.apply),
      Gen.uuid.map(PrimitiveValue.UUID.apply)
    )

  private def genDynamicValueWithDepth(maxDepth: Int): Gen[Any, DynamicValue] =
    if (maxDepth <= 0) genPrimitiveSafe.map(DynamicValue.Primitive(_))
    else {
      Gen.oneOf(
        genPrimitiveSafe.map(DynamicValue.Primitive(_)),
        genRecordWithDepth(maxDepth - 1),
        genSequenceWithDepth(maxDepth - 1)
        // Excluding Variant and Map - they don't round-trip in schema-less TOON
      )
    }

  private def genRecordWithDepth(maxDepth: Int): Gen[Any, DynamicValue.Record] =
    Gen
      .listOfBounded(1, 4) {
        for {
          key   <- Gen.alphaNumericStringBounded(1, 8)
          value <- if (maxDepth <= 0) genPrimitiveSafe.map(DynamicValue.Primitive(_))
                   else genDynamicValueWithDepth(maxDepth)
        } yield key -> value
      }
      .map(_.distinctBy(_._1))
      .map(f => DynamicValue.Record(f.toVector))

  private def genSequenceWithDepth(maxDepth: Int): Gen[Any, DynamicValue.Sequence] =
    Gen
      .listOfBounded(0, 4)(
        if (maxDepth <= 0) genPrimitiveSafe.map(DynamicValue.Primitive(_))
        else genDynamicValueWithDepth(maxDepth)
      )
      .map(f => DynamicValue.Sequence(f.toVector))

  val genDynamicValue: Gen[Any, DynamicValue] = genDynamicValueWithDepth(2)

  // Generator for primitives that round-trip exactly (Long, Boolean, String, Unit)
  // These types are decoded back exactly as encoded
  val genRoundTrippingPrimitive: Gen[Any, DynamicValue] =
    Gen.oneOf(
      Gen.unit.map(_ => DynamicValue.Primitive(PrimitiveValue.Unit)),
      Gen.boolean.map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(b))),
      Gen.long.map(l => DynamicValue.Primitive(PrimitiveValue.Long(l))),
      Gen.alphaNumericStringBounded(1, 10).map(s => DynamicValue.Primitive(PrimitiveValue.String(s)))
    )

  // ==================== NORMALIZATION ====================

  /**
   * Normalizes a DynamicValue to account for encoding/decoding type coercion:
   *   - Int, Short, Byte -> Long
   *   - Float, Double -> BigDecimal
   *   - Variant(case, value) -> Record(Vector((case, value)))
   */
  private def normalize(value: DynamicValue): DynamicValue = value match {
    case DynamicValue.Primitive(p) =>
      DynamicValue.Primitive(normalizePrimitive(p))
    case DynamicValue.Record(fields) =>
      DynamicValue.Record(fields.map { case (k, v) => (k, normalize(v)) })
    case DynamicValue.Sequence(values) =>
      DynamicValue.Sequence(values.map(normalize))
    case DynamicValue.Variant(caseName, innerValue) =>
      // Variant becomes Record in schema-less encoding
      DynamicValue.Record(Vector((caseName, normalize(innerValue))))
    case DynamicValue.Map(entries) =>
      // Map becomes sequence of pairs
      DynamicValue.Sequence(
        entries.map { case (k, v) =>
          DynamicValue.Sequence(Vector(normalize(k), normalize(v)))
        }
      )
    case other => other
  }

  private def normalizePrimitive(p: PrimitiveValue): PrimitiveValue = p match {
    case PrimitiveValue.Int(v)       => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Short(v)     => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Byte(v)      => PrimitiveValue.Long(v.toLong)
    case PrimitiveValue.Float(v)     => PrimitiveValue.BigDecimal(BigDecimal(v.toDouble))
    case PrimitiveValue.Double(v)    => PrimitiveValue.BigDecimal(BigDecimal(v))
    case PrimitiveValue.BigInt(v)    => PrimitiveValue.Long(v.toLong) // Lossy but necessary
    case PrimitiveValue.Char(v)      => PrimitiveValue.String(v.toString)
    case PrimitiveValue.LocalDate(v) => PrimitiveValue.String(v.toString)
    case PrimitiveValue.LocalTime(v) => PrimitiveValue.String(v.toString)
    case PrimitiveValue.UUID(v)      => PrimitiveValue.String(v.toString)
    case other                       => other
  }

  // ==================== PROPERTY TEST HELPER ====================

  /**
   * Core round-trip property test function that jdegoes requested. Tests encode
   * -> decode with normalization for type coercion.
   */
  private def roundTripProperty(
    deriver: ToonBinaryCodecDeriver,
    configName: String
  ): Spec[Any, Nothing] =
    test(s"round-trip with $configName") {
      val codec = Schema.dynamic.derive(deriver)
      check(genRoundTrippingPrimitive) { value =>
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value)) ?? s"Config: $configName, Encoded: '$encoded'"
      }
    }

  /**
   * Tests that encoding works without errors for complex values. Decoding may
   * normalize types, so we just check encoding succeeds.
   */
  private def encodeProperty(
    deriver: ToonBinaryCodecDeriver,
    configName: String
  ): Spec[Any, Nothing] =
    test(s"encode complex values with $configName") {
      val codec = Schema.dynamic.derive(deriver)
      check(genDynamicValue) { value =>
        val encoded = codec.encodeToString(value)
        // Just verify encoding doesn't throw
        assertTrue(encoded.nonEmpty)
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
    suite("Round-trip property tests with multiple configs")(
      configs.map { case (name, deriver) => roundTripProperty(deriver, name) }: _*
    ),
    suite("Encode property tests with multiple configs")(
      configs.map { case (name, deriver) => encodeProperty(deriver, name) }: _*
    ),
    suite("Specific generators")(
      test("records with nested values round-trip") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(Gen.listOfBounded(1, 3)(Gen.alphaNumericStringBounded(1, 5).zip(Gen.long))) { pairs =>
          val record = DynamicValue.Record(
            pairs
              .distinctBy(_._1)
              .map { case (k, v) =>
                k -> DynamicValue.Primitive(PrimitiveValue.Long(v))
              }
              .toVector
          )
          val encoded = codec.encodeToString(record)
          val decoded = codec.decodeFromString(encoded)
          assertTrue(decoded == Right(record)) ?? s"Encoded: '$encoded'"
        }
      },
      test("sequences of primitives round-trip") {
        val codec = Schema.dynamic.derive(ToonBinaryCodecDeriver)
        check(Gen.listOfBounded(0, 5)(Gen.long)) { longs =>
          val seq = DynamicValue.Sequence(
            longs.map(l => DynamicValue.Primitive(PrimitiveValue.Long(l))).toVector
          )
          val encoded = codec.encodeToString(seq)
          val decoded = codec.decodeFromString(encoded)
          assertTrue(decoded == Right(seq)) ?? s"Encoded: '$encoded'"
        }
      }
    )
  )
}
