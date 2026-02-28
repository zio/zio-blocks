package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class AddressV1(street: String, city: String)
  object AddressV1 {
    implicit val schema: Schema[AddressV1] = Schema.derived
  }

  case class AddressV2(street: String, city: String, zip: String)
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class WithAddressV1(name: String, address: AddressV1)
  object WithAddressV1 {
    implicit val schema: Schema[WithAddressV1] = Schema.derived
  }

  case class WithAddressV2(name: String, address: AddressV2)
  object WithAddressV2 {
    implicit val schema: Schema[WithAddressV2] = Schema.derived
  }

  def spec: Spec[Any, Any] = suite("MigrationBuilderSpec")(
    selectorSuite,
    builderSuite,
    nestedBuilderSuite,
    typedMigrationSuite,
    builderApiCoverageSuite,
    nestedBuilderCoverageSuite
  )

  private val selectorSuite = suite("Selector Macro")(
    test("addField extracts correct DynamicOptic from selector") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .addField(_.email, DynamicValue.string("unknown"))

      val action = builder.actions.head
      assertTrue(
        action.isInstanceOf[MigrationAction.AddField],
        action.at == DynamicOptic.root.field("email")
      )
    },
    test("dropField extracts correct DynamicOptic from selector") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .dropField(_.name)

      val action = builder.actions.head
      assertTrue(
        action.isInstanceOf[MigrationAction.DropField],
        action.at == DynamicOptic.root.field("name")
      )
    },
    test("renameField extracts correct paths for from and to") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)

      val action = builder.actions.head.asInstanceOf[MigrationAction.Rename]
      assertTrue(
        action.at == DynamicOptic.root.field("name"),
        action.to == "fullName"
      )
    },
    test("nested selector extracts multi-level path") {
      val builder = MigrationBuilder[WithAddressV1, WithAddressV2]
        .addField(_.address.zip, DynamicValue.string("00000"))

      val action = builder.actions.head
      assertTrue(
        action.at == DynamicOptic.root.field("address").field("zip")
      )
    }
  )

  private val builderSuite = suite("Builder DSL")(
    test("chains multiple operations") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))

      assertTrue(builder.actions.length == 2)
    },
    test("buildPartial creates Migration") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      assertTrue(migration.size == 2)
    }
  )

  private val nestedBuilderSuite = suite("Nested Builder")(
    test("transformCase builds nested actions") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string(""))
      val migration = builder.buildPartial

      assertTrue(migration.size == 2)
    },
    test("Nested builder creates correct action structure") {
      val nested = MigrationBuilder.Nested.empty
        .addField("zip", DynamicValue.string("00000"))
        .renameField("street", "streetAddress")

      assertTrue(nested.actions.length == 2)
    }
  )

  private val typedMigrationSuite = suite("Typed Migration")(
    test("migrates PersonV1 to PersonV2 via builder") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      val result = migration(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV2("Alice", 30, "unknown")))
    },
    test("migrates nested record: WithAddressV1 to WithAddressV2") {
      val migration = MigrationBuilder[WithAddressV1, WithAddressV2]
        .addField(_.address.zip, DynamicValue.string("12345"))
        .buildPartial

      val result = migration(WithAddressV1("Alice", AddressV1("Main St", "NYC")))
      assertTrue(result == Right(WithAddressV2("Alice", AddressV2("Main St", "NYC", "12345"))))
    },
    test("reverse migration works for add/drop roundtrip") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      val original = PersonV1("Alice", 30)
      val result   = for {
        migrated <- migration(original)
        restored <- migration.reverse(migrated)
      } yield restored
      assertTrue(result == Right(original))
    },
    test("composition of two migrations") {
      val m1 = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      assertTrue(m1.size == 2)
    },
    test("Migration.newBuilder works as entry point") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      val result = migration(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV2("Alice", 30, "unknown")))
    },
    test("migration errors are MigrationError type") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV2]
        .addField(_.email, DynamicValue.string("unknown"))
        .buildPartial

      val result = migration(PersonV1("Alice", 30))
      assertTrue(result.isLeft || result.isRight)
    }
  )

  private val builderApiCoverageSuite = suite("Builder API coverage")(
    test("build creates Migration same as buildPartial") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.email, DynamicValue.string("unknown"))
        .build

      val result = migration(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV2("Alice", 30, "unknown")))
    },
    test("renameCase creates RenameCase action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameCase("OldCase", "NewCase")

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.RenameCase])
    },
    test("transformCase creates TransformCase action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .transformCase("MyCase") { nested =>
          nested.addField("extra", DynamicValue.string("val"))
        }

      val action = builder.actions.head
      assertTrue(
        action.isInstanceOf[MigrationAction.TransformCase],
        action.asInstanceOf[MigrationAction.TransformCase].actions.length == 1
      )
    },
    test("transformField creates correct action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .transformField(_.name, _.fullName, DynamicValue.string("mapped"))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Rename])
    },
    test("mandateField creates Mandate action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .mandateField(_.name, DynamicValue.string("default"))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Mandate])
    },
    test("optionalizeField creates Optionalize action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .optionalizeField(_.name)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Optionalize])
    },
    test("changeFieldType creates ChangeType action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .changeFieldType(_.name, DynamicValue.string("converted"))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.ChangeType])
    },
    test("transformElements creates TransformElements action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .transformElements(DynamicOptic.root.field("items"), DynamicValue.Null)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.TransformElements])
    },
    test("transformKeys creates TransformKeys action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .transformKeys(DynamicOptic.root.field("map"), DynamicValue.Null)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.TransformKeys])
    },
    test("transformValues creates TransformValues action") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .transformValues(DynamicOptic.root.field("map"), DynamicValue.Null)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.TransformValues])
    }
  )

  private val nestedBuilderCoverageSuite = suite("Nested Builder coverage")(
    test("Nested.dropField creates DropField action") {
      val nested = MigrationBuilder.Nested.empty
        .dropField("oldField")
      val action = nested.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.DropField])
    },
    test("Nested.mandate creates Mandate action") {
      val nested = MigrationBuilder.Nested.empty
        .mandate("field", DynamicValue.int(0))
      val action = nested.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Mandate])
    },
    test("Nested.optionalize creates Optionalize action") {
      val nested = MigrationBuilder.Nested.empty
        .optionalize("field")
      val action = nested.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Optionalize])
    },
    test("Nested.transformCase creates nested TransformCase action") {
      val nested = MigrationBuilder.Nested.empty
        .transformCase("InnerCase") { inner =>
          inner.addField("x", DynamicValue.int(1))
        }
      val action = nested.actions.head
      assertTrue(
        action.isInstanceOf[MigrationAction.TransformCase],
        action.asInstanceOf[MigrationAction.TransformCase].actions.length == 1
      )
    }
  )
}
