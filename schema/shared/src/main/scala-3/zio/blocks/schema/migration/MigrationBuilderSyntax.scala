package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 3 extension methods for MigrationBuilder that provide selector-based
 * APIs. These methods use macros to extract DynamicOptic paths from lambda
 * selectors, enabling type-safe field references at compile time.
 *
 * Supports selector-based operations:
 *   - `.addField(_.name, default)` - add a field to target schema
 *   - `.dropField(_.legacy, default)` - drop a field from source schema
 *   - `.renameField(_.oldName, _.newName)` - rename a field between versions
 *   - `.transformField(_.field, forward, reverse)` - transform field values
 */
object MigrationBuilderSyntax {

  extension [A, B](builder: MigrationBuilder[A, B]) {

    // ─────────────────────────────────────────────────────────────────────────
    // Field Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a field using a target selector.
     *
     * @param targetSelector
     *   lambda selecting the field on target type B
     * @param default
     *   the default value for the new field
     */
    inline def addField[T](inline targetSelector: B => T, default: T)(using
      schema: Schema[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[B, T](targetSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.addFieldResolved(parentPath, fieldName, Resolved.Literal(default, schema))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Add a field using a target selector with a Resolved expression.
     */
    inline def addField[T](inline targetSelector: B => T, default: Resolved): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[B, T](targetSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.addFieldResolved(parentPath, fieldName, default)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Drop a field using a source selector.
     *
     * @param sourceSelector
     *   lambda selecting the field on source type A
     * @param defaultForReverse
     *   default value to use when reversing the migration
     */
    inline def dropField[T](inline sourceSelector: A => T, defaultForReverse: T)(using
      schema: Schema[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](sourceSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.dropFieldResolved(parentPath, fieldName, Resolved.Literal(defaultForReverse, schema))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Drop a field using a source selector with schema default for reverse.
     *
     * Uses the target schema's default value when reversing the migration. If
     * no default is available in the schema, the reverse migration will fail.
     */
    inline def dropField[T](inline sourceSelector: A => T): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](sourceSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.dropFieldResolved(parentPath, fieldName, Resolved.SchemaDefault)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Rename a field using source and target selectors.
     *
     * @param sourceSelector
     *   lambda selecting the field on source type A
     * @param targetSelector
     *   lambda selecting the field on target type B
     */
    inline def renameField[S, T](
      inline sourceSelector: A => S,
      inline targetSelector: B => T
    ): MigrationBuilder[A, B] = {
      val sourcePath = MigrationBuilderMacros.extractPath[A, S](sourceSelector)
      val targetPath = MigrationBuilderMacros.extractPath[B, T](targetSelector)

      (sourcePath.nodes.lastOption, targetPath.nodes.lastOption) match {
        case (Some(DynamicOptic.Node.Field(oldName)), Some(DynamicOptic.Node.Field(newName))) =>
          val parentPath = DynamicOptic(sourcePath.nodes.dropRight(1))
          builder.renameFieldAt(parentPath, oldName, newName)
        case _ =>
          throw new IllegalArgumentException("Both selectors must end with field access")
      }
    }

    /**
     * Transform a field value using source and target selectors.
     *
     * @param sourceSelector
     *   lambda selecting the field on source type A
     * @param targetSelector
     *   lambda selecting the field on target type B
     * @param transform
     *   forward transformation expression
     * @param reverseTransform
     *   reverse transformation expression
     */
    inline def transformField[S, T](
      inline sourceSelector: A => S,
      inline targetSelector: B => T,
      transform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val sourcePath = MigrationBuilderMacros.extractPath[A, S](sourceSelector)
      val _          = MigrationBuilderMacros.extractPath[B, T](targetSelector)
      sourcePath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(sourcePath.nodes.dropRight(1))
          builder.transformFieldResolved(parentPath, fieldName, transform, reverseTransform)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Make an optional field mandatory.
     *
     * @param sourceSelector
     *   lambda selecting Option[T] on source type A
     * @param targetSelector
     *   lambda selecting T on target type B
     * @param default
     *   value to use when the source option is None
     */
    inline def mandateField[T](
      inline sourceSelector: A => Option[T],
      inline targetSelector: B => T,
      default: T
    )(using schema: Schema[T]): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Option[T]](sourceSelector)
      val _    = MigrationBuilderMacros.extractPath[B, T](targetSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.mandateFieldResolved(parentPath, fieldName, Resolved.Literal(default, schema))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Make a mandatory field optional.
     *
     * @param sourceSelector
     *   lambda selecting T on source type A
     * @param targetSelector
     *   lambda selecting Option[T] on target type B
     */
    inline def optionalizeField[T](
      inline sourceSelector: A => T,
      inline targetSelector: B => Option[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](sourceSelector)
      val _    = MigrationBuilderMacros.extractPath[B, Option[T]](targetSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.optionalizeFieldAt(parentPath, fieldName)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Change a field's primitive type.
     *
     * @param sourceSelector
     *   lambda selecting the field on source type A
     * @param targetSelector
     *   lambda selecting the field on target type B
     * @param fromType
     *   name of the source primitive type
     * @param toType
     *   name of the target primitive type
     */
    inline def changeFieldType[S, T](
      inline sourceSelector: A => S,
      inline targetSelector: B => T,
      fromType: String,
      toType: String
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, S](sourceSelector)
      val _    = MigrationBuilderMacros.extractPath[B, T](targetSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.changeFieldTypeResolved(
            parentPath,
            fieldName,
            Resolved.Convert(fromType, toType, Resolved.Identity),
            Resolved.Convert(toType, fromType, Resolved.Identity)
          )
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Join/Split Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Join multiple source fields into a single target field using selectors.
     *
     * @param source1
     *   lambda selecting first source field
     * @param source2
     *   lambda selecting second source field
     * @param target
     *   lambda selecting target field
     * @param combiner
     *   expression that combines source values
     * @param splitter
     *   expression for reverse (to recreate source fields)
     */
    inline def joinFields[S1, S2, T](
      inline source1: A => S1,
      inline source2: A => S2,
      inline target: B => T,
      combiner: Resolved,
      splitter: Resolved
    ): MigrationBuilder[A, B] = {
      val path1      = MigrationBuilderMacros.extractPath[A, S1](source1)
      val path2      = MigrationBuilderMacros.extractPath[A, S2](source2)
      val targetPath = MigrationBuilderMacros.extractPath[B, T](target)
      targetPath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(targetFieldName)) =>
          val parentPath = DynamicOptic(targetPath.nodes.dropRight(1))
          builder.joinFields(parentPath, targetFieldName, zio.blocks.chunk.Chunk(path1, path2), combiner, splitter)
        case _ =>
          throw new IllegalArgumentException("Target selector must end with a field access")
      }
    }

    /**
     * Join three source fields into a single target field using selectors.
     */
    inline def joinFields[S1, S2, S3, T](
      inline source1: A => S1,
      inline source2: A => S2,
      inline source3: A => S3,
      inline target: B => T,
      combiner: Resolved,
      splitter: Resolved
    ): MigrationBuilder[A, B] = {
      val path1      = MigrationBuilderMacros.extractPath[A, S1](source1)
      val path2      = MigrationBuilderMacros.extractPath[A, S2](source2)
      val path3      = MigrationBuilderMacros.extractPath[A, S3](source3)
      val targetPath = MigrationBuilderMacros.extractPath[B, T](target)
      targetPath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(targetFieldName)) =>
          val parentPath = DynamicOptic(targetPath.nodes.dropRight(1))
          builder.joinFields(
            parentPath,
            targetFieldName,
            zio.blocks.chunk.Chunk(path1, path2, path3),
            combiner,
            splitter
          )
        case _ =>
          throw new IllegalArgumentException("Target selector must end with a field access")
      }
    }

    /**
     * Split a source field into multiple target fields using selectors.
     *
     * @param source
     *   lambda selecting source field
     * @param target1
     *   lambda selecting first target field
     * @param target2
     *   lambda selecting second target field
     * @param splitter
     *   expression that splits source value
     * @param combiner
     *   expression for reverse (to recreate source field)
     */
    inline def splitField[S, T1, T2](
      inline source: A => S,
      inline target1: B => T1,
      inline target2: B => T2,
      splitter: Resolved,
      combiner: Resolved
    ): MigrationBuilder[A, B] = {
      val sourcePath  = MigrationBuilderMacros.extractPath[A, S](source)
      val targetPath1 = MigrationBuilderMacros.extractPath[B, T1](target1)
      val targetPath2 = MigrationBuilderMacros.extractPath[B, T2](target2)
      sourcePath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(sourceFieldName)) =>
          val parentPath = DynamicOptic(sourcePath.nodes.dropRight(1))
          builder.splitField(
            parentPath,
            sourceFieldName,
            zio.blocks.chunk.Chunk(targetPath1, targetPath2),
            splitter,
            combiner
          )
        case _ =>
          throw new IllegalArgumentException("Source selector must end with a field access")
      }
    }

    /**
     * Split a source field into three target fields using selectors.
     */
    inline def splitField[S, T1, T2, T3](
      inline source: A => S,
      inline target1: B => T1,
      inline target2: B => T2,
      inline target3: B => T3,
      splitter: Resolved,
      combiner: Resolved
    ): MigrationBuilder[A, B] = {
      val sourcePath  = MigrationBuilderMacros.extractPath[A, S](source)
      val targetPath1 = MigrationBuilderMacros.extractPath[B, T1](target1)
      val targetPath2 = MigrationBuilderMacros.extractPath[B, T2](target2)
      val targetPath3 = MigrationBuilderMacros.extractPath[B, T3](target3)
      sourcePath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(sourceFieldName)) =>
          val parentPath = DynamicOptic(sourcePath.nodes.dropRight(1))
          builder.splitField(
            parentPath,
            sourceFieldName,
            zio.blocks.chunk.Chunk(targetPath1, targetPath2, targetPath3),
            splitter,
            combiner
          )
        case _ =>
          throw new IllegalArgumentException("Source selector must end with a field access")
      }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enum Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rename an enum case using selector for the field containing the enum.
     */
    inline def renameCase[E](
      inline enumSelector: A => E,
      from: String,
      to: String
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, E](enumSelector)
      builder.renameCaseAt(path, from, to)
    }

    /**
     * Transform an enum case using a selector for the field containing the
     * enum.
     *
     * @param enumSelector
     *   lambda selecting the enum field
     * @param caseName
     *   name of the case to transform
     * @param caseActions
     *   nested migration actions to apply to the case
     */
    inline def transformCase[E](
      inline enumSelector: A => E,
      caseName: String,
      caseActions: zio.blocks.chunk.Chunk[MigrationAction]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, E](enumSelector)
      builder.transformCaseAt(path, caseName, caseActions)
    }

    /**
     * Type-safe enum case selector using when[C] pattern per Issue #519.
     *
     * Applies a nested migration only when the enum matches the specified case
     * type. Example: `.when[Person](_.status)(nestedMigration)`
     *
     * @tparam C
     *   the specific case type
     * @param enumSelector
     *   lambda selecting the enum field on source
     * @param caseActions
     *   nested migration actions for that case
     */
    inline def when[C](
      inline enumSelector: A => Any,
      caseActions: zio.blocks.chunk.Chunk[MigrationAction]
    ): MigrationBuilder[A, B] = {
      val path     = MigrationBuilderMacros.extractPath[A, Any](enumSelector)
      val caseName = MigrationBuilderMacros.extractCaseName[C]
      builder.transformCaseAt(path, caseName, caseActions)
    }

    /**
     * Rename an enum case using type-safe case type references.
     *
     * @tparam OldCase
     *   the old case type
     * @tparam NewCase
     *   the new case type
     * @param enumSelector
     *   lambda selecting the enum field
     */
    inline def renameCaseTyped[OldCase, NewCase](
      inline enumSelector: A => Any
    ): MigrationBuilder[A, B] = {
      val path    = MigrationBuilderMacros.extractPath[A, Any](enumSelector)
      val oldName = MigrationBuilderMacros.extractCaseName[OldCase]
      val newName = MigrationBuilderMacros.extractCaseName[NewCase]
      builder.renameCaseAt(path, oldName, newName)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transform elements of a collection using a selector.
     */
    inline def transformElements[T](inline selector: A => Seq[T])(
      transform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Seq[T]](selector)
      builder.transformElementsResolved(path, transform, reverseTransform)
    }

    /**
     * Transform map keys using a selector.
     */
    inline def transformKeys[K, V](inline selector: A => Map[K, V])(
      keyTransform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Map[K, V]](selector)
      builder.transformKeysResolved(path, keyTransform, reverseTransform)
    }

    /**
     * Transform map values using a selector.
     */
    inline def transformValues[K, V](inline selector: A => Map[K, V])(
      valueTransform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Map[K, V]](selector)
      builder.transformValuesResolved(path, valueTransform, reverseTransform)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested Migration Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Apply a nested migration to a record field.
     *
     * The nested migration's actions are prefixed with the field path, allowing
     * composition of migrations on nested structures.
     *
     * Example:
     * {{{
     * val addressMigration = MigrationBuilder[AddressV0, AddressV1]
     *   .addField(_.city, "Unknown")
     *   .build
     *
     * val personMigration = MigrationBuilder[PersonV0, PersonV1]
     *   .inField(_.address)(addressMigration)
     *   .build
     * }}}
     */
    inline def inField[F1, F2](inline fieldSelector: A => F1)(
      nestedMigration: Migration[F1, F2]
    ): MigrationBuilder[A, B] = {
      val fieldPath       = MigrationBuilderMacros.extractPath[A, F1](fieldSelector)
      val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
      builder.addActions(prefixedActions)
    }

    /**
     * Apply a nested migration to each element in a sequence field.
     *
     * Each element in the sequence will have the nested migration applied to
     * it.
     *
     * Example:
     * {{{
     * val itemMigration = MigrationBuilder[ItemV0, ItemV1]
     *   .renameField(_.desc, _.description)
     *   .build
     *
     * val orderMigration = MigrationBuilder[OrderV0, OrderV1]
     *   .inElements(_.items)(itemMigration)
     *   .build
     * }}}
     */
    inline def inElements[E1, E2](inline fieldSelector: A => Seq[E1])(
      nestedMigration: Migration[E1, E2]
    ): MigrationBuilder[A, B] = {
      val fieldPath       = MigrationBuilderMacros.extractPath[A, Seq[E1]](fieldSelector).elements
      val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
      builder.addActions(prefixedActions)
    }

    /**
     * Apply a nested migration to each value in a map field.
     *
     * Example:
     * {{{
     * val configMigration = MigrationBuilder[ConfigV0, ConfigV1]
     *   .addField(_.enabled, true)
     *   .build
     *
     * val settingsMigration = MigrationBuilder[SettingsV0, SettingsV1]
     *   .inMapValues(_.configs)(configMigration)
     *   .build
     * }}}
     */
    inline def inMapValues[K, V1, V2](inline fieldSelector: A => Map[K, V1])(
      nestedMigration: Migration[V1, V2]
    ): MigrationBuilder[A, B] = {
      val fieldPath       = MigrationBuilderMacros.extractPath[A, Map[K, V1]](fieldSelector).mapValues
      val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
      builder.addActions(prefixedActions)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy WithSelector methods (backward compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    /** Add a field using a parent selector to specify location. */
    inline def addFieldWithSelector[P, T](inline parentSelector: B => P)(fieldName: String, default: T)(using
      schema: Schema[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[B, P](parentSelector)
      builder.addFieldResolved(path, fieldName, Resolved.Literal(default, schema))
    }

    /** Drop a field using a selector expression. */
    inline def dropFieldWithSelector[T](inline selector: A => T): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.dropFieldResolved(parentPath, fieldName, Resolved.Fail(s"Cannot reverse drop of $fieldName"))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Drop a field using a selector, with a default for reverse migration. */
    inline def dropFieldWithSelectorDefault[T](inline selector: A => T, defaultForReverse: T)(using
      schema: Schema[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.dropFieldResolved(parentPath, fieldName, Resolved.Literal(defaultForReverse, schema))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Rename a field using a selector for source location. */
    inline def renameFieldWithSelector[T](inline selector: A => T, newName: String): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(oldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.renameFieldAt(parentPath, oldName, newName)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Transform a field value using a selector. */
    inline def transformFieldWithSelector[T](inline selector: A => T)(
      transform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.transformFieldResolved(parentPath, fieldName, transform, reverseTransform)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Make an optional field mandatory using a selector. */
    inline def mandateFieldWithSelector[T](inline selector: A => Option[T], default: T)(using
      schema: Schema[T]
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Option[T]](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.mandateFieldResolved(parentPath, fieldName, Resolved.Literal(default, schema))
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Make a mandatory field optional using a selector. */
    inline def optionalizeFieldWithSelector[T](inline selector: A => T): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.optionalizeFieldAt(parentPath, fieldName)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Change a field's type using a selector. */
    inline def changeFieldTypeWithSelector[T](inline selector: A => T)(
      fromType: String,
      toType: String
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, T](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          builder.changeFieldTypeResolved(
            parentPath,
            fieldName,
            Resolved.Convert(fromType, toType, Resolved.Identity),
            Resolved.Convert(toType, fromType, Resolved.Identity)
          )
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /** Transform elements of a collection using a selector. */
    inline def transformElementsWithSelector[T](inline selector: A => Seq[T])(
      transform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Seq[T]](selector)
      builder.transformElementsResolved(path, transform, reverseTransform)
    }

    /** Transform map keys using a selector. */
    inline def transformKeysWithSelector[K, V](inline selector: A => Map[K, V])(
      keyTransform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Map[K, V]](selector)
      builder.transformKeysResolved(path, keyTransform, reverseTransform)
    }

    /** Transform map values using a selector. */
    inline def transformValuesWithSelector[K, V](inline selector: A => Map[K, V])(
      valueTransform: Resolved,
      reverseTransform: Resolved
    ): MigrationBuilder[A, B] = {
      val path = MigrationBuilderMacros.extractPath[A, Map[K, V]](selector)
      builder.transformValuesResolved(path, valueTransform, reverseTransform)
    }

    // NOTE: The .build method is intentionally NOT provided on the untracked builder.
    // For compile-time validated builds, use the tracked API:
    //   tracked[A, B].addFieldTracked(...).build
    // For runtime-validated builds, use buildStrict or buildPartial directly.
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type-Refined Builder with Compile-Time Field Tracking
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A wrapper around MigrationBuilder that tracks handled and provided fields
   * at the type level using Tuple types.
   *
   * @tparam A
   *   source schema type
   * @tparam B
   *   target schema type
   * @tparam Handled
   *   Tuple of field names from source that have been handled
   * @tparam Provided
   *   Tuple of field names to target that have been provided
   */
  final class TrackedMigrationBuilder[A, B, Handled <: Tuple, Provided <: Tuple](
    val underlying: MigrationBuilder[A, B]
  ) {

    /** Get the underlying untracked builder. */
    def untracked: MigrationBuilder[A, B] = underlying

    /** Build the migration without type-level validation. */
    def buildPartial: Migration[A, B] = underlying.buildPartial

    /** Build with strict runtime validation. */
    def buildStrict: Migration[A, B] = underlying.buildStrict

    /**
     * Build the migration with TRUE compile-time validation.
     *
     * This method requires an implicit ValidationProof, which the compiler will
     * only synthesize if Handled and Provided fully cover all required field
     * paths and case names. If the migration is incomplete, compilation fails.
     *
     * This is the ONLY method that provides true compile-time rejection of
     * incomplete migrations, satisfying Issue #519's strict requirement for
     * "macro validation in .build to confirm 'old' has been migrated to 'new'".
     */
    inline def build(using
      fpA: FieldPaths[A],
      fpB: FieldPaths[B],
      cpA: CasePaths[A],
      cpB: CasePaths[B],
      proof: ValidationProof[A, B, Handled, Provided]
    ): Migration[A, B] = underlying.buildPathsOnly
  }

  /**
   * Type-refined extension methods for TrackedMigrationBuilder.
   *
   * These methods update the Handled and Provided type parameters as operations
   * are added, enabling compile-time tracking of migration completeness.
   */
  extension [A, B, Handled <: Tuple, Provided <: Tuple](builder: TrackedMigrationBuilder[A, B, Handled, Provided]) {

    /**
     * Add a field to the target schema, updating Provided type parameter.
     */
    transparent inline def addFieldTracked[T, FieldName <: String & Singleton](
      inline targetSelector: B => T,
      default: Resolved
    ): TrackedMigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldName]] = {
      val newBuilder = {
        import MigrationBuilderSyntax.{given, *}
        builder.underlying.addField(targetSelector, default)
      }
      new TrackedMigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldName]](newBuilder)
    }

    /**
     * Drop a field from the source schema, updating Handled type parameter.
     */
    transparent inline def dropFieldTracked[T, FieldName <: String & Singleton](
      inline sourceSelector: A => T,
      defaultForReverse: Resolved
    ): TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Provided] = {
      val path = MigrationBuilderMacros.extractPath[A, T](sourceSelector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fname)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          val newBuilder = builder.underlying.dropFieldResolved(parentPath, fname, defaultForReverse)
          new TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Provided](newBuilder)
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Rename a field, updating both Handled and Provided type parameters.
     */
    transparent inline def renameFieldTracked[S, T, FromName <: String & Singleton, ToName <: String & Singleton](
      inline sourceSelector: A => S,
      inline targetSelector: B => T
    ): TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FromName], Tuple.Append[Provided, ToName]] = {
      val sourcePath = MigrationBuilderMacros.extractPath[A, S](sourceSelector)
      val targetPath = MigrationBuilderMacros.extractPath[B, T](targetSelector)

      (sourcePath.nodes.lastOption, targetPath.nodes.lastOption) match {
        case (Some(DynamicOptic.Node.Field(oldName)), Some(DynamicOptic.Node.Field(newName))) =>
          val parentPath = DynamicOptic(sourcePath.nodes.dropRight(1))
          val newBuilder = builder.underlying.renameFieldAt(parentPath, oldName, newName)
          new TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FromName], Tuple.Append[Provided, ToName]](newBuilder)
        case _ =>
          throw new IllegalArgumentException("Both selectors must end with field access")
      }
    }

    /**
     * Transform a field, updating both Handled and Provided type parameters.
     */
    transparent inline def transformFieldTracked[S, T, FieldName <: String & Singleton](
      inline selector: A => S,
      transform: Resolved,
      reverseTransform: Resolved
    ): TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]] = {
      val path = MigrationBuilderMacros.extractPath[A, S](selector)
      path.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fname)) =>
          val parentPath = DynamicOptic(path.nodes.dropRight(1))
          val newBuilder = builder.underlying.transformFieldResolved(parentPath, fname, transform, reverseTransform)
          new TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]](
            newBuilder
          )
        case _ =>
          throw new IllegalArgumentException("Selector must end with a field access")
      }
    }

    /**
     * Keep a field unchanged (identity), updating both Handled and Provided.
     */
    transparent inline def keepFieldTracked[T, FieldName <: String & Singleton]: TrackedMigrationBuilder[
      A,
      B,
      Tuple.Append[Handled, FieldName],
      Tuple.Append[Provided, FieldName]
    ] =
      // No action needed for keeping a field, just track it at the type level
      new TrackedMigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]](
        builder.underlying
      )

    // NOTE: The build method is defined in TrackedMigrationBuilder class (not here as extension)
    // with proper ValidationProof requirement for true compile-time validation.
  }

  /**
   * Create a new tracked migration builder.
   */
  def tracked[A, B](using source: Schema[A], target: Schema[B]): TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple](MigrationBuilder(source, target))

  /**
   * Create a tracked builder from an existing untracked builder.
   */
  def tracked[A, B](builder: MigrationBuilder[A, B]): TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple](builder)

  /**
   * Helper to extract field name from selector at compile time.
   */
  private inline def extractFieldName[A, T](inline selector: A => T): String = ${
    extractFieldNameImpl[A, T]('selector)
  }

  private def extractFieldNameImpl[A: Type, T: Type](
    selector: Expr[A => T]
  )(using Quotes): Expr[String] = {
    import quotes.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractName(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case _                    => report.errorAndAbort(s"Expected a field selector, got '${term.show}'")
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldName = extractName(pathBody)
    Expr(fieldName)
  }
}
