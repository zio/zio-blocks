package zio.blocks.schema.benchmarks

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema._
import zio.blocks.schema.migration._

@State(Scope.Thread)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xms1g",
    "-Xmx1g"
  )
)
class DynamicMigrationBenchmark extends BaseBenchmark {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  case class UserV1(id: Int)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
  }

  case class UserV2(id: Int, active: Boolean)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived[UserV2]
  }

  var personV1: PersonV1 = null.asInstanceOf[PersonV1]
  var userV1: UserV1     = null.asInstanceOf[UserV1]

  var renameMigration: Migration[PersonV1, PersonV2]  = null.asInstanceOf[Migration[PersonV1, PersonV2]]
  var addFieldMigration: Migration[UserV1, UserV2]    = null.asInstanceOf[Migration[UserV1, UserV2]]
  var builderMigration: Migration[PersonV1, PersonV2] = null.asInstanceOf[Migration[PersonV1, PersonV2]]

  @Setup
  def setup(): Unit = {
    personV1 = PersonV1("Alice", 30)
    userV1 = UserV1(1)

    // Using convenience constructors
    renameMigration = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
    addFieldMigration = Migration.addField[UserV1, UserV2]("active", DynamicValue.boolean(true))

    // Using builder API
    builderMigration = Migration
      .newBuilder[PersonV1, PersonV2]
      .renameField("name", "fullName")
      .build
  }

  @Benchmark
  def manualRename(): PersonV2 =
    PersonV2(personV1.name, personV1.age)

  @Benchmark
  def interpretedRename(): Either[MigrationError, PersonV2] =
    renameMigration.migrate(personV1)

  @Benchmark
  def builderRename(): Either[MigrationError, PersonV2] =
    builderMigration.migrate(personV1)

  @Benchmark
  def manualAddField(): UserV2 =
    UserV2(userV1.id, true)

  @Benchmark
  def interpretedAddField(): Either[MigrationError, UserV2] =
    addFieldMigration.migrate(userV1)

  @Benchmark
  def dynamicMigrationRename(): Either[MigrationError, DynamicValue] = {
    val dynV1 = PersonV1.schema.toDynamicValue(personV1)
    DynamicMigration.renameField("name", "fullName").migrate(dynV1)
  }

  // ──────────────── Additional Benchmarks ────────────────

  var composedMigration: Migration[PersonV1, PersonV1] = null.asInstanceOf[Migration[PersonV1, PersonV1]]
  var nestMigration: DynamicMigration                  = null.asInstanceOf[DynamicMigration]

  @Setup
  def setupExtra(): Unit = {
    composedMigration = Migration
      .newBuilder[PersonV1, PersonV1]
      .renameField("name", "fullName")
      .addField("email", DynamicValue.string("default@example.com"))
      .dropField("age")
      .buildPartial

    nestMigration = DynamicMigration.nest(Vector("name", "age"), "info")
  }

  @Benchmark
  def composedThreeStep(): Either[MigrationError, PersonV1] =
    composedMigration.migrate(personV1)

  @Benchmark
  def reverseMigration(): Either[MigrationError, PersonV1] = {
    val forward = renameMigration.migrate(personV1).toOption.get
    renameMigration.reverse.migrate(forward)
  }

  @Benchmark
  def nestUnnestRoundtrip(): Either[MigrationError, DynamicValue] = {
    val dynV1    = PersonV1.schema.toDynamicValue(personV1)
    val nested   = nestMigration.migrate(dynV1)
    val unnested = nestMigration.reverse.migrate(nested.toOption.get)
    unnested
  }

  @Benchmark
  def dynamicMigrationAddField(): Either[MigrationError, DynamicValue] = {
    val dynV1 = UserV1.schema.toDynamicValue(userV1)
    DynamicMigration.addField("active", DynamicValue.boolean(true)).migrate(dynV1)
  }
}
