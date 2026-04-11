package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicValue}

class MigrationBuilder[A, B](using schemaA: Schema[A], schemaB: Schema[B]) {
  private var actions = Vector.empty[MigrationAction]

  def addField(target: String, default: DynamicValue): this.type = {
    actions :+= AddField(List(target), default)
    this
  }
  def dropField(source: String, defaultForReverse: DynamicValue = DynamicValue.Null): this.type = {
    actions :+= DropField(List(source), defaultForReverse)
    this
  }
  def renameField(from: String, to: String): this.type = {
    actions :+= Rename(List(from), to)
    this
  }

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), schemaA, schemaB)

  def buildPartial: Migration[A, B] = build
}
