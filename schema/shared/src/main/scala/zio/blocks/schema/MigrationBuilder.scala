package zio.blocks.schema

final class MigrationBuilder[A, B](
  val actions: Vector[MigrationAction],
  val source: Schema[A],
  val target: Schema[B]
) extends MigrationBuilderMacros[A, B] {

  def addFieldCore(at: DynamicOptic, default: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.AddField(at, default), source, target)

  def dropFieldCore(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.DropField(at, defaultForReverse), source, target)

  def renameFieldCore(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Rename(at, from, to), source, target)

  def transformFieldCore(at: DynamicOptic, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValue(at, transform), source, target)

  def mandateFieldCore(at: DynamicOptic, default: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Mandate(at, default), source, target)

  def optionalizeFieldCore(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Optionalize(at, defaultForReverse), source, target)

  def changeFieldTypeCore(at: DynamicOptic, converter: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.ChangeType(at, converter), source, target)

  def renameCaseCore(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to), source, target)

  def transformCaseCore[CaseA, CaseB](caseName: String, caseMigration: MigrationBuilder[CaseA, CaseB]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformCase(DynamicOptic.root.caseOf(caseName), caseMigration.actions), source, target)

  def transformElementsCore(at: DynamicOptic, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformElements(at, transform), source, target)

  def transformKeysCore(at: DynamicOptic, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformKeys(at, transform), source, target)

  def transformValuesCore(at: DynamicOptic, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValues(at, transform), source, target)

  def buildPartial: Migration[A, B] = Migration(DynamicMigration(actions), source, target)
}

object MigrationBuilder {
  def make[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(Vector.empty, source, target)
}
