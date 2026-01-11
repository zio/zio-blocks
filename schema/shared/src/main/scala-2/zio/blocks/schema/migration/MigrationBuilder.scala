package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A builder for constructing migrations from type `A` to type `B`.
 *
 * All selector-accepting methods are implemented via macros that:
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
   * The default is specified as a `DynamicValue` for serializability.
   */
  def addField(
    target: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.addFieldImpl[A, B]

  /**
   * Drop a field from the source.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldImpl[A, B]

  /**
   * Rename a field from source to target.
   */
  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameFieldImpl[A, B]

  /**
   * Transform a field value.
   */
  def transformField(
    from: A => Any,
    to: B => Any,
    transform: DynamicTransform
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformFieldImpl[A, B]

  /**
   * Convert an optional field in source to a required field in target.
   */
  def mandateField(
    source: A => Option[?],
    target: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.mandateFieldImpl[A, B]

  /**
   * Convert a required field in source to an optional field in target.
   */
  def optionalizeField(
    source: A => Any,
    target: B => Option[?]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  /**
   * Change the type of a field (primitive-to-primitive only).
   */
  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: DynamicTransform
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.changeFieldTypeImpl[A, B]

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
  )(implicit
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB](caseSourceSchema, caseTargetSchema, Vector.empty)
    val builtInner = caseMigration(innerBuilder)
    appendAction(MigrationAction.TransformCase(DynamicOptic.root, caseName, builtInner.actions))
  }

  // ----- Collections -----

  /**
   * Transform each element in a collection.
   */
  def transformElements(
    at: A => Iterable[?],
    transform: DynamicTransform
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformElementsImpl[A, B]

  // ----- Maps -----

  /**
   * Transform each key in a map.
   */
  def transformKeys(
    at: A => Map[?, ?],
    transform: DynamicTransform
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformKeysImpl[A, B]

  /**
   * Transform each value in a map.
   */
  def transformValues(
    at: A => Map[?, ?],
    transform: DynamicTransform
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformValuesImpl[A, B]

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
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

