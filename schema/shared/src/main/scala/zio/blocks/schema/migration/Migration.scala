package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/** Typed façade wrapping a PURE DynamicMigration (no closures) plus schemas.
  *
  * NOTE: With PR #614/#589, we store STRUCTURAL schemas (Schema#structural)
  * so migrations can be authored/applied without keeping old runtime case classes.
  */
final case class Migration[-A, +B] private (
    program: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
) { self =>

  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(self.program ++ that.program, self.sourceSchema, that.targetSchema)

  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /** Apply migration:
    *   A -> DynamicValue -> (DynamicMigration) -> DynamicValue -> B
    */
  def apply(value: A): Either[MigrationError, B] =
    for {
      dynB <- DynamicMigrationInterpreter(program, sourceSchema.toDynamicValue(value))
      outB <- targetSchema
        .fromDynamicValue(dynB)
        .left
        .map(err => MigrationError.InvalidOp("Decode", err.toString))
    } yield outB
}

object Migration {

  /** Explicit-schema constructor (single source of truth). */
  def fromProgram[A, B](
      program: DynamicMigration,
      sa: Schema[A],
      sb: Schema[B]
  ): Migration[A, B] =
    new Migration(program, sa.structural, sb.structural)

  /** Preferred constructor: summon schemas from givens and delegate. */
  def fromProgram[A, B](program: DynamicMigration)(using
      sa: Schema[A],
      sb: Schema[B]
  ): Migration[A, B] =
    fromProgram(program, sa, sb)

  def id[A](using s: Schema[A]): Migration[A, A] =
    fromProgram[A, A](DynamicMigration.Id)

  // Optional: if you don’t have Schema[Any], better to delete this entirely.
  // If you *do* have Schema[Any], prefer: val idAny = id[Any]
}

