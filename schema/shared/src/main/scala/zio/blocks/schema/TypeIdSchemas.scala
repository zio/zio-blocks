package zio.blocks.schema

import zio.blocks.typeid._

/**
 * Provides implicit Schema instances for all TypeId-related types. This enables
 * serialization and deserialization of TypeId and TypeRepr through any
 * Schema-supported format (JSON, MessagePack, etc.).
 *
 * Note: These schemas handle the circular dependencies between TypeId and
 * TypeRepr via lazy initialization.
 */
object TypeIdSchemas {

  // Leaf types (no dependencies on other typeid types)
  implicit lazy val varianceSchema: Schema[Variance] = Schema.derived

  implicit lazy val kindSchema: Schema[Kind] = Schema.derived

  // TypeBounds has forward reference to TypeRepr
  implicit lazy val typeBoundsSchema: Schema[TypeBounds] = Schema.derived

  // TypeParam depends on Variance, TypeBounds, Kind
  implicit lazy val typeParamSchema: Schema[TypeParam] = Schema.derived

  // Owner hierarchy
  implicit lazy val ownerSegmentSchema: Schema[Owner.Segment] = Schema.derived
  implicit lazy val ownerSchema: Schema[Owner]                = Schema.derived

  // Constant values
  implicit lazy val constantSchema: Schema[Constant] = Schema.derived

  // Annotation hierarchy
  implicit lazy val annotationArgSchema: Schema[AnnotationArg] = Schema.derived
  implicit lazy val annotationSchema: Schema[Annotation]       = Schema.derived

  // Param and ParamClause
  implicit lazy val paramSchema: Schema[Param]             = Schema.derived
  implicit lazy val paramClauseSchema: Schema[ParamClause] = Schema.derived

  // Member
  implicit lazy val memberSchema: Schema[Member] = Schema.derived

  // Enum case types
  implicit lazy val enumCaseParamSchema: Schema[EnumCaseParam] = Schema.derived
  implicit lazy val enumCaseInfoSchema: Schema[EnumCaseInfo]   = Schema.derived

  // TypeDefKind (depends on TypeRepr via TypeAlias, etc.)
  implicit lazy val typeDefKindSchema: Schema[TypeDefKind] = Schema.derived

  // TypeRepr and TypeId (mutually recursive)
  implicit lazy val typeReprSchema: Schema[TypeRepr]        = Schema.derived
  implicit lazy val typeIdWildcardSchema: Schema[TypeId[_]] =
    Schema.derived[TypeId[Any]].asInstanceOf[Schema[TypeId[_]]]
}
