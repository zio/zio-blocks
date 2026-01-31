package zio.blocks.schema.patch

import zio.blocks.schema.Schema

trait DynamicPatchCompanionVersionSpecific {

  import DynamicPatch._

  // Schema instances derived automatically in Scala 3

  // StringOp schemas
  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] = Schema.derived
  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] = Schema.derived
  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] = Schema.derived
  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] = Schema.derived
  implicit lazy val stringOpSchema: Schema[StringOp]              = Schema.derived

  // PrimitiveOp schemas
  implicit lazy val primitiveOpIntDeltaSchema: Schema[PrimitiveOp.IntDelta]                     = Schema.derived
  implicit lazy val primitiveOpLongDeltaSchema: Schema[PrimitiveOp.LongDelta]                   = Schema.derived
  implicit lazy val primitiveOpDoubleDeltaSchema: Schema[PrimitiveOp.DoubleDelta]               = Schema.derived
  implicit lazy val primitiveOpFloatDeltaSchema: Schema[PrimitiveOp.FloatDelta]                 = Schema.derived
  implicit lazy val primitiveOpShortDeltaSchema: Schema[PrimitiveOp.ShortDelta]                 = Schema.derived
  implicit lazy val primitiveOpByteDeltaSchema: Schema[PrimitiveOp.ByteDelta]                   = Schema.derived
  implicit lazy val primitiveOpBigIntDeltaSchema: Schema[PrimitiveOp.BigIntDelta]               = Schema.derived
  implicit lazy val primitiveOpBigDecimalDeltaSchema: Schema[PrimitiveOp.BigDecimalDelta]       = Schema.derived
  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit]                 = Schema.derived
  implicit lazy val primitiveOpInstantDeltaSchema: Schema[PrimitiveOp.InstantDelta]             = Schema.derived
  implicit lazy val primitiveOpDurationDeltaSchema: Schema[PrimitiveOp.DurationDelta]           = Schema.derived
  implicit lazy val primitiveOpLocalDateDeltaSchema: Schema[PrimitiveOp.LocalDateDelta]         = Schema.derived
  implicit lazy val primitiveOpLocalDateTimeDeltaSchema: Schema[PrimitiveOp.LocalDateTimeDelta] =
    Schema.derived
  implicit lazy val primitiveOpPeriodDeltaSchema: Schema[PrimitiveOp.PeriodDelta] = Schema.derived
  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp]                        = Schema.derived

  // SeqOp schemas
  implicit lazy val seqOpInsertSchema: Schema[SeqOp.Insert] = Schema.derived
  implicit lazy val seqOpAppendSchema: Schema[SeqOp.Append] = Schema.derived
  implicit lazy val seqOpDeleteSchema: Schema[SeqOp.Delete] = Schema.derived
  implicit lazy val seqOpModifySchema: Schema[SeqOp.Modify] = Schema.derived
  implicit lazy val seqOpSchema: Schema[SeqOp]              = Schema.derived

  // MapOp schemas
  implicit lazy val mapOpAddSchema: Schema[MapOp.Add]       = Schema.derived
  implicit lazy val mapOpRemoveSchema: Schema[MapOp.Remove] = Schema.derived
  implicit lazy val mapOpModifySchema: Schema[MapOp.Modify] = Schema.derived
  implicit lazy val mapOpSchema: Schema[MapOp]              = Schema.derived

  // Operation schemas
  implicit lazy val operationSetSchema: Schema[Operation.Set]                       = Schema.derived
  implicit lazy val operationPrimitiveDeltaSchema: Schema[Operation.PrimitiveDelta] = Schema.derived
  implicit lazy val operationSequenceEditSchema: Schema[Operation.SequenceEdit]     = Schema.derived
  implicit lazy val operationMapEditSchema: Schema[Operation.MapEdit]               = Schema.derived
  implicit lazy val operationPatchSchema: Schema[Operation.Patch]                   = Schema.derived
  implicit lazy val operationSchema: Schema[Operation]                              = Schema.derived

  // DynamicPatchOp and DynamicPatch schemas
  implicit lazy val dynamicPatchOpSchema: Schema[DynamicPatchOp] = Schema.derived
  implicit lazy val dynamicPatchSchema: Schema[DynamicPatch]     = Schema.derived
}
