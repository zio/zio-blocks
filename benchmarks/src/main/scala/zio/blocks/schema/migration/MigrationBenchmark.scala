package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.DynamicValue
import zio.blocks.BaseBenchmark
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class MigrationBenchmark extends BaseBenchmark {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, yearsOld: Int)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  var personV1: PersonV1 = _
  var migration: Migration[PersonV1, PersonV2] = _

  @Setup
  def setup(): Unit = {
    personV1 = PersonV1("John Doe", 30)
    migration = Migration.newBuilder[PersonV1, PersonV2]
      .renameField(_.name, _.fullName)
      .renameField(_.age, _.yearsOld)
      .build
  }

  @Benchmark
  def unusedOverhead(bh: Blackhole): Unit = {
    // This benchmark measures the overhead when migration system is not explicitly used.
    // We simply convert to DynamicValue and back, which is a baseline operation for ZIO Schema.
    val dynamicValue = personV1Schema.toDynamicValue(personV1)
    val _ = personV1Schema.fromDynamicValue(dynamicValue)
    bh.consume(dynamicValue)
  }

  @Benchmark
  def migrationApplication(bh: Blackhole): Unit = {
    // This benchmark measures the performance of applying a migration.
    val result = migration.apply(personV1)
    bh.consume(result)
  }

  @Benchmark
  def migrationSerialization(bh: Blackhole): Unit = {
    import zio.json._
    import zio.blocks.schema.migration.DynamicMigrationCodec._
    val jsonString = migration.dynamicMigration.toJson
    bh.consume(jsonString)
  }

  @Benchmark
  def migrationDeserialization(bh: Blackhole): Unit = {
    import zio.json._
    import zio.blocks.schema.migration.DynamicMigrationCodec._
    val jsonString = migration.dynamicMigration.toJson 
    val deserialized = jsonString.fromJson[DynamicMigration]
    bh.consume(deserialized)
  }

}
