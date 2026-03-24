package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

object Changeset {

  /**
   * Phantom type wrapper to preserve literal string types through macro
   * expansion. Direct use of ConstantType causes literal types to widen to
   * String. By wrapping in FieldName[N], the type argument is preserved when
   * using appliedType.
   */
  sealed trait FieldName[N]

  // Field operations (tracked for validation)
  sealed trait AddField[N]
  sealed trait DropField[N]
  sealed trait RenameField[From, To]
  sealed trait TransformField[From, To]
  sealed trait MandateField[Source, Target]
  sealed trait OptionalizeField[Source, Target]
  sealed trait ChangeFieldType[Source, Target]
  sealed trait MigrateField[Name]

  // Case operations (tracked but not field-validated)
  sealed trait RenameCase[From, To]
  sealed trait TransformCase[CaseName]

  // Collection operations (tracked but not field-validated)
  sealed trait TransformElements[FieldName]
  sealed trait TransformKeys[FieldName]
  sealed trait TransformValues[FieldName]
}

/**
 * A builder for constructing migrations from type `A` to type `B`.
 *
 * All selector-accepting methods are implemented via macros that:
 *   1. Inspect the selector expression
 *   2. Validate it is a supported projection
 *   3. Convert it into a `DynamicOptic`
 *   4. Store that optic in the migration action
 *
 * `DynamicOptic` is never exposed publicly in the builder API.
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 * @tparam Changeset
 *   Intersection type tracking which operations have been performed
 */
final class MigrationBuilder[A, B, Changeset](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  // ----- Record operations -----

  /**
   * Add a new field to the target with a default value.
   *
   * @param target
   *   Selector for the field to add
   * @param default
   *   Expression providing the default value
   */
  // format: off
  def addField(target: B => Any, default: SchemaExpr[_, _]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.addFieldImpl[A, B, Changeset]
  // format: on

  /**
   * Drop a field from the source.
   *
   * @param source
   *   Selector for the field to drop
   * @param defaultForReverse
   *   Default value to use when reversing the migration (for re-adding the
   *   field)
   */
  // format: off
  def dropField(source: A => Any, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B, Changeset]
  // format: on

  /**
   * Rename a field from source to target.
   */
  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B, _] = macro MigrationBuilderMacros.renameFieldImpl[A, B, Changeset]

  /**
   * Transform a field value.
   *
   * @param from
   *   Selector for the source field
   * @param to
   *   Selector for the target field (used for validation)
   * @param transform
   *   Expression that computes the new value
   */
  def transformField(
    from: A => Any,
    to: B => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, _] = macro MigrationBuilderMacros.transformFieldImpl[A, B, Changeset]

  /**
   * Convert an optional field in source to a required field in target.
   *
   * @param source
   *   Selector for the optional source field
   * @param target
   *   Selector for the required target field (used for validation)
   * @param default
   *   Expression providing the default value when source is None
   */
  def mandateField(
    source: A => Option[_],
    target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, _] = macro MigrationBuilderMacros.mandateFieldImpl[A, B, Changeset]

  /**
   * Convert a required field in source to an optional field in target.
   */
  // format: off
  def optionalizeField(source: A => Any, target: B => Option[_]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.optionalizeFieldImpl[A, B, Changeset]
  // format: on

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * @param source
   *   Selector for the source field
   * @param target
   *   Selector for the target field (used for validation)
   * @param converter
   *   Expression that converts between types
   */
  // format: off
  def changeFieldType(source: A => Any, target: B => Any, converter: SchemaExpr[_, _]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.changeFieldTypeImpl[A, B, Changeset]
  // format: on


  // format: off
  def migrateField[F1, F2](selector: A => F1, migration: Migration[F1, F2]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.migrateFieldExplicitImpl[A, B, F1, F2, Changeset]
  // format: on

  // format: off
  def migrateField[F1, F2](selector: A => F1)(implicit migration: Migration[F1, F2]): MigrationBuilder[A, B, _] =
    macro MigrationBuilderMacros.migrateFieldImplicitImpl[A, B, F1, F2, Changeset]
  // format: on

  // ----- Enum operations -----

  /**
   * Rename an enum case.
   */
  def renameCase(
    from: String,
    to: String
  ): MigrationBuilder[A, B, Changeset] =
    appendAction(MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /**
   * Transform the fields within an enum case.
   */
  def transformCase[CaseA, CaseB](
    caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB, Any] => MigrationBuilder[CaseA, CaseB, _]
  )(implicit
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B, Changeset] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB, Any](caseSourceSchema, caseTargetSchema, Vector.empty)
    val builtInner   = caseMigration(innerBuilder)
    appendAction(MigrationAction.TransformCase(DynamicOptic.root.caseOf(caseName), builtInner.actions))
  }

  // ----- Collections -----

  /**
   * Transform each element in a collection.
   *
   * @param at
   *   Selector for the collection field
   * @param transform
   *   Expression that transforms each element
   */
  // format: off
  def transformElements(at: A => Iterable[_], transform: SchemaExpr[_, _]): MigrationBuilder[A, B, Changeset] =
    macro MigrationBuilderMacros.transformElementsImpl[A, B, Changeset]
  // format: on

  // ----- Maps -----

  /**
   * Transform each key in a map.
   *
   * @param at
   *   Selector for the map field
   * @param transform
   *   Expression that transforms each key
   */
  // format: off
  def transformKeys(at: A => Map[_, _], transform: SchemaExpr[_, _]): MigrationBuilder[A, B, Changeset] =
    macro MigrationBuilderMacros.transformKeysImpl[A, B, Changeset]
  // format: on

  /**
   * Transform each value in a map.
   *
   * @param at
   *   Selector for the map field
   * @param transform
   *   Expression that transforms each value
   */
  // format: off
  def transformValues(at: A => Map[_, _], transform: SchemaExpr[_, _]): MigrationBuilder[A, B, Changeset] =
    macro MigrationBuilderMacros.transformValuesImpl[A, B, Changeset]
  // format: on

  // ----- Build -----

  /**
   * Build migration with full macro validation. Requires implicit evidence that
   * all source fields are handled and all target fields are provided.
   */
  def build(implicit ev: MigrationComplete[A, B, Changeset]): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /**
   * Build migration without full validation.
   */
  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  // ----- Internal -----

  private[migration] def appendAction(action: MigrationAction): MigrationBuilder[A, B, Changeset] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

object MigrationBuilder {
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

/**
 * Evidence that a migration is complete. All source fields must be handled and
 * all target fields must be provided.
 */
trait MigrationComplete[-A, -B, -Changeset]

object MigrationComplete {
  private[migration] def unsafeCreate[A, B, CS]: MigrationComplete[A, B, CS] =
    instance.asInstanceOf[MigrationComplete[A, B, CS]]

  private val instance: MigrationComplete[Any, Any, Any] =
    new MigrationComplete[Any, Any, Any] {}

  // format: off
  implicit def derive[A, B, CS]: MigrationComplete[A, B, CS] =
    macro MigrationValidationMacros.validateMigrationImpl[A, B, CS]
  // format: on
}
