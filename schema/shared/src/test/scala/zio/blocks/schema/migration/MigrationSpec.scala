package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.{DynamicValue, Schema}

object MigrationSpec extends ZIOSpecDefault {

  override def spec = suite("MigrationSpec")(
    test("Rename field in a simple case class") {
      // V1
      case class PersonV1(name: String, age: Int)
      implicit val schemaV1: Schema[PersonV1] = Schema.derived[PersonV1]

      // V2 (renamed 'name' to 'fullName')
      case class PersonV2(fullName: String, age: Int)
      implicit val schemaV2: Schema[PersonV2] = Schema.derived[PersonV2]

      val migration = Migration.renameField[PersonV1, PersonV2]("name", "fullName")

      val v1     = PersonV1("Alice", 30)
      val result = migration.migrate(v1)

      assertTrue(result == Right(PersonV2("Alice", 30)))
    },

    test("Add field with default value") {
      // V1
      case class UserV1(id: Int)
      implicit val schemaV1: Schema[UserV1] = Schema.derived[UserV1]

      // V2 (added 'active' boolean)
      case class UserV2(id: Int, active: Boolean)
      implicit val schemaV2: Schema[UserV2] = Schema.derived[UserV2]

      val migration = Migration.addField[UserV1, UserV2]("active", DynamicValue.boolean(true))

      val v1     = UserV1(1)
      val result = migration.migrate(v1)

      assertTrue(result == Right(UserV2(1, true)))
    },

    test("Composition: Rename and Add") {
      // V1
      case class ItemV1(id: Int, title: String)
      implicit val schemaV1: Schema[ItemV1] = Schema.derived[ItemV1]

      // V2 (renamed 'title' -> 'name', added 'price')
      case class ItemV2(id: Int, name: String, price: Double)
      implicit val schemaV2: Schema[ItemV2] = Schema.derived[ItemV2]

      val migration =
        Migration.renameField[ItemV1, ItemV1]("title", "name") >>>
          Migration.addField[ItemV1, ItemV2]("price", DynamicValue.double(9.99))

      // Note: In the typed API above, the intermediate type ItemV1 isn't quite right for the second step
      // because after rename, it doesn't match ItemV1 schema structurally.
      // This highlights a need for `DynamicMigration` composition at the unsafe level
      // or a way to represent intermediate schemas.
      // For this test, we accept that we are composing operations on DynamicValues
      // and checking the final decode.

      // Refined composition manually for test:
      val dynMig = DynamicMigration.RenameField("title", "name") +
        DynamicMigration.AddClassField("price", DynamicValue.double(9.99))

      val compositeMigration = Migration.manual[ItemV1, ItemV2](dynMig)

      val v1     = ItemV1(100, "Book")
      val result = compositeMigration.migrate(v1)

      assertTrue(result == Right(ItemV2(100, "Book", 9.99)))
    }
  )
}
