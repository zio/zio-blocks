package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr._

object MigrationGenerator {

  val genName: Gen[Any, String] =
    Gen.int(1, 10).flatMap(n => Gen.stringN(n)(Gen.alphaNumericChar))

  val anyPrimitive: Gen[Any, DynamicValue] = Gen.oneOf(
    Gen
      .int(0, 20)
      .flatMap(n => Gen.stringN(n)(Gen.alphaNumericChar))
      .map(s => DynamicValue.Primitive(PrimitiveValue.String(s))),
    Gen.int.map(i => DynamicValue.Primitive(PrimitiveValue.Int(i))),
    Gen.boolean.map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(b)))
  )

  val anyRecord: Gen[Any, DynamicValue] =
    Gen
      .int(1, 10)
      .flatMap(n => Gen.vectorOfN(n)(genName.zip(anyPrimitive)))
      .map(fields => DynamicValue.Record(fields.toVector))

  val anyAction: Gen[Any, MigrationAction] = Gen.oneOf(
    genName.zip(genName).map { case (f, t) =>
      Rename(DynamicOptic.root.field(f), t)
    },
    genName.zip(anyPrimitive).map { case (n, v) =>
      AddField(DynamicOptic.root.field(n), Constant(v))
    }
  )

  val anyDynamicMigration: Gen[Any, DynamicMigration] =
    Gen
      .int(1, 5)
      .flatMap(n => Gen.vectorOfN(n)(anyAction))
      .map(v => DynamicMigration(v.toVector))
}
