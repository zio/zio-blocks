package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

sealed trait MigrationAction {
  def at: List[String]
  def reverse: MigrationAction
}

final case class AddField(at: List[String], default: DynamicValue) extends MigrationAction {
  def reverse = DropField(at, default)
}
final case class DropField(at: List[String], defaultForReverse: DynamicValue) extends MigrationAction {
  def reverse = AddField(at, defaultForReverse)
}
final case class Rename(at: List[String], to: String) extends MigrationAction {
  def reverse = Rename(at.init :+ to, at.last)
}
final case class TransformValue(at: List[String], transform: String) extends MigrationAction {
  def reverse = this
}
