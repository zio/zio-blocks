package zio.blocks.schema

/**
 * Scala 3 macro-based selector methods for `MigrationBuilder`.
 *
 * These methods accept lambda selector expressions (e.g. `_.fieldName`,
 * `_.address.city`) and extract `DynamicOptic` paths at compile time.
 */
trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  /**
   * Add a field using a selector on the target type.
   *
   * Example: `builder.addField(_.newField, DynamicValue.int(0))`
   */
  inline def addField(inline target: B => Any, default: DynamicValue): MigrationBuilder[A, B] =
    ${ MigrationMacros.addFieldImpl[A, B]('self, 'target, 'default) }

  /**
   * Drop a field using a selector on the source type.
   *
   * Example: `builder.dropField(_.oldField, DynamicValue.Null)`
   */
  inline def dropField(inline source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    ${ MigrationMacros.dropFieldImpl[A, B]('self, 'source, 'defaultForReverse) }

  /**
   * Rename a field using selectors on source and target types.
   *
   * Example: `builder.renameField(_.oldName, _.newName)`
   */
  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
    ${ MigrationMacros.renameFieldImpl[A, B]('self, 'from, 'to) }

  /**
   * Make a field mandatory using a selector on the source type.
   *
   * Example: `builder.mandateField(_.optionalAge, DynamicValue.int(0))`
   */
  inline def mandateField(inline source: A => Any, default: DynamicValue): MigrationBuilder[A, B] =
    ${ MigrationMacros.mandateImpl[A, B]('self, 'source, 'default) }

  /**
   * Make a field optional using a selector on the source type.
   *
   * Example: `builder.optionalizeField(_.requiredField)`
   */
  inline def optionalizeField(inline source: A => Any): MigrationBuilder[A, B] =
    ${ MigrationMacros.optionalizeImpl[A, B]('self, 'source) }
}
