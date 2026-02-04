package zio.blocks.schema.migration


import scala.language.experimental.macros
import zio.blocks.schema.{Schema, SchemaExpr, ToStructural}
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.MigrationAction._

/**
 * Scala-2 macro-backed MigrationBuilder DSL (issue #519).
 *
 * This mirrors the Scala-3 builder API:
 *   - user writes selectors (A => Any, B => Any), never optics
 *   - macros compile selectors into DynamicOptic paths
 *   - the resulting migration is pure data: Vector[MigrationAction]
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) { self =>

  // ----------------------------
  // Record operations
  // ----------------------------

  def addField(
    target: B => Any,
    default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.addFieldImpl[A, B]

  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[B, _] = MigrationSchemaExpr.default
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldImpl[A, B]

  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameFieldImpl[A, B]

  def transformField(
    from: A => Any,
    to: B => Any,
    transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformFieldImpl[A, B]

  def mandateField(
    source: A => Option[_],
    target: B => Any,
    default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.mandateFieldImpl[A, B]

  def optionalizeField(
    source: A => Any,
    target: B => Option[_]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.changeFieldTypeImpl[A, B]

  // ----------------------------
  // Enum operations (limited)
  // ----------------------------

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    copyAppended(RenameCase(at = DynamicOptic.root, from = from, to = to))

  def renameCaseAt(
    at: A => Any,
    from: String,
    to: String
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameCaseAtImpl[A, B]

  def transformCaseAt[CaseA, CaseB](
  at: A => CaseA
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformCaseAtImpl[A, B, CaseA, CaseB]



  def transformCase[CaseA, CaseB](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit sa: Schema[CaseA], sb: Schema[CaseB]): MigrationBuilder[A, B] = {
    val nested = caseMigration(new MigrationBuilder[CaseA, CaseB](sa, sb, Vector.empty))
    copyAppended(TransformCase(at = DynamicOptic.root, actions = nested.actions))
  }

  // ----------------------------
  // Collections / Maps
  // ----------------------------

  def transformElements(
    at: A => Vector[_],
    transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformElementsImpl[A, B]

  def transformKeys(
    at: A => Map[_, _],
    transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformKeysImpl[A, B]

  def transformValues(
    at: A => Map[_, _],
    transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformValuesImpl[A, B]

  // ----------------------------
  // Build
  // ----------------------------

  /** Build migration with validation (shape + constraints). */
  def build(implicit ta: ToStructural[A], tb: ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    MigrationValidator.validateOrThrow(prog, sourceSchema, targetSchema)
    Migration.fromProgram[A, B](prog)(sourceSchema, targetSchema, ta, tb)
  }

  /** Build migration without validation. */
  def buildPartial(implicit ta: ToStructural[A], tb: ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    Migration.fromProgram[A, B](prog)(sourceSchema, targetSchema, ta, tb)
  }

  // ----------------------------
  // Internals
  // ----------------------------

  private[migration] def copyAppended(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

object MigrationBuilder {
  def apply[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sa, sb, Vector.empty)
}
