package zio.blocks.schema.patch

import zio.blocks.schema.{DynamicPatch, PatchMode, Schema}

// Schema instances for patch types.
// Note: These schemas are in test code due to Scala 2 macro limitations.
// In Scala 2, Schema.derived macro calls cannot be used in the same compilation run
// as the type definitions.

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
