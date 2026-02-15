package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSelectorSpec extends SchemaBaseSpec {

  case class PersonV1(firstName: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, nickname: Option[String])
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]
  }

  case class PersonWithAddress(name: String, address: Address)
  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSelectorSpec")(
    selectorToOpticSuite,
    selectorBuilderSuite
  )

  private val selectorToOpticSuite = suite("selectorToOptic")(
    test("simple field selector produces correct DynamicOptic") {
      val optic    = MigrationBuilderMacros.selectorToOptic[PersonV1, String](_.firstName)
      val expected = DynamicOptic.root.field("firstName")
      assertTrue(optic == expected)
    },
    test("nested field selector produces correct DynamicOptic") {
      val optic    = MigrationBuilderMacros.selectorToOptic[PersonWithAddress, String](_.address.street)
      val expected = DynamicOptic.root.field("address").field("street")
      assertTrue(optic == expected)
    }
  )

  private val selectorBuilderSuite = suite("selector-based builder methods")(
    test("renameField with selector") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .addFieldAt[Option[String]](DynamicOptic.root.field("nickname"), None)
      val migration = builder.buildPartial
      assertTrue(migration.dynamicMigration.actions.size == 2)
    },
    test("addField with selector") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .addFieldAt[Option[String]](DynamicOptic.root.field("nickname"), None)
      val migration = builder.buildPartial
      assertTrue(migration.dynamicMigration.actions.size == 2)
    },
    test("dryRun with selector-based builder") {
      val builder = MigrationBuilder[PersonV1, PersonV1]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .renameFieldAt(DynamicOptic.root.field("fullName"), "firstName")
      val result = builder.dryRun(PersonV1("Alice", 30))
      assertTrue(
        result.isRight,
        result.toOption.get == PersonV1("Alice", 30)
      )
    },
    test("selector extension renameField works") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.firstName, "fullName")
        .addFieldAt[Option[String]](DynamicOptic.root.field("nickname"), None)
      val migration = builder.buildPartial
      assertTrue(
        migration.dynamicMigration.actions.size == 2,
        migration.dynamicMigration.actions.head match {
          case MigrationAction.Rename(at, newName) =>
            at == DynamicOptic.root.field("firstName") && newName == "fullName"
          case _ => false
        }
      )
    },
    test("selector extension dropField works") {
      val builder = MigrationBuilder[PersonV1, PersonV1]
        .dropField(_.age)
      val migration = builder.buildPartial
      assertTrue(
        migration.dynamicMigration.actions.size == 1,
        migration.dynamicMigration.actions.head match {
          case MigrationAction.DropField(at, _) =>
            at == DynamicOptic.root.field("age")
          case _ => false
        }
      )
    },
    test("selector extension addField works") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .addField[Option[String]](_.nickname)(None)
      val migration = builder.buildPartial
      assertTrue(migration.dynamicMigration.actions.size == 2)
    }
  )
}
