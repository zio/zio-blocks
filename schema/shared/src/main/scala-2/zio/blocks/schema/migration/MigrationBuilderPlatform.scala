package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 2).
 * Provides string-based field selection for Scala 2 compatibility.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>
  import scala.language.experimental.macros

  // ============================================================================
  // Selector-based Macros
  // ============================================================================

  def addField(target: B => Any, default: zio.blocks.schema.DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addFieldImpl[A, B]

  def dropField(source: A => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameFieldImpl[A, B]

  def optionalizeField(source: A => Any, target: B => Option[_]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  def mandateField(source: A => Option[_], target: B => Any, default: zio.blocks.schema.DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.mandateFieldImpl[A, B]

  // ============================================================================
  // String-based Convenience Aliases (Scala 2)
  // ============================================================================

  /**
   * Rename a field using string names.
   */
  def rename(from: String, to: String): MigrationBuilder[A, B] =
    self.renameField(from, to)

  /**
   * Drop a field using string name.
   */
  def drop(fieldName: String): MigrationBuilder[A, B] =
    self.dropField(fieldName)

  /**
   * Add a field with typed default value.
   */
  def add[T](fieldName: String, defaultValue: T)(implicit schema: zio.blocks.schema.Schema[T]): MigrationBuilder[A, B] =
    self.addFieldWithDefault(fieldName, defaultValue)

  /**
   * Make field required with typed default.
   */
  def mandate[T](fieldName: String, defaultForNone: T)(implicit schema: zio.blocks.schema.Schema[T]): MigrationBuilder[A, B] =
    self.mandateFieldWithDefault(fieldName, defaultForNone)

  /**
   * Make a field optional.
   */
  def optionalize(fieldName: String): MigrationBuilder[A, B] =
    self.optionalizeField(fieldName)
}
