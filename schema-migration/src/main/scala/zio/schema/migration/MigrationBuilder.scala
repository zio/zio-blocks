package zio.schema.migration

import zio.schema._
import zio.Chunk
import scala.quoted._

/**
 * Fluent builder for constructing typed migrations.
 *
 * Supports both string-based and macro-based APIs:
 * - String: .addField[Int]("age", 0)
 * - Macro: .addFieldMacro(_.age, 0)
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Chunk[MigrationAction]
) {

  /**
   * Add a new field with a default value
   * In production: addField(_.age, 0) - extracts "age" via macro
   */
  def addField[T: Schema](fieldName: String, defaultValue: T): MigrationBuilder[A, B] = {
    val dynamic = DynamicValue.fromSchemaAndValue(implicitly[Schema[T]], defaultValue)
    val action = MigrationAction.AddField(FieldPath(fieldName), dynamic)
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop an existing field
   * In production: dropField(_.oldField) - extracts "oldField" via macro
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(FieldPath(fieldName))
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Rename a field
   * In production: renameField(_.oldName, _.newName) - extracts both via macro
   */
  def renameField(oldName: String, newName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.RenameField(
      FieldPath(oldName),
      FieldPath(newName)
    )
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Transform a field using a serializable transformation
   * Example: transformField("name", SerializableTransformation.Uppercase)
   */
  def transformField(
    fieldName: String,
    transformation: SerializableTransformation
  ): MigrationBuilder[A, B] = {
    val action = MigrationAction.TransformField(FieldPath(fieldName), transformation)
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)
  }

  // ===== Macro-Based API =====

  /**
   * Add a field using type-safe selector
   * Example: .addFieldMacro(_.age, 0)
   */
  inline def addFieldMacro[T: Schema](inline selector: B => T, defaultValue: T): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B, T]('this, 'selector, 'defaultValue) }

  /**
   * Drop a field using type-safe selector
   * Example: .dropFieldMacro(_.oldField)
   */
  inline def dropFieldMacro(inline selector: A => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropFieldImpl[A, B]('this, 'selector) }

  /**
   * Rename a field using type-safe selectors
   * Example: .renameFieldMacro(_.oldName, _.newName)
   */
  inline def renameFieldMacro(inline oldSelector: A => Any, inline newSelector: B => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('this, 'oldSelector, 'newSelector) }

  /**
   * Transform a field using type-safe selector
   * Example: .transformFieldMacro(_.name, SerializableTransformation.Uppercase)
   */
  inline def transformFieldMacro(
    inline selector: A => Any,
    transformation: SerializableTransformation
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformFieldImpl[A, B]('this, 'selector, 'transformation) }

  /**
   * Build the final migration
   */
  def build: Migration[A, B] = {
    val optimized = DynamicMigration(actions).optimize
    Migration(optimized, sourceSchema, targetSchema)
  }

  /**
   * Build without optimization (useful for debugging)
   */
  def buildUnoptimized: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {
  /**
   * Create a new builder
   */
  def apply[A: Schema, B: Schema]: MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](
      implicitly[Schema[A]],
      implicitly[Schema[B]],
      Chunk.empty
    )
}
