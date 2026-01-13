package zio.blocks.schema.migration

import zio.Chunk
import zio.blocks.schema.{DynamicValue, OpticCheck, Schema, SchemaExpr}

/**
 * Migration-only additions around SchemaExpr.
 *
 * #519 requirement:
 * DefaultValue must be macro-captured with the *field schema*,
 * and evaluated via schema.defaultValue, producing a DynamicValue.
 */
object MigrationSchemaExpr {

  /**
   * User-facing marker (people can write MigrationSchemaExpr.default).
   * The macros should replace this marker with DefaultValueFromSchema(fieldSchema).
   */
  final case class DefaultValue[S, A]() extends SchemaExpr[S, A] {
    override def eval(input: S): Either[OpticCheck, Chunk[A]] =
      Left(new OpticCheck("DefaultValue marker: must be captured by migration macros"))

    override def evalDynamic(input: S): Either[OpticCheck, Chunk[DynamicValue]] =
      Left(new OpticCheck("DefaultValue marker: must be captured by migration macros"))
  }

  /**
   * Captured default value expression (PURE DATA, serializable) storing the field schema.
   * This is what gets embedded in MigrationAction for reversals and adds/mandates.
   */
  final case class DefaultValueFromSchema[S, A](schema: Schema[A]) extends SchemaExpr[S, A] {
    override def eval(input: S): Either[OpticCheck, Chunk[A]] =
      schema.defaultValue match {
        case Some(a) => Right(Chunk.single(a))
        case None    => Left(new OpticCheck(s"No defaultValue available for schema ${schema}"))
      }

    override def evalDynamic(input: S): Either[OpticCheck, Chunk[DynamicValue]] =
      schema.defaultValue match {
        case Some(a) =>
          // Per ZIO Schema dynamic docs: Schema#toDynamic converts a typed value to DynamicValue. :contentReference[oaicite:3]{index=3}
          schema.toDynamic(a) match {
            case Right(dv) => Right(Chunk.single(dv))
            case Left(err) => Left(new OpticCheck(s"Failed converting default to DynamicValue: $err"))
          }
        case None =>
          Left(new OpticCheck(s"No defaultValue available for schema ${schema}"))
      }
  }

  /** Convenience: literal. */
  def literal[S, A](value: A)(using sch: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr.Literal[S, A](value, sch)

  /** Convenience: marker. (Macros should capture it.) */
  def default[S, A]: SchemaExpr[S, A] =
    DefaultValue[S, A]()

  /** Used by macros: if expr is the marker, replace it with captured default. */
  def captureDefaultIfMarker[S, A](expr: SchemaExpr[S, A], fieldSchema: Schema[A]): SchemaExpr[S, A] =
    expr match {
      case _: DefaultValue[?, ?] =>
        DefaultValueFromSchema[S, A](fieldSchema)
      case other =>
        other
    }

  /** Used by macros directly. */
  def defaultFromSchema[S, A](fieldSchema: Schema[A]): SchemaExpr[S, A] =
    DefaultValueFromSchema[S, A](fieldSchema)
}
