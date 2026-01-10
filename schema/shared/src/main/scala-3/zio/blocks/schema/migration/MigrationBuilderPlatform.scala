package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 3).
 * 
 * Provides type-safe field selector syntax using inline/macro expansion.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>
  
  /**
   * Rename a field using type-safe selectors.
   * 
   * Example: `.renameField(_.firstName, _.fullName)`
   */
  inline def renameField(
    inline oldField: A => Any,
    inline newField: B => Any
  )(using fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'oldField, 'newField, 'fromSchema, 'toSchema)
  }
  
  /**
   * Drop a field using type-safe selector.
   * 
   * Example: `.dropField(_.obsoleteField)`
   */
  inline def dropField(inline field: A => Any)(using fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.dropFieldImpl[A, B]('self, 'field, 'fromSchema, 'toSchema)
  }
  
  /**
   * Make a field optional using type-safe selector.
   * 
   * Example: `.optionalize(_.fieldName)`
   */
  inline def optionalize(inline field: A => Any)(using fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.optionalizeImpl[A, B]('self, 'field, 'fromSchema, 'toSchema)
  }
}
