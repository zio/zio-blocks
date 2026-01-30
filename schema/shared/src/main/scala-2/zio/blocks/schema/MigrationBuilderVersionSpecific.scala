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

  /** Transform a field value using a selector on the source type. */
  def transformField(source: A => Any, transform: DynamicValue, reverseTransform: Option[DynamicValue]): MigrationBuilder[A, B] = macro MigrationMacros.transformFieldImpl[A, B]

  /** Change the type of a field using a selector on the source type. */
  def changeFieldType(source: A => Any, converter: DynamicValue, reverseConverter: Option[DynamicValue]): MigrationBuilder[A, B] = macro MigrationMacros.changeFieldTypeImpl[A, B]

  /** Transform all elements in a collection field. */
  def transformElements(source: A => Any, transform: DynamicValue, reverseTransform: Option[DynamicValue]): MigrationBuilder[A, B] = macro MigrationMacros.transformElementsImpl[A, B]

  /** Transform all keys in a map field. */
  def transformKeys(source: A => Any, transform: DynamicValue, reverseTransform: Option[DynamicValue]): MigrationBuilder[A, B] = macro MigrationMacros.transformKeysImpl[A, B]

  /** Transform all values in a map field. */
  def transformValues(source: A => Any, transform: DynamicValue, reverseTransform: Option[DynamicValue]): MigrationBuilder[A, B] = macro MigrationMacros.transformValuesImpl[A, B]
  // format: on
}
