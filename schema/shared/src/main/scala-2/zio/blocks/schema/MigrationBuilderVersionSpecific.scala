package zio.blocks.schema

import scala.language.experimental.macros

/**
 * Scala 2 macro-based selector methods for `MigrationBuilder`.
 *
 * These methods accept lambda selector expressions (e.g. `_.fieldName`,
 * `_.address.city`) and extract `DynamicOptic` paths at compile time.
 */
trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  // format: off
  /** Add a field using a selector on the target type. */
  def addField(target: B => Any, default: DynamicValue): MigrationBuilder[A, B] = macro MigrationMacros.addFieldImpl[A, B]

  /** Drop a field using a selector on the source type. */
  def dropField(source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] = macro MigrationMacros.dropFieldImpl[A, B]

  /** Rename a field using selectors on source and target types. */
  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = macro MigrationMacros.renameFieldImpl[A, B]

  /** Make a field mandatory using a selector on the source type. */
  def mandateField(source: A => Any, default: DynamicValue): MigrationBuilder[A, B] = macro MigrationMacros.mandateFieldImpl[A, B]

  /** Make a field optional using a selector on the source type. */
  def optionalizeField(source: A => Any): MigrationBuilder[A, B] = macro MigrationMacros.optionalizeFieldImpl[A, B]
  // format: on
}
