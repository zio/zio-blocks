package zio.blocks.schema.toon

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.{DynamicValue, DynamicValueGen, Schema}
import zio.blocks.schema.json.DiscriminatorKind

object ToonDynamicValueSpec extends ZIOSpecDefault {

  /**
   * Helper function to test DynamicValue round-trip with a specific deriver
   * configuration. This allows testing multiple configuration options as
   * requested by the maintainer.
   */
  def testDynamicValueRoundTrip(
    deriver: ToonBinaryCodecDeriver,
    testName: String
  ): Spec[Any, Nothing] =
    test(s"DynamicValue round-trip with $testName") {
      check(DynamicValueGen.genDynamicValue) { dynamicValue =>
        val codec = Schema.dynamic.derive(deriver)

        // Encode to string
        val encoded = codec.encodeToString(dynamicValue)

        // Decode back to DynamicValue
        val decoded = codec.decodeFromString(encoded)

        decoded match {
          case Right(result) =>
            // Assert equality - the core requirement
            assert(result)(equalTo(dynamicValue))
          case Left(error) =>
            // If decoding fails, we want to know about it
            assert(s"Failed to decode: $error\nEncoded: $encoded\nOriginal: $dynamicValue")(equalTo(""))
        }
      }
    }

  def spec = suite("ToonDynamicValueSpec")(
    // Test 1: Default configuration (Key discriminator, Auto array format)
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver,
      "default config (Key discriminator, Auto arrays)"
    ),

    // Test 2: Field discriminator with "type" field
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type")),
      "Field discriminator with 'type'"
    ),

    // Test 3: Field discriminator with custom field name
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("kind")),
      "Field discriminator with 'kind'"
    ),

    // Test 4: None discriminator (sequential matching)
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None),
      "None discriminator (sequential)"
    ),

    // Test 5: Force Tabular array format
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular),
      "Tabular array format"
    ),

    // Test 6: Force Inline array format
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline),
      "Inline array format"
    ),

    // Test 7: Force List array format
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List),
      "List array format"
    ),

    // Test 8: Combined configuration (Field discriminator + Tabular arrays)
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.Field("@type"))
        .withArrayFormat(ArrayFormat.Tabular),
      "Field discriminator + Tabular arrays"
    ),

    // Test 9: Combined configuration (None discriminator + Inline arrays)
    testDynamicValueRoundTrip(
      ToonBinaryCodecDeriver
        .withDiscriminatorKind(DiscriminatorKind.None)
        .withArrayFormat(ArrayFormat.Inline),
      "None discriminator + Inline arrays"
    ),

    // Test 10: Test specific DynamicValue patterns
    suite("Specific DynamicValue patterns")(
      test("primitive values") {
        check(DynamicValueGen.genPrimitiveValue.map(DynamicValue.Primitive(_))) { _ =>
          val codec        = Schema.dynamic.derive(ToonBinaryCodecDeriver)
          val roundTripped = codec.decodeFromString(codec.encodeToString(dv))
          assert(roundTripped)(isRight(equalTo(dv)))
        }
      },
      test("record values") {
        check(DynamicValueGen.genRecord) { _ =>
          val codec        = Schema.dynamic.derive(ToonBinaryCodecDeriver)
          val roundTripped = codec.decodeFromString(codec.encodeToString(dv))
          assert(roundTripped)(isRight(equalTo(dv)))
        }
      },
      test("variant values") {
        check(DynamicValueGen.genVariant) { _ =>
          val codec        = Schema.dynamic.derive(ToonBinaryCodecDeriver)
          val roundTripped = codec.decodeFromString(codec.encodeToString(dv))
          assert(roundTripped)(isRight(equalTo(dv)))
        }
      },
      test("sequence values") {
        check(DynamicValueGen.genSequence) { _ =>
          val codec        = Schema.dynamic.derive(ToonBinaryCodecDeriver)
          val roundTripped = codec.decodeFromString(codec.encodeToString(dv))
          assert(roundTripped)(isRight(equalTo(dv)))
        }
      },
      test("map values") {
        check(DynamicValueGen.genMap) { _ =>
          val codec        = Schema.dynamic.derive(ToonBinaryCodecDeriver)
          val roundTripped = codec.decodeFromString(codec.encodeToString(dv))
          assert(roundTripped)(isRight(equalTo(dv)))
        }
      }
    )
  )
}
