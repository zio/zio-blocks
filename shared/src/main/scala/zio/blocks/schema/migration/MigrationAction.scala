package zio.blocks.schema.migration

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

// Record actions
final case class AddField(at: DynamicOptic, default: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = DropField(at, default)
}
final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = AddField(at, defaultForReverse)
}
final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
  def reverse: MigrationAction = Rename(DynamicOptic(at.segments.init :+ to), at.segments.lastOption.getOrElse(""))
}
final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = this
}
final case class Mandate(at: DynamicOptic, default: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = Optionalize(at)
}
final case class Optionalize(at: DynamicOptic) extends MigrationAction {
  def reverse: MigrationAction = Mandate(at, SchemaExpr.DefaultValue)
}
final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = Split(at, sourcePaths, combiner)
}
final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = Join(at, targetPaths, splitter)
}
final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = this
}

// Enum actions
final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
  def reverse: MigrationAction = RenameCase(at, to, from)
}
final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
  def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
}

// Collection/Map actions
final case class TransformElements(at: DynamicOptic, transform: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = this
}
final case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = this
}
final case class TransformValues(at: DynamicOptic, transform: SchemaExpr[?,?]) extends MigrationAction {
  def reverse: MigrationAction = this
}
