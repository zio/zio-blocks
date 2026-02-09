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
  var nestMigration: DynamicMigration     = uninitialized
  var unnestMigration: DynamicMigration   = uninitialized
  var nestableRecord: DynamicValue        = uninitialized
  var nestedForUnnest: DynamicValue       = uninitialized

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

    nestableRecord = DynamicValue.Record(
      Chunk(
        "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "street"  -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
        "city"    -> DynamicValue.Primitive(PrimitiveValue.String("Springfield")),
        "zip"     -> DynamicValue.Primitive(PrimitiveValue.String("62701")),
        "country" -> DynamicValue.Primitive(PrimitiveValue.String("US"))
      )
    )

    nestedForUnnest = DynamicValue.Record(
      Chunk(
        "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "address" -> DynamicValue.Record(
          Chunk(
            "street"  -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
            "city"    -> DynamicValue.Primitive(PrimitiveValue.String("Springfield")),
            "zip"     -> DynamicValue.Primitive(PrimitiveValue.String("62701")),
            "country" -> DynamicValue.Primitive(PrimitiveValue.String("US"))
          )
        )
      )
    )

    nestMigration = DynamicMigration.single(
      MigrationAction.Nest(DynamicOptic.root, "address", Vector("street", "city", "zip", "country"))
    )

    unnestMigration = DynamicMigration.single(
      MigrationAction.Unnest(DynamicOptic.root, "address", Vector("street", "city", "zip", "country"))
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

  @Benchmark
  def nestFields(): Either[SchemaError, DynamicValue] =
    nestMigration(nestableRecord)

  @Benchmark
  def unnestFields(): Either[SchemaError, DynamicValue] =
    unnestMigration(nestedForUnnest)
}
