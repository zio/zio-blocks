package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) extends MigrationBuilderVersionSpecific[A, B] {

  def addFieldAt(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(path, fieldName, defaultValue))

  def dropFieldAt(path: DynamicOptic, fieldName: String, defaultForReverse: Option[DynamicValue] = None): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(path, fieldName, defaultForReverse))

  def renameFieldAt(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(path, from, to))

  def transformFieldAt(path: DynamicOptic, transform: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(path, transform))

  def mandateFieldAt(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(path, fieldName, defaultValue))

  def optionalizeFieldAt(path: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(path, fieldName))

  def changeFieldTypeAt(path: DynamicOptic, fieldName: String, converter: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(path, fieldName, converter))

  def joinFieldsAt(path: DynamicOptic, sources: Vector[String], target: String, joinExpr: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(path, sources, target, joinExpr))

  def splitFieldAt(path: DynamicOptic, source: String, targets: Vector[String], splitExpr: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(path, source, targets, splitExpr))

  def renameCaseAt(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(path, from, to))

  def transformCaseAt(path: DynamicOptic, caseName: String, caseActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(path, caseName, caseActions))

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCaseAt(DynamicOptic.root, from, to)

  def transformElementsAt(path: DynamicOptic, transform: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(path, transform))

  def transformKeysAt(path: DynamicOptic, transform: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(path, transform))

  def transformValuesAt(path: DynamicOptic, transform: SchemaExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(path, transform))

  def addField(fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    addFieldAt(DynamicOptic.root, fieldName, defaultValue)

  def addFieldTyped[T](fieldName: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    addFieldAt(DynamicOptic.root, fieldName, schema.toDynamicValue(defaultValue))

  def dropField(fieldName: String): MigrationBuilder[A, B] =
    dropFieldAt(DynamicOptic.root, fieldName, None)

  def dropFieldWithDefault(fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    dropFieldAt(DynamicOptic.root, fieldName, Some(defaultForReverse))

  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    renameFieldAt(DynamicOptic.root, from, to)

  def transformField(fieldName: String, transform: SchemaExpr): MigrationBuilder[A, B] =
    transformFieldAt(DynamicOptic.root.field(fieldName), transform)

  def mandateField(fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    mandateFieldAt(DynamicOptic.root, fieldName, defaultValue)

  def mandateFieldTyped[T](fieldName: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    mandateFieldAt(DynamicOptic.root, fieldName, schema.toDynamicValue(defaultValue))

  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    optionalizeFieldAt(DynamicOptic.root, fieldName)

  def changeFieldType(fieldName: String, converter: SchemaExpr): MigrationBuilder[A, B] =
    changeFieldTypeAt(DynamicOptic.root, fieldName, converter)

  def joinFields(sources: Seq[String], target: String, joinExpr: SchemaExpr): MigrationBuilder[A, B] =
    joinFieldsAt(DynamicOptic.root, sources.toVector, target, joinExpr)

  def splitField(source: String, targets: Seq[String], splitExpr: SchemaExpr): MigrationBuilder[A, B] =
    splitFieldAt(DynamicOptic.root, source, targets.toVector, splitExpr)

  def transformElements(transform: SchemaExpr): MigrationBuilder[A, B] =
    transformElementsAt(DynamicOptic.root, transform)

  def transformKeys(transform: SchemaExpr): MigrationBuilder[A, B] =
    transformKeysAt(DynamicOptic.root, transform)

  def transformValues(transform: SchemaExpr): MigrationBuilder[A, B] =
    transformValuesAt(DynamicOptic.root, transform)

  def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    copy(actions = actions :+ action)

  def withActions(newActions: MigrationAction*): MigrationBuilder[A, B] =
    copy(actions = actions ++ newActions)

  def buildPartial: Migration[A, B] =
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))

  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)

  def getActions: Vector[MigrationAction] = actions

  def size: Int = actions.size
}

trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] => }

object MigrationBuilder {
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
