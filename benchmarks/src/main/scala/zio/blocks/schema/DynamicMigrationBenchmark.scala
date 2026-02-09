package zio.blocks.schema

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.chunk.Chunk
import zio.blocks.schema.migration._

import scala.compiletime.uninitialized

class DynamicMigrationBenchmark extends BaseBenchmark {

  var simpleRecord: DynamicValue          = uninitialized
  var nestedRecord: DynamicValue          = uninitialized
  var sequenceValue: DynamicValue         = uninitialized
  var addFieldMigration: DynamicMigration = uninitialized
  var renameMigration: DynamicMigration   = uninitialized
  var composedMigration: DynamicMigration = uninitialized
  var nestedMigration: DynamicMigration   = uninitialized
  var sequenceMigration: DynamicMigration = uninitialized

  @Setup
  def setup(): Unit = {
    simpleRecord = DynamicValue.Record(
      Chunk(
        "name"  -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "email" -> DynamicValue.Primitive(PrimitiveValue.String("john@example.com"))
      )
    )

    nestedRecord = DynamicValue.Record(
      Chunk(
        "user" -> DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        ),
        "active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
      )
    )

    sequenceValue = DynamicValue.Sequence(
      Chunk.fromIterable((1 to 100).map { i =>
        DynamicValue.Record(
          Chunk(
            "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(i)),
            "name" -> DynamicValue.Primitive(PrimitiveValue.String(s"item$i"))
          )
        )
      })
    )

    addFieldMigration = DynamicMigration.single(
      MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        DynamicValue.Primitive(PrimitiveValue.Int(0))
      )
    )

    renameMigration = DynamicMigration.single(
      MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
    )

    composedMigration = addFieldMigration ++ renameMigration

    nestedMigration = DynamicMigration.single(
      MigrationAction.AddField(
        DynamicOptic.root.field("user").field("role"),
        DynamicValue.Primitive(PrimitiveValue.String("user"))
      )
    )

    sequenceMigration = DynamicMigration.single(
      MigrationAction.TransformElements(
        DynamicOptic.root,
        Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("active"),
            DynamicValue.Primitive(PrimitiveValue.Boolean(true))
          )
        )
      )
    )
  }

  @Benchmark
  def addField(): Either[SchemaError, DynamicValue] =
    addFieldMigration(simpleRecord)

  @Benchmark
  def renameField(): Either[SchemaError, DynamicValue] =
    renameMigration(simpleRecord)

  @Benchmark
  def composedMigrationApply(): Either[SchemaError, DynamicValue] =
    composedMigration(simpleRecord)

  @Benchmark
  def nestedFieldMigration(): Either[SchemaError, DynamicValue] =
    nestedMigration(nestedRecord)

  @Benchmark
  def sequenceTransform(): Either[SchemaError, DynamicValue] =
    sequenceMigration(sequenceValue)

  @Benchmark
  def reverseMigration(): DynamicMigration =
    composedMigration.reverse
}
