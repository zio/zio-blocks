package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}

/**
 * Serializable expression for migration value computations. Used for defaults,
 * transforms, and combiners. Fully serializable (no closures, no Schema refs).
 */
sealed trait MigrationExpr {

  /**
   * Evaluates this expression with the given root DynamicValue as context.
   * Returns a single value (e.g. for AddField default) or fails with path info.
   */
  def eval(root: DynamicValue): Either[SchemaError, DynamicValue]
}

object MigrationExpr {

  /** A constant value. */
  final case class Literal(value: DynamicValue) extends MigrationExpr {
    def eval(root: DynamicValue): Either[SchemaError, DynamicValue] = Right(value)
  }

  /** Value at the given path from the root. */
  final case class SourcePath(path: DynamicOptic) extends MigrationExpr {
    def eval(root: DynamicValue): Either[SchemaError, DynamicValue] =
      root.get(path).one
  }

  /** Combine values from multiple paths (e.g. firstName + " " + lastName). */
  final case class Combine(sourcePaths: Chunk[DynamicOptic], op: MigrationAction.CombineOp) extends MigrationExpr {
    def eval(root: DynamicValue): Either[SchemaError, DynamicValue] =
      op.eval(root, sourcePaths)
  }

  /** Describes how to split one value into multiple target paths (used by [[MigrationBuilder.split]]). */
  final case class Split(targetPaths: Chunk[DynamicOptic], op: MigrationAction.SplitOp)

  /** Build a literal from a DynamicValue. */
  def literal(value: DynamicValue): MigrationExpr = Literal(value)

  /** Build an expression that reads from the given path. */
  def fromPath(path: DynamicOptic): MigrationExpr = SourcePath(path)

  /** Build a combine expression for string concatenation. */
  def stringConcat(separator: String)(paths: DynamicOptic*): MigrationExpr =
    Combine(Chunk.from(paths), MigrationAction.CombineOp.StringConcat(separator))

  /** Build a split expression for splitting a string into target paths. */
  def stringSplit(separator: String)(targetPaths: DynamicOptic*): Split =
    Split(Chunk.from(targetPaths), MigrationAction.SplitOp.StringSplit(separator))
}
