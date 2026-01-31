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
      aStruct <- asA
                   .into(value)
                   .left
                   .map(e => MigrationError.InvalidOp("IntoStructural", e.toString))

      dynA = sourceSchema.toDynamicValue(aStruct)

      dynB <- DynamicMigrationInterpreter(program, dynA)

      bStruct <- targetSchema
                   .fromDynamicValue(dynB)
                   .left
                   .map(err => MigrationError.InvalidOp("DecodeStructural", err.toString))

      outB <- asB
                .from(bStruct)
                .left
                .map(e => MigrationError.InvalidOp("FromStructural", e.toString))
    } yield outB

  def reverse: Migration[B, A] =
    Migration.fromProgram[B, A](program.reverse)(using
      targetSchema.asInstanceOf[Schema[B]],
      sourceSchema.asInstanceOf[Schema[A]],
      tsb.asInstanceOf[ToStructural[B]],
      tsa.asInstanceOf[ToStructural[A]]
    )
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
    fromProgram[A, A](DynamicMigration.id)
}
