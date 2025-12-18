package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  def addField(target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(target)
    copy(actions = actions :+ MigrationAction.AddField(optic, default))
  }

  def dropField(source: A => Any, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.DropField(optic, defaultForReverse))
  }

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = {
    val fromOptic = Macro.toPath(from)
    val toOptic = Macro.toPath(to)
    val toName = toOptic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target must be a field")
    }
    copy(actions = actions :+ MigrationAction.RenameField(fromOptic, toName))
  }

  def transformField(from: A => Any, to: B => Any, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val path = Macro.toPath(from)
    copy(actions = actions :+ MigrationAction.TransformValue(path, transform))
  }

  def mandateField(source: A => Option[Any], target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val path = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.Mandate(path, default))
  }

  def optionalizeField(source: A => Any, target: B => Option[Any]): MigrationBuilder[A, B] = {
    val path = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.Optionalize(path))
  }

  def changeFieldType(source: A => Any, target: B => Any, converter: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val path = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.ChangeType(path, converter))
  }

  def joinFields(target: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(target, sourcePaths, combiner))

  def splitField(source: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(source, targetPaths, splitter))

  // Legacy/Overloaded methods
  def addField(target: DynamicOptic, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(target, default))

  def dropField(source: DynamicOptic, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(source, defaultForReverse))

  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameField(from, to))

  def transformField(from: DynamicOptic, unusedTo: DynamicOptic, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(from, transform))
    
  def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  def transformCase[SumA, CaseA, SumB, CaseB](path: DynamicOptic, caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]): MigrationBuilder[A, B] =
    ???

  def build: Migration[A, B] =
    Macro.validateMigration(this) match {
      case Right(migration) => migration
      case Left(error) => throw new RuntimeException(s"Migration validation failed: $error")
    }

  def buildPartial: Migration[A, B] =
    Migration(
      DynamicMigration(actions),
      sourceSchema,
      targetSchema
    )
}
