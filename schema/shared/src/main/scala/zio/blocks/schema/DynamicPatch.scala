package zio.blocks.schema

/**
 * DynamicPatch represents an untyped patch that operates on DynamicValue. This
 * is the core implementation that Patch[A] wraps.
 */
final case class DynamicPatch(ops: Vector[DynamicPatch.DynamicPatchOp]) {

  /**
   * Apply this patch to a DynamicValue with the specified mode.
   */
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    var result = value
    var i      = 0
    while (i < ops.length) {
      val op = ops(i)
      DynamicPatch.applyOp(result, op, mode) match {
        case Right(r) => result = r
        case left     =>
          if (mode == PatchMode.Lenient) () // skip and continue
          else return left
      }
      i += 1
    }
    Right(result)
  }

  /**
   * Apply this patch to a DynamicValue with Strict mode.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    apply(value, PatchMode.Strict)

  /**
   * Compose this patch with another patch sequentially.
   */
  def ++(that: DynamicPatch): DynamicPatch =
    DynamicPatch(this.ops ++ that.ops)
}

object DynamicPatch {

  /**
   * A single operation in a dynamic patch, consisting of an optic path and an
   * operation.
   */
  final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation)

  /**
   * Empty patch (monoid identity).
   */
  val empty: DynamicPatch = DynamicPatch(Vector.empty)

  /**
   * Create a patch that sets a value at the root.
   */
  def set(value: DynamicValue): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.Set(value))))

  /**
   * Create a patch with a single operation at the root.
   */
  def apply(operation: Operation): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, operation)))

  /**
   * Apply a single operation to navigate to the target and apply the operation.
   */
  private def applyOp(
    value: DynamicValue,
    patchOp: DynamicPatchOp,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] =
    if (patchOp.optic.nodes.isEmpty) {
      // Apply directly to the value
      Operation.apply(value, patchOp.operation, mode)
    } else {
      // Navigate and apply
      navigateAndApply(value, patchOp.optic.nodes.toList, patchOp.operation, mode)
    }

  /**
   * Navigate through the optic path and apply the operation at the target.
   */
  private def navigateAndApply(
    value: DynamicValue,
    path: List[DynamicOptic.Node],
    operation: Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = path match {
    case Nil =>
      Operation.apply(value, operation, mode)

    case DynamicOptic.Node.Field(name) :: rest =>
      value match {
        case DynamicValue.Record(fields) =>
          val fieldIdx = fields.indexWhere(_._1 == name)
          if (fieldIdx < 0) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.FieldNotFound(name)))
          } else {
            navigateAndApply(fields(fieldIdx)._2, rest, operation, mode).map { modified =>
              DynamicValue.Record(fields.updated(fieldIdx, (name, modified)))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Record", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.AtIndex(index) :: rest =>
      value match {
        case DynamicValue.Sequence(elements) =>
          if (index < 0 || index >= elements.length) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.IndexOutOfBounds(index, elements.length)))
          } else {
            navigateAndApply(elements(index), rest, operation, mode).map { modified =>
              DynamicValue.Sequence(elements.updated(index, modified))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Sequence", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.AtMapKey(key) :: rest =>
      val keyDv = key.asInstanceOf[DynamicValue]
      value match {
        case DynamicValue.Map(entries) =>
          val entryIdx = entries.indexWhere(_._1 == keyDv)
          if (entryIdx < 0) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
          } else {
            navigateAndApply(entries(entryIdx)._2, rest, operation, mode).map { modified =>
              DynamicValue.Map(entries.updated(entryIdx, (keyDv, modified)))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Map", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.Case(name) :: rest =>
      value match {
        case DynamicValue.Variant(caseName, innerValue) =>
          if (caseName != name) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.TypeMismatch(name, caseName)))
          } else {
            navigateAndApply(innerValue, rest, operation, mode).map { modified =>
              DynamicValue.Variant(caseName, modified)
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Variant", value.getClass.getSimpleName)))
      }

    case _ :: rest =>
      // For other node types, skip navigation and apply to value directly
      navigateAndApply(value, rest, operation, mode)
  }
}
