package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration._

object MigrationLawSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, yearsOld: Int, city: Option[String])
  case class PersonV3(fullName: String, yearsOld: Int, country: String)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
  implicit val personV3Schema: Schema[PersonV3] = Schema.derived[PersonV3]

  val migrationV1toV2: Migration[PersonV1, PersonV2] = Migration.newBuilder[PersonV1, PersonV2]
    .renameField(_.name, _.fullName)
    .renameField(_.age, _.yearsOld)
    .addField(_.city, SchemaExpr.Constant(DynamicValue.None))
    .build

  val migrationV2toV3: Migration[PersonV2, PersonV3] = Migration.newBuilder[PersonV2, PersonV3]
    .dropField(_.city, SchemaExpr.Constant(DynamicValue.Primitive("Unknown")))
    .addField(_.country, SchemaExpr.Constant(DynamicValue.Primitive("USA")))
    .build

  val migrationV1toV3: Migration[PersonV1, PersonV3] = migrationV1toV2.andThen(migrationV2toV3)

  def spec = suite("MigrationLawSpec")(
    test("identity law: m.reverse.reverse should be m") {
      val m = migrationV1toV2
      assertTrue(m.dynamicMigration.actions == m.reverse.reverse.dynamicMigration.actions)
    },

    test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = migrationV1toV2
      val m2 = migrationV2toV3
      val m3 = Migration.newBuilder[PersonV3, PersonV3]().build // Identity migration for type C

      val combined1 = (m1.andThen(m2)).andThen(m3)
      val combined2 = m1.andThen(m2.andThen(m3))

      assertTrue(combined1.dynamicMigration.actions == combined2.dynamicMigration.actions)
    },

    test("structural reverse: m.reverse.reverse should be structurally equivalent to m") {
      val m = migrationV1toV2
      val reversedTwice = m.reverse.reverse

      assertTrue(reversedTwice.sourceSchema == m.sourceSchema && reversedTwice.targetSchema == m.targetSchema)
      assertTrue(reversedTwice.dynamicMigration.actions == m.dynamicMigration.actions)
    },

    test("serialization roundtrip: DynamicMigration should survive serialization and deserialization") {
      import zio.json._

      val dynamicMigration = migrationV1toV2.dynamicMigration
      val jsonString = dynamicMigration.toJson
      val deserializedMigration = jsonString.fromJson[DynamicMigration]

      assertTrue(deserializedMigration == Right(dynamicMigration))
    }
  )
}
