package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

import scala.language.experimental.macros

/**
 * Scala 2 version of the migration builder syntax. Provides macro-validated
 * selector methods that convert lambda expressions like `_.name` or
 * `_.address.street` into [[DynamicOptic]] paths at compile time.
 */
trait MigrationBuilderSyntax[A, B] { self: MigrationBuilder[A, B] =>

  /**
   * Adds a field to the target type with a default value.
   */
  def addField(target: B => Any, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addFieldImpl[A, B]

  /**
   * Drops a field from the source type.
   */
  def dropField(source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B]

  /**
   * Renames a field from the source type to the target type.
   */
  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameFieldImpl[A, B]

  /**
   * Transforms a field value using a serializable transform.
   */
  def transformField(
    from: A => Any,
    to: B => Any,
    transform: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformFieldImpl[A, B]

  /**
   * Makes a mandatory field optional.
   */
  def optionalizeField(source: A => Any, target: B => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  /**
   * Changes the type of a field using a serializable converter.
   */
  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.changeFieldTypeImpl[A, B]

  /**
   * Renames a case in a variant/enum at the given path.
   */
  def renameCase(
    at: A => Any,
    fromName: String,
    toName: String
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameCaseImpl[A, B]

  /**
   * Transforms elements of a collection at the given path.
   */
  def transformElements(
    at: A => Any,
    transform: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformElementsImpl[A, B]

  /**
   * Transforms keys of a map at the given path.
   */
  def transformKeys(
    at: A => Any,
    transform: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformKeysImpl[A, B]

  /**
   * Transforms values of a map at the given path.
   */
  def transformValues(
    at: A => Any,
    transform: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformValuesImpl[A, B]

  /**
   * Builds the migration with validation.
   */
  def build: Migration[A, B] = macro MigrationBuilderMacros.buildImpl[A, B]
}
