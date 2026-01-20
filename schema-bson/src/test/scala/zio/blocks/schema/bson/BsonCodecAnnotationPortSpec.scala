package zio.blocks.schema.bson

import zio.blocks.schema._
import zio.bson._
import zio.bson.BsonBuilder._
import zio.test._

/**
 * Ported tests from old zio-schema-bson's BsonConfig and MixedConfig test suites.
 * These tests originally used BSON-specific annotations (@bsonField, @bsonHint,
 * @bsonDiscriminator, @bsonExclude, @bsonNoExtraFields). The new implementation uses
 * Config and Modifier instead.
 *
 * Mapping:
 * - @bsonDiscriminator("name") → Config.withSumTypeHandling(DiscriminatorField("name"))
 * - @bsonHint("name") → @Modifier.rename("name")
 * - @bsonField("name") → @Modifier.rename("name")
 * - @bsonExclude → @Modifier.transient
 * - @bsonNoExtraFields → Config.withIgnoreExtraFields(false)
 */
object BsonCodecAnnotationPortSpec extends ZIOSpecDefault {

  // ============================================================================
  // PORTED FROM BsonConfig.scala.old
  // ============================================================================

  object BsonConfigPort {
    // 1. WithoutDiscriminator - plain variant without discriminator
    sealed trait WithoutDiscriminator
    object WithoutDiscriminator {
      case class A(s: String) extends WithoutDiscriminator
      case class B(s: String) extends WithoutDiscriminator

      val schema: Schema[WithoutDiscriminator] = Schema.derived[WithoutDiscriminator]
      val codec: BsonCodec[WithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 2. WithClassNameTransformOptions - using Config.withClassNameMapping
    sealed trait WithClassNameTransformOptions
    object WithClassNameTransformOptions {
      case class A(s: String) extends WithClassNameTransformOptions
      case class B(s: String) extends WithClassNameTransformOptions

      val schema: Schema[WithClassNameTransformOptions] = Schema.derived[WithClassNameTransformOptions]
      val codec: BsonCodec[WithClassNameTransformOptions] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withClassNameMapping(_.toLowerCase)
      )
    }

    // 3. WithDiscriminatorOptions - using Config.withSumTypeHandling(DiscriminatorField)
    sealed trait WithDiscriminatorOptions
    object WithDiscriminatorOptions {
      case class A(s: String) extends WithDiscriminatorOptions
      case class B(s: String) extends WithDiscriminatorOptions

      val schema: Schema[WithDiscriminatorOptions] = Schema.derived[WithDiscriminatorOptions]
      val codec: BsonCodec[WithDiscriminatorOptions] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")
        )
      )
    }

    // 4. WithoutDiscriminatorOptions - using Config.withSumTypeHandling(WrapperWithClassNameField)
    sealed trait WithoutDiscriminatorOptions
    object WithoutDiscriminatorOptions {
      case class A(s: String) extends WithoutDiscriminatorOptions
      case class B(s: String) extends WithoutDiscriminatorOptions

      val schema: Schema[WithoutDiscriminatorOptions] = Schema.derived[WithoutDiscriminatorOptions]
      val codec: BsonCodec[WithoutDiscriminatorOptions] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.WrapperWithClassNameField
        )
      )
    }

    // 5. WithDiscriminator - using Config to specify discriminator field name
    sealed trait WithDiscriminator
    object WithDiscriminator {
      case class A(s: String) extends WithDiscriminator
      case class B(s: String) extends WithDiscriminator

      val schema: Schema[WithDiscriminator] = Schema.derived[WithDiscriminator]
      val codec: BsonCodec[WithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 6. CaseNameEnumLike - using @Modifier.rename for enum-like case objects
    // Note: For case objects to encode as strings, we need NoDiscriminator mode
    sealed trait CaseNameEnumLike
    object CaseNameEnumLike {
      @Modifier.rename("aName")
      case object A extends CaseNameEnumLike

      @Modifier.rename("bName")
      case object B extends CaseNameEnumLike

      val schema: Schema[CaseNameEnumLike] = Schema.derived[CaseNameEnumLike]
      val codec: BsonCodec[CaseNameEnumLike] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.NoDiscriminator)
      )
    }

    // 7. CaseNameWithoutDiscriminator - using @Modifier.rename without discriminator
    sealed trait CaseNameWithoutDiscriminator
    object CaseNameWithoutDiscriminator {
      @Modifier.rename("aName")
      case class A(s: String) extends CaseNameWithoutDiscriminator

      @Modifier.rename("bName")
      case class B(s: String) extends CaseNameWithoutDiscriminator

      val schema: Schema[CaseNameWithoutDiscriminator] = Schema.derived[CaseNameWithoutDiscriminator]
      val codec: BsonCodec[CaseNameWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 8. CaseNameWithDiscriminator - using @Modifier.rename with discriminator
    sealed trait CaseNameWithDiscriminator
    object CaseNameWithDiscriminator {
      @Modifier.rename("aName")
      case class A(s: String) extends CaseNameWithDiscriminator

      @Modifier.rename("bName")
      case class B(s: String) extends CaseNameWithDiscriminator

      val schema: Schema[CaseNameWithDiscriminator] = Schema.derived[CaseNameWithDiscriminator]
      val codec: BsonCodec[CaseNameWithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 9. FieldName - using @Modifier.rename for field
    case class FieldName(@Modifier.rename("customName") a: String)
    object FieldName {
      val schema: Schema[FieldName] = Schema.derived[FieldName]
      val codec: BsonCodec[FieldName] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 10. AllowExtraFields - default behavior allows extra fields
    case class AllowExtraFields(a: String)
    object AllowExtraFields {
      val schema: Schema[AllowExtraFields] = Schema.derived[AllowExtraFields]
      val codec: BsonCodec[AllowExtraFields] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 11. RejectExtraFields - using Config.withIgnoreExtraFields(false)
    case class RejectExtraFields(a: String)
    object RejectExtraFields {
      val schema: Schema[RejectExtraFields] = Schema.derived[RejectExtraFields]
      val codec: BsonCodec[RejectExtraFields] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withIgnoreExtraFields(false)
      )
    }

    // 12. TransientField - using @Modifier.transient
    case class TransientField(@Modifier.transient a: String = "defaultValue", b: Int)
    object TransientField {
      val schema: Schema[TransientField] = Schema.derived[TransientField]
      val codec: BsonCodec[TransientField] = BsonSchemaCodec.bsonCodec(schema)
    }
  }

  // ============================================================================
  // PORTED FROM MixedConfig.scala.old
  // ============================================================================

  object MixedConfigPort {
    // 1. NoDiscriminator - using Config.withSumTypeHandling(NoDiscriminator)
    sealed trait NoDiscriminator
    object NoDiscriminator {
      case class A(a: String) extends NoDiscriminator
      case class B(b: String) extends NoDiscriminator
      case class C(c: String) extends NoDiscriminator

      val schema: Schema[NoDiscriminator] = Schema.derived[NoDiscriminator]
      val codec: BsonCodec[NoDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.NoDiscriminator)
      )
    }

    // 2. WithoutDiscriminator - plain variant
    sealed trait WithoutDiscriminator
    object WithoutDiscriminator {
      case class A(s: String) extends WithoutDiscriminator
      case class B(s: String) extends WithoutDiscriminator

      val schema: Schema[WithoutDiscriminator] = Schema.derived[WithoutDiscriminator]
      val codec: BsonCodec[WithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 3. WithDiscriminator - using Config with discriminator field "$type"
    sealed trait WithDiscriminator
    object WithDiscriminator {
      case class A(s: String) extends WithDiscriminator
      case class B(s: String) extends WithDiscriminator

      val schema: Schema[WithDiscriminator] = Schema.derived[WithDiscriminator]
      val codec: BsonCodec[WithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 4. CaseNameEnumLike - using @Modifier.rename for enum-like case objects
    // Note: For case objects to encode as strings, we need NoDiscriminator mode
    sealed trait CaseNameEnumLike
    object CaseNameEnumLike {
      @Modifier.rename("aName")
      case object A extends CaseNameEnumLike

      @Modifier.rename("bName")
      case object B extends CaseNameEnumLike

      val schema: Schema[CaseNameEnumLike] = Schema.derived[CaseNameEnumLike]
      val codec: BsonCodec[CaseNameEnumLike] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.NoDiscriminator)
      )
    }

    // 5. CaseNameWithoutDiscriminator - using @Modifier.rename without discriminator
    sealed trait CaseNameWithoutDiscriminator
    object CaseNameWithoutDiscriminator {
      @Modifier.rename("aName")
      case class A(s: String) extends CaseNameWithoutDiscriminator

      @Modifier.rename("bName")
      case class B(s: String) extends CaseNameWithoutDiscriminator

      val schema: Schema[CaseNameWithoutDiscriminator] = Schema.derived[CaseNameWithoutDiscriminator]
      val codec: BsonCodec[CaseNameWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 6. CaseNameWithDiscriminator - using @Modifier.rename with discriminator
    sealed trait CaseNameWithDiscriminator
    object CaseNameWithDiscriminator {
      @Modifier.rename("aName")
      case class A(s: String) extends CaseNameWithDiscriminator

      @Modifier.rename("bName")
      case class B(s: String) extends CaseNameWithDiscriminator

      val schema: Schema[CaseNameWithDiscriminator] = Schema.derived[CaseNameWithDiscriminator]
      val codec: BsonCodec[CaseNameWithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 7. CaseNameAliasesWithoutDiscriminator - using @Modifier.alias with @Modifier.rename
    sealed trait CaseNameAliasesWithoutDiscriminator
    object CaseNameAliasesWithoutDiscriminator {
      @Modifier.rename("aName")
      @Modifier.alias("aAlias1")
      @Modifier.alias("aAlias2")
      case class A(s: String) extends CaseNameAliasesWithoutDiscriminator

      @Modifier.alias("bAlias1")
      @Modifier.alias("bAlias2")
      case class B(s: String) extends CaseNameAliasesWithoutDiscriminator

      val schema: Schema[CaseNameAliasesWithoutDiscriminator] = Schema.derived[CaseNameAliasesWithoutDiscriminator]
      val codec: BsonCodec[CaseNameAliasesWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 8. CaseNameAliasesWithDiscriminator - using @Modifier.alias with discriminator
    sealed trait CaseNameAliasesWithDiscriminator
    object CaseNameAliasesWithDiscriminator {
      @Modifier.rename("aName")
      @Modifier.alias("aAlias1")
      @Modifier.alias("aAlias2")
      case class A(s: String) extends CaseNameAliasesWithDiscriminator

      @Modifier.alias("bAlias1")
      @Modifier.alias("bAlias2")
      case class B(s: String) extends CaseNameAliasesWithDiscriminator

      val schema: Schema[CaseNameAliasesWithDiscriminator] = Schema.derived[CaseNameAliasesWithDiscriminator]
      val codec: BsonCodec[CaseNameAliasesWithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 9. FieldName - using @Modifier.rename for field
    case class FieldName(@Modifier.rename("customName") a: String)
    object FieldName {
      val schema: Schema[FieldName] = Schema.derived[FieldName]
      val codec: BsonCodec[FieldName] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 10. FieldDefaultValue - field with default value
    case class FieldDefaultValue(a: String = "defaultValue")
    object FieldDefaultValue {
      val schema: Schema[FieldDefaultValue] = Schema.derived[FieldDefaultValue]
      val codec: BsonCodec[FieldDefaultValue] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 11. AllowExtraFields - default behavior
    case class AllowExtraFields(a: String)
    object AllowExtraFields {
      val schema: Schema[AllowExtraFields] = Schema.derived[AllowExtraFields]
      val codec: BsonCodec[AllowExtraFields] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 12. RejectExtraFields - using Config.withIgnoreExtraFields(false)
    case class RejectExtraFields(a: String)
    object RejectExtraFields {
      val schema: Schema[RejectExtraFields] = Schema.derived[RejectExtraFields]
      val codec: BsonCodec[RejectExtraFields] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withIgnoreExtraFields(false)
      )
    }

    // 13. TransientCaseWithoutDiscriminator - using @Modifier.transient on case
    sealed trait TransientCaseWithoutDiscriminator
    object TransientCaseWithoutDiscriminator {
      case class A(s: String) extends TransientCaseWithoutDiscriminator
      case class B(s: String) extends TransientCaseWithoutDiscriminator

      @Modifier.transient
      case class C(s: String) extends TransientCaseWithoutDiscriminator

      val schema: Schema[TransientCaseWithoutDiscriminator] = Schema.derived[TransientCaseWithoutDiscriminator]
      val codec: BsonCodec[TransientCaseWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
    }

    // 14. TransientCaseWithDiscriminator - using @Modifier.transient with discriminator
    sealed trait TransientCaseWithDiscriminator
    object TransientCaseWithDiscriminator {
      case class A(s: String) extends TransientCaseWithDiscriminator
      case class B(s: String) extends TransientCaseWithDiscriminator

      @Modifier.transient
      case class C(s: String) extends TransientCaseWithDiscriminator

      val schema: Schema[TransientCaseWithDiscriminator] = Schema.derived[TransientCaseWithDiscriminator]
      val codec: BsonCodec[TransientCaseWithDiscriminator] = BsonSchemaCodec.bsonCodec(
        schema,
        BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("$type")
        )
      )
    }

    // 15. TransientField - using @Modifier.transient on field
    case class TransientField(@Modifier.transient a: String = "defaultValue", b: Int)
    object TransientField {
      val schema: Schema[TransientField] = Schema.derived[TransientField]
      val codec: BsonCodec[TransientField] = BsonSchemaCodec.bsonCodec(schema)
    }
  }

  // ============================================================================
  // TEST SUITES
  // ============================================================================

  def spec = suite("BsonCodecAnnotationPortSpec")(
    suite("ported from BsonConfig (BSON annotations)")(
      bsonConfigTests
    ),
    suite("ported from MixedConfig (mixed annotations)")(
      mixedConfigTests
    )
  )

  // ============================================================================
  // BsonConfig Test Cases
  // ============================================================================

  def bsonConfigTests = suite("bson config tests")(
    suite("without discriminator")(
      test("encode A") {
        val value   = BsonConfigPort.WithoutDiscriminator.A("str")
        val codec   = BsonConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("A" -> doc("s" -> str("str"))))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.WithoutDiscriminator.A("str")
        val codec   = BsonConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.WithoutDiscriminator.B("str")
        val codec   = BsonConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("with class name transform")(
      test("encode A with lowercase") {
        val value   = BsonConfigPort.WithClassNameTransformOptions.A("str")
        val codec   = BsonConfigPort.WithClassNameTransformOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> doc("s" -> str("str"))))
      },
      test("encode/decode A with lowercase") {
        val value   = BsonConfigPort.WithClassNameTransformOptions.A("str")
        val codec   = BsonConfigPort.WithClassNameTransformOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithClassNameTransformOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B with lowercase") {
        val value   = BsonConfigPort.WithClassNameTransformOptions.B("str")
        val codec   = BsonConfigPort.WithClassNameTransformOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithClassNameTransformOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("with discriminator field 'type'")(
      test("encode A with discriminator") {
        val value   = BsonConfigPort.WithDiscriminatorOptions.A("str")
        val codec   = BsonConfigPort.WithDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("type" -> str("A"), "s" -> str("str")))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.WithDiscriminatorOptions.A("str")
        val codec   = BsonConfigPort.WithDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithDiscriminatorOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.WithDiscriminatorOptions.B("str")
        val codec   = BsonConfigPort.WithDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithDiscriminatorOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("without discriminator options (WrapperWithClassNameField)")(
      test("encode A") {
        val value   = BsonConfigPort.WithoutDiscriminatorOptions.A("str")
        val codec   = BsonConfigPort.WithoutDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("A" -> doc("s" -> str("str"))))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.WithoutDiscriminatorOptions.A("str")
        val codec   = BsonConfigPort.WithoutDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithoutDiscriminatorOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.WithoutDiscriminatorOptions.B("str")
        val codec   = BsonConfigPort.WithoutDiscriminatorOptions.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithoutDiscriminatorOptions](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("with discriminator '$type'")(
      test("encode A with $type discriminator") {
        val value   = BsonConfigPort.WithDiscriminator.A("str")
        val codec   = BsonConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("A"), "s" -> str("str")))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.WithDiscriminator.A("str")
        val codec   = BsonConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.WithDiscriminator.B("str")
        val codec   = BsonConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.WithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name enum like")(
      test("encode A as 'aName'") {
        val value   = BsonConfigPort.CaseNameEnumLike.A
        val codec   = BsonConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == str("aName"))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.CaseNameEnumLike.A
        val codec   = BsonConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameEnumLike](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.CaseNameEnumLike.B
        val codec   = BsonConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameEnumLike](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name without discriminator")(
      test("encode A as 'aName'") {
        val value   = BsonConfigPort.CaseNameWithoutDiscriminator.A("str")
        val codec   = BsonConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("aName" -> doc("s" -> str("str"))))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.CaseNameWithoutDiscriminator.A("str")
        val codec   = BsonConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.CaseNameWithoutDiscriminator.B("str")
        val codec   = BsonConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name with discriminator")(
      test("encode A with renamed case") {
        val value   = BsonConfigPort.CaseNameWithDiscriminator.A("str")
        val codec   = BsonConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("aName"), "s" -> str("str")))
      },
      test("encode/decode A") {
        val value   = BsonConfigPort.CaseNameWithDiscriminator.A("str")
        val codec   = BsonConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = BsonConfigPort.CaseNameWithDiscriminator.B("str")
        val codec   = BsonConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.CaseNameWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("field name")(
      test("encode with renamed field") {
        val value   = BsonConfigPort.FieldName("str")
        val codec   = BsonConfigPort.FieldName.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("customName" -> str("str")))
      },
      test("encode/decode") {
        val value   = BsonConfigPort.FieldName("str")
        val codec   = BsonConfigPort.FieldName.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[BsonConfigPort.FieldName](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("allow extra fields")(
      test("encode") {
        val value   = BsonConfigPort.AllowExtraFields("str")
        val codec   = BsonConfigPort.AllowExtraFields.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("decode with extra fields") {
        val bson    = doc("extra" -> doc(), "a" -> str("str"))
        val codec   = BsonConfigPort.AllowExtraFields.codec
        val decoded = bson.as[BsonConfigPort.AllowExtraFields](codec.decoder)
        assertTrue(decoded == Right(BsonConfigPort.AllowExtraFields("str")))
      }
    ),
    suite("reject extra fields")(
      test("encode") {
        val value   = BsonConfigPort.RejectExtraFields("str")
        val codec   = BsonConfigPort.RejectExtraFields.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("decode with extra fields fails") {
        val bson    = doc("extra" -> doc(), "a" -> str("str"))
        val codec   = BsonConfigPort.RejectExtraFields.codec
        val decoded = bson.as[BsonConfigPort.RejectExtraFields](codec.decoder)
        assertTrue(decoded.isLeft && decoded.left.exists(_.contains("extra")))
      }
    ),
    suite("transient field")(
      test("encode without transient field") {
        val value   = BsonConfigPort.TransientField("str", 1)
        val codec   = BsonConfigPort.TransientField.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("b" -> int(1)))
      },
      test("decode with default value") {
        val bson    = doc("b" -> int(1))
        val codec   = BsonConfigPort.TransientField.codec
        val decoded = bson.as[BsonConfigPort.TransientField](codec.decoder)
        assertTrue(decoded == Right(BsonConfigPort.TransientField("defaultValue", 1)))
      },
      test("decode ignores transient field value") {
        val bson    = doc("a" -> str("str"), "b" -> int(1))
        val codec   = BsonConfigPort.TransientField.codec
        val decoded = bson.as[BsonConfigPort.TransientField](codec.decoder)
        assertTrue(decoded == Right(BsonConfigPort.TransientField("str", 1)))
      }
    )
  )

  // ============================================================================
  // MixedConfig Test Cases
  // ============================================================================

  def mixedConfigTests = suite("mixed config tests")(
    suite("no discriminator mode")(
      test("encode A") {
        val value   = MixedConfigPort.NoDiscriminator.A("str")
        val codec   = MixedConfigPort.NoDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.NoDiscriminator.A("str")
        val codec   = MixedConfigPort.NoDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.NoDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.NoDiscriminator.B("str")
        val codec   = MixedConfigPort.NoDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.NoDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode C") {
        val value   = MixedConfigPort.NoDiscriminator.C("str")
        val codec   = MixedConfigPort.NoDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.NoDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("without discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.WithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("A" -> doc("s" -> str("str"))))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.WithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.WithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.WithoutDiscriminator.B("str")
        val codec   = MixedConfigPort.WithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.WithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("with discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.WithDiscriminator.A("str")
        val codec   = MixedConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("A"), "s" -> str("str")))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.WithDiscriminator.A("str")
        val codec   = MixedConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.WithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.WithDiscriminator.B("str")
        val codec   = MixedConfigPort.WithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.WithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name enum like")(
      test("encode A") {
        val value   = MixedConfigPort.CaseNameEnumLike.A
        val codec   = MixedConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == str("aName"))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.CaseNameEnumLike.A
        val codec   = MixedConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameEnumLike](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.CaseNameEnumLike.B
        val codec   = MixedConfigPort.CaseNameEnumLike.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameEnumLike](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name without discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.CaseNameWithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("aName" -> doc("s" -> str("str"))))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.CaseNameWithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.CaseNameWithoutDiscriminator.B("str")
        val codec   = MixedConfigPort.CaseNameWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name with discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.CaseNameWithDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("aName"), "s" -> str("str")))
      },
      test("encode/decode A") {
        val value   = MixedConfigPort.CaseNameWithDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encode/decode B") {
        val value   = MixedConfigPort.CaseNameWithDiscriminator.B("str")
        val codec   = MixedConfigPort.CaseNameWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.CaseNameWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("case name aliases without discriminator")(
      test("encode A uses primary name") {
        val value   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("aName" -> doc("s" -> str("str"))))
      },
      test("encode B uses case name") {
        val value   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.B("str")
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("B" -> doc("s" -> str("str"))))
      },
      test("decode A with primary name") {
        val bson    = doc("aName" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.A("str")))
      },
      test("decode A with alias1") {
        val bson    = doc("aAlias1" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.A("str")))
      },
      test("decode A with alias2") {
        val bson    = doc("aAlias2" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.A("str")))
      },
      test("decode B with primary name") {
        val bson    = doc("B" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.B("str")))
      },
      test("decode B with alias1") {
        val bson    = doc("bAlias1" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.B("str")))
      },
      test("decode B with alias2") {
        val bson    = doc("bAlias2" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.CaseNameAliasesWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithoutDiscriminator.B("str")))
      }
    ),
    suite("case name aliases with discriminator")(
      test("encode A uses primary name") {
        val value   = MixedConfigPort.CaseNameAliasesWithDiscriminator.A("str")
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("aName"), "s" -> str("str")))
      },
      test("encode B uses case name") {
        val value   = MixedConfigPort.CaseNameAliasesWithDiscriminator.B("str")
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("B"), "s" -> str("str")))
      },
      test("decode A with primary name") {
        val bson    = doc("$type" -> str("aName"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.A("str")))
      },
      test("decode A with alias1") {
        val bson    = doc("$type" -> str("aAlias1"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.A("str")))
      },
      test("decode A with alias2") {
        val bson    = doc("$type" -> str("aAlias2"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.A("str")))
      },
      test("decode B with primary name") {
        val bson    = doc("$type" -> str("B"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.B("str")))
      },
      test("decode B with alias1") {
        val bson    = doc("$type" -> str("bAlias1"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.B("str")))
      },
      test("decode B with alias2") {
        val bson    = doc("$type" -> str("bAlias2"), "s" -> str("str"))
        val codec   = MixedConfigPort.CaseNameAliasesWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.CaseNameAliasesWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.CaseNameAliasesWithDiscriminator.B("str")))
      }
    ),
    suite("field name")(
      test("encode") {
        val value   = MixedConfigPort.FieldName("str")
        val codec   = MixedConfigPort.FieldName.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("customName" -> str("str")))
      },
      test("encode/decode") {
        val value   = MixedConfigPort.FieldName("str")
        val codec   = MixedConfigPort.FieldName.codec
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[MixedConfigPort.FieldName](codec.decoder)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("field default value")(
      test("encode") {
        val value   = MixedConfigPort.FieldDefaultValue("str")
        val codec   = MixedConfigPort.FieldDefaultValue.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("decode with missing field uses default") {
        val bson    = doc()
        val codec   = MixedConfigPort.FieldDefaultValue.codec
        val decoded = bson.as[MixedConfigPort.FieldDefaultValue](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.FieldDefaultValue("defaultValue")))
      }
    ),
    suite("allow extra fields")(
      test("encode") {
        val value   = MixedConfigPort.AllowExtraFields("str")
        val codec   = MixedConfigPort.AllowExtraFields.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("decode with extra fields") {
        val bson    = doc("extra" -> doc(), "a" -> str("str"))
        val codec   = MixedConfigPort.AllowExtraFields.codec
        val decoded = bson.as[MixedConfigPort.AllowExtraFields](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.AllowExtraFields("str")))
      }
    ),
    suite("reject extra fields")(
      test("encode") {
        val value   = MixedConfigPort.RejectExtraFields("str")
        val codec   = MixedConfigPort.RejectExtraFields.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("a" -> str("str")))
      },
      test("decode with extra fields fails") {
        val bson    = doc("extra" -> doc(), "a" -> str("str"))
        val codec   = MixedConfigPort.RejectExtraFields.codec
        val decoded = bson.as[MixedConfigPort.RejectExtraFields](codec.decoder)
        assertTrue(decoded.isLeft && decoded.left.exists(_.contains("extra")))
      }
    ),
    suite("transient case without discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.TransientCaseWithoutDiscriminator.A("str")
        val codec   = MixedConfigPort.TransientCaseWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("A" -> doc("s" -> str("str"))))
      },
      test("encode B") {
        val value   = MixedConfigPort.TransientCaseWithoutDiscriminator.B("str")
        val codec   = MixedConfigPort.TransientCaseWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("B" -> doc("s" -> str("str"))))
      },
      test("encode C as empty document") {
        val value   = MixedConfigPort.TransientCaseWithoutDiscriminator.C("str")
        val codec   = MixedConfigPort.TransientCaseWithoutDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc())
      },
      test("decode A") {
        val bson    = doc("A" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.TransientCaseWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.TransientCaseWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientCaseWithoutDiscriminator.A("str")))
      },
      test("decode B") {
        val bson    = doc("B" -> doc("s" -> str("str")))
        val codec   = MixedConfigPort.TransientCaseWithoutDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.TransientCaseWithoutDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientCaseWithoutDiscriminator.B("str")))
      }
    ),
    suite("transient case with discriminator")(
      test("encode A") {
        val value   = MixedConfigPort.TransientCaseWithDiscriminator.A("str")
        val codec   = MixedConfigPort.TransientCaseWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("A"), "s" -> str("str")))
      },
      test("encode B") {
        val value   = MixedConfigPort.TransientCaseWithDiscriminator.B("str")
        val codec   = MixedConfigPort.TransientCaseWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("$type" -> str("B"), "s" -> str("str")))
      },
      test("encode C as empty document") {
        val value   = MixedConfigPort.TransientCaseWithDiscriminator.C("str")
        val codec   = MixedConfigPort.TransientCaseWithDiscriminator.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc())
      },
      test("decode A") {
        val bson    = doc("$type" -> str("A"), "s" -> str("str"))
        val codec   = MixedConfigPort.TransientCaseWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.TransientCaseWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientCaseWithDiscriminator.A("str")))
      },
      test("decode B") {
        val bson    = doc("$type" -> str("B"), "s" -> str("str"))
        val codec   = MixedConfigPort.TransientCaseWithDiscriminator.codec
        val decoded = bson.as[MixedConfigPort.TransientCaseWithDiscriminator](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientCaseWithDiscriminator.B("str")))
      }
    ),
    suite("transient field")(
      test("encode without transient field") {
        val value   = MixedConfigPort.TransientField("str", 1)
        val codec   = MixedConfigPort.TransientField.codec
        val encoded = codec.encoder.toBsonValue(value)
        assertTrue(encoded == doc("b" -> int(1)))
      },
      test("decode with default value") {
        val bson    = doc("b" -> int(1))
        val codec   = MixedConfigPort.TransientField.codec
        val decoded = bson.as[MixedConfigPort.TransientField](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientField("defaultValue", 1)))
      },
      test("decode ignores transient field value") {
        val bson    = doc("a" -> str("str"), "b" -> int(1))
        val codec   = MixedConfigPort.TransientField.codec
        val decoded = bson.as[MixedConfigPort.TransientField](codec.decoder)
        assertTrue(decoded == Right(MixedConfigPort.TransientField("str", 1)))
      }
    )
  )
}
