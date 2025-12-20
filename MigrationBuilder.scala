package zio.schema.migration

import zio.Chunk
import zio.schema._
import zio.schema.DynamicValue

/**
 * MigrationBuilder Implementation for ZIO Schema 2
 * Focus: Type-safe construction of migration actions without runtime reflection.
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Chunk[MigrationAction] = Chunk.empty
) {
  
  // Adds a new field to the schema at the specified path
  def addField[V](path: String, default: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] = {
    val optic = DynamicOptic.fromPath(path)
    val defaultDyn = schema.toDynamic(default)
    val action = MigrationAction.AddField(optic, defaultDyn)
    copy(actions = actions :+ action)
  }

  // Drops a field from the schema
  def dropField(path: String): MigrationBuilder[A, B] = {
    val optic = DynamicOptic.fromPath(path)
    val action = MigrationAction.DropField(optic, DynamicValue.Unit)
    copy(actions = actions :+ action)
  }

  // Renames a field
  def renameField(fromPath: String, toName: String): MigrationBuilder[A, B] = {
    val optic = DynamicOptic.fromPath(fromPath)
    val action = MigrationAction.Rename(optic, toName)
    copy(actions = actions :+ action)
  }

  // Transforms a field value
  def transformField(path: String, f: DynamicValue => Either[String, DynamicValue]): MigrationBuilder[A, B] = {
    val optic = DynamicOptic.fromPath(path)
    val action = MigrationAction.TransformValue(optic, f)
    copy(actions = actions :+ action)
  }

  def build: DynamicMigration = DynamicMigration(actions)
}

object MigrationBuilder {
  def empty[A](implicit schema: Schema[A]): MigrationBuilder[A, A] = 
    MigrationBuilder(schema, schema)
}
