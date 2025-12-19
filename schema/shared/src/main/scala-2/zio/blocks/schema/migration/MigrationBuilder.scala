package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  // Selector-based API methods - not supported in Scala 2
  def addField(target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def dropField(source: A => Any, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def transformField(from: A => Any, to: B => Any, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def mandateField(source: A => Option[Any], target: B => Any, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def optionalizeField(source: A => Any, target: B => Option[Any]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def changeFieldType(source: A => Any, target: B => Any, converter: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def joinFields(sources: (A => Any, A => Any), target: B => Any, join: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def splitField(source: A => Any, targets: (B => Any, B => Any), split: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  // Legacy DynamicOptic-based methods for compatibility
  def addField(target: DynamicOptic, default: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def dropField(source: DynamicOptic, defaultForReverse: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def transformField(from: DynamicOptic, unusedTo: DynamicOptic, transform: SchemaExpr[Any, _]): MigrationBuilder[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def build(): Either[MigrationError, Migration[A, B]] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }

  def buildPartial(): Migration[A, B] = {
    throw new UnsupportedOperationException("Migration building with macros is not supported in Scala 2")
  }
}