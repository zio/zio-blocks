package zio.blocks.schema.patch

import zio.blocks.schema.{DynamicPatch, PatchMode, Schema}

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
