package zio.blocks.schema.patch

import zio.blocks.schema.{DynamicPatch, PatchMode, Schema}

/**
 * Schema instances for patch types. These are defined in test code to avoid
 * Scala 2 macro issues where Schema.derived cannot be used in the same
 * compilation unit as the Schema definition.
 */
object PatchSchemas {

  implicit val stringOpSchema: Schema[StringOp] = Schema.derived

  implicit val primitiveOpSchema: Schema[PrimitiveOp] = Schema.derived

  implicit val seqOpSchema: Schema[SeqOp] = Schema.derived

  implicit val mapOpSchema: Schema[MapOp] = Schema.derived

  implicit val operationSchema: Schema[Operation] = Schema.derived

  implicit val patchPathSchema: Schema[PatchPath] = Schema.derived

  implicit val dynamicPatchOpSchema: Schema[DynamicPatchOp] = Schema.derived

  implicit val dynamicPatchSchema: Schema[DynamicPatch] = Schema.derived

  implicit val patchModeSchema: Schema[PatchMode] = Schema.derived
}
