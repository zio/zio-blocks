package zio.blocks.schema.json

import zio.blocks.schema.Schema

trait JsonPatchCompanionVersionSpecific {
  import JsonPatch._

  // Schema instances derived automatically in Scala 3

  // StringOp schemas
  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] = Schema.derived
  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] = Schema.derived
  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] = Schema.derived
  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] = Schema.derived
  implicit lazy val stringOpSchema: Schema[StringOp]              = Schema.derived

  // PrimitiveOp schemas
  implicit lazy val primitiveOpNumberDeltaSchema: Schema[PrimitiveOp.NumberDelta] = Schema.derived
  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit]   = Schema.derived
  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp]                        = Schema.derived

  // ArrayOp schemas
  implicit lazy val arrayOpInsertSchema: Schema[ArrayOp.Insert] = Schema.derived
  implicit lazy val arrayOpAppendSchema: Schema[ArrayOp.Append] = Schema.derived
  implicit lazy val arrayOpDeleteSchema: Schema[ArrayOp.Delete] = Schema.derived
  implicit lazy val arrayOpModifySchema: Schema[ArrayOp.Modify] = Schema.derived
  implicit lazy val arrayOpSchema: Schema[ArrayOp]              = Schema.derived

  // ObjectOp schemas
  implicit lazy val objectOpAddSchema: Schema[ObjectOp.Add]       = Schema.derived
  implicit lazy val objectOpRemoveSchema: Schema[ObjectOp.Remove] = Schema.derived
  implicit lazy val objectOpModifySchema: Schema[ObjectOp.Modify] = Schema.derived
  implicit lazy val objectOpSchema: Schema[ObjectOp]              = Schema.derived

  // Op schemas
  implicit lazy val opSetSchema: Schema[Op.Set]                       = Schema.derived
  implicit lazy val opPrimitiveDeltaSchema: Schema[Op.PrimitiveDelta] = Schema.derived
  implicit lazy val opArrayEditSchema: Schema[Op.ArrayEdit]           = Schema.derived
  implicit lazy val opObjectEditSchema: Schema[Op.ObjectEdit]         = Schema.derived
  implicit lazy val opNestedSchema: Schema[Op.Nested]                 = Schema.derived
  implicit lazy val opSchema: Schema[Op]                              = Schema.derived

  // JsonPatchOp schema
  implicit lazy val jsonPatchOpSchema: Schema[JsonPatchOp] = Schema.derived

  // JsonPatch schema
  implicit lazy val schema: Schema[JsonPatch] = Schema.derived
}
