package zio.blocks.schema

import zio.blocks.typeid._

/**
 * TypeId Schema Instances (Scala 3 only).
 *
 * These enable serialization of TypeId, TypeRepr, and related types. Required
 * for round-trip serialization as per jdegoes' design.
 *
 * Mixed into SchemaCompanionVersionSpecific so that these implicits are
 * automatically available without explicit imports.
 *
 * Note: These are Scala 3 only because Scala 2's macro derivation cannot handle
 * existential types like TypeId[_] in TypeRepr.Ref.
 */
trait TypeIdSchemaInstances {
  implicit lazy val varianceSchema: Schema[Variance]           = Schema.derived[Variance]
  implicit lazy val kindSchema: Schema[Kind]                   = Schema.derived[Kind]
  implicit lazy val ownerSegmentSchema: Schema[Owner.Segment]  = Schema.derived[Owner.Segment]
  implicit lazy val ownerSchema: Schema[Owner]                 = Schema.derived[Owner]
  implicit lazy val typeBoundsSchema: Schema[TypeBounds]       = Schema.derived[TypeBounds]
  implicit lazy val typeParamSchema: Schema[TypeParam]         = Schema.derived[TypeParam]
  implicit lazy val constantSchema: Schema[Constant]           = Schema.derived[Constant]
  implicit lazy val memberSchema: Schema[Member]               = Schema.derived[Member]
  implicit lazy val typeReprSchema: Schema[TypeRepr]           = Schema.derived[TypeRepr]
  implicit lazy val annotationArgSchema: Schema[AnnotationArg] = Schema.derived[AnnotationArg]
  implicit lazy val annotationSchema: Schema[Annotation]       = Schema.derived[Annotation]
  implicit lazy val enumCaseParamSchema: Schema[EnumCaseParam] = Schema.derived[EnumCaseParam]
  implicit lazy val enumCaseInfoSchema: Schema[EnumCaseInfo]   = Schema.derived[EnumCaseInfo]
  implicit lazy val typeDefKindSchema: Schema[TypeDefKind]     = Schema.derived[TypeDefKind]
  implicit lazy val dynamicTypeIdSchema: Schema[DynamicTypeId] = Schema.derived[DynamicTypeId]
  implicit lazy val typeIdAnySchema: Schema[TypeId[Any]]       =
    dynamicTypeIdSchema.transformOrFail[TypeId[Any]](
      dynamicId => Right(new TypeId[Any](dynamicId)),
      typeId => typeId.dynamic
    )

  // Alias for TypeId[?] / TypeId[_] wildcard usage in tests
  implicit lazy val typeIdWildcardSchema: Schema[TypeId[?]] = typeIdAnySchema.asInstanceOf[Schema[TypeId[?]]]
}
