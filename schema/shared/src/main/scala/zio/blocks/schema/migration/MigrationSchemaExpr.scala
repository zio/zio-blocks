package zio.blocks.schema.migration

import zio.Chunk
import zio.blocks.schema.{DynamicValue, OpticCheck, Schema, SchemaExpr}

/**
 * Migration-only additions around SchemaExpr.
 *
 * IMPORTANT: This file must NOT capture Schema inside DynamicMigration programs.
 * DefaultValue is a *pure marker* that the interpreter resolves using the target schema.
 */
object MigrationSchemaExpr {

  /**
   * Marker meaning: "use the schema default for the target field".
   *
   * - Pure data (no Schema captured!)
   * - Interpreter must special-case it.
   */
  final case class DefaultValue[S, A]() extends SchemaExpr[S, A] {
    override def eval(input: S): Either[OpticCheck, Chunk[A]] =
      Left(new OpticCheck("DefaultValue marker: must be resolved by migration interpreter"))

    override def evalDynamic(input: S): Either[OpticCheck, Chunk[DynamicValue]] =
      Left(new OpticCheck("DefaultValue marker: must be resolved by migration interpreter"))
  }

  /** Convenience: literal (you already use this style elsewhere). */
  def literal[S, A](value: A)(using sch: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr.Literal[S, A](value, sch)

  /** Convenience: default marker */
  def default[S, A]: SchemaExpr[S, A] =
    DefaultValue[S, A]()
}
