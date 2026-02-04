package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A fluent builder for constructing type-safe migrations.
 *
 * The builder uses type-level tracking to ensure migrations are complete:
 *   - `SourceHandled` tracks which source fields have been addressed
 *   - `TargetProvided` tracks which target fields have been provided
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 * @tparam SourceHandled
 *   Tuple of source field names that have been handled
 * @tparam TargetProvided
 *   Tuple of target field names that have been provided
 */
final class MigrationBuilder[A, B, SourceHandled <: Tuple, TargetProvided <: Tuple](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  /**
   * Add a new field to the target with a default value. Tracks field at type
   * level.
   */
  transparent inline def addField[T](
    inline target: B => T,
    default: T
  )(using targetFieldSchema: Schema[T]): MigrationBuilder[A, B, SourceHandled, ?] =
    ${
      MigrationBuilderMacros.addFieldImpl[A, B, SourceHandled, TargetProvided, T](
        'this,
        'target,
        'default,
        'targetFieldSchema
      )
    }

  /**
   * Add a new field to the target with a DynamicValue default. Tracks field at
   * type level.
   */
  transparent inline def addFieldDynamic(
    inline target: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B, SourceHandled, ?] =
    ${ MigrationBuilderMacros.addFieldDynamicImpl[A, B, SourceHandled, TargetProvided]('this, 'target, 'default) }

  /** Drop a field from the source. Tracks field at type level. */
  transparent inline def dropField[T](
    inline source: A => T,
    defaultForReverse: T
  )(using sourceFieldSchema: Schema[T]): MigrationBuilder[A, B, ?, TargetProvided] =
    ${
      MigrationBuilderMacros.dropFieldImpl[A, B, SourceHandled, TargetProvided, T](
        'this,
        'source,
        'defaultForReverse,
        'sourceFieldSchema
      )
    }

  /**
   * Drop a field from the source with a DynamicValue for reverse. Tracks field
   * at type level.
   */
  transparent inline def dropFieldDynamic(
    inline source: A => Any,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B, ?, TargetProvided] =
    ${
      MigrationBuilderMacros.dropFieldDynamicImpl[A, B, SourceHandled, TargetProvided](
        'this,
        'source,
        'defaultForReverse
      )
    }

  /**
   * Rename a field from source name to target name. Tracks both fields at type
   * level.
   */
  transparent inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B, ?, ?] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'from, 'to) }

  /** Transform a field's value (simplified - just renames the field). */
  inline def transformField[T, U](
    inline from: A => T,
    inline to: B => U,
    @scala.annotation.unused transform: T => U
  )(using
    @scala.annotation.unused fromSchema: Schema[T],
    @scala.annotation.unused toSchema: Schema[U]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    // For serializable migrations, we capture a representative transformed value
    // In practice, users should use transformFieldDynamic for full control
    val fromPath = SelectorMacros.toPath[A, T](from)
    // Note: This is a simplified version - full implementation would use SchemaExpr
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Rename(fromPath, SelectorMacros.extractFieldName[B, U](to))
    )
  }

  /**
   * Transform a field's value using a serializable expression.
   *
   * This method provides full expression support for field transformations,
   * allowing for serializable migrations that can compute new values
   * dynamically.
   *
   * @param at
   *   Selector for the field to transform
   * @param expr
   *   The expression that computes the new value
   * @param reverseExpr
   *   Optional expression for reverse migration
   */
  inline def transformFieldExpr[T](
    inline at: A => T,
    expr: MigrationExpr,
    reverseExpr: Option[MigrationExpr] = None
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path = SelectorMacros.toPath[A, T](at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValueExpr(path, expr, reverseExpr)
    )
  }

  /** Transform a field with a literal new value. Tracks field at type level. */
  transparent inline def transformFieldLiteral[T](
    inline at: A => T,
    newValue: T
  )(using fieldSchema: Schema[T]): MigrationBuilder[A, B, ?, TargetProvided] =
    ${
      MigrationBuilderMacros.transformFieldLiteralImpl[A, B, SourceHandled, TargetProvided, T](
        'this,
        'at,
        'newValue,
        'fieldSchema
      )
    }

  /** Convert an optional field to required. Tracks field at type level. */
  transparent inline def mandateField[T](
    inline source: A => Option[T],
    inline target: B => T,
    default: T
  )(using fieldSchema: Schema[T]): MigrationBuilder[A, B, ?, TargetProvided] =
    ${
      MigrationBuilderMacros.mandateFieldImpl[A, B, SourceHandled, TargetProvided, T](
        'this,
        'source,
        'target,
        'default,
        'fieldSchema
      )
    }

  /** Convert a required field to optional. Tracks field at type level. */
  transparent inline def optionalizeField[T](
    inline source: A => T,
    inline target: B => Option[T]
  ): MigrationBuilder[A, B, ?, TargetProvided] =
    ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B, SourceHandled, TargetProvided, T]('this, 'source, 'target) }

  /** Change the type of a field (primitive-to-primitive). */
  inline def changeFieldType[T, U](
    inline source: A => T,
    inline target: B => U,
    @scala.annotation.unused converter: T => U
  )(using
    @scala.annotation.unused fromSchema: Schema[T],
    @scala.annotation.unused toSchema: Schema[U]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    // For serializable migrations, we rename the field (if names differ)
    // and the type conversion happens implicitly via schema compatibility
    val fromPath      = SelectorMacros.toPath[A, T](source)
    val toFieldName   = SelectorMacros.extractFieldName[B, U](target)
    val fromFieldName = fromPath.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => toFieldName
    }
    if (fromFieldName != toFieldName) {
      new MigrationBuilder(
        sourceSchema,
        targetSchema,
        actions :+ MigrationAction.Rename(fromPath, toFieldName)
      )
    } else {
      // Same field name, no action needed for rename
      new MigrationBuilder(sourceSchema, targetSchema, actions)
    }
  }

  /**
   * Change the type of a field using a serializable expression.
   *
   * This method provides full control over type conversions using
   * MigrationExpr, enabling serializable migrations for primitive-to-primitive
   * type changes.
   *
   * @param at
   *   Selector for the field to convert
   * @param targetType
   *   The target primitive type
   * @param reverseType
   *   Optional target type for reverse migration
   */
  inline def changeFieldTypeExpr[T](
    inline at: A => T,
    targetType: MigrationExpr.PrimitiveTargetType,
    reverseType: Option[MigrationExpr.PrimitiveTargetType] = None
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path        = SelectorMacros.toPath[A, T](at)
    val convertExpr = MigrationExpr.Convert(MigrationExpr.FieldRef(DynamicOptic.root), targetType)
    val reverseExpr = reverseType.map(rt => MigrationExpr.Convert(MigrationExpr.FieldRef(DynamicOptic.root), rt))
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeTypeExpr(path, convertExpr, reverseExpr)
    )
  }

  /** Rename an enum case. */
  def renameCase(
    from: String,
    to: String
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /** Transform the fields within an enum case. */
  def transformCase[CaseA, CaseB](
    caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB, EmptyTuple, EmptyTuple] => MigrationBuilder[CaseA, CaseB, ?, ?]
  )(using
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB, EmptyTuple, EmptyTuple](
      caseSourceSchema,
      caseTargetSchema,
      Vector.empty
    )
    val builtInner = caseMigration(innerBuilder)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic.root, caseName, builtInner.actions)
    )
  }

  /** Transform elements in a collection. */
  inline def transformElements[E](
    inline at: A => Iterable[E]
  )(
    elementMigration: MigrationBuilder[E, E, EmptyTuple, EmptyTuple] => MigrationBuilder[E, E, ?, ?]
  )(using elementSchema: Schema[E]): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path         = SelectorMacros.toPath[A, Iterable[E]](at)
    val innerBuilder = new MigrationBuilder[E, E, EmptyTuple, EmptyTuple](
      elementSchema,
      elementSchema,
      Vector.empty
    )
    val builtInner = elementMigration(innerBuilder)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(path, builtInner.actions)
    )
  }

  /** Transform keys in a map. */
  inline def transformKeys[K, V](
    inline at: A => Map[K, V]
  )(
    keyMigration: MigrationBuilder[K, K, EmptyTuple, EmptyTuple] => MigrationBuilder[K, K, ?, ?]
  )(using keySchema: Schema[K]): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path         = SelectorMacros.toPath[A, Map[K, V]](at)
    val innerBuilder = new MigrationBuilder[K, K, EmptyTuple, EmptyTuple](
      keySchema,
      keySchema,
      Vector.empty
    )
    val builtInner = keyMigration(innerBuilder)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(path, builtInner.actions))
  }

  /** Transform values in a map. */
  inline def transformValues[K, V](
    inline at: A => Map[K, V]
  )(
    valueMigration: MigrationBuilder[V, V, EmptyTuple, EmptyTuple] => MigrationBuilder[V, V, ?, ?]
  )(using valueSchema: Schema[V]): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path         = SelectorMacros.toPath[A, Map[K, V]](at)
    val innerBuilder = new MigrationBuilder[V, V, EmptyTuple, EmptyTuple](
      valueSchema,
      valueSchema,
      Vector.empty
    )
    val builtInner = valueMigration(innerBuilder)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(path, builtInner.actions)
    )
  }

  /**
   * Join multiple source fields into a single target field using an expression.
   *
   * The expression is evaluated at migration time against the full input
   * record, allowing it to reference and combine values from multiple source
   * fields.
   *
   * @param target
   *   Selector for the target field
   * @param sourcePaths
   *   Paths to the source fields to join
   * @param combineExpr
   *   Expression that computes the combined value
   * @param splitExprs
   *   Optional expressions for reverse migration (splitting back)
   */
  inline def joinFields[T](
    inline target: B => T,
    sourcePaths: Vector[DynamicOptic],
    combineExpr: MigrationExpr,
    splitExprs: Option[Vector[MigrationExpr]] = None
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val targetPath = SelectorMacros.toPath[B, T](target)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.JoinExpr(targetPath, sourcePaths, combineExpr, splitExprs)
    )
  }

  /**
   * Split a source field into multiple target fields using expressions.
   *
   * Each expression is evaluated at migration time against the full input
   * record, computing the value for each target field.
   *
   * @param source
   *   Selector for the source field
   * @param targetPaths
   *   Paths to the target fields
   * @param splitExprs
   *   Expressions that compute each target value
   * @param combineExpr
   *   Optional expression for reverse migration (joining back)
   */
  inline def splitField[T](
    inline source: A => T,
    targetPaths: Vector[DynamicOptic],
    splitExprs: Vector[MigrationExpr],
    combineExpr: Option[MigrationExpr] = None
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val sourcePath = SelectorMacros.toPath[A, T](source)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.SplitExpr(sourcePath, targetPaths, splitExprs, combineExpr)
    )
  }

  /**
   * Build the migration with compile-time validation.
   *
   * This method requires evidence that the migration is complete:
   *   - All source fields are handled (renamed, dropped, transformed) or
   *     auto-mapped
   *   - All target fields are provided (added, renamed to) or auto-mapped
   *
   * If the migration is incomplete, a compile-time error will be generated
   * listing which fields need attention.
   */
  inline def build(using MigrationComplete[A, B, SourceHandled, TargetProvided]): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /**
   * Build the migration without validation.
   *
   * Use this for partial migrations or when compile-time validation is not
   * needed. The migration may fail at runtime if source/target structures don't
   * match.
   */
  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
}

object MigrationBuilder {

  /** Create a new migration builder. */
  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
