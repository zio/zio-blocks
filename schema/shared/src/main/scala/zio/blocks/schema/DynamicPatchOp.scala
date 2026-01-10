package zio.blocks.schema

/**
 * A single patch operation combining a path (DynamicOptic) with an operation.
 *
 * @param optic
 *   The path to the target location in the value
 * @param operation
 *   The operation to apply at that location
 */
final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation)
