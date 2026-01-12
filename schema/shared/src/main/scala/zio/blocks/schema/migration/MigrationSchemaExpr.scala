package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema, SchemaExpr}

/** Migration-only additions around the existing zio.blocks.schema.SchemaExpr.
  * Keeps migration code in the migration package and avoids changing core
  * SchemaExpr too much.
  */
object MigrationSchemaExpr {

  /** Marker meaning "use the schema default for the target field".
    *
    * We represent it as a SchemaExpr that *cannot be evaluated without a
    * schema*. The migration interpreter will special-case it.
    */
   final case class DefaultValue[S, A](schema: Schema[A]) extends SchemaExpr[S, A] {

    override def eval(input: S) =
      schema.defaultValue match {
        case Some(dv) => Right(Chunk(dv))
        case None =>
          Left(new zio.blocks.schema.OpticCheck("DefaultValue: schema has no defaultValue"))
      }

    override def evalDynamic(input: S) =
      schema.defaultValue match {
        case Some(dv) => Right(Chunk(dv))
        case None =>
          Left(new zio.blocks.schema.OpticCheck("DefaultValue: schema has no defaultValue"))
      }
  }


  /** Convenience: a literal with schema (already exists, but ergonomic). */
  def literal[S, A](value: A)(using sch: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr.Literal[S, A](value, sch)

  /** Convenience: get the default marker for a given A. */
  def default[S, A](using sch: Schema[A]): SchemaExpr[S, A] =
    DefaultValue[S, A](sch)
}
