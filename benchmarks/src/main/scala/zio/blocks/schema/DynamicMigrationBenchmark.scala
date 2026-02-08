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

  var renameMigration: Migration[PersonV1, PersonV2] = null.asInstanceOf[Migration[PersonV1, PersonV2]]
  var addFieldMigration: Migration[UserV1, UserV2]   = null.asInstanceOf[Migration[UserV1, UserV2]]

  // var macroRenameMigration: Migration[PersonV1, PersonV2] = null.asInstanceOf[Migration[PersonV1, PersonV2]]
  // var macroAddFieldMigration: Migration[UserV1, UserV2]  = null.asInstanceOf[Migration[UserV1, UserV2]]

  @Setup
  def setup(): Unit = {
    personV1 = PersonV1("Alice", 30)
    userV1 = UserV1(1)

    // Interpreted
    renameMigration = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
    addFieldMigration = Migration.addField[UserV1, UserV2]("active", DynamicValue.boolean(true))

    // Macro Derived (Disabled for CI cross-build compatibility)
    /*
    import zio.blocks.schema.migration.macros.MacroMigration

    macroRenameMigration = MacroMigration.derive[PersonV1, PersonV2](DynamicMigration.RenameField("name", "fullName"))
    macroAddFieldMigration = MacroMigration.derive[UserV1, UserV2](DynamicMigration.AddClassField("active", DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true))))
     */
  }

  @Benchmark
  def manualRename(): PersonV2 =
    PersonV2(personV1.name, personV1.age)

  @Benchmark
  def interpretedRename(): Either[String, PersonV2] =
    renameMigration.migrate(personV1)

  /*
  @Benchmark
  def macroRename(): Either[String, PersonV2] =
    macroRenameMigration.migrate(personV1)
   */

  @Benchmark
  def manualAddField(): UserV2 =
    UserV2(userV1.id, true)

  @Benchmark
  def interpretedAddField(): Either[String, UserV2] =
    addFieldMigration.migrate(userV1)

  /*
  @Benchmark
  def macroAddField(): Either[String, UserV2] =
    macroAddFieldMigration.migrate(userV1)
   */
}
