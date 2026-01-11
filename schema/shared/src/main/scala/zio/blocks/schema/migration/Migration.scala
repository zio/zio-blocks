package zio.blocks.schema.migration

import zio.blocks.schema.{As, Schema, ToStructural}

final class Migration[A, B] private (
  val program: DynamicMigration,
  val tsa: ToStructural[A],
  val tsb: ToStructural[B],
  val sourceSchema: Schema[tsa.StructuralType],
  val targetSchema: Schema[tsb.StructuralType]
) { self =>

  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(
      self.program ++ that.program,
      self.tsa,
      that.tsb,
      self.sourceSchema,
      that.targetSchema
    )

  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  def apply(value: A)(using
    asA: As[A, tsa.StructuralType],
    asB: As[B, tsb.StructuralType]
  ): Either[MigrationError, B] =
    for {
      aStruct <- asA.into(value).left.map(e => MigrationError.InvalidOp("IntoStructural", e.toString))
      dynB    <- DynamicMigrationInterpreter(program, sourceSchema.toDynamicValue(aStruct))
      bStruct <- targetSchema
                   .fromDynamicValue(dynB)
                   .left
                   .map(err => MigrationError.InvalidOp("DecodeStructural", err.toString))
      outB <- asB.from(bStruct).left.map(e => MigrationError.InvalidOp("FromStructural", e.toString))
    } yield outB
}

object Migration {

  def fromProgram[A, B](program: DynamicMigration)(using
    sa: Schema[A],
    sb: Schema[B],
    tsa: ToStructural[A],
    tsb: ToStructural[B]
  ): Migration[A, B] =
    new Migration[A, B](program, tsa, tsb, sa.structural, sb.structural)

  def id[A](using s: Schema[A], ts: ToStructural[A]): Migration[A, A] =
    fromProgram[A, A](DynamicMigration.Id)
}
