package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * Scala 3 version of the migration builder syntax. Provides macro-validated
 * selector methods that convert lambda expressions like `_.name` or
 * `_.address.street` into [[DynamicOptic]] paths at compile time.
 */
trait MigrationBuilderSyntax[A, B] { self: MigrationBuilder[A, B] =>

  /**
   * Adds a field to the target type with a default value.
   *
   * @param target
   *   selector for the new field, e.g. `_.age`
   * @param defaultValue
   *   the default value for the new field
   */
  inline def addField(inline target: B => Any, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    addFieldAt(MigrationBuilderMacros.selectorToOptic[B](target), defaultValue)

  /**
   * Adds a field to the target type with a typed default value.
   *
   * @param target
   *   selector for the new field, e.g. `_.age`
   * @param defaultValue
   *   the typed default value
   */
  inline def addField[T](inline target: B => T, defaultValue: T)(using schema: Schema[T]): MigrationBuilder[A, B] =
    addFieldAt(MigrationBuilderMacros.selectorToOptic[B](target), schema.toDynamicValue(defaultValue))

  /**
   * Drops a field from the source type.
   *
   * @param source
   *   selector for the field to drop, e.g. `_.oldField`
   * @param defaultForReverse
   *   default value used if this action is reversed
   */
  inline def dropField(inline source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    dropFieldAt(MigrationBuilderMacros.selectorToOptic[A](source), defaultForReverse)

  /**
   * Drops a field from the source type with a typed default for reverse.
   */
  inline def dropField[T](inline source: A => T, defaultForReverse: T)(using schema: Schema[T]): MigrationBuilder[A, B] =
    dropFieldAt(MigrationBuilderMacros.selectorToOptic[A](source), schema.toDynamicValue(defaultForReverse))

  /**
   * Renames a field from the source type to a new name in the target type.
   *
   * @param from
   *   selector for the source field, e.g. `_.firstName`
   * @param to
   *   selector for the target field, e.g. `_.givenName`
   */
  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] = {
    val fromOptic = MigrationBuilderMacros.selectorToOptic[A](from)
    val toOptic   = MigrationBuilderMacros.selectorToOptic[B](to)
    val fromNodes = fromOptic.nodes
    val toNodes   = toOptic.nodes
    if (fromNodes.isEmpty || toNodes.isEmpty)
      self
    else {
      (fromNodes.last, toNodes.last) match {
        case (fromField: DynamicOptic.Node.Field, toField: DynamicOptic.Node.Field) =>
          val parentPath = new DynamicOptic(fromNodes.init)
          renameFieldAt(parentPath, fromField.name, toField.name)
        case _ => self
      }
    }
  }

  /**
   * Transforms a field value using a serializable transform.
   */
  inline def transformField(
    inline from: A => Any,
    inline to: B => Any,
    transform: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    transformFieldAt(
      MigrationBuilderMacros.selectorToOptic[A](from),
      MigrationBuilderMacros.selectorToOptic[B](to),
      transform
    )

  /**
   * Makes an optional field mandatory with a default value for None cases.
   */
  inline def mandateField[T](
    inline source: A => Option[T],
    inline target: B => T,
    defaultValue: T
  )(using schema: Schema[T]): MigrationBuilder[A, B] =
    mandateFieldAt(
      MigrationBuilderMacros.selectorToOptic[A](source),
      MigrationBuilderMacros.selectorToOptic[B](target),
      schema.toDynamicValue(defaultValue)
    )

  /**
   * Makes a mandatory field optional.
   */
  inline def optionalizeField(
    inline source: A => Any,
    inline target: B => Any
  ): MigrationBuilder[A, B] =
    optionalizeFieldAt(
      MigrationBuilderMacros.selectorToOptic[A](source),
      MigrationBuilderMacros.selectorToOptic[B](target)
    )

  /**
   * Changes the type of a field using a serializable converter.
   */
  inline def changeFieldType(
    inline source: A => Any,
    inline target: B => Any,
    converter: DynamicValueTransform,
    reverseConverter: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    changeFieldTypeAt(
      MigrationBuilderMacros.selectorToOptic[A](source),
      MigrationBuilderMacros.selectorToOptic[B](target),
      converter,
      reverseConverter
    )

  /**
   * Renames a case in a variant/enum.
   */
  inline def renameCase(
    inline at: A => Any,
    fromName: String,
    toName: String
  ): MigrationBuilder[A, B] =
    renameCaseAt(MigrationBuilderMacros.selectorToOptic[A](at), fromName, toName)

  /**
   * Transforms elements of a collection at the given path.
   */
  inline def transformElements(
    inline at: A => Any,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    transformElementsAt(MigrationBuilderMacros.selectorToOptic[A](at), transform, reverseTransform)

  /**
   * Transforms keys of a map at the given path.
   */
  inline def transformKeys(
    inline at: A => Any,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    transformKeysAt(MigrationBuilderMacros.selectorToOptic[A](at), transform, reverseTransform)

  /**
   * Transforms values of a map at the given path.
   */
  inline def transformValues(
    inline at: A => Any,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    transformValuesAt(MigrationBuilderMacros.selectorToOptic[A](at), transform, reverseTransform)

  /**
   * Builds the migration with full macro validation. Checks that all fields in
   * the target type are accounted for by the migration actions.
   */
  inline def build: Migration[A, B] = ${ MigrationBuilderMacros.buildImpl[A, B]('self) }
}
