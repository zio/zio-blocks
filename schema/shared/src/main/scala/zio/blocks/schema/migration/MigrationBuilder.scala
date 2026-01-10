package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A type-safe builder for creating migrations between schema versions.
 * 
 * The builder provides a fluent API with compile-time validation in Scala 3
 * and string-based API in Scala 2.
 * 
 * Scala 3 Example:
 * {{{
 *   val migration = Migration.builder[PersonV0, PersonV1]
 *     .renameField(_.firstName, _.fullName)  // Type-safe!
 *     .addField("country", "USA")
 *     .build
 * }}}
 * 
 * Scala 2 Example:
 * {{{
 *   val migration = Migration.builder[PersonV0, PersonV1]
 *     .renameField("firstName", "fullName")  // String-based
 *     .addField("country", "USA")
 *     .build
 * }}}
 * 
 * @tparam A The source schema type
 * @tparam B The target schema type
 */
final class MigrationBuilder[A, B](
  val actions: Vector[MigrationAction]
)(implicit val fromSchema: Schema[A], val toSchema: Schema[B])
extends MigrationBuilderPlatform[A, B] {
  
  /**
   * Add a new field with a constant default value.
   */
  def addField[T](fieldName: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = {
    MigrationBuilder[A, B](
      actions :+ MigrationAction.AddField(fieldName, schema.toDynamicValue(defaultValue))
    )(fromSchema, toSchema)
  }
  
  /**
   * Make an optional field required by extracting from Some or using a default.
   */
  def mandate[T](fieldName: String, defaultForNone: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = {
    MigrationBuilder[A, B](
      actions :+ MigrationAction.Mandate(fieldName, schema.toDynamicValue(defaultForNone))
    )(fromSchema, toSchema)
  }
  
  /**
   * Rename a case in an enum/variant.
   */
  def renameCase(oldCase: String, newCase: String): MigrationBuilder[A, B] = {
    MigrationBuilder[A, B](
      actions :+ MigrationAction.RenameCase(oldCase, newCase)
    )(fromSchema, toSchema)
  }
  
  /**
   * Remove a case from an enum/variant.
   */
  def removeCase(caseName: String): MigrationBuilder[A, B] = {
    MigrationBuilder[A, B](
      actions :+ MigrationAction.RemoveCase(caseName)
    )(fromSchema, toSchema)
  }
  
  /**
   * Build the final migration.
   */
  def build: Migration[A, B] = {
    Migration[A, B](DynamicMigration(actions))(fromSchema, toSchema)
  }
}

object MigrationBuilder {
  /**
   * Create a new migration builder.
   */
  def apply[A, B](implicit fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](Vector.empty)(fromSchema, toSchema)
  }
  
  /**
   * Internal constructor for creating builders with actions (used by macros).
   */
  def apply[A, B](actions: Vector[MigrationAction])(implicit fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](actions)(fromSchema, toSchema)
  }
}
