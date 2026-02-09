package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * Scala 3 version-specific extensions for `MigrationBuilder` that provide
 * selector-based methods using inline macros.
 *
 * These methods allow using `_.fieldName` syntax instead of string-based field
 * names, as required by the bounty spec's user-facing API.
 *
 * Example:
 * {{{
 * Migration.newBuilder[PersonV1, PersonV2]
 *   .renameFieldS(_.name, _.fullName)
 *   .addFieldS(_.email, s("default"))
 *   .build
 * }}}
 */
trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  // ──────────────── Selector-based Record Operations ────────────────

  /**
   * Rename a field using selector functions on source and target types.
   */
  inline def renameFieldS[X, Y](
    inline from: A => X,
    inline to: B => Y
  ): MigrationBuilder[A, B] = {
    val fromName = SelectorMacros.extractFieldName[A, X](from)
    val toName   = SelectorMacros.extractFieldName[B, Y](to)
    renameField(fromName, toName)
  }

  /**
   * Add a field using a selector on the target type.
   */
  inline def addFieldS[X](
    inline target: B => X,
    default: DynamicValue
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[B, X](target)
    addField(fieldName, default)
  }

  /**
   * Drop a field using a selector on the source type.
   */
  inline def dropFieldS[X](
    inline source: A => X
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    dropField(fieldName)
  }

  /**
   * Drop a field using a selector, with a default for reverse.
   */
  inline def dropFieldS[X](
    inline source: A => X,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    dropField(fieldName, defaultForReverse)
  }

  /**
   * Transform a field value using a selector.
   */
  inline def transformFieldS[X](
    inline source: A => X,
    migration: DynamicMigration
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    transformField(fieldName, migration)
  }

  /**
   * Mandate a field (Option -> required) using a selector.
   */
  inline def mandateFieldS[X](
    inline source: A => X,
    default: DynamicValue
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    mandateField(fieldName, default)
  }

  /**
   * Optionalize a field (required -> Option) using a selector.
   */
  inline def optionalizeFieldS[X](
    inline source: A => X
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    optionalizeField(fieldName)
  }

  /**
   * Change field type using a selector and MigrationExpr.
   */
  inline def changeFieldTypeExprS[X](
    inline source: A => X,
    expr: MigrationExpr
  ): MigrationBuilder[A, B] = {
    val fieldName = SelectorMacros.extractFieldName[A, X](source)
    changeFieldTypeExpr(fieldName, expr)
  }

  /**
   * Transform collection elements at a path specified by selector.
   */
  inline def transformElementsS[X](
    inline at: A => X,
    migration: DynamicMigration
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath[A, X](at)
    transformElements(path, migration)
  }

  /**
   * Transform map keys at a path specified by selector.
   */
  inline def transformKeysS[X](
    inline at: A => X,
    migration: DynamicMigration
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath[A, X](at)
    transformKeys(path, migration)
  }

  /**
   * Transform map values at a path specified by selector.
   */
  inline def transformValuesS[X](
    inline at: A => X,
    migration: DynamicMigration
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath[A, X](at)
    transformValues(path, migration)
  }
}
