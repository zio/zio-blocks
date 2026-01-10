package zio.blocks.schema.migration

import zio.blocks.schema._
import scala.quoted._

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
    inline field1: A => Any,
    inline field2: B => Any
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'field1, 'field2, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Drop a field using type-safe selector.
   *
   * Example: `.dropField(_.obsoleteField)`
   */
  inline def dropField(
    inline field: A => Any
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.dropFieldImpl[A, B]('self, 'field, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Make a field optional using type-safe selector.
   *
   * Example: `.optionalize(_.fieldName)`
   */
  inline def optionalize(
    inline field: A => Any
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.optionalizeImpl[A, B]('self, 'field, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Add a field using type-safe selector on the target type.
   *
   * Example: `.addField(_.country, "USA")`
   *
   * Supports nested paths: `.addField(_.address.zipCode, "00000")`
   */
  inline def addField[T](
    inline target: B => Any,
    inline defaultValue: T
  )(using defaultSchema: Schema[T]): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.addFieldImpl[A, B, T](
      'self,
      'target,
      'defaultValue,
      '{ self.fromSchema },
      '{ self.toSchema },
      'defaultSchema
    )
  }

  /**
   * Make a field required using type-safe selector.
   *
   * Example: `.mandate(_.optionalField, defaultWhenNone)`
   */
  inline def mandate[T](
    inline field: A => Any,
    inline defaultValue: T
  )(using defaultSchema: Schema[T]): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.mandateImpl[A, B, T](
      'self,
      'field,
      'defaultValue,
      '{ self.fromSchema },
      '{ self.toSchema },
      'defaultSchema
    )
  }

  /**
   * Transform a field's value using an expression and a type-safe selector.
   */
  inline def transformField(
    inline field: A => Any,
    inline transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformFieldImpl[A, B]('self, 'field, 'transform, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Change a field's type using a coercion expression and a type-safe selector.
   */
  inline def changeFieldType(
    inline field: A => Any,
    inline converter: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.changeFieldTypeImpl[A, B](
      'self,
      'field,
      'converter,
      '{ self.fromSchema },
      '{ self.toSchema }
    )
  }

  /**
   * Transform elements in a collection using a type-safe selector.
   */
  inline def transformElements(
    inline field: A => Any,
    inline transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformElementsImpl[A, B](
      'self,
      'field,
      'transform,
      '{ self.fromSchema },
      '{ self.toSchema }
    )
  }

  /**
   * Transform keys in a map using a type-safe selector.
   */
  inline def transformKeys(
    inline field: A => Any,
    inline transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformKeysImpl[A, B]('self, 'field, 'transform, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Transform values in a map using a type-safe selector.
   */
  inline def transformValues(
    inline field: A => Any,
    inline transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformValuesImpl[A, B](
      'self,
      'field,
      'transform,
      '{ self.fromSchema },
      '{ self.toSchema }
    )
  }

  /**
   * Transform a case in an enum using a type-safe selector.
   */
  inline def transformCase(
    inline caseSelector: A => Any,
    inline transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.transformCaseImpl[A, B](
      'self,
      'caseSelector,
      'transform,
      '{ self.fromSchema },
      '{ self.toSchema }
    )
  }

  /**
   * Join multiple fields into one using type-safe selectors.
   */
  inline def join(
    inline target: B => Any,
    inline combiner: SchemaExpr[DynamicValue, DynamicValue],
    inline sources: A => Any*
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.joinImpl[A, B]('self, 'target, 'combiner, 'sources, '{ self.fromSchema }, '{ self.toSchema })
  }

  /**
   * Split one field into multiple using type-safe selectors.
   */
  inline def split(
    inline source: A => Any,
    inline splitter: SchemaExpr[DynamicValue, DynamicValue],
    inline targets: B => Any*
  ): MigrationBuilder[A, B] = ${
    MigrationBuilderMacros.splitImpl[A, B](
      'self,
      'source,
      'splitter,
      'targets,
      '{ self.fromSchema },
      '{ self.toSchema }
    )
  }

  /**
   * Build the final migration with compile-time validation.
   */
  inline def build: Either[String, Migration[A, B]] = ${
    MigrationBuilderMacros.validateAndBuild[A, B]('self)
  }
}
