package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * Builder for constructing migrations from path-based actions. Use path-based
 * APIs (DynamicOptic) to specify source and target locations. No macro
 * validation in [[buildPartial]]; [[build]] is equivalent for this implementation.
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Chunk[MigrationAction]
) {

  def addField(at: DynamicOptic, default: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(at, default))

  def dropField(at: DynamicOptic, defaultForReverse: MigrationExpr = MigrationExpr.Literal(DynamicValue.Null)): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(at, defaultForReverse))

  def renameField(from: DynamicOptic, toName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(from, toName))

  def transformField(at: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, transform))

  def mandateField(at: DynamicOptic, default: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(at, default))

  def optionalizeField(at: DynamicOptic): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(at))

  def changeFieldType(at: DynamicOptic, converter: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(at, converter))

  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, from, to))

  def transformCase(at: DynamicOptic, caseActions: Chunk[MigrationAction]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(at, caseActions))

  def transformElements(at: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, transform))

  def transformKeys(at: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, transform))

  def transformValues(at: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, transform))

  def join(at: DynamicOptic, sourcePaths: Chunk[DynamicOptic], combineOp: MigrationExpr.CombineOp): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(at, sourcePaths, combineOp))

  def split(at: DynamicOptic, targetPaths: Chunk[DynamicOptic], splitOp: MigrationExpr.SplitOp): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(at, targetPaths, splitOp))

  /** Build migration (no macro validation in this implementation). */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /** Build migration without full validation (same as build here). */
  def buildPartial: Migration[A, B] = build
}

object MigrationBuilder {

  def apply[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Chunk.empty)
}
