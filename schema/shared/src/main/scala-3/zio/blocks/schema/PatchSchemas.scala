package zio.blocks.schema

// Schema instances for patch types.
// Note: In Scala 2, Schema.derived macro calls cannot be used in the same compilation run
// as the type definitions, so these schemas are defined in a separate source directory.
// For symmetry, Scala 3 uses the same approach.

object PatchSchemas {

  implicit val stringOpSchema: Schema[Patch.StringOp] = Schema.derived

  implicit val primitiveOpSchema: Schema[Patch.PrimitiveOp] = Schema.derived

  implicit val seqOpSchema: Schema[Patch.SeqOp] = Schema.derived

  implicit val mapOpSchema: Schema[Patch.MapOp] = Schema.derived

  implicit val operationSchema: Schema[Patch.Operation] = Schema.derived

  implicit val dynamicOpticNodeSchema: Schema[DynamicOptic.Node] = Schema.derived

  implicit val dynamicPatchOpSchema: Schema[Patch.DynamicPatchOp] = Schema.derived

  implicit val dynamicPatchSchema: Schema[DynamicPatch] = Schema.derived

  implicit val patchModeSchema: Schema[PatchMode] = Schema.derived
}
