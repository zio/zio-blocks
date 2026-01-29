package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * A builder for constructing schema migrations.
 *
 * The MigrationBuilder provides a fluent API for defining migrations between
 * schema versions. It supports all common migration operations including
 * adding/removing fields, renaming, transforming values, and working with
 * collections and enums.
 *
 * All selector-based methods accept functions like `_.fieldName` which are
 * converted to [[DynamicOptic]] paths using the [[ToDynamicOptic]] type class.
 *
 * @param sourceSchema the schema for the source type A
 * @param targetSchema the schema for the target type B
 * @param actions the accumulated migration actions
 * @tparam A the source type
 * @tparam B the target type
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ==========================================================================
  // Record Operations
  // ==========================================================================

  /**
   * Adds a new field with a default value.
   *
   * The target selector specifies where the new field should be added.
   * The default value expression provides the initial value.
   *
   * Example:
   * {{`
   *   Migration.newBuilder[PersonV0, PersonV1]
   *     .addField(_.age, SchemaExpr.Literal(0, Schema.int))
   * `}}
   *
   * @param target a selector for the new field location
   * @param default the default value expression
   * @tparam C the type of the default value
   * @return an updated builder
   */
  def addField[C](target: B => C, default: SchemaExpr[A, C]): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(target).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(optic, default)
    )
  }

  /**
   * Adds a new field with a literal default value.
   *
   * This is a convenience method that wraps the value in a Literal expression.
   *
   * @param target a selector for the new field location
   * @param defaultValue the default value
   * @tparam C the type of the default value
   * @return an updated builder
   */
  def addField[C](target: B => C, defaultValue: C)(implicit schema: Schema[C]): MigrationBuilder[A, B] = {
    addField(target, SchemaExpr.Literal(defaultValue, schema))
  }

  /**
   * Removes a field.
   *
   * The source selector specifies which field to remove. For reverse migration,
   * a default value must be provided.
   *
   * @param source a selector for the field to remove
   * @param defaultForReverse the default value for reverse migration
   * @tparam C the type of the field
   * @return an updated builder
   */
  def dropField[C](
    source: A => C,
    defaultForReverse: SchemaExpr[B, C]
  ): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(source).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(optic, defaultForReverse)
    )
  }

  /**
   * Removes a field with a literal default for reverse migration.
   *
   * @param source a selector for the field to remove
   * @param defaultValue the default value for reverse migration
   * @tparam C the type of the field
   * @return an updated builder
   */
  def dropField[C](source: A => C, defaultValue: C)(implicit schema: Schema[C]): MigrationBuilder[A, B] = {
    dropField(source, SchemaExpr.Literal(defaultValue, schema))
  }

  /**
   * Renames a field.
   *
   * The source selector specifies the field to rename, and the target selector
   * specifies the new name.
   *
   * Example:
   * {{`
   *   Migration.newBuilder[OldPerson, NewPerson]
   *     .renameField(_.fullName, _.name)
   * `}}
   *
   * @param from a selector for the field to rename
   * @param to a selector for the new field name
   * @tparam C the type of the field
   * @return an updated builder
   */
  def renameField[C](from: A => C, to: B => C): MigrationBuilder[A, B] = {
    val fromOptic = ToDynamicOptic.derive(from).optic
    val toOptic = ToDynamicOptic.derive(to).optic

    // Extract the target field name from the to optic
    val newName = toOptic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ =>
        throw new IllegalArgumentException("Target selector must be a field access")
    }

    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Rename(fromOptic, newName)
    )
  }

  /**
   * Transforms a field's value.
   *
   * The source selector specifies the field to transform, and the target
   * selector specifies where the result should go. The transform expression
   * defines how to convert the value.
   *
   * Example:
   * {{`
   *   Migration.newBuilder[Person, Person]
   *     .transformField(
   *       _.age,
   *       _.age,
   *       SchemaExpr.Arithmetic(
   *         SchemaExpr.Optic(optic(_.age)),
   *         SchemaExpr.Literal(1, Schema.int),
   *         SchemaExpr.ArithmeticOperator.Add
   *       )
   *     )
   * `}}
   *
   * @param from a selector for the source field
   * @param to a selector for the target field
   * @param transform the transformation expression
   * @tparam C the source field type
   * @tparam D the target field type
   * @return an updated builder
   */
  def transformField[C, D](
    from: A => C,
    to: B => D,
    transform: SchemaExpr[A, D]
  ): MigrationBuilder[A, B] = {
    val fromOptic = ToDynamicOptic.derive(from).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(fromOptic, transform, None)
    )
  }

  /**
   * Transforms a field's value with a bidirectional transformation.
   *
   * @param from a selector for the source field
   * @param to a selector for the target field
   * @param transform the forward transformation
   * @param reverseTransform the reverse transformation
   * @tparam C the source field type
   * @tparam D the target field type
   * @return an updated builder
   */
  def transformField[C, D](
    from: A => C,
    to: B => D,
    transform: SchemaExpr[A, D],
    reverseTransform: SchemaExpr[B, C]
  ): MigrationBuilder[A, B] = {
    val fromOptic = ToDynamicOptic.derive(from).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(fromOptic, transform, Some(reverseTransform))
    )
  }

  /**
   * Makes an optional field mandatory.
   *
   * If the field is None, the default value is used.
   *
   * @param source a selector for the optional field
   * @param target a selector for the mandatory field
   * @param default the default value for None cases
   * @tparam C the base type (without Option)
   * @return an updated builder
   */
  def mandateField[C](
    source: A => Option[C],
    target: B => C,
    default: SchemaExpr[A, C]
  ): MigrationBuilder[A, B] = {
    val sourceOptic = ToDynamicOptic.derive(source).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Mandate(sourceOptic, default)
    )
  }

  /**
   * Makes a mandatory field optional.
   *
   * @param source a selector for the mandatory field
   * @param target a selector for the optional field
   * @tparam C the base type (without Option)
   * @return an updated builder
   */
  def optionalizeField[C](
    source: A => C,
    target: B => Option[C]
  ): MigrationBuilder[A, B] = {
    val sourceOptic = ToDynamicOptic.derive(source).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Optionalize(sourceOptic)
    )
  }

  /**
   * Changes the type of a field (primitive-to-primitive only).
   *
   * @param source a selector for the source field
   * @param target a selector for the target field
   * @param converter the type conversion expression
   * @tparam C the source type
   * @tparam D the target type
   * @return an updated builder
   */
  def changeFieldType[C, D](
    source: A => C,
    target: B => D,
    converter: SchemaExpr[A, D]
  ): MigrationBuilder[A, B] = {
    val sourceOptic = ToDynamicOptic.derive(source).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(sourceOptic, converter, None)
    )
  }

  /**
   * Changes the type of a field with bidirectional conversion.
   *
   * @param source a selector for the source field
   * @param target a selector for the target field
   * @param converter the forward conversion
   * @param reverseConverter the reverse conversion
   * @tparam C the source type
   * @tparam D the target type
   * @return an updated builder
   */
  def changeFieldType[C, D](
    source: A => C,
    target: B => D,
    converter: SchemaExpr[A, D],
    reverseConverter: SchemaExpr[B, C]
  ): MigrationBuilder[A, B] = {
    val sourceOptic = ToDynamicOptic.derive(source).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(sourceOptic, converter, Some(reverseConverter))
    )
  }

  // ==========================================================================
  // Join and Split Operations
  // ==========================================================================

  /**
   * Joins multiple fields into a single field.
   *
   * @param target a selector for the joined field
   * @param sourcePaths the selectors for the fields to join
   * @param combiner the expression that combines the source values
   * @tparam C the type of the joined field
   * @return an updated builder
   */
  def joinFields[C](
    target: B => C,
    sourcePaths: Vector[A => _],
    combiner: SchemaExpr[A, C]
  ): MigrationBuilder[A, B] = {
    val targetOptic = ToDynamicOptic.derive(target).optic
    val sourceOptics = sourcePaths.map(f => ToDynamicOptic.derive(f).optic)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Join(targetOptic, sourceOptics, combiner)
    )
  }

  /**
   * Splits a field into multiple fields.
   *
   * @param source a selector for the field to split
   * @param targetPaths the selectors for the target fields
   * @param splitter the expression that splits the source value
   * @tparam C the type of the source field
   * @return an updated builder
   */
  def splitField[C](
    source: A => C,
    targetPaths: Vector[B => _],
    splitter: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = {
    val sourceOptic = ToDynamicOptic.derive(source).optic
    val targetOptics = targetPaths.map(f => ToDynamicOptic.derive(f).optic)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Split(sourceOptic, targetOptics, splitter)
    )
  }

  // ==========================================================================
  // Enum Operations
  // ==========================================================================

  /**
   * Renames a case in an enum/sum type.
   *
   * @param from the original case name
   * @param to the new case name
   * @return an updated builder
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] = {
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to)
    )
  }

  /**
   * Renames a case in a nested enum.
   *
   * @param path a selector for the enum field
   * @param from the original case name
   * @param to the new case name
   * @tparam C the enum type
   * @return an updated builder
   */
  def renameCaseAt[C](path: A => C, from: String, to: String): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(path).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(optic, from, to)
    )
  }

  /**
   * Transforms a specific case in an enum.
   *
   * @param caseName the name of the case to transform
   * @param caseMigration a builder function for the case transformation
   * @tparam SumA the source sum type
   * @tparam CaseA the source case type
   * @tparam SumB the target sum type
   * @tparam CaseB the target case type
   * @return an updated builder
   */
  def transformCase[CaseA, CaseB](
    caseName: String,
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit caseASchema: Schema[CaseA], caseBSchema: Schema[CaseB]): MigrationBuilder[A, B] = {
    val innerBuilder = caseMigration(Migration.newBuilder[CaseA, CaseB])
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic.root, caseName, innerBuilder.actions)
    )
  }

  // ==========================================================================
  // Collection Operations
  // ==========================================================================

  /**
   * Transforms all elements in a collection.
   *
   * @param at a selector for the collection field
   * @param transform the transformation expression for each element
   * @tparam C the element type
   * @tparam D the transformed element type
   * @return an updated builder
   */
  def transformElements[C, D](
    at: A => Vector[C],
    transform: SchemaExpr[A, D]
  ): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(at).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(optic, transform)
    )
  }

  // ==========================================================================
  // Map Operations
  // ==========================================================================

  /**
   * Transforms all keys in a map.
   *
   * @param at a selector for the map field
   * @param transform the transformation expression for each key
   * @tparam K the key type
   * @tparam V the value type
   * @tparam K2 the transformed key type
   * @return an updated builder
   */
  def transformKeys[K, V, K2](
    at: A => Map[K, V],
    transform: SchemaExpr[A, K2]
  ): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(at).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformKeys(optic, transform)
    )
  }

  /**
   * Transforms all values in a map.
   *
   * @param at a selector for the map field
   * @param transform the transformation expression for each value
   * @tparam K the key type
   * @tparam V the value type
   * @tparam V2 the transformed value type
   * @return an updated builder
   */
  def transformValues[K, V, V2](
    at: A => Map[K, V],
    transform: SchemaExpr[A, V2]
  ): MigrationBuilder[A, B] = {
    val optic = ToDynamicOptic.derive(at).optic
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(optic, transform)
    )
  }

  // ==========================================================================
  // Builder Finalization
  // ==========================================================================

  /**
   * Builds the migration with full validation.
   *
   * This method validates that the accumulated actions properly transform
   * the source schema to the target schema.
   *
   * @return the built migration
   */
  def build: Migration[A, B] = {
    // TODO: Add comprehensive validation
    // - Check that all source fields referenced exist
    // - Check that all target fields are properly created
    // - Verify type compatibility
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Builds the migration without full validation.
   *
   * This method creates the migration without performing comprehensive
   * validation. Use with caution.
   *
   * @return the built migration
   */
  def buildPartial: Migration[A, B] = {
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }
}

object MigrationBuilder {

  /**
   * Creates a new migration builder.
   */
  def apply[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)
}
