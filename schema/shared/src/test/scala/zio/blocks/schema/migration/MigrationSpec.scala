package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object MigrationSpec extends ZIOSpecDefault {

  private def dynamicLiteral[A: Schema](value: A): DynamicSchemaExpr =
    DynamicSchemaExpr.Literal(Schema[A].toDynamicValue(value))

  private def literal[A: Schema](value: A): SchemaExpr[Any, A] =
    SchemaExpr.literal(value)

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Selector Syntax")(
      test("simple field selector") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(0))
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },

      test("renameField selector syntax") {
        case class PersonV1(firstName: String)
        case class PersonV2(fullName: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("dropField selector syntax") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(0))
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("transformField selector syntax") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Long)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.age, _.age, literal(30L))
          .build

        val input  = PersonV1(30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      },

      test("changeFieldType selector syntax") {
        case class PersonV1(score: Int)
        case class PersonV2(score: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .changeFieldType(_.score, _.score, literal("42"))
          .build

        val input  = PersonV1(42)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("42")))
      },

      test("mandateField builder creates correct action") {
        case class PersonV1(age: Option[Int])
        case class PersonV2(age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .mandateField(_.age, _.age, literal(0))
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.Mandate])
      },

      test("optionalizeField builder creates correct action") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Option[Int])

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .optionalizeField(_.age, _.age)
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.Optionalize])
      },

      test("transformElements builder creates correct action") {
        case class Container(items: List[Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformElements(_.items, literal(0))
          .buildPartial

        assertTrue(
          migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformElements]
        )
      },

      test("transformKeys builder creates correct action") {
        case class Container(data: Map[String, Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformKeys(_.data, literal("key"))
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformKeys])
      },

      test("transformValues builder creates correct action") {
        case class Container(data: Map[String, Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformValues(_.data, literal(0))
          .buildPartial

        assertTrue(
          migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformValues]
        )
      },

      test("renameCase builder creates correct action") {
        sealed trait Status
        case object Active  extends Status
        case object Pending extends Status

        implicit val schema: Schema[Status] = Schema.derived[Status]

        val migration = Migration
          .newBuilder[Status, Status]
          .renameCase("Active", "Enabled")
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.RenameCase])
      }
    ),

    suite("Migration Companion Methods")(
      test("Migration.identity creates empty migration") {
        case class Person(name: String)
        implicit val schema: Schema[Person] = Schema.derived[Person]

        val migration = Migration.identity[Person]
        assertTrue(migration.isEmpty && migration.size == 0)
      },

      test("Migration.fromAction creates single-action migration") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          dynamicLiteral(0)
        )
        val migration = Migration.fromAction[PersonV1, PersonV2](action)
        assertTrue(migration.size == 1)
      },

      test("Migration.fromDynamic wraps DynamicMigration") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val dynamicMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            dynamicLiteral(0)
          )
        )
        val migration = Migration.fromDynamic[PersonV1, PersonV2](dynamicMigration)
        assertTrue(migration.size == 1 && migration.dynamicMigration == dynamicMigration)
      },

      test("Migration.++ composes migrations") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)
        case class PersonV3(name: String, age: Int, city: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        implicit val v3Schema: Schema[PersonV3] = Schema.derived[PersonV3]

        val m1 = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(0))
          .build
        val m2 = Migration
          .newBuilder[PersonV2, PersonV3]
          .addField(_.city, literal(""))
          .build
        val composed = m1 ++ m2
        assertTrue(composed.size == 2)
      },

      test("Migration.reverse swaps schemas") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(0))
          .build
        val reversed = migration.reverse
        assertTrue(reversed.sourceSchema == v2Schema && reversed.targetSchema == v1Schema)
      }
    ),

    suite("Migration Composition with migrateField")(
      test("migrateField with explicit migration") {
        case class User(name: String, age: Int)
        case class UserV2(name: String, age: Int, email: String)
        case class Profile(id: Int, user: User)
        case class ProfileV2(id: Int, user: UserV2)

        implicit val userSchema: Schema[User]           = Schema.derived[User]
        implicit val userV2Schema: Schema[UserV2]       = Schema.derived[UserV2]
        implicit val profileSchema: Schema[Profile]     = Schema.derived[Profile]
        implicit val profileV2Schema: Schema[ProfileV2] = Schema.derived[ProfileV2]

        val userMigration: Migration[User, UserV2] = Migration
          .newBuilder[User, UserV2]
          .addField(_.email, literal("default@example.com"))
          .build

        val profileMigration = Migration
          .newBuilder[Profile, ProfileV2]
          .migrateField(_.user, userMigration)
          .build

        val input  = Profile(1, User("Alice", 30))
        val result = profileMigration(input)

        assertTrue(result == Right(ProfileV2(1, UserV2("Alice", 30, "default@example.com"))))
      },

      test("migrateField with implicit migration") {
        case class Address(street: String, city: String)
        case class AddressV2(street: String, city: String, zip: String)
        case class Company(name: String, address: Address)
        case class CompanyV2(name: String, address: AddressV2)

        implicit val addressSchema: Schema[Address]     = Schema.derived[Address]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val companySchema: Schema[Company]     = Schema.derived[Company]
        implicit val companyV2Schema: Schema[CompanyV2] = Schema.derived[CompanyV2]

        implicit val addressMigration: Migration[Address, AddressV2] = Migration
          .newBuilder[Address, AddressV2]
          .addField(_.zip, literal("00000"))
          .build

        val companyMigration = Migration
          .newBuilder[Company, CompanyV2]
          .migrateField(_.address)
          .build

        val input  = Company("Acme", Address("123 Main St", "Springfield"))
        val result = companyMigration(input)

        assertTrue(result == Right(CompanyV2("Acme", AddressV2("123 Main St", "Springfield", "00000"))))
      },

      test("migrateField tracks nested fields for validation") {
        case class Inner(a: Int, b: String)
        case class InnerV2(a: Int, b: String, c: Boolean)
        case class Outer(x: String, inner: Inner)
        case class OuterV2(x: String, inner: InnerV2)

        implicit val innerSchema: Schema[Inner]     = Schema.derived[Inner]
        implicit val innerV2Schema: Schema[InnerV2] = Schema.derived[InnerV2]
        implicit val outerSchema: Schema[Outer]     = Schema.derived[Outer]
        implicit val outerV2Schema: Schema[OuterV2] = Schema.derived[OuterV2]

        val innerMigration: Migration[Inner, InnerV2] = Migration
          .newBuilder[Inner, InnerV2]
          .addField(_.c, literal(false))
          .build

        val outerMigration = Migration
          .newBuilder[Outer, OuterV2]
          .migrateField(_.inner, innerMigration)
          .build

        val input  = Outer("test", Inner(42, "hello"))
        val result = outerMigration(input)

        assertTrue(result == Right(OuterV2("test", InnerV2(42, "hello", false))))
      },

      test("ApplyMigration action reverse works correctly") {
        val innerMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("newField"),
            dynamicLiteral(42)
          )
        )
        val action   = MigrationAction.ApplyMigration(DynamicOptic.root.field("nested"), innerMigration)
        val reversed = action.reverse

        assertTrue(
          reversed.isInstanceOf[MigrationAction.ApplyMigration] &&
            reversed
              .asInstanceOf[MigrationAction.ApplyMigration]
              .migration
              .actions
              .head
              .isInstanceOf[MigrationAction.DropField]
        )
      },

      test("ApplyMigration executes nested migration at path") {
        val input = DynamicValue.Record(
          "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "data" -> DynamicValue.Record(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
          )
        )

        val nestedMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            dynamicLiteral(25)
          )
        )

        val migration = DynamicMigration.single(
          MigrationAction.ApplyMigration(DynamicOptic.root.field("data"), nestedMigration)
        )

        val expected = DynamicValue.Record(
          "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "data" -> DynamicValue.Record(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )

        assertTrue(migration(input) == Right(expected))
      },

      test("migrateField combined with other operations") {
        case class Settings(theme: String)
        case class SettingsV2(theme: String, fontSize: Int)
        case class Config(name: String, settings: Settings)
        case class ConfigV2(title: String, settings: SettingsV2)

        implicit val settingsSchema: Schema[Settings]     = Schema.derived[Settings]
        implicit val settingsV2Schema: Schema[SettingsV2] = Schema.derived[SettingsV2]
        implicit val configSchema: Schema[Config]         = Schema.derived[Config]
        implicit val configV2Schema: Schema[ConfigV2]     = Schema.derived[ConfigV2]

        val settingsMigration: Migration[Settings, SettingsV2] = Migration
          .newBuilder[Settings, SettingsV2]
          .addField(_.fontSize, literal(12))
          .build

        val configMigration = Migration
          .newBuilder[Config, ConfigV2]
          .renameField(_.name, _.title)
          .migrateField(_.settings, settingsMigration)
          .build

        val input  = Config("MyConfig", Settings("dark"))
        val result = configMigration(input)

        assertTrue(result == Right(ConfigV2("MyConfig", SettingsV2("dark", 12))))
      }
    ),

    suite("Nested Type Migrations")(
      test("Migration.fromActions API works for direct action construction") {
        case class AddressV1(street: String, city: String)
        case class PersonV1(name: String, address: AddressV1)

        case class AddressV2(street: String, city: String, zip: String)
        case class PersonV2(name: String, address: AddressV2)

        implicit val addressV1Schema: Schema[AddressV1] = Schema.derived[AddressV1]
        implicit val personV1Schema: Schema[PersonV1]   = Schema.derived[PersonV1]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("address").field("zip"),
            dynamicLiteral("00000")
          )
        )

        val input  = PersonV1("Alice", AddressV1("123 Main St", "NYC"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", AddressV2("123 Main St", "NYC", "00000"))))
      },

      test("2-level nested case class migration - rename nested field") {
        case class AddressV1(street: String, city: String)
        case class PersonV1(name: String, address: AddressV1)

        case class AddressV2(streetName: String, city: String)
        case class PersonV2(name: String, address: AddressV2)

        implicit val addressV1Schema: Schema[AddressV1] = Schema.derived[AddressV1]
        implicit val personV1Schema: Schema[PersonV1]   = Schema.derived[PersonV1]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]

        val addressMigration = Migration
          .newBuilder[AddressV1, AddressV2]
          .renameField(_.street, _.streetName)
          .build

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .migrateField(_.address, addressMigration)
          .build

        val input  = PersonV1("Bob", AddressV1("456 Oak Ave", "LA"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Bob", AddressV2("456 Oak Ave", "LA"))))
      },

      test("3-level nested case class migration - Company with Department with Employee") {
        case class EmployeeV1(name: String, salary: Int)
        case class DepartmentV1(name: String, employee: EmployeeV1)
        case class CompanyV1(name: String, department: DepartmentV1)

        case class EmployeeV2(name: String, salary: Int, bonus: Int)
        case class DepartmentV2(name: String, employee: EmployeeV2)
        case class CompanyV2(name: String, department: DepartmentV2)

        implicit val employeeV1Schema: Schema[EmployeeV1]     = Schema.derived[EmployeeV1]
        implicit val departmentV1Schema: Schema[DepartmentV1] = Schema.derived[DepartmentV1]
        implicit val companyV1Schema: Schema[CompanyV1]       = Schema.derived[CompanyV1]
        implicit val employeeV2Schema: Schema[EmployeeV2]     = Schema.derived[EmployeeV2]
        implicit val departmentV2Schema: Schema[DepartmentV2] = Schema.derived[DepartmentV2]
        implicit val companyV2Schema: Schema[CompanyV2]       = Schema.derived[CompanyV2]

        val employeeMigration = Migration
          .newBuilder[EmployeeV1, EmployeeV2]
          .addField(_.bonus, literal(1000))
          .build

        val departmentMigration = Migration
          .newBuilder[DepartmentV1, DepartmentV2]
          .migrateField(_.employee, employeeMigration)
          .build

        val migration = Migration
          .newBuilder[CompanyV1, CompanyV2]
          .migrateField(_.department, departmentMigration)
          .buildPartial

        val input = CompanyV1(
          "TechCorp",
          DepartmentV1("Engineering", EmployeeV1("Charlie", 80000))
        )
        val result = migration(input)

        assertTrue(
          result == Right(
            CompanyV2(
              "TechCorp",
              DepartmentV2("Engineering", EmployeeV2("Charlie", 80000, 1000))
            )
          )
        )
      },

      test("4-level nested case class migration") {
        case class L4V1(value: String)
        case class L3V1(name: String, l4: L4V1)
        case class L2V1(id: Int, l3: L3V1)
        case class L1V1(title: String, l2: L2V1)

        case class L4V2(value: String, metadata: String)
        case class L3V2(name: String, l4: L4V2)
        case class L2V2(id: Int, l3: L3V2)
        case class L1V2(title: String, l2: L2V2)

        implicit val l4V1Schema: Schema[L4V1] = Schema.derived[L4V1]
        implicit val l3V1Schema: Schema[L3V1] = Schema.derived[L3V1]
        implicit val l2V1Schema: Schema[L2V1] = Schema.derived[L2V1]
        implicit val l1V1Schema: Schema[L1V1] = Schema.derived[L1V1]
        implicit val l4V2Schema: Schema[L4V2] = Schema.derived[L4V2]
        implicit val l3V2Schema: Schema[L3V2] = Schema.derived[L3V2]
        implicit val l2V2Schema: Schema[L2V2] = Schema.derived[L2V2]
        implicit val l1V2Schema: Schema[L1V2] = Schema.derived[L1V2]

        val l4Migration = Migration
          .newBuilder[L4V1, L4V2]
          .addField(_.metadata, literal("default"))
          .build

        val l3Migration = Migration
          .newBuilder[L3V1, L3V2]
          .migrateField(_.l4, l4Migration)
          .build

        val l2Migration = Migration
          .newBuilder[L2V1, L2V2]
          .migrateField(_.l3, l3Migration)
          .buildPartial

        val migration = Migration
          .newBuilder[L1V1, L1V2]
          .migrateField(_.l2, l2Migration)
          .buildPartial

        val input = L1V1(
          "Root",
          L2V1(1, L3V1("Middle", L4V1("Leaf")))
        )
        val result = migration(input)

        assertTrue(
          result == Right(
            L1V2(
              "Root",
              L2V2(1, L3V2("Middle", L4V2("Leaf", "default")))
            )
          )
        )
      },

      test("Option[NestedType] migration - identity migration preserves structure") {
        case class AddressV1(city: String)
        case class PersonV1(name: String, address: Option[AddressV1])

        case class AddressV2(city: String)
        case class PersonV2(name: String, address: Option[AddressV2])

        implicit val addressV1Schema: Schema[AddressV1] = Schema.derived[AddressV1]
        implicit val personV1Schema: Schema[PersonV1]   = Schema.derived[PersonV1]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]

        val migration = Migration.newBuilder[PersonV1, PersonV2].buildPartial

        val inputWithSome  = PersonV1("Dave", Some(AddressV1("Boston")))
        val resultWithSome = migration(inputWithSome)

        assertTrue(resultWithSome == Right(PersonV2("Dave", Some(AddressV2("Boston")))))
      },

      test("List[NestedType] migration - simple container structure transformation") {
        case class ItemV1(name: String)
        case class ContainerV1(items: List[ItemV1])

        case class ItemV2(name: String)
        case class ContainerV2(items: List[ItemV2])

        implicit val itemV1Schema: Schema[ItemV1]           = Schema.derived[ItemV1]
        implicit val containerV1Schema: Schema[ContainerV1] = Schema.derived[ContainerV1]
        implicit val itemV2Schema: Schema[ItemV2]           = Schema.derived[ItemV2]
        implicit val containerV2Schema: Schema[ContainerV2] = Schema.derived[ContainerV2]

        val migration = Migration.newBuilder[ContainerV1, ContainerV2].buildPartial

        val input = ContainerV1(
          List(ItemV1("Item1"), ItemV1("Item2"))
        )
        val result = migration(input)

        assertTrue(result.isRight)
      },

      test("Map[String, NestedType] migration - simple map structure transformation") {
        case class ConfigV1(value: String)
        case class SettingsV1(configs: Map[String, ConfigV1])

        case class ConfigV2(value: String)
        case class SettingsV2(configs: Map[String, ConfigV2])

        implicit val configV1Schema: Schema[ConfigV1]     = Schema.derived[ConfigV1]
        implicit val settingsV1Schema: Schema[SettingsV1] = Schema.derived[SettingsV1]
        implicit val configV2Schema: Schema[ConfigV2]     = Schema.derived[ConfigV2]
        implicit val settingsV2Schema: Schema[SettingsV2] = Schema.derived[SettingsV2]

        val migration = Migration.newBuilder[SettingsV1, SettingsV2].buildPartial

        val input = SettingsV1(
          Map("setting1" -> ConfigV1("on"), "setting2" -> ConfigV1("off"))
        )
        val result = migration(input)

        assertTrue(result.isRight)
      },

      test("Nested field rename in deeply nested path") {
        case class L3V1(oldField: Int)
        case class L2V1(l3: L3V1)
        case class L1V1(l2: L2V1)

        case class L3V2(newField: Int)
        case class L2V2(l3: L3V2)
        case class L1V2(l2: L2V2)

        implicit val l3V1Schema: Schema[L3V1] = Schema.derived[L3V1]
        implicit val l2V1Schema: Schema[L2V1] = Schema.derived[L2V1]
        implicit val l1V1Schema: Schema[L1V1] = Schema.derived[L1V1]
        implicit val l3V2Schema: Schema[L3V2] = Schema.derived[L3V2]
        implicit val l2V2Schema: Schema[L2V2] = Schema.derived[L2V2]
        implicit val l1V2Schema: Schema[L1V2] = Schema.derived[L1V2]

        val l3Migration = Migration
          .newBuilder[L3V1, L3V2]
          .renameField(_.oldField, _.newField)
          .build

        val l2Migration = Migration
          .newBuilder[L2V1, L2V2]
          .migrateField(_.l3, l3Migration)
          .build

        val migration = Migration
          .newBuilder[L1V1, L1V2]
          .migrateField(_.l2, l2Migration)
          .buildPartial

        val input  = L1V1(L2V1(L3V1(42)))
        val result = migration(input)

        assertTrue(result == Right(L1V2(L2V2(L3V2(42)))))
      },

      test("Multiple operations on nested types - add and rename fields") {
        case class InnerV1(a: Int, b: String)
        case class OuterV1(outer: String, inner: InnerV1)

        case class InnerV2(a: Int, c: String, d: Boolean)
        case class OuterV2(outer: String, inner: InnerV2)

        implicit val innerV1Schema: Schema[InnerV1] = Schema.derived[InnerV1]
        implicit val outerV1Schema: Schema[OuterV1] = Schema.derived[OuterV1]
        implicit val innerV2Schema: Schema[InnerV2] = Schema.derived[InnerV2]
        implicit val outerV2Schema: Schema[OuterV2] = Schema.derived[OuterV2]

        val innerMigration = Migration
          .newBuilder[InnerV1, InnerV2]
          .renameField(_.b, _.c)
          .addField(_.d, literal(true))
          .build

        val migration = Migration
          .newBuilder[OuterV1, OuterV2]
          .migrateField(_.inner, innerMigration)
          .build

        val input  = OuterV1("Test", InnerV1(10, "hello"))
        val result = migration(input)

        assertTrue(result == Right(OuterV2("Test", InnerV2(10, "hello", true))))
      }
    ),

    suite("Deep Nested Selectors")(
      test("renameField with deep nested selector") {
        case class Address1(street: String, city: String)
        case class Person1(name: String, address: Address1)

        case class Address2(streetName: String, city: String)
        case class Person2(name: String, address: Address2)

        implicit val a1Schema: Schema[Address1] = Schema.derived
        implicit val p1Schema: Schema[Person1]  = Schema.derived
        implicit val a2Schema: Schema[Address2] = Schema.derived
        implicit val p2Schema: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .renameField(_.address.street, _.address.streetName)
          .buildPartial

        val input  = Person1("Alice", Address1("123 Main", "NYC"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main", "NYC"))))
      },
      test("addField with deep nested selector") {
        case class Address1(street: String)
        case class Person1(name: String, address: Address1)

        case class Address2(street: String, number: Int)
        case class Person2(name: String, address: Address2)

        implicit val a1Schema: Schema[Address1] = Schema.derived
        implicit val p1Schema: Schema[Person1]  = Schema.derived
        implicit val a2Schema: Schema[Address2] = Schema.derived
        implicit val p2Schema: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .addField(_.address.number, literal(0))
          .buildPartial

        val input  = Person1("Alice", Address1("123 Main"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main", 0))))
      },
      test("dropField with deep nested selector") {
        case class Address1(street: String, zip: String)
        case class Person1(name: String, address: Address1)

        case class Address2(street: String)
        case class Person2(name: String, address: Address2)

        implicit val a1Schema: Schema[Address1] = Schema.derived
        implicit val p1Schema: Schema[Person1]  = Schema.derived
        implicit val a2Schema: Schema[Address2] = Schema.derived
        implicit val p2Schema: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .dropField(_.address.zip, literal("00000"))
          .buildPartial

        val input  = Person1("Alice", Address1("123 Main", "10001"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main"))))
      },
      test("combined deep rename and add with nested selectors") {
        case class Address1(street: String)
        case class Person1(name: String, address: Address1)

        case class Address2(streetName: String, number: Int)
        case class Person2(name: String, address: Address2)

        implicit val a1Schema: Schema[Address1] = Schema.derived
        implicit val p1Schema: Schema[Person1]  = Schema.derived
        implicit val a2Schema: Schema[Address2] = Schema.derived
        implicit val p2Schema: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .renameField(_.address.street, _.address.streetName)
          .addField(_.address.number, literal(0))
          .buildPartial

        val input  = Person1("Alice", Address1("123 Main"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main", 0))))
      },
      test("3-level deep addField") {
        case class Street1(name: String)
        case class Address1(street: Street1, city: String)
        case class Person1(name: String, address: Address1)

        case class Street2(name: String, number: Int)
        case class Address2(street: Street2, city: String)
        case class Person2(name: String, address: Address2)

        implicit val s1: Schema[Street1]  = Schema.derived
        implicit val a1: Schema[Address1] = Schema.derived
        implicit val p1: Schema[Person1]  = Schema.derived
        implicit val s2: Schema[Street2]  = Schema.derived
        implicit val a2: Schema[Address2] = Schema.derived
        implicit val p2: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .addField(_.address.street.number, literal(0))
          .buildPartial

        val input  = Person1("Alice", Address1(Street1("Main"), "NYC"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2(Street2("Main", 0), "NYC"))))
      },
      test("3-level deep renameField") {
        case class Street1(name: String)
        case class Address1(street: Street1)
        case class Person1(info: Address1)

        case class Street2(label: String)
        case class Address2(street: Street2)
        case class Person2(info: Address2)

        implicit val s1: Schema[Street1]  = Schema.derived
        implicit val a1: Schema[Address1] = Schema.derived
        implicit val p1: Schema[Person1]  = Schema.derived
        implicit val s2: Schema[Street2]  = Schema.derived
        implicit val a2: Schema[Address2] = Schema.derived
        implicit val p2: Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .renameField(_.info.street.name, _.info.street.label)
          .buildPartial

        val input  = Person1(Address1(Street1("Main")))
        val result = migration(input)

        assertTrue(result == Right(Person2(Address2(Street2("Main")))))
      },
      test("3-level deep combined rename and drop") {
        case class Geo1(lat: Double, lng: Double, alt: Double)
        case class Location1(geo: Geo1)
        case class Event1(location: Location1)

        case class Geo2(latitude: Double, lng: Double)
        case class Location2(geo: Geo2)
        case class Event2(location: Location2)

        implicit val g1: Schema[Geo1]      = Schema.derived
        implicit val l1: Schema[Location1] = Schema.derived
        implicit val e1: Schema[Event1]    = Schema.derived
        implicit val g2: Schema[Geo2]      = Schema.derived
        implicit val l2: Schema[Location2] = Schema.derived
        implicit val e2: Schema[Event2]    = Schema.derived

        val migration = Migration
          .newBuilder[Event1, Event2]
          .renameField(_.location.geo.lat, _.location.geo.latitude)
          .dropField(_.location.geo.alt, literal(0.0))
          .buildPartial

        val input  = Event1(Location1(Geo1(40.7, -74.0, 10.0)))
        val result = migration(input)

        assertTrue(result == Right(Event2(Location2(Geo2(40.7, -74.0)))))
      },
      test("fromActions with deep optic paths") {
        case class Addr1(street: String, city: String)
        case class Org1(name: String, addr: Addr1)

        case class Addr2(street: String, city: String, zip: String)
        case class Org2(name: String, addr: Addr2)

        implicit val a1: Schema[Addr1] = Schema.derived
        implicit val o1: Schema[Org1]  = Schema.derived
        implicit val a2: Schema[Addr2] = Schema.derived
        implicit val o2: Schema[Org2]  = Schema.derived

        val migration = Migration.fromActions[Org1, Org2](
          MigrationAction.AddField(
            DynamicOptic.root.field("addr").field("zip"),
            dynamicLiteral("00000")
          )
        )

        val input  = Org1("Acme", Addr1("123 Main", "NYC"))
        val result = migration(input)

        assertTrue(result == Right(Org2("Acme", Addr2("123 Main", "NYC", "00000"))))
      }
    ),

    suite("Recursive Type Limitations")(
      test("recursive type basic migration works with buildPartial") {
        // This test documents that recursive types require special care.
        // Recursive types use Schema.Deferred to break cycles at the type level.
        // Simple identity migrations work, but adding fields to recursive chains requires buildPartial

        case class ListNodeV1(value: Int, next: Option[ListNodeV1])
        case class ListNodeV2(value: Int, next: Option[ListNodeV2])

        implicit lazy val listNodeV1Schema: Schema[ListNodeV1] = Schema.derived[ListNodeV1]
        implicit lazy val listNodeV2Schema: Schema[ListNodeV2] = Schema.derived[ListNodeV2]

        val migration = Migration.newBuilder[ListNodeV1, ListNodeV2].buildPartial

        val input  = ListNodeV1(1, Some(ListNodeV1(2, None)))
        val result = migration(input)

        assertTrue(result.isRight)
      }
    )
  )
}
