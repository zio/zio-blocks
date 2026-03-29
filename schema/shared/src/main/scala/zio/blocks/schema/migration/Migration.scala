package zio.blocks.schema.migration

import zio.blocks.schema.{ DynamicValue, Schema, SchemaExpr }

// ─────────────────────────────────────────────────────────────────────────────
//  Migration[A, B] — typed user-facing API
//  Wraps DynamicMigration with compile-time type information.
//  Not pure data (schemas contain bindings), but introspectable.
// ─────────────────────────────────────────────────────────────────────────────

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],  // structural schemas — used for type info only
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type A, producing either a
   * MigrationError or a value of type B.
   */
  def apply(value: A): Either[MigrationError, B] =
    for {
      dyn     <- sourceSchema.toDynamicValue(value)
                   .toRight(MigrationError.TransformFailed(
                     zio.blocks.schema.DynamicOptic.identity,
                     s"Failed to convert $value to DynamicValue"
                   ))
      migrated <- dynamicMigration(dyn)
      result   <- targetSchema.fromDynamicValue(migrated)
                   .left.map(err =>
                     MigrationError.TransformFailed(
                       zio.blocks.schema.DynamicOptic.identity,
                       s"Failed to reconstruct target type: $err"
                     )
                   )
    } yield result

  /**
   * Compose this migration with another sequentially.
   * (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)  (associativity)
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Structural reverse of this migration.
   * m.reverse.reverse == m (structurally)
   * Note: runtime reverse is best-effort for non-invertible transforms.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
}

object Migration {

  /**
   * The identity migration.
   * Migration.identity[A].apply(a) == Right(a)
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Entry point to build a typed migration using the fluent MigrationBuilder API.
   * Selectors are macro-validated at compile time.
   *
   * Usage:
   * {{{
   *   Migration.migrate[PersonV1, PersonV2]
   *     .renameField(_.name, _.fullName)
   *     .addField(_.country, SchemaExpr.const("RO"))
   *     .build
   * }}}
   */
  def migrate[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
