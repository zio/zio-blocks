package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    var idx                   = 0
    val len                   = actions.length

    while (idx < len) {
      actions(idx)(current) match {
        case Right(updated) =>
          current = updated
          idx += 1
        case Left(error) =>
          return Left(error)
      }
    }

    Right(current)
  }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  def isEmpty: Boolean = actions.isEmpty

  def size: Int = actions.size

  def prepend(action: MigrationAction): DynamicMigration =
    DynamicMigration(action +: actions)

  def append(action: MigrationAction): DynamicMigration =
    DynamicMigration(actions :+ action)

  def applyOrElse(value: DynamicValue): DynamicValue =
    apply(value).getOrElse(value)

  def applyOption(value: DynamicValue): Option[DynamicValue] =
    apply(value).toOption

  override def equals(that: Any): Boolean = that match {
    case m: DynamicMigration => actions == m.actions
    case _                   => false
  }

  override def hashCode: Int = actions.hashCode

  override def toString: String =
    if (actions.isEmpty) "DynamicMigration.identity"
    else s"DynamicMigration(${actions.length} actions)"
}

object DynamicMigration {

  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)

  def compose(migrations: DynamicMigration*): DynamicMigration =
    migrations.foldLeft(identity)(_ ++ _)

  def addField(fieldName: String, defaultValue: DynamicValue): DynamicMigration =
    single(MigrationAction.AddField(zio.blocks.schema.DynamicOptic.root, fieldName, defaultValue))

  def dropField(fieldName: String, defaultForReverse: Option[DynamicValue] = None): DynamicMigration =
    single(MigrationAction.DropField(zio.blocks.schema.DynamicOptic.root, fieldName, defaultForReverse))

  def renameField(from: String, to: String): DynamicMigration =
    single(MigrationAction.Rename(zio.blocks.schema.DynamicOptic.root, from, to))

  def transformField(fieldName: String, transform: SchemaExpr): DynamicMigration =
    single(
      MigrationAction.TransformValue(
        zio.blocks.schema.DynamicOptic.root.field(fieldName),
        transform
      )
    )

  def changeFieldType(fieldName: String, converter: SchemaExpr): DynamicMigration =
    single(MigrationAction.ChangeType(zio.blocks.schema.DynamicOptic.root, fieldName, converter))

  def mandateField(fieldName: String, defaultValue: DynamicValue): DynamicMigration =
    single(MigrationAction.Mandate(zio.blocks.schema.DynamicOptic.root, fieldName, defaultValue))

  def optionalizeField(fieldName: String): DynamicMigration =
    single(MigrationAction.Optionalize(zio.blocks.schema.DynamicOptic.root, fieldName))

  def renameCase(from: String, to: String): DynamicMigration =
    single(MigrationAction.RenameCase(zio.blocks.schema.DynamicOptic.root, from, to))

  def transformElements(transform: SchemaExpr): DynamicMigration =
    single(MigrationAction.TransformElements(zio.blocks.schema.DynamicOptic.root, transform))

  def transformMapKeys(transform: SchemaExpr): DynamicMigration =
    single(MigrationAction.TransformKeys(zio.blocks.schema.DynamicOptic.root, transform))

  def transformMapValues(transform: SchemaExpr): DynamicMigration =
    single(MigrationAction.TransformValues(zio.blocks.schema.DynamicOptic.root, transform))

  def joinFields(sources: Vector[String], target: String, joinExpr: SchemaExpr): DynamicMigration =
    single(MigrationAction.Join(zio.blocks.schema.DynamicOptic.root, sources, target, joinExpr))

  def splitField(source: String, targets: Vector[String], splitExpr: SchemaExpr): DynamicMigration =
    single(MigrationAction.Split(zio.blocks.schema.DynamicOptic.root, source, targets, splitExpr))
}
