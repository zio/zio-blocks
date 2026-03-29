package zio.blocks.schema.migration

import scala.annotation.compileTimeOnly
import zio.blocks.schema.{ DynamicOptic, Schema, SchemaExpr }
import MigrationAction.*

// ─────────────────────────────────────────────────────────────────────────────
//  MigrationBuilder[A, B]
//  Fluent builder for constructing typed migrations.
//  All selector-accepting methods are macros — DynamicOptic is never exposed.
// ─────────────────────────────────────────────────────────────────────────────

final class MigrationBuilder[A, B] private[migration] (
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  private val actions: Vector[MigrationAction]
) {

  // ── Record operations ──────────────────────────────────────────────────────

  /**
   * Add a new field to the target type B.
   * The macro extracts `target`'s path and validates it exists in B.
   * `default` computes the field value from the source data.
   *
   * {{{
   *   .addField(_.country, SchemaExpr.const("RO"))
   * }}}
   */
  inline def addField[V](
    inline target: B => V,
    default: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.addFieldImpl[A, B, V]('this, 'target, 'default) }

  /**
   * Drop a field from the source type A.
   * The macro extracts `source`'s path and validates it exists in A.
   *
   * {{{
   *   .dropField(_.legacyId)
   * }}}
   */
  inline def dropField[V](
    inline source: A => V,
    defaultForReverse: SchemaExpr[B, V] = SchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.dropFieldImpl[A, B, V]('this, 'source, 'defaultForReverse) }

  /**
   * Rename a field from source to target.
   * Both selectors are macro-validated.
   *
   * {{{
   *   .renameField(_.name, _.fullName)
   * }}}
   */
  inline def renameField[V](
    inline from: A => V,
    inline to: B => V
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.renameFieldImpl[A, B, V]('this, 'from, 'to) }

  /**
   * Transform the value of a field.
   *
   * {{{
   *   .transformField(_.age, _.age, SchemaExpr.map(a => a + 1))
   * }}}
   */
  inline def transformField[V, W](
    inline from: A => V,
    inline to: B => W,
    transform: SchemaExpr[A, W]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformFieldImpl[A, B, V, W]('this, 'from, 'to, 'transform) }

  /**
   * Make an optional field mandatory.
   *
   * {{{
   *   .mandateField(_.maybeEmail, SchemaExpr.const("unknown@example.com"))
   * }}}
   */
  inline def mandateField[V](
    inline source: A => Option[V],
    inline target: B => V,
    default: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.mandateFieldImpl[A, B, V]('this, 'source, 'target, 'default) }

  /**
   * Make a mandatory field optional.
   *
   * {{{
   *   .optionalizeField(_.email, _.maybeEmail)
   * }}}
   */
  inline def optionalizeField[V](
    inline source: A => V,
    inline target: B => Option[V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.optionalizeFieldImpl[A, B, V]('this, 'source, 'target) }

  /**
   * Change the type of a field with an explicit converter.
   *
   * {{{
   *   .changeFieldType(_.age, _.age, SchemaExpr.map(i => i.toString))
   * }}}
   */
  inline def changeFieldType[V, W](
    inline source: A => V,
    inline target: B => W,
    converter: SchemaExpr[A, W]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.changeFieldTypeImpl[A, B, V, W]('this, 'source, 'target, 'converter) }

  /**
   * Combine multiple source fields into a single target field.
   *
   * {{{
   *   .joinFields(
   *     sources = List(_.firstName, _.lastName),
   *     target  = _.fullName,
   *     combiner = SchemaExpr.joinStrings(" ")
   *   )
   * }}}
   */
  inline def joinFields[V](
    inline sources: List[A => Any],
    inline target: B => V,
    combiner: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.joinFieldsImpl[A, B, V]('this, 'sources, 'target, 'combiner) }

  /**
   * Split a single source field into multiple target fields.
   */
  inline def splitField[V](
    inline source: A => V,
    inline targets: List[B => Any],
    splitter: SchemaExpr[A, List[Any]]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.splitFieldImpl[A, B, V]('this, 'source, 'targets, 'splitter) }

  // ── Enum / Sum operations ──────────────────────────────────────────────────

  /** Rename an enum case. */
  inline def renameCase[SumA, SumB](
    inline at: A => SumA,
    from: String,
    to: String
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.renameCaseImpl[A, B, SumA]('this, 'at, 'from, 'to) }

  /** Apply a nested migration to a specific enum case. */
  inline def transformCase[CaseA, CaseB](
    inline at: A => CaseA,
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformCaseImpl[A, B, CaseA, CaseB]('this, 'at, 'caseMigration, 'caseSourceSchema, 'caseTargetSchema) }

  // ── Collection / Map operations ────────────────────────────────────────────

  /** Transform each element of a collection field. */
  inline def transformElements[V](
    inline at: A => Seq[V],
    transform: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformElementsImpl[A, B, V]('this, 'at, 'transform) }

  /** Transform each key in a map field. */
  inline def transformKeys[K, V](
    inline at: A => Map[K, V],
    transform: SchemaExpr[A, K]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformKeysImpl[A, B, K, V]('this, 'at, 'transform) }

  /** Transform each value in a map field. */
  inline def transformValues[K, V](
    inline at: A => Map[K, V],
    transform: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationMacros.transformValuesImpl[A, B, K, V]('this, 'at, 'transform) }

  // ── Build ──────────────────────────────────────────────────────────────────

  /** Build the Migration with full macro validation. */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /** Build without full cross-field validation (useful for partial migrations). */
  def buildPartial: Migration[A, B] = build

  // ── Internal — used by macros only ────────────────────────────────────────

  private[migration] def appendAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)

  private[migration] def appendActions(newActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions ++ newActions)
}

object MigrationBuilder {
  def apply[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions)
}
