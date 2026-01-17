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
   * Transform a field value using type-safe selectors. Supports nested paths:
   * `.transformField(_.address.street, _.address.street)(transform)`
   *
   * @param source Selector for source field
   * @param target Selector for target field (typically same path)
   * @param transform Expression to transform the field value
   */
  inline def transformField(
    inline source: A => Any,
    inline target: B => Any,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformFieldImpl[A, B]('self, 'source, 'target, 'transform)
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

  // ============================================================================
  // Join / Split with type-safe selectors
  // ============================================================================

  /**
   * Join two source fields into a single target field using type-safe selectors.
   *
   * Example:
   * {{{
   *   .joinFields2(_.firstName, _.lastName)(_.fullName)(combiner)
   *   .joinFields2(_.address.street, _.origin.country)(_.address.fullAddress)(combiner)
   * }}}
   *
   * The combiner receives a `DynamicValue.Sequence` containing the two source values.
   *
   * @param source1 Selector for first source field
   * @param source2 Selector for second source field
   * @param target Selector for target field
   * @param combiner Expression to combine source values
   * @param splitterForReverse Optional splitter for reverse migration
   */
  inline def joinFields2(
    inline source1: A => Any,
    inline source2: A => Any
  )(
    inline target: B => Any
  )(
    combiner: SchemaExpr[DynamicValue, DynamicValue],
    splitterForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.joinFields2Impl[A, B]('self, 'source1, 'source2, 'target, 'combiner, 'splitterForReverse)
  }

  /**
   * Split a source field into two target fields using type-safe selectors.
   *
   * Example:
   * {{{
   *   .splitField2(_.fullName)(_.firstName, _.lastName)(splitter)
   *   .splitField2(_.fullAddress)(_.address.street, _.origin.country)(splitter)
   * }}}
   *
   * The splitter should return a `DynamicValue.Sequence` with two values.
   *
   * @param source Selector for source field
   * @param target1 Selector for first target field
   * @param target2 Selector for second target field
   * @param splitter Expression to split source value
   * @param combinerForReverse Optional combiner for reverse migration
   */
  inline def splitField2(
    inline source: A => Any
  )(
    inline target1: B => Any,
    inline target2: B => Any
  )(
    splitter: SchemaExpr[DynamicValue, DynamicValue],
    combinerForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.splitField2Impl[A, B]('self, 'source, 'target1, 'target2, 'splitter, 'combinerForReverse)
  }
}
