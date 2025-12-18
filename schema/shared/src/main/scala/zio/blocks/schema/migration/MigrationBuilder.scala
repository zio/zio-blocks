package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  // Selector-based API methods
  inline def addField(inline target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(target)
    copy(actions = actions :+ MigrationAction.AddField(optic, default))
  }

  inline def dropField(inline source: A => Any, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.DropField(optic, defaultForReverse))
  }

  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] = {
    val fromOptic = Macro.toPath(from)
    val toOptic = Macro.toPath(to)
    val toName = toOptic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target must be a field")
    }
    copy(actions = actions :+ MigrationAction.RenameField(fromOptic, toName))
  }

  inline def transformField(inline from: A => Any, inline to: B => Any, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(from)
    copy(actions = actions :+ MigrationAction.TransformValue(optic, transform))
  }

  // mandateField and optionalizeField temporarily disabled
  // inline def mandateField(inline source: A => Option[Any], inline target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
  //   val optic = Macro.toPath(source)
  //   copy(actions = actions :+ MigrationAction.Mandate(optic, default))
  // }

  // inline def optionalizeField(inline source: A => Any, inline target: B => Option[Any]): MigrationBuilder[A, B] = {
  //   val optic = Macro.toPath(source)
  //   copy(actions = actions :+ MigrationAction.Optionalize(optic))
  // }

  inline def changeFieldType(inline source: A => Any, inline target: B => Any, converter: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    val optic = Macro.toPath(source)
    copy(actions = actions :+ MigrationAction.ChangeType(optic, converter))
  }

  // joinFields and splitField temporarily disabled
  // def joinFields(target: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
  //   copy(actions = actions :+ MigrationAction.Join(target, sourcePaths, combiner))

  // def splitField(source: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
  //   copy(actions = actions :+ MigrationAction.Split(source, targetPaths, splitter))

  // Legacy DynamicOptic-based methods for compatibility
  def addField(target: DynamicOptic, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(target, default))

  def dropField(source: DynamicOptic, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(source, defaultForReverse))

  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameField(from, to))

  def transformField(from: DynamicOptic, unusedTo: DynamicOptic, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(from, transform))

  def build: Migration[A, B] =
    Migration(
      DynamicMigration(actions),
      sourceSchema,
      targetSchema
    )
}


