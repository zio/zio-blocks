package zio.blocks.schema

import zio.blocks.schema.diff._

final case class Patch[A](dynamicPatch: DynamicPatch, schema: Schema[A]) {

  def apply(value: A, mode: PatchMode = PatchMode.Strict): Either[SchemaError, A] = {
    val dyn = schema.toDynamicValue(value)
    dynamicPatch.apply(dyn)(mode).flatMap { res =>
      schema.fromDynamicValue(res)
    }
  }

  def ++(that: Patch[A]): Patch[A] = Patch(dynamicPatch ++ that.dynamicPatch, schema)
}

object Patch {
  def empty[A](implicit schema: Schema[A]): Patch[A] = Patch(DynamicPatch.empty, schema)

  /**
   * Constructs a patch that sets a value at the given path (Dynamic API).
   */
  def set[A](optic: DynamicOptic, value: DynamicValue)(implicit schema: Schema[A]): Patch[A] =
    Patch(DynamicPatch(Vector(DynamicPatchOp(optic, Operation.Set(value)))), schema)

  /**
   * Constructs a patch that sets a value at the given typed Optic path (Typed
   * API). This fixes the type mismatch errors in PatchSpec.
   */
  def set[S, A](optic: Optic[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] = {
    val dynOptic = optic.toDynamic
    val dynValue = schemaA.toDynamicValue(value)
    Patch(DynamicPatch(Vector(DynamicPatchOp(dynOptic, Operation.Set(dynValue)))), schemaS)
  }

  def diff[A](oldValue: A, newValue: A)(implicit schema: Schema[A]): Patch[A] = {
    val oldDyn = schema.toDynamicValue(oldValue)
    val newDyn = schema.toDynamicValue(newValue)
    Patch(Differ.diff(oldDyn, newDyn), schema)
  }
}
