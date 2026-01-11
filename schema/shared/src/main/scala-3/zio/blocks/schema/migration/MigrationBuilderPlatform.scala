package zio.blocks.schema.migration

// format: off
import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 3). Provides type-safe
 * selector syntax using inline/macro expansion. Supports nested paths like
 * `_.address.street`.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>

  // ============================================================================
  // Type-safe selector methods (macro-based with nested path support)
  // ============================================================================

  /**
   * Rename a field using type-safe selectors. Supports nested paths:
   * `.renameField(_.address.street, _.location.streetName)`
   */
  inline def renameField(
    inline fromSelector: A => Any,
    inline toSelector: B => Any
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'fromSelector, 'toSelector)
  }

  /**
   * Drop a field using type-safe selector. Supports nested paths:
   * `.dropField(_.address.obsoleteField)`
   */
  inline def dropField(inline selector: A => Any): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.dropFieldImpl[A, B]('self, 'selector)
  }

  /**
   * Add a field using type-safe selector. Supports nested paths:
   * `.addField(_.address.newField, dynamicDefault)`
   */
  inline def addField(
    inline selector: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.addFieldImpl[A, B]('self, 'selector, 'default)
  }

  /**
   * Make a field optional using type-safe selector. Supports nested paths:
   * `.optionalizeField(_.address.field, _.address.optionalField)`
   *
   * Note: The target selector is used for type-checking but the actual path is
   * extracted from the source selector.
   */
  inline def optionalizeField(
    inline source: A => Any
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.optionalizeImpl[A, B]('self, 'source)
  }

  /**
   * Make an optional field required using type-safe selectors. Supports nested
   * paths.
   */
  inline def mandateField(
    inline sourceSelector: A => Option[?],
    inline targetSelector: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.mandateFieldImpl[A, B]('self, 'sourceSelector, 'targetSelector, 'default)
  }

  /**
   * Change the type of a field using type-safe selectors. Supports nested
   * paths.
   */
  inline def changeFieldType(
    inline sourceSelector: A => Any,
    inline targetSelector: B => Any,
    converter: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.changeFieldTypeImpl[A, B]('self, 'sourceSelector, 'targetSelector, 'converter)
  }

  /**
   * Transform elements in a collection using type-safe selector. Supports
   * nested paths: `.transformElements(_.addresses, transform)`
   */
  inline def transformElements(
    inline selector: A => Seq[?],
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformElementsImpl[A, B]('self, 'selector, 'transform)
  }

  /**
   * Transform keys in a map using type-safe selector. Supports nested paths.
   */
  inline def transformKeys(
    inline selector: A => Map[?, ?],
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformKeysImpl[A, B]('self, 'selector, 'transform)
  }

  /**
   * Transform values in a map using type-safe selector. Supports nested paths.
   */
  inline def transformValues(
    inline selector: A => Map[?, ?],
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformValuesImpl[A, B]('self, 'selector, 'transform)
  }
}
