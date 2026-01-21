package zio.schema.bson

import zio.bson._
import zio.blocks.schema.{Schema, Modifier}
import zio.schema.bson.BsonSchemaCodec.{Config, SumTypeHandling}

object BsonConfig {
  sealed trait WithoutDiscriminator

  object WithoutDiscriminator {
    case class A(s: String) extends WithoutDiscriminator
    case class B(s: String) extends WithoutDiscriminator

    implicit lazy val schema: Schema[WithoutDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  sealed trait WithClassNameTransformOptions

  object WithClassNameTransformOptions {
    case class A(s: String) extends WithClassNameTransformOptions
    case class B(s: String) extends WithClassNameTransformOptions

    implicit lazy val schema: Schema[WithClassNameTransformOptions]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithClassNameTransformOptions] = BsonSchemaCodec.bsonCodec(
      schema,
      Config.withClassNameMapping(classNameMapping = _.toLowerCase)
    )
  }

  sealed trait WithDiscriminatorOptions

  object WithDiscriminatorOptions {
    case class A(s: String) extends WithDiscriminatorOptions
    case class B(s: String) extends WithDiscriminatorOptions

    implicit lazy val schema: Schema[WithDiscriminatorOptions]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithDiscriminatorOptions] = BsonSchemaCodec.bsonCodec(
      schema,
      Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("type"))
    )
  }

  sealed trait WithoutDiscriminatorOptions

  object WithoutDiscriminatorOptions {
    case class A(s: String) extends WithoutDiscriminatorOptions
    case class B(s: String) extends WithoutDiscriminatorOptions

    implicit lazy val schema: Schema[WithoutDiscriminatorOptions]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithoutDiscriminatorOptions] = BsonSchemaCodec.bsonCodec(
      schema,
      Config.withSumTypeHandling(SumTypeHandling.WrapperWithClassNameField)
    )
  }

  // @bsonDiscriminator("$type") removed
  sealed trait WithDiscriminator

  object WithDiscriminator {
    case class A(s: String) extends WithDiscriminator
    case class B(s: String) extends WithDiscriminator

    implicit lazy val schema: Schema[WithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithDiscriminator] = BsonSchemaCodec.bsonCodec(
      schema,
      Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("$type"))
    )
  }

  sealed trait CaseNameEnumLike

  object CaseNameEnumLike {
    @Modifier.rename("aName")
    case object A extends CaseNameEnumLike
    @Modifier.rename("bName")
    case object B extends CaseNameEnumLike

    implicit lazy val schema: Schema[CaseNameEnumLike]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameEnumLike] = BsonSchemaCodec.bsonCodec(schema)
  }

  sealed trait CaseNameWithoutDiscriminator

  object CaseNameWithoutDiscriminator {
    @Modifier.rename("aName")
    case class A(s: String) extends CaseNameWithoutDiscriminator
    @Modifier.rename("bName")
    case class B(s: String) extends CaseNameWithoutDiscriminator

    implicit lazy val schema: Schema[CaseNameWithoutDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  // @bsonDiscriminator("$type")
  sealed trait CaseNameWithDiscriminator

  object CaseNameWithDiscriminator {
    @Modifier.rename("aName")
    case class A(s: String) extends CaseNameWithDiscriminator
    @Modifier.rename("bName")
    case class B(s: String) extends CaseNameWithDiscriminator

    implicit lazy val schema: Schema[CaseNameWithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameWithDiscriminator] = BsonSchemaCodec.bsonCodec(
      schema,
      Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("$type"))
    )
  }

  case class FieldName(@Modifier.rename("customName") a: String)

  object FieldName {
    implicit lazy val schema: Schema[FieldName]   = Schema.derived
    implicit lazy val codec: BsonCodec[FieldName] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class AllowExtraFields(a: String)

  object AllowExtraFields {
    implicit lazy val schema: Schema[AllowExtraFields] = Schema.derived
    // Default config allows extra fields
    implicit lazy val codec: BsonCodec[AllowExtraFields] = BsonSchemaCodec.bsonCodec(schema)
  }

  // @bsonNoExtraFields
  case class RejectExtraFields(a: String)

  object RejectExtraFields {
    implicit lazy val schema: Schema[RejectExtraFields]   = Schema.derived
    implicit lazy val codec: BsonCodec[RejectExtraFields] = BsonSchemaCodec.bsonCodec(
      schema,
      Config(allowExtraFields = false)
    )
  }

  // @bsonExclude a, @bsonField("b") for b? No, just exclude a.
  // Test: doc("b" -> int(1)). 'a' excluded.
  case class TransientField(@Modifier.transient a: String = "defaultValue", b: Int)

  object TransientField {
    implicit lazy val schema: Schema[TransientField]   = Schema.derived
    implicit lazy val codec: BsonCodec[TransientField] = BsonSchemaCodec.bsonCodec(schema)
  }
}
