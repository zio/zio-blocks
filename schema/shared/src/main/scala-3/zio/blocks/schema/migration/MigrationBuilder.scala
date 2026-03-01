package zio.blocks.schema.migration

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

  inline def addField(
    inline target: B => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B]('{ this }, 'target, 'default) }

  inline def dropField(
    inline source: A => Any,
    defaultForReverse: DynamicValue = DynamicValue.Null
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropFieldImpl[A, B]('{ this }, 'source, 'defaultForReverse) }

  inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('{ this }, 'from, 'to) }

  inline def transformField(
    inline from: A => Any,
    inline to: B => Any,
    transform: DynamicValue
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformFieldImpl[A, B]('{ this }, 'from, 'to, 'transform) }

  inline def mandateField(
    inline source: A => Any,
    default: DynamicValue
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.mandateFieldImpl[A, B]('{ this }, 'source, 'default) }

  inline def optionalizeField(
    inline source: A => Any
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B]('{ this }, 'source) }

  inline def changeFieldType(
    inline source: A => Any,
    converter: DynamicValue
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.changeFieldTypeImpl[A, B]('{ this }, 'source, 'converter) }

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
    new MigrationBuilder(
      actions :+ MigrationAction.TransformElements(at, transform),
      sourceSchema,
      targetSchema
    )

  def transformKeys(at: DynamicOptic, transform: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(
      actions :+ MigrationAction.TransformKeys(at, transform),
      sourceSchema,
      targetSchema
    )

  def transformValues(at: DynamicOptic, transform: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(
      actions :+ MigrationAction.TransformValues(at, transform),
      sourceSchema,
      targetSchema
    )

  // ── Build ─────────────────────────────────────────────────────────

  /**
   * Build migration with runtime validation. When a default or example value is
   * available on the source schema, the migration is applied to it and the
   * result is checked against the target schema.
   */
  def build: Migration[A, B] = {
    val dm        = new DynamicMigration(actions)
    val sourceDyn = sourceSchema.toDynamicSchema
    val targetDyn = targetSchema.toDynamicSchema
    val sample    = sourceDyn.getDefaultValue.orElse(sourceDyn.examples.headOption)
    sample.foreach { sourceSample =>
      dm(sourceSample) match {
        case Left(err) =>
          throw new IllegalArgumentException(s"Migration validation failed: ${err.getMessage}")
        case Right(result) =>
          targetDyn.check(result).foreach { err =>
            throw new IllegalArgumentException(
              s"Migration validation failed: result does not conform to target schema: ${err.message}"
            )
          }
      }
    }
    new Migration(dm, sourceSchema, targetSchema)
  }

  /** Build migration without validation. */
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
