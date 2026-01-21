package zio.schema.migration

import zio.blocks.schema._

sealed trait SerializedMigrationAction

object SerializedMigrationAction {
  final case class AddField(at: DynamicOptic, default: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class DropField(at: DynamicOptic, defaultForReverse: SerializedSchemaExpr)
      extends SerializedMigrationAction
  final case class Rename(at: DynamicOptic, to: String) extends SerializedMigrationAction
  final case class TransformValue(
    at: DynamicOptic,
    transform: SerializedSchemaExpr,
    inverse: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class Mandate(at: DynamicOptic, default: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class Optionalize(at: DynamicOptic)                            extends SerializedMigrationAction
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SerializedSchemaExpr,
    splitter: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SerializedSchemaExpr,
    combiner: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class ChangeType(at: DynamicOptic, converter: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class RenameCase(at: DynamicOptic, from: String, to: String)        extends SerializedMigrationAction
  final case class TransformCase(at: DynamicOptic, actions: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformElements(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformKeys(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformValues(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction

  implicit val schema: Schema[SerializedMigrationAction] = Schema.derived
}
