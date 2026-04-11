package zio.blocks.schema.migration

import zio.blocks.schema.Schema

class MigrationBuilder[A, B](using schemaA: Schema[A], schemaB: Schema[B]) {
  private var actions: Vector[MigrationAction] = Vector.empty
  private def optic[T](f: T => Any): DynamicOptic = DynamicOptic.fromSelector(f)

  // Record operations
  def addField(target: B => Any, default: SchemaExpr[A, _]): this.type = {
    actions :+= AddField(optic(target), default); this
  }
  def dropField(source: A => Any, defaultForReverse: SchemaExpr[B, _] = SchemaExpr.DefaultValue): this.type = {
    actions :+= DropField(optic(source), defaultForReverse); this
  }
  def renameField(from: A => Any, to: B => Any): this.type = {
    actions :+= Rename(optic(from), ""); this
  }
  def transformField(from: A => Any, to: B => Any, transform: SchemaExpr[A, _]): this.type = {
    actions :+= TransformValue(optic(to), transform); this
  }
  def mandateField(source: A => Option[?], target: B => Any, default: SchemaExpr[A, _]): this.type = {
    actions :+= Mandate(optic(target), default); this
  }
  def optionalizeField(source: A => Any, target: B => Option[?]): this.type = {
    actions :+= Optionalize(optic(target)); this
  }
  def changeFieldType(source: A => Any, target: B => Any, converter: SchemaExpr[A, _]): this.type = {
    actions :+= ChangeType(optic(target), converter); this
  }

  // Enum operations
  def renameCase[SumA, SumB](from: String, to: String): this.type = {
    actions :+= RenameCase(DynamicOptic.root, from, to); this
  }
  def transformCase[SumA, CaseA, SumB, CaseB](): this.type = this

  // Collections
  def transformElements(at: A => Vector[?], transform: SchemaExpr[A, _]): this.type = {
    actions :+= TransformElements(optic(at), transform); this
  }
  def transformKeys(at: A => Map[?,?], transform: SchemaExpr[A, _]): this.type = {
    actions :+= TransformKeys(optic(at), transform); this
  }
  def transformValues(at: A => Map[?,?], transform: SchemaExpr[A, _]): this.type = {
    actions :+= TransformValues(optic(at), transform); this
  }

  def build: Migration[A, B] = Migration(DynamicMigration(actions), schemaA, schemaB)
  def buildPartial: Migration[A, B] = build
}
