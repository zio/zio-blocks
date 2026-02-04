package zio.blocks.schema.migration

import zio.blocks.schema._

final class Migration[A, B] private (
  val program: DynamicMigration,
  val tsa: ToStructural[A],
  val tsb: ToStructural[B],
  private val sourceSchemaAny: Schema[_],
  private val targetSchemaAny: Schema[_]
) { self =>

  // Re-type the schemas using the constructor params tsa/tsb
  private val sourceSchema: Schema[tsa.StructuralType] =
    sourceSchemaAny.asInstanceOf[Schema[tsa.StructuralType]]

  private val targetSchema: Schema[tsb.StructuralType] =
    targetSchemaAny.asInstanceOf[Schema[tsb.StructuralType]]

  def apply(value: A)(implicit
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
    Migration.fromProgram[B, A](program.reverse)(
      targetSchemaAny.asInstanceOf[Schema[B]],
      sourceSchemaAny.asInstanceOf[Schema[A]],
      tsb.asInstanceOf[ToStructural[B]],
      tsa.asInstanceOf[ToStructural[A]]
    )
}

object Migration {

  def fromProgram[A, B](program: DynamicMigration)(implicit
    sa: Schema[A],
    sb: Schema[B],
    tsa: ToStructural[A],
    tsb: ToStructural[B]
  ): Migration[A, B] =
    new Migration[A, B](
      program,
      tsa,
      tsb,
      sa.structural,
      sb.structural
    )

  def id[A](implicit
    s: Schema[A],
    ts: ToStructural[A]
  ): Migration[A, A] =
    fromProgram[A, A](DynamicMigration.id)
}
