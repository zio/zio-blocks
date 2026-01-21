package zio.schema.bson

import zio.bson._
import zio.blocks.schema.{Schema, Modifier}
import zio.schema.bson.BsonSchemaCodec.Config

object MixedConfig {

  sealed trait NoDiscriminator

  object NoDiscriminator {
    @Modifier.rename("A")
    case class A(@Modifier.rename("a") a: String) extends NoDiscriminator
    @Modifier.rename("B")
    case class B(@Modifier.rename("b") b: String) extends NoDiscriminator
    @Modifier.rename("C")
    case class C(@Modifier.rename("c") c: String) extends NoDiscriminator

    implicit lazy val schema: Schema[NoDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[NoDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  sealed trait WithoutDiscriminator

  object WithoutDiscriminator {
    case class A(s: String) extends WithoutDiscriminator
    case class B(s: String) extends WithoutDiscriminator

    implicit lazy val schema: Schema[WithoutDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  @Modifier.config("bson.discriminator", "$type")
  sealed trait WithDiscriminator

  object WithDiscriminator {
    case class A(s: String) extends WithDiscriminator
    case class B(s: String) extends WithDiscriminator

    implicit lazy val schema: Schema[WithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[WithDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
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

  @Modifier.config("bson.discriminator", "$type")
  sealed trait CaseNameWithDiscriminator

  object CaseNameWithDiscriminator {
    @Modifier.rename("aName")
    case class A(s: String) extends CaseNameWithDiscriminator
    @Modifier.rename("bName")
    case class B(s: String) extends CaseNameWithDiscriminator

    implicit lazy val schema: Schema[CaseNameWithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameWithDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  sealed trait CaseNameAliasesWithoutDiscriminator

  object CaseNameAliasesWithoutDiscriminator {
    @Modifier.rename("aName") @Modifier.alias("aAlias1") @Modifier.alias("aAlias2")
    case class A(s: String) extends CaseNameAliasesWithoutDiscriminator
    @Modifier.alias("bAlias1") @Modifier.alias("bAlias2")
    case class B(s: String) extends CaseNameAliasesWithoutDiscriminator

    implicit lazy val schema: Schema[CaseNameAliasesWithoutDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameAliasesWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  @Modifier.config("bson.discriminator", "$type")
  sealed trait CaseNameAliasesWithDiscriminator

  object CaseNameAliasesWithDiscriminator {
    @Modifier.rename("aName") @Modifier.alias("aAlias1") @Modifier.alias("aAlias2")
    case class A(s: String) extends CaseNameAliasesWithDiscriminator
    @Modifier.alias("bAlias1") @Modifier.alias("bAlias2")
    case class B(s: String) extends CaseNameAliasesWithDiscriminator

    implicit lazy val schema: Schema[CaseNameAliasesWithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[CaseNameAliasesWithDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class FieldName(@Modifier.rename("customName") a: String)

  object FieldName {
    implicit lazy val schema: Schema[FieldName]   = Schema.derived
    implicit lazy val codec: BsonCodec[FieldName] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class FieldDefaultValue(a: String = "defaultValue")

  object FieldDefaultValue {
    implicit lazy val schema: Schema[FieldDefaultValue]   = Schema.derived
    implicit lazy val codec: BsonCodec[FieldDefaultValue] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class AllowExtraFields(a: String)

  object AllowExtraFields {
    implicit lazy val schema: Schema[AllowExtraFields]   = Schema.derived
    implicit lazy val codec: BsonCodec[AllowExtraFields] =
      BsonSchemaCodec.bsonCodec(schema, Config.default.copy(allowExtraFields = true))
  }

  case class RejectExtraFields(a: String)

  object RejectExtraFields {
    implicit lazy val schema: Schema[RejectExtraFields]   = Schema.derived
    implicit lazy val codec: BsonCodec[RejectExtraFields] =
      BsonSchemaCodec.bsonCodec(schema, Config.default.copy(allowExtraFields = false))
  }

  sealed trait TransientCaseWithoutDiscriminator

  object TransientCaseWithoutDiscriminator {
    case class A(s: String) extends TransientCaseWithoutDiscriminator
    case class B(s: String) extends TransientCaseWithoutDiscriminator
    @Modifier.transient
    case class C(s: String) extends TransientCaseWithoutDiscriminator

    implicit lazy val schema: Schema[TransientCaseWithoutDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[TransientCaseWithoutDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  @Modifier.config("bson.discriminator", "$type")
  sealed trait TransientCaseWithDiscriminator

  object TransientCaseWithDiscriminator {
    case class A(s: String) extends TransientCaseWithDiscriminator
    case class B(s: String) extends TransientCaseWithDiscriminator
    @Modifier.transient
    case class C(s: String) extends TransientCaseWithDiscriminator

    implicit lazy val schema: Schema[TransientCaseWithDiscriminator]   = Schema.derived
    implicit lazy val codec: BsonCodec[TransientCaseWithDiscriminator] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class TransientField(@Modifier.transient a: String = "defaultValue", b: Int)

  object TransientField {
    // Need default value for 'a' to be decodable if transient.
    // 'a' is String. Provide default "defaultValue" (from test expectation).
    implicit lazy val schema: Schema[TransientField]   = Schema.derived
    implicit lazy val codec: BsonCodec[TransientField] = BsonSchemaCodec.bsonCodec(schema)
  }
}
