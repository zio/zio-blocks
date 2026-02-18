package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * Type-safe builder for constructing [[Migration]]s. Selector expressions like
 * `_.field.nested` are converted to [[DynamicOptic]] paths at compile time via
 * macros.
 */
final class MigrationBuilder[A, B](
  val actions: Chunk[MigrationAction],
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B]
) {

  // ── Record Operations ─────────────────────────────────────────────

  def addField(target: B => Any, default: DynamicValue): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.addFieldImpl[A, B]

  def dropField(source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.dropFieldImpl[A, B]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.renameFieldImpl[A, B]

  def transformField(from: A => Any, to: B => Any, transform: DynamicValue): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.transformFieldImpl[A, B]

  def mandateField(source: A => Any, default: DynamicValue): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.mandateFieldImpl[A, B]

  def optionalizeField(source: A => Any): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  def changeFieldType(source: A => Any, converter: DynamicValue): MigrationBuilder[A, B] = macro
    MigrationBuilderMacros.changeFieldTypeImpl[A, B]

  // ── Enum Operations ───────────────────────────────────────────────

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to),
      sourceSchema,
      targetSchema
    )

  def transformCase(caseName: String)(
    f: MigrationBuilder.Nested => MigrationBuilder.Nested
  ): MigrationBuilder[A, B] = {
    val nested    = f(MigrationBuilder.Nested.empty)
    val caseOptic = DynamicOptic.root.caseOf(caseName)
    new MigrationBuilder(
      actions :+ MigrationAction.TransformCase(caseOptic, nested.actions),
      sourceSchema,
      targetSchema
    )
  }

  // ── Collection / Map Operations ───────────────────────────────────

  def transformElements(at: DynamicOptic, transform: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformElements(at, transform), sourceSchema, targetSchema)

  def transformKeys(at: DynamicOptic, transform: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformKeys(at, transform), sourceSchema, targetSchema)

  def transformValues(at: DynamicOptic, transform: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValues(at, transform), sourceSchema, targetSchema)

  // ── Build ─────────────────────────────────────────────────────────

  /** Build migration with full validation. */
  def build: Migration[A, B] =
    new Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  /** Build migration without full validation. */
  def buildPartial: Migration[A, B] =
    new Migration(new DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {

  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(Chunk.empty, sourceSchema, targetSchema)

  /** Builder for nested contexts (inside `transformCase`). */
  final class Nested private[migration] (
    private[migration] val actions: Chunk[MigrationAction]
  ) {

    def addField(fieldName: String, default: DynamicValue): Nested =
      new Nested(actions :+ MigrationAction.AddField(DynamicOptic.root.field(fieldName), default))

    def dropField(fieldName: String, defaultForReverse: DynamicValue = DynamicValue.Null): Nested =
      new Nested(actions :+ MigrationAction.DropField(DynamicOptic.root.field(fieldName), defaultForReverse))

    def renameField(from: String, to: String): Nested =
      new Nested(actions :+ MigrationAction.Rename(DynamicOptic.root.field(from), to))

    def mandate(fieldName: String, default: DynamicValue): Nested =
      new Nested(actions :+ MigrationAction.Mandate(DynamicOptic.root.field(fieldName), default))

    def optionalize(fieldName: String): Nested =
      new Nested(actions :+ MigrationAction.Optionalize(DynamicOptic.root.field(fieldName)))

    def transformCase(caseName: String)(f: Nested => Nested): Nested = {
      val inner     = f(Nested.empty)
      val caseOptic = DynamicOptic.root.caseOf(caseName)
      new Nested(actions :+ MigrationAction.TransformCase(caseOptic, inner.actions))
    }
  }

  object Nested {
    val empty: Nested = new Nested(Chunk.empty)
  }
}
