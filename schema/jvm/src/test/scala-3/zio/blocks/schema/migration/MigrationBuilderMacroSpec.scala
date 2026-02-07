package zio.blocks.schema.migration

import scala.reflect.Selectable.reflectiveSelectable
import zio.Scope
import zio.blocks.schema.*
import zio.test.*

object MigrationBuilderMacroSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment & Scope, Any] = suite("MigrationBuilder Macro API")(
    suite("Selector-based field operations")(
      test("add using selector extracts field path") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("drop using selector extracts field path") {
        val builder = MigrationBuilder[PersonV2, PersonV1]
          .drop(_.age, DynamicValue.int(0))
        val migration = builder.build
        val original  = PersonV2("Alice", 30)
        val result    = migration(original)

        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("rename using selectors extracts both paths") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("changeType using selector extracts field path") {
        val builder = MigrationBuilder[PersonWithIntId, PersonWithLongId]
          .changeType(_.id, PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        val migration = builder.build
        val original  = PersonWithIntId("Alice", 42)
        val result    = migration(original)

        assertTrue(result == Right(PersonWithLongId("Alice", 42L)))
      },
      test("nested selector path works for deeply nested fields") {
        val builder = MigrationBuilder[NestedV1, NestedV2]
          .add(_.person.contact.phone, DynamicValue.string("555-0000"))
        val migration = builder.build
        val original  = NestedV1(PersonWithContact("Alice", Contact("alice@example.com")))
        val result    = migration(original)

        assertTrue(result == Right(NestedV2(PersonWithContactV2("Alice", ContactV2("alice@example.com", "555-0000")))))
      },
      test("multiple selector operations chain correctly") {
        val builder = MigrationBuilder[PersonV1, PersonV3]
          .add(_.age, DynamicValue.int(0))
          .add(_.active, DynamicValue.boolean(true))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 0, true)))
      },
      test("transform using selector applies bidirectional transformation") {
        val builder = MigrationBuilder[PersonWithCount, PersonWithCount]
          .transform(
            _.count,
            DynamicValueTransform.numericMultiply(2),
            DynamicValueTransform.numericMultiply(0.5)
          )
        val migration = builder.build
        val original  = PersonWithCount("Alice", 10)
        val result    = migration(original)

        assertTrue(result == Right(PersonWithCount("Alice", 20)))
      }
    ),
    suite("Macro compile-time extraction")(
      test("fieldPath macro extracts simple field name") {
        val path = MigrationBuilderMacros.fieldPath[PersonV1, String](_.name)
        assertTrue(path == "name")
      },
      test("fieldPath macro extracts nested field path") {
        val path = MigrationBuilderMacros.fieldPath[NestedV1, String](_.person.name)
        assertTrue(path == "person.name")
      },
      test("fieldPath macro extracts deeply nested field path") {
        val path = MigrationBuilderMacros.fieldPath[NestedV1, String](_.person.contact.email)
        assertTrue(path == "person.contact.email")
      },
      test("lastFieldName macro extracts terminal field name") {
        val name = MigrationBuilderMacros.lastFieldName[NestedV1, String](_.person.contact.email)
        assertTrue(name == "email")
      }
    ),
    suite("Mixed API usage")(
      test("can mix selector-based and string-based operations") {
        val builder = MigrationBuilder[PersonV1, PersonV3]
          .add(_.age, DynamicValue.int(25))
          .addField("active", DynamicValue.boolean(false))
        val migration = builder.buildPartial
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 25, false)))
      }
    ),
    suite("MigrationExpr-based operations")(
      test("addExpr using literal default") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addExpr(_.age, MigrationExpr.literal(42))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 42)))
      },
      test("toDynamicOptic macro extracts simple field path") {
        val optic = MigrationBuilderMacros.toDynamicOptic[PersonV1, String](_.name)
        assertTrue(
          optic.nodes.size == 1 &&
            optic.nodes.head == DynamicOptic.Node.Field("name")
        )
      },
      test("toDynamicOptic macro extracts nested field path") {
        val optic = MigrationBuilderMacros.toDynamicOptic[NestedV1, String](_.person.contact.email)
        assertTrue(
          optic.nodes.size == 3 &&
            optic.nodes(0) == DynamicOptic.Node.Field("person") &&
            optic.nodes(1) == DynamicOptic.Node.Field("contact") &&
            optic.nodes(2) == DynamicOptic.Node.Field("email")
        )
      }
    ),
    suite("Field name extraction macros")(
      test("extractFieldNames extracts all field names from a case class") {
        val fields = MigrationBuilderMacros.extractFieldNames[PersonV2]
        assertTrue(
          fields.contains("name") &&
            fields.contains("age") &&
            fields.size == 2
        )
      },
      test("extractFieldNames works with multiple fields") {
        val fields = MigrationBuilderMacros.extractFieldNames[PersonV3]
        assertTrue(
          fields.contains("name") &&
            fields.contains("age") &&
            fields.contains("active") &&
            fields.size == 3
        )
      }
    ),
    suite("Migration completeness validation")(
      test("validateCompileTime passes when migration is complete") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonV2](
          added = Set("age"),
          removed = Set.empty,
          renamedFrom = Set.empty,
          renamedTo = Set.empty
        )
        assertTrue(true)
      },
      test("validateCompileTime passes with rename") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonRenamed](
          added = Set.empty,
          removed = Set.empty,
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        assertTrue(true)
      }
    ),
    suite("build with compile-time validation")(
      test("build succeeds for complete migration") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("build succeeds with rename") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("build succeeds with add and drop") {
        val builder = MigrationBuilder[PersonV2, PersonRenamed]
          .drop(_.age, DynamicValue.int(0))
          .rename(_.name, _.fullName)
        val migration = builder.build
        val original  = PersonV2("Alice", 30)
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("build tracks addedFieldNames correctly") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))

        assertTrue(builder.addedFieldNames == Set("age"))
      },
      test("build tracks removedFieldNames correctly") {
        val builder = MigrationBuilder[PersonV2, PersonV1]
          .drop(_.age, DynamicValue.int(0))

        assertTrue(builder.removedFieldNames == Set("age"))
      },
      test("build tracks renamedFromNames and renamedToNames correctly") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)

        assertTrue(
          builder.renamedFromNames == Set("name") &&
            builder.renamedToNames == Set("fullName")
        )
      },
      test("build handles joinFields tracking correctly") {
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val builder = MigrationBuilder[PersonWithSplitName, PersonWithFullName]
          .joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
          .drop(_.age, DynamicValue.int(0))

        assertTrue(
          builder.addedFieldNames == Set("fullName"),
          builder.removedFieldNames == Set("firstName", "lastName", "age")
        )
      },
      test("build handles splitField tracking correctly") {
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))

        val builder = MigrationBuilder[PersonWithFullName, PersonWithSplitName]
          .splitField("fullName", Vector("firstName", "lastName"), splitter, combiner)
          .add(_.age, DynamicValue.int(0))

        assertTrue(
          builder.addedFieldNames == Set("firstName", "lastName", "age"),
          builder.removedFieldNames == Set("fullName")
        )
      }
    ),
    suite("buildChecked compile-time validation")(
      test("buildChecked succeeds with correct literal field sets") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.buildChecked(
          added = Set("age"),
          removed = Set.empty
        )
        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("buildChecked succeeds with rename operations") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.buildChecked(
          added = Set.empty,
          removed = Set.empty,
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("buildChecked succeeds with add and drop") {
        val builder = MigrationBuilder[PersonV2, PersonRenamed]
          .drop(_.age, DynamicValue.int(0))
          .rename(_.name, _.fullName)
        val migration = builder.buildChecked(
          added = Set.empty,
          removed = Set("age"),
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("validateCompileTime passes with correct operations") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonV2](
          added = Set("age"),
          removed = Set.empty,
          renamedFrom = Set.empty,
          renamedTo = Set.empty
        )
        assertTrue(true)
      },
      test("validateCompileTime passes with rename") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonRenamed](
          added = Set.empty,
          removed = Set.empty,
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        assertTrue(true)
      },
      test("validateCompileTime passes with complex migration") {
        MigrationBuilderMacros.validateCompileTime[PersonV2, PersonRenamed](
          added = Set.empty,
          removed = Set("age"),
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        assertTrue(true)
      }
    ),
    suite("TypedMigrationBuilder compile-time failure tests")(
      test("incomplete migration would fail at compile time") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val completeMigration = MigrationBuilder[PersonV1, PersonV2, Empty, Empty](
          summon[Schema[PersonV1]],
          summon[Schema[PersonV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.age, DynamicValue.int(0))
          .buildTyped

        val result = completeMigration(PersonV1("Alice"))
        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("migration with drop requires handling removed field") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val completeMigration = MigrationBuilder[PersonV2, PersonV1, Empty, Empty](
          summon[Schema[PersonV2]],
          summon[Schema[PersonV1]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .buildTyped

        val result = completeMigration(PersonV2("Alice", 30))
        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("complex migration with multiple operations must handle all changes") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val completeMigration = MigrationBuilder[PersonV2, PersonV4, Empty, Empty](
          summon[Schema[PersonV2]],
          summon[Schema[PersonV4]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .addTyped(_.email, DynamicValue.string("default@test.com"))
          .buildTyped

        val result = completeMigration(PersonV2("Alice", 30))
        assertTrue(result == Right(PersonV4("Alice", "default@test.com")))
      },
      test("nested field migration must handle nested changes") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val completeMigration = MigrationBuilder[PersonWithAddress, PersonWithAddressV2, Empty, Empty](
          summon[Schema[PersonWithAddress]],
          summon[Schema[PersonWithAddressV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.address.zip, DynamicValue.string("12345"))
          .buildTyped

        val result = completeMigration(PersonWithAddress("Alice", AddressSimple("123 Main St", "NYC")))
        assertTrue(result == Right(PersonWithAddressV2("Alice", AddressWithZip("123 Main St", "NYC", "12345"))))
      }
    ),
    suite("TypedMigrationBuilder compile-time validation")(
      test("addTyped tracks field at type level and buildTyped validates") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonV1, PersonV2, Empty, Empty](
          summon[Schema[PersonV1]],
          summon[Schema[PersonV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.age, DynamicValue.int(25))
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 25)))
      },
      test("dropTyped tracks field at type level") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonV2, PersonV1, Empty, Empty](
          summon[Schema[PersonV2]],
          summon[Schema[PersonV1]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .buildTyped

        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("renameTyped tracks both old and new fields at type level") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonV1, PersonRenamed, Empty, Empty](
          summon[Schema[PersonV1]],
          summon[Schema[PersonRenamed]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .renameTyped(_.name, _.fullName)
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("multiple typed operations chain correctly") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonV1, PersonV3, Empty, Empty](
          summon[Schema[PersonV1]],
          summon[Schema[PersonV3]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.age, DynamicValue.int(30))
          .addTyped(_.active, DynamicValue.boolean(true))
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 30, true)))
      },
      test("TypedMigrationBuilder.from fluent API works") {
        import TypedMigrationBuilderMacro.*

        val migration = TypedMigrationBuilderMacro
          .from[PersonV1]
          .to[PersonV2]
          .addTyped(_.age, DynamicValue.int(42))
          .buildTyped

        val original = PersonV1("Bob")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Bob", 42)))
      },
      test("complex migration with add, drop, and rename") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonV2, PersonV4, Empty, Empty](
          summon[Schema[PersonV2]],
          summon[Schema[PersonV4]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .addTyped(_.email, DynamicValue.string("default@example.com"))
          .buildTyped

        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonV4("Alice", "default@example.com")))
      },
      test("nested field paths work with addTyped") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[PersonWithAddress, PersonWithAddressV2, Empty, Empty](
          summon[Schema[PersonWithAddress]],
          summon[Schema[PersonWithAddressV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.address.zip, DynamicValue.string("12345"))
          .buildTyped

        val original = PersonWithAddress("Alice", AddressSimple("123 Main St", "NYC"))
        val result   = migration(original)

        assertTrue(result == Right(PersonWithAddressV2("Alice", AddressWithZip("123 Main St", "NYC", "12345"))))
      },
      test("deeply nested paths (3+ levels) work with tree-based tracking") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[CompanyV1, CompanyV2, Empty, Empty](
          summon[Schema[CompanyV1]],
          summon[Schema[CompanyV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.headquarters.location.country.code, DynamicValue.string("US"))
          .buildTyped

        val original = CompanyV1("Acme", HeadquartersV1(LocationV1(CountryV1("United States"))))
        val result   = migration(original)

        assertTrue(result == Right(CompanyV2("Acme", HeadquartersV2(LocationV2(CountryV2("United States", "US"))))))
      },
      test("multiple deeply nested operations on different branches") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[OrganizationV1, OrganizationV2, Empty, Empty](
          summon[Schema[OrganizationV1]],
          summon[Schema[OrganizationV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.ceo.contact.phone, DynamicValue.string("555-1234"))
          .addTyped(_.cfo.contact.phone, DynamicValue.string("555-5678"))
          .buildTyped

        val original = OrganizationV1(
          "TechCorp",
          ExecutiveV1("Alice", ContactInfoV1("alice@tech.com")),
          ExecutiveV1("Bob", ContactInfoV1("bob@tech.com"))
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            OrganizationV2(
              "TechCorp",
              ExecutiveV2("Alice", ContactInfoV2("alice@tech.com", "555-1234")),
              ExecutiveV2("Bob", ContactInfoV2("bob@tech.com", "555-5678"))
            )
          )
        )
      },
      test("drop nested field and add at different nesting level") {
        import TypedMigrationBuilderMacro.*
        import TypeLevel.Empty

        val migration = MigrationBuilder[ProjectV1, ProjectV2, Empty, Empty](
          summon[Schema[ProjectV1]],
          summon[Schema[ProjectV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.owner.department.budget, DynamicValue.int(0))
          .addTyped(_.owner.title, DynamicValue.string("Engineer"))
          .buildTyped

        val original = ProjectV1("Migration", OwnerV1("Alice", DepartmentV1("Engineering", 100000)))
        val result   = migration(original)

        assertTrue(result == Right(ProjectV2("Migration", OwnerV2("Alice", "Engineer", DepartmentV2("Engineering")))))
      }
    ),
    suite("Nested field path tracking")(
      test("addedFieldNames tracks nested paths correctly") {
        val builder = MigrationBuilder[EmployeeV1, EmployeeV2]
          .add(_.address.zip, DynamicValue.string("12345"))

        assertTrue(builder.addedFieldNames == Set("address.zip"))
      },
      test("removedFieldNames tracks nested paths correctly") {
        val builder = MigrationBuilder[EmployeeV2, EmployeeV1]
          .drop(_.address.zip, DynamicValue.string(""))

        assertTrue(builder.removedFieldNames == Set("address.zip"))
      },
      test("renamedFromNames and renamedToNames track nested paths correctly") {
        val builder = MigrationBuilder[PersonWithAddress, PersonWithAddress]
          .renameField("address.street", "address.streetName")

        assertTrue(
          builder.renamedFromNames == Set("address.street") &&
            builder.renamedToNames == Set("address.streetName")
        )
      },
      test("build validates nested field additions") {
        val builder = MigrationBuilder[EmployeeV1, EmployeeV2]
          .add(_.address.zip, DynamicValue.string("12345"))
        val migration = builder.build
        val original  = EmployeeV1("Alice", AddressSimple("123 Main St", "NYC"))
        val result    = migration(original)

        assertTrue(result == Right(EmployeeV2("Alice", AddressWithZip("123 Main St", "NYC", "12345"))))
      },
      test("build validates nested field removals") {
        val builder = MigrationBuilder[EmployeeV2, EmployeeV1]
          .drop(_.address.zip, DynamicValue.string(""))
        val migration = builder.build
        val original  = EmployeeV2("Alice", AddressWithZip("123 Main St", "NYC", "12345"))
        val result    = migration(original)

        assertTrue(result == Right(EmployeeV1("Alice", AddressSimple("123 Main St", "NYC"))))
      },
      test("build validates deeply nested paths (3+ levels)") {
        val builder = MigrationBuilder[CompanyV1, CompanyV2]
          .add(_.headquarters.location.country.code, DynamicValue.string("US"))
        val migration = builder.build
        val original  = CompanyV1("Acme", HeadquartersV1(LocationV1(CountryV1("United States"))))
        val result    = migration(original)

        assertTrue(result == Right(CompanyV2("Acme", HeadquartersV2(LocationV2(CountryV2("United States", "US"))))))
      },
      test("build validates multiple nested operations on different branches") {
        val builder = MigrationBuilder[OrganizationV1, OrganizationV2]
          .add(_.ceo.contact.phone, DynamicValue.string("555-1234"))
          .add(_.cfo.contact.phone, DynamicValue.string("555-5678"))
        val migration = builder.build

        val original = OrganizationV1(
          "TechCorp",
          ExecutiveV1("Alice", ContactInfoV1("alice@tech.com")),
          ExecutiveV1("Bob", ContactInfoV1("bob@tech.com"))
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            OrganizationV2(
              "TechCorp",
              ExecutiveV2("Alice", ContactInfoV2("alice@tech.com", "555-1234")),
              ExecutiveV2("Bob", ContactInfoV2("bob@tech.com", "555-5678"))
            )
          )
        )
      }
    ),
    suite("Structural type migrations")(
      test("migration from structural type to case class") {
        val nominalSchema: Schema[PersonNominal]  = Schema.derived
        val structuralSchema                      = nominalSchema.structural
        val targetSchema: Schema[PersonNominalV2] = Schema.derived

        val migration = DynamicMigration.record(_.addField("email", DynamicValue.string("default@example.com")))

        val structuralInstance: { def name: String; def age: Int } = new {
          def name: String = "Alice"
          def age: Int     = 30
        }

        val dynamicValue = structuralSchema.toDynamicValue(structuralInstance)
        val migrated     = migration(dynamicValue)
        val result       = migrated.flatMap(
          targetSchema.fromDynamicValue(_).left.map(e => MigrationError.incompatibleValue(DynamicOptic.root, e.message))
        )

        assertTrue(result == Right(PersonNominalV2("Alice", 30, "default@example.com")))
      },
      test("migration from case class to structural type") {
        val sourceSchema: Schema[PersonNominal]               = Schema.derived
        val targetNominalSchema: Schema[PersonNominalRenamed] = Schema.derived
        val targetStructuralSchema                            = targetNominalSchema.structural

        val migration      = DynamicMigration.record(_.renameField("name", "fullName"))
        val sourceInstance = PersonNominal("Alice", 30)

        val dynamicValue = sourceSchema.toDynamicValue(sourceInstance)
        val migrated     = migration(dynamicValue)
        val result       = migrated.flatMap(
          targetStructuralSchema
            .fromDynamicValue(_)
            .left
            .map(e => MigrationError.incompatibleValue(DynamicOptic.root, e.message))
        )

        assertTrue(
          result.isRight &&
            result.toOption.get.fullName == "Alice" &&
            result.toOption.get.age == 30
        )
      },
      test("migration between two structural types") {
        val v1NominalSchema: Schema[PersonNominal]   = Schema.derived
        val v2NominalSchema: Schema[PersonNominalV2] = Schema.derived
        val v1StructuralSchema                       = v1NominalSchema.structural
        val v2StructuralSchema                       = v2NominalSchema.structural

        val migration = DynamicMigration.record(_.addField("email", DynamicValue.string("contact@example.com")))

        val v1Instance: { def name: String; def age: Int } = new {
          def name: String = "Bob"
          def age: Int     = 25
        }

        val dynamicV1       = v1StructuralSchema.toDynamicValue(v1Instance)
        val migratedDynamic = migration(dynamicV1)
        val result          = migratedDynamic.flatMap(
          v2StructuralSchema
            .fromDynamicValue(_)
            .left
            .map(e => MigrationError.incompatibleValue(DynamicOptic.root, e.message))
        )

        assertTrue(
          result.isRight &&
            result.toOption.get.name == "Bob" &&
            result.toOption.get.age == 25 &&
            result.toOption.get.email == "contact@example.com"
        )
      },
      test("nested structural type migration") {
        val v1Schema: Schema[PersonWithAddressNominal] = Schema.derived

        val migration = DynamicMigration.record(
          _.nested("address")(
            _.addField("zip", DynamicValue.string("12345"))
          )
        )

        val v1Instance      = PersonWithAddressNominal("Carol", AddressNominal("456 Oak Ave", "Boston"))
        val dynamicV1       = v1Schema.toDynamicValue(v1Instance)
        val migratedDynamic = migration(dynamicV1)

        assertTrue(migratedDynamic match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("name") == Some(DynamicValue.string("Carol")) &&
            (fieldMap.get("address") match {
              case Some(DynamicValue.Record(addrFields)) =>
                val addrMap = addrFields.toMap
                addrMap.get("street") == Some(DynamicValue.string("456 Oak Ave")) &&
                addrMap.get("city") == Some(DynamicValue.string("Boston")) &&
                addrMap.get("zip") == Some(DynamicValue.string("12345"))
              case _ => false
            })
          case _ => false
        })
      },
      test("typed migration using structural schemas") {
        val sourceSchema: Schema[PersonNominal]   = Schema.derived
        val targetSchema: Schema[PersonNominalV2] = Schema.derived
        val sourceStructural                      = sourceSchema.structural
        val targetStructural                      = targetSchema.structural

        val dynamicMig = DynamicMigration.record(_.addField("email", DynamicValue.string("test@test.com")))
        val migration  = Migration(dynamicMig, sourceStructural, targetStructural)

        val source: { def name: String; def age: Int } = new {
          def name: String = "Dave"
          def age: Int     = 40
        }

        val result = migration(source)

        assertTrue(
          result.isRight &&
            result.toOption.get.name == "Dave" &&
            result.toOption.get.age == 40 &&
            result.toOption.get.email == "test@test.com"
        )
      },
      test("migration roundtrip nominal to dynamic to nominal") {
        val nominalV1Schema: Schema[PersonNominal]   = Schema.derived
        val nominalV2Schema: Schema[PersonNominalV2] = Schema.derived

        val original  = PersonNominal("Eve", 35)
        val dynamicV1 = nominalV1Schema.toDynamicValue(original)

        val migration = DynamicMigration.record(_.addField("email", DynamicValue.string("eve@example.com")))
        val dynamicV2 = migration(dynamicV1)

        val result = dynamicV2.flatMap(
          nominalV2Schema
            .fromDynamicValue(_)
            .left
            .map(e => MigrationError.incompatibleValue(DynamicOptic.root, e.message))
        )

        assertTrue(result == Right(PersonNominalV2("Eve", 35, "eve@example.com")))
      },
      test("DynamicValue representation is identical for structural and nominal schemas") {
        val nominalSchema: Schema[PersonNominal] = Schema.derived
        val structuralSchema                     = nominalSchema.structural

        val nominalInstance                                        = PersonNominal("Frank", 50)
        val structuralInstance: { def name: String; def age: Int } = new {
          def name: String = "Frank"
          def age: Int     = 50
        }

        val nominalDynamic    = nominalSchema.toDynamicValue(nominalInstance)
        val structuralDynamic = structuralSchema.toDynamicValue(structuralInstance)

        assertTrue((nominalDynamic, structuralDynamic) match {
          case (DynamicValue.Record(nFields), DynamicValue.Record(sFields)) =>
            nFields.toMap == sFields.toMap
          case _ => false
        })
      }
    )
  )
}

case class PersonWithCount(name: String, count: Int)
object PersonWithCount {
  implicit val schema: Schema[PersonWithCount] = Schema.derived
}

case class PersonV4(name: String, email: String)
object PersonV4 {
  implicit val schema: Schema[PersonV4] = Schema.derived
}

case class PersonWithAddress(name: String, address: AddressSimple)
object PersonWithAddress {
  implicit val schema: Schema[PersonWithAddress] = Schema.derived
}

case class AddressSimple(street: String, city: String)
object AddressSimple {
  implicit val schema: Schema[AddressSimple] = Schema.derived
}

case class PersonWithAddressV2(name: String, address: AddressWithZip)
object PersonWithAddressV2 {
  implicit val schema: Schema[PersonWithAddressV2] = Schema.derived
}

case class AddressWithZip(street: String, city: String, zip: String)
object AddressWithZip {
  implicit val schema: Schema[AddressWithZip] = Schema.derived
}

case class CountryV1(name: String)
object CountryV1 {
  implicit val schema: Schema[CountryV1] = Schema.derived
}

case class CountryV2(name: String, code: String)
object CountryV2 {
  implicit val schema: Schema[CountryV2] = Schema.derived
}

case class LocationV1(country: CountryV1)
object LocationV1 {
  implicit val schema: Schema[LocationV1] = Schema.derived
}

case class LocationV2(country: CountryV2)
object LocationV2 {
  implicit val schema: Schema[LocationV2] = Schema.derived
}

case class HeadquartersV1(location: LocationV1)
object HeadquartersV1 {
  implicit val schema: Schema[HeadquartersV1] = Schema.derived
}

case class HeadquartersV2(location: LocationV2)
object HeadquartersV2 {
  implicit val schema: Schema[HeadquartersV2] = Schema.derived
}

case class CompanyV1(name: String, headquarters: HeadquartersV1)
object CompanyV1 {
  implicit val schema: Schema[CompanyV1] = Schema.derived
}

case class CompanyV2(name: String, headquarters: HeadquartersV2)
object CompanyV2 {
  implicit val schema: Schema[CompanyV2] = Schema.derived
}

case class ContactInfoV1(email: String)
object ContactInfoV1 {
  implicit val schema: Schema[ContactInfoV1] = Schema.derived
}

case class ContactInfoV2(email: String, phone: String)
object ContactInfoV2 {
  implicit val schema: Schema[ContactInfoV2] = Schema.derived
}

case class ExecutiveV1(name: String, contact: ContactInfoV1)
object ExecutiveV1 {
  implicit val schema: Schema[ExecutiveV1] = Schema.derived
}

case class ExecutiveV2(name: String, contact: ContactInfoV2)
object ExecutiveV2 {
  implicit val schema: Schema[ExecutiveV2] = Schema.derived
}

case class OrganizationV1(name: String, ceo: ExecutiveV1, cfo: ExecutiveV1)
object OrganizationV1 {
  implicit val schema: Schema[OrganizationV1] = Schema.derived
}

case class OrganizationV2(name: String, ceo: ExecutiveV2, cfo: ExecutiveV2)
object OrganizationV2 {
  implicit val schema: Schema[OrganizationV2] = Schema.derived
}

case class DepartmentV1(name: String, budget: Int)
object DepartmentV1 {
  implicit val schema: Schema[DepartmentV1] = Schema.derived
}

case class DepartmentV2(name: String)
object DepartmentV2 {
  implicit val schema: Schema[DepartmentV2] = Schema.derived
}

case class OwnerV1(name: String, department: DepartmentV1)
object OwnerV1 {
  implicit val schema: Schema[OwnerV1] = Schema.derived
}

case class OwnerV2(name: String, title: String, department: DepartmentV2)
object OwnerV2 {
  implicit val schema: Schema[OwnerV2] = Schema.derived
}

case class ProjectV1(name: String, owner: OwnerV1)
object ProjectV1 {
  implicit val schema: Schema[ProjectV1] = Schema.derived
}

case class ProjectV2(name: String, owner: OwnerV2)
object ProjectV2 {
  implicit val schema: Schema[ProjectV2] = Schema.derived
}

case class PersonNominal(name: String, age: Int)
object PersonNominal {
  implicit val schema: Schema[PersonNominal] = Schema.derived
}

case class PersonNominalV2(name: String, age: Int, email: String)
object PersonNominalV2 {
  implicit val schema: Schema[PersonNominalV2] = Schema.derived
}

case class PersonNominalRenamed(fullName: String, age: Int)
object PersonNominalRenamed {
  implicit val schema: Schema[PersonNominalRenamed] = Schema.derived
}

case class AddressNominal(street: String, city: String)
object AddressNominal {
  implicit val schema: Schema[AddressNominal] = Schema.derived
}

case class AddressNominalV2(street: String, city: String, zip: String)
object AddressNominalV2 {
  implicit val schema: Schema[AddressNominalV2] = Schema.derived
}

case class PersonWithAddressNominal(name: String, address: AddressNominal)
object PersonWithAddressNominal {
  implicit val schema: Schema[PersonWithAddressNominal] = Schema.derived
}

case class PersonWithAddressNominalV2(name: String, address: AddressNominalV2)
object PersonWithAddressNominalV2 {
  implicit val schema: Schema[PersonWithAddressNominalV2] = Schema.derived
}

case class EmployeeV1(name: String, address: AddressSimple)
object EmployeeV1 {
  implicit val schema: Schema[EmployeeV1] = Schema.derived
}

case class EmployeeV2(name: String, address: AddressWithZip)
object EmployeeV2 {
  implicit val schema: Schema[EmployeeV2] = Schema.derived
}
