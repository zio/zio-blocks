package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * A builder for constructing migrations from type `A` to type `B`.
 *
 * All selector-accepting methods use inline macros that:
 * 1. Inspect the selector expression
 * 2. Validate it is a supported projection
 * 3. Convert it into a `DynamicOptic`
 * 4. Store that optic in the migration action
 *
 * `DynamicOptic` is never exposed publicly in the builder API.
 *
 * @tparam A The source type
 * @tparam B The target type
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  // ----- Record operations -----

  /**
   * Add a new field to the target with a default value.
   *
   * @param target Selector for the field to add
   * @param default Expression providing the default value
   */
  inline def addField(
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val targetPath = SelectorMacros.toPath[B, Any](target)
    appendAction(MigrationAction.AddField(targetPath, default))
  }

  /**
   * Drop a field from the source.
   *
   * @param source Selector for the field to drop
   * @param defaultForReverse Default value to use when reversing the migration (for re-adding the field)
   */
  inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[_, _] 
  ): MigrationBuilder[A, B] = {
    val sourcePath = SelectorMacros.toPath[A, Any](source)
    appendAction(MigrationAction.DropField(sourcePath, defaultForReverse))
  }

  /**
   * Rename a field from source to target.
   */
  inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacros.toPath[A, Any](from)
    val toPath = SelectorMacros.toPath[B, Any](to)
    val toName = toPath.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target selector must end with a field access")
    }
    appendAction(MigrationAction.Rename(fromPath, toName))
  }

  /**
   * Transform a field value.
   *
   * @param from Selector for the source field
   * @param to Selector for the target field (used for validation)
   * @param transform Expression that computes the new value
   */
  inline def transformField(
    inline from: A => Any,
    @scala.annotation.unused inline to: B => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacros.toPath[A, Any](from)
    appendAction(MigrationAction.TransformValue(fromPath, transform))
  }

  /**
   * Convert an optional field in source to a required field in target.
   *
   * @param source Selector for the optional source field
   * @param target Selector for the required target field (used for validation)
   * @param default Expression providing the default value when source is None
   */
  inline def mandateField(
    inline source: A => Option[?],
    @scala.annotation.unused inline target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val sourcePath = SelectorMacros.toPath[A, Option[?]](source)
    appendAction(MigrationAction.Mandate(sourcePath, default))
  }

  /**
   * Convert a required field in source to an optional field in target.
   */
  inline def optionalizeField(
    inline source: A => Any,
    @scala.annotation.unused inline target: B => Option[?]
  ): MigrationBuilder[A, B] = {
    val sourcePath = SelectorMacros.toPath[A, Any](source)
    appendAction(MigrationAction.Optionalize(sourcePath))
  }

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * @param source Selector for the source field
   * @param target Selector for the target field (used for validation)
   * @param converter Expression that converts between types
   */
  inline def changeFieldType(
    inline source: A => Any,
    @scala.annotation.unused inline target: B => Any,
    converter: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val sourcePath = SelectorMacros.toPath[A, Any](source)
    appendAction(MigrationAction.ChangeType(sourcePath, converter))
  }

  // ----- Enum operations -----

  /**
   * Rename an enum case.
   */
  def renameCase(
    from: String,
    to: String
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /**
   * Transform the fields within an enum case.
   */
  def transformCase[CaseA, CaseB](
    caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(using
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB](caseSourceSchema, caseTargetSchema, Vector.empty)
    val builtInner = caseMigration(innerBuilder)
    appendAction(MigrationAction.TransformCase(DynamicOptic.root.caseOf(caseName), builtInner.actions))
  }

  // ----- Collections -----

  /**
   * Transform each element in a collection.
   *
   * @param at Selector for the collection field
   * @param transform Expression that transforms each element
   */
  inline def transformElements(
    inline at: A => Iterable[?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.toPath[A, Iterable[?]](at)
    appendAction(MigrationAction.TransformElements(path, transform))
  }

  // ----- Maps -----

  /**
   * Transform each key in a map.
   *
   * @param at Selector for the map field
   * @param transform Expression that transforms each key
   */
  inline def transformKeys(
    inline at: A => Map[?, ?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.toPath[A, Map[?, ?]](at)
    appendAction(MigrationAction.TransformKeys(path, transform))
  }

  /**
   * Transform each value in a map.
   *
   * @param at Selector for the map field
   * @param transform Expression that transforms each value
   */
  inline def transformValues(
    inline at: A => Map[?, ?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.toPath[A, Map[?, ?]](at)
    appendAction(MigrationAction.TransformValues(path, transform))
  }


  // ----- Build -----

  /**
   * Build migration with full macro validation.
   */
  def build: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /**
   * Build migration without full validation.
   */
  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  // ----- Internal -----

  private[migration] def appendAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

object MigrationBuilder {
  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

